# Mapa de referencia

Usar esta página para volver a una explicación o localizar su código al consultar la [referencia](index.md). El [estado comprobado](verification.md) prevalece sobre cualquier descripción de capacidad.

<a id="concept-index"></a>

## Consultar por concepto

Cada fila lleva a la explicación, el código y la comprobación existentes. No es otro recorrido ni exige leer las tecnologías anteriores. Para una primera ejecución, usar [preparación](getting-started.md).

| Conceptos que se quieren reforzar | Explicación y ejemplo | Código y comprobación |
|---|---|---|
| <a id="concept-java-spring"></a> **Java y Spring:** records, invariantes, interfaces, inyección, proxy, transacciones; streams y JDBC | [Módulos y composición](handbook/architecture.md#topic-problema-y-decision), [Quantity](handbook/architecture.md#topic-ejercicio-regla-de-dominio), [paginación](handbook/api-contracts.md#exercise-java-pagination), [frontera transaccional](handbook/transactions.md#exercise-spring-transaction) | [ManageOrderService](../apps/commerce-api/src/main/java/dev/reference/commerce/orders/application/service/ManageOrderService.java), [dominio](../apps/commerce-api/src/test/java/dev/reference/commerce/CommerceDomainTest.java) y [integración](../apps/commerce-api/src/test/java/dev/reference/commerce/CommerceIntegrationTest.java) |
| <a id="concept-architecture"></a> **Arquitectura:** DDD, módulos/procesos, puertos, adaptadores, DIP, decisiones | [Responsabilidades y alternativas](handbook/architecture.md#topic-problema-y-decision), [requisito a decisión](handbook/architecture.md#topic-del-requisito-a-la-decision), [ADR](decisions/index.md) | [ArchitectureRules](../apps/commerce-api/src/test/java/dev/reference/commerce/ArchitectureRules.java) y [fixtures negativos](../apps/commerce-api/src/test/java/dev/reference/commerce/ArchitectureTest.java) |
| <a id="concept-data"></a> **HTTP y datos:** Problem Details, contrato, cursor, aislamiento, stock e idempotencia | [Contratos](handbook/api-contracts.md), [última unidad](handbook/transactions.md#topic-dos-peticiones-y-la-ultima-unidad), [reintento](handbook/transactions.md#topic-repetir-una-peticion-es-normal) | [OpenAPI](../contracts/http/commerce.openapi.json), [SQL de inventario](../apps/commerce-api/src/main/java/dev/reference/commerce/inventory/infrastructure/persistence/JdbcInventoryRepositoryAdapter.java), [pruebas reales](../apps/commerce-api/src/test/java/dev/reference/commerce/CommerceIntegrationTest.java) |
| <a id="concept-kafka"></a> **Kafka:** topics, particiones, outbox/inbox, DLT, replay, Streams, ventanas y deduplicación | [Kafka y Streams](handbook/events.md#topic-kafka-eventos-y-streams), [outbox](handbook/events.md#topic-publicar-despues-de-confirmar), [ventanas](handbook/events.md#topic-ventanas-y-deduplicacion) | [Publicador](../apps/commerce-api/src/main/java/dev/reference/commerce/shared/infrastructure/messaging/OutboxPublisher.java), [topología](../apps/order-analytics/src/main/java/dev/reference/analytics/OrderSummaryTopology.java), [recuperación](../apps/order-analytics/src/test/java/dev/reference/analytics/KafkaRecoveryTest.java) |
| <a id="concept-python-ai"></a> **Python e IA:** Protocol, async/await, RAG, ranking, métricas, citas, LangGraph, checkpoint y aprobación | [Prerrequisitos Python y recuperación](handbook/retrieval.md#python-prerequisites), [evaluación](handbook/retrieval.md#topic-que-medir), [workflow](handbook/workflows.md#topic-estado-y-fallo-entre-sistemas) | [Puertos](../apps/ai-service/src/ai_service/application/ports.py), [recuperador](../apps/ai-service/src/ai_service/infrastructure/retrieval.py), [evaluación](../apps/ai-service/tests/test_evaluate.py), [reinicio/reintento](../apps/ai-service/tests/test_workflows.py) |
| <a id="concept-security"></a> **Seguridad y resiliencia:** OIDC/JWT/JWKS, tenant, prompt injection, deadline, cancelación y circuit breaker | [Identidad](handbook/security.md#topic-autenticacion-y-autorizacion), [amenazas](handbook/security.md#topic-modelo-de-amenazas-minimo), [fallos](handbook/security.md#topic-fallos-de-dependencias) | [JWT](../apps/ai-service/src/ai_service/infrastructure/security.py), [proveedor](../apps/ai-service/src/ai_service/infrastructure/providers.py), [pruebas de identidad](../apps/ai-service/tests/test_security.py), [fallos controlados](../apps/ai-service/tests/test_providers.py) |
| <a id="concept-observability"></a> **Observabilidad:** OpenTelemetry, Prometheus, trazas/logs, contexto, cardinalidad, PromQL, alertas y SLO | [Elegir señal](handbook/operations.md#topic-senales-con-una-pregunta), [reglas](handbook/operations.md#ref-topic-r30), [SLI/SLO](handbook/operations.md#topic-sli-slo-y-capacidad) | [Dashboard](../infra/observability/dashboard.json), [reglas sintéticas](../infra/observability/alerts.test.yaml), [señales actuales](../scenarios/observability.py) |
| <a id="concept-delivery"></a> **Entrega:** Compose, CI, probes, Kubernetes/HPA/PVC, Terraform/mock, migración y reversión | [Ciclo de vida](handbook/operations.md#ref-topic-r35), [Kubernetes](handbook/delivery.md#topic-laboratorio-kubernetes), [Terraform](handbook/delivery.md#topic-fundacion-cloud), [adaptación productiva](handbook/delivery.md#topic-reutilizar-la-referencia-en-un-sistema-real) | [HPA y efectos](../scripts/kind-scale-check.py), [planes mock](../infra/cloud/tests/plan.tftest.hcl), [gate](../scripts/test.sh) |

## Encontrar una decisión por pregunta

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

Cada capítulo incluye fuentes oficiales y lecturas con una pregunta de comparación. Los ejemplos de código externos se fijan al fragmento consultado para conservar el contenido explicado; su fecha y finalidad constan en la [bibliografía](bibliography.md#repositorios). No son versiones elegidas para las dependencias de esta referencia:

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

La [bibliografía recomendada](bibliography.md#repositorios) reúne los ejemplos externos y sus fragmentos fijados. Cada entrada identifica fecha de consulta, utilidad y diferencias con esta implementación. Esos ejemplos sirven para comparar una decisión; no seleccionan versiones de dependencias ni certifican el código. La popularidad no sustituye esa comparación.
