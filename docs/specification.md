# Especificación de la referencia

Versión 5, 2026-10-23. Fuente canónica de requisitos implementables. Sustituye propuestas de alcance anteriores. Estado de ejecución en [verificación](verification.md); criterios en [aceptación](acceptance.md).

## Ampliación de referencia y mantenimiento

Se conservan tres aplicaciones, nueve capítulos, Java 21, Python 3.12 y sus contratos de éxito. Se completan errores HTTP de framework, diagnóstico seguro, guardas intermodulares y versiones soportadas de observabilidad, con regresiones. La publicación mantiene separados hechos implementados, ejercicios y alternativas; no añade cloud ni modelos de pago.

El índice ofrece lectura secuencial y tres sesiones cortas Java/Spring; el mapa existente permite consulta por concepto. Cada tema relevante enlaza explicación, ejemplo, código, comprobación y documentación oficial. La bibliografía central reúne 17 volúmenes y nueve ejemplos externos, con 35 correspondencias y 50 asociaciones opcionales; el manual prioriza una lectura y un contraste por capítulo. Ediciones, capítulos y fragmentos se contrastan con fuentes primarias; no se presupone acceso completo a libros ni aval de autores. Se sustituye el mantenimiento público de estrellas por utilidad, fuente y fecha de consulta. La verificación registra resultados reales y sus límites.

## Objetivo y límites

Tres servicios pequeños, funcionales en Docker, explican decisiones de sistemas profesionales: Java transaccional, procesamiento de eventos y Python/IA. Material público impersonal; datos y dominio sintéticos. Código de referencia, sin afirmar certificación productiva ni alta disponibilidad de dependencias locales.

El código fuente externo no se modifica ni se copia en bloque. No publicar, facturar APIs automáticamente ni ejecutar Terraform apply. No frontend, pagos reales, event sourcing, service mesh ni entrenamiento de modelos.

## Contratos comunes

- JSON camelCase; UUID en identidades; UTC ISO-8601 en fechas; importes enteros en céntimos EUR.
- JWT OIDC firmado, validación de issuer/audience/expiración. Claims `sub`, `tenant`, `realm_access.roles`; roles CUSTOMER y MANAGER. Tenant y usuario se derivan del token, nunca de un campo controlado por el cliente.
- Un usuario CUSTOMER solo ve sus pedidos; MANAGER administra pedidos del mismo tenant. Otro tenant no recibe datos. No existe endpoint de emisión arbitraria de JWT en las aplicaciones.
- Respuestas de error Problem Details: status, title, detail y correlationId; sin stacktrace ni secretos. Los 401 incluyen el desafío `WWW-Authenticate: Bearer`. 400 datos inválidos, 401 credenciales ausentes/inválidas, 403 permiso insuficiente, 404 recurso no visible, 409 conflicto, 503 indisponibilidad temporal por saturación, plazo agotado o dependencia no disponible.
- `Idempotency-Key` obligatorio en creación de pedido y propuesta/ejecución de devolución. La clave se vincula a tenant/actor/operación/hash del cuerpo. Repetición idéntica devuelve resultado anterior; cuerpo diferente produce 409. Registros persistidos junto al efecto de negocio. El ámbito se codifica sin ambigüedad entre componentes; no restringir arbitrariamente sub a UUID. Los ámbitos antiguos ambiguos requieren una decisión explícita de migración: no borrar ni reinterpretar claves automáticamente.
- Los probes de salud no requieren JWT. Todas las APIs de negocio sí. Correlación propagada por HTTP y eventos sin registrar cuerpos o tokens.

## commerce-api · API de comercio

Java 21, Spring Boot 4.1.1, Maven Wrapper. Baseline actualizado durante el análisis de dependencias: [ADR 006](decisions/006-maintenance.md). Capas por módulo `orders`, `inventory`, `returns`: domain, application/model, application/port/input, application/port/output, application/service, infrastructure. Domain no importa frameworks. Puertos independientes de infraestructura. El dominio depende solo del JDK y del dominio propio; cada puerto output pertenece a su módulo. La colaboración entre módulos usa puertos input, no repositorios ajenos. Las mismas reglas ArchUnit se ejecutan sobre producción y fixtures negativos representativos. Spring transaccional en aplicación es una decisión explícita. PostgreSQL y Flyway; locking pesimista o update condicional protege inventario en una transacción.

