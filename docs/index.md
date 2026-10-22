<a id="manual-de-arquitectura-de-software-e-integracion-de-ia"></a>
<a id="manual-de-arquitectura-de-software-e-integración-de-ia"></a>

# Referencia de arquitectura de software e integración de IA

Esta referencia documenta decisiones implementadas sobre una tienda de ejemplo para responder a preguntas habituales de un backend: dónde colocar las reglas de negocio, cómo evitar ventas sin stock, cómo publicar eventos sin perderlos y cómo utilizar IA sin darle permisos que no le corresponden.

Las decisiones implementadas se conectan con las comprobaciones que permiten contrastarlas; las propuestas de evolución se identifican expresamente. La lectura del código y los casos reproducibles explican por qué funciona, qué ocurre cuando algo falla y qué habría que cambiar ante otros requisitos. El propósito es consultar, repasar y compartir criterios técnicos con otros desarrolladores.

## A quién va dirigido

A desarrolladores que conocen programación, peticiones HTTP y consultas SQL básicas. Para seguir el código conviene poder leer clases Java y funciones Python; no es necesario dominar Spring Boot, Kafka o LangGraph antes de empezar.

No hace falta instalar herramientas para leer las explicaciones y los diagramas. El [capítulo 0](getting-started.md) prepara el entorno para las comprobaciones e introduce a los usuarios y las aplicaciones del ejemplo.

<a id="como-estudiar-cada-capitulo"></a>
<a id="cómo-estudiar-cada-capítulo"></a>

## Cómo utilizar esta referencia

