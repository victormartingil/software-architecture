# 6. Coordinar una devolución con aprobación

[← Anterior](retrieval.md) · [Índice del manual](../index.md) · [Siguiente →](security.md)

El capítulo 5 permitió consultar políticas. Consultar una política y autorizar una devolución son operaciones distintas. Ahora se estudia el flujo que guarda progreso, espera al responsable y continúa sin duplicar el efecto.

**Objetivo:** distinguir un punto de control del estado de negocio y explicar por qué la reanudación conserva claves idempotentes y vuelve a comprobar permisos.

<a id="topic-una-propuesta-no-es-un-permiso"></a>

## Separar solicitud, aprobación y ejecución

Recuperar una política o generar una recomendación no autoriza una devolución. La API de comercio conserva las reglas y comprueba al actor en cada operación. El flujo coordina pasos y recuerda progreso; no otorga roles ni escribe directamente en tablas de negocio.

Un flujo de trabajo (workflow) coordina varios pasos. Al crearlo, el asistente llama a Comercio con el JWT del usuario y una clave idempotente. Comercio registra una propuesta PENDING (pendiente) para un pedido visible. LangGraph representa los pasos como un grafo: cada nodo realiza una tarea y cada conexión indica qué paso viene después. Guarda el progreso y se pausa. Un MANAGER del mismo tenant puede aprobar; otro tenant recibe 404 y un cliente sin rol recibe 403. Después se recupera la versión aprobada y se ejecuta con una clave estable derivada de la devolución.

[![Secuencia de devolución con aprobación humana](../diagrams/return-sequence.svg)](../diagrams/return-sequence.svg)

El [puerto de Comercio](../../apps/ai-service/src/ai_service/application/ports.py) expresa operaciones de devolución y devuelve `ReturnResult` tipado. El [adaptador HTTP](../../apps/ai-service/src/ai_service/infrastructure/commerce.py) conoce rutas, verbos y JSON; valida identidad, estado y versión antes de devolver el resultado. Una respuesta malformada se traduce en error de dependencia, sin registrar su cuerpo ni reintentar escrituras arbitrariamente. No es necesario un módulo desplegable adicional para esta frontera.

El JWT se usa transitoriamente para la llamada y no se guarda en puntos de control. Guardarlo convertiría el almacén del flujo en una colección de credenciales reutilizables. La identidad de propietario y tenant sí debe persistir para controlar consultas y reanudaciones.

<a id="topic-estado-y-fallo-entre-sistemas"></a>

## Reanudar tras una respuesta perdida

[![Estados de negocio de una devolución](../diagrams/return-states.svg)](../diagrams/return-states.svg)

Este diagrama describe el estado de negocio. Una nueva aprobación explícita cambia versión y vencimiento; no es una repetición idempotente de la misma aprobación. El vencimiento se verifica como condición de ejecución, sin un estado EXPIRED persistido. Un punto de control (checkpoint) es una copia guardada del progreso del grafo para poder pausar y reanudar; puede guardar IDs y un estado observado, pero no es la fuente de verdad del negocio ni concede permisos. No existe commit atómico entre la llamada HTTP y el checkpoint: comercio puede confirmar y la respuesta perderse. Al reintentar, la clave estable devuelve el efecto anterior y el backend vuelve a validar la autorización. LangGraph no proporciona por sí solo «exactamente una ejecución» sobre sistemas externos.

