# ADR 002 — Confirmación local, entrega y repetición

**Estado:** aceptado. **Contexto:** PostgreSQL y Kafka no comparten commit. Clientes y consumidores pueden perder respuestas y repetir.

**Decisión:** reservar stock, guardar pedido, resultado idempotente y outbox dentro de PostgreSQL. Publicar con confirmación antes de marcar. Consumidor registra inbox y efecto en transacción, después offset. Claves vinculadas a tenant/actor/operación/recurso y hash del cuerpo. El puerto recibe un `Scope` estructurado; el adaptador codifica sus cinco componentes como array JSON y almacena `scope_version=2`. No se concatena texto con delimitadores. Eventos conservan eventId normalizado UUID.

**Alternativas:** envío directo deja ventana de pérdida; publicar primero deja hechos fantasma. Una transacción Kafka puede atomizar operaciones dentro de Kafka, sin incluir automáticamente tablas PostgreSQL. CDC sería opción de mayor volumen con más infraestructura.

**Consecuencias:** se admiten mensajes repetidos y se controla su efecto. Outbox necesita observación y mantenimiento. Streams deduplica siete días y emite actualizaciones de agregados, no ventas netas. El replay histórico requiere otro procedimiento y retención explícita.

**Comprobación:** ORD/EVT/STR y fallo broker/restart; [eventos](../handbook/events.md).

**Migración de ámbitos:** V2 bloquea la tabla y rechaza la migración si existen registros V1: el formato concatenado es ambiguo y no permite reconstruir identidades con seguridad. No se borra ni reinterpreta ninguna clave ni se arranca la nueva aplicación sobre ese historial; cambiar solo el prefijo permitiría repetir efectos ya aplicados. La versión obligatoria sin valor por defecto rechaza también escrituras del binario antiguo tras una migración vacía. Es una actualización con parada de escritores, no un despliegue mixto compatible.

En este laboratorio, la única salida soportada si hay datos V1 es un **reset explícito completo del entorno sintético**, aceptando la pérdida conjunta de pedidos, idempotencia y estado derivado; véase el [runbook](../handbook/operations.md). No vaciar solo `idempotency` ni usar `flyway repair` para eludir el guard. No se ejecuta reset automáticamente. Con datos reales se requeriría un plan específico de reconciliación que esta referencia no implementa.

**Fuentes:** [migraciones y rollback transaccional de Flyway](https://documentation.red-gate.com/fd/migrations-271585107.html), [bloqueos de PostgreSQL](https://www.postgresql.org/docs/current/explicit-locking.html). Una colisión del hash del lock solo serializa más operaciones; la búsqueda usa la clave estructurada completa.
