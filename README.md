# Software Architecture & Applied AI

Tres servicios pequeños para explicar y verificar arquitectura de backend, eventos e integración fiable de IA. Un dominio sintético de pedidos, inventario y devoluciones conecta teoría, código y fallos reproducibles.

| Función | Servicio ejecutable | Responsabilidad |
|---|---|---|
| API de comercio | `commerce-api` · Java / Spring Boot | Crear pedidos, proteger stock y gestionar devoluciones. |
| Resumen de pedidos | `order-analytics` · Java / Kafka Streams | Leer eventos y resumir pedidos creados por producto y minuto. |
| API del asistente | `ai-service` · Python / FastAPI | Consultar políticas con citas y coordinar devoluciones aprobadas. |

Comercio publica los avisos de pedidos en **Kafka**, un servidor de eventos. **Keycloak** es otro servidor: gestiona identidades y emite tokens, sin formar parte de Spring Boot. Los scripts locales son los clientes que llaman a nuestras APIs. El [mapa de componentes](docs/repository-design.md#component-map) explica cada pieza y su correspondencia con el código. No hay frontend propio.

## Temario

Recorrido de lo esencial a la operación. Cada concepto enlaza con la explicación, el código y las pruebas del tema.

1. [Límites de arquitectura y patrones](docs/handbook/architecture.md#topic-problema-y-decision)
   1. [Módulos, servicios y puertos/adaptadores](docs/handbook/architecture.md#topic-por-que-tres-servicios)
   2. [Adapter, Repository, Strategy, Outbox/Inbox y Circuit Breaker](docs/handbook/architecture.md#topic-patrones-con-una-responsabilidad-concreta)
2. [Contratos de API](docs/handbook/api-contracts.md#topic-significado-antes-que-transporte)
   1. [Errores de dominio y Problem Details](docs/handbook/api-contracts.md#topic-significado-antes-que-transporte)
   2. [OpenAPI, cursor y paginación por clave](docs/handbook/api-contracts.md#topic-un-listado-pequeno-y-estable)
3. [Datos, transacciones y concurrencia](docs/handbook/transactions.md#topic-la-invariante-primero)
   1. [Invariantes, constraints y stock concurrente](docs/handbook/transactions.md#topic-dos-peticiones-y-la-ultima-unidad)
   2. [Idempotencia, reintentos y devoluciones aprobadas](docs/handbook/transactions.md#topic-repetir-una-peticion-es-normal)
4. [Comunicación asíncrona y eventos](docs/handbook/events.md#topic-kafka-eventos-y-streams)
   1. [Eventos, topics, claves y particiones](docs/handbook/events.md#topic-kafka-eventos-y-streams)
   2. [Outbox/Inbox, entrega repetida, DLT y replay](docs/handbook/events.md#topic-publicar-despues-de-confirmar)
   3. [Kafka Streams: biblioteca, ventanas, tiempo y estado](docs/handbook/events.md#topic-ventanas-y-deduplicacion)
5. [Integración de IA y RAG](docs/handbook/retrieval.md#topic-evidencia-antes-de-redactar)
   1. [Recuperación lexical/vectorial, citas y aislamiento tenant](docs/handbook/retrieval.md#topic-evidencia-antes-de-redactar)
   2. [Abstención y evaluación de recuperación](docs/handbook/retrieval.md#topic-que-medir)
   3. [Proveedor, salidas estructuradas y streaming SSE](docs/handbook/retrieval.md#topic-simulacion-y-proveedor-real)
6. [Workflows persistentes y aprobación humana](docs/handbook/workflows.md#topic-una-propuesta-no-es-un-permiso)
   1. [Checkpoint, pausa y reanudación](docs/handbook/workflows.md#topic-estado-y-fallo-entre-sistemas)
   2. [Autorización, versión e idempotencia del efecto](docs/handbook/workflows.md#topic-una-propuesta-no-es-un-permiso)
7. [Seguridad y resiliencia](docs/handbook/security.md#topic-autenticacion-y-autorizacion)
   1. [OIDC, JWT, JWKS y autorización por tenant](docs/handbook/security.md#topic-autenticacion-y-autorizacion)
   2. [Prompt injection y límites de confianza](docs/handbook/security.md#topic-modelo-de-amenazas-minimo)
   3. [Timeouts, reintentos, breaker y cancelación](docs/handbook/security.md#topic-fallos-de-dependencias)
8. [Operación y observabilidad](docs/handbook/operations.md#topic-senales-con-una-pregunta)
   1. [Métricas, logs, trazas y correlación](docs/handbook/operations.md#topic-senales-con-una-pregunta)
   2. [Alertas, runbooks, SLI/SLO y capacidad](docs/handbook/operations.md#topic-sli-slo-y-capacidad)
9. [Entrega y verificación](docs/handbook/delivery.md#topic-tres-niveles-distintos-de-prueba)
   1. [Compose, contenedores y CI](docs/handbook/delivery.md#topic-delivery-cambios-y-reversion)
   2. [Kubernetes, probes y HPA](docs/handbook/delivery.md#topic-laboratorio-kubernetes)
   3. [Terraform, mocks y límites de una validación cloud](docs/handbook/delivery.md#topic-fundacion-cloud)

[![Contexto: quién utiliza la tienda y sus sistemas externos](docs/diagrams/c4-context.svg)](docs/diagrams/c4-context.svg)

[Aplicaciones y datos · C4 nivel 2](docs/repository-design.md#topic-c4-contenedores-nivel-2) · [Índice de vistas](docs/reference-map.md#topic-vistas-visuales) · [Maestro editable draw.io](docs/diagrams/architecture.drawio). Editar el `.drawio` con draw.io y volver a exportar el SVG.

## Ejecutar

Requisitos: Docker Engine 25+ con Compose v2 y 10 GiB de memoria disponibles; Python 3.12, uv y Make. Para pruebas Java: JDK 21; Maven Wrapper descarga Maven. kind y kubectl solo para el laboratorio Kubernetes. La primera construcción necesita Internet.

```bash
make up
make demo
make down
```

La demo obtiene tokens OIDC locales, crea un pedido, verifica stock e idempotencia, comprueba aislamiento, consulta políticas y ejecuta una devolución con aprobación. Usa IA **simulada y explícita**: no necesita claves ni genera costes de API. La salida incluye el eventId y el resumen Kafka de la ventana del pedido actual, contrastado con outbox/inbox; no acepta el primer mensaje histórico. El escenario usa acceso de operador local y se ejecuta sin otros escritores concurrentes. Los datos y credenciales locales son sintéticos. `down` conserva volúmenes; el borrado requiere `make reset CONFIRM=delete-local-data`.

- Commerce: `http://localhost:8080`
- AI y contrato OpenAPI: `http://localhost:8081/docs`
- Keycloak: `http://localhost:8088`; usuarios `alice`, `manager`, `bob`, contraseña local `local-demo-only`.
- `make observability` y `make observability-smoke`: [dashboard Grafana](http://localhost:3000/d/architecture-reference), métricas Prometheus y trazas Tempo de una operación nueva; [guía breve](docs/handbook/operations.md).

Si se actualiza un entorno de la primera versión con claves idempotentes antiguas, Flyway V2 bloqueará el arranque para conservar sus efectos. No vaciar solo la tabla de claves. El [ADR 002](docs/decisions/002-consistency.md) explica la incompatibilidad y el reset completo **explícito** del laboratorio sintético; nunca se ejecuta automáticamente.

Para volver a consultar una operación concreta: `make kafka-summary ORDER_ID=...`, sustituyendo `...` por el UUID mostrado por la demo.

## Consultar y verificar

| Entrada | Contenido |
|---|---|
| [Manual](docs/index.md) · `make docs` | Recorrido navegable, diagramas y enlaces al código |
| [Diseño y C4](docs/repository-design.md) | Límites, estructura y secuencia de entregas |
| [Mapa de referencia](docs/reference-map.md) | Qué consultar para cada problema |
| [Contratos HTTP](docs/handbook/api-contracts.md) | OpenAPI, errores semánticos y paginación acotada |
| [Especificación](docs/specification.md) y [AC](docs/acceptance.md) | Contratos y resultados exigidos |
| [ADR](docs/decisions/index.md) | Decisiones, alternativas y costes |
| [Verificación](docs/verification.md) | Evidencia ejecutada, revisión y límites |

```bash
docker compose --profile observability pull prometheus  # Primera descarga para pruebas de alertas
make verify                 # Tests reales, arquitectura, contratos, alertas, higiene y docs
make eval                   # Dataset fijo de recuperación, sin LLM real
make terraform-check        # Planes con mocks; no crea recursos cloud
make down
make kind-up                # Clúster local exclusivo, sin cambiar contexto global
make kind-smoke
make kind-scale-check       # Observa HPA 1→2 y prueba idempotencia entre ambos pods
make kind-down
```

Cada tema explica cuándo usar el mecanismo, por qué y cómo comprobarlo con el código enlazado en el [recorrido](docs/index.md#topic-cuando-aplicar-cada-mecanismo).

Kubernetes y Terraform son laboratorios adicionales. AI conserva una réplica por su checkpoint SQLite; comercio permite HPA. El entorno local no demuestra alta disponibilidad. Las condiciones y resultados exactos están en la matriz de verificación. CI está definida para GitHub Actions; ejecutar localmente no equivale a haberla ejecutado en GitHub.

El proveedor real es optativo mediante `compose.live.yaml`, modelo, clave, presupuesto declarado y cupo explícitos. No se ejecuta en `make up`, demo ni CI. La evaluación del simulador no acredita calidad de un modelo real.

English: Small reference implementations connecting transactional Java, Kafka Streams and reliable AI workflows. Each case pairs design trade-offs with executable checks. Spanish documentation, English code and identifiers.
