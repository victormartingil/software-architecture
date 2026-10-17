package dev.reference.commerce.shared.infrastructure;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditConsumerTest {
  @Test
  void sharedUuidCasesAreValidatedBeforePersistence() throws Exception {
    var json = new ObjectMapper();
    var contracts = Path.of("../../contracts/events/fixtures");
    for (var testCase : json.readTree(contracts.resolve("uuid-cases.json").toFile()).get("cases")) {
      var event = (ObjectNode) json.readTree(contracts.resolve("orders.placed.v1.json").toFile());
      event.set("eventId", testCase.get("eventId"));
      event.set("aggregateId", testCase.get("aggregateId"));
      ((ObjectNode) event.get("data")).set("orderId", testCase.get("orderId"));
      var db = mock(JdbcTemplate.class);
      var metrics = new SimpleMeterRegistry();
      var consumer = new AuditConsumer(db, json, metrics);
      if (testCase.get("accepted").asBoolean()) {
        when(db.update(anyString(), any(Object[].class))).thenReturn(1);
        consumer.consume(event.toString());
        verify(db).update("INSERT INTO inbox(event_id) VALUES (?) ON CONFLICT DO NOTHING",
            UUID.fromString(testCase.get("eventId").asText()));
        verify(db).update("INSERT INTO audit_orders(event_id,order_id,tenant) VALUES (?,?,?)",
            UUID.fromString(testCase.get("eventId").asText()),
            UUID.fromString(testCase.get("orderId").asText()), "demo");
        assertThat(metrics.counter("commerce.audit.processed").count()).isEqualTo(1);
      } else {
        assertThatThrownBy(() -> consumer.consume(event.toString()))
            .as(testCase.get("name").asText()).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(db);
        assertThat(metrics.counter("commerce.audit.processed").count()).isZero();
      }
      metrics.close();
    }
  }
}
