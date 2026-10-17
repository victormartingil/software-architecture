# Contratos HTTP y paginación acotada

## Problema y decisión

Una API puede devolver JSON válido y romper a sus consumidores: cambia un estado, permite un campo que antes rechazaba o lista recursos de otro usuario. El contrato debe describir esas decisiones y tener una comprobación ejecutable. En esta referencia, [OpenAPI de Comercio](../../contracts/http/commerce.openapi.json) es la fuente canónica HTTP; [la especificación](../specification.md) explica las invariantes y límites. Un contrato formal no sustituye pruebas de autorización ni una transacción correcta.

Los controladores de pedidos, inventario y devoluciones adaptan cada entrada a su puerto de aplicación. El JSON conserva nombres y estados anteriores. `quantity` y `version` requieren literales enteros: cadenas como `"2"` y números escritos como `1.9` o `2.0` producen 400, en vez de conversiones silenciosas. OpenAPI/JSON Schema considera entero el valor matemático 2.0; esa restricción de representación se indica en la descripción y se comprueba en HTTP. Los campos extra de entrada se toleran por compatibilidad; las respuestas conservan esquema acotado. No hay una clase compartida que concentre todas las rutas de negocio. Las llamadas del asistente usan operaciones por intención; solo el adaptador HTTP conoce rutas y verbos. Que una integración use HTTP no obliga a exponer `request(method, path)` en su puerto.

## Significado antes que transporte

El dominio expresa errores como NOT_FOUND o CONFLICT, sin importar Spring ni devolver un status HTTP. `ProblemAdvice` traduce esos significados a 404 o 409. Otro adaptador podría traducir el mismo fallo a un resultado de mensajería. Esta separación evita que la regla «aprobación vencida» dependa del protocolo con el que se ejecuta.

Las respuestas de error mantienen Problem Details: status, title, detail y correlationId, con mensajes controlados. No devuelven el cuerpo del proveedor ni un stacktrace. Un fallo de autorización y una dependencia caída no son equivalentes: preservar 401/403/404/409 cuando corresponde permite que el consumidor decida correctamente. Un 503 no autoriza a repetir cualquier escritura: hace falta identidad idempotente y una política explícita.

## Un listado pequeño y estable

`GET /api/v1/orders` acepta `limit` de 1 a 50, predeterminado 10, `after` UUID opcional y filtro `status` PLACED o REFUNDED. Devuelve `items` y `nextCursor`; este último es nulo al terminar. El orden es fijo por id ascendente. No ofrece un lenguaje genérico de filtros ni permite ordenar por cualquier columna.

La consulta aplica tenant y propietario en SQL; MANAGER ve únicamente su tenant. Recupera como máximo límite más uno: la fila adicional indica si existe otra página. El cursor procede del último elemento devuelto, no de la fila adicional. Un cursor no concede permisos; la consulta vuelve a aplicar el aislamiento en cada petición.

El orden por UUID es determinista, pero no representa fecha de creación. Las páginas no son una instantánea: escrituras concurrentes pueden aparecer detrás de un cursor ya recorrido o cambiar la pertenencia a un filtro. Si el consumidor necesita una exportación consistente, hacen falta requisitos distintos. No se promete un total exacto ni se añade una consulta count para esta capacidad.

El cursor acota la respuesta, no garantiza un coste constante. Antes de reutilizar el [listado JDBC](../../apps/commerce-api/src/main/java/dev/reference/commerce/orders/infrastructure/persistence/JdbcOrderRepositoryAdapter.java) con mucho volumen, examinar `EXPLAIN (ANALYZE, BUFFERS)` sobre datos representativos. Índices compuestos que empiecen por tenant y, según consulta, actor pueden reducir filas recorridas; también cuestan escritura y espacio. El filtro status y su selectividad cambian el plan. No se atribuye rendimiento industrial al dataset pequeño.

La alternativa offset es más sencilla para saltar a un número de página, pero necesita razonar sobre coste y desplazamientos ante cambios. El cursor sirve bien a la navegación secuencial de este caso; no es una solución universal para cualquier catálogo.

## Micropráctica: contrato y aislamiento reales

**Pregunta:** ¿un JSON correcto puede contener pedidos del tenant equivocado? Leer el OpenAPI enlazado, [OrderQuery](../../apps/commerce-api/src/main/java/dev/reference/commerce/orders/application/model/OrderQuery.java) y [CommerceIntegrationTest](../../apps/commerce-api/src/test/java/dev/reference/commerce/CommerceIntegrationTest.java). Desde la raíz, con Java 21 y Docker:

```bash
(cd apps/commerce-api && \
  DOCKER_HOST="$(docker context inspect --format '{{.Endpoints.docker.Host}}')" \
  TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
  ./mvnw -B -ntp -Dapi.version=1.44 '-Dtest=CommerceIntegrationTest#orderPagesUseTenantOwnerAndKeyset+httpContractMatchesActualRoutesAndResponses' test)
```

**Resultado esperado:** paginación, filtro y aislamiento aprobados con PostgreSQL real; respuestas contrastadas con el contrato y negativos de forma rechazados. Los valores límite y cursores inválidos producen 400. La comprobación contractual debe detectar una respuesta alterada, mientras la de autorización demuestra quién puede obtenerla. No se requieren claves de IA ni datos externos.

**Alternativa:** generar clientes desde OpenAPI puede aportar valor si existen consumidores que los necesitan. Aquí no se añade un generador ni modelos duplicados: el contrato se verifica contra el comportamiento. Una modificación de ruta, seguridad, entrada o respuesta exige actualizar contrato y pruebas en el mismo cambio.

Fuentes: [PostgreSQL EXPLAIN](https://www.postgresql.org/docs/16/using-explain.html), [OpenAPI](https://spec.openapis.org/oas/v3.1.0), [JSON Schema](https://json-schema.org/draft/2020-12), [Problem Details](https://www.rfc-editor.org/rfc/rfc9457).
