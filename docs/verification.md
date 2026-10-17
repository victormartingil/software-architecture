# Verificación y evaluación de la referencia

Ejecución local del **2026-10-17**, ARM64, Docker con 10 GiB y Java 21. Evidencia resumida y reproducible; los informes de herramientas se generan en `reports/` y `target/`, excluidos de publicación. GitHub Actions también completó la ejecución inicial; no se atribuye un despliegue cloud. La revisión y reparación por especialistas que figura al final sustituye las valoraciones anteriores y distingue evidencia actual de laboratorios no repetidos.

## Resultado por capacidad

| AC | Evidencia y resultado | Estado |
|---|---|---|
| SPEC-01 | Contratos/errores/AC fijados antes de implementar; ADR006 registra cambio de baseline por seguridad | Verificado |
| ARCH-01, ARCH-02 | Dominio Java solo JDK/dominio propio; aplicación sin adaptadores; output privado de módulo. Reglas reales e import-linter | Verificado |
| ARCH-03 | Cinco negativos Java identifican origen/destino con ArchitectureRules.ALL; positivo de colaboración input y fuente productiva sin fixtures. Negativo Python real conservado | Verificado |
| ORD-01 | `priceAndIdempotency`: precio servidor y reserva persistida | Verificado |
| ORD-02 | `concurrentLastUnit`: dos transacciones reales por la última unidad | Verificado |
| ORD-03 | Scope tipado y array JSON; identidades con delimitadores aisladas, incluso concurrentes; replay estable y conflicto por cuerpo distinto | Verificado |
| ORD-04 | `rollbackIncludesStockAndOrder`: fallo revierte stock/pedido/outbox | Verificado |
| RET-01 | Propuesta sobre pedido visible y constraint único por pedido; smoke comprueba segunda propuesta409 | Verificado |
| RET-02 | Manager/tenant/versión comprobados en integración y API403/404 | Verificado |
| RET-03 | Reloj controlado: aprobación vencida y versión incorrecta rechazadas | Verificado |
| RET-04 | `concurrentReturnExecutionRestoresOnce`, reintento estable y stock final idéntico | Verificado |
| EVT-01 | Broker pausado, commit conserva pendiente y recuperación publica | Verificado |
| EVT-02 | Duplicado UUID normalizado; espera incremento del contador de duplicados y un efecto inbox | Verificado |
| EVT-03 | Confirmación broker precede marcado; revisión del publicador y prueba integración | Verificado |
| EVT-04 | Evento realmente producido validado con JSON Schema, además de fixtures | Verificado |
| EVT-05 | Doce casos UUID compartidos; rechazo antes de SQL, DLT Streams sin resumen y abreviado en Kafka real sin inbox. Fallo DLT/recuperación aprobados, identidad estable | Verificado |
| STR-01 | TopologyTestDriver comprueba ventana, tenant/SKU, cantidad e importe | Verificado |
| STR-02 | Duplicados, formas UUID y llegada desordenada dentro de grace | Verificado |
| STR-03 | Avance explícito de stream-time descarta tardíos e incrementa contador | Verificado |
| STR-04 | Kafka real: duplicados explícitos por particiones 0/1 antes y después de eliminar RocksDB/restaurar changelog; sin inflar el resumen | Verificado |
| AI-01, AI-07 | Dataset 42 casos/32 respondibles, incluye multirrelevancia: recall@3=1, hit-rate@3=1, MRR@3=0,953125, aislamiento=abstención=1 | Verificado, corpus sintético |
| AI-02 | Filtro tenant previo, pregunta por contenido ajeno abstiene | Verificado |
| AI-03 | `fake-simulation` visible en respuesta; tests y smoke sin claves | Verificado |
| AI-04 | Sobre/tipos/referencias suministradas validados antes del éxito; 28 casos nuevos, HTTP simulado y errores 503 controlados | Verificado, sin modelo real |
| AI-05 | Fallo de contrato cuenta una vez sin retry; tres fallos abren, probe inválido reabre y válido recupera. Deadlines/cancelación/epoch conservados | Verificado |
| AI-06 | SSE inicio/resultado y prueba ASGI de desconexión que libera trabajo | Verificado |
| AI-08 | pgvector real: dimensiones, filtro tenant y orden con vectores fixture | Verificado |
| WF-01 | Cierre/reapertura SQLite permite continuar workflow | Verificado |
| WF-02 | Pérdida de respuesta, reintento, renovación explícita y ausencia de JWT en checkpoint | Verificado |
| SEC-01 | Tokens Keycloak reales: 401/403/404; ámbito estructurado evita recuperar resultados de otra identidad con delimitadores | Verificado |
| SEC-02 | RSA/JWKS: firma/issuer/audiencia/expiración y claims inválidos; rechazo sin exp | Verificado |
| SEC-03 | Corpus/preguntas adversariales; generador sin herramienta privilegiada y autorización backend | Verificado para amenazas declaradas |
| SEC-04 | Higiene de fuente, Trivy secreto0, revisión logs/instrumentación sin cuerpos/tokens | Verificado en alcance revisado |
| OBS-01 | Tempo sobre Boot 4 final contiene HTTP, outbox.publish, Kafka send y process en el mismo traceId | Verificado |
| OBS-02 | Scrape/dashboard/alerta comprobados previamente; tests REST/SSE actuales acreditan un resultado por operación, incluidos error y cancelación | Verificado en ambos alcances |
| OPS-01 | Imágenes finales ARM64, Compose desde volúmenes vacíos y smoke completo con Keycloak | Verificado |
| OPS-04 | Demo correlaciona pedido actual con outbox/inbox/resumen exacto; Analytics detenido no pasa y recuperado sí | Verificado en Compose |
| OPS-02 | Down/up conserva pedido REFUNDED y workflow EXECUTED; reset solo proyecto explícito | Verificado |
| OPS-03 | Aplicaciones no-root, probes y heaps acotados; imágenes ARM64 | Verificado |
| K8S-01 | kind 0.33 / Kubernetes 1.36.4: ejecución limpia, probes/rollout y smoke de imágenes finales aprobados | Verificado |
| K8S-02 | HPA1→2 réplicas listas;43.592 GET correctos/0 fallos en 150 s, cap de 300 peticiones/s; smoke con dos réplicas | Verificado, laboratorio |
| TF-01 | fmt/validate y cuatro planes mock; rol GKE mínimo, imágenes por repositorio, rechazo IPv6/IPv4 inválida/red abierta; mutación Owner rechazada | Verificado, sin cloud |
| DOC-01 | Ocho fichas, ejemplos/alternativas/límites/fuentes y revisión editorial independiente | Verificado |
| DOC-02 | Trece SVG inspeccionados a 1000/688 px, medición geométrica en navegador y MkDocs/enlaces aprobados; etiquetas C4 corregidas (F4) | Verificado |
| DOC-03 | Trece vistas: estructura, identidad/señales, comportamiento, SQL y despliegues local/cloud; seis ADR conectados a código | Verificado |
| DOC-05 | Ocho microprácticas con archivos/comando/resultado/fallo/alternativa; runbook autenticado con broker detenido ejecutado literalmente | Verificado |
| DOC-04 | Mapa funcional/código, servicios de apoyo y distinción de niveles en explicaciones y trece vistas | Verificado |
| QA-03 | JUnit Python obligatorio local/CI; tres pruebas de scripts y experimento pytest real omitido rechazado aunque pytest termina 0 | Verificado |
| QA-01 | Validadores independientes Python/IA y Java/eventos/arquitectura; F1–F4 cerrados, sin altos/medios abiertos en alcance | Verificado |
| QA-02 | Síntesis independiente 92/100, cada dimensión >=8/10; make verify y eval aprobados | Verificado |
| LIVE | No se ha llamado a un modelo ni servicio de embeddings real | No ejecutado, optativo |
| CLOUD-DEPLOY | Sin apply ni recursos cloud creados | Fuera de alcance |

