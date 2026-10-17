package dev.reference.analytics;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;
import org.apache.kafka.streams.KafkaStreams;

/** Internal probe/metrics listener, deliberately independent of business APIs. */
final class MetricsServer implements AutoCloseable {
  private final HttpServer server;

  MetricsServer(KafkaStreams streams, OrderSummaryTopology topology, int port) throws IOException {
    this(streams, topology, port, () -> streams.state() == KafkaStreams.State.RUNNING);
  }

  MetricsServer(
      KafkaStreams streams, OrderSummaryTopology topology, int port, BooleanSupplier ready)
      throws IOException {
    server = HttpServer.create(new InetSocketAddress(port), 0);
    server.createContext(
        "/metrics",
        exchange -> {
          var bytes = render(streams, topology).getBytes(StandardCharsets.UTF_8);
          exchange
              .getResponseHeaders()
              .set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
          exchange.sendResponseHeaders(200, bytes.length);
          try (var out = exchange.getResponseBody()) {
            out.write(bytes);
          }
        });
    server.createContext(
        "/live",
        exchange -> {
          var state = streams.state();
          boolean failed =
              state == KafkaStreams.State.ERROR || state == KafkaStreams.State.PENDING_ERROR;
          exchange.sendResponseHeaders(failed ? 503 : 200, -1);
          exchange.close();
        });
    server.createContext(
        "/health",
        exchange -> {
          exchange.sendResponseHeaders(ready.getAsBoolean() ? 200 : 503, -1);
          exchange.close();
        });
  }

  static String render(KafkaStreams streams, OrderSummaryTopology topology) {
    double lag =
        streams.metrics().entrySet().stream()
            .filter(e -> e.getKey().name().equals("records-lag-max"))
            .map(e -> e.getValue().metricValue())
            .filter(Number.class::isInstance)
            .mapToDouble(v -> ((Number) v).doubleValue())
            .filter(Double::isFinite)
            .max()
            .orElse(0);
    return "# TYPE analytics_late_events_total counter\nanalytics_late_events_total "
        + topology.lateEvents.sum()
        + "\n# TYPE analytics_invalid_events_total counter\nanalytics_invalid_events_total "
        + topology.invalidEvents.sum()
        + "\n# TYPE analytics_records_lag_max gauge\nanalytics_records_lag_max "
        + lag
        + "\n";
  }

  int port() {
    return server.getAddress().getPort();
  }

  void start() {
    server.start();
  }

  public void close() {
    server.stop(0);
  }
}
