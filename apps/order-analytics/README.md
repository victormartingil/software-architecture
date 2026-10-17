# Order analytics

Resumen de pedidos: proceso Java que lee `orders.placed.v1` desde Kafka. `OrderSummaryTopology` deduplica y acumula pedidos creados por organización, producto y minuto. Kafka es el servidor de mensajes; Kafka Streams es la biblioteca ejecutada aquí. No es otra API HTTP de negocio.

[Mapa de aplicaciones y sistemas de apoyo](../../docs/repository-design.md#component-map).

Procesa hechos `OrderPlaced`: resume pedidos creados, sin descontar devoluciones. Java 21 y Kafka Streams 4.0.2. No necesita Spring ni una API de negocio.

## Flujo y garantías

`orders.placed.v1 → validación → repartición eventId → deduplicación → repartición tenant/SKU → ventana → orders.summary.v1`.

La deduplicación usa RocksDB persistente y changelog, retiene identidades siete días de **stream time** y elimina entradas vencidas cada hora de stream time. El topic de entrada debe retener siete días. La identidad es global aunque el duplicado llegue por otra partición. Una identidad repetida con datos distintos se considera el mismo evento: los productores deben mantener su inmutabilidad.

Ventanas tumbling de un minuto por `occurredAt`, tolerancia de treinta segundos. El stream time avanza al recibir eventos, no al transcurrir tiempo de pared. Al cerrar la ventana se descartan tardíos y se incrementa `analytics_late_events_total`. `TopologyTestDriver` controla ese avance explícitamente.

La salida publica **actualizaciones**, con clave JSON `[tenant,sku]@windowStartMillis`; un consumidor mantiene el último valor por clave. No debe sumar todas las actualizaciones como hechos independientes. Payload: `tenantId`, `sku`, `windowStart`, `windowEnd`, `orderCount`, `totalQuantity`, `totalCents`. Se usan enteros sin overflow silencioso. El schema está en `../../contracts/events`.

Kafka Streams usa `exactly_once_v2` para sus offsets, stores y salidas Kafka. Esto no convierte en exactamente-una-vez los efectos externos de consumidores ni sustituye la deduplicación de eventos publicados dos veces por outbox. Las stores se reconstruyen desde changelog si se pierde el disco local, conservando el mismo `APPLICATION_ID`. Cambiarlo crea una aplicación nueva que reprocesa desde el inicio disponible.

Los mensajes inválidos conservan clave y contenido original en `orders.analytics.v1.dlt`, dentro de la transacción Streams, e incrementan `analytics_invalid_events_total`. No registrar cuerpos. Inspeccionar la causa en un entorno autorizado, corregir al productor y reintroducir un contrato válido conservando eventId cuando represente el mismo hecho. El stream se detiene si no puede escribir DLT; no ignora silenciosamente el error.

## Ejecución y pruebas

```sh
mvn -f apps/order-analytics/pom.xml verify
mvn -f apps/order-analytics/pom.xml package
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 java -jar apps/order-analytics/target/order-analytics-1.0.0.jar
```

El test Kafka real usa Testcontainers; la verificación canónica exige ejecutarlo y rechaza omisiones si Docker no está disponible. En Rancher Desktop, exportar `DOCKER_HOST` con la dirección de `docker context inspect --format '{{.Endpoints.docker.Host}}'`; para Docker reciente añadir `-Dapi.version=1.44` a Maven. Un test omitido no acredita recuperación real. La prueba elimina RocksDB después de cerrar la primera instancia y verifica restauración de agregado y deduplicación desde Kafka.

| Variable | Valor predeterminado | Uso |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Brokers |
| `APPLICATION_ID` | `order-analytics-v1` | Identidad estable, grupos y changelogs |
| `STATE_DIR` | `/tmp/order-analytics` | En Docker `/state`, montar volumen con propietario UID 10001 |
| `READY_FILE` | `/tmp/analytics-ready` | Presente solamente en estado RUNNING |
| `METRICS_PORT` | `9404` | Listener interno de `/metrics`, `/health` y `/live` |

`StreamsLifecycle` espera RUNNING hasta 60 s al arrancar y solicita cierre con un plazo de 20 s. Un timeout o fallo elimina readiness; un cierre que devuelve false se informa como fallo, sin afirmar que terminó. El shutdown hook cierra también el listener HTTP. Compose y Kubernetes conceden 30 s de terminación, por encima del plazo de cierre solicitado a Kafka. Los callbacks de estado no ejecutan close ni se mantiene su monitor mientras Kafka espera hilos: evita un bloqueo mutuo.

`max.poll.records=100` acota registros por poll; `buffered.records.per.partition=100` es un umbral de pausa por partición que puede superarse al añadir un lote. Ninguno es un rate limit ni un techo exacto de memoria. Observar lag junto a capacidad de proceso, tamaño de mensajes, particiones y readiness; aumentar pods está limitado por particiones/tareas y datos locales.

Liveness `/live` tolera rebalance y solo falla ERROR/PENDING_ERROR; readiness `/health` responde 200 solo RUNNING; Docker usa el archivo para evitar añadir curl. Métricas Prometheus: `analytics_late_events_total`, `analytics_invalid_events_total` y `analytics_records_lag_max` (máximo lag observado entre consumidores; cero antes de asignar particiones, interpretar junto con readiness). Counters reinician con proceso, usar `rate()` en dashboard. No publicar puerto de métricas a Internet.

Heap 256 MiB, direct buffers 128 MiB y RocksDB limitado por store a cache 4 MiB y dos write buffers de 1 MiB. El número de particiones/stores multiplica memoria nativa: la referencia presupone tres particiones y un stream thread. No equivale a dimensionamiento para tráfico industrial. Replicación interna 1 para broker local; producción exige broker redundante, TLS/ACL y política de retención/capacidad.

## Decisiones y alternativas

La DSL resuelve ventanas, materialización y changelog; dos procesadores pequeños cubren deduplicación y métrica explícita de tardíos. Un consumidor sin Streams necesitaría implementar recuperación y ventanas. No se añade un microservicio REST para consultar una única agregación: puede consumirse la salida Kafka.

Límite: el barrido horario de deduplicación recorre el store. Es sencillo y verificable con volúmenes de referencia; un caudal alto requeriría expiración indexada y dimensionamiento medido. Los eventos futuros incorrectos pueden adelantar stream time; en producción se debe acordar y validar un máximo de desfase de reloj sin romper backfills autorizados.

Fuentes oficiales consultadas: [Processor API 4.0](https://kafka.apache.org/40/streams/developer-guide/processor-api/), [pruebas](https://kafka.apache.org/40/streams/developer-guide/testing/), [memoria](https://kafka.apache.org/40/streams/developer-guide/memory-mgmt/), [ciclo de vida](https://kafka.apache.org/40/javadoc/org/apache/kafka/streams/KafkaStreams.html). Los ejemplos se implementan para el contrato de esta referencia.

El ingreso aplica un máximo de treinta segundos de adelanto sobre reloj UTC del proceso antes de asignar tiempo de evento. Inválidos se aíslan en DLT sin adelantar ventanas; ver [relojes y cuarentena](../../docs/handbook/events.md#topic-relojes-incorrectos-y-cuarentena).