- `GET /api/v1/inventory/{sku}`: stock disponible y versión. Seed SKU `BOOK-1`, precio 2500, stock inicial 10 por tenant `demo` y `other`.
- `POST /api/v1/orders`: `{sku,quantity}` (1..100), devuelve 201 `{id,sku,quantity,totalCents,status,version}`. Precio del catálogo; decremento stock, pedido, idempotencia y outbox atómicos. Sin HTTP externo dentro de la transacción.
- `GET /api/v1/orders/{id}`: recurso visible o 404. Estados PLACED y REFUNDED.
- `POST /api/v1/returns`: `{orderId,reason}` no vacía de hasta 500 caracteres. Crea PENDING para pedido PLACED del actor/tenant; único proceso activo por pedido.
- `GET /api/v1/returns/{id}`: propuesta, versión, estado y vencimiento si existe aprobación.
- `POST /api/v1/returns/{id}/approve`: MANAGER del mismo tenant, `{version}`. Registra aprobación de esa versión y contenido con vencimiento a diez minutos.
- `POST /api/v1/returns/{id}/execute`: MANAGER del tenant, `{version}` y clave idempotente. Comprueba aprobación/versión/vencimiento dentro de la transacción. Marca EXECUTED, pedido REFUNDED y repone stock exactamente una vez. No dinero ni proveedor externo real.

Versionar propuestas impide aprobar contenido distinto al ejecutado. No modificar una propuesta aprobada: una nueva requiere nueva aprobación. Los bloqueos y constraints resuelven carreras de concurrencia; preconsultas no sustituyen constraints.

## Eventos y consumidor

Topic `orders.placed.v1`, tres particiones, key orderId, sobre `{eventId,eventType,schemaVersion,occurredAt,aggregateId,tenantId,correlationId,data}`; tipo OrderPlaced, versión 1, data `{orderId,sku,quantity,totalCents}`.

Outbox PostgreSQL en misma transacción de pedido; publicador por lotes con `FOR UPDATE SKIP LOCKED`, espera confirmación Kafka antes de marcar publicado. Fallo conserva registro pendiente y contador de intento; caída después del envío permite duplicado. Sin prometer transacción distribuida.

Consumidor de auditoría dentro de commerce-api con grupo independiente y tabla inbox UNIQUE(eventId), registra efecto y deduplicación atómicos antes de confirmar offset. Mensaje inválido usa retries limitados y topic `orders.placed.v1.dlt`; fallo de envío a DLT no se confirma como procesado. Procedimiento explícito de reproceso conserva identidad. Las identidades eventId, aggregateId y data.orderId exigen representación UUID completa 8-4-4-4-12, sin abreviaturas; se aceptan mayúsculas/minúsculas y se normaliza la identidad antes de deduplicar. Ambos consumidores prueban el mismo corpus de representaciones. Eventos no contienen datos personales.

## order-analytics

Java 21, Kafka Streams 4.0.2, Maven. Consumidor del mismo contrato, deduplica eventId con store persistente, agrupa por tenant y sku, ventanas tumbling de un minuto sobre occurredAt, grace treinta segundos. Topic de resultados `orders.summary.v1` con `{tenantId,sku,windowStart,windowEnd,orderCount,totalQuantity,totalCents}`. No proyección del estado de devoluciones: resume hechos de creación de pedido.

Retención de entrada y deduplicación de siete días; después no hay promesa de deduplicación de eventos históricos reintroducidos. Eventos tardíos se cuentan y descartan; inválidos van a DLT. Estado local reconstruible mediante changelog. Prueba determinista usa avance explícito de stream time. REST no necesario; resultados verificables consumiendo topic de salida.

## ai-service · API del asistente

Python 3.12, FastAPI, Pydantic, httpx, LangGraph, SQLite checkpoint persistente local para workflow (un proceso); PostgreSQL para variante vectorial. `Protocol` para proveedor y gateway de comercio. Dominio sin FastAPI/SDK. Import-linter controla dependencias.

- `POST /api/v1/answers`: `{question,retrievalMode}` con question de 1..2000; modos lexical (predeterminado) y vector. Devuelve `{answer,citations,abstained,mode}`. Solo corpus sintético autorizado del tenant, seleccionado en servidor.
- `POST /api/v1/answers/stream`: mismo input y SSE `start`, `result` o `error`; no ejecutar decisiones sobre contenido parcial. Generación completa validada antes de emitir resultado. Liberar recursos al cancelar cliente.
- `POST /api/v1/workflows/returns`: `{orderId,reason}`, crea propuesta llamando API commerce con JWT del actor; devuelve `{workflowId,returnId,status}`. No guardar JWT en checkpoints.
- `GET /api/v1/workflows/{id}`: solo propietario del mismo tenant o MANAGER de ese tenant.
- `POST /api/v1/workflows/{id}/approve`: MANAGER del tenant; aprobar y reanudar workflow. El backend revalida aprobación/versión al ejecutar. Clave estable de ejecución basada en returnId. Reintentar tras fallo no repite devolución.

