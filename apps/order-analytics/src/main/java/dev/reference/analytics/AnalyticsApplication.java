package dev.reference.analytics;

import java.nio.file.*;
import java.time.Duration;
import java.util.Properties;
import org.apache.kafka.streams.*;

public final class AnalyticsApplication {
  private AnalyticsApplication() {}

  public static Properties configuration(
      String bootstrapServers, String applicationId, String stateDirectory) {
    Properties properties = new Properties();
    properties.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
    properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    properties.put(StreamsConfig.STATE_DIR_CONFIG, stateDirectory);
    properties.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE_V2);
    properties.put(StreamsConfig.REPLICATION_FACTOR_CONFIG, 1);
    properties.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, 1);
    properties.put(StreamsConfig.ROCKSDB_CONFIG_SETTER_CLASS_CONFIG, BoundedRocksDb.class);
    properties.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, 0);
    properties.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 100);
    properties.put(StreamsConfig.consumerPrefix("auto.offset.reset"), "earliest");
    // Bound each poll and the buffered records per partition, not the total throughput.
    properties.put(StreamsConfig.consumerPrefix("max.poll.records"), 100);
    properties.put(StreamsConfig.BUFFERED_RECORDS_PER_PARTITION_CONFIG, 100);
    properties.put(StreamsConfig.producerPrefix("max.request.size"), 1048576);
    return properties;
  }

  public static void main(String[] args) throws Exception {
    Path readyFile = Path.of(env("READY_FILE", "/tmp/analytics-ready"));
    Files.deleteIfExists(readyFile);
    OrderSummaryTopology topology = new OrderSummaryTopology();
    KafkaStreams streams =
        new KafkaStreams(
            topology.build(),
            configuration(
                env("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
                env("APPLICATION_ID", "order-analytics-v1"),
                env("STATE_DIR", "/tmp/order-analytics")));
    var lifecycle = new StreamsLifecycle(streams, readyFile, Duration.ofSeconds(20));
    MetricsServer metrics;
    try {
      metrics =
          new MetricsServer(
              streams, topology, Integer.parseInt(env("METRICS_PORT", "9404")), lifecycle::isReady);
    } catch (Exception failure) {
      try {
        lifecycle.close();
      } catch (Exception closeFailure) {
        failure.addSuppressed(closeFailure);
      }
      throw failure;
    }
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    lifecycle.close();
                  } finally {
                    metrics.close();
                  }
                },
                "analytics-shutdown"));
    try {
      metrics.start();
      lifecycle.start(Duration.ofSeconds(60));
    } catch (Exception failure) {
      try {
        lifecycle.close();
      } catch (Exception closeFailure) {
        failure.addSuppressed(closeFailure);
      } finally {
        metrics.close();
      }
      throw failure;
    }
  }

  private static String env(String name, String fallback) {
    return System.getenv().getOrDefault(name, fallback);
  }
}