1. Identificar el problema, las restricciones y la decisión que se analiza.
2. Seguir el recorrido por los archivos enlazados: entrada, regla de negocio y acceso al sistema externo.
3. Predecir el resultado de la comprobación antes de ejecutarla.
4. Comparar la salida con la explicación y razonar sobre la pregunta de diseño final.
5. Resolver los [casos de regla de dominio](handbook/architecture.md#topic-ejercicio-regla-de-dominio), [carrera de stock](handbook/transactions.md#topic-ejercicio-carrera-de-stock) y [pregunta de recuperación](handbook/retrieval.md#topic-ejercicio-caso-de-recuperacion) en sus secciones, antes de abrir la resolución razonada.
6. Consultar las lecturas complementarias cuando se necesite más profundidad. Al terminar, resolver el [caso integrador](handbook/delivery.md#topic-caso-integrador) y contrastarlo con su rúbrica.

Las comprobaciones reproducibles contrastan la implementación; los casos de aplicación ofrecen ejercicios, pistas y resoluciones para producir una solución y justificarla. No hace falta construir otra aplicación. Se puede seguir el recorrido completo, cuyas primeras secciones se apoyan unas en otras, o utilizar el [mapa de referencia](reference-map.md) para consultar una decisión concreta. Ambos recorridos sirven para repasar y compartir las explicaciones.

La [bibliografía recomendada](bibliography.md) reúne lecturas y ejemplos asociados a cada problema. Es una biblioteca de consulta: no hace falta comprar ni leer todos los libros para seguir el recorrido. Para una duda concreta, empezar por la lectura breve del final del capítulo y usar el catálogo solo si hace falta ampliar.

<a id="study-java-spring"></a>

## Repasar Java y Spring en tres sesiones

Sesiones orientativas de unos 45 minutos de trabajo activo; descargas y arranque pueden necesitar tiempo adicional. Empezar con JDK 21 y una copia de trabajo. Docker solo es necesario para las comprobaciones de integración. Si se puede resolver la predicción y justificar el código sin mirar, pasar a la siguiente sesión y consultar el libro solo ante una duda.

| Sesión | Leer y predecir · 10 min | Escribir y contrastar · 25 min | Cierre · 10 min |
|---|---|---|---|
| 1. Reglas y contratos Java | [Responsabilidades](handbook/architecture.md#topic-problema-y-decision): ¿qué conoce Spring y qué conoce SQL? | [Reconstruir Quantity](handbook/architecture.md#topic-ejercicio-regla-de-dominio) sin mirar y ejecutar CommerceDomainTest, sin Docker. Seguir después constructor, interfaces y adaptador. | Explicar por qué la validación HTTP no basta y qué protege el dominio frente a lo que protege SQL. Consulta: [records y constructores](bibliography.md#refs-r06). |
| 2. Colecciones y paginación | [Cursor y sobrelectura](handbook/api-contracts.md#topic-un-listado-pequeno-y-estable): predecir 0, limit y limit+1 filas. | [Reconstruir list](handbook/api-contracts.md#exercise-java-pagination); comparar con la fuente. Con Docker, ejecutar la prueba enfocada enlazada. | Explicar qué fila determina el cursor, dónde se filtran permisos y por qué no hay snapshot. Consulta: [streams y acceso a datos](bibliography.md#refs-r08). |
| 3. Spring y transacciones | [Última unidad](handbook/transactions.md#topic-dos-peticiones-y-la-ultima-unidad): predecir commit y rollback. | [Reconstruir la frontera transaccional](handbook/transactions.md#exercise-spring-transaction) y contrastar proxy frente a llamada directa, con la prueba existente si hay Docker. | Explicar qué garantiza Spring, qué exige SQL y cómo se relaciona la [idempotencia](handbook/transactions.md#topic-repetir-una-peticion-es-normal). Consulta: [transacciones](bibliography.md#refs-r10). |

Los ejercicios modifican solo una copia de estudio y conservan contratos. Volver a la implementación de referencia al terminar; no introducir variantes de aprendizaje en una entrega. No hace falta ejecutar Kubernetes, Terraform ni un modelo real.

### Continuar con una prioridad de backend senior

Después de las tres sesiones, seguir [transacciones](handbook/transactions.md) → [Kafka](handbook/events.md#exercise-kafka-grace) → [seguridad](handbook/security.md) → [observabilidad](handbook/operations.md#exercise-promql) → [Python/IA](handbook/retrieval.md#exercise-python-provider) y [workflows](handbook/workflows.md). Dejar [Kubernetes y Terraform](handbook/delivery.md) como ampliación opcional. Antes de pasar de tema, explicar la garantía, producir una pequeña solución y reconocer su límite; consultar el libro cuando quede una duda concreta.

Intentar primero sin IA ni solución, pedir una pista si se bloquea el razonamiento y usar la IA después para revisar la propuesta y plantear un contraejemplo. Días después repetir una variante sin mirar: recordar el mecanismo y justificarlo importa más que reconocer una respuesta al leerla. La corrección se contrasta con código, documentación y pruebas, también cuando la respuesta viene de una IA.

Para consultar la referencia con buscador, índice lateral y modo claro u oscuro, ejecutar `make docs` desde la raíz, con uv instalado. Abrir la dirección local que indique el comando en la terminal. También se puede seguir completo desde los enlaces Markdown de GitHub.

<a id="orden-de-estudio"></a>

## Recorrido recomendado

| Sección | Pregunta que resuelve | Criterios y comprobaciones |
|---|---|---|
| [0. Preparación y primer pedido](getting-started.md) | ¿Qué hace el ejemplo y cómo se ejecuta? | Aplicaciones, usuarios y resultado de la demostración. |
| [1. Organización del código](handbook/architecture.md) | ¿Dónde debe vivir cada responsabilidad? | Recorrido del caso de uso y límites entre módulos, puertos y adaptadores. |
| [2. Contratos HTTP](handbook/api-contracts.md) | ¿Cómo se define lo que una API acepta y devuelve? | Errores y paginación conservando los permisos. |
| [3. Transacciones y concurrencia](handbook/transactions.md) | ¿Qué ocurre si dos compradores piden la última unidad? | Atomicidad, bloqueo e idempotencia sobre la última unidad. |
| [4. Eventos y Kafka Streams](handbook/events.md) | ¿Cómo llega el pedido a otros procesos? | Publicación, consumo, repetición y agregación por ventanas. |
| [5. Documentos e IA](handbook/retrieval.md) | ¿En qué evidencia se apoya una respuesta? | Separación entre recuperación y generación; interpretación de métricas. |
| [6. Devoluciones y aprobación](handbook/workflows.md) | ¿Cómo se reanuda una operación sin repetir su efecto? | Progreso guardado, autorización y estado de negocio. |
| [7. Seguridad y resiliencia](handbook/security.md) | ¿Quién puede actuar y qué ocurre si falla una dependencia? | Tokens, permisos, tiempos de espera y reintentos. |
| [8. Observabilidad y diagnóstico](handbook/operations.md) | ¿Cómo se localiza y se investiga un fallo? | Métricas, trazas y logs de una misma operación. |
| [9. Despliegue y cambios](handbook/delivery.md) | ¿Qué se comprueba antes de desplegar o escalar? | Garantías de Compose, Kubernetes y Terraform; justificación de un cambio. Los laboratorios kind/Terraform son opcionales. |

<a id="topic-cuando-aplicar-cada-mecanismo"></a>

## Volver a una explicación

Para localizar una implementación, usar el [mapa por preguntas y conceptos](reference-map.md#concept-index): Java/Spring, Kafka, Python/IA, seguridad, observabilidad o despliegue. Para entender una decisión, consultar los [ADR](decisions/index.md): registros breves del problema, las alternativas y sus consecuencias. La [verificación](verification.md) separa resultados comprobados y limitaciones pendientes.

La referencia se centra en backend e integración fiable de IA. La simulación permite estudiar permisos y coordinación sin pagar un modelo; su resultado no mide la calidad de una respuesta generativa real. Las lecturas enlazadas amplían los temas que el ejemplo solo introduce.

**Siguiente: [0. Preparar el entorno y ejecutar el primer pedido →](getting-started.md)**
