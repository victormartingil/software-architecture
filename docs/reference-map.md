# Mapa de referencia

Usar esta página para volver a una explicación o localizar su código después de estudiar el [manual](index.md). El [estado comprobado](verification.md) prevalece sobre cualquier descripción de capacidad.

| Pregunta | Código | Explicación | AC |
|---|---|---|---|
| ¿Qué se separa y por qué? | [Caso de uso y puertos](../apps/commerce-api/src/main/java/dev/reference/commerce/orders/application/service/ManageOrderService.java) | [Arquitectura](handbook/architecture.md) | ARCH |
| ¿Cómo mantener un contrato HTTP y paginar sin mezclar tenants? | [OpenAPI](../contracts/http/commerce.openapi.json) · [consulta](../apps/commerce-api/src/main/java/dev/reference/commerce/orders/infrastructure/persistence/JdbcOrderRepositoryAdapter.java) | [Contratos HTTP](handbook/api-contracts.md) | EVOL-04/05 |
| ¿Cómo proteger la última unidad? | [Inventario](../apps/commerce-api/src/main/java/dev/reference/commerce/inventory/application/service/InventoryService.java) | [Transacciones](handbook/transactions.md) | ORD |
| ¿Cómo publicar después de commit? | [Publicador](../apps/commerce-api/src/main/java/dev/reference/commerce/shared/infrastructure/messaging/OutboxPublisher.java) · [inbox](../apps/commerce-api/src/main/java/dev/reference/commerce/shared/infrastructure/messaging/AuditConsumer.java) | [Eventos](handbook/events.md) | EVT |
| ¿Qué significa una ventana? | [Topología](../apps/order-analytics/src/main/java/dev/reference/analytics/OrderSummaryTopology.java) | [Eventos](handbook/events.md) | STR |
| ¿Cuándo abstenerse? | [Recuperación](../apps/ai-service/src/ai_service/infrastructure/retrieval.py) · [evaluación](../apps/ai-service/src/ai_service/evaluate.py) | [RAG](handbook/retrieval.md) | AI |
| ¿Qué permite una aprobación? | [Devolución](../apps/commerce-api/src/main/java/dev/reference/commerce/returns/application/service/ManageReturnService.java) · [workflow](../apps/ai-service/src/ai_service/infrastructure/workflows.py) | [Workflows](handbook/workflows.md) | RET/WF |
| ¿Qué se considera una frontera de seguridad? | [JWT](../apps/ai-service/src/ai_service/infrastructure/security.py) · [proveedor](../apps/ai-service/src/ai_service/infrastructure/providers.py) | [Seguridad](handbook/security.md) | SEC |
| ¿Cómo detectar y reparar un fallo? | [Dashboard](../infra/observability/dashboard.json) · [señales reales](../scenarios/observability.py) | [Operación](handbook/operations.md) | OBS/OPS |
| ¿Cómo entregar sin atribuir garantías no medidas? | [HPA](../scripts/kind-scale-check.py) · [Terraform](../infra/cloud/main.tf) | [Despliegue y cambios](handbook/delivery.md) | K8S/TF |

<a id="topic-vistas-visuales"></a>

## Vistas visuales

Cada diagrama se explica en el capítulo correspondiente. Abrir el SVG para ampliar los detalles de las secuencias y del mapa de aplicaciones. Las instrucciones para modificar las imágenes están en la [guía de contribución](contributing.md).