Proveedor `fake` predeterminado y determinista; siempre identificado como simulación. Adaptador real OpenAI vía Responses API con esquema estructurado, modelo obligatorio por configuración, sin selección implícita. No ejecutar con clave sin presupuesto/configuración explícitos. Timeout HTTP de diez segundos por fase de red, máximo dos intentos solo transitorios, circuit breaker abre tras tres fallos y prueba recuperación a treinta segundos. El adaptador valida sobre, tipos de salida y referencias suministradas por recuperación autorizada antes de registrar éxito; un incumplimiento del contrato cuenta como fallo del breaker, sin reintento de ese contenido. Máximo cuatro llamadas concurrentes y 512 tokens de salida. El plazo total de doce segundos incluye la espera de capacidad y la ejecución. El presupuesto de generación y sus intentos debe terminar dentro de ese plazo y registrar fallos por timeout en el breaker, distinguiéndolos de la cancelación del cliente. Los resultados IA, incluidos errores y cancelaciones, se contabilizan una sola vez con etiquetas acotadas. Falta de evidencia devuelve abstención sin llamar al proveedor de generación. La recuperación vectorial puede haber llamado antes al adaptador de embeddings.

Corpus lexical versionado con referencias y tenant, recuperación top-3. Variant vector pgvector con embedding configurado, dimensión validada y filtro tenant en consulta. No insertar documentos mediante API pública. Dataset mínimo cuarenta casos con relevancias, abstenciones y ataques. Recall@3 >=0.90 sobre casos respondibles del corpus; 100% aislamiento y referencias existentes. La fórmula de recall admite varias relevancias por pregunta; hit-rate y MRR se informan separadamente. Calidad real LLM requiere evaluación independiente, no se infiere de fake.

Las mutaciones del workflow tienen un plazo total de doce segundos, incluida la espera del lock, y admiten como máximo dieciséis operaciones activas o en espera por proceso. Exceso o plazo agotado devuelve 503; cancelación libera cupo. La serialización SQLite es una capacidad deliberadamente limitada, no aislamiento de rendimiento entre tenants. El reintento conserva identidad idempotente.

Analytics rechaza occurredAt adelantado más de treinta segundos respecto a un Clock de proceso antes de asignar tiempo de evento; la extracción conserva el tiempo previo de partición y el registro va a DLT con timestamp del reloj al aislarlo, para que un dato de 1970 no agote su retención. El reloj debe estar sincronizado. Esta política operativa no sustituye el esquema del evento: pasado/replay conserva las restricciones de grace/retención.

## Seguridad y entornos

Observabilidad local optativa con OpenTelemetry, Prometheus, Tempo y Grafana: métricas para tendencias/alertas, trazas para seguir una operación y logs estructurados para hechos diagnósticos. No requiere Datadog ni cuenta externa. El escenario verifica targets, incremento de señales y traza de una operación actual, con plazos y sin aceptar datos históricos como evidencia. No registrar cuerpos, preguntas o tokens; etiquetas métricas de cardinalidad acotada. La consola de contenedores es suficiente para logs locales; no añadir un almacén de logs sin necesidad demostrada.

El laboratorio HPA debe observar una transición real de una a dos réplicas disponibles y comprobar idempotencia concurrente entre pods. La carga y cualquier ajuste temporal son acotados, visibles y restaurados; no equivale a un benchmark o a disponibilidad cloud.

Keycloak local como OIDC: realm `reference`, client público `reference-cli` con password grant solo para automatizar demostración local. Usuarios alice (CUSTOMER/demo), manager (MANAGER/demo) y bob (CUSTOMER/other). Credenciales sintéticas explícitas en import local; nunca describir password grant como opción recomendada para cliente público productivo. Producción usa Authorization Code + PKCE. Audiencia `reference-api`, issuer validado. Proveedor configurado por entorno; JWKS interno y issuer público separados para networking Docker.

Docker Compose: núcleo Postgres/pgvector, Kafka KRaft, Keycloak y tres servicios. Volúmenes con nombre de proyecto, puertos publicados solo loopback. Perfiles observabilidad (OTel Collector, Prometheus, Grafana, Tempo) y live-ai (config optativa). Java heap acotado; conjunto dentro de 10 GiB Docker. Contenedores no-root para aplicaciones, probes y apagado limpio. No destruir volúmenes en `down` normal.

