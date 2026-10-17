# ADR 002 — Confirmación local, entrega y repetición

**Estado:** aceptado. **Contexto:** PostgreSQL y Kafka no comparten commit. Clientes y consumidores pueden perder respuestas y repetir.

**Decisión:** reservar stock, guardar pedido, resultado idempotente y outbox dentro de PostgreSQL. Publicar con confirmación antes de marcar. Consumidor registra inbox y efecto en transacción, después offset. Keys vinculadas a tenant/actor/operación/hash. Eventos conservan eventId normalizado UUID.

**Alternativas:** envío directo deja ventana de pérdida; publicar primero deja hechos fantasma. Una transacción Kafka puede atomizar operaciones dentro de Kafka, sin incluir automáticamente tablas PostgreSQL. CDC sería opción de mayor volumen con más infraestructura.

**Consecuencias:** se admiten mensajes repetidos y se controla su efecto. Outbox necesita observación y mantenimiento. Streams deduplica siete días y emite actualizaciones de agregados, no ventas netas. El replay histórico requiere otro procedimiento y retención explícita.

**Comprobación:** ORD/EVT/STR y fallo broker/restart; [eventos](../handbook/events.md).