## Pruebas y herramientas

- **Commerce: 30 pruebas** (20 de integración PostgreSQL/Kafka, 7 arquitectura, 2 JWT/JWKS y 1 consumidor con doce casos compartidos), sin omisiones. Incluye ámbitos ambiguos, precondición de migración y DLT fallida.
- **Analytics: 11 pruebas** (6 de topología, 2 contratos, 2 métricas/probes, 1 de recuperación Kafka), sin omisiones tras Jackson/LZ4 actualizados.
- **AI: 74 pruebas**, incluida integración pgvector real mediante `scripts/test.sh`, sin omisiones. Deadlines/cancelación, métricas, multirrelevancia y seis casos del adaptador de embeddings sin red. Dos contratos arquitectónicos aprobados. El dataset de 42 casos se evalúa por separado.
- `make test` usa contenedores aislados para Java/pgvector y exige JUnit sin skips para **Java y Python**, local y CI con el mismo comando. **115 pruebas de aplicación** más **tres pruebas de scripts**. La espera PostgreSQL usa TCP para no aceptar el servidor temporal antes de terminar init; el mismo criterio está en readiness Compose/kind. `make verify` añade higiene/contratos/documentación.
- `make eval` reproduce recuperación offline; no mide un LLM real.
- `make terraform-check`: cuatro tests mock, ninguna conexión cloud. CLI fijada sin actualizar instalación host.
- `actionlint 1.7.7` valida el workflow. La ejecución inicial de GitHub Actions (39036928123) terminó con éxito en sus tres jobs: verify, python-security y java-security. Incluyó pruebas/evaluación, Terraform mock, build de imágenes, Compose desde vacío y smoke autenticado. Esta ejecución corresponde al commit inicial; no se atribuye CI de los cambios posteriores, verificados localmente. Un pipeline verde no demuestra cobertura de escenarios ausentes.

