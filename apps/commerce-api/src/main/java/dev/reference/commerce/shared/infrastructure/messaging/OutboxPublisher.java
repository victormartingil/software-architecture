package dev.reference.commerce.shared.infrastructure.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.events.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {
  private final io.micrometer.tracing.Tracer tracer;
  private final JdbcTemplate db;
  private final KafkaTemplate<String, String> kafka;

  public OutboxPublisher(
      JdbcTemplate db,
      KafkaTemplate<String, String> kafka,
      MeterRegistry metrics,
      io.micrometer.tracing.Tracer tracer) {
    this.db = db;
    this.kafka = kafka;
    this.tracer = tracer;
    metrics.gauge(
        "commerce.outbox.pending",
        db,
        d ->
            d.queryForObject("SELECT count(*) FROM outbox WHERE published_at IS NULL", Long.class));
  }

  @Scheduled(
      initialDelayString = "${OUTBOX_INITIAL_DELAY_MS:1000}",
      fixedDelayString = "${OUTBOX_DELAY_MS:1000}")
  @Transactional
  public void publish() {
    var rows =
        db.queryForList(
            "SELECT event_id,aggregate_id,payload,trace_id,parent_span_id FROM outbox WHERE"
                + " published_at IS NULL ORDER BY event_id LIMIT 20 FOR UPDATE SKIP LOCKED");
    for (var row : rows) {
      db.update("UPDATE outbox SET attempts=attempts+1 WHERE event_id=?", row.get("event_id"));
      try {
        var parent =
            row.get("trace_id") == null
                ? null
                : tracer
                    .traceContextBuilder()
                    .traceId(row.get("trace_id").toString())
                    .spanId(row.get("parent_span_id").toString())
                    .sampled(true)
                    .build();
        var span =
            (parent == null ? tracer.spanBuilder() : tracer.spanBuilder().setParent(parent))
                .name("outbox.publish")
                .start();
        try (var scope = tracer.withSpan(span)) {
          kafka
              .send(
                  "orders.placed.v1",
                  row.get("aggregate_id").toString(),
                  row.get("payload").toString())
              .get(12, TimeUnit.SECONDS);
        } finally {
          span.end();
        }
        db.update("UPDATE outbox SET published_at=now() WHERE event_id=?", row.get("event_id"));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      } catch (Exception e) {
        org.slf4j.LoggerFactory.getLogger(getClass())
            .warn("Outbox delivery deferred eventId={}", row.get("event_id"));
        break;
      }
    }
  }
}
