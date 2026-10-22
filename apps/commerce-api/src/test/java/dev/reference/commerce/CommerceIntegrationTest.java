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
import dev.reference.commerce.shared.infrastructure.messaging.OutboxPublisher;
import dev.reference.commerce.shared.infrastructure.persistence.JdbcIdempotencyAdapter;
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
      var first =
          pool.submit(
              () -> {
                gate.await();
                return orders.create(firstActor, "bob:order:retry", command);
              });
      var second =
          pool.submit(
              () -> {
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
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM idempotency WHERE scope_version=2", Integer.class))
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
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("not found");
    assertThat(db.queryForObject("SELECT count(*) FROM return_proposals", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void tenantsWithDelimitersCannotShareCachedResults() {
    db.update(
        "INSERT INTO inventory(tenant,sku,stock,price) VALUES ('demo:alice','BOOK-1',10,2500)");
    try {
      var command = new CreateOrderCommand("BOOK-1", 1);
      var firstActor = new Actor("demo", "alice", false);
      var secondActor = new Actor("demo:alice", "bob", false);
      var first = orders.create(firstActor, "bob:order:retry", command);
      var second = orders.create(secondActor, "retry", command);
      assertThat(second.id()).isNotEqualTo(first.id());
      assertThat(orders.create(secondActor, "retry", command)).isEqualTo(second);
      assertThatThrownBy(() -> orders.get(secondActor, first.id()))
          .hasMessageContaining("not found");
      assertThatThrownBy(() -> orders.get(firstActor, second.id()))
          .hasMessageContaining("not found");
    } finally {
      db.update("DELETE FROM inventory WHERE tenant='demo:alice'");
    }
  }

  @Test
  void structuredScopeSeparatesOperationAndResourceWithoutLosingSpecialCharacters() {
    var adapter = new JdbcIdempotencyAdapter(db, json);
    var tx =
        new org.springframework.transaction.support.TransactionTemplate(
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                db.getDataSource()));
    var scopes =
        List.of(
            new Idempotency.Scope("t:\"", "u:雪", "a:b", "c", "k:retry"),
            new Idempotency.Scope("t:\"", "u:雪", "a", "b:c", "k:retry"),
            new Idempotency.Scope("t:\"", "u:雪", "a", "b", "c:k:retry"));
    var effects = new java.util.concurrent.atomic.AtomicInteger();
    for (var scope : scopes) {
      Integer result =
          tx.execute(status -> adapter.execute(scope, 1, Integer.class, effects::incrementAndGet));
      Integer replay =
          tx.execute(status -> adapter.execute(scope, 1, Integer.class, effects::incrementAndGet));
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
          .schemas(schema)
          .target("1")
          .load()
          .migrate();
      db.update(
          "INSERT INTO "
              + schema
              + ".idempotency VALUES ('demo:alice:order:retry','hash','{}'::jsonb)");
      var migration =
          org.flywaydb.core.Flyway.configure()
              .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
              .schemas(schema)
              .load();
      assertThatThrownBy(migration::migrate)
          .hasStackTraceContaining("Legacy idempotency records exist");
      assertThat(db.queryForObject("SELECT scope FROM " + schema + ".idempotency", String.class))
          .isEqualTo("demo:alice:order:retry");
      assertThat(
              db.queryForObject(
                  "SELECT max(version)::integer FROM "
                      + schema
                      + ".flyway_schema_history WHERE success",
                  Integer.class))
          .isEqualTo(1);
      assertThat(
              db.queryForObject(
                  "SELECT count(*) FROM information_schema.columns WHERE table_schema=?"
                      + " AND table_name='idempotency' AND column_name='scope_version'",
                  Integer.class,
                  schema))
          .isZero();
    } finally {
      db.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }
    // Even a legacy binary using explicit columns cannot silently create an unversioned key.
    assertThatThrownBy(
            () ->
                db.update(
                    "INSERT INTO idempotency(scope,request_hash,result)"
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
                    assertThat(e.kind()).isEqualTo(BusinessException.Kind.CONFLICT);
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
    assertThat(result.status())
        .isEqualTo(dev.reference.commerce.returns.domain.ReturnStatus.EXECUTED);
    assertThat(orders.get(ALICE, o.id()).status())
        .isEqualTo(dev.reference.commerce.orders.domain.OrderStatus.REFUNDED);
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
        .andExpect(header().string("WWW-Authenticate", "Bearer"))
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.correlationId").exists());
    http.perform(
            get("/api/v1/inventory/BOOK-1").header("Authorization", "Bearer not-a-jwt"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.correlationId").exists());
    http.perform(get("/api/v1/inventory/BOOK-1").with(jwt().authorities(List.of())))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist("WWW-Authenticate"))
        .andExpect(jsonPath("$.status").value(403))
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
    var invalid =
        (com.fasterxml.jackson.databind.node.ObjectNode)
            json.readTree(
                java.nio.file.Path.of("../../contracts/events/fixtures/orders.placed.v1.json")
                    .toFile());
    invalid.put("eventId", "1-1-1-1-1");
    invalid.put("correlationId", marker);
    producer.send("orders.placed.v1", marker, invalid.toString()).get(15, TimeUnit.SECONDS);
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
      assertThat(
              db.queryForObject(
                  "SELECT count(*) FROM inbox WHERE event_id=?",
                  Integer.class,
                  UUID.fromString("00000001-0001-0001-0001-000000000001")))
          .isZero();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void failedDltPublicationDoesNotCommitOriginalOffsetAndRecovers() throws Exception {
    String marker = "dlt-unavailable-" + UUID.randomUUID();
    var failing = new java.util.concurrent.atomic.AtomicBoolean(true);
    var failedAttempts = new CountDownLatch(2);
    org.mockito.Mockito.doAnswer(
            invocation -> {
              org.apache.kafka.clients.producer.ProducerRecord<String, String> record =
                  invocation.getArgument(0);
              if (record.topic().equals("orders.placed.v1.dlt")
                  && marker.equals(record.key())
                  && failing.get()) {
                failedAttempts.countDown();
                return CompletableFuture.failedFuture(
                    new org.apache.kafka.common.KafkaException("Injected DLT send failure"));
              }
              return invocation.callRealMethod();
            })
        .when(observedProducer)
        .send(
            org.mockito.ArgumentMatchers.any(
                org.apache.kafka.clients.producer.ProducerRecord.class));

    var order = orders.create(ALICE, "after-poison", new CreateOrderCommand("BOOK-1", 1));
    String payload = db.queryForObject("SELECT payload FROM outbox", String.class);
    var partition = new org.apache.kafka.common.TopicPartition("orders.placed.v1", 0);
    try (var admin =
        org.apache.kafka.clients.admin.Admin.create(
            Map.of("bootstrap.servers", kafka.getBootstrapServers()))) {
      var poison =
          producer
              .send(
                  new org.apache.kafka.clients.producer.ProducerRecord<>(
                      partition.topic(), partition.partition(), marker, "{\"invalid\":true}"))
              .get(15, TimeUnit.SECONDS);
      var following =
          producer
              .send(
                  new org.apache.kafka.clients.producer.ProducerRecord<>(
                      partition.topic(), partition.partition(), order.id().toString(), payload))
              .get(15, TimeUnit.SECONDS);
      try {
        assertThat(failedAttempts.await(20, TimeUnit.SECONDS)).isTrue();
        var committed =
            admin
                .listConsumerGroupOffsets("commerce-audit-v1")
                .partitionsToOffsetAndMetadata()
                .get(10, TimeUnit.SECONDS)
                .get(partition);
        assertThat(committed == null ? 0 : committed.offset())
            .isLessThanOrEqualTo(poison.getRecordMetadata().offset());
        assertThat(
                db.queryForObject(
                    "SELECT count(*) FROM audit_orders WHERE order_id=?",
                    Integer.class,
                    order.id()))
            .isZero();
      } finally {
        failing.set(false);
      }
      // A real broker acknowledgement now permits recovery and the following business effect.
      awaitCount("SELECT count(*) FROM audit_orders WHERE order_id='" + order.id() + "'", 1);
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
      long offset = -1;
      while (offset <= following.getRecordMetadata().offset() && System.nanoTime() < deadline) {
        var committed =
            admin
                .listConsumerGroupOffsets("commerce-audit-v1")
                .partitionsToOffsetAndMetadata()
                .get(10, TimeUnit.SECONDS)
                .get(partition);
        offset = committed == null ? -1 : committed.offset();
        if (offset <= following.getRecordMetadata().offset()) Thread.sleep(100);
      }
      assertThat(offset).isGreaterThan(following.getRecordMetadata().offset());
      var config =
          Map.<String, Object>of(
              "bootstrap.servers",
              kafka.getBootstrapServers(),
              "group.id",
              "dlt-assert-" + UUID.randomUUID(),
              "auto.offset.reset",
              "earliest");
      try (var consumer =
          new org.apache.kafka.clients.consumer.KafkaConsumer<String, String>(
              config,
              new org.apache.kafka.common.serialization.StringDeserializer(),
              new org.apache.kafka.common.serialization.StringDeserializer())) {
        consumer.assign(
            List.of(new org.apache.kafka.common.TopicPartition("orders.placed.v1.dlt", 0)));
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

  @Test
  void orderPagesUseTenantOwnerAndKeyset() throws Exception {
    // Deliberately fixed UUIDs exercise PostgreSQL UUID ordering, including the sign boundary.
    var ids =
        List.of(
            "00000000-0000-0000-0000-000000000001",
            "00000000-0000-0000-0000-000000000002",
            "80000000-0000-0000-0000-000000000000",
            "ffffffff-ffff-ffff-ffff-ffffffffffff");
    for (String id : ids)
      db.update(
          "INSERT INTO orders VALUES (?,'demo','alice','BOOK-1',1,2500,'PLACED',0)",
          UUID.fromString(id));
    db.update(
        "INSERT INTO orders VALUES (?,'demo','charlie','BOOK-1',1,2500,'PLACED',0)",
        UUID.randomUUID());
    db.update(
        "INSERT INTO orders VALUES (?,'other','alice','BOOK-1',1,2500,'PLACED',0)",
        UUID.randomUUID());
    var first = orders.list(ALICE, new OrderQuery(2, null, null));
    assertThat(first.items())
        .extracting(order -> order.id().toString())
        .containsExactlyElementsOf(ids.subList(0, 2));
    assertThat(first.nextCursor()).isEqualTo(UUID.fromString(ids.get(1)));
    var last = orders.list(ALICE, new OrderQuery(2, first.nextCursor(), null));
    assertThat(last.items())
        .extracting(order -> order.id().toString())
        .containsExactlyElementsOf(ids.subList(2, 4));
    assertThat(last.nextCursor()).isNull();
    assertThat(orders.list(ALICE, new OrderQuery(2, UUID.fromString(ids.getLast()), null)).items())
        .isEmpty();
    assertThat(orders.list(MANAGER, new OrderQuery(50, null, null)).items()).hasSize(5);
    assertThat(
            orders
                .list(new Actor("other", "manager", true), new OrderQuery(50, null, null))
                .items())
        .hasSize(1);
    assertThat(
            orders.list(new Actor("demo", "nobody", false), new OrderQuery(50, null, null)).items())
        .isEmpty();
    db.update("UPDATE orders SET status='REFUNDED' WHERE id=?", UUID.fromString(ids.get(1)));
    assertThat(
            orders
                .list(
                    ALICE,
                    new OrderQuery(
                        50, null, dev.reference.commerce.orders.domain.OrderStatus.REFUNDED))
                .items())
        .extracting(OrderResult::id)
        .containsExactly(UUID.fromString(ids.get(1)));
    assertThat(
            orders
                .list(
                    ALICE,
                    new OrderQuery(
                        50, null, dev.reference.commerce.orders.domain.OrderStatus.PLACED))
                .items())
        .hasSize(3);
    var customer = apiActor("demo", "alice", "CUSTOMER");
    for (String query :
        List.of(
            "limit=0",
            "limit=51",
            "limit=no",
            "after=not-a-uuid",
            "after=1-1-1-1-1",
            "status=UNKNOWN")) {
      var result =
          http.perform(get("/api/v1/orders?" + query).with(customer))
              .andExpect(status().isBadRequest())
              .andReturn();
      CommerceContractAssertions.response("/api/v1/orders", "get", result);
    }
    var defaults =
        http.perform(get("/api/v1/orders").with(customer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(4))
            .andExpect(jsonPath("$.nextCursor").isEmpty())
            .andReturn();
    CommerceContractAssertions.response("/api/v1/orders", "get", defaults);
    var filtered =
        http.perform(get("/api/v1/orders?limit=1&status=PLACED").with(customer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.nextCursor").value(ids.getFirst()))
            .andReturn();
    CommerceContractAssertions.response("/api/v1/orders", "get", filtered);
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor apiActor(
      String tenant, String subject, String role) {
    return jwt()
        .jwt(
            token ->
                token
                    .subject(subject)
                    .claim("tenant", tenant)
                    .claim("realm_access", Map.of("roles", List.of(role))))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }

  @Autowired
  @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
  org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping mappings;

  @Test
  void frameworkErrorsPreserveStatusHeadersAndCorrelation() throws Exception {
    var customer = apiActor("demo", "alice", "CUSTOMER");
    String correlation = "framework-errors-test";
    var method =
        http.perform(
                delete("/api/v1/orders").with(customer).header("X-Correlation-Id", correlation))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("GET")))
            .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("POST")))
            .andExpect(jsonPath("$.status").value(405))
            .andExpect(jsonPath("$.correlationId").value(correlation))
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andReturn();
    assertThat(method.getResponse().getHeader("X-Correlation-Id")).isEqualTo(correlation);
    var media =
        http.perform(
                post("/api/v1/orders")
                    .with(customer)
                    .header("X-Correlation-Id", correlation)
                    .header("Idempotency-Key", "unsupported")
                    .contentType("text/plain")
                    .content("private-request-marker"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(
                header().string("Accept", org.hamcrest.Matchers.containsString("application/json")))
            .andExpect(jsonPath("$.status").value(415))
            .andExpect(jsonPath("$.correlationId").value(correlation))
            .andReturn();
    CommerceContractAssertions.response("/api/v1/orders", "post", media);
    assertThat(media.getResponse().getContentAsString()).doesNotContain("private-request-marker");
    for (String accept : List.of("text/plain", "invalid media type")) {
      var negotiation =
          http.perform(
                  get("/api/v1/orders")
                      .with(customer)
                      .header("X-Correlation-Id", correlation)
                      .header("Accept", accept))
              .andExpect(status().isNotAcceptable())
              .andExpect(jsonPath("$.status").value(406))
              .andExpect(jsonPath("$.correlationId").value(correlation))
              .andReturn();
      CommerceContractAssertions.response("/api/v1/orders", "get", negotiation);
    }
    http.perform(
            get("/api/v1/no-such-route").with(customer).header("X-Correlation-Id", correlation))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.correlationId").value(correlation));
  }

  @Test
  void httpContractMatchesActualRoutesAndResponses() throws Exception {
    var contract = CommerceContractAssertions.document();
    var actualRoutes = new TreeSet<String>();
    mappings
        .getHandlerMethods()
        .keySet()
        .forEach(
            mapping -> {
              for (String pattern : mapping.getPatternValues())
                if (pattern.startsWith("/api/"))
                  mapping
                      .getMethodsCondition()
                      .getMethods()
                      .forEach(
                          method -> actualRoutes.add(method.name().toLowerCase() + " " + pattern));
            });
    var declaredRoutes = new TreeSet<String>();
    contract
        .path("paths")
        .fields()
        .forEachRemaining(
            path ->
                path.getValue()
                    .fieldNames()
                    .forEachRemaining(method -> declaredRoutes.add(method + " " + path.getKey())));
    assertThat(actualRoutes).containsExactlyElementsOf(declaredRoutes);
    for (String route : declaredRoutes) {
      String[] parts = route.split(" ", 2);
      String path =
          parts[1].replace("{id}", UUID.randomUUID().toString()).replace("{sku}", "BOOK-1");
      var request = parts[0].equals("get") ? get(path) : post(path);
      CommerceContractAssertions.response(
          parts[1],
          parts[0],
          http.perform(request).andExpect(status().isUnauthorized()).andReturn());
    }
    var customer = apiActor("demo", "alice", "CUSTOMER");
    var manager = apiActor("demo", "manager", "MANAGER");
    String body = "{\"sku\":\"BOOK-1\",\"quantity\":2}";
    CommerceContractAssertions.request("/api/v1/orders", "post", body, true);
    var created =
        http.perform(
                post("/api/v1/orders")
                    .with(customer)
                    .header("Idempotency-Key", "contract-order")
                    .contentType("application/json")
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    CommerceContractAssertions.response("/api/v1/orders", "post", created);
    String unknownFields = "{\"sku\":\"BOOK-1\",\"quantity\":1,\"unused\":true}";
    CommerceContractAssertions.request("/api/v1/orders", "post", unknownFields, true);
    CommerceContractAssertions.response(
        "/api/v1/orders",
        "post",
        http.perform(
                post("/api/v1/orders")
                    .with(customer)
                    .header("Idempotency-Key", "unknown-fields")
                    .contentType("application/json")
                    .content(unknownFields))
            .andExpect(status().isCreated())
            .andReturn());
    for (String quantityToken : List.of("1.9", "\"2\"", "2.0")) {
      String invalidQuantity = "{\"sku\":\"BOOK-1\",\"quantity\":" + quantityToken + "}";
      // JSON Schema treats 2.0 as integral; HTTP additionally enforces an integer wire token.
      CommerceContractAssertions.request(
          "/api/v1/orders", "post", invalidQuantity, quantityToken.equals("2.0"));
      CommerceContractAssertions.response(
          "/api/v1/orders",
          "post",
          http.perform(
                  post("/api/v1/orders")
                      .with(customer)
                      .header("Idempotency-Key", "strict-quantity")
                      .contentType("application/json")
                      .content(invalidQuantity))
              .andExpect(status().isBadRequest())
              .andReturn());
    }
    String orderId = json.readTree(created.getResponse().getContentAsString()).path("id").asText();
    CommerceContractAssertions.response(
        "/api/v1/orders/{id}",
        "get",
        http.perform(get("/api/v1/orders/" + orderId).with(customer))
            .andExpect(status().isOk())
            .andReturn());
    CommerceContractAssertions.response(
        "/api/v1/inventory/{sku}",
        "get",
        http.perform(get("/api/v1/inventory/BOOK-1").with(customer))
            .andExpect(status().isOk())
            .andReturn());
    String proposalBody = "{\"orderId\":\"" + orderId + "\",\"reason\":\"Unopened\"}";
    CommerceContractAssertions.request("/api/v1/returns", "post", proposalBody, true);
    var proposal =
        http.perform(
                post("/api/v1/returns")
                    .with(customer)
                    .header("Idempotency-Key", "contract-return")
                    .contentType("application/json")
                    .content(proposalBody))
            .andExpect(status().isCreated())
            .andReturn();
    CommerceContractAssertions.response("/api/v1/returns", "post", proposal);
    String returnId =
        json.readTree(proposal.getResponse().getContentAsString()).path("id").asText();
    CommerceContractAssertions.response(
        "/api/v1/returns/{id}",
        "get",
        http.perform(get("/api/v1/returns/" + returnId).with(customer))
            .andExpect(status().isOk())
            .andReturn());
    for (String versionToken : List.of("1.9", "\"2\"", "2.0")) {
      String invalidVersion = "{\"version\":" + versionToken + "}";
      CommerceContractAssertions.request(
          "/api/v1/returns/{id}/approve", "post", invalidVersion, versionToken.equals("2.0"));
      CommerceContractAssertions.response(
          "/api/v1/returns/{id}/approve",
          "post",
          http.perform(
                  post("/api/v1/returns/" + returnId + "/approve")
                      .with(manager)
                      .contentType("application/json")
                      .content(invalidVersion))
              .andExpect(status().isBadRequest())
              .andReturn());
    }
    for (String action : List.of("approve", "execute")) {
      String version = action.equals("approve") ? "{\"version\":0}" : "{\"version\":1}";
      CommerceContractAssertions.request("/api/v1/returns/{id}/" + action, "post", version, true);
      CommerceContractAssertions.response(
          "/api/v1/returns/{id}/" + action,
          "post",
          http.perform(
                  post("/api/v1/returns/" + returnId + "/" + action)
                      .with(manager)
                      .header("Idempotency-Key", "contract-execute")
                      .contentType("application/json")
                      .content(version))
              .andExpect(status().isOk())
              .andReturn());
    }
    for (String invalid :
        List.of(
            "{\"sku\":\"BOOK-1\",\"quantity\":0}",
            "{\"sku\":\" \",\"quantity\":1}",
            "{\"sku\":\"BOOK-1\",\"quantity\":101}")) {
      CommerceContractAssertions.request("/api/v1/orders", "post", invalid, false);
      CommerceContractAssertions.response(
          "/api/v1/orders",
          "post",
          http.perform(
                  post("/api/v1/orders")
                      .with(customer)
                      .header("Idempotency-Key", "invalid")
                      .contentType("application/json")
                      .content(invalid))
              .andExpect(status().isBadRequest())
              .andReturn());
    }
    for (String invalid :
        List.of(
            "{\"orderId\":\"not-uuid\",\"reason\":\"Unopened\"}",
            "{\"orderId\":\"" + orderId + "\",\"reason\":\" \"}")) {
      CommerceContractAssertions.request("/api/v1/returns", "post", invalid, false);
      CommerceContractAssertions.response(
          "/api/v1/returns",
          "post",
          http.perform(
                  post("/api/v1/returns")
                      .with(customer)
                      .header("Idempotency-Key", "invalid-return")
                      .contentType("application/json")
                      .content(invalid))
              .andExpect(status().isBadRequest())
              .andReturn());
    }
    CommerceContractAssertions.response(
        "/api/v1/orders",
        "get",
        http.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized()).andReturn());
    CommerceContractAssertions.response(
        "/api/v1/orders",
        "get",
        http.perform(get("/api/v1/orders").with(apiActor("demo", "alice", "VIEWER")))
            .andExpect(status().isForbidden())
            .andReturn());
    CommerceContractAssertions.response(
        "/api/v1/orders/{id}",
        "get",
        http.perform(get("/api/v1/orders/" + orderId).with(apiActor("other", "alice", "CUSTOMER")))
            .andExpect(status().isNotFound())
            .andReturn());
    CommerceContractAssertions.response(
        "/api/v1/orders",
        "post",
        http.perform(
                post("/api/v1/orders")
                    .with(customer)
                    .header("Idempotency-Key", "contract-order")
                    .contentType("application/json")
                    .content("{\"sku\":\"BOOK-1\",\"quantity\":1}"))
            .andExpect(status().isConflict())
            .andReturn());
    CommerceContractAssertions.response(
        "/api/v1/orders/{id}",
        "get",
        http.perform(get("/api/v1/orders/1-1-1-1-1").with(customer))
            .andExpect(status().isNotFound())
            .andReturn());
    // A wrong response state and omitted mandatory output are rejected by the same canonical
    // schema.
    var orderSchema =
        CommerceContractAssertions.schema(
            contract.path("components").path("schemas").path("Order"));
    var wrong =
        (com.fasterxml.jackson.databind.node.ObjectNode)
            json.readTree(created.getResponse().getContentAsString());
    wrong.put("status", "UNKNOWN");
    assertThat(orderSchema.validate(wrong)).isNotEmpty();
    wrong.put("status", "PLACED");
    wrong.remove("quantity");
    assertThat(orderSchema.validate(wrong)).isNotEmpty();
  }

  @Test
  void directInvocationBypassesTransactionAndLeavesPartialEffects() throws Exception {
    assertThat(org.springframework.aop.support.AopUtils.isAopProxy(orders)).isTrue();
    var direct =
        org.springframework.test.util.AopTestUtils
            .<dev.reference.commerce.orders.application.service.ManageOrderService>getTargetObject(
                orders);
    assertThat(org.springframework.aop.support.AopUtils.isAopProxy(direct)).isFalse();
    db.execute("ALTER TABLE outbox ADD CONSTRAINT reject_direct_test CHECK (false) NOT VALID");
    try {
      assertThatThrownBy(() -> orders.create(ALICE, "proxy", new CreateOrderCommand("BOOK-1", 1)))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
      assertThat(
              db.queryForObject("SELECT stock FROM inventory WHERE tenant='demo'", Integer.class))
          .isEqualTo(10);
      assertThat(db.queryForObject("SELECT count(*) FROM orders", Integer.class)).isZero();
      assertThatThrownBy(() -> direct.create(ALICE, "direct", new CreateOrderCommand("BOOK-1", 1)))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
      assertThat(
              db.queryForObject("SELECT stock FROM inventory WHERE tenant='demo'", Integer.class))
          .isEqualTo(9);
      assertThat(db.queryForObject("SELECT count(*) FROM orders", Integer.class)).isEqualTo(1);
      assertThat(db.queryForObject("SELECT count(*) FROM outbox", Integer.class)).isZero();
      assertThat(db.queryForObject("SELECT count(*) FROM idempotency", Integer.class)).isZero();
    } finally {
      db.execute("ALTER TABLE outbox DROP CONSTRAINT reject_direct_test");
      reset();
    }
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
