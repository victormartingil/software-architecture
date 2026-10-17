package dev.reference.analytics;

import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.*;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.*;

/** Counts created orders by tenant, product and event-time window; refunds are not subtracted. */
public final class OrderSummaryTopology {
  public static final String INPUT = "orders.placed.v1";
  public static final String OUTPUT = "orders.summary.v1";
  public static final String DLT = "orders.analytics.v1.dlt";
  public static final String SEEN = "seen-events";
  private static final long RETENTION = Duration.ofDays(7).toMillis();
  static final Duration MAX_FUTURE_SKEW = Duration.ofSeconds(30);
  private final Clock clock;
  public final LongAdder lateEvents = new LongAdder();
  public final LongAdder invalidEvents = new LongAdder();

  public OrderSummaryTopology() { this(Clock.systemUTC()); }

  OrderSummaryTopology(Clock clock) { this.clock = java.util.Objects.requireNonNull(clock); }

  private record ClassifiedEvent(String payload, boolean accepted) {}

  private com.fasterxml.jackson.databind.JsonNode supportedEvent(String value) {
    var event = EventCodec.parse(value);
    if (event == null || Instant.parse(event.get("occurredAt").asText())
        .isAfter(clock.instant().plus(MAX_FUTURE_SKEW))) return null;
    return event;
  }

