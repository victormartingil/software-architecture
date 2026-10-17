package dev.reference.analytics;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StreamsLifecycleTest {
  @TempDir Path directory;

  @Test
  void startsRemovesStaleReadinessAndClosesOnceWithinConfiguredDeadline() throws Exception {
    Path readyFile = directory.resolve("ready");
    Files.writeString(readyFile, "stale");
    var streams = new ControlledStreams();
    streams.onStart =
        () -> {
          assertFalse(Files.exists(readyFile));
          streams.transition(KafkaStreams.State.RUNNING);
        };
    var lifecycle = new StreamsLifecycle(streams, readyFile, Duration.ofMillis(250));
    lifecycle.start(Duration.ofSeconds(1));
    assertTrue(lifecycle.isReady());
    assertTrue(Files.exists(readyFile));
    streams.transition(KafkaStreams.State.REBALANCING);
    assertFalse(lifecycle.isReady());
    assertFalse(Files.exists(readyFile));
    streams.transition(KafkaStreams.State.RUNNING);
    lifecycle.close();
    lifecycle.close();
    assertEquals(1, streams.closes);
    assertEquals(Duration.ofMillis(250), streams.closeTimeout);
    assertFalse(lifecycle.isReady());
    assertFalse(Files.exists(readyFile));
    streams.transition(KafkaStreams.State.RUNNING); // A late callback cannot resurrect readiness.
    assertFalse(lifecycle.isReady());
    assertFalse(Files.exists(readyFile));
  }

  @Test
  void startupTimeoutAndStartFailureCanStillReleaseResources() throws Exception {
    var streams = new ControlledStreams();
    try (var lifecycle =
        new StreamsLifecycle(streams, directory.resolve("ready"), Duration.ofSeconds(1))) {
      assertThrows(TimeoutException.class, () -> lifecycle.start(Duration.ofMillis(1)));
      assertFalse(lifecycle.isReady());
    }
    assertEquals(1, streams.closes);
    var failingStreams = new ControlledStreams();
    failingStreams.onStart =
        () -> {
          throw new IllegalStateException("start failed");
        };
    try (var lifecycle =
        new StreamsLifecycle(failingStreams, directory.resolve("ready"), Duration.ofSeconds(1))) {
      assertThrows(IllegalStateException.class, () -> lifecycle.start(Duration.ofSeconds(1)));
    }
    assertEquals(1, failingStreams.closes);
  }

  @Test
  void falseCloseIsVisibleAndDoesNotLeaveReadiness() throws Exception {
    var streams = new ControlledStreams();
    streams.closeResult = false;
    streams.onStart = () -> streams.transition(KafkaStreams.State.RUNNING);
    var readyFile = directory.resolve("ready");
    var lifecycle = new StreamsLifecycle(streams, readyFile, Duration.ofMillis(1));
    lifecycle.start(Duration.ofSeconds(1));
    var failure = assertThrows(IllegalStateException.class, lifecycle::close);
    assertTrue(failure.getMessage().contains("did not stop within PT0.001S"));
    assertFalse(lifecycle.isReady());
    assertFalse(Files.exists(readyFile));
    assertThrows(IllegalStateException.class, lifecycle::close);
  }

  @Test
  void readinessFilesystemFailureAndErrorStateFailStartup() throws Exception {
    for (boolean filesystemFailure : new boolean[] {true, false}) {
      var streams = new ControlledStreams();
      var readyFile = directory.resolve("ready-" + filesystemFailure);
      streams.onStart =
          () -> {
            if (filesystemFailure) {
              try {
                Files.createDirectory(readyFile);
                Files.writeString(readyFile.resolve("obstruction"), "synthetic");
              } catch (java.io.IOException failure) {
                throw new RuntimeException(failure);
              }
            }
            streams.transition(
                filesystemFailure ? KafkaStreams.State.RUNNING : KafkaStreams.State.ERROR);
          };
      var lifecycle = new StreamsLifecycle(streams, readyFile, Duration.ofSeconds(1));
      assertThrows(ExecutionException.class, () -> lifecycle.start(Duration.ofSeconds(1)));
      assertFalse(lifecycle.isReady());
      if (filesystemFailure) {
        Files.delete(readyFile.resolve("obstruction"));
        Files.delete(readyFile);
      }
      lifecycle.close();
    }
  }

  @Test
  void httpReadinessTracksRunningRebalanceFailureAndShutdown() throws Exception {
    var streams = new ControlledStreams();
    streams.onStart = () -> streams.transition(KafkaStreams.State.RUNNING);
    var readyFile = directory.resolve("http-ready");
    try (var lifecycle = new StreamsLifecycle(streams, readyFile, Duration.ofSeconds(1));
        var metrics =
            new MetricsServer(streams, new OrderSummaryTopology(), 0, lifecycle::isReady);
        var http = java.net.http.HttpClient.newHttpClient()) {
      metrics.start();
      var request =
          java.net.http.HttpRequest.newBuilder(
                  java.net.URI.create("http://localhost:" + metrics.port() + "/health"))
              .timeout(Duration.ofSeconds(2))
              .build();
      assertEquals(
          503,
          http.send(request, java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode());
      lifecycle.start(Duration.ofSeconds(1));
      assertEquals(
          200,
          http.send(request, java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode());
      streams.transition(KafkaStreams.State.REBALANCING);
      assertEquals(
          503,
          http.send(request, java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode());
      streams.transition(KafkaStreams.State.RUNNING);
      streams.transition(KafkaStreams.State.ERROR);
      assertEquals(
          503,
          http.send(request, java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode());
      assertFalse(Files.exists(readyFile));
      lifecycle.close();
      assertEquals(
          503,
          http.send(request, java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode());
    }
  }

  @Test
  void consumerBatchAndPartitionBuffersAreBoundedWithoutChangingTransactions() {
    var properties =
        AnalyticsApplication.configuration("localhost:1", "configuration", directory.toString());
    var config = new StreamsConfig(properties);
    assertEquals(
        100, config.getMainConsumerConfigs("configuration", "consumer", 0).get("max.poll.records"));
    assertEquals(100, config.getInt(StreamsConfig.BUFFERED_RECORDS_PER_PARTITION_CONFIG));
    assertEquals(
        StreamsConfig.EXACTLY_ONCE_V2, config.getString(StreamsConfig.PROCESSING_GUARANTEE_CONFIG));
  }

  private final class ControlledStreams extends KafkaStreams {
    private StateListener listener;
    private Runnable onStart = () -> {};
    private Duration closeTimeout;
    private boolean closeResult = true;
    private int closes;

    ControlledStreams() {
      super(
          new OrderSummaryTopology().build(),
          AnalyticsApplication.configuration(
              "localhost:1", "lifecycle-" + java.util.UUID.randomUUID(), directory.toString()));
    }

    @Override
    public void setStateListener(StateListener listener) {
      this.listener = listener;
    }

    @Override
    public void start() {
      onStart.run();
    }

    void transition(State state) {
      listener.onChange(state, State.CREATED);
    }

    @Override
    public boolean close(Duration timeout) {
      closes++;
      closeTimeout = timeout;
      // Reproduce a Kafka callback on another thread while close waits for that thread.
      var callback = new Thread(() -> transition(State.PENDING_SHUTDOWN));
      callback.start();
      try {
        callback.join(1000);
        assertFalse(callback.isAlive(), "close retained the state-listener lock");
      } catch (InterruptedException failure) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(failure);
      }
      assertTrue(super.close(Duration.ofSeconds(1)));
      return closeResult;
    }
  }
}
