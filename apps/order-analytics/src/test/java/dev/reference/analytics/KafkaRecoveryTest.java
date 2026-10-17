package dev.reference.analytics;

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
      StreamsLifecycle first = start(bootstrap, app);
      try {
        send(producer, 0, 1, 2);
        send(producer, 1, 1, 2);
        assertEquals(2, awaitCount(consumer, 1).get("totalQuantity").asInt());
        assertNoInflatedUpdates(consumer, 1);
      } finally {
        first.close();
        assertFalse(first.isReady());
        assertFalse(Files.exists(state.resolve("ready")));
      }
      // Simulate a replacement node with no local RocksDB: Kafka changelogs must restore both
      // stores.
      try (var files = Files.walk(state)) {
        for (Path p : files.sorted(Comparator.reverseOrder()).toList())
          if (!p.equals(state)) Files.delete(p);
      }
      StreamsLifecycle replacement = start(bootstrap, app);
      try {
        send(producer, 0, 1, 2); // same identity on both original partitions after restoration
        send(producer, 1, 1, 2);
        send(producer, 0, 2, 3);
        send(producer, 1, 2, 3);
        var summary = awaitCount(consumer, 2);
        assertEquals(5, summary.get("totalQuantity").asInt());
        assertEquals(12500, summary.get("totalCents").asInt());
        assertNoInflatedUpdates(consumer, 2);
      } finally {
        replacement.close();
        assertFalse(replacement.isReady());
        assertFalse(Files.exists(state.resolve("ready")));
      }
    }
  }

  private void assertNoInflatedUpdates(KafkaConsumer<String, String> consumer, int count)
      throws Exception {
    long until = System.nanoTime() + Duration.ofSeconds(2).toNanos();
    while (System.nanoTime() < until)
      for (var record : consumer.poll(Duration.ofMillis(100)))
        assertTrue(
            EventCodec.JSON.readTree(record.value()).get("orderCount").asInt() <= count,
            "duplicate from a different partition inflated count");
  }

  private StreamsLifecycle start(String bootstrap, String app) throws Exception {
    var streams =
        new KafkaStreams(
            new OrderSummaryTopology().build(),
            AnalyticsApplication.configuration(bootstrap, app, state.toString()));
    var lifecycle = new StreamsLifecycle(streams, state.resolve("ready"), Duration.ofSeconds(20));
    try {
      lifecycle.start(Duration.ofSeconds(60));
      assertTrue(lifecycle.isReady());
      assertTrue(Files.exists(state.resolve("ready")));
      return lifecycle;
    } catch (Exception failure) {
      lifecycle.close();
      throw failure;
    }
  }

  private void send(KafkaProducer<String, String> p, int partition, int id, int qty)
      throws Exception {
    p.send(
            new ProducerRecord<>(
                OrderSummaryTopology.INPUT,
                partition,
                "order",
                OrderSummaryTopologyTest.event(
                    OrderSummaryTopologyTest.id(id), "demo", EVENT_SECONDS, qty)))
        .get();
  }

  private com.fasterxml.jackson.databind.JsonNode awaitCount(
      KafkaConsumer<String, String> c, int count) throws Exception {
    long until = System.nanoTime() + Duration.ofSeconds(60).toNanos();
    while (System.nanoTime() < until) {
      com.fasterxml.jackson.databind.JsonNode matching = null;
      for (var r : c.poll(Duration.ofMillis(200))) {
        var n = EventCodec.JSON.readTree(r.value());
        assertTrue(n.get("orderCount").asInt() <= count, "duplicate inflated count");
        if (n.get("orderCount").asInt() == count) matching = n;
      }
      // Inspect the entire poll batch: a later update must not be discarded on early return.
      if (matching != null) return matching;
    }
    throw new AssertionError("No summary with count " + count);
  }
}