Tests consultables: [CommerceIntegrationTest](../apps/commerce-api/src/test/java/dev/reference/commerce/CommerceIntegrationTest.java), [recuperación Analytics](../apps/order-analytics/src/test/java/com/reference/analytics/KafkaRecoveryTest.java), [workflows AI](../apps/ai-service/tests/test_workflows.py).

La nomenclatura funcional se corresponde con el código en el [mapa canónico](repository-design.md#component-map). `ManageOrderService` y `ManageReturnService` nombran los ciclos cohesivos de pedido y devolución; `OrderSummaryTopology`, el resumen de pedidos; `CommerceController`, el adaptador HTTP; `JwtVerifier`, la verificación local del token. Python distingue `DocumentRetriever`, `AnswerProvider` y `ReturnApprovalWorkflow`. Se conservan rutas, eventos, configuración e identificadores de despliegue. La ejecución actual confirma 30 Commerce, 11 Streams y 74 Python, incluidas PostgreSQL/Kafka/pgvector, sin omisiones; dos contratos import-linter aprobados. `scripts/test.sh` limpia el build Java para no sumar informes JUnit obsoletos después de renombrar clases.

El [maestro draw.io](diagrams/architecture.drawio) contiene trece vistas, enumeradas en el [índice visual](reference-map.md#vistas-visuales). Se conservaron las diez vistas existentes y se añadieron despliegue Compose/kind, fundación GCP y modelo físico de las siete tablas de Comercio. Cada SVG se inserta en su explicación; el README abre con contexto. No hay fuentes Mermaid duplicadas ni generador de autoría como dependencia del repositorio.

Revisión documental del 17 de octubre de 2026: los trece SVG finales se midieron en navegador mediante `getBBox`, transformaciones y muestreo de rutas cada tres unidades o menos, vinculando la medición al SHA-256 de cada exportación. **447 líneas de texto y 80 relaciones etiquetadas**, cero incidencias detectadas por el comprobador geométrico. Se abrieron los renders completos a 1000 px y al ancho de inserción de 688 px. El C4 nivel 2 y las secuencias conservan enlaces para leer sus detalles a tamaño completo. En secuencias, las líneas verticales son participantes/tiempo y los mensajes se anclan a puntos dentro de sus grupos; el marco opt representa una condición, no otro servicio.

F4 queda corregido: más separación entre Kafka y Comercio y etiquetas nativas de relación ampliadas. Se reexportó e inspeccionó el resultado; el control geométrico anterior no había detectado la ocultación observada por el revisor. La inspección visual complementa las mediciones; ninguna prueba acredita comprensión por lectores ni ausencia universal de defectos entre renderers. Se precisaron también advisory lock/ámbito V2, abstención sin generación tras recuperación y el bloque condicional de aprobación/ejecución. Un especialista independiente contrastó los seis diagramas nuevos o modificados semánticamente con código, migraciones y configuración, sin errores materiales.

El XML vigente se cargó en el editor draw.io en modo dispositivo mediante «Edit Diagram»: el editor reconoció sus trece páginas, textos y formas nativas. En la copia de prueba, desplazar el grupo Compose conservó sus textos, componentes y conector HTTP; la copia se descartó sin guardar sobre el maestro. El selector de archivo no respondió a la automatización; la carga XML permitió comprobar la edición sin publicar ni guardar en almacenamiento externo. No se promete igualdad visual entre renderers o temas del editor. Los renders y mediciones regenerables permanecen ignorados. Higiene/enlaces, diff sin errores de whitespace y build MkDocs estricto aprobados para esta entrega; en la entrega visual no se repitieron pruebas de aplicación al no cambiar runtime. No se ha publicado ni hecho push por esta revisión visual.

## Seguridad de dependencias

Revisión previa al commit inicial (2026-10-17): 169 archivos públicos, sin binarios ni enlaces simbólicos; contexto privado, credenciales de entorno, informes y compilados excluidos del índice. Higiene de fuente y enlaces, `git diff --cached --check` y MkDocs estricto aprobados. Trivy 0.70.0 sobre una copia temporal del índice: cero secretos detectados; la copia se eliminó. Las contraseñas incluidas son exclusivamente las del laboratorio sintético documentado. El maestro draw.io y sus SVG tienen funciones distintas; los wrappers Maven repetidos permiten ejecutar cada servicio independientemente. Esta revisión reduce el riesgo de publicación, sin garantizar ausencia absoluta de datos sensibles.

pip-audit 2.9.0 sobre ambos lockfiles: sin vulnerabilidades conocidas. Trivy 0.70.0 sobre cuatro manifiestos y fuente pública: 0 HIGH/CRITICAL y 0 secretos detectados. Segundo scan sobre JARs desplegables y dependencias runtime de Analytics: 135 paquetes detectados y 0 HIGH/CRITICAL. Copiar transitivas sin sombrear evita inferir cobertura del único paquete detectado en un fat JAR.

Se repararon avisos sin exclusiones: base Boot 4.1.1 / Spring 7.0.9, Tomcat 11.0.27, Jackson 2.21.7/3.1.7, driver 42.7.12, LZ4 1.11.4, Material 9.7.7 y pymdown 11.0.1. [ADR006](decisions/006-maintenance.md) conserva motivo y fuentes. Los resultados dependen de la base de advisories de la fecha; no son certificación ni escaneo del SO de imágenes.

## Revisiones y límites

Revisiones por agentes independientes con foco Java/eventos, Python/IA/seguridad e infraestructura/documentación. Hallazgos materiales reparados: JWT sin exp, JWKS de cifrado confundida con firma, propiedad del probe half-open, renovación explícita de aprobación, identidad UUID, permisos del store, liveness/readiness y red/configuración Kafka en kind. No se atribuye una auditoría humana externa.

La base local no es HA; AI/SQLite requiere un proceso y una réplica. El cap live es por adaptador/proceso y presupuesto USD declarado, sin facturación medida. pgvector usa fixtures en tests; la calidad de embeddings/modelo real requiere otra evaluación. El dataset pequeño no prueba generalización ni resistencia universal a prompt injection.

## Reparación y validación por especialistas

Se mantuvo el orden piezas pequeñas → módulos → conjunto. Un agente implementó Java/eventos, otro Python/IA y el coordinador infraestructura/escenarios; un validador distinto de los autores revisó los bloques y el manual. Hubo revisión cruzada adicional del escenario integrado. Es revisión por agentes, no auditoría humana externa. Máximo dos rondas formales de reparación, sin rebajar AC ni añadir servicios.

La revisión previa bajó el juicio inicial de 90 a 82/100 por defectos que las pruebas existentes no cubrían. La implementación posterior cierra los ocho bloques siguientes; conservar esos motivos evita reintroducir garantías falsas sin mantener un diario de intentos.

| Bloque cerrado | Cambio y evidencia significativa | Límite conservado |
|---|---|---|
| R1 · Idempotencia | [Scope](../apps/commerce-api/src/main/java/dev/reference/commerce/shared/application/Idempotency.java) tipado de cinco componentes, array JSON y versión 2 persistida; tests de tenant/subject/operación/recurso/clave con delimitadores, concurrencia, visibilidad y replay | [V2](../apps/commerce-api/src/main/resources/db/migration/V2__structured_idempotency_scope.sql) bloquea y rechaza historial V1 ambiguo sin borrar datos; escritores antiguos no pueden insertar. No es actualización gradual; [ADR002](decisions/002-consistency.md) |
| R2 · Plazos y breaker | [AnswerService](../apps/ai-service/src/ai_service/application/answers.py) incluye cola en 12 s; [proveedor](../apps/ai-service/src/ai_service/infrastructure/providers.py) distingue deadline de desconexión y protege periodos con epoch. Reintroducir anidamiento antiguo en copia temporal hace fallar el test de cola | Límites por proceso, no presupuesto distribuido ni facturación medida |
| R3 · Métricas IA | [main.py](../apps/ai-service/src/ai_service/main.py) registra answered/abstained/error/cancelled una sola vez; tests de deltas REST/SSE, incluido rechazo, timeout y desconexión | Se cuentan operaciones de respuesta iniciadas tras validar entrada/identidad; no todos los intentos HTTP |
| R4 · Infraestructura | [IAM](../infra/cloud/main.tf): rol mínimo GKE en proyecto y lector de imágenes por repositorio. Cuatro planes; mutación a Owner manteniendo las keys de recursos rechazada por aserción semántica | Terraform 1.10 deja email calculado desconocido durante plan: cableado a identidad revisado en fuente. Sin apply/IAM efectivo |
| R5 · Fallos de eventos | [DLT](../apps/commerce-api/src/test/java/dev/reference/commerce/CommerceIntegrationTest.java): fallo inyectado solo al enviar a DLT, consumidor/broker reales, offset sin avanzar y recuperación. [Streams](../apps/order-analytics/src/test/java/com/reference/analytics/KafkaRecoveryTest.java): duplicados explícitos en dos particiones antes/después de pérdida de RocksDB | La inyección simula fallo de publicación, no una prueba de ACL del broker; dedup limitada a siete días |
| R6 · Gate equivalente | [test.sh](../scripts/test.sh) produce JUnit Python obligatorio y [checker](../scripts/check_test_evidence.py) rechaza omisiones/errores/vacío. Pytest real con un skip termina 0 y el gate lo rechaza | Pruebas rápidas seleccionadas son deliberadamente parciales; no sustituyen make verify |
| R7 · Composición | [event_flow.py](../scenarios/event_flow.py) compara el pedido actual, publicación, inbox y agregado exacto de su tenant/SKU/ventana UTC. Smoke positivo; Analytics detenido falla y recuperado pasa | Operador local, sin escritores concurrentes; lector acotado a 10.000 registros, no consulta pública de analytics |
| R8 · Medición y manual | [evaluate.py](../apps/ai-service/src/ai_service/evaluate.py): recall multirrelevancia, hit-rate y MRR separados. Ocho fichas de 637–900 palabras con micropráctica; [runbook](handbook/operations.md) ejecutado literalmente con Kafka detenido y recuperación | Fake no mide fidelidad LLM; MRR@3=0,953125 deja visible que top-3 perfecto no garantiza el mejor primer documento |

Ejecución anterior al cierre F1–F3: `make verify eval`, 80 pruebas de aplicación sin omisiones, tres pruebas de scripts, dos contratos import-linter, higiene y build estricto aprobados. `make terraform-check`: cuatro planes mock aprobados. Los transportes y plazos pequeños de tests IA no consumen APIs reales. El guard de migración se prueba con PostgreSQL/Flyway reales, incluidos rollback y preservación de registros.

La demo usa imágenes ARM64 construidas de las fuentes actualizadas y un proyecto Compose de pruebas separado, sin reutilizar volúmenes V1. El último arranque desde volúmenes vacíos con las probes TCP actuales y su smoke correlacionado terminó correctamente. Se verificaron el flujo autorizado completo, la comprobación negativa de Analytics y el runbook de broker caído. No se ha ejecutado reset sobre entornos anteriores. Los entornos temporales propios se retiran al terminar.

Los contratos de aplicación se mantienen; la revisión visual posterior precisa diagramas y añade vistas de despliegue/datos, como se detalla arriba. Kind/HPA y señales completas de Tempo/Prometheus conservan su evidencia anterior; no se atribuye repetición actual de esos laboratorios. La ruta `make kind-smoke` fija contexto/kubeconfig del clúster y el backend de inspección, pero su ejecución actual aún no está acreditada. Terraform no ha creado infraestructura. El modelo real sigue optativo.

Higiene de la entrega R1–R8: fuente pública y enlaces comprobados, diff sin errores de whitespace y Trivy 0.70.0 sobre 174 archivos públicos: cero secretos detectados. Sin nuevas dependencias ni cambios de versiones; los scans de dependencias mantienen el alcance y fecha anteriores. Se retiraron las copias temporales de experimentos; informes regenerables ignorados. No se ha añadido un segundo maestro de diagramas ni documentación privada a Git.

Fuentes primarias contrastadas: [asyncio timeout/cancelación](https://docs.python.org/3.12/library/asyncio-task.html#timeouts), [Spring Kafka recuperación](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html), [GKE cuentas de nodos](https://docs.cloud.google.com/kubernetes-engine/docs/how-to/service-accounts), [Terraform cidrnetmask](https://developer.hashicorp.com/terraform/language/functions/cidrnetmask), [pytest exit codes](https://docs.pytest.org/en/stable/reference/exit-codes.html), [OIDC sub](https://openid.net/specs/openid-connect-core-1_0.html#IDToken). No son avales de la implementación.

## Revisión independiente y cierre de F1–F4

La revisión ciega de `3c24b52` por tres agentes sin historial redujo la valoración de 91 a 89/100: encontró parsing IA no protegido, UUID divergentes entre consumidores, guardas Java insuficientes y etiquetas C4 tapadas. Las reparaciones posteriores conservan esos motivos sin mantener listas de hallazgos ya obsoletas. F4 se cerró en la revisión visual descrita arriba; F1–F3 se corrigen por prioridad, sin añadir dependencias ni modificar versiones, topología o contratos de negocio.

| Hallazgo y reproducción | Corrección y evidencia | Estado y límite |
|---|---|---|
| F1 · JSON `[]`, `output:[null]` o `content:[null]` provocaba AttributeError sin contar fallo; tipos/citas se validaban después del éxito | [Proveedor](../apps/ai-service/src/ai_service/infrastructure/providers.py) valida sobre, tipos y resultado completo antes de resetear breaker. [28 regresiones nuevas](../apps/ai-service/tests/test_providers.py) cubren error controlado, tres fallos, apertura, probe inválido y recuperación válida, sin retry del contenido inválido | Cerrado. Referencias existentes no prueban fidelidad semántica; autorización tenant sigue en recuperación/aplicación |
| F2 · `1-1-1-1-1` se normalizaba y auditaba, mientras Streams lo rechazaba | [AuditConsumer](../apps/commerce-api/src/main/java/dev/reference/commerce/shared/infrastructure/AuditConsumer.java) exige UUID completo en tres campos antes de SQL. [Corpus común de doce casos](../contracts/events/fixtures/uuid-cases.json), [negativos sin SQL](../apps/commerce-api/src/test/java/dev/reference/commerce/shared/infrastructure/AuditConsumerTest.java), [DLT sin resumen](../apps/order-analytics/src/test/java/com/reference/analytics/OrderSummaryTopologyTest.java) y abreviado enviado a Kafka real sin inbox | Cerrado. Mayúsculas/minúsculas conservan identidad; no cambia retención de siete días |
| F3 · Jackson en dominio y repositorio ajeno pasaban el guard; el negativo usaba otra regla | [ArchitectureRules.ALL](../apps/commerce-api/src/test/java/dev/reference/commerce/ArchitectureRules.java) es la política única para producción y [negativos](../apps/commerce-api/src/test/java/dev/reference/commerce/ArchitectureTest.java). Cinco infracciones identifican origen/destino (Jackson 2/3, Spring, adaptador propio y repositorio ajeno), más positivo input cruzado. Fixtures excluidas de producción | Cerrado. Se permite selección vacía solo al probar fixtures aisladas; no se acepta como evidencia de infracción. No prueba corrección de negocio |
| F4 · Comercio tapaba dos etiquetas Kafka | Separación/etiquetas ajustadas en maestro; trece SVG inspeccionados y medidos | Cerrado en entrega visual anterior; diagramas sin cambios en esta reparación |

Un especialista implementó F1 y otro lo validó sin editar: 66 pruebas enfocadas, nueve contratos adversos adicionales y cancelación de half-open comprobados de forma independiente. Un revisor distinto del autor contrastó F2/F3 y su evidencia JUnit. Un tercer especialista validó proporción, claridad y rúbrica del conjunto, sin brechas materiales nuevas. Son revisiones por agentes, no auditoría humana ni certificación externa. Sin hallazgos técnicos altos/medios abiertos en el alcance reparado; se conserva el máximo de dos rondas de reparación por entrega.

Cierre canónico: **`make verify` y `make eval` aprobados**, con **115 pruebas de aplicación** (30 Commerce, 11 Analytics, 74 AI), **tres pruebas de scripts**, **dos contratos import-linter**, higiene pública/enlaces y build MkDocs estricto. JUnit acredita cero errores, fallos u omisiones. El corpus UUID es un objeto `cases`, consumido por ambos lectores y compatible con el gate de contratos sin excepciones. Evaluación offline: 42 casos, recall@3=1, hit-rate@3=1, MRR@3=0,953125, aislamiento=abstención=1; no mide un modelo real.

Fuentes primarias contrastadas para estas correcciones: [Responses/salidas estructuradas](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=responses), [UUID Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/UUID.html), [ArchUnit: importación, reglas y selecciones vacías](https://www.archunit.org/userguide/html/000_Index.html). Se mantienen las versiones compatibles existentes.

### Pendientes opcionales y límites conservados

**F5, evidencia de operación:** automatizar la observación HPA 1→2 y añadir un escenario optativo que consulte targets, métricas y traza de la operación actual. `kind-load.py` valida GET/errores; no acredita por sí solo escalado ni efecto concurrente entre pods. CI no activa la pila de observabilidad. La evidencia manual anterior permanece válida en su ámbito; no se repitieron Compose/kind/HPA/telemetría/Terraform en este cierre. Sin apply ni servicios IA de pago.

Otros negativos opcionales: propuestas concurrentes con claves distintas, pérdida de respuesta al crear propuesta seguida de reinicio y rotación JWKS. No son defectos demostrados ni motivo para añadir plataformas. Scans de dependencias/imágenes conservan su fecha y alcance anteriores; esta entrega no introduce dependencias. Higiene pública/enlaces y documentación se verifican con el gate actual; no se confunden con un nuevo pentest ni scan completo de imágenes.

## Valoración vigente

**92/100**, confirmado tras el gate completo y revisión independiente del conjunto, conforme a la rúbrica existente. El cierre de validación externa, identidad uniforme y reglas efectivas justifica subir desde 89; F4 ya estaba reparado. Es juicio interno de ingeniería sobre una referencia didáctica local, sin precisión estadística ni certificación productiva.

| Dimensión | Puntos | Evidencia y límite |
|---|---:|---|
| Corrección | 24/25 | Contratos adversos y UUID corregidos; invariantes/recuperación reales, dominio pequeño |
| Arquitectura | 19/20 | Política única, negativos identificables y colaboración permitida; no toda propiedad se expresa en ArchUnit |
| Seguridad/IA | 14/15 | Validación previa al breaker, permisos, plazos y cancelación; sin modelo real ni pentest |
| Didáctica | 14/15 | Ejemplos pequeños, tres fichas precisadas, ADR y trece vistas; sin estudio con lectores |
| Pruebas | 9/10 | 115 pruebas más tres de scripts y negativos reales; escenarios distribuidos no exhaustivos |
| Operación | 8/10 | Evidencia histórica de laboratorio conservada; F5 requiere observación manual, cloud sin desplegar |
| DevEx | 4/5 | Gates equivalentes y comandos guiados; entorno completo exige recursos y descargas |

La suma alcanza el umbral de QA-02 y cada dimensión al menos 8/10 con evidencia ejecutada. No se amplía el repositorio por elevar la nota: mantener estos casos sencillos y garantías explícitas.
