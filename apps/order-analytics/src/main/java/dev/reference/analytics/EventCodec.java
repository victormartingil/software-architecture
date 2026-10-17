package dev.reference.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** A strict integration boundary; invalid input is data to quarantine, not a process crash. */
final class EventCodec {
  static final ObjectMapper JSON = new ObjectMapper();

  private EventCodec() {}

  static JsonNode parse(String value) {
    try {
      JsonNode n = JSON.readTree(value);
      fields(
          n,
          Set.of(
              "eventId",
              "eventType",
              "schemaVersion",
              "occurredAt",
              "aggregateId",
              "tenantId",
              "correlationId",
              "data"));
      uuid(n, "eventId");
      uuid(n, "aggregateId");
      text(n, "tenantId");
      text(n, "correlationId");
      if (!n.path("eventType").asText().equals("OrderPlaced")
          || !n.path("schemaVersion").isInt()
          || n.get("schemaVersion").intValue() != 1) throw new IllegalArgumentException();
      if (Instant.parse(text(n, "occurredAt")).toEpochMilli() < 0)
        throw new IllegalArgumentException();
      JsonNode d = n.path("data");
      fields(d, Set.of("orderId", "sku", "quantity", "totalCents"));
      uuid(d, "orderId");
      text(d, "sku");
      if (!UUID.fromString(d.get("orderId").asText())
          .equals(UUID.fromString(n.get("aggregateId").asText())))
        throw new IllegalArgumentException();
      if (!d.path("quantity").isIntegralNumber()
          || !d.get("quantity").canConvertToInt()
          || d.get("quantity").asInt() < 1
          || d.get("quantity").asInt() > 100) throw new IllegalArgumentException();
      if (!d.path("totalCents").isIntegralNumber()
          || !d.get("totalCents").canConvertToLong()
          || d.get("totalCents").asLong() < 0) throw new IllegalArgumentException();
      return n;
    } catch (Exception invalid) {
      return null;
    }
  }

  private static void fields(JsonNode n, Set<String> fields) {
    if (n == null || !n.isObject() || n.size() != fields.size())
      throw new IllegalArgumentException();
    fields.forEach(
        f -> {
          if (!n.hasNonNull(f)) throw new IllegalArgumentException();
        });
  }

  private static String text(JsonNode n, String field) {
    JsonNode v = n.path(field);
    if (!v.isTextual() || v.asText().isBlank() || v.asText().length() > 200)
      throw new IllegalArgumentException();
    return v.asText();
  }

  private static void uuid(JsonNode n, String field) {
    String v = text(n, field);
    if (!UUID.fromString(v).toString().equals(v.toLowerCase(java.util.Locale.ROOT)))
      throw new IllegalArgumentException();
  }

  static String encode(Object value) {
    try {
      return JSON.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to serialize internal result", e);
    }
  }
}
