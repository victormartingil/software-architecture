# Arquitectura de software e integración de IA

Un manual práctico para estudiar cómo organizar un backend, proteger sus datos, comunicar servicios e integrar IA con permisos y límites claros. El hilo conductor es una tienda de ejemplo: un comprador realiza un pedido, consulta las condiciones de devolución y solicita una devolución que aprueba un responsable.

**[Empezar a estudiar](docs/index.md)** · **[Preparar el entorno](docs/getting-started.md)** · **[Consultar un tema](docs/reference-map.md)**

## Qué se aprende

El recorrido sigue el mismo pedido a través de tres aplicaciones:

| Aplicación | Qué aporta al ejemplo |
|---|---|
| Comercio · `commerce-api` | Pedidos, inventario y devoluciones con Java, Spring Boot y PostgreSQL. |
| Resumen de pedidos · `order-analytics` | Procesamiento de eventos y agregaciones con Kafka Streams. |
| Asistente · `ai-service` | Consulta de documentos y coordinación de devoluciones con Python y FastAPI. |

Cada capítulo explica un problema, sigue su solución en el código y propone una práctica para comprobar el resultado. Los diagramas ayudan a relacionar las piezas; al final se incluyen documentación oficial, lecturas y ejemplos para profundizar.

## Antes de empezar

Conviene conocer programación, HTTP y SQL básicos. El manual explica los conceptos de arquitectura a medida que aparecen. Se puede leer sin instalar nada; las prácticas indican las herramientas que necesitan.

Para ejecutar la tienda hacen falta Docker Compose, Python, uv y Make. El [capítulo 0](docs/getting-started.md) detalla versiones, memoria, arranque y resultado esperado. La IA está simulada por defecto: la demostración no necesita claves ni genera costes de API.

## Índice de capítulos

Empezar por [0. Preparar el entorno y ejecutar el primer pedido](docs/getting-started.md). Después, seguir los capítulos en orden; los subtemas permiten volver directamente a una explicación.

1. [Organización del código y límites de arquitectura](docs/handbook/architecture.md)
   1. [Módulos, servicios y puertos/adaptadores](docs/handbook/architecture.md#topic-problema-y-decision)
   2. [Adapter, Repository, Strategy, Outbox/Inbox y Circuit Breaker](docs/handbook/architecture.md#topic-patrones-con-una-responsabilidad-concreta)
2. [Contratos HTTP y paginación](docs/handbook/api-contracts.md)
   1. [Errores de dominio y Problem Details](docs/handbook/api-contracts.md#topic-significado-antes-que-transporte)
   2. [OpenAPI, cursor y paginación por clave](docs/handbook/api-contracts.md#topic-un-listado-pequeno-y-estable)
3. [Datos, transacciones y concurrencia](docs/handbook/transactions.md)
   1. [Reglas de negocio, restricciones SQL y stock concurrente](docs/handbook/transactions.md#topic-dos-peticiones-y-la-ultima-unidad)
   2. [Idempotencia, reintentos y devoluciones aprobadas](docs/handbook/transactions.md#topic-repetir-una-peticion-es-normal)
4. [Comunicación asíncrona y eventos](docs/handbook/events.md)
   1. [Eventos, topics, claves y particiones](docs/handbook/events.md#topic-kafka-eventos-y-streams)
   2. [Outbox/Inbox, entrega repetida, DLT y replay](docs/handbook/events.md#topic-publicar-despues-de-confirmar)
   3. [Kafka Streams: biblioteca, ventanas, tiempo y estado](docs/handbook/events.md#topic-ventanas-y-deduplicacion)
5. [Integración de IA y RAG](docs/handbook/retrieval.md)
   1. [Búsqueda léxica y vectorial, citas y aislamiento por organización](docs/handbook/retrieval.md#topic-evidencia-antes-de-redactar)
   2. [Abstención y evaluación de recuperación](docs/handbook/retrieval.md#topic-que-medir)
   3. [Proveedor, salidas estructuradas y streaming SSE](docs/handbook/retrieval.md#topic-simulacion-y-proveedor-real)
6. [Flujos de trabajo persistentes y aprobación humana](docs/handbook/workflows.md)
   1. [Puntos de control, pausa y reanudación](docs/handbook/workflows.md#topic-estado-y-fallo-entre-sistemas)
   2. [Autorización, versión e idempotencia del efecto](docs/handbook/workflows.md#topic-una-propuesta-no-es-un-permiso)
7. [Seguridad y resiliencia](docs/handbook/security.md)
   1. [OIDC, JWT, JWKS y autorización por tenant](docs/handbook/security.md#topic-autenticacion-y-autorizacion)
   2. [Prompt injection y límites de confianza](docs/handbook/security.md#topic-modelo-de-amenazas-minimo)
   3. [Tiempos de espera, reintentos, circuito de protección y cancelación](docs/handbook/security.md#topic-fallos-de-dependencias)
8. [Observabilidad y diagnóstico](docs/handbook/operations.md)
   1. [Métricas, logs, trazas y correlación](docs/handbook/operations.md#topic-senales-con-una-pregunta)
   2. [Alertas, procedimientos de recuperación, SLI/SLO y capacidad](docs/handbook/operations.md#topic-sli-slo-y-capacidad)
9. [Entrega y verificación](docs/handbook/delivery.md)
   1. [Compose, contenedores y CI](docs/handbook/delivery.md#topic-delivery-cambios-y-reversion)
   2. [Kubernetes, probes y HPA](docs/handbook/delivery.md#topic-laboratorio-kubernetes)
   3. [Terraform y validación de infraestructura sin desplegar](docs/handbook/delivery.md#topic-fundacion-cloud)

## El sistema de un vistazo

[![Comprador y responsable utilizan la tienda de ejemplo](docs/diagrams/c4-context.svg)](docs/diagrams/c4-context.svg)

El comprador consulta y solicita; el responsable aprueba. El [mapa de componentes](docs/repository-design.md#component-map) muestra qué aplicación y qué almacén intervienen en cada paso.

## Documentación de consulta

| Si se necesita… | Consultar |
|---|---|
| Encontrar código por problema | [Mapa de referencia](docs/reference-map.md) |
| Entender una decisión y sus alternativas | [Registro de decisiones](docs/decisions/index.md) |
| Consultar contratos y requisitos | [Especificación](docs/specification.md) y [criterios de aceptación](docs/acceptance.md) |
| Saber qué se ha comprobado | [Verificación y límites](docs/verification.md) |
| Modificar documentación o diagramas | [Guía de contribución](docs/contributing.md) |

El entorno local facilita el aprendizaje; no demuestra alta disponibilidad ni calidad de un modelo generativo real. Kubernetes y Terraform se estudian como prácticas adicionales, sin crear infraestructura de pago.
