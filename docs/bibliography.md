# Bibliografía recomendada

[Índice de lectura](index.md) · [Buscar un concepto](reference-map.md) · [Libros](#libros) · [Referencias por tema](#por-tema) · [Ejemplos GitHub](#repositorios)

Biblioteca de consulta para profundizar en las decisiones del ejemplo. **No es un programa obligatorio de 17 libros.** Primero estudiar el caso y su comprobación; abrir una lectura si queda una pregunta concreta. Los capítulos del manual seleccionan una entrada breve; aquí se conserva el mapa ampliado de 35 temas y 50 asociaciones con libros.

Las correspondencias son una selección técnica propia: no significan que el código derive del libro ni que su autor avale esta implementación. Documentación oficial para APIs y versiones; libros para fundamentos. La página editorial permite identificar edición, portada y opciones de compra o acceso; no se enlazan copias no autorizadas.

<a id="libros"></a>

## Libros por necesidad

**Para empezar con Java/Spring:** Core Java por apartado, Spring por mecanismo, DDIA por garantía y Learning DDD por elección de modelo. El resto se consulta según el capítulo. Los volúmenes I y II de Core Java son entradas diferentes. No se exige comprarlos todos.

| ID | Libro y autor | Pregunta principal |
|---|---|---|
| [B1](#book-b1) | Core Java I, 14.ª, Cay S. Horstmann | ¿Qué expresa este tipo, interfaz o firma genérica? |
| [B1-II](#book-b1-ii) | Core Java II, 14.ª, Cay S. Horstmann | ¿Cómo funcionan streams, JDBC y tiempo? |
| [B2](#book-b2) | Spring Start Here, 2.ª MEAP, Laurentiu Spilca | ¿Qué hace realmente Spring al construir e interceptar el servicio? |
| [B3](#book-b3) | Designing Data-Intensive Applications, 2.ª, Martin Kleppmann y Chris Riccomini | ¿Qué garantía sostienen los datos y qué pasa al fallar? |
| [B4](#book-b4) | Kafka: The Definitive Guide, 2.ª, Gwen Shapira, Todd Palino, Rajini Sivaram y Krit Petty | ¿Qué confirma Kafka y cómo procesa su consumidor? |
| [B5](#book-b5) | Release It!, 2.ª, Michael Nygard | ¿Cómo acotar fallos y entregar cambios? |
| [B6](#book-b6) | Learning OpenTelemetry, Ted Young y Austin Parker | ¿Cómo instrumentar y transportar señales? |
| [B7](#book-b7) | Observability Engineering, 2.ª, Charity Majors, Liz Fong-Jones y George Miranda | ¿Qué investigar con los datos instrumentados? |
| [B8](#book-b8) | Fundamentals of Software Architecture, 2.ª, Mark Richards y Neal Ford | ¿Cómo justificar y comprobar una decisión arquitectónica? |
| [B9](#book-b9) | Learning Domain-Driven Design, Vlad Khononov | ¿Cuándo basta lógica sencilla y cuándo conviene un modelo rico? |
| [B10](#book-b10) | Clean Architecture, Robert C. Martin | ¿Qué límites propone la regla de dependencias? |
| [B11](#book-b11) | Architecture Patterns with Python, Harry Percival y Bob Gregory | ¿Cómo comparar servicios y puertos en Python? |
| [B12](#book-b12) | AI Engineering, Chip Huyen | ¿Cómo recuperar evidencia y evaluar una aplicación de IA? |
| [B14](#book-b14) | Prometheus: Up & Running, 2.ª, Julien Pivotto y Brian Brazil | ¿Cómo convertir métricas en consultas y alertas? |
| [B15](#book-b15) | Unit Testing Principles, Practices, and Patterns, Vladimir Khorikov | ¿Qué debe verificar una prueba de integración? |
| [B16](#book-b16) | Kubernetes: Up and Running, 3.ª, Brendan Burns, Joe Beda, Kelsey Hightower y Lachlan Evenson | ¿Cómo relacionar despliegue, réplicas y almacenamiento? |
| [B17](#book-b17) | Terraform: Up and Running, 3.ª, Yevgeniy Brikman | ¿Cómo gestionar estado y probar infraestructura? |

### Fichas y límites

<a id="book-b1"></a>

#### B1. Core Java I, 14.ª

**Autor:** Cay S. Horstmann. **[Editorial, portada y acceso/compra](https://www.oreilly.com/library/view/core-java-vol/9780135558553/)**.

**Para consultar:** capítulos/apartados 6.1; 4.7, 4.7.2 y 7.1; 8.1.4, 8.2 y 8.6.2; 10.3.2, 10.5.5–10.5.9 y 10.7.1. Java 25; ejemplo Java 21. Consultar solo los apartados usados. **Alcance de consulta:** Índice editorial contrastado; no lectura íntegra del libro.

<a id="book-b1-ii"></a>

#### B1-II. Core Java II, 14.ª

**Autor:** Cay S. Horstmann. **[Editorial, portada y acceso/compra](https://www.oreilly.com/library/view/core-java-volume/9780135558775/)**.

**Para consultar:** capítulos/apartados 1.3, 1.4 y 1.8; 5, especialmente 5.5 y 5.9; 6. Segundo volumen independiente; streams, JDBC y tiempo. No implica leerlo entero. **Alcance de consulta:** Índice editorial contrastado; no lectura íntegra del libro.

<a id="book-b2"></a>

#### B2. Spring Start Here, 2.ª MEAP

**Autor:** Laurentiu Spilca. **[Editorial, portada y acceso/compra](https://www.manning.com/books/spring-start-here-second-edition)**.

**Para consultar:** capítulos/apartados 2.2, 3.2.2 y 4.2; 10.3.3; 6.1 y 13. Numeración provisional; Spring 7/Boot 4. Revalidar al terminar la edición. **Alcance de consulta:** Índice y extractos públicos consultados; no lectura íntegra del libro.

<a id="book-b3"></a>

#### B3. Designing Data-Intensive Applications, 2.ª

**Autor:** Martin Kleppmann y Chris Riccomini. **[Editorial, portada y acceso/compra](https://www.oreilly.com/library/view/designing-data-intensive-applications/9781098119058/)**.

**Para consultar:** capítulos/apartados 4–5, 8–9 y 12. Índice de la segunda edición; no reutilizar números de la primera. **Alcance de consulta:** Índice editorial contrastado; no lectura íntegra del libro.

<a id="book-b4"></a>

#### B4. Kafka: The Definitive Guide, 2.ª

**Autor:** Gwen Shapira, Todd Palino, Rajini Sivaram y Krit Petty. **[Editorial, portada y acceso/compra](https://www.oreilly.com/library/view/kafka-the-definitive/9781492043072/titlepage01.html)**.

**Para consultar:** capítulos/apartados 3–4, 7–8 y 14. Fundamentos 2021. Kafka 4/KRaft y APIs se contrastan con documentación versionada. **Alcance de consulta:** Índice editorial contrastado; no lectura íntegra del libro.

<a id="book-b5"></a>

#### B5. Release It!, 2.ª

**Autor:** Michael Nygard. **[Editorial, portada y acceso/compra](https://www.oreilly.com/library/view/release-it-2nd/9781680504552/)**.

**Para consultar:** capítulos/apartados 4–5 y 13. Patrones 2018; no guía de versiones. **Alcance de consulta:** Índice y extractos públicos consultados; no lectura íntegra del libro.

<a id="book-b6"></a>

#### B6. Learning OpenTelemetry

**Autor:** Ted Young y Austin Parker. **[Editorial, portada y acceso/compra](https://www.oreilly.com/library/view/learning-opentelemetry/9781098147174/)**.

**Para consultar:** capítulos/apartados 3 y 5; 8. Conceptos 2024; APIs y Collector dependen de la versión. **Alcance de consulta:** Índice editorial contrastado; no lectura íntegra del libro.

<a id="book-b7"></a>

#### B7. Observability Engineering, 2.ª

**Autor:** Charity Majors, Liz Fong-Jones y George Miranda. **[Editorial, portada y acceso/compra](https://www.oreilly.com/library/view/observability-engineering-2nd/9781098179915/)**.

**Para consultar:** capítulos/apartados 8. Índice 2026; lectura conceptual adicional al libro OTel. **Alcance de consulta:** Índice editorial contrastado; no lectura íntegra del libro.

<a id="book-b8"></a>

#### B8. Fundamentals of Software Architecture, 2.ª

**Autor:** Mark Richards y Neal Ford. **[Editorial, portada y acceso/compra](https://www.oreilly.com/library/view/fundamentals-of-software/9781098175504/)**.

**Para consultar:** capítulos/apartados 3, 6, 8 y 21–22. Índice 2025; evaluar decisiones, no aplicar estilos como plantilla. **Alcance de consulta:** Índice editorial contrastado; no lectura íntegra del libro.

<a id="book-b9"></a>

#### B9. Learning Domain-Driven Design

**Autor:** Vlad Khononov. **[Editorial, portada y acceso/compra](https://www.oreilly.com/library/view/learning-domain-driven-design/9781098100124/)**.

**Para consultar:** capítulos/apartados 3–6 y 9. Conceptos 2021; módulos del ejemplo no acreditan bounded contexts descubiertos. **Alcance de consulta:** Índice y extractos públicos consultados; no lectura íntegra del libro.

<a id="book-b10"></a>

#### B10. Clean Architecture

**Autor:** Robert C. Martin. **[Editorial, portada y acceso/compra](https://www.oreilly.com/library/view/clean-architecture-a/9780134494272/ch22.xhtml)**.

**Para consultar:** capítulos/apartados 22. Contraste 2017. Aplicación Java usa Spring deliberadamente. **Alcance de consulta:** Índice y extractos públicos consultados; no lectura íntegra del libro.

<a id="book-b11"></a>

#### B11. Architecture Patterns with Python

**Autor:** Harry Percival y Bob Gregory. **[Editorial, portada y acceso/compra](https://www.cosmicpython.com/)**.

**Para consultar:** capítulos/apartados 2 y 4. Texto gratuito 2020; el caso SQLAlchemy/UoW difiere de AI/FastAPI. **Alcance de consulta:** Texto completo legalmente accesible en Cosmic Python; capítulos 2, 4 y 6 consultados.

<a id="book-b12"></a>

#### B12. AI Engineering

**Autor:** Chip Huyen. **[Editorial, portada y acceso/compra](https://www.oreilly.com/library/view/ai-engineering/9781098166298/)**.

**Para consultar:** capítulos/apartados 3–6 y 10. Digital 2024/comercial 2025. No sustituye API actual LangGraph ni evaluación real. **Alcance de consulta:** Índice editorial contrastado; no lectura íntegra del libro.

<a id="book-b14"></a>

#### B14. Prometheus: Up & Running, 2.ª

**Autor:** Julien Pivotto y Brian Brazil. **[Editorial, portada y acceso/compra](https://www.oreilly.com/library/view/prometheus-up/9781098131135/)**.

**Para consultar:** capítulos/apartados 18. Edición 2023 anterior a Prometheus 3. **Alcance de consulta:** Índice editorial contrastado; no lectura íntegra del libro.

<a id="book-b15"></a>

#### B15. Unit Testing Principles, Practices, and Patterns

**Autor:** Vladimir Khorikov. **[Editorial, portada y acceso/compra](https://www.manning.com/books/unit-testing)**.

**Para consultar:** capítulos/apartados 8. Capítulo 8 accesible. C#2020; recomendaciones metodológicas, no APIs Java/Python. **Alcance de consulta:** Índice y capítulo 8 público consultado; no se presupone acceso al resto.

<a id="book-b16"></a>

#### B16. Kubernetes: Up and Running, 3.ª

**Autor:** Brendan Burns, Joe Beda, Kelsey Hightower y Lachlan Evenson. **[Editorial, portada y acceso/compra](https://www.oreilly.com/library/view/kubernetes-up-and/9781098110192/)**.

**Para consultar:** capítulos/apartados 9, 10, 13 y 16. Edición 2022; manifiestos/probes/HPA con docs vigentes. **Alcance de consulta:** Índice editorial contrastado; no lectura íntegra del libro.

<a id="book-b17"></a>

#### B17. Terraform: Up and Running, 3.ª

**Autor:** Yevgeniy Brikman. **[Editorial, portada y acceso/compra](https://www.oreilly.com/library/view/terraform-up-and/9781098116736/)**.

**Para consultar:** capítulos/apartados 3 y 9. Edición 2022; mocks nativos se documentan aparte con HashiCorp. **Alcance de consulta:** Índice editorial contrastado; no lectura íntegra del libro.

<a id="por-tema"></a>

## Referencias por tema

Cada fila enlaza al apartado del manual y a código representativo. Los rangos amplios son ampliaciones opcionales: la lectura inicial de cada capítulo selecciona menos contenido. No se citan páginas porque cambian entre formatos. La numeración de Spring Start Here MEAP debe revalidarse al publicarse la edición terminada.

<a id="chapters-architecture"></a>

### Arquitectura y Java

| Tema y código | Libro: capítulos/apartados | Para qué leerlo |
|---|---|---|
| <a id="refs-r01"></a>[R01. Separar las responsabilidades](handbook/architecture.md#ref-topic-r01) · [ManageOrderService.java](../apps/commerce-api/src/main/java/dev/reference/commerce/orders/application/service/ManageOrderService.java) | [B2](#book-b2): 2.2, 3.2.2 y 4.2<br>[B1](#book-b1): 6.1 | beans, inyección por constructor y abstracciones; interfaces como contratos. |
| <a id="refs-r02"></a>[R02. Por qué tres servicios](handbook/architecture.md#ref-topic-r02) · [ManageOrderService.java](../apps/commerce-api/src/main/java/dev/reference/commerce/orders/application/service/ManageOrderService.java) | [B9](#book-b9): 3–4<br>[B8](#book-b8): 3 y 8 | límites de modelo e integración entre contextos; modularidad y componentes frente a despliegue. |
| <a id="refs-r03"></a>[R03. Del requisito a la decisión](handbook/architecture.md#ref-topic-r03) · [ADR001](decisions/001-boundaries.md) | [B8](#book-b8): 21 | registrar decisiones, alternativas y consecuencias. |
| <a id="refs-r04"></a>[R04. Patrones con una responsabilidad concreta](handbook/architecture.md#ref-topic-r04) · [ports.py](../apps/ai-service/src/ai_service/application/ports.py) | [B10](#book-b10): 22<br>[B11](#book-b11): 2 y 4 | inversión de dependencias, límites, negocio y organización; repositorio y capa de servicios en Python. |
| <a id="refs-r05"></a>[R05. Comprobación reproducible: comprobar las dependencias permitidas](handbook/architecture.md#ref-topic-r05) · [ArchitectureRules.java](../apps/commerce-api/src/test/java/dev/reference/commerce/ArchitectureRules.java) | [B8](#book-b8): 6 | fitness functions y gobierno automatizado. |
| <a id="refs-r06"></a>[R06. Caso de aplicación: proteger una cantidad](handbook/architecture.md#ref-topic-r06) · [Quantity.java](../apps/commerce-api/src/main/java/dev/reference/commerce/shared/domain/Quantity.java) | [B1](#book-b1): 4.7, 4.7.2 y 7.1<br>[B9](#book-b9): 6 | records, constructores y excepciones; invariantes y value objects como contraste. |

<a id="chapters-api-contracts"></a>

### Contratos HTTP y paginación

| Tema y código | Libro: capítulos/apartados | Para qué leerlo |
|---|---|---|
| <a id="refs-r07"></a>[R07. Traducir errores de negocio a HTTP](handbook/api-contracts.md#ref-topic-r07) · [ProblemAdvice.java](../apps/commerce-api/src/main/java/dev/reference/commerce/shared/infrastructure/rest/ProblemAdvice.java) | [B2](#book-b2): 10.3.3 | REST y gestión de excepciones. |
| <a id="refs-r08"></a>[R08. Paginar pedidos con un cursor](handbook/api-contracts.md#ref-topic-r08) · [ManageOrderService.java](../apps/commerce-api/src/main/java/dev/reference/commerce/orders/application/service/ManageOrderService.java) | [B1-II](#book-b1-ii): 1.3, 1.4 y 1.8<br>[B3](#book-b3): 4 | transformación, limitación y recogida del stream; índices y acceso a datos. |
| <a id="refs-r09"></a>[R09. Comprobación reproducible: contrato y aislamiento reales](handbook/api-contracts.md#ref-topic-r09) · [CommerceIntegrationTest.java](../apps/commerce-api/src/test/java/dev/reference/commerce/CommerceIntegrationTest.java) | [B15](#book-b15): 8 | diseño de pruebas de integración. |

<a id="chapters-transactions"></a>

### Transacciones e idempotencia

| Tema y código | Libro: capítulos/apartados | Para qué leerlo |
|---|---|---|
| <a id="refs-r10"></a>[R10. Evitar que dos pedidos vendan la misma unidad](handbook/transactions.md#ref-topic-r10) · [ManageOrderService.java](../apps/commerce-api/src/main/java/dev/reference/commerce/orders/application/service/ManageOrderService.java) | [B2](#book-b2): 6.1 y 13<br>[B3](#book-b3): 8 | proxy y transacciones Spring; atomicidad, aislamiento y concurrencia. |
| <a id="refs-r11"></a>[R11. Recuperar el resultado al repetir una petición](handbook/transactions.md#ref-topic-r11) · [JdbcIdempotencyAdapter.java](../apps/commerce-api/src/main/java/dev/reference/commerce/shared/infrastructure/persistence/JdbcIdempotencyAdapter.java) | [B3](#book-b3): 8<br>[B1](#book-b1): 8.1.4, 8.2 y 8.6.2 | coordinación transaccional e idempotencia de efectos; métodos genéricos, borrado y `Class<T>`. |
| <a id="refs-r12"></a>[R12. Esquema que sostiene las garantías](handbook/transactions.md#ref-topic-r12) · [JdbcOrderRepositoryAdapter.java](../apps/commerce-api/src/main/java/dev/reference/commerce/orders/infrastructure/persistence/JdbcOrderRepositoryAdapter.java) | [B1-II](#book-b1-ii): 5, especialmente 5.5 y 5.9 | JDBC, consultas preparadas y transacciones. |
| <a id="refs-r13"></a>[R13. Caso de aplicación: corregir una carrera](handbook/transactions.md#ref-topic-r13) · [CommerceIntegrationTest.java](../apps/commerce-api/src/test/java/dev/reference/commerce/CommerceIntegrationTest.java) | [B15](#book-b15): 8 | dependencias reales y regresiones de integración. |

<a id="chapters-events"></a>

### Eventos y Kafka Streams

| Tema y código | Libro: capítulos/apartados | Para qué leerlo |
|---|---|---|
| <a id="refs-r14"></a>[R14. Publicar después de confirmar](handbook/events.md#ref-topic-r14) · [OutboxEventsAdapter.java](../apps/commerce-api/src/main/java/dev/reference/commerce/shared/infrastructure/messaging/OutboxEventsAdapter.java) | [B9](#book-b9): 9<br>[B4](#book-b4): 3, 7 y 8 | outbox y coordinación entre componentes; productor, entrega fiable y alcance de exactly-once. |
| <a id="refs-r15"></a>[R15. Mensajes inválidos](handbook/events.md#ref-topic-r15) · [KafkaConfiguration.java](../apps/commerce-api/src/main/java/dev/reference/commerce/shared/infrastructure/messaging/KafkaConfiguration.java) | [B3](#book-b3): 5<br>[B4](#book-b4): 4 y 7 | esquemas, codificación y evolución; consumo y entrega fiable. |
| <a id="refs-r16"></a>[R16. Ventanas y deduplicación](handbook/events.md#ref-topic-r16) · [OrderSummaryTopology.java](../apps/order-analytics/src/main/java/dev/reference/analytics/OrderSummaryTopology.java) | [B4](#book-b4): 14<br>[B3](#book-b3): 12 | procesamiento con Kafka Streams; tiempo de evento, agregaciones y estado. |
| <a id="refs-r17"></a>[R17. Relojes incorrectos y cuarentena](handbook/events.md#ref-topic-r17) · [OrderSummaryTopology.java](../apps/order-analytics/src/main/java/dev/reference/analytics/OrderSummaryTopology.java) | [B3](#book-b3): 9 | relojes no fiables y fallos de sistemas distribuidos. |

<a id="chapters-retrieval"></a>

### Recuperación y evaluación IA

| Tema y código | Libro: capítulos/apartados | Para qué leerlo |
|---|---|---|
| <a id="refs-r18"></a>[R18. Buscar información antes de generar texto](handbook/retrieval.md#ref-topic-r18) · [retrieval.py](../apps/ai-service/src/ai_service/infrastructure/retrieval.py) | [B12](#book-b12): 6 | recuperación, RAG y elección del método. |
| <a id="refs-r19"></a>[R19. Comparar la simulación con el proveedor real](handbook/retrieval.md#ref-topic-r19) · [providers.py](../apps/ai-service/src/ai_service/infrastructure/providers.py) | [B12](#book-b12): 10 | arquitectura, integración y controles operativos. |
| <a id="refs-r20"></a>[R20. Medir la recuperación de documentos](handbook/retrieval.md#ref-topic-r20) · [evaluate.py](../apps/ai-service/src/ai_service/evaluate.py) | [B12](#book-b12): 3, 4 y 6 | evaluación de sistemas y recuperación. |
| <a id="refs-r21"></a>[R21. Evaluar si la respuesta está respaldada](handbook/retrieval.md#ref-topic-r21) · [answers.py](../apps/ai-service/src/ai_service/application/answers.py) | [B12](#book-b12): 3–4 | criterios de evaluación de respuesta. |

<a id="chapters-workflows"></a>

### Workflows y aprobación

| Tema y código | Libro: capítulos/apartados | Para qué leerlo |
|---|---|---|
| <a id="refs-r22"></a>[R22. Separar solicitud, aprobación y ejecución](handbook/workflows.md#ref-topic-r22) · [ReturnProposal.java](../apps/commerce-api/src/main/java/dev/reference/commerce/returns/domain/ReturnProposal.java) | [B9](#book-b9): 5–6<br>[B1-II](#book-b1-ii): 6 | orquestación de aplicación frente a reglas de dominio; Instant y reglas temporales. |
| <a id="refs-r23"></a>[R23. Reanudar tras una respuesta perdida](handbook/workflows.md#ref-topic-r23) · [workflows.py](../apps/ai-service/src/ai_service/infrastructure/workflows.py) | [B12](#book-b12): 6 y 10<br>[B3](#book-b3): 5 y 9 | workflows y arquitectura de integración; ejecución durable y fallos parciales. |
| <a id="refs-r24"></a>[R24. Persistencia y escalado](handbook/workflows.md#ref-topic-r24) · [workflows.py](../apps/ai-service/src/ai_service/infrastructure/workflows.py) | [B3](#book-b3): 8–9 | control concurrente y límites ante fallos. |

<a id="chapters-security"></a>

### Seguridad y resiliencia

| Tema y código | Libro: capítulos/apartados | Para qué leerlo |
|---|---|---|
| <a id="refs-r25"></a>[R25. Modelo de amenazas mínimo](handbook/security.md#ref-topic-r25) · [providers.py](../apps/ai-service/src/ai_service/infrastructure/providers.py) | [B12](#book-b12): 5 | entradas no confiables y defensas de prompting. |
| <a id="refs-r26"></a>[R26. Fallos de dependencias](handbook/security.md#ref-topic-r26) · [providers.py](../apps/ai-service/src/ai_service/infrastructure/providers.py) | [B5](#book-b5): 4–5 | fallos en cascada, timeouts, circuit breaker y bulkheads. |
| <a id="refs-r27"></a>[R27. Comprobación reproducible: comprobar esperas y cancelaciones](handbook/security.md#ref-topic-r27) · [test_providers.py](../apps/ai-service/tests/test_providers.py) | [B15](#book-b15): 8 | frontera de integración y sustitución de dependencias externas. |

<a id="chapters-operations"></a>

### Observabilidad y ciclo de vida

| Tema y código | Libro: capítulos/apartados | Para qué leerlo |
|---|---|---|
| <a id="refs-r28"></a>[R28. Elegir entre métricas, trazas y logs](handbook/operations.md#ref-topic-r28) · [CorrelationFilter.java](../apps/commerce-api/src/main/java/dev/reference/commerce/shared/infrastructure/rest/CorrelationFilter.java) | [B6](#book-b6): 3 y 5<br>[B7](#book-b7): 8 | señales e instrumentación; análisis de datos instrumentados. |
| <a id="refs-r29"></a>[R29. Para profundizar](handbook/operations.md#ref-topic-r29) · [collector.yaml](../infra/observability/collector.yaml) | [B6](#book-b6): 8 | pipelines de telemetría. |
| <a id="refs-r30"></a>[R30. Prueba rápida de las reglas de alerta](handbook/operations.md#ref-topic-r30) · [alerts.yaml](../infra/observability/alerts.yaml) | [B14](#book-b14): 18 | reglas de alerta, for, etiquetas y anotaciones de las alertas. |
| <a id="refs-r35"></a>[R35. Ciclo de vida local](handbook/operations.md#ref-topic-r35) · [StreamsLifecycle.java](../apps/order-analytics/src/main/java/dev/reference/analytics/StreamsLifecycle.java) | [B1](#book-b1): 10.3.2, 10.5.5–10.5.9 y 10.7.1<br>[B5](#book-b5): 5 | sincronización, tareas y concurrencia; esperas acotadas y recursos. |

<a id="chapters-delivery"></a>

### Entrega e infraestructura

| Tema y código | Libro: capítulos/apartados | Para qué leerlo |
|---|---|---|
| <a id="refs-r31"></a>[R31. Laboratorio Kubernetes](handbook/delivery.md#ref-topic-r31) · [apps.yaml](../infra/kubernetes/apps.yaml) | [B16](#book-b16): 9, 10, 13 y 16 | réplicas, despliegues, configuración y almacenamiento. |
| <a id="refs-r32"></a>[R32. Infraestructura en Google Cloud](handbook/delivery.md#ref-topic-r32) · [main.tf](../infra/cloud/main.tf) | [B17](#book-b17): 3 y 9 | estado y pruebas automatizadas de infraestructura; contrastar los mocks actuales con HashiCorp. |
| <a id="refs-r33"></a>[R33. Entregar cambios y preparar la reversión](handbook/delivery.md#ref-topic-r33) · [test.sh](../scripts/test.sh) | [B5](#book-b5): 13<br>[B8](#book-b8): 6 | despliegue y cambios; comprobaciones automatizadas de arquitectura. |
| <a id="refs-r34"></a>[R34. Reutilizar la referencia en un sistema real](handbook/delivery.md#ref-topic-r34) · [apps.yaml](../infra/kubernetes/apps.yaml) | [B8](#book-b8): 22 | riesgos y análisis de arquitectura. |

<a id="repositorios"></a>

## Ejemplos de código externos

Lectura comparada, no dependencias que haya que incorporar. Fragmentos fijados a los commits consultados el 22-10-2026. No se han ejecutado estos proyectos como parte de la referencia. El tamaño de la comunidad no sustituye la inspección del ejemplo.

| Repositorio y fragmento | Qué observar | Diferencia que conservar |
|---|---|---|
| [spring-projects/spring-modulith](https://github.com/spring-projects/spring-modulith/blob/1028b09682320bb3feaf017c72379a81835e3ad3/spring-modulith-core/src/main/java/org/springframework/modulith/core/ApplicationModules.java) | `ApplicationModules.of` y `verify`: detección y comprobación de módulos. | Biblioteca externa frente a las guardas ArchUnit específicas del ejemplo. |
| [apache/kafka](https://github.com/apache/kafka/blob/e4cc5d18f45d2965e4e1e571adc34bfb80cba642/streams/examples/src/main/java/org/apache/kafka/streams/examples/wordcount/WordCountDemo.java) | WordCount: `stream → groupBy → count → toStream`. | Aquí hay clave tenant/SKU, ventana, estado y deduplicación. |
| [run-llama/llama_index](https://github.com/run-llama/llama_index/blob/8469a034226d20b70a667dc7faf013770716709f/docs/examples/low_level/retrieval.ipynb) | Recuperación vectorial por pasos: embedding, consulta y selección de nodos. | El notebook utiliza Pinecone y servicios externos; leer sin ejecutarlo ni añadirlos. No acredita aislamiento por tenant. |
| [resilience4j/resilience4j](https://github.com/resilience4j/resilience4j/blob/2f3d998c85f10b8ff88a3dca9f24bacf11c3a250/resilience4j-circuitbreaker/src/main/java/io/github/resilience4j/circuitbreaker/internal/CircuitBreakerStateMachine.java) | Transiciones cerrado/abierto/prueba en la máquina de estados. | Biblioteca Java configurable frente al circuito Python pequeño y sus pruebas. |
| [open-telemetry/opentelemetry-demo](https://github.com/open-telemetry/opentelemetry-demo/blob/3ee33db6f17a7482f63553c514c81c53ba8a696f/src/otel-collector/otelcol-config.yml) | Recorrido receivers/processors/exporters en los pipelines. | Demo mucho mayor; no copiar su topología ni sus nombres de componentes sin contrastar versión. |
| [GoogleCloudPlatform/microservices-demo](https://github.com/GoogleCloudPlatform/microservices-demo/tree/99d6025d494fd2ad9cd49a7881d590e7d8a773e5/kubernetes-manifests) | `frontend.yaml`: labels, selector, Service, probes y recursos. | Más servicios y requisitos; no es una topología mínima necesaria. |
| [cosmicpython/code](https://github.com/cosmicpython/code/blob/14c84797ffa77255d53cf1a02fe6aafda2b68aeb/src/allocation/adapters/repository.py) | `repository.py` y `unit_of_work.py`: frontera de datos y transacción explícita. | El servicio AI no implementa SQLAlchemy ni esta unidad de trabajo. |
| [spring-guides/gs-relational-data-access](https://github.com/spring-guides/gs-relational-data-access/blob/a4c1d4f0d8b2957a3b73953081698e8defe9fc78/complete/src/main/java/com/example/relationaldataaccess/RelationalDataAccessApplication.java) | Inyección de JdbcTemplate, parámetros y RowMapper. | Ejemplo mínimo; no demuestra locks, rollback ni concurrencia del caso de comercio. |
| [langchain-ai/langgraph](https://github.com/langchain-ai/langgraph/blob/c2df8ab04b120872e3cc11dc9506a9a25dc48b58/libs/langgraph/tests/test_interrupt_state.py) | `test_interrupt_state.py`: reanudación y tareas ya completadas. | Pruebas de biblioteca más avanzadas; escoger los casos nombrados, comprobar compatibilidad con el lockfile. |

En LangGraph, empezar por `test_finished_task_does_not_report_answered_interrupt` y `test_resume_at_the_latest_checkpoint_does_not_rerun_finished_task`, junto a la [documentación de interrupciones](https://docs.langchain.com/oss/python/langgraph/interrupts). El antiguo notebook de human-in-the-loop redirige a documentación y no se utiliza como tutorial vigente.

## Documentación de versiones y mantenimiento

- [Spring: transacciones declarativas](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html) y [errores HTTP](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html).
- [PostgreSQL: aislamiento Read Committed](https://www.postgresql.org/docs/17/transaction-iso.html#XACT-READ-COMMITTED) y [bloqueos](https://www.postgresql.org/docs/17/explicit-locking.html).
- [Kafka 4: Streams](https://kafka.apache.org/40/streams/developer-guide/dsl-api/) y [configuración](https://kafka.apache.org/40/streams/developer-guide/config-streams/).
- [Python 3.12: tareas, timeout y cancelación](https://docs.python.org/3.12/library/asyncio-task.html) y [HTTPX: plazos por fase](https://www.python-httpx.org/advanced/timeouts/).
- [LangGraph: persistencia](https://docs.langchain.com/oss/python/langgraph/persistence) e [interrupciones](https://docs.langchain.com/oss/python/langgraph/interrupts).
- [OpenTelemetry: contexto](https://opentelemetry.io/docs/concepts/context-propagation/) y [Collector](https://opentelemetry.io/docs/collector/configuration/).
- [Prometheus: reglas sintéticas](https://prometheus.io/docs/prometheus/latest/configuration/unit_testing_rules/) y [histogramas](https://prometheus.io/docs/practices/histograms/).
- [Kubernetes: probes](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/) y [HPA](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale-walkthrough/).
- [Terraform: mocks nativos](https://developer.hashicorp.com/terraform/language/tests/mocking), posteriores a la edición del libro.

**Fecha del inventario:** 23-10-2026; índices y extractos editoriales consultados el 22 y 23-10-2026. Las fuentes vivas pueden cambiar. Antes de reutilizar una API se contrasta su versión con el código y sus locks. Una referencia de índice acredita localización, no profundidad exacta del tratamiento ni lectura completa. Las limitaciones de la implementación se mantienen en el manual y en [verificación](verification.md).

[Volver al índice](index.md) · [Consultar un concepto](reference-map.md)
