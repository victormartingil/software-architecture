# Mapa de referencia

Consultar por pregunta; ejecutar antes de ampliar el stack. El [estado comprobado](verification.md) prevalece sobre cualquier descripción de capacidad.

| Pregunta | Código | Explicación | AC |
|---|---|---|---|
| ¿Qué se separa y por qué? | commerce, puertos por módulo | [Arquitectura](handbook/architecture.md) | ARCH |
| ¿Cómo proteger la última unidad? | orders/inventory y migración SQL | [Transacciones](handbook/transactions.md) | ORD |
| ¿Cómo publicar después de commit? | outbox e inbox | [Eventos](handbook/events.md) | EVT |
| ¿Qué significa una ventana? | OrderSummaryTopology | [Eventos](handbook/events.md) | STR |
| ¿Cuándo abstenerse? | retrieval, answers, evaluate | [RAG](handbook/retrieval.md) | AI |
| ¿Qué permite una aprobación? | returns y workflows | [Workflows](handbook/workflows.md) | RET/WF |
| ¿Qué se considera una frontera de seguridad? | OIDC, autorización y proveedor | [Seguridad](handbook/security.md) | SEC |
| ¿Cómo detectar y reparar un fallo? | métricas y escenarios | [Operación](handbook/operations.md) | OBS/OPS |
| ¿Cómo entregar sin atribuir garantías no medidas? | kind y Terraform | [Delivery](handbook/delivery.md) | K8S/TF |

## Vistas visuales

Un [maestro draw.io de trece páginas](diagrams/architecture.drawio), con objetos y relaciones editables. Editar el `.drawio` con draw.io y volver a exportar el SVG correspondiente. Las imágenes se insertan en su explicación; abrir el SVG para leer los detalles a tamaño completo, especialmente C4 nivel 2 y secuencias.

| Pregunta | Vista SVG | Explicación |
|---|---|---|
| ¿Quién usa el sistema? | [C4 contexto](diagrams/c4-context.svg) | [Diseño](repository-design.md) |
| ¿Qué aplicaciones y datos existen? | [C4 contenedores](diagrams/c4-containers.svg) | [Diseño](repository-design.md) |
| ¿Quién emite y valida tokens? | [Identidad](diagrams/c4-identity.svg) | [Seguridad](handbook/security.md) |
| ¿Por dónde circulan trazas y métricas? | [Señales](diagrams/c4-observability.svg) | [Operación](handbook/operations.md) |
| ¿Cómo se invierten dependencias? | [Puertos y adaptadores](diagrams/ports-and-adapters.svg) | [Arquitectura](handbook/architecture.md) |
| ¿Qué ocurre antes y después del commit? | [Pedido y outbox](diagrams/order-sequence.svg) | [Transacciones](handbook/transactions.md) |
| ¿Qué restricciones hay en SQL? | [Modelo físico](diagrams/commerce-data.svg) | [Transacciones](handbook/transactions.md) |
| ¿Cómo se obtiene el resumen por ventana? | [Procesamiento de eventos](diagrams/order-summary-flow.svg) | [Eventos](handbook/events.md) |
| ¿Cuándo responde o se abstiene la IA? | [RAG](diagrams/answer-flow.svg) | [Recuperación](handbook/retrieval.md) |
| ¿Cómo se reanuda una devolución? | [Secuencia aprobada](diagrams/return-sequence.svg) | [Workflows](handbook/workflows.md) |
| ¿Qué transiciones autoriza Comercio? | [Estados](diagrams/return-states.svg) | [Workflows](handbook/workflows.md) |
| ¿Dónde corre cada proceso local? | [Compose y kind](diagrams/deployment-local.svg) | [Delivery](handbook/delivery.md) |
| ¿Qué define Terraform y qué queda pendiente? | [Fundación GCP](diagrams/cloud-foundation.svg) | [Delivery](handbook/delivery.md) |

## Herramientas elegidas y alternativas

Java/Spring Boot concentra reglas de negocio y transacciones. Python/FastAPI aísla la integración de IA; LangGraph aporta checkpoint y reanudación, no autoridad. Kafka muestra entrega y replay; Streams añade una pregunta concreta sobre tiempo de evento. PostgreSQL sirve datos transaccionales y pgvector permite una comparación de recuperación sin introducir otra base.

Docker Compose es el punto de entrada. Kubernetes y Terraform son laboratorios separados: arrancar el sistema no requiere una cuenta cloud. OpenTelemetry, Prometheus, Tempo y Grafana enlazan señales. JUnit/Testcontainers, TopologyTestDriver, pytest, ArchUnit e import-linter prueban comportamiento y restricciones.

Spring AI, MCP, event sourcing, sagas, service mesh, Backstage y múltiples proveedores no se añaden por completar un catálogo. Criterio de entrada: una necesidad observable, una alternativa más simple descartada y un AC que muestre el beneficio.

## Referencias orientativas

Los libros guían preguntas, sin sustituir fuentes actuales del stack: *Software Architecture: The Hard Parts* (Ford et al.), *Fundamentals of Software Architecture* (Richards/Ford), *Designing Data-Intensive Applications* (Kleppmann), *Enterprise Integration Patterns* (Hohpe/Woolf), *Release It!* (Nygard), *Building Microservices* (Newman), *Domain-Driven Design* (Evans) y *AI Engineering* (Huyen). No se reproducen sus textos.

Para ejemplos públicos, contrastar alcance antes de adaptar: [Spring Modulith](https://github.com/spring-projects/spring-modulith) sobre módulos verificables, [Confluent tutorials](https://github.com/confluentinc/tutorials) sobre Kafka, [OpenTelemetry demo](https://github.com/open-telemetry/opentelemetry-demo) sobre señales y [Google microservices demo](https://github.com/GoogleCloudPlatform/microservices-demo) sobre despliegue. Son referencias, no dependencias ni avales. Ningún número de estrellas garantiza corrección o compatibilidad con este caso.
