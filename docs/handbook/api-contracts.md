# 2. Definir contratos HTTP y paginar resultados

[← Anterior](architecture.md) · [Índice de la referencia](../index.md) · [Siguiente →](transactions.md)

El capítulo 1 separó el caso de uso de su adaptador HTTP. Este capítulo estudia ese adaptador desde el punto de vista del cliente: qué datos acepta, qué errores devuelve y cómo permite recorrer los pedidos.

**Objetivo:** leer un contrato OpenAPI, distinguir errores de negocio y HTTP y explicar una página con cursor.

## Definir qué acepta y devuelve la API

Una API puede devolver JSON válido y dejar de ser compatible con sus clientes: cambia un estado, permite un campo que antes rechazaba o lista recursos de otro usuario. El contrato debe describir esas decisiones y tener una comprobación ejecutable. OpenAPI describe rutas, entradas y respuestas en un formato que pueden leer las herramientas. JSON Schema define la forma y las restricciones de los datos JSON. En esta referencia, [OpenAPI de Comercio](../../contracts/http/commerce.openapi.json) es la fuente canónica HTTP; [la especificación](../specification.md) explica las invariantes y límites. Un contrato formal no sustituye pruebas de autorización ni una transacción correcta.

Los controladores de pedidos, inventario y devoluciones adaptan cada entrada a su puerto de aplicación. Los nombres y estados del JSON forman parte del contrato. `quantity` y `version` requieren literales enteros; por ejemplo, `2` es válido y `"2"` produce 400. Los campos extra de entrada se toleran por compatibilidad; las respuestas siguen un esquema definido.

<details markdown="1">
<summary>Por qué el literal 2.0 requiere una comprobación HTTP</summary>

También se rechazan `1.9` y `2.0`, en vez de convertirlos silenciosamente. OpenAPI/JSON Schema considera entero el valor matemático 2.0; la restricción sobre su escritura se indica en la descripción y se comprueba en HTTP. Un validador de esquema no acredita por sí solo esa condición del transporte.

</details>

Las llamadas del asistente usan operaciones por intención; solo el adaptador HTTP conoce rutas y verbos. Que una integración use HTTP no obliga a exponer `request(method, path)` en su puerto.

<a id="topic-significado-antes-que-transporte"></a>

<a id="ref-topic-r07"></a>

## Traducir errores de negocio a HTTP

El dominio expresa errores como NOT_FOUND o CONFLICT, sin importar Spring ni devolver un status HTTP. `ProblemAdvice` traduce esos significados a 404 o 409. Otro adaptador podría traducir el mismo fallo a un resultado de mensajería. Esta separación evita que la regla «aprobación vencida» dependa del protocolo con el que se ejecuta.

Las respuestas de error mantienen Problem Details: status, title, detail y correlationId, con mensajes controlados. No devuelven el cuerpo del proveedor ni un stacktrace. Los códigos distinguen la causa: 401 para credenciales ausentes o inválidas, 403 para permisos insuficientes, 404 para un recurso no visible y 409 para un conflicto de negocio. 400 indica una entrada inválida y 503, indisponibilidad temporal del servicio, por ejemplo por saturación, plazo agotado o fallo de una dependencia. Un 503 no autoriza a repetir cualquier escritura: hace falta identidad idempotente y una política explícita.

<a id="topic-un-listado-pequeno-y-estable"></a>

<a id="ref-topic-r08"></a>

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

<a id="practica-guiada-contrato-y-aislamiento-reales"></a>
<a id="práctica-guiada-contrato-y-aislamiento-reales"></a>

<a id="ref-topic-r09"></a>

## Comprobación reproducible: contrato y aislamiento reales

### 1. Seguir el código

Leer el OpenAPI enlazado, [OrderQuery](../../apps/commerce-api/src/main/java/dev/reference/commerce/orders/application/model/OrderQuery.java) y [CommerceIntegrationTest](../../apps/commerce-api/src/test/java/dev/reference/commerce/CommerceIntegrationTest.java). En la prueba, localizar `orderPagesUseTenantOwnerAndKeyset` para permisos y cursor, y `httpContractMatchesActualRoutesAndResponses` para la forma de las respuestas. Desde la raíz, con Java 21 y Docker:

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

