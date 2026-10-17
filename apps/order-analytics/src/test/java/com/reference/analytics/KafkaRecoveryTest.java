package com.reference.analytics;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.*;
import org.apache.kafka.streams.KafkaStreams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class KafkaRecoveryTest {
  private static final long EVENT_SECONDS = java.time.Instant.now().getEpochSecond() / 60 * 60 + 20;

  @Container
  static final KafkaContainer KAFKA =
      new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.2"));

  @TempDir Path state;

  @Test
  void rebuildsStateFromChangelogAndDeduplicatesAfterStateLoss() throws Exception {
    String bootstrap = KAFKA.getBootstrapServers(), app = "recovery-" + UUID.randomUUID();
    try (Admin admin = Admin.create(Map.of("bootstrap.servers", bootstrap))) {
      admin
          .createTopics(
              java.util.List.of(
                  new NewTopic(OrderSummaryTopology.INPUT, 3, (short) 1),
                  new NewTopic(OrderSummaryTopology.OUTPUT, 3, (short) 1),
                  new NewTopic(OrderSummaryTopology.DLT, 3, (short) 1)))
          .all()
          .get();
    }
    Properties producerConfig = new Properties();
    producerConfig.put("bootstrap.servers", bootstrap);
    producerConfig.put("key.serializer", StringSerializer.class);
    producerConfig.put("value.serializer", StringSerializer.class);
    Properties consumerConfig = new Properties();
    consumerConfig.put("bootstrap.servers", bootstrap);
    consumerConfig.put("group.id", app + "-assert");
    consumerConfig.put("auto.offset.reset", "earliest");
    consumerConfig.put("isolation.level", "read_committed");
    consumerConfig.put("key.deserializer", StringDeserializer.class);
    consumerConfig.put("value.deserializer", StringDeserializer.class);
    try (var producer = new KafkaProducer<String, String>(producerConfig);
        var consumer = new KafkaConsumer<String, String>(consumerConfig)) {
      consumer.subscribe(List.of(OrderSummaryTopology.OUTPUT));
      KafkaStreams first = start(bootstrap, app);
      try {
        send(producer, 1, 2);
        assertEquals(2, awaitCount(consumer, 1).get("totalQuantity").asInt());
      } finally {
        assertTrue(
            first.close(
                new KafkaStreams.CloseOptions().timeout(Duration.ofSeconds(30)).leaveGroup(true)));
      }
      // Simulate a replacement node with no local RocksDB: Kafka changelogs must restore both
      // stores.
      try (var files = Files.walk(state)) {
        for (Path p : files.sorted(Comparator.reverseOrder()).toList())
          if (!p.equals(state)) Files.delete(p);
      }
      KafkaStreams replacement = start(bootstrap, app);
      try {
        send(producer, 1, 2); // same identity must not count twice after restoration
        send(producer, 2, 3);
        var summary = awaitCount(consumer, 2);
        assertEquals(5, summary.get("totalQuantity").asInt());
        assertEquals(12500, summary.get("totalCents").asInt());
        long until = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < until)
          for (var r : consumer.poll(Duration.ofMillis(100)))
            assertTrue(EventCodec.JSON.readTree(r.value()).get("orderCount").asInt() <= 2);
      } finally {
        replacement.close(
            new KafkaStreams.CloseOptions().timeout(Duration.ofSeconds(30)).leaveGroup(true));
      }
    }
  }

  private KafkaStreams start(String bootstrap, String app) throws Exception {
    var streams =
        new KafkaStreams(
            new OrderSummaryTopology().build(),
            AnalyticsApplication.configuration(bootstrap, app, state.toString()));
    streams.start();
    long until = System.nanoTime() + Duration.ofSeconds(60).toNanos();
    while (streams.state() != KafkaStreams.State.RUNNING && System.nanoTime() < until)
      Thread.sleep(100);
    assertEquals(KafkaStreams.State.RUNNING, streams.state());
    return streams;
  }

  private void send(KafkaProducer<String, String> p, int id, int qty) throws Exception {
    p.send(
            new ProducerRecord<>(
                OrderSummaryTopology.INPUT,
                "order",
                OrderSummaryTopologyTest.event(
                    OrderSummaryTopologyTest.id(id), "demo", EVENT_SECONDS, qty)))
        .get();
  }

  private com.fasterxml.jackson.databind.JsonNode awaitCount(
      KafkaConsumer<String, String> c, int count) throws Exception {
    long until = System.nanoTime() + Duration.ofSeconds(60).toNanos();
    while (System.nanoTime() < until)
      for (var r : c.poll(Duration.ofMillis(200))) {
        var n = EventCodec.JSON.readTree(r.value());
        assertTrue(n.get("orderCount").asInt() <= count, "duplicate inflated count");
        if (n.get("orderCount").asInt() == count) return n;
      }
    throw new AssertionError("No summary with count " + count);
  }
}
