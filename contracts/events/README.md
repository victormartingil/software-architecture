# Contratos de eventos

JSON Schema 2020-12, fixtures sintéticos y versiones explícitas. Los cambios aditivos también requieren coordinar consumidores: estos contratos rechazan campos desconocidos para detectar divergencia. Un cambio incompatible exige nuevo topic/schema y migración explícita.

`orders.placed.v1`: key orderId, `eventId` estable e inmutable por hecho, aggregateId = data.orderId. IDs UUID; tenant y correlationId sin datos personales. Una propiedad cruzada como aggregateId = orderId se comprueba en código además del schema.

`orders.summary.v1`: actualizaciones por clave `[tenantId,sku]@windowStartMillis`. Mantener último resultado, no sumar actualizaciones. Ventanas UTC de un minuto, fecha final exclusiva; no representa ventas netas después de devoluciones.

Fixtures se validan en `EventSchemaTest` de order-analytics. El productor debe validar su evento realmente serializado, además del fixture. La retención y particiones se configuran en infraestructura, no en JSON Schema.