Kubernetes kind dedicado: mismos servicios/imágenes, namespace propio, probes, requests/limits, PDB y HPA 1..2 para commerce-api; metrics-server explícito para demostración. AI conserva una réplica: SQLite y locks por proceso no permiten escalado horizontal seguro del workflow. Cambiar el checkpointer a PostgreSQL también requeriría exclusión/CAS distribuidos; véase ADR de despliegue. Kafka/Postgres/Keycloak locales no HA. No modificar otro contexto ni despliegue existente. Esta precisión corrige la incompatibilidad entre el requisito inicial «HPA para APIs» y la persistencia elegida, antes de desplegar.

Terraform: base VPC, Artifact Registry y GKE Autopilot/IAM. Backends DB/Kafka cloud son entradas externas documentadas. Solo fmt/validate y tests plan con mocks; no apply. CLI fijada en contenedor, no actualizar host. Tests de mocks no acreditan despliegue cloud. Aserciones de IAM comprueban el rol mínimo oficial de nodos y lectura acotada del repositorio, no el número de roles. operator_cidr admite únicamente IPv4 /32.

## Calidad editorial y referencias

La presentación principal es una referencia profesional de decisiones implementadas y verificadas: problema → restricciones → alternativas → decisión → implementación → evidencia → límites. La portada permite localizar criterios y comprobaciones; se mantienen tanto la consulta por problema como la lectura progresiva para repasar y compartir conocimiento. El cambio de enfoque conserva íntegramente explicaciones, ejemplos, ejercicios, pistas y soluciones, sin atribuir experiencia productiva ni resultados que no estén acreditados. Los casos hipotéticos y laboratorios mantienen sus límites visibles.

La referencia conserva una lectura progresiva: preparación del entorno y nueve capítulos sobre la misma tienda de ejemplo. Cada capítulo conecta con el anterior, indica el objetivo, guía la lectura y comprobación del código y termina con una pregunta razonada y el enlace al siguiente. Las instrucciones de mantenimiento y la evidencia de entrega se consultan aparte. Usar español natural de España y explicar los términos técnicos antes de apoyarse en ellos; conservar nombres de código y contratos. La estructura se inspira en la progresión de The Rust Book y el tutorial de Django, sin copiar su contenido ni ampliar el stack.


La cobertura didáctica conecta módulos y contratos, concurrencia e idempotencia, broker y consumidores, recuperación y evaluación, coordinación y autorización, fallos y operación. Explicar los términos al aparecer y usar ejemplos pequeños: cursor con sobrelectura, última unidad concurrente, ventana temporal y ranking recuperado. Tres vistas específicas explican topología Kafka, concurrencia SQL y estados del circuito IA; complementan secuencias y flujos existentes dentro del mismo maestro editable. Los ejemplos ilustran el contrato actual sin añadir capacidades al runtime ni atribuir resultados nuevos.

La aplicación de la auditoría didáctica del 21-10-2026 añade tres ejercicios de aplicación con pista y solución razonada, un recorrido requisito → alternativa → decisión → evidencia y una evaluación semántica de respuestas preparadas, sin llamadas a modelos. Un cierre integrador permite justificar un cambio con una rúbrica breve. Extractos fieles al código, descripciones textuales de relaciones esenciales, inspección DLT acotada y lecturas orientadas completan el recorrido. Los ejercicios no alteran los contratos ni el dataset canónico; los laboratorios kind/Terraform siguen siendo opcionales. Código y documentación originales incluyen condiciones explícitas de reutilización, conservando las licencias de terceros.

