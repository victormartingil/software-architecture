# Transacciones, idempotencia y concurrencia

## La invariante primero

Dos compradores pueden leer stock uno al mismo tiempo. Si ambos restan en memoria y guardan cero, se venden dos unidades aunque el contador parezca correcto. La regla relevante es que cada pedido aceptado corresponde a una reserva efectiva, sin stock negativo. Una transacción no arregla por sí sola una secuencia de lecturas y escrituras mal diseñada.

El [adaptador de inventario](../../apps/commerce-api/src/main/java/dev/reference/commerce/inventory/infrastructure/persistence/JdbcInventoryRepositoryAdapter.java) usa una actualización condicional: descontar solo si `stock >= quantity`, comprobando filas afectadas. PostgreSQL coordina escrituras concurrentes sobre la misma fila. La reserva, el pedido, el registro idempotente y el evento outbox se confirman en una transacción local. Precio e importe se calculan en servidor; el cliente no puede enviar un total arbitrario.

[![Secuencia de pedido y publicación del evento](../diagrams/order-sequence.svg)](../diagrams/order-sequence.svg)

[Maestro draw.io](../diagrams/architecture.drawio). Editar el `.drawio` con draw.io y volver a exportar el SVG.

## Esquema que sostiene las garantías

[![Modelo físico de las siete tablas de Comercio](../diagrams/commerce-data.svg)](../diagrams/commerce-data.svg)

[Maestro draw.io](../diagrams/architecture.drawio). PK identifica una fila; FK exige una referencia existente. La cardinalidad se lee izquierda:derecha: un pedido tiene cero o una propuesta y cada propuesta corresponde a un pedido. Solo se dibujan las dos FK declaradas en Flyway; las referencias lógicas sin FK se enumeran aparte.

## Repetir una petición es normal

El cliente puede perder la respuesta después del commit. Repetir la solicitud no debería duplicar pedido ni reserva. `Idempotency-Key` identifica la intención del cliente, dentro del tenant, actor, operación y recurso. Se almacena también el hash del cuerpo: reutilizar una clave para otra intención produce 409. La misma intención recupera el resultado anterior. La identidad del actor viene del JWT, no del JSON.

El puerto recibe un ámbito estructurado y el [adaptador idempotente](../../apps/commerce-api/src/main/java/dev/reference/commerce/shared/infrastructure/persistence/JdbcIdempotencyAdapter.java) lo codifica como array JSON. Concatenar con `:` permitiría confundir componentes legítimos. La versión V2 rechaza migrar si hay claves antiguas ambiguas: no las borra ni permite repetir efectos con otro prefijo; [ADR002](../decisions/002-consistency.md) explica el límite y el reset sintético explícito.

La implementación serializa solicitudes con la misma clave dentro de PostgreSQL y guarda el resultado en la misma transacción del efecto. Una caché en memoria no bastaría ante reinicios o varias réplicas. Tampoco basta comprobar «si existe» y luego insertar sin constraint o exclusión: dos transacciones podrían pasar la comprobación.

La devolución tiene una invariante semejante. Una aprobación se vincula a la versión y al contenido de una propuesta, con caducidad. La ejecución comprueba de nuevo estado, versión y fecha dentro de la transacción; registra devolución, cambia pedido y repone inventario una sola vez. Una respuesta perdida se recupera con una clave estable de ejecución. Se trata de un reembolso sintético: no hay transferencia de dinero ni transacción con un proveedor de pagos.

## Alternativas y costes

Bloquear la fila antes de decidir puede simplificar reglas más complejas; aumenta el tiempo de espera y exige orden consistente de bloqueos. Una versión optimista sirve cuando los conflictos son infrecuentes y el cliente puede reintentar. El update condicional expresa bien este caso pequeño. No se escoge un nivel de aislamiento más costoso por costumbre: se razona sobre anomalías concretas y se prueba la invariante.

Mantener inventario y pedidos en la misma base evita una transacción distribuida. Si fueran servicios independientes, la reserva necesitaría protocolo, estado intermedio, caducidad y compensación. Una saga coordina esas etapas, pero no ofrece el rollback instantáneo de una transacción SQL.

## Micropráctica: una reserva y un resultado durables

**Pregunta:** ¿qué ocurre si falla el outbox después de reservar? Seguir el adaptador de inventario y el idempotente enlazados, después [CommerceIntegrationTest](../../apps/commerce-api/src/test/java/dev/reference/commerce/CommerceIntegrationTest.java). Desde la raíz, con Java 21 y Docker:

```bash
(cd apps/commerce-api && \
  DOCKER_HOST="$(docker context inspect --format '{{.Endpoints.docker.Host}}')" \
  TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
  ./mvnw -B -ntp -Dapi.version=1.44 '-Dtest=CommerceIntegrationTest#priceAndIdempotency+concurrentLastUnit+rollbackIncludesStockAndOrder+subjectsWithDelimitersCannotShareCachedResultsEvenConcurrently' test)
```

**Resultado:** cuatro pruebas aprobadas, usando PostgreSQL y Kafka efímeros. Una clave repetida conserva el resultado; dos compradores no venden dos veces la última unidad. El fallo controlado añade temporalmente un constraint que rechaza outbox: deben quedar cero pedidos y stock intacto. La carrera con `:` conserva dos identidades distintas. No modifica volúmenes Compose.

**Alternativa:** mocks verifican coordinación, pero no prueban bloqueos ni aislamiento SQL. ¿Seguiría siendo una transacción local si inventario estuviera en otro servicio? Serían necesarios estados intermedios y compensación, no otra anotación.

En operación se debe definir retención de claves, volumen de outbox, tiempo de bloqueo y estrategia de migración compatible. No borrar registros idempotentes durante un plazo en el que clientes todavía puedan repetir solicitudes.

Fuentes: [PostgreSQL concurrencia](https://www.postgresql.org/docs/16/mvcc.html), [bloqueos explícitos](https://www.postgresql.org/docs/16/explicit-locking.html), [Spring transacciones](https://docs.spring.io/spring-framework/reference/data-access/transaction.html).
