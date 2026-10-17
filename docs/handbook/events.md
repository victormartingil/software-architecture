# Eventos fiables y tiempo de procesamiento

## Publicar después de confirmar

Guardar un pedido y enviar Kafka son dos escrituras en sistemas distintos. Si la primera funciona y la segunda falla, el consumidor nunca recibe el hecho. Enviar primero invierte el problema: puede publicarse un pedido que luego no existe. `@Async` cambia el hilo, sin crear atomicidad.

El outbox guarda el evento junto al pedido. Un publicador reclama lotes pendientes con `FOR UPDATE SKIP LOCKED`, envía y espera confirmación del broker antes de marcarlos. Si cae tras enviar y antes de marcar, publicará de nuevo. La garantía es entrega al menos una vez dentro de las condiciones de retención y recuperación; el consumidor debe tolerar duplicados.

El consumidor de auditoría usa una tabla inbox con `UNIQUE(eventId)`. Registrar identidad y efecto se hace en una transacción; el offset se confirma después. Un reinicio puede repetir un mensaje pero la restricción evita un segundo efecto. La identidad de evento debe mantenerse en los reintentos y en el reproceso. Generar un UUID nuevo convierte la repetición en otro hecho.

El contrato vive en [orders.placed.v1](../../contracts/events/orders.placed.v1.schema.json). Tenant, correlación, versión y tiempo pertenecen al sobre; el contenido contiene solo datos sintéticos necesarios. La clave Kafka es orderId para conservar orden por pedido dentro de su partición. No existe un orden total entre las tres particiones.

Ambos consumidores exigen UUID completos en eventId, aggregateId y data.orderId. `UUID.fromString` puede aceptar abreviaturas; por eso se contrasta también su representación completa, ignorando capitalización. El [corpus compartido](../../contracts/events/fixtures/uuid-cases.json) comprueba doce casos: Comercio rechaza antes de SQL y Streams envía a DLT sin resumen. La forma normalizada mantiene identidad al repetir mayúsculas/minúsculas. La ruta Kafka real verifica además que un abreviado termina en DLT sin inbox.

## Mensajes inválidos

Un error transitorio admite reintentos limitados. Un mensaje estructuralmente inválido no se arregla esperando: termina en DLT, separada para auditoría y analytics. Si publicar la DLT falla, no se debe confirmar el original como procesado. El runbook debe explicar causa, reparación y replay explícito; una DLT sin dueño es almacenamiento de fallos olvidados.

La evolución debe mantener forma y semántica compatibles. Aquí los schemas y el codec rechazan campos desconocidos: incluso añadir un campo opcional requiere actualizar primero consumidores o publicar una versión distinta. No se presupone tolerancia automática. Cambiar el significado del importe o su unidad exige una decisión de versión. Un esquema valida forma y algunas restricciones; no demuestra que un hecho corresponda a una transacción autorizada. El test compara eventos realmente producidos, además de fixtures.

## Ventanas y deduplicación

[OrderSummaryTopology](../../apps/order-analytics/src/main/java/dev/reference/analytics/OrderSummaryTopology.java) resume pedidos por tenant, SKU y ventana de un minuto usando `occurredAt`, el tiempo del hecho. La tolerancia de treinta segundos permite llegar fuera de orden respecto al avance de stream time. No significa esperar treinta segundos de reloj tras cada mensaje.

[![Validación, deduplicación y resumen de pedidos](../diagrams/order-summary-flow.svg)](../diagrams/order-summary-flow.svg)

[Maestro draw.io](../diagrams/architecture.drawio). Editar el `.drawio` con draw.io y volver a exportar el SVG.

El topic de salida publica actualizaciones de una tabla: la misma clave de ventana puede aparecer varias veces con un agregado más reciente. No sumar esas actualizaciones como si fueran hechos distintos. El resultado cuenta creaciones, aunque después exista una devolución; llamarlo ventas netas sería incorrecto. La deduplicación dura siete días, acorde al contrato de retención. Un replay más antiguo requiere otra política.

El store local mejora acceso, mientras el changelog permite reconstrucción. No es una copia de seguridad universal: depende de retención, identidad de aplicación y disponibilidad de Kafka. El laboratorio verifica reinicio y restauración; la topología determinista verifica ventanas y tardíos sin dormir.

