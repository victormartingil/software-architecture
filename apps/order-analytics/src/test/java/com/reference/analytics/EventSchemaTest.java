package com.reference.analytics;

import static org.junit.jupiter.api.Assertions.*;

import com.networknt.schema.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EventSchemaTest {
  @Test
  void fixturesMatchVersionedSchemas() throws Exception {
    Path contracts = Path.of("../../contracts/events");
    var factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    for (String name : java.util.List.of("orders.placed.v1", "orders.summary.v1")) {
      var schema =
          factory.getSchema(
              EventCodec.JSON.readTree(contracts.resolve(name + ".schema.json").toFile()));
      var fixture =
          EventCodec.JSON.readTree(contracts.resolve("fixtures/" + name + ".json").toFile());
      assertTrue(schema.validate(fixture).isEmpty(), schema.validate(fixture).toString());
      ((com.fasterxml.jackson.databind.node.ObjectNode) fixture).put("unexpected", true);
      assertFalse(schema.validate(fixture).isEmpty());
    }
  }

  @Test
  void realTopologyOutputMatchesSchema() throws Exception {
    var contracts = Path.of("../../contracts/events");
    var schema =
        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(
                EventCodec.JSON.readTree(
                    contracts.resolve("orders.summary.v1.schema.json").toFile()));
    Path state = java.nio.file.Files.createTempDirectory("analytics-schema");
    try (var driver =
        new org.apache.kafka.streams.TopologyTestDriver(
            new OrderSummaryTopology().build(),
            AnalyticsApplication.configuration("unused:9092", "schema", state.toString()))) {
      var in =
          driver.createInputTopic(
              OrderSummaryTopology.INPUT,
              new org.apache.kafka.common.serialization.StringSerializer(),
              new org.apache.kafka.common.serialization.StringSerializer());
      var out =
          driver.createOutputTopic(
              OrderSummaryTopology.OUTPUT,
              new org.apache.kafka.common.serialization.StringDeserializer(),
              new org.apache.kafka.common.serialization.StringDeserializer());
      in.pipeInput(
          "order",
          java.nio.file.Files.readString(contracts.resolve("fixtures/orders.placed.v1.json")));
      var actual = EventCodec.JSON.readTree(out.readValue());
      assertTrue(schema.validate(actual).isEmpty(), schema.validate(actual).toString());
      assertEquals(
          EventCodec.JSON.readTree(contracts.resolve("fixtures/orders.summary.v1.json").toFile()),
          actual);
    } finally {
      try (var paths = java.nio.file.Files.walk(state)) {
        for (var p : paths.sorted(java.util.Comparator.reverseOrder()).toList())
          java.nio.file.Files.delete(p);
      }
    }
  }
}
