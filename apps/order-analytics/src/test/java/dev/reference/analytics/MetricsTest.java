package dev.reference.analytics;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Map;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
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
      assertFalse(rendered.contains("analytics_records_lag_max"));
    }
  }

  @Test
  void distinguishesMissingInvalidZeroAndPositiveLag() {
    var topology = new OrderSummaryTopology();
    assertFalse(MetricsServer.render(Map.of(), topology).contains("analytics_records_lag_max"));
    for (Object invalid : new Object[] {Double.NaN, Double.POSITIVE_INFINITY, "unavailable"}) {
      assertFalse(
          MetricsServer.render(Map.of(lagName("0"), metric(lagName("0"), invalid)), topology)
              .contains("analytics_records_lag_max"));
    }
    assertTrue(
        MetricsServer.render(Map.of(lagName("0"), metric(lagName("0"), 0.0)), topology)
            .contains("analytics_records_lag_max 0.0\n"));
    assertTrue(
        MetricsServer.render(
                Map.of(lagName("0"), metric(lagName("0"), 3.0),
                    lagName("1"), metric(lagName("1"), 7.0)), topology)
            .contains("analytics_records_lag_max 7.0\n"));
    var other = new MetricName("unrelated", "test", "", Map.of());
    assertFalse(
        MetricsServer.render(Map.of(other, metric(other, 99.0)), topology)
            .contains("analytics_records_lag_max"));
  }

  private static MetricName lagName(String partition) {
    return new MetricName("records-lag-max", "consumer-fetch-manager-metrics", "",
        Map.of("partition", partition));
  }

  private static Metric metric(MetricName name, Object value) {
    return new Metric() {
      public MetricName metricName() { return name; }
      public Object metricValue() { return value; }
    };
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