| Pregunta | Vista SVG | Explicación |
|---|---|---|
| ¿Quién usa el sistema? | [C4 contexto](diagrams/c4-context.svg) | [Diseño](repository-design.md) |
| ¿Qué aplicaciones y datos existen? | [C4 contenedores](diagrams/c4-containers.svg) | [Diseño](repository-design.md) |
| ¿Quién emite y valida tokens? | [Identidad](diagrams/c4-identity.svg) | [Seguridad](handbook/security.md) |
| ¿Por dónde circulan trazas y métricas? | [Señales](diagrams/c4-observability.svg) | [Operación](handbook/operations.md) |
| ¿Cómo se invierten dependencias? | [Puertos y adaptadores](diagrams/ports-and-adapters.svg) | [Arquitectura](handbook/architecture.md) |
| ¿Qué pasa con dos compras de la última unidad? | [Concurrencia SQL](diagrams/stock-concurrency.svg) | [Transacciones](handbook/transactions.md#topic-dos-peticiones-y-la-ultima-unidad) |
| ¿Qué ocurre antes y después del commit? | [Pedido y outbox](diagrams/order-sequence.svg) | [Transacciones](handbook/transactions.md) |
| ¿Qué restricciones hay en SQL? | [Modelo físico](diagrams/commerce-data.svg) | [Transacciones](handbook/transactions.md) |
| ¿Qué diferencia al broker de una biblioteca Streams? | [Kafka y lectores](diagrams/kafka-consumers.svg) | [Eventos](handbook/events.md#topic-kafka-eventos-y-streams) |
| ¿Cómo se obtiene el resumen por ventana? | [Procesamiento de eventos](diagrams/order-summary-flow.svg) | [Eventos](handbook/events.md) |
| ¿Cuándo deja de llamar y prueba recuperar el proveedor? | [Circuito IA](diagrams/provider-circuit.svg) | [Resiliencia](handbook/security.md#topic-circuito-del-proveedor) |
| ¿Cuándo responde o se abstiene la IA? | [RAG](diagrams/answer-flow.svg) | [Recuperación](handbook/retrieval.md) |
| ¿Cómo se reanuda una devolución? | [Secuencia aprobada](diagrams/return-sequence.svg) | [Workflows](handbook/workflows.md) |
| ¿Qué transiciones autoriza Comercio? | [Estados](diagrams/return-states.svg) | [Workflows](handbook/workflows.md) |
| ¿Dónde corre cada proceso local? | [Compose y kind](diagrams/deployment-local.svg) | [Despliegue y cambios](handbook/delivery.md) |
| ¿Qué define Terraform y qué queda pendiente? | [Infraestructura GCP](diagrams/cloud-foundation.svg) | [Despliegue y cambios](handbook/delivery.md) |

## Herramientas elegidas y alternativas

Java/Spring Boot concentra reglas de negocio y transacciones. Python/FastAPI aísla la integración de IA; LangGraph aporta checkpoint y reanudación, no autoridad. Kafka muestra entrega y replay; Streams añade una pregunta concreta sobre tiempo de evento. PostgreSQL sirve datos transaccionales y pgvector permite una comparación de recuperación sin introducir otra base.

Docker Compose es el punto de entrada. Kubernetes y Terraform son laboratorios separados: arrancar el sistema no requiere una cuenta cloud. OpenTelemetry, Prometheus, Tempo y Grafana enlazan señales. JUnit/Testcontainers, TopologyTestDriver, pytest, ArchUnit e import-linter prueban comportamiento y restricciones.

Spring AI, MCP, event sourcing, sagas, service mesh, Backstage y múltiples proveedores no se añaden por completar un catálogo. Criterio de entrada: una necesidad observable, una alternativa más simple descartada y un AC que muestre el beneficio.

## Lecturas y ejemplos externos

Cada capítulo incluye las fuentes oficiales del concepto y, cuando ayuda a estudiarlo, una lectura o un ejemplo de código externo:

| Tema | Recursos complementarios |
|---|---|
| Módulos y límites | [Arquitectura](handbook/architecture.md#topic-lectura-y-repositorio-de-referencia) |
| Datos y transacciones | [Transacciones](handbook/transactions.md#topic-dos-peticiones-y-la-ultima-unidad) · DDIA en el capítulo de eventos |
| Eventos y Kafka Streams | [Eventos](handbook/events.md#topic-lecturas-y-ejemplo-de-referencia) |
| RAG y evaluación | [Recuperación](handbook/retrieval.md#topic-lectura-y-ejemplo-de-referencia) |
| Fallos y resiliencia | [Circuito IA, Release It! y Resilience4j](handbook/security.md#topic-lectura-y-ejemplo-de-resiliencia) |
| Métricas, trazas y logs | [Operación](handbook/operations.md#topic-ejemplo-de-observabilidad) |
| Kubernetes | [Despliegue y cambios](handbook/delivery.md#topic-ejemplo-de-kubernetes) |
| TLS de base de datos | [Canal y certificados](handbook/delivery.md#topic-tls-en-conexiones-a-postgresql) |

## Repositorios enlazados

Las siguientes cifras registran la consulta del 17-10-2026. Sirven como contexto de las referencias, no como criterio de calidad ni como requisito para seguir el manual. Los capítulos enlazan la parte concreta que complementa su explicación.

| Repositorio | Estrellas registradas | Capítulo |
|---|---:|---|
| Spring Modulith | 1.200 | [Módulos y dependencias](handbook/architecture.md#topic-lectura-y-repositorio-de-referencia) |
| Apache Kafka | 34.036 | [Topología WordCount](handbook/events.md#topic-lecturas-y-ejemplo-de-referencia) |
| LlamaIndex | 52.539 | [Recuperación de documentos](handbook/retrieval.md#topic-lectura-y-ejemplo-de-referencia) |
| Resilience4j | 10.787 | [Circuito de protección](handbook/security.md#topic-lectura-y-ejemplo-de-resiliencia) |
| OpenTelemetry Demo | 3.409 | [Configuración de señales](handbook/operations.md#topic-ejemplo-de-observabilidad) |
| Online Boutique | 21.060 | [Manifiestos Kubernetes](handbook/delivery.md#topic-ejemplo-de-kubernetes) |
