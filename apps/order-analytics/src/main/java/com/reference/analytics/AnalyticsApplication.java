package com.reference.analytics;

import java.nio.file.*;
import java.time.Duration;
import java.util.Properties;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;

public final class AnalyticsApplication {
  private AnalyticsApplication() {}

  public static Properties configuration(String bootstrap, String appId, String stateDir) {
    Properties p = new Properties();
    p.put(StreamsConfig.APPLICATION_ID_CONFIG, appId);
    p.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
    p.put(StreamsConfig.STATE_DIR_CONFIG, stateDir);
    p.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE_V2);
    p.put(StreamsConfig.REPLICATION_FACTOR_CONFIG, 1);
    p.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, 1);
    p.put(StreamsConfig.ROCKSDB_CONFIG_SETTER_CLASS_CONFIG, BoundedRocksDb.class);
    p.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, 0);
    p.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 100);
    p.put(StreamsConfig.consumerPrefix("auto.offset.reset"), "earliest");
    p.put(StreamsConfig.producerPrefix("max.request.size"), 1048576);
    return p;
  }

  public static void main(String[] args) throws Exception {
    Path ready = Path.of(env("READY_FILE", "/tmp/analytics-ready"));
    Files.deleteIfExists(ready);
    OrderSummaryTopology topology = new OrderSummaryTopology();
    KafkaStreams streams =
        new KafkaStreams(
            topology.build(),
            configuration(
                env("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
                env("APPLICATION_ID", "order-analytics-v1"),
                env("STATE_DIR", "/tmp/order-analytics")));
    streams.setStateListener(
        (state, old) -> {
          try {
            if (state == KafkaStreams.State.RUNNING) Files.writeString(ready, "running\n");
            else Files.deleteIfExists(ready);
          } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot update readiness", e);
          }
        });
    streams.setUncaughtExceptionHandler(
        e -> StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_CLIENT);
    MetricsServer metrics =
        new MetricsServer(streams, topology, Integer.parseInt(env("METRICS_PORT", "9404")));
    metrics.start();
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  metrics.close();
                  streams.close(Duration.ofSeconds(20));
                }));
    streams.start();
  }

  private static String env(String name, String fallback) {
    return System.getenv().getOrDefault(name, fallback);
  }
}
