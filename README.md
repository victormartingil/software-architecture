# Software Architecture & Applied AI

Tres servicios pequeños para explicar y verificar arquitectura de backend, eventos e integración fiable de IA. Un dominio sintético de pedidos, inventario y devoluciones conecta teoría, código y fallos reproducibles.

| Función | Servicio ejecutable | Responsabilidad |
|---|---|---|
| API de comercio | `commerce-api` · Java / Spring Boot | Crear pedidos, proteger stock y gestionar devoluciones. |
| Resumen de pedidos | `order-analytics` · Java / Kafka Streams | Leer eventos y resumir pedidos creados por producto y minuto. |
| API del asistente | `ai-service` · Python / FastAPI | Consultar políticas con citas y coordinar devoluciones aprobadas. |

Comercio publica los avisos de pedidos en **Kafka**, un servidor de eventos. **Keycloak** es otro servidor: gestiona identidades y emite tokens, sin formar parte de Spring Boot. Los scripts locales son los clientes que llaman a nuestras APIs. El [mapa de componentes](docs/repository-design.md#component-map) explica cada pieza y su correspondencia con el código. No hay frontend propio.

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
- `make observability`: Grafana en `http://localhost:3000` y Prometheus en `http://localhost:9090`.

Si se actualiza un entorno de la primera versión con claves idempotentes antiguas, Flyway V2 bloqueará el arranque para conservar sus efectos. No vaciar solo la tabla de claves. El [ADR 002](docs/decisions/002-consistency.md) explica la incompatibilidad y el reset completo **explícito** del laboratorio sintético; nunca se ejecuta automáticamente.

Para volver a consultar una operación concreta: `make kafka-summary ORDER_ID=...`, sustituyendo `...` por el UUID mostrado por la demo.

## Consultar y verificar

| Entrada | Contenido |
|---|---|
| [Manual](docs/index.md) · `make docs` | Recorrido navegable, diagramas y enlaces al código |
| [Diseño y C4](docs/repository-design.md) | Límites, estructura y secuencia de entregas |
| [Mapa de referencia](docs/reference-map.md) | Qué consultar para cada problema |
| [Especificación](docs/specification.md) y [AC](docs/acceptance.md) | Contratos y resultados exigidos |
| [ADR](docs/decisions/index.md) | Decisiones, alternativas y costes |
| [Verificación](docs/verification.md) | Evidencia ejecutada, revisión y límites |

```bash
make verify                 # Tests reales, arquitectura, contratos, higiene y docs
make eval                   # Dataset fijo de recuperación, sin LLM real
make terraform-check        # Planes con mocks; no crea recursos cloud
make down
make kind-up                # Clúster local exclusivo, sin cambiar contexto global
make kind-smoke
python3 scripts/kind-load.py # Carga GET autenticada para HPA de comercio
make kind-down
```

Kubernetes y Terraform son laboratorios adicionales. AI conserva una réplica por su checkpoint SQLite; comercio permite HPA. El entorno local no demuestra alta disponibilidad. Las condiciones y resultados exactos están en la matriz de verificación. CI está definida para GitHub Actions; ejecutar localmente no equivale a haberla ejecutado en GitHub.

El proveedor real es optativo mediante `compose.live.yaml`, modelo, clave, presupuesto declarado y cupo explícitos. No se ejecuta en `make up`, demo ni CI. La evaluación del simulador no acredita calidad de un modelo real.

English: Small reference implementations connecting transactional Java, Kafka Streams and reliable AI workflows. Each case pairs design trade-offs with executable checks. Spanish documentation, English code and identifiers.
