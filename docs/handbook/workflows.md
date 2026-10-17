# Workflow persistente y aprobación de efectos

## Una propuesta no es un permiso

Recuperar una política o generar una recomendación no autoriza una devolución. La API de comercio conserva las reglas y comprueba al actor en cada operación. El workflow coordina pasos y recuerda progreso; no otorga roles ni escribe directamente en tablas de negocio.

Crear un workflow llama a comercio con el JWT del usuario y una clave idempotente. Comercio registra una propuesta PENDING para un pedido visible. El grafo persiste y se pausa. Un MANAGER del mismo tenant puede aprobar; otro tenant recibe 404 y un cliente sin rol recibe 403. Después se recupera la versión aprobada y se ejecuta con una clave estable derivada de la devolución.

[![Secuencia de devolución con aprobación humana](../diagrams/return-sequence.svg)](../diagrams/return-sequence.svg)

[Maestro draw.io](../diagrams/architecture.drawio). Editar el `.drawio` con draw.io y volver a exportar el SVG.

El [puerto de Comercio](../../apps/ai-service/src/ai_service/application/ports.py) expresa operaciones de devolución y devuelve `ReturnResult` tipado. El [adaptador HTTP](../../apps/ai-service/src/ai_service/infrastructure/commerce.py) conoce rutas, verbos y JSON; valida identidad, estado y versión antes de devolver el resultado. Una respuesta malformada se traduce en error de dependencia, sin registrar su cuerpo ni reintentar escrituras arbitrariamente. No es necesario un módulo desplegable adicional para esta frontera.

El JWT se usa transitoriamente para la llamada y no se guarda en checkpoints. Guardarlo convertiría el almacén del workflow en una colección de credenciales reutilizables. La identidad de propietario y tenant sí debe persistir para controlar consultas y reanudaciones.

## Estado y fallo entre sistemas

[![Estados de negocio de una devolución](../diagrams/return-states.svg)](../diagrams/return-states.svg)

[Maestro draw.io](../diagrams/architecture.drawio). Editar el `.drawio` con draw.io y volver a exportar el SVG.

Este diagrama describe el estado de negocio. Una nueva aprobación explícita cambia versión y vencimiento; no es una repetición idempotente de la misma aprobación. El vencimiento se verifica como condición de ejecución, sin un estado EXPIRED persistido. El grafo tiene además checkpoints de coordinación. No existe commit atómico entre la llamada HTTP y el checkpoint: comercio puede confirmar y la respuesta perderse. Al reintentar, la clave estable devuelve el efecto anterior. LangGraph no proporciona por sí solo «exactamente una ejecución» sobre sistemas externos.

La aprobación cambia versión. Ejecutar con la versión anterior debe fallar. Una propuesta aprobada no se edita silenciosamente; cualquier cambio requiere nueva aprobación. La caducidad de diez minutos limita una autorización que dejó de representar la intención actual. El backend aplica estos controles dentro de su transacción, incluso si se invoca sin pasar por AI.

## Persistencia y escalado

El [workflow](../../apps/ai-service/src/ai_service/infrastructure/workflows.py) usa SQLite y un proceso para que el ejemplo sea inspeccionable y persistente. El volumen permite reiniciar la aplicación. Un lock asyncio solo coordina tareas de ese proceso. Las mutaciones se serializan deliberadamente: máximo dieciséis operaciones en curso o en cola y doce segundos de plazo total, incluida la espera del lock. Saturación o vencimiento devuelven 503; cancelación libera cupo. Así una dependencia bloqueada no acumula una cola ilimitada. No hay garantía de rendimiento aislado por tenant ni se puede añadir otra réplica y asumir exclusión distribuida.

Para varias réplicas se necesita un checkpointer compartido, registro idempotente durable y transición condicionada por versión/estado o bloqueo distribuido. Cambiar únicamente SQLite por PostgreSQL no resuelve carreras entre dos aprobaciones. Kubernetes conserva una réplica AI y escala el backend de comercio; la restricción está documentada antes del despliegue.

Un flujo determinista es suficiente aquí. Un agente autónomo tendría sentido si existieran decisiones abiertas justificadas y evaluables. Añadir herramientas aumenta superficie de autorización y riesgo; MCP estandariza intercambio, sin reemplazar permisos ni aislamiento.

## Micropráctica: recuperar progreso sin repetir el efecto

**Pregunta:** ¿quién decide cuando una aprobación caduca entre dos llamadas? Leer el workflow enlazado, [ManageReturnService](../../apps/commerce-api/src/main/java/dev/reference/commerce/returns/application/service/ManageReturnService.java) y [test_workflows.py](../../apps/ai-service/tests/test_workflows.py). Desde la raíz:

```bash
(cd apps/ai-service && uv run pytest tests/test_workflows.py -q)
```

**Resultado:** pruebas rápidas aprobadas, con gateway simulado y SQLite temporal; no requieren Docker ni claves. Reconstruyen el grafo desde el checkpoint, simulan respuesta perdida y caducidad entre aprobación y ejecución. También bloquean una dependencia y prueban plazo de cola, rechazo por capacidad, liberación tras cancelación y reintento con la misma clave. La propuesta no se ejecuta sin autorización vigente y el JWT no aparece en el almacén. Este nivel prueba coordinación, sin acreditar por sí solo transacciones SQL del backend.

Con Docker, `make up` y `make demo` prueban JWT reales de Keycloak, aprobación, reintento y reposición de stock una vez; `make verify` añade vencimiento y concurrencia de comercio. Ambos comandos se ejecutan desde la raíz.

**Alternativa:** una máquina de estados propia puede ser suficiente para este flujo pequeño. ¿Qué aporta LangGraph aquí? Persistencia y reanudación inspeccionables; no autoridad de negocio ni exclusión entre réplicas.

Fuentes: [LangGraph persistencia](https://docs.langchain.com/oss/python/langgraph/persistence), [interrupciones](https://docs.langchain.com/oss/python/langgraph/interrupts), [OWASP autorización](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html).