  public Topology build() {
    StreamsBuilder b = new StreamsBuilder();
    b.addStateStore(
        Stores.keyValueStoreBuilder(
            Stores.persistentKeyValueStore(SEEN), Serdes.String(), Serdes.Long()));
    KStream<String, String> raw =
        b.stream(
            INPUT,
            Consumed.with(Serdes.String(), Serdes.String())
                .withTimestampExtractor(
                    (record, previous) -> {
                      var event = supportedEvent((String) record.value());
                      return event == null
                          // Neither the payload nor Kafka timestamp of bad data may close windows.
                          ? Math.max(0, previous)
                          : Instant.parse(event.get("occurredAt").asText()).toEpochMilli();
                    }));
    // Classify once: a clock boundary cannot send the same record down both routes.
    KStream<String, ClassifiedEvent> classified = raw.mapValues(
        value -> new ClassifiedEvent(value, supportedEvent(value) != null));
    classified.filter((key, event) -> !event.accepted())
        .mapValues(ClassifiedEvent::payload)
        .processValues(() -> new ContextualFixedKeyProcessor<String, String, String>() {
          public void process(FixedKeyRecord<String, String> record) {
            invalidEvents.increment();
            // Quarantine retention starts now, independently of the untrusted event timestamp.
            context().forward(record.withTimestamp(clock.millis()));
          }
        }, Named.as("quarantine"))
        .to(DLT, Produced.with(Serdes.String(), Serdes.String()));
    // Repartition before dedup: even duplicate identities arriving on different input partitions
    // share a store.
    KStream<String, String> unique =
        classified.filter((key, event) -> event.accepted())
            .mapValues(ClassifiedEvent::payload)
            .selectKey(
                (k, v) ->
                    java.util
                        .UUID
                        .fromString(EventCodec.parse(v).get("eventId").asText())
                        .toString())
            .repartition(
                Repartitioned.with(Serdes.String(), Serdes.String()).withName("event-identity"))
            .process(Deduplicate::new, Named.as("deduplicate"), SEEN);
    unique
        .selectKey(
            (k, v) -> {
              var n = EventCodec.parse(v);
              // JSON array encoding avoids delimiter collisions between tenant and sku.
              return EventCodec.encode(
                  java.util.List.of(n.get("tenantId").asText(), n.get("data").get("sku").asText()));
            })
        .repartition(Repartitioned.with(Serdes.String(), Serdes.String()).withName("tenant-sku"))
        .processValues(() -> new RejectLate(lateEvents), Named.as("reject-late"))
        .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
        .windowedBy(TimeWindows.ofSizeAndGrace(Duration.ofMinutes(1), Duration.ofSeconds(30)))
        .aggregate(
            () -> "{\"orderCount\":0,\"totalQuantity\":0,\"totalCents\":0}",
            (key, value, aggregate) -> {
              try {
                var a = EventCodec.JSON.readTree(aggregate);
                var d = EventCodec.parse(value).get("data");
                return EventCodec.encode(
                    Map.of(
                        "orderCount",
                        a.get("orderCount").bigIntegerValue().add(java.math.BigInteger.ONE),
                        "totalQuantity",
                        a.get("totalQuantity")
                            .bigIntegerValue()
                            .add(d.get("quantity").bigIntegerValue()),
                        "totalCents",
                        a.get("totalCents")
                            .bigIntegerValue()
                            .add(d.get("totalCents").bigIntegerValue())));
              } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
              }
            },
            Materialized
                .<String, String, WindowStore<org.apache.kafka.common.utils.Bytes, byte[]>>as(
                    "order-totals")
                .withKeySerde(Serdes.String())
                .withValueSerde(Serdes.String())
                .withRetention(Duration.ofDays(7))
                .withCachingDisabled())
        .toStream()
        .map(
            (window, totals) -> {
              try {
                var dimensions = EventCodec.JSON.readTree(window.key());
                var a = EventCodec.JSON.readTree(totals);
                return KeyValue.pair(
                    window.key() + "@" + window.window().start(),
                    EventCodec.encode(
                        Map.of(
                            "tenantId",
                            dimensions.get(0).asText(),
                            "sku",
                            dimensions.get(1).asText(),
                            "windowStart",
                            Instant.ofEpochMilli(window.window().start()).toString(),
                            "windowEnd",
                            Instant.ofEpochMilli(window.window().end()).toString(),
                            "orderCount",
                            a.get("orderCount").bigIntegerValue(),
                            "totalQuantity",
                            a.get("totalQuantity").bigIntegerValue(),
                            "totalCents",
                            a.get("totalCents").bigIntegerValue())));
              } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
              }
            })
        .to(OUTPUT, Produced.with(Serdes.String(), Serdes.String()));
    return b.build();
  }

  private static final class Deduplicate implements Processor<String, String, String, String> {
    private ProcessorContext<String, String> context;
    private KeyValueStore<String, Long> seen;

    public void init(ProcessorContext<String, String> context) {
      this.context = context;
      seen = context.getStateStore(SEEN);
      context.schedule(
          Duration.ofHours(1),
          PunctuationType.STREAM_TIME,
          now -> {
            try (var entries = seen.all()) {
              while (entries.hasNext()) {
                var e = entries.next();
                if (e.value < now - RETENTION) seen.delete(e.key);
              }
            }
          });
    }

    public void process(Record<String, String> record) {
      long timestamp =
          Instant.parse(EventCodec.parse(record.value()).get("occurredAt").asText()).toEpochMilli();
      Long previous = seen.get(record.key());
      if (previous != null && previous >= context.currentStreamTimeMs() - RETENTION) return;
      seen.put(record.key(), Math.max(timestamp, context.currentStreamTimeMs()));
      context.forward(record.withTimestamp(timestamp));
    }
  }

  private static final class RejectLate implements FixedKeyProcessor<String, String, String> {
    private final LongAdder count;
    private FixedKeyProcessorContext<String, String> context;
    private org.apache.kafka.common.metrics.Sensor sensor;

    RejectLate(LongAdder count) {
      this.count = count;
    }

    public void init(FixedKeyProcessorContext<String, String> c) {
      context = c;
      sensor =
          c.metrics()
              .addSensor(
                  "late-orders-" + c.taskId(),
                  org.apache.kafka.common.metrics.Sensor.RecordingLevel.INFO);
      sensor.add(
          new org.apache.kafka.common.MetricName(
              "late-orders-total",
              "order-analytics",
              "Discarded events outside grace",
              Map.of("task", c.taskId().toString())),
          new org.apache.kafka.common.metrics.stats.CumulativeCount());
    }

    public void process(FixedKeyRecord<String, String> r) {
      long close = (r.timestamp() / 60000 + 1) * 60000 + 30000;
      if (context.currentStreamTimeMs() >= close) {
        count.increment();
        sensor.record();
        return;
      }
      context.forward(r);
    }
  }
}
