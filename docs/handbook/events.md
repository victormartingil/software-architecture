# Eventos fiables y tiempo de procesamiento

## Publicar después de confirmar

Guardar un pedido y enviar Kafka son dos escrituras en sistemas distintos. Si la primera funciona y la segunda falla, el consumidor nunca recibe el hecho. Enviar primero invierte el problema: puede publicarse un pedido que luego no existe. `@Async` cambia el hilo, sin crear atomicidad.

El outbox guarda el evento junto al pedido. Un publicador reclama lotes pendientes con `FOR UPDATE SKIP LOCKED`, envía y espera confirmación del broker antes de marcarlos. Si cae tras enviar y antes de marcar, publicará de nuevo. La garantía es entrega al menos una vez dentro de las condiciones de retención y recuperación; el consumidor debe tolerar duplicados.

El consumidor de auditoría usa una tabla inbox con `UNIQUE(eventId)`. Registrar identidad y efecto se hace en una transacción; el offset se confirma después. Un reinicio puede repetir un mensaje pero la restricción evita un segundo efecto. La identidad de evento debe mantenerse en los reintentos y en el reproceso. Generar un UUID nuevo convierte la repetición en otro hecho.

El contrato vive en [orders.placed.v1](../../contracts/events/orders.placed.v1.schema.json). Tenant, correlación, versión y tiempo pertenecen al sobre; el contenido contiene solo datos sintéticos necesarios. La clave Kafka es orderId para conservar orden por pedido dentro de su partición. No existe un orden total entre las tres particiones.

## Mensajes inválidos

Un error transitorio admite reintentos limitados. Un mensaje estructuralmente inválido no se arregla esperando: termina en DLT, separada para auditoría y analytics. Si publicar la DLT falla, no se debe confirmar el original como procesado. El runbook debe explicar causa, reparación y replay explícito; una DLT sin dueño es almacenamiento de fallos olvidados.

La evolución debe mantener forma y semántica compatibles. Aquí los schemas y el codec rechazan campos desconocidos: incluso añadir un campo opcional requiere actualizar primero consumidores o publicar una versión distinta. No se presupone tolerancia automática. Cambiar el significado del importe o su unidad exige una decisión de versión. Un esquema valida forma y algunas restricciones; no demuestra que un hecho corresponda a una transacción autorizada. El test compara eventos realmente producidos, además de fixtures.

## Ventanas y deduplicación

[OrderSummaryTopology](../../apps/order-analytics/src/main/java/com/reference/analytics/OrderSummaryTopology.java) resume pedidos por tenant, SKU y ventana de un minuto usando `occurredAt`, el tiempo del hecho. La tolerancia de treinta segundos permite llegar fuera de orden respecto al avance de stream time. No significa esperar treinta segundos de reloj tras cada mensaje.

[![Validación, deduplicación y resumen de pedidos](../diagrams/order-summary-flow.svg)](../diagrams/order-summary-flow.svg)

[Maestro draw.io](../diagrams/architecture.drawio). Editar el `.drawio` con draw.io y volver a exportar el SVG.

El topic de salida publica actualizaciones de una tabla: la misma clave de ventana puede aparecer varias veces con un agregado más reciente. No sumar esas actualizaciones como si fueran hechos distintos. El resultado cuenta creaciones, aunque después exista una devolución; llamarlo ventas netas sería incorrecto. La deduplicación dura siete días, acorde al contrato de retención. Un replay más antiguo requiere otra política.

El store local mejora acceso, mientras el changelog permite reconstrucción. No es una copia de seguridad universal: depende de retención, identidad de aplicación y disponibilidad de Kafka. El laboratorio verifica reinicio y restauración; la topología determinista verifica ventanas y tardíos sin dormir.

Analytics configura `exactly_once_v2`: offsets, changelog y salida se confirman dentro de la transacción Kafka. Sus consumidores deben leer con `isolation.level=read_committed`. Esta atomicidad no incluye PostgreSQL ni HTTP; eventId sigue siendo necesario para reconocer publicaciones repetidas del mismo hecho de negocio.

## Reproducción

```bash
make demo
make kafka-summary
cd apps/order-analytics && ./mvnw verify
```

El test real pausa el broker para comprobar conservación del outbox y prueba deduplicación de consumidor. TopologyTestDriver controla timestamps explícitos y el test con Kafka comprueba reconstrucción. Sus garantías y resultados exactos están en [verificación](../verification.md).

Fuentes: [Kafka Streams conceptos](https://kafka.apache.org/40/streams/core-concepts/), [Streams testing](https://kafka.apache.org/40/streams/developer-guide/testing/), [outbox](https://microservices.io/patterns/data/transactional-outbox.html).