El [mapa de componentes](repository-design.md#component-map) fija nombres funcionales y correspondencia con el código. Las explicaciones y diagramas distinguen aplicación ejecutable, módulo interno, biblioteca, servidor y protocolo; OIDC/JWT/JWKS no se representan como servicios propios. Cada vista tiene una página canónica del maestro `diagrams/architecture.drawio` y una exportación SVG.


La verificación integrada enlaza el pedido de esta ejecución con outbox, inbox y la actualización Kafka esperada; leer el primer mensaje histórico no acredita esa colaboración. La verificación canónica genera y valida JUnit Python tanto localmente como en CI y rechaza omisiones.

Fichas con una ruta de dos o tres archivos y una micropráctica ejecutable, resultado esperado, fallo y alternativa. Extensión de 500-900 palabras orientativas, orden lógico, ejemplo/alternativa/límite/comando/fallo/fuente. El README ofrece un temario jerárquico enlazado a las fichas; cada concepto introduce brevemente las distinciones necesarias para seguir el ejemplo. Enlaces oficiales apuntan a la página concreta. Libros y repositorios externos se incluyen solo cuando complementan el aprendizaje; registrar la edición del libro y, para repositorios seleccionados por popularidad, estrellas y fecha de consulta. Las estrellas no avalan calidad ni compatibilidad. C4 contexto/contenedores y vistas de identidad/señales; dependencias internas, secuencias pedido-outbox y devolución, estados workflow, flujos RAG/Streams, modelo físico SQL y despliegues local/cloud. La vista cloud describe configuración y planes mock, no un despliegue realizado. Mantener un único maestro draw.io; ampliar o corregir vistas existentes antes de añadir otra. Exportar SVG desde el maestro e inspeccionar cada vista modificada al tamaño de inserción. Fuente de verdad de código es el archivo ejecutable, no copia divergente en documento.

Patrones implementados: Adapter, Repository, Strategy y composición; Outbox/Inbox, Circuit Breaker e idempotencia con motivo concreto. Otros GoF solo teoría y referencias verificadas. ADR breves para límites, transacciones, entrega eventos, recuperación, permisos, estado y despliegue. No atribuir uso industrial ni métricas hipotéticas.

Versiones Python e imágenes fijadas en lockfiles, Dockerfiles y manifiestos. No cambiar major sin registrar decisión. Fuentes verificadas: [Spring Boot](https://docs.spring.io/spring-boot/4.1/reference/), [FastAPI](https://fastapi.tiangolo.com/advanced/events/), [LangGraph](https://docs.langchain.com/oss/python/langgraph/persistence), [Streams testing](https://kafka.apache.org/40/streams/developer-guide/testing/).

## Evolución acotada de organización y contratos

Se conservan los tres servicios, contratos existentes, datos sintéticos y versiones. No se incorpora contenido de repositorios privados. EVOL-01..07 amplía el alcance con estos casos mínimos:

- Java usa raíz `dev.reference` y nombres descriptivos. Comercio ubica controladores REST en su módulo funcional y adaptadores en subpaquetes por tecnología; `shared` conserva solo capacidades transversales. Dominio define errores por significado, estados enum y cantidad válida; infraestructura mapea a HTTP manteniendo status y JSON anteriores.
- `GET /api/v1/orders?limit=10&after=<UUID>&status=PLACED`: límite 1..50, cursor opcional, estados PLACED/REFUNDED, orden fijo id ASC. Devuelve `{items,nextCursor}`; consulta solo tenant/propietario autorizado (MANAGER: tenant), obtiene como máximo límite+1 y no calcula total. El cursor continúa por identificador; no es una instantánea ni promete orden temporal de creación ante escrituras concurrentes.
- Contrato OpenAPI canónico de Comercio bajo `contracts/http/`, contrastado con MockMvc y JSON Schema. Se mantiene JSON HTTP existente, incluidos enums serializados como sus nombres. Sin generador ni biblioteca nueva por defecto. Se desactiva la coerción numérica escalar de Jackson3 HTTP: quantity/version como string o literal decimal producen 400; antes podían convertirse silenciosamente. Las peticiones canónicas enteras y los status/JSON de salida se conservan. Los campos extra de entrada siguen tolerados. Los parámetros id antiguos conservan su conversión UUID legada; el nuevo cursor after exige representación completa. JSON Schema integer no distingue 2 de 2.0 por representación, por eso esa restricción wire se describe y prueba explícitamente.
- Gateway Python expresa crear/consultar/aprobar/ejecutar devolución con resultados tipados; infraestructura contiene verbos, rutas, JSON y traducción de errores. No guardar JWT ni reintentar escrituras arbitrariamente.
- Prueba mínima de proxy Spring acredita transacción/rollback y muestra fallo de auto-invocación o llamada directa fuera del proxy. Se extrae soporte de integración solo donde realmente se comparte.
- Streams conserva Kafka y proceso propio: límites explícitos de consumo, arranque/cierre probado, plazo de cierre y readiness comprobables. No se incorpora Pub/Sub, autoscaler ni plataforma de caos. Documentar lag frente a capacidad y cierre sin prometer HA ni ausencia universal de duplicados.

## Garantías selectivas de mantenimiento

Sin cambiar contratos ni dependencias: ArchUnit comprueba ausencia de ciclos entre los módulos completos orders, inventory y returns, excluyendo shared y composición; positivos y negativos usan la política canónica. La evaluación lexical conserva sus 42 casos y agregados, añade IDs y un informe JSON optativo dentro de reports/, con hashes de entradas, métricas por caso y alcance exclusivamente de recuperación offline. Las reglas Prometheus existentes se prueban con series sintéticas, incluidos umbrales, plazos y resolución; el gate reutiliza la imagen fijada en Compose, sin levantar servidores para esa comprobación.
