# Recuperación, generación y evaluación

## Evidencia antes de redactar

Una respuesta fluida puede ser falsa. RAG (Retrieval-Augmented Generation, generación aumentada por recuperación) busca primero evidencia en documentos autorizados y después entrega esa evidencia al generador. Recuperar documentos y redactar una respuesta son dos pasos distintos; una base vectorial es una opción de recuperación, no un requisito de RAG. El corpus es pequeño y sintético: no hace falta comenzar por embeddings. El baseline lexical permite aislar la calidad de recuperación del comportamiento generativo.

El [recuperador](../../apps/ai-service/src/ai_service/infrastructure/retrieval.py) filtra por tenant antes de seleccionar los tres documentos más relevantes. El tenant procede del JWT validado. No se recupera de todos los usuarios para filtrar después de generar: el contenido ya habría cruzado una frontera de seguridad. No hay endpoint público de subida de documentos ni caché compartida entre tenants.

[![Recuperación autorizada y validación de respuestas](../diagrams/answer-flow.svg)](../diagrams/answer-flow.svg)

[Maestro draw.io](../diagrams/architecture.drawio). Editar el `.drawio` con draw.io y volver a exportar el SVG.

[AnswerService](../../apps/ai-service/src/ai_service/application/answers.py) conoce los puertos de recuperación y generación. Los adaptadores concretos se ensamblan en la entrada FastAPI. Una respuesta solo puede citar identificadores realmente recuperados; una cita existente no demuestra que el texto esté respaldado semánticamente. Esa segunda propiedad necesita evaluación del modelo y revisión del contenido.

## Simulación y proveedor real

`fake` es el modo predeterminado y se identifica en la respuesta. Hace el entorno reproducible sin claves ni gasto; verifica integración, permisos, formato y flujo de errores. No acredita calidad, seguridad lingüística ni coste de un modelo real.

El adaptador OpenAI usa Responses con salida estructurada, modelo explícito y límite de salida. Valida sobre JSON, tipos, rechazo y contenido completo antes de registrar éxito en el breaker: `answer` textual, `abstained` booleano y citas de documentos suministrados. Recuperación/aplicación conserva la autorización por tenant; el adaptador no la decide. Un HTTP 200 con JSON inválido produce 503 y cuenta como fallo, sin reintentar ese contenido. La [regresión del proveedor](../../apps/ai-service/tests/test_providers.py) verifica tres fallos, apertura, prueba de recuperación inválida y posterior recuperación válida. El plazo total incluye cola; presupuesto de generación, como máximo dos intentos transitorios y circuit breaker acotan fallos. La configuración live exige presupuesto declarado y límite de solicitudes. El presupuesto en USD no es un contador de facturación: precios, tokens de entrada y tarifas del modelo deben comprobarse antes de una evaluación real. El cupo se aplica por adaptador/proceso y se reinicia al arrancar: generación y embeddings llevan contadores distintos, sin presupuesto global persistente.

La variante pgvector valida dimensión de embeddings y filtra tenant en SQL antes de ordenar por distancia. Los tests usan vectores fixture contra PostgreSQL/pgvector real para verificar aislamiento y orden, sin pagar embeddings. La ingesta de un corpus con embeddings reales requiere configuración deliberada. Búsqueda lexical y vectorial deben compararse sobre las mismas preguntas; una variante híbrida sería una ampliación posterior. Un índice vectorial no garantiza mejores respuestas.

## Qué medir

El [dataset](../../apps/ai-service/data/evaluation.json) incluye preguntas respondibles, ausencia de evidencia, accesos cruzados e instrucciones maliciosas. Los identificadores relevantes se etiquetan explícitamente. Recall@3 mide qué proporción de documentos relevantes aparece en los tres recuperados, para casos respondibles. Hit-rate@3 indica si apareció al menos un relevante; MRR@3 promedia el inverso del puesto del primer relevante. Recuperar uno de dos relevantes produce recall 0,5 y hit-rate 1. Abstenciones y aislamiento se reportan por separado; no se mezclan para inflar una media.

La evaluación es pequeña, sintética y cercana al corpus. Una puntuación perfecta no implica generalización. Para un modelo real añadir fidelidad a evidencia, corrección de citas, abstención apropiada, latencia p95, tokens y coste por tarea completada. Conservar modelo, versión de prompt, dataset y juicio humano. Un juez LLM puede ayudar, pero hereda sesgos y no sustituye pruebas deterministas de permisos.

## Streaming con una garantía pequeña

SSE emite inicio y un resultado completo ya validado. Permite enseñar conexión, terminación y cancelación sin permitir acciones a partir de texto parcial. No es streaming token a token. Desconectar cancela la tarea y libera el semáforo; la prueba ASGI verifica esa ruta. Un cliente debe distinguir un evento `error` de una respuesta válida.

## Micropráctica: acertar el conjunto no asegura el primer puesto

**Pregunta:** ¿qué métrica descubre un relevante que llega tercero? Leer el recuperador enlazado, [evaluate.py](../../apps/ai-service/src/ai_service/evaluate.py) y [test_evaluate.py](../../apps/ai-service/tests/test_evaluate.py). Desde la raíz:

```bash
make eval
(cd apps/ai-service && uv run pytest tests/test_evaluate.py tests/test_answers.py -q)
```

**Resultado del dataset actual:** 42 casos, 32 respondibles, recall@3 e hit-rate@3 de 1 y MRR@3 de 0,953125. Son mediciones de recuperación offline, sin evaluar modelo real. La prueba multirrelevancia fuerza una recuperación incompleta y distingue recall de hit-rate; otra rechaza una cita no recuperada. Las pruebas rápidas no incluyen PostgreSQL: `make verify` también comprueba pgvector real.

**Alternativa:** comparar embeddings o búsqueda híbrida con las mismas etiquetas, sin asumir que mayor complejidad mejora resultados. Antes de cambiar el ranking, preguntar si el problema es ausencia de evidencia, orden de documentos o fidelidad de generación: requieren controles distintos.

Fuentes: [Responses](https://platform.openai.com/docs/api-reference/responses), [salidas estructuradas](https://developers.openai.com/api/docs/guides/structured-outputs), [pgvector](https://github.com/pgvector/pgvector), [FastAPI respuestas](https://fastapi.tiangolo.com/advanced/custom-response/).

## Lectura y ejemplo de referencia

Lectura: [*AI Engineering*, de Chip Huyen](https://www.oreilly.com/library/view/ai-engineering/9781098166298/), para diseño, evaluación y operación de aplicaciones con modelos fundacionales. [La guía de recuperación de LlamaIndex](https://github.com/run-llama/llama_index/blob/main/docs/examples/low_level/retrieval.ipynb) muestra código de retrieval en otro stack (52.539 estrellas, consultadas el 17-10-2026); sirve para comparar estrategias, no para sustituir los filtros de autorización de esta referencia.
