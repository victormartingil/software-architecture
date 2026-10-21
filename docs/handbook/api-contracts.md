# 2. Definir contratos HTTP y paginar resultados

[← Anterior](architecture.md) · [Índice del manual](../index.md) · [Siguiente →](transactions.md)

El capítulo 1 separó el caso de uso de su adaptador HTTP. Este capítulo estudia ese adaptador desde el punto de vista del cliente: qué datos acepta, qué errores devuelve y cómo permite recorrer los pedidos.

**Objetivo:** leer un contrato OpenAPI, distinguir errores de negocio y HTTP y explicar una página con cursor.

## Definir qué acepta y devuelve la API

Una API puede devolver JSON válido y dejar de ser compatible con sus clientes: cambia un estado, permite un campo que antes rechazaba o lista recursos de otro usuario. El contrato debe describir esas decisiones y tener una comprobación ejecutable. OpenAPI describe rutas, entradas y respuestas en un formato que pueden leer las herramientas. JSON Schema define la forma y las restricciones de los datos JSON. En esta referencia, [OpenAPI de Comercio](../../contracts/http/commerce.openapi.json) es la fuente canónica HTTP; [la especificación](../specification.md) explica las invariantes y límites. Un contrato formal no sustituye pruebas de autorización ni una transacción correcta.

Los controladores de pedidos, inventario y devoluciones adaptan cada entrada a su puerto de aplicación. Los nombres y estados del JSON forman parte del contrato. `quantity` y `version` requieren literales enteros: cadenas como `"2"` y números escritos como `1.9` o `2.0` producen 400, en vez de conversiones silenciosas. OpenAPI/JSON Schema considera entero el valor matemático 2.0; esa restricción sobre la escritura del número se indica en la descripción y se comprueba en HTTP. Los campos extra de entrada se toleran por compatibilidad; las respuestas siguen un esquema definido. Las llamadas del asistente usan operaciones por intención; solo el adaptador HTTP conoce rutas y verbos. Que una integración use HTTP no obliga a exponer `request(method, path)` en su puerto.

<a id="topic-significado-antes-que-transporte"></a>

## Traducir errores de negocio a HTTP

El dominio expresa errores como NOT_FOUND o CONFLICT, sin importar Spring ni devolver un status HTTP. `ProblemAdvice` traduce esos significados a 404 o 409. Otro adaptador podría traducir el mismo fallo a un resultado de mensajería. Esta separación evita que la regla «aprobación vencida» dependa del protocolo con el que se ejecuta.

Las respuestas de error mantienen Problem Details: status, title, detail y correlationId, con mensajes controlados. No devuelven el cuerpo del proveedor ni un stacktrace. Los códigos distinguen la causa: 401 para credenciales ausentes o inválidas, 403 para permisos insuficientes, 404 para un recurso no visible y 409 para un conflicto de negocio. 400 indica una entrada inválida y 503, indisponibilidad temporal del servicio, por ejemplo por saturación, plazo agotado o fallo de una dependencia. Un 503 no autoriza a repetir cualquier escritura: hace falta identidad idempotente y una política explícita.

<a id="topic-un-listado-pequeno-y-estable"></a>

## Paginar pedidos con un cursor

Un cursor identifica dónde continuar una lista; cada página devuelve solo una parte de los resultados. `GET /api/v1/orders` acepta `limit` de 1 a 50, predeterminado 10, `after` UUID opcional y filtro `status` PLACED o REFUNDED. Devuelve `items` y `nextCursor`; este último es nulo al terminar. El orden es fijo por id ascendente. No ofrece un lenguaje genérico de filtros ni permite ordenar por cualquier columna.

La consulta aplica tenant y propietario en SQL; MANAGER ve únicamente su tenant. Recupera como máximo límite más uno: la fila adicional indica si existe otra página. El cursor procede del último elemento devuelto, no de la fila adicional. Un cursor indica una posición, pero no concede permisos; la consulta vuelve a aplicar el aislamiento en cada petición.

El orden por UUID es determinista, pero no representa fecha de creación. Las páginas no son una instantánea: escrituras concurrentes pueden aparecer detrás de un cursor ya recorrido o cambiar la pertenencia a un filtro. Si el consumidor necesita una exportación consistente, hacen falta requisitos distintos. No se promete un total exacto ni se añade una consulta count para esta capacidad.

Ejemplo ilustrativo con tres pedidos autorizados ordenados `A < B < C`: las letras representan UUID completos, no cursores válidos para copiar.

| Petición | Filas leídas | Elementos devueltos | nextCursor |
|---|---|---|---|
| limit=2 | A, B, C | A, B | B |
| limit=2, after=B | C | C | null |

