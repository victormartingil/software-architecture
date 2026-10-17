package dev.reference.analytics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;

/** Coordinates readiness and bounded lifecycle; never closes from a Streams callback. */
final class StreamsLifecycle implements AutoCloseable {
  private final KafkaStreams streams;
  private final Path readyFile;
  private final Duration closeTimeout;
  private final CompletableFuture<Void> running = new CompletableFuture<>();
  private boolean closing;
  private boolean ready;
  private boolean failed;
  private volatile RuntimeException closeFailure;

  StreamsLifecycle(KafkaStreams streams, Path readyFile, Duration closeTimeout) {
    if (closeTimeout.isNegative() || closeTimeout.isZero())
      throw new IllegalArgumentException("Close timeout must be positive");
    this.streams = streams;
    this.readyFile = readyFile;
    this.closeTimeout = closeTimeout;
    streams.setStateListener((state, previous) -> updateReadiness(state));
    streams.setUncaughtExceptionHandler(
        failure -> {
          fail(failure);
          return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_CLIENT;
        });
  }

  void start(Duration startupTimeout) throws Exception {
    if (startupTimeout.isNegative() || startupTimeout.isZero())
      throw new IllegalArgumentException("Startup timeout must be positive");
    synchronized (this) {
      if (closing) throw new IllegalStateException("Streams lifecycle is closing");
      Files.deleteIfExists(readyFile);
    }
    streams.start(); // This topology has no global stores: Kafka starts its threads asynchronously.
    running.get(startupTimeout.toMillis(), TimeUnit.MILLISECONDS);
  }

  synchronized boolean isReady() {
    return ready && !failed && !closing;
  }

  private synchronized void fail(Throwable failure) {
    failed = true;
    ready = false;
    running.completeExceptionally(failure);
    try {
      Files.deleteIfExists(readyFile);
    } catch (IOException cleanupFailure) {
      failure.addSuppressed(cleanupFailure);
    }
    System.getLogger(StreamsLifecycle.class.getName())
        .log(System.Logger.Level.ERROR, "Analytics readiness failed", failure);
  }

  private synchronized void updateReadiness(KafkaStreams.State state) {
    ready = false;
    if (state == KafkaStreams.State.ERROR || state == KafkaStreams.State.PENDING_ERROR) {
      fail(new IllegalStateException("Streams entered " + state));
      return;
    }
    try {
      if (state == KafkaStreams.State.RUNNING && !closing && !failed) {
        Files.writeString(readyFile, "running\n");
        ready = true;
        running.complete(null);
      } else {
        Files.deleteIfExists(readyFile);
      }
    } catch (IOException failure) {
      fail(failure);
    }
  }

  @Override
  public void close() {
    synchronized (this) {
      if (closing) {
        if (closeFailure != null) throw closeFailure;
        return;
      }
      closing = true;
      ready = false;
      running.completeExceptionally(new IllegalStateException("Streams lifecycle is closing"));
    }
    // Do not retain the listener's lock while Kafka waits for its worker threads.
    RuntimeException cleanupFailure = null;
    try {
      Files.deleteIfExists(readyFile);
    } catch (IOException failure) {
      cleanupFailure = new IllegalStateException("Cannot remove readiness file", failure);
    }
    try {
      if (!streams.close(closeTimeout)) {
        closeFailure = new IllegalStateException("Streams did not stop within " + closeTimeout);
        throw closeFailure;
      }
    } catch (RuntimeException failure) {
      closeFailure = failure;
      if (cleanupFailure != null) failure.addSuppressed(cleanupFailure);
      throw failure;
    }
    if (cleanupFailure != null) {
      closeFailure = cleanupFailure;
      throw cleanupFailure;
    }
  }
}
