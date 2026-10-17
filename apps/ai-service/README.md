# AI service

API del asistente: aplicación Python que responde con documentos autorizados y coordina devoluciones. FastAPI recibe HTTP; `ReturnApprovalWorkflow` usa LangGraph para reanudar pasos. `JwtVerifier` comprueba el token localmente con claves públicas de Keycloak; no realiza el login. Los puertos `AnswerProvider` y `DocumentRetriever` distinguen generar de buscar evidencia.

[Mapa de aplicaciones y sistemas de apoyo](../../docs/repository-design.md#component-map).

Servicio pequeño para consultar políticas sintéticas y coordinar una devolución. El modelo propone texto; la API de comercio conserva la autoridad sobre permisos, versiones, aprobación de diez minutos y efectos transaccionales. No hay herramientas accesibles desde el endpoint de preguntas.

## Ejecutar y comprobar

Desde esta carpeta:

```sh
uv sync --frozen
WORKFLOW_DB=./state/workflows.sqlite uv run uvicorn ai_service.main:app --port 8081
uv run pytest -q
uv run lint-imports
uv run python -m ai_service.evaluate
```

El entorno completo se arranca mediante el Compose raíz. `/health` y `/metrics` son públicos para operación local; las APIs requieren JWT OIDC. Un único worker utiliza el volumen SQLite. No ejecutar múltiples réplicas con el mismo archivo: la variante local es persistente, no un coordinador distribuido.

| Configuración | Uso |
|---|---|
| `OIDC_ISSUER`, `OIDC_AUDIENCE` | Issuer público exacto y audiencia del JWT |
| `OIDC_JWKS_URL` | Dirección interna de claves RSA, independiente del issuer público |
| `COMMERCE_URL` | Base HTTP de comercio; default `http://commerce-api:8080` |
| `WORKFLOW_DB` | Archivo SQLite persistente; default `/data/workflows.sqlite` |
| `AI_PROVIDER` | `fake` predeterminado, `openai` optativo |
| `OPENAI_API_KEY`, `OPENAI_MODEL` | Credencial y modelo explícitos, sin default real |
| `AI_MAX_REQUESTS` | Cupo por adaptador y proceso; generación y embeddings tienen contadores distintos. Incluye reintentos de generación; default cero |
| `AI_LIVE_BUDGET_USD` | Presupuesto declarado positivo obligatorio para modo real |
| `VECTOR_DSN` | Conexión PostgreSQL con extensión pgvector |
| `EMBEDDING_MODEL`, `EMBEDDING_DIMENSIONS` | Modelo y dimensión explícitos para recuperación vectorial |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OTLP HTTP optativo, por ejemplo `http://otel-collector:4318` |
| `TEST_VECTOR_DSN` | Base desechable dedicada; el test trunca `ai_documents` |

El presupuesto USD expresa la intención del operador; no calcula una factura ni sustituye límites de gasto del proveedor. El cupo de solicitudes se aplica por adaptador en proceso y se reinicia con él: con ambos adaptadores activos pueden consumirse hasta dos cupos. No es un contador global persistente. Preguntas de hasta 2000 caracteres, 512 tokens máximos de salida, cuatro respuestas concurrentes y plazo global de doce segundos acotan cada operación. Antes de activar un modelo real deben comprobarse precios y fijarse límites en la cuenta del proveedor. CI no usa claves ni red de IA.

## Recuperación y respuestas

`POST /api/v1/answers` recibe `question` y `retrievalMode` (`lexical` o `vector`). La búsqueda léxica filtra por tenant antes de puntuar y devuelve hasta tres fragmentos; ausencia de coincidencias produce abstención sin llamar al proveedor. No hay caché compartida. La simulación devuelve una cita existente y se identifica siempre como `fake-simulation`, incluso al abstenerse.

El adaptador OpenAI usa Responses, `store:false` y un JSON Schema estricto. El caso de uso vuelve a validar referencias contra los fragmentos autorizados. Rechazo, respuesta incompleta y salida inválida se convierten en error de dependencia, sin inventar una respuesta. Solo errores de transporte, 429 y 5xx admiten un segundo intento. El circuit breaker abre tras tres operaciones fallidas y permite una prueba de recuperación a treinta segundos.

`POST /api/v1/answers/stream` emite SSE `start` y después `result` validado o `error`. Es streaming de estado de la petición, no tokens incrementales. Desconectar cancela la tarea y libera el semáforo; ningún contenido parcial autoriza efectos.

La variante vectorial se habilita con DSN, modelo de embeddings, clave, cupo y presupuesto declarado; sin esa configuración devuelve 503. Ejecuta distancia coseno con filtros SQL de tenant y dimensión. La tabla se crea con `data/vector-schema.sql`; la ingesta es administrativa y debe usar el mismo modelo/dimensión configurados al consultar. Para este corpus pequeño se usa búsqueda exacta; un índice ANN requeriría medir recall bajo filtrado. La prueba PostgreSQL utiliza vectores fixture, sin facturar embeddings:

```sh
TEST_VECTOR_DSN=postgresql://... uv run pytest tests/test_vector.py -q
```

## Workflow y fallos

`POST /api/v1/workflows/returns`, con `Idempotency-Key`, crea una propuesta en comercio. Un UUID derivado de tenant/actor/clave identifica el workflow; reutilizar la clave con otro cuerpo produce 409. LangGraph persiste estado y pausa en un `interrupt`. `GET /api/v1/workflows/{id}` exige propietario o manager del mismo tenant.

`POST /api/v1/workflows/{id}/approve` exige MANAGER, reanuda el grafo y transmite el JWT actual a comercio. Usa la versión devuelta al aprobar; comercio verifica de nuevo vencimiento y versión al ejecutar. La clave `execute:<returnId>` permanece estable. Si comercio confirma el efecto pero se pierde la respuesta, el siguiente intento consulta su estado antes de actuar. Cada petición explícita de MANAGER a `/approve` solicita una aprobación vigente al backend, incluso después de vencer la anterior. No existe renovación automática en segundo plano; un vencimiento entre aprobar y ejecutar sigue produciendo conflicto.

JWT y correlación se pasan por contexto de ejecución, fuera del estado y configuración persistida. El test reinicia el checkpointer, pierde deliberadamente una respuesta y comprueba tanto efecto único como ausencia de credenciales en el archivo SQLite.

## Límites y evidencia

El dataset contiene 41 casos fijos y mide recuperación, aislamiento y abstención. No mide veracidad de un modelo real ni resistencia universal a prompt injection. La separación de herramientas, permisos backend y filtros previos son controles verificables; las instrucciones al modelo son una defensa adicional.

Las pruebas incluyen JWKS RSA, issuer/audience/expiración/firma, contratos HTTP simulados, cancelación SSE, límites, recuperación del breaker y pgvector real optativo. Import-linter tiene una prueba negativa que introduce una dependencia prohibida en una copia temporal. Las trazas OTLP no capturan cuerpos ni cabeceras sensibles; métricas describen resultados y duración sin etiquetas de usuarios o preguntas.

Referencias: [Responses estructurado](https://developers.openai.com/api/docs/guides/structured-outputs), [persistencia LangGraph](https://docs.langchain.com/oss/python/langgraph/persistence), [pgvector](https://github.com/pgvector/pgvector), [validación PyJWT](https://pyjwt.readthedocs.io/en/stable/usage.html).