<a id="exercise-java-pagination"></a>

## Caso de aplicación: reconstruir la página en Java

En una copia de estudio, reconstruir solo el cuerpo de [ManageOrderService.list](../../apps/commerce-api/src/main/java/dev/reference/commerce/orders/application/service/ManageOrderService.java), sin mirar primero su implementación. Conservar firma, puertos, consulta SQL y contrato HTTP. `orders.list(actor, query)` ya devuelve como máximo `limit + 1` filas autorizadas; el ejercicio transforma ese resultado, no vuelve a filtrar permisos en memoria.

**Predicción:** con `limit=2`, anotar tamaño de `items` y cursor para cero filas, `[A]`, `[A,B]` y `[A,B,C]`, donde las letras representan pedidos ordenados. **Entregable:** el método Java y esas cuatro predicciones. **Pista:** comparar el tamaño leído antes de limitar; convertir con `OrderResult.from` y obtener el último elemento solo si existe continuación.

Con JDK 21 y Docker, contrastar la variante mediante `orderPagesUseTenantOwnerAndKeyset` usando el comando de la comprobación anterior. Sin Docker, comparar razonadamente con la fuente, sin afirmar que se han probado SQL o permisos. Terminado cuando no se devuelve la fila adicional, no se omiten pedidos y una lista vacía no intenta acceder a su último elemento. Restaurar la implementación de referencia al acabar.

<details markdown="1">
<summary>Ver una resolución razonada</summary>

```java
public OrderPage list(Actor actor, OrderQuery query) {
  var candidates = orders.list(actor, query);
  boolean hasMore = candidates.size() > query.limit();
  var items = candidates.stream().limit(query.limit()).map(OrderResult::from).toList();
  return new OrderPage(items, hasMore ? items.getLast().id() : null);
}
```

Los cuatro resultados son `0/null`, `1/null`, `2/null` y `2/B`. El condicional solo evalúa `getLast` cuando `hasMore` es verdadero; con límites positivos y sobrelectura eso implica elementos devueltos. `Stream.limit` acota la página, `map` adapta el modelo y `toList` recoge el resultado. No sustituyen el aislamiento aplicado en SQL ni convierten las páginas en una instantánea.

</details>

<a id="documentacion-del-capitulo"></a>
<a id="documentación-del-capítulo"></a>

## Documentación de la sección

[PostgreSQL EXPLAIN](https://www.postgresql.org/docs/17/using-explain.html), [OpenAPI](https://spec.openapis.org/oas/v3.1.0), [JSON Schema: tipos numéricos y enteros](https://json-schema.org/understanding-json-schema/reference/numeric), [Problem Details](https://www.rfc-editor.org/rfc/rfc9457).

## Para profundizar

**Primero:** [Core Java II, 14.ª, apartados 1.3–1.4](../bibliography.md#book-b1-ii), para `map` y límite del stream. **Alternativa:** [Spring Start Here, 2.ª MEAP, 10.3.3](../bibliography.md#book-b2), para REST y excepciones; numeración provisional. Terminado cuando se explica el cursor y se separa error de dominio de transporte. [Correspondencias de contratos](../bibliography.md#chapters-api-contracts).

<a id="comprobar-lo-aprendido"></a>

## Pregunta de diseño

En el ejemplo A, B, C, ¿qué ocurre si la primera página devuelve A y B pero utiliza C como cursor?

<details markdown="1">
<summary>Ver la explicación</summary>
<p>La segunda consulta empezaría después de C y se saltaría ese pedido. El cursor debe ser B, el último elemento realmente devuelto.</p>
</details>

El contrato describe lo que espera el cliente. El siguiente capítulo comprueba cómo se conserva ese resultado ante concurrencia y reintentos.

---

[← 1. Organizar el código en módulos, puertos y adaptadores](architecture.md) · [Índice](../index.md) · [3. Proteger datos con transacciones e idempotencia →](transactions.md)