El consumidor limita cada poll a 100 registros y pausa por partición al superar un umbral de 100 en su cola; un lote puede excederlo. No son límites globales de memoria o caudal. Observar lag y capacidad antes de añadir tareas. `StreamsLifecycle` prueba arranque RUNNING hasta 60 s y cierre solicitado hasta 20 s, con readiness HTTP/archivo retirado durante rebalance y parada. Compose y Kubernetes conceden 30 s de terminación. Un timeout se hace visible, sin declarar completado el cierre.

Analytics configura `exactly_once_v2`: offsets, changelog y salida se confirman dentro de la transacción Kafka. Sus consumidores deben leer con `isolation.level=read_committed`. Esta atomicidad no incluye PostgreSQL ni HTTP; eventId sigue siendo necesario para reconocer publicaciones repetidas del mismo hecho de negocio.

## Relojes incorrectos y cuarentena

El tiempo de evento decide cuándo se cierra una ventana. Un `occurredAt` de 2099 puede adelantar el tiempo del task y convertir pedidos actuales en descartes tardíos. [OrderSummaryTopology](../../apps/order-analytics/src/main/java/dev/reference/analytics/OrderSummaryTopology.java) aplica una tolerancia máxima de treinta segundos respecto al reloj del proceso antes de asignar timestamp. Un inválido conserva el tiempo previo de partición (cero al inicio) y va a DLT: tampoco se confía en su timestamp Kafka. Solo la rama DLT asigna hora de cuarentena, para que su retención no nazca vencida. El límite exacto se admite; [la regresión](../../apps/order-analytics/src/test/java/dev/reference/analytics/OrderSummaryTopologyTest.java) comprueba que el siguiente pedido actual sigue agregándose. Sincronizar relojes y revisar DLT; el replay del pasado sigue sujeto a grace/retención, no se convierte en una reconstrucción histórica automática.

## Micropráctica: no perder el mensaje que falló

**Pregunta:** ¿avanza el consumidor si tampoco funciona la DLT? Leer [KafkaConfiguration](../../apps/commerce-api/src/main/java/dev/reference/commerce/shared/infrastructure/messaging/KafkaConfiguration.java), [CommerceIntegrationTest](../../apps/commerce-api/src/test/java/dev/reference/commerce/CommerceIntegrationTest.java) y [KafkaRecoveryTest](../../apps/order-analytics/src/test/java/dev/reference/analytics/KafkaRecoveryTest.java). Desde la raíz, con Java 21 y Docker:

```bash
(cd apps/commerce-api && \
  DOCKER_HOST="$(docker context inspect --format '{{.Endpoints.docker.Host}}')" \
  TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
  ./mvnw -B -ntp -Dapi.version=1.44 '-Dtest=CommerceIntegrationTest#failedDltPublicationDoesNotCommitOriginalOffsetAndRecovers' test)
(cd apps/order-analytics && \
  DOCKER_HOST="$(docker context inspect --format '{{.Endpoints.docker.Host}}')" \
  TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
  ./mvnw -B -ntp -Dapi.version=1.44 -Dtest=KafkaRecoveryTest test)
```

**Resultado:** dos integraciones aprobadas. La primera inyecta el fallo únicamente en el envío DLT: el offset original no avanza ni se procesa el mensaje posterior; recuperar permite ambos. La segunda envía duplicados explícitos por particiones distintas antes y después de perder RocksDB; el resumen no se infla.

**Alternativa:** omitir el mensaje deja avanzar, pero pierde evidencia. Elegir responsable y procedimiento de reproceso. Con `make up` y `make demo`, el escenario además correlaciona su pedido actual con outbox, inbox y ventana Kafka; no toma el primer resultado histórico ni suma actualizaciones. `make kafka-summary` exige `ORDER_ID` de ese pedido.

Fuentes: [Kafka y retención por timestamp](https://kafka.apache.org/40/implementation/log/), [TimestampExtractor](https://kafka.apache.org/40/javadoc/org/apache/kafka/streams/processor/TimestampExtractor.html), [Kafka Streams conceptos](https://kafka.apache.org/40/streams/core-concepts/), [Streams testing](https://kafka.apache.org/40/streams/developer-guide/testing/), [outbox](https://microservices.io/patterns/data/transactional-outbox.html).
