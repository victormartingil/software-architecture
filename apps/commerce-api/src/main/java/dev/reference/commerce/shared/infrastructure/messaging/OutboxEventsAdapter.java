package dev.reference.commerce.shared.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.reference.commerce.shared.application.Events;
import java.time.Instant;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OutboxEventsAdapter implements Events {
  private final io.micrometer.tracing.Tracer tracer;
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public OutboxEventsAdapter(
      JdbcTemplate db, ObjectMapper objectMapper, io.micrometer.tracing.Tracer tracer) {
    this.db = db;
    json = objectMapper;
    this.tracer = tracer;
  }

  public void placed(UUID id, String tenant, String sku, int quantity, int total) {
    UUID event = UUID.randomUUID();
    var envelope =
        Map.of(
            "eventId",
            event,
            "eventType",
            "OrderPlaced",
            "schemaVersion",
            1,
            "occurredAt",
            Instant.now(),
            "aggregateId",
            id,
            "tenantId",
            tenant,
            "correlationId",
            Objects.toString(MDC.get("correlationId"), UUID.randomUUID().toString()),
            "data",
            Map.of("orderId", id, "sku", sku, "quantity", quantity, "totalCents", total));
    try {
      var span = tracer.currentSpan();
      db.update(
          "INSERT INTO outbox(event_id,payload,aggregate_id,trace_id,parent_span_id) VALUES"
              + " (?,?,?,?,?)",
          event,
          json.writeValueAsString(envelope),
          id,
          span == null ? null : span.context().traceId(),
          span == null ? null : span.context().spanId());
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
