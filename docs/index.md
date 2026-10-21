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

Para localizar una implementación, usar el [mapa por preguntas](reference-map.md). Para entender una decisión, consultar los [ADR](decisions/index.md): registros breves del problema, las alternativas y sus consecuencias. La [verificación](verification.md) separa resultados comprobados y limitaciones pendientes.

La referencia se centra en backend e integración fiable de IA. La simulación permite estudiar permisos y coordinación sin pagar un modelo; su resultado no mide la calidad de una respuesta generativa real. Las lecturas enlazadas amplían los temas que el ejemplo solo introduce.

**Siguiente: [0. Preparar el entorno y ejecutar el primer pedido →](getting-started.md)**
