package com.reference.analytics;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.apache.kafka.streams.KafkaStreams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MetricsTest {
  @TempDir Path state;

  @Test
  void exportsPrometheusCountersAndLag() {
    var topology = new OrderSummaryTopology();
    topology.lateEvents.add(2);
    topology.invalidEvents.increment();
    try (var streams =
        new KafkaStreams(
            topology.build(),
            AnalyticsApplication.configuration("localhost:1", "metrics", state.toString()))) {
      String rendered = MetricsServer.render(streams, topology);
      assertTrue(rendered.contains("analytics_late_events_total 2\n"));
      assertTrue(rendered.contains("analytics_invalid_events_total 1\n"));
      assertTrue(rendered.contains("analytics_records_lag_max 0.0\n"));
    }
  }

  @Test
  void probesDistinguishProcessLivenessFromStreamReadiness() throws Exception {
    var topology = new OrderSummaryTopology();
    try (var streams =
            new KafkaStreams(
                topology.build(),
                AnalyticsApplication.configuration("localhost:1", "probes", state.toString()));
        var server = new MetricsServer(streams, topology, 0);
        var http = java.net.http.HttpClient.newHttpClient()) {
      server.start();
      for (var route : java.util.Map.of("/live", 200, "/health", 503, "/metrics", 200).entrySet()) {
        var request =
            java.net.http.HttpRequest.newBuilder(
                    java.net.URI.create("http://localhost:" + server.port() + route.getKey()))
                .timeout(java.time.Duration.ofSeconds(3))
                .build();
        assertEquals(
            route.getValue().intValue(),
            http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
      }
    }
  }
}
