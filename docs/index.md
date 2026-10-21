# Manual de arquitectura de software e integración de IA

Este manual estudia una tienda de ejemplo para responder a preguntas habituales de un backend: dónde colocar las reglas de negocio, cómo evitar ventas sin stock, cómo publicar eventos sin perderlos y cómo utilizar IA sin darle permisos que no le corresponden.

Se trabaja sobre una implementación ya construida. La lectura del código y las prácticas permiten comprobar por qué funciona, qué ocurre cuando algo falla y qué habría que cambiar ante otros requisitos.

## A quién va dirigido

A desarrolladores que conocen programación, peticiones HTTP y consultas SQL básicas. Para seguir el código conviene poder leer clases Java y funciones Python; no es necesario dominar Spring Boot, Kafka o LangGraph antes de empezar.

No hace falta instalar herramientas para leer las explicaciones y los diagramas. El [capítulo 0](getting-started.md) prepara el entorno para las prácticas e introduce a los usuarios y las aplicaciones del ejemplo.

## Cómo estudiar cada capítulo

1. Leer el problema y el objetivo del capítulo.
2. Seguir el recorrido por los archivos enlazados: entrada, regla de negocio y acceso al sistema externo.
3. Predecir el resultado de la práctica antes de ejecutarla.
4. Comparar la salida con la explicación y responder a la pregunta final.
5. Resolver los [ejercicios de regla de dominio](handbook/architecture.md#topic-ejercicio-regla-de-dominio), [carrera de stock](handbook/transactions.md#topic-ejercicio-carrera-de-stock) y [pregunta de recuperación](handbook/retrieval.md#topic-ejercicio-caso-de-recuperacion) al llegar a sus capítulos, antes de abrir la solución.
6. Consultar las lecturas complementarias cuando se necesite más profundidad. Al terminar, resolver el [caso integrador](handbook/delivery.md#topic-caso-integrador) y contrastarlo con su rúbrica.

Las prácticas guiadas comprueban la implementación; los ejercicios piden producir una solución y justificarla. No hace falta construir otra aplicación. Los primeros capítulos se apoyan unos en otros. Una vez completados, el [mapa de referencia](reference-map.md) permite consultar un problema concreto sin repetir el recorrido.

Para leer el manual con buscador, índice lateral y modo claro u oscuro, ejecutar `make docs` desde la raíz, con uv instalado. Abrir la dirección local que indique el comando en la terminal. También se puede seguir completo desde los enlaces Markdown de GitHub.

## Orden de estudio

| Capítulo | Pregunta que resuelve | Al terminar se podrá… |
|---|---|---|
| [0. Preparación y primer pedido](getting-started.md) | ¿Qué hace el ejemplo y cómo se ejecuta? | Reconocer sus aplicaciones, usuarios y resultado de la demostración. |
| [1. Organización del código](handbook/architecture.md) | ¿Dónde debe vivir cada responsabilidad? | Seguir un caso de uso a través de módulos, puertos y adaptadores. |
| [2. Contratos HTTP](handbook/api-contracts.md) | ¿Cómo se define lo que una API acepta y devuelve? | Interpretar errores y recorrer páginas sin saltarse permisos. |
| [3. Transacciones y concurrencia](handbook/transactions.md) | ¿Qué ocurre si dos compradores piden la última unidad? | Explicar atomicidad, bloqueo e idempotencia con ese caso. |
| [4. Eventos y Kafka Streams](handbook/events.md) | ¿Cómo llega el pedido a otros procesos? | Distinguir publicación, consumo, repetición y agregación por ventanas. |
| [5. Documentos e IA](handbook/retrieval.md) | ¿En qué evidencia se apoya una respuesta? | Separar recuperación de generación y leer sus métricas. |
| [6. Devoluciones y aprobación](handbook/workflows.md) | ¿Cómo se reanuda una operación sin repetir su efecto? | Diferenciar progreso guardado, autorización y estado de negocio. |
| [7. Seguridad y resiliencia](handbook/security.md) | ¿Quién puede actuar y qué ocurre si falla una dependencia? | Razonar sobre tokens, permisos, tiempos de espera y reintentos. |
| [8. Observabilidad y diagnóstico](handbook/operations.md) | ¿Cómo se localiza y se investiga un fallo? | Relacionar métricas, trazas y logs de una misma operación. |
| [9. Despliegue y cambios](handbook/delivery.md) | ¿Qué se comprueba antes de desplegar o escalar? | Distinguir las garantías de Compose, Kubernetes y Terraform y justificar un cambio. Los laboratorios kind/Terraform son opcionales. |

<a id="topic-cuando-aplicar-cada-mecanismo"></a>

## Volver a una explicación

Para localizar una implementación, usar el [mapa por preguntas](reference-map.md). Para entender una decisión, consultar los [ADR](decisions/index.md): registros breves del problema, las alternativas y sus consecuencias. La [verificación](verification.md) separa resultados comprobados y limitaciones pendientes.

El manual se centra en backend e integración fiable de IA. La simulación permite estudiar permisos y coordinación sin pagar un modelo; su resultado no mide la calidad de una respuesta generativa real. Las lecturas enlazadas amplían los temas que el ejemplo solo introduce.

**Siguiente: [0. Preparar el entorno y ejecutar el primer pedido →](getting-started.md)**
