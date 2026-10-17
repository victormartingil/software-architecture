package com.reference.analytics;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.apache.kafka.common.serialization.*;
import org.apache.kafka.streams.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class OrderSummaryTopologyTest {
  @TempDir Path dir;

  static String event(String id, String tenant, long seconds, int quantity) {
    return EventCodec.encode(
        Map.of(
            "eventId",
            id,
            "eventType",
            "OrderPlaced",
            "schemaVersion",
            1,
            "occurredAt",
            Instant.ofEpochSecond(seconds).toString(),
            "aggregateId",
            "11111111-1111-4111-8111-111111111111",
            "tenantId",
            tenant,
            "correlationId",
            "test-correlation",
            "data",
            Map.of(
                "orderId",
                "11111111-1111-4111-8111-111111111111",
                "sku",
                "BOOK-1",
                "quantity",
                quantity,
                "totalCents",
                quantity * 2500)));
  }

  static String id(int n) {
    return String.format("00000000-0000-4000-8000-%012d", n);
  }

  @Test
  void aggregatesAndSeparatesTenantsWithoutDuplicates() throws Exception {
    var topology = new OrderSummaryTopology();
    try (var driver =
        new TopologyTestDriver(
            topology.build(),
            AnalyticsApplication.configuration("unused:9092", "test", dir.toString()))) {
      var in =
          driver.createInputTopic(
              OrderSummaryTopology.INPUT, new StringSerializer(), new StringSerializer());
      var out =
          driver.createOutputTopic(
              OrderSummaryTopology.OUTPUT, new StringDeserializer(), new StringDeserializer());
      in.pipeInput("order", event(id(1), "demo", 20, 2));
      in.pipeInput("order", event(id(1), "demo", 20, 2));
      in.pipeInput("order", event(id(2), "demo", 10, 3));
      in.pipeInput("order", event(id(3), "other", 30, 1));
      var records = out.readValuesToList();
      assertEquals(3, records.size());
      var aggregate = EventCodec.JSON.readTree(records.get(1));
      assertEquals(2, aggregate.get("orderCount").asInt());
      assertEquals(5, aggregate.get("totalQuantity").asInt());
      assertEquals(12500, aggregate.get("totalCents").asInt());
      assertEquals("1970-01-01T00:00:00Z", aggregate.get("windowStart").asText());
      assertEquals("other", EventCodec.JSON.readTree(records.get(2)).get("tenantId").asText());
    }
  }

  @Test
  void lateEventsAreCountedAndInvalidEventsQuarantined() {
    var topology = new OrderSummaryTopology();
    try (var driver =
        new TopologyTestDriver(
            topology.build(),
            AnalyticsApplication.configuration("unused:9092", "test", dir.toString()))) {
      var in =
          driver.createInputTopic(
              OrderSummaryTopology.INPUT, new StringSerializer(), new StringSerializer());
      var out =
          driver.createOutputTopic(
              OrderSummaryTopology.OUTPUT, new StringDeserializer(), new StringDeserializer());
      var dlt =
          driver.createOutputTopic(
              OrderSummaryTopology.DLT, new StringDeserializer(), new StringDeserializer());
      in.pipeInput("order", event(id(1), "demo", 95, 1));
      in.pipeInput("order", event(id(2), "demo", 20, 1));
      assertEquals(1, out.readValuesToList().size());
      assertEquals(1, topology.lateEvents.sum());
      in.pipeInput("bad", "{broken");
      assertEquals("{broken", dlt.readValue());
      in.pipeInput("bad", event(id(3), "demo", 100, 0));
      assertFalse(dlt.isEmpty());
    }
  }

  @Test
  void graceBoundaryAndSevenDayDedupRetention() {
    var topology = new OrderSummaryTopology();
    try (var driver =
        new TopologyTestDriver(
            topology.build(),
            AnalyticsApplication.configuration("unused:9092", "retention", dir.toString()))) {
      var in =
          driver.createInputTopic(
              OrderSummaryTopology.INPUT, new StringSerializer(), new StringSerializer());
      var out =
          driver.createOutputTopic(
              OrderSummaryTopology.OUTPUT, new StringDeserializer(), new StringDeserializer());
      in.pipeInput("order", event(id(1), "demo", 89, 1));
      in.pipeInput("order", event(id(2), "demo", 20, 1)); // 29 seconds after end: accepted
      assertEquals(2, out.readValuesToList().size());
      in.pipeInput("order", event(id(3), "demo", 90, 1));
      in.pipeInput("order", event(id(4), "demo", 25, 1)); // exactly at grace boundary: closed
      assertEquals(1, topology.lateEvents.sum());
      in.pipeInput("order", event(id(5), "demo", 7 * 86400 + 4000, 1));
      assertNull(driver.getKeyValueStore(OrderSummaryTopology.SEEN).get(id(1)));
    }
  }

  @Test
  void uuidCaseDoesNotChangeEventIdentity() {
    var topology = new OrderSummaryTopology();
    try (var driver =
        new TopologyTestDriver(
            topology.build(),
            AnalyticsApplication.configuration("unused:9092", "uuid-case", dir.toString()))) {
      var in =
          driver.createInputTopic(
              OrderSummaryTopology.INPUT, new StringSerializer(), new StringSerializer());
      var out =
          driver.createOutputTopic(
              OrderSummaryTopology.OUTPUT, new StringDeserializer(), new StringDeserializer());
      String identity = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
      in.pipeInput("order", event(identity, "demo", 20, 2));
      in.pipeInput("order", event(identity.toUpperCase(java.util.Locale.ROOT), "demo", 20, 2));
      assertEquals(1, out.readValuesToList().size());
    }
  }

  @Test
  void rejectsUnknownFieldsAndIdentityMismatch() throws Exception {
    var json = EventCodec.JSON.readTree(event(id(1), "demo", 20, 1));
    ((com.fasterxml.jackson.databind.node.ObjectNode) json).put("secret", "no");
    assertNull(EventCodec.parse(json.toString()));
    json = EventCodec.JSON.readTree(event(id(1), "demo", 20, 1));
    ((com.fasterxml.jackson.databind.node.ObjectNode) json.get("data")).put("orderId", id(9));
    assertNull(EventCodec.parse(json.toString()));
  }
  @Test
  void sharedUuidCasesRejectInvalidEventsWithoutSummary() throws Exception {
    var fixtures = java.nio.file.Path.of("../../contracts/events/fixtures");
    for (var testCase : EventCodec.JSON.readTree(fixtures.resolve("uuid-cases.json").toFile()).get("cases")) {
      var event = (com.fasterxml.jackson.databind.node.ObjectNode)
          EventCodec.JSON.readTree(fixtures.resolve("orders.placed.v1.json").toFile());
      event.set("eventId", testCase.get("eventId"));
      event.set("aggregateId", testCase.get("aggregateId"));
      ((com.fasterxml.jackson.databind.node.ObjectNode) event.get("data"))
          .set("orderId", testCase.get("orderId"));
      var topology = new OrderSummaryTopology();
      try (var driver = new TopologyTestDriver(topology.build(),
          AnalyticsApplication.configuration("unused:9092", "uuid-contract", dir.toString()))) {
        var in = driver.createInputTopic(OrderSummaryTopology.INPUT,
            new StringSerializer(), new StringSerializer());
        var out = driver.createOutputTopic(OrderSummaryTopology.OUTPUT,
            new StringDeserializer(), new StringDeserializer());
        var dlt = driver.createOutputTopic(OrderSummaryTopology.DLT,
            new StringDeserializer(), new StringDeserializer());
        String payload = event.toString();
        in.pipeInput(testCase.get("orderId").asText(), payload);
        if (testCase.get("accepted").asBoolean()) {
          assertFalse(out.isEmpty(), testCase.get("name").asText());
          assertTrue(dlt.isEmpty(), testCase.get("name").asText());
        } else {
          assertTrue(out.isEmpty(), testCase.get("name").asText());
          assertEquals(payload, dlt.readValue(), testCase.get("name").asText());
          assertTrue(dlt.isEmpty());
        }
      }
    }
  }

}
