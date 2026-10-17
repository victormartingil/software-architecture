package dev.reference.commerce;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.reference.commerce.orders.application.model.*;
import dev.reference.commerce.orders.application.port.input.OrderUseCase;
import dev.reference.commerce.returns.application.model.*;
import dev.reference.commerce.returns.application.port.input.ReturnUseCase;
import dev.reference.commerce.shared.application.Idempotency;
import dev.reference.commerce.shared.domain.*;
import dev.reference.commerce.shared.infrastructure.JdbcIdempotencyAdapter;
import dev.reference.commerce.shared.infrastructure.OutboxPublisher;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
    properties = {
      "OUTBOX_INITIAL_DELAY_MS=3600000",
      "management.tracing.export.otlp.enabled=false",
      "management.logging.export.otlp.enabled=false",
      "management.otlp.metrics.export.enabled=false",
      "spring.kafka.producer.properties.delivery.timeout.ms=3000",
      "spring.kafka.producer.properties.request.timeout.ms=1000"
    })
@AutoConfigureMockMvc
@org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing
@Testcontainers
class CommerceIntegrationTest {
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.7-alpine");

  @Container
  static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.2"));

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
  }

  @Autowired OrderUseCase orders;
  @Autowired ReturnUseCase returns;
  @Autowired JdbcTemplate db;
  @Autowired MockMvc http;
  @Autowired OutboxPublisher publisher;
  @Autowired KafkaTemplate<String, String> producer;
  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean(name = "kafkaTemplate")
  KafkaTemplate<?, ?> observedProducer;
  @Autowired ObjectMapper json;
  @Autowired MutableClock clock;
  @Autowired io.micrometer.tracing.Tracer tracer;
  @Autowired io.micrometer.core.instrument.MeterRegistry metrics;
  static final Actor ALICE = new Actor("demo", "alice", false),
      MANAGER = new Actor("demo", "manager", true);

  @TestConfiguration
  static class Config {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }

  static class MutableClock extends Clock {
    Instant now = Instant.parse("2026-10-17T12:00:00Z");

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId z) {
      return this;
    }

    public Instant instant() {
      return now;
    }
  }

  @AfterAll
  static void stopKafkaBeforeContainers(
      @Autowired org.springframework.context.ConfigurableApplicationContext context) {
    context.getBean(org.springframework.kafka.config.KafkaListenerEndpointRegistry.class).stop();
    context.getBean(org.springframework.kafka.core.DefaultKafkaProducerFactory.class).destroy();
  }

  @BeforeEach
  void reset() {
    db.execute("TRUNCATE return_proposals,orders,idempotency,outbox,inbox,audit_orders CASCADE");
    db.update("UPDATE inventory SET stock=10,version=0");
    clock.now = Instant.parse("2026-10-17T12:00:00Z");
  }

  @Test
  void priceAndIdempotency() {
    var command = new CreateOrderCommand("BOOK-1", 2);
    var first = orders.create(ALICE, "same", command);
    assertThat(first.totalCents()).isEqualTo(5000);
    assertThat(orders.create(ALICE, "same", command)).isEqualTo(first);
    assertThat(db.queryForObject("SELECT stock FROM inventory WHERE tenant='demo'", Integer.class))
        .isEqualTo(8);
    assertThat(db.queryForObject("SELECT count(*) FROM outbox", Integer.class)).isEqualTo(1);
    assertThatThrownBy(() -> orders.create(ALICE, "same", new CreateOrderCommand("BOOK-1", 1)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("different");
  }

  @Test
  void subjectsWithDelimitersCannotShareCachedResultsEvenConcurrently() throws Exception {
    var firstActor = new Actor("demo", "alice", false);
    var secondActor = new Actor("demo", "alice:order:bob", false);
    var command = new CreateOrderCommand("BOOK-1", 1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      var first = pool.submit(() -> {
        gate.await();
        return orders.create(firstActor, "bob:order:retry", command);
      });
      var second = pool.submit(() -> {
        gate.await();
        return orders.create(secondActor, "retry", command);
      });
      gate.countDown();
      var firstOrder = first.get(15, TimeUnit.SECONDS);
      var secondOrder = second.get(15, TimeUnit.SECONDS);
      assertThat(firstOrder.id()).isNotEqualTo(secondOrder.id());
      assertThat(orders.create(firstActor, "bob:order:retry", command)).isEqualTo(firstOrder);
      assertThat(orders.create(secondActor, "retry", command)).isEqualTo(secondOrder);
      assertThatThrownBy(() -> orders.get(secondActor, firstOrder.id()))
          .hasMessageContaining("not found");
      assertThatThrownBy(() -> orders.get(firstActor, secondOrder.id()))
          .hasMessageContaining("not found");
    }
    assertThat(db.queryForObject("SELECT count(*) FROM orders", Integer.class)).isEqualTo(2);
    assertThat(db.queryForObject("SELECT stock FROM inventory WHERE tenant='demo'", Integer.class))
        .isEqualTo(8);
    assertThat(db.queryForObject("SELECT count(*) FROM idempotency WHERE scope_version=2", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void returnProposalCacheCannotBypassOwnershipThroughAmbiguousSubjects() {
    var order = orders.create(ALICE, "order", new CreateOrderCommand("BOOK-1", 1));
    var command = new CreateReturnCommand(order.id(), "Unopened");
    var proposal = returns.create(ALICE, "bob:proposal:retry", command);
    assertThat(returns.create(ALICE, "bob:proposal:retry", command)).isEqualTo(proposal);
    var other = new Actor("demo", "alice:proposal:bob", false);
    assertThatThrownBy(() -> returns.create(other, "retry", command))
        .isInstanceOf(BusinessException.class).hasMessageContaining("not found");
    assertThat(db.queryForObject("SELECT count(*) FROM return_proposals", Integer.class)).isEqualTo(1);
  }

  @Test
  void tenantsWithDelimitersCannotShareCachedResults() {
    db.update("INSERT INTO inventory(tenant,sku,stock,price) VALUES ('demo:alice','BOOK-1',10,2500)");
    try {
      var command = new CreateOrderCommand("BOOK-1", 1);
      var firstActor = new Actor("demo", "alice", false);
      var secondActor = new Actor("demo:alice", "bob", false);
      var first = orders.create(firstActor, "bob:order:retry", command);
      var second = orders.create(secondActor, "retry", command);
      assertThat(second.id()).isNotEqualTo(first.id());
      assertThat(orders.create(secondActor, "retry", command)).isEqualTo(second);
      assertThatThrownBy(() -> orders.get(secondActor, first.id())).hasMessageContaining("not found");
      assertThatThrownBy(() -> orders.get(firstActor, second.id())).hasMessageContaining("not found");
    } finally {
      db.update("DELETE FROM inventory WHERE tenant='demo:alice'");
    }
  }

  @Test
  void structuredScopeSeparatesOperationAndResourceWithoutLosingSpecialCharacters() {
    var adapter = new JdbcIdempotencyAdapter(db, json);
    var tx = new org.springframework.transaction.support.TransactionTemplate(
        new org.springframework.jdbc.datasource.DataSourceTransactionManager(db.getDataSource()));
    var scopes = List.of(
        new Idempotency.Scope("t:\"", "u:雪", "a:b", "c", "k:retry"),
        new Idempotency.Scope("t:\"", "u:雪", "a", "b:c", "k:retry"),
        new Idempotency.Scope("t:\"", "u:雪", "a", "b", "c:k:retry"));
    var effects = new java.util.concurrent.atomic.AtomicInteger();
    for (var scope : scopes) {
      Integer result = tx.execute(status -> adapter.execute(scope, 1, Integer.class, effects::incrementAndGet));
      Integer replay = tx.execute(status -> adapter.execute(scope, 1, Integer.class, effects::incrementAndGet));
      assertThat(replay).isEqualTo(result);
    }
    assertThat(effects.get()).isEqualTo(3);
  }

  @Test
  void legacyMigrationStopsWithoutDeletingOrReinterpretingRecords() {
    String schema = "legacy_migration_test";
    try {
      org.flywaydb.core.Flyway.configure()
          .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
          .schemas(schema).target("1").load().migrate();
      db.update("INSERT INTO " + schema + ".idempotency VALUES ('demo:alice:order:retry','hash','{}'::jsonb)");
      var migration = org.flywaydb.core.Flyway.configure()
          .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
          .schemas(schema).load();
      assertThatThrownBy(migration::migrate).hasStackTraceContaining("Legacy idempotency records exist");
      assertThat(db.queryForObject("SELECT scope FROM " + schema + ".idempotency", String.class))
          .isEqualTo("demo:alice:order:retry");
      assertThat(db.queryForObject("SELECT max(version)::integer FROM " + schema
          + ".flyway_schema_history WHERE success", Integer.class)).isEqualTo(1);
      assertThat(db.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=?"
          + " AND table_name='idempotency' AND column_name='scope_version'", Integer.class, schema))
          .isZero();
    } finally {
      db.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }
    // Even a legacy binary using explicit columns cannot silently create an unversioned key.
    assertThatThrownBy(() -> db.update("INSERT INTO idempotency(scope,request_hash,result)"
        + " VALUES ('legacy','hash','{}'::jsonb)"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void concurrentLastUnit() throws Exception {
    db.update("UPDATE inventory SET stock=1 WHERE tenant='demo'");
    try (var executor = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      var tasks = new ArrayList<Future<Boolean>>();
      for (int i = 0; i < 2; i++) {
        String key = "race-" + i;
        tasks.add(
            executor.submit(
                () -> {
                  gate.await();
                  try {
                    orders.create(ALICE, key, new CreateOrderCommand("BOOK-1", 1));
                    return true;
                  } catch (BusinessException e) {
                    assertThat(e.status()).isEqualTo(409);
                    return false;
                  }
                }));
      }
      gate.countDown();
      int accepted = 0;
      for (var t : tasks) if (t.get(15, TimeUnit.SECONDS)) accepted++;
      assertThat(accepted).isEqualTo(1);
      assertThat(
              db.queryForObject("SELECT stock FROM inventory WHERE tenant='demo'", Integer.class))
          .isZero();
    }
  }

  @Test
  void rollbackIncludesStockAndOrder() {
    db.execute("ALTER TABLE outbox ADD CONSTRAINT reject_test CHECK (false) NOT VALID");
    try {
      assertThatThrownBy(
              () -> orders.create(ALICE, "rollback", new CreateOrderCommand("BOOK-1", 1)))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    } finally {
      db.execute("ALTER TABLE outbox DROP CONSTRAINT reject_test");
    }
    assertThat(db.queryForObject("SELECT count(*) FROM orders", Integer.class)).isZero();
    assertThat(db.queryForObject("SELECT stock FROM inventory WHERE tenant='demo'", Integer.class))
        .isEqualTo(10);
    assertThat(db.queryForObject("SELECT count(*) FROM idempotency", Integer.class)).isZero();
  }

  @Test
  void returnsRequireCurrentApprovalAndRestoreOnce() throws Exception {
    var o = orders.create(ALICE, "order", new CreateOrderCommand("BOOK-1", 2));
    var p = returns.create(ALICE, "proposal", new CreateReturnCommand(o.id(), "Unopened"));
    assertThatThrownBy(() -> returns.execute(MANAGER, p.id(), "unapproved", 0))
        .hasMessageContaining("Approval");
    assertThatThrownBy(() -> returns.approve(ALICE, p.id(), 0)).hasMessageContaining("Manager");
    var approved = returns.approve(MANAGER, p.id(), 0);
    assertThat(approved.version()).isEqualTo(1);
    assertThatThrownBy(() -> returns.execute(MANAGER, p.id(), "wrong", 0))
        .hasMessageContaining("Approval");
    var result = returns.execute(MANAGER, p.id(), "execute", approved.version());
    assertThat(returns.execute(MANAGER, p.id(), "execute", approved.version())).isEqualTo(result);
    assertThat(result.status()).isEqualTo("EXECUTED");
    assertThat(orders.get(ALICE, o.id()).status()).isEqualTo("REFUNDED");
    assertThat(db.queryForObject("SELECT stock FROM inventory WHERE tenant='demo'", Integer.class))
        .isEqualTo(10);
    assertThatThrownBy(() -> returns.execute(MANAGER, p.id(), "new-key", approved.version()))
        .hasMessageContaining("Approval");
  }

  @Test
  void expiresAfterTenMinutes() {
    var o = orders.create(ALICE, "o", new CreateOrderCommand("BOOK-1", 1));
    var p = returns.create(ALICE, "p", new CreateReturnCommand(o.id(), "Unopened"));
    var a = returns.approve(MANAGER, p.id(), 0);
    clock.now = clock.now.plusSeconds(600);
    assertThatThrownBy(() -> returns.execute(MANAGER, p.id(), "x", a.version()))
        .hasMessageContaining("expired");
  }

  @Test
  void tenantAndOwnerIsolation() {
    var o = orders.create(ALICE, "o", new CreateOrderCommand("BOOK-1", 1));
    assertThatThrownBy(() -> orders.get(new Actor("other", "manager", true), o.id()))
        .hasMessageContaining("not found");
    assertThatThrownBy(() -> orders.get(new Actor("demo", "charlie", false), o.id()))
        .hasMessageContaining("not found");
    assertThat(orders.get(MANAGER, o.id()).id()).isEqualTo(o.id());
  }

  @Test
  void apiValidationAndAuthentication() throws Exception {
    http.perform(get("/api/v1/inventory/BOOK-1"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.correlationId").exists());
    http.perform(
            post("/api/v1/orders")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("alice")
                                    .claim("tenant", "demo")
                                    .claim("realm_access", Map.of("roles", List.of("CUSTOMER"))))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .header("Idempotency-Key", "a")
                .contentType("application/json")
                .content("{\"sku\":\"BOOK-1\",\"quantity\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value("Invalid request"))
        .andExpect(jsonPath("$.correlationId").isString());
    http.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
  }

  @Test
  void outboxPublishesAndInboxDeduplicates() throws Exception {
    orders.create(ALICE, "event", new CreateOrderCommand("BOOK-1", 1));
    String payload = db.queryForObject("SELECT payload FROM outbox", String.class);
    publisher.publish();
    awaitCount("SELECT count(*) FROM audit_orders", 1);
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM outbox WHERE published_at IS NOT NULL", Integer.class))
        .isEqualTo(1);
    double before = metrics.counter("commerce.audit.duplicates").count();
    var duplicate = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(payload);
    duplicate.put("eventId", duplicate.path("eventId").asText().toUpperCase(java.util.Locale.ROOT));
    duplicate.put(
        "aggregateId", duplicate.path("aggregateId").asText().toUpperCase(java.util.Locale.ROOT));
    producer
        .send(
            "orders.placed.v1",
            json.readTree(payload).path("aggregateId").asText(),
            json.writeValueAsString(duplicate))
        .get(15, TimeUnit.SECONDS);
    long duplicateDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
    while (metrics.counter("commerce.audit.duplicates").count() <= before
        && System.nanoTime() < duplicateDeadline) Thread.sleep(50);
    assertThat(metrics.counter("commerce.audit.duplicates").count()).isGreaterThan(before);
    assertThat(db.queryForObject("SELECT count(*) FROM audit_orders", Integer.class)).isEqualTo(1);
    var envelope = json.readTree(payload);
    var schemaFile = java.nio.file.Path.of("../../contracts/events/orders.placed.v1.schema.json");
    var schema =
        com.networknt.schema.JsonSchemaFactory.getInstance(
                com.networknt.schema.SpecVersion.VersionFlag.V202012)
            .getSchema(schemaFile.toUri());
    assertThat(schema.validate(envelope)).isEmpty();
    assertThat(envelope.path("schemaVersion").asInt()).isEqualTo(1);
    assertThat(envelope.path("correlationId").asText()).isNotBlank();
  }

  @Test
  void invalidEventReachesDlt() throws Exception {
    String marker = "invalid-" + UUID.randomUUID();
    var invalid = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(
        java.nio.file.Path.of("../../contracts/events/fixtures/orders.placed.v1.json").toFile());
    invalid.put("eventId", "1-1-1-1-1");
    invalid.put("correlationId", marker);
    producer
        .send("orders.placed.v1", marker, invalid.toString())
        .get(15, TimeUnit.SECONDS);
    var config = new HashMap<String, Object>();
    config.put(
        org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
        kafka.getBootstrapServers());
    config.put(
        org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG,
        "test-" + UUID.randomUUID());
    config.put(
        org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    try (var c =
        new org.apache.kafka.clients.consumer.KafkaConsumer<String, String>(
            config,
            new org.apache.kafka.common.serialization.StringDeserializer(),
            new org.apache.kafka.common.serialization.StringDeserializer())) {
      c.subscribe(List.of("orders.placed.v1.dlt"));
      boolean seen = false;
      long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
      while (!seen && System.nanoTime() < until) {
        for (var r : c.poll(Duration.ofMillis(200))) if (r.value().contains(marker)) seen = true;
      }
      assertThat(seen).isTrue();
      assertThat(db.queryForObject("SELECT count(*) FROM inbox WHERE event_id=?", Integer.class,
          UUID.fromString("00000001-0001-0001-0001-000000000001"))).isZero();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void failedDltPublicationDoesNotCommitOriginalOffsetAndRecovers() throws Exception {
    String marker = "dlt-unavailable-" + UUID.randomUUID();
    var failing = new java.util.concurrent.atomic.AtomicBoolean(true);
    var failedAttempts = new CountDownLatch(2);
    org.mockito.Mockito.doAnswer(invocation -> {
      org.apache.kafka.clients.producer.ProducerRecord<String, String> record = invocation.getArgument(0);
      if (record.topic().equals("orders.placed.v1.dlt") && marker.equals(record.key()) && failing.get()) {
        failedAttempts.countDown();
        return CompletableFuture.failedFuture(new org.apache.kafka.common.KafkaException("Injected DLT send failure"));
      }
      return invocation.callRealMethod();
    }).when(observedProducer).send(org.mockito.ArgumentMatchers.any(org.apache.kafka.clients.producer.ProducerRecord.class));

    var order = orders.create(ALICE, "after-poison", new CreateOrderCommand("BOOK-1", 1));
    String payload = db.queryForObject("SELECT payload FROM outbox", String.class);
    var partition = new org.apache.kafka.common.TopicPartition("orders.placed.v1", 0);
    try (var admin = org.apache.kafka.clients.admin.Admin.create(Map.of("bootstrap.servers", kafka.getBootstrapServers()))) {
      var poison = producer.send(new org.apache.kafka.clients.producer.ProducerRecord<>(
          partition.topic(), partition.partition(), marker, "{\"invalid\":true}")).get(15, TimeUnit.SECONDS);
      var following = producer.send(new org.apache.kafka.clients.producer.ProducerRecord<>(
          partition.topic(), partition.partition(), order.id().toString(), payload)).get(15, TimeUnit.SECONDS);
      try {
        assertThat(failedAttempts.await(20, TimeUnit.SECONDS)).isTrue();
        var committed = admin.listConsumerGroupOffsets("commerce-audit-v1")
            .partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS).get(partition);
        assertThat(committed == null ? 0 : committed.offset()).isLessThanOrEqualTo(poison.getRecordMetadata().offset());
        assertThat(db.queryForObject("SELECT count(*) FROM audit_orders WHERE order_id=?", Integer.class, order.id()))
            .isZero();
      } finally {
        failing.set(false);
      }
      // A real broker acknowledgement now permits recovery and the following business effect.
      awaitCount("SELECT count(*) FROM audit_orders WHERE order_id='" + order.id() + "'", 1);
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
      long offset = -1;
      while (offset <= following.getRecordMetadata().offset() && System.nanoTime() < deadline) {
        var committed = admin.listConsumerGroupOffsets("commerce-audit-v1")
            .partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS).get(partition);
        offset = committed == null ? -1 : committed.offset();
        if (offset <= following.getRecordMetadata().offset()) Thread.sleep(100);
      }
      assertThat(offset).isGreaterThan(following.getRecordMetadata().offset());
      var config = Map.<String, Object>of("bootstrap.servers", kafka.getBootstrapServers(),
          "group.id", "dlt-assert-" + UUID.randomUUID(), "auto.offset.reset", "earliest");
      try (var consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<String, String>(config,
          new org.apache.kafka.common.serialization.StringDeserializer(),
          new org.apache.kafka.common.serialization.StringDeserializer())) {
        consumer.assign(List.of(new org.apache.kafka.common.TopicPartition("orders.placed.v1.dlt", 0)));
        consumer.seekToBeginning(consumer.assignment());
        boolean seen = false;
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!seen && System.nanoTime() < until)
          for (var record : consumer.poll(Duration.ofMillis(100)))
            if (marker.equals(record.key())) seen = true;
        assertThat(seen).isTrue();
      }
    } finally {
      failing.set(false);
    }
  }

  @Test
  void duplicateConcurrentRequestsShareOneEffect() throws Exception {
    try (var pool = Executors.newFixedThreadPool(4)) {
      var gate = new CountDownLatch(1);
      var results = new ArrayList<Future<OrderResult>>();
      for (int i = 0; i < 4; i++)
        results.add(
            pool.submit(
                () -> {
                  gate.await();
                  return orders.create(ALICE, "identical", new CreateOrderCommand("BOOK-1", 1));
                }));
      gate.countDown();
      var id = results.getFirst().get(15, TimeUnit.SECONDS).id();
      for (var result : results) assertThat(result.get(15, TimeUnit.SECONDS).id()).isEqualTo(id);
      assertThat(db.queryForObject("SELECT count(*) FROM orders", Integer.class)).isEqualTo(1);
    }
  }

  @Test
  void concurrentReturnExecutionRestoresOnce() throws Exception {
    var o = orders.create(ALICE, "o", new CreateOrderCommand("BOOK-1", 1));
    var p = returns.create(ALICE, "p", new CreateReturnCommand(o.id(), "Unopened"));
    var approved = returns.approve(MANAGER, p.id(), 0);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      var results = new ArrayList<Future<Boolean>>();
      for (int i = 0; i < 2; i++) {
        String key = "execute-" + i;
        results.add(
            pool.submit(
                () -> {
                  gate.await();
                  try {
                    returns.execute(MANAGER, p.id(), key, approved.version());
                    return true;
                  } catch (BusinessException e) {
                    return false;
                  }
                }));
      }
      gate.countDown();
      int count = 0;
      for (var r : results) if (r.get(15, TimeUnit.SECONDS)) count++;
      assertThat(count).isEqualTo(1);
    }
    assertThat(db.queryForObject("SELECT stock FROM inventory WHERE tenant='demo'", Integer.class))
        .isEqualTo(10);
  }

  @Test
  void brokerOutageRetainsPendingAndRecovers() throws Exception {
    producer.send("orders.placed.v1", "warmup", "{}").get(15, TimeUnit.SECONDS);
    orders.create(ALICE, "outage", new CreateOrderCommand("BOOK-1", 1));
    kafka.getDockerClient().pauseContainerCmd(kafka.getContainerId()).exec();
    try {
      publisher.publish();
      assertThat(
              db.queryForObject(
                  "SELECT count(*) FROM outbox WHERE published_at IS NULL AND attempts=1",
                  Integer.class))
          .isEqualTo(1);
    } finally {
      kafka.getDockerClient().unpauseContainerCmd(kafka.getContainerId()).exec();
    }
    publisher.publish();
    awaitCount("SELECT count(*) FROM audit_orders", 1);
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM outbox WHERE published_at IS NOT NULL", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void traceContextSurvivesOutboxBoundary() throws Exception {
    var span = tracer.nextSpan().name("test.http").start();
    String traceId = span.context().traceId();
    try (var scope = tracer.withSpan(span)) {
      orders.create(ALICE, "traced", new CreateOrderCommand("BOOK-1", 1));
    } finally {
      span.end();
    }
    assertThat(db.queryForObject("SELECT trace_id FROM outbox", String.class)).isEqualTo(traceId);
    publisher.publish();
    var config = new HashMap<String, Object>();
    config.put("bootstrap.servers", kafka.getBootstrapServers());
    config.put("group.id", "trace-test-" + UUID.randomUUID());
    config.put("auto.offset.reset", "earliest");
    try (var c =
        new org.apache.kafka.clients.consumer.KafkaConsumer<String, String>(
            config,
            new org.apache.kafka.common.serialization.StringDeserializer(),
            new org.apache.kafka.common.serialization.StringDeserializer())) {
      c.subscribe(List.of("orders.placed.v1"));
      boolean found = false;
      long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
      while (!found && System.nanoTime() < until) {
        for (var r : c.poll(Duration.ofMillis(200))) {
          var header = r.headers().lastHeader("traceparent");
          if (header != null
              && new String(header.value(), java.nio.charset.StandardCharsets.UTF_8)
                  .contains(traceId)) found = true;
        }
      }
      assertThat(found).isTrue();
    }
  }

  @Test
  void apiRoleAndResourceIsolation() throws Exception {
    var o = orders.create(ALICE, "api-order", new CreateOrderCommand("BOOK-1", 1));
    var p = returns.create(ALICE, "api-proposal", new CreateReturnCommand(o.id(), "Unopened"));
    var customer =
        jwt()
            .jwt(
                j ->
                    j.subject("alice")
                        .claim("tenant", "demo")
                        .claim("realm_access", Map.of("roles", List.of("CUSTOMER"))))
            .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    var foreignManager =
        jwt()
            .jwt(
                j ->
                    j.subject("manager-other")
                        .claim("tenant", "other")
                        .claim("realm_access", Map.of("roles", List.of("MANAGER"))))
            .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"));
    http.perform(
            post("/api/v1/returns/" + p.id() + "/approve")
                .with(customer)
                .contentType("application/json")
                .content("{\"version\":0}"))
        .andExpect(status().isForbidden());
    http.perform(get("/api/v1/orders/" + o.id()).with(foreignManager))
        .andExpect(status().isNotFound());
    http.perform(
            post("/api/v1/returns/" + p.id() + "/approve")
                .with(foreignManager)
                .contentType("application/json")
                .content("{\"version\":0}"))
        .andExpect(status().isNotFound());
  }

  private void awaitCount(String sql, int expected) throws Exception {
    long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
    while (System.nanoTime() < until) {
      if (db.queryForObject(sql, Integer.class) == expected) return;
      Thread.sleep(100);
    }
    assertThat(db.queryForObject(sql, Integer.class)).isEqualTo(expected);
  }
}