La fila C permite detectar otra página en la primera petición; devolverla también haría que la página superase su límite. El cursor B solo expresa una posición: permisos y filtros vuelven a aplicarse en la segunda consulta.

Para reconocer la respuesta real, este ejemplo ilustrativo pide `GET /api/v1/orders?limit=1` y supone que hay más de un pedido visible. HTTP 200 indica que la consulta se ha completado:

```json
{
  "items": [{
    "id": "11111111-1111-4111-8111-111111111111",
    "sku": "BOOK-1",
    "quantity": 1,
    "totalCents": 2500,
    "status": "PLACED",
    "version": 0
  }],
  "nextCursor": "11111111-1111-4111-8111-111111111111"
}
```

`items` contiene los pedidos de esta página; `totalCents` expresa céntimos de euro (2500 = 25 €). Para continuar, enviar el mismo límite y `after=11111111-1111-4111-8111-111111111111`. Cuando no quedan más resultados después de la página, `nextCursor` es `null`, aunque esa última página contenga pedidos.

El cursor acota la respuesta, no garantiza un coste constante. Antes de reutilizar el [listado JDBC](../../apps/commerce-api/src/main/java/dev/reference/commerce/orders/infrastructure/persistence/JdbcOrderRepositoryAdapter.java) con mucho volumen, examinar `EXPLAIN (ANALYZE, BUFFERS)` sobre datos representativos. Índices compuestos que empiecen por tenant y, según consulta, actor pueden reducir filas recorridas; también cuestan escritura y espacio. El filtro status y su selectividad cambian el plan. No se atribuye rendimiento industrial al dataset pequeño.

La alternativa offset es más sencilla para saltar a un número de página, pero necesita razonar sobre coste y desplazamientos ante cambios. El cursor sirve bien a la navegación secuencial de este caso; no es una solución universal para cualquier catálogo.

## Práctica guiada: contrato y aislamiento reales

### 1. Seguir el código

Leer el OpenAPI enlazado, [OrderQuery](../../apps/commerce-api/src/main/java/dev/reference/commerce/orders/application/model/OrderQuery.java) y [CommerceIntegrationTest](../../apps/commerce-api/src/test/java/dev/reference/commerce/CommerceIntegrationTest.java). Desde la raíz, con Java 21 y Docker:

### 2. Predecir el resultado

Si se leen tres filas para devolver una página de dos elementos, ¿qué identificador debe enviarse como cursor? Anotar una respuesta antes de ejecutar los comandos.

### 3. Ejecutar y comparar

```bash
(cd apps/commerce-api && \
  DOCKER_HOST="$(docker context inspect --format '{{.Endpoints.docker.Host}}')" \
  TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
  ./mvnw -B -ntp -Dapi.version=1.44 '-Dtest=CommerceIntegrationTest#orderPagesUseTenantOwnerAndKeyset+httpContractMatchesActualRoutesAndResponses' test)
```

**Qué observar:** paginación, filtro y aislamiento aprobados con PostgreSQL real; respuestas contrastadas con el contrato y negativos de forma rechazados. Los valores límite y cursores inválidos producen 400. La comprobación contractual debe detectar una respuesta alterada, mientras la de autorización demuestra quién puede obtenerla. La práctica no necesita claves de IA ni datos externos.

**Alternativa:** generar clientes desde OpenAPI puede aportar valor si existen consumidores que los necesitan. Aquí no se añade un generador ni modelos duplicados: el contrato se verifica contra el comportamiento. Una modificación de ruta, seguridad, entrada o respuesta exige actualizar contrato y pruebas en el mismo cambio.

## Documentación del capítulo

[PostgreSQL EXPLAIN](https://www.postgresql.org/docs/17/using-explain.html), [OpenAPI](https://spec.openapis.org/oas/v3.1.0), [JSON Schema: tipos numéricos y enteros](https://json-schema.org/understanding-json-schema/reference/numeric), [Problem Details](https://www.rfc-editor.org/rfc/rfc9457).

## Comprobar lo aprendido

En el ejemplo A, B, C, ¿qué ocurre si la primera página devuelve A y B pero utiliza C como cursor?

<details>
<summary>Ver la explicación</summary>
<p>La segunda consulta empezaría después de C y se saltaría ese pedido. El cursor debe ser B, el último elemento realmente devuelto.</p>
</details>

El contrato describe lo que espera el cliente. El siguiente capítulo comprueba cómo se conserva ese resultado ante concurrencia y reintentos.

---

[← 1. Organizar el código en módulos, puertos y adaptadores](architecture.md) · [Índice](../index.md) · [3. Proteger datos con transacciones e idempotencia →](transactions.md)