Al reanudar un `interrupt`, LangGraph vuelve a ejecutar el nodo desde su principio. Cualquier efecto anterior a la pausa debe admitir repetición. Aquí el nodo de espera solo interrumpe; propuesta y ejecución usan claves estables frente a reinicios o respuestas perdidas. La pausa se conserva entre peticiones HTTP distintas: no mantiene una conexión abierta esperando al responsable. Véanse las [reglas de reanudación e idempotencia de interrupts](https://docs.langchain.com/oss/python/langgraph/interrupts#side-effects-called-before-interrupt-must-be-idempotent).

La versión es un contador de cambios, no la versión de la API. Si se aprueba una propuesta con versión 0, Comercio devuelve la versión 1; ejecutar todavía con 0 debe fallar. El flujo consulta la versión aprobada para ejecutar con ella. Una propuesta aprobada no se edita silenciosamente; cualquier cambio requiere nueva aprobación. La caducidad de diez minutos limita una autorización que dejó de representar la intención actual. El backend aplica estos controles dentro de su transacción, incluso si se invoca sin pasar por AI.

## Persistencia y escalado

El [workflow](../../apps/ai-service/src/ai_service/infrastructure/workflows.py) usa SQLite y un proceso para que el ejemplo sea inspeccionable y persistente. El volumen permite reiniciar la aplicación. Un bloqueo (lock) de asyncio solo coordina tareas de ese proceso. Los cambios se procesan uno detrás de otro: máximo dieciséis operaciones en curso o en cola y doce segundos de plazo total, incluida la espera del bloqueo. Saturación o vencimiento devuelven 503; cancelación libera cupo. Así una dependencia bloqueada no acumula una cola ilimitada. No hay garantía de rendimiento aislado por tenant ni se puede añadir otra réplica y asumir exclusión distribuida.

Para varias réplicas se necesita un almacén compartido de puntos de control, registro idempotente persistente y transición condicionada por versión/estado o bloqueo distribuido. Cambiar únicamente SQLite por PostgreSQL no resuelve carreras entre dos aprobaciones. Kubernetes conserva una réplica AI y escala el backend de comercio; la restricción está documentada antes del despliegue.

Un flujo determinista —con pasos definidos por el programa— es suficiente aquí. Un agente autónomo tendría sentido si existieran decisiones abiertas justificadas y evaluables. Añadir herramientas aumenta superficie de autorización y riesgo; MCP es un protocolo para intercambiar contexto y exponer herramientas a aplicaciones de IA; no reemplaza permisos ni aislamiento.

## Práctica guiada: recuperar progreso sin repetir el efecto

### 1. Seguir el código

Leer el workflow enlazado, [ManageReturnService](../../apps/commerce-api/src/main/java/dev/reference/commerce/returns/application/service/ManageReturnService.java) y [test_workflows.py](../../apps/ai-service/tests/test_workflows.py). Desde la raíz:

### 2. Predecir el resultado

Comercio ejecuta una devolución, pero se pierde su respuesta. ¿Reanudar el flujo debería reponer otra vez el stock? Anotar una respuesta antes de ejecutar los comandos.

### 3. Ejecutar y comparar

```bash
(cd apps/ai-service && uv run pytest tests/test_workflows.py -q)
```

**Qué observar:** las pruebas deben pasar con una conexión simulada a Comercio y SQLite temporal; no requieren Docker ni claves. Reconstruyen el grafo desde el checkpoint, simulan respuesta perdida y caducidad entre aprobación y ejecución. También bloquean una dependencia y prueban plazo de cola, rechazo por capacidad, liberación tras cancelación y reintento con la misma clave. La propuesta no se ejecuta sin autorización vigente y el JWT no aparece en el almacén. Este nivel prueba coordinación, sin acreditar por sí solo transacciones SQL del backend.

Con Docker, `make up` y `make demo` prueban JWT reales de Keycloak, aprobación, reintento y reposición de stock una vez; `make verify` añade vencimiento y concurrencia de comercio. Ambos comandos se ejecutan desde la raíz.

**Alternativa:** una máquina de estados propia puede ser suficiente para este flujo pequeño. ¿Qué aporta LangGraph aquí? Persistencia y reanudación inspeccionables; no autoridad de negocio ni exclusión entre réplicas.

## Documentación del capítulo

[LangGraph persistencia](https://docs.langchain.com/oss/python/langgraph/persistence), [interrupciones](https://docs.langchain.com/oss/python/langgraph/interrupts), [OWASP autorización](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html).

## Comprobar lo aprendido

¿Guardar el JWT del responsable en el punto de control facilitaría una reanudación segura?

<details>
<summary>Ver la explicación</summary>
<p>No. Guardaría una credencial reutilizable. El flujo conserva progreso e identidades; las nuevas peticiones aportan el token y Comercio comprueba los permisos vigentes.</p>
</details>

El flujo conserva progreso sin asumir autoridad de negocio. El siguiente capítulo desarrolla los permisos y los límites de las llamadas externas.

---

[← 5. Buscar documentos y evaluar respuestas de IA](retrieval.md) · [Índice](../index.md) · [7. Aplicar permisos y limitar los fallos →](security.md)
