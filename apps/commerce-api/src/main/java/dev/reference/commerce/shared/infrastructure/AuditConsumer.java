package dev.reference.commerce.shared.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.events.enabled", havingValue = "true", matchIfMissing = true)
public class AuditConsumer {
  private final io.micrometer.core.instrument.MeterRegistry metrics;
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public AuditConsumer(
      JdbcTemplate db, ObjectMapper j, io.micrometer.core.instrument.MeterRegistry metrics) {
    this.db = db;
    json = j;
    this.metrics = metrics;
  }

  @KafkaListener(topics = "orders.placed.v1")
  @Transactional
  public void consume(String payload) throws Exception {
    var e = json.readTree(payload);
    var data = e.path("data");
    if (!e.isObject() || e.size() != 8 || !data.isObject() || data.size() != 4)
      throw new IllegalArgumentException("Invalid event shape");
    for (String field :
        java.util.List.of(
            "eventId", "eventType", "occurredAt", "aggregateId", "tenantId", "correlationId"))
      if (!e.path(field).isTextual()
          || e.path(field).asText().isBlank()
          || e.path(field).asText().length() > 200)
        throw new IllegalArgumentException("Invalid event field");
    for (String field : java.util.List.of("orderId", "sku"))
      if (!data.path(field).isTextual()
          || data.path(field).asText().isBlank()
          || data.path(field).asText().length() > 200)
        throw new IllegalArgumentException("Invalid event data field");
    if (!e.path("eventType").asText().equals("OrderPlaced")
        || !e.path("schemaVersion").isIntegralNumber()
        || !e.path("schemaVersion").canConvertToInt()
        || e.path("schemaVersion").asInt() != 1
        || !data.path("quantity").isIntegralNumber()
        || !data.path("quantity").canConvertToInt()
        || data.path("quantity").asInt() < 1
        || data.path("quantity").asInt() > 100
        || !data.path("totalCents").isIntegralNumber()
        || !data.path("totalCents").canConvertToLong()
        || data.path("totalCents").asLong() < 0)
      throw new IllegalArgumentException("Invalid OrderPlaced envelope");
    java.time.Instant.parse(e.path("occurredAt").asText());
    UUID id = fullUuid(e.path("eventId").asText());
    UUID order = fullUuid(data.path("orderId").asText());
    if (!order.equals(fullUuid(e.path("aggregateId").asText())))
      throw new IllegalArgumentException("Aggregate mismatch");
    MDC.put("correlationId", e.path("correlationId").asText());
    try {
      if (db.update("INSERT INTO inbox(event_id) VALUES (?) ON CONFLICT DO NOTHING", id) == 1) {
        db.update(
            "INSERT INTO audit_orders(event_id,order_id,tenant) VALUES (?,?,?)",
            id,
            order,
            e.path("tenantId").asText());
        metrics.counter("commerce.audit.processed").increment();
        org.slf4j.LoggerFactory.getLogger(getClass())
            .info(
                "Order event audited eventId={} correlationId={}",
                id,
                e.path("correlationId").asText());
      } else {
        metrics.counter("commerce.audit.duplicates").increment();
      }
    } finally {
      MDC.remove("correlationId");
    }
  }

  private static UUID fullUuid(String value) {
    UUID id = UUID.fromString(value);
    if (!id.toString().equalsIgnoreCase(value))
      throw new IllegalArgumentException("Expected a complete UUID");
    return id;
  }
}
