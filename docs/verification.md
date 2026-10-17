# Verificación y evaluación de la referencia

Ejecución local del **2026-10-17**, ARM64, Docker con 10 GiB y Java 21. Evidencia resumida y reproducible; los informes de herramientas se generan en `reports/` y `target/`, excluidos de publicación. GitHub Actions también completó la ejecución inicial; no se atribuye un despliegue cloud. La revisión y reparación por especialistas que figura al final sustituye las valoraciones anteriores y distingue evidencia actual de laboratorios no repetidos.

## Resultado por capacidad

| AC | Evidencia y resultado | Estado |
|---|---|---|
| SPEC-01 | Contratos/errores/AC fijados antes de implementar; ADR006 registra cambio de baseline por seguridad | Verificado |
| ARCH-01, ARCH-02 | ArchUnit y dos contratos import-linter; aplicación no conoce adaptadores, dominio sin frameworks | Verificado |
| ARCH-03 | Infracción Java comprobada; copia Python temporal con import prohibido falla y se elimina | Verificado |
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
| EVT-05 | Fallo específico de envío DLT: offset original no avanza ni pasa el mensaje posterior; recuperación produce DLT real y efecto único. Replay conserva identidad | Verificado |
| STR-01 | TopologyTestDriver comprueba ventana, tenant/SKU, cantidad e importe | Verificado |
| STR-02 | Duplicados, formas UUID y llegada desordenada dentro de grace | Verificado |
| STR-03 | Avance explícito de stream-time descarta tardíos e incrementa contador | Verificado |
| STR-04 | Kafka real: duplicados explícitos por particiones 0/1 antes y después de eliminar RocksDB/restaurar changelog; sin inflar el resumen | Verificado |
| AI-01, AI-07 | Dataset 42 casos/32 respondibles, incluye multirrelevancia: recall@3=1, hit-rate@3=1, MRR@3=0,953125, aislamiento=abstención=1 | Verificado, corpus sintético |
| AI-02 | Filtro tenant previo, pregunta por contenido ajeno abstiene | Verificado |
| AI-03 | `fake-simulation` visible en respuesta; tests y smoke sin claves | Verificado |
| AI-04 | Mock HTTP Responses: estructura, rechazo/incompleto, errores y timeout | Verificado, sin modelo real |
| AI-05 | Total 12 s incluye cola; generación 11 s y HTTP 10 s/fase, dos intentos. Deadlines contabilizados, cancelación distinguida, éxito antiguo no cierra otro periodo | Verificado |
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
| DOC-02 | MkDocs estricto, enlaces y precisión de plazos/comandos aprobados; diagramas sin cambios, evidencia visual previa conservada | Verificado |
| DOC-03 | C4 contexto/contenedores, secuencias, estado y seis ADR conectados a código | Verificado |
| DOC-05 | Ocho microprácticas con archivos/comando/resultado/fallo/alternativa; runbook autenticado con broker detenido ejecutado literalmente | Verificado |
| DOC-04 | Mapa funcional/código, servicios de apoyo y distinción de niveles en explicaciones y diez vistas | Verificado |
| QA-03 | JUnit Python obligatorio local/CI; tres pruebas de scripts y experimento pytest real omitido rechazado aunque pytest termina 0 | Verificado |
| QA-01, QA-02 | R1–R8 reparados y validados por especialistas; sin hallazgos altos/medios abiertos en alcance; 91/100 y cada dimensión >=8/10 | Verificado |
| LIVE | No se ha llamado a un modelo ni servicio de embeddings real | No ejecutado, optativo |
| CLOUD-DEPLOY | Sin apply ni recursos cloud creados | Fuera de alcance |

## Pruebas y herramientas

- **Commerce: 24 pruebas** (20 de integración PostgreSQL/Kafka, 2 ArchUnit, 2 JWT/JWKS), sin omisiones. Incluye ámbitos ambiguos, precondición de migración y DLT fallida.
- **Analytics: 10 pruebas** (5 de topología, 2 contratos, 2 métricas/probes, 1 de recuperación Kafka), sin omisiones tras Jackson/LZ4 actualizados.
- **AI: 46 pruebas**, incluida integración pgvector real mediante `scripts/test.sh`, sin omisiones. Deadlines/cancelación, métricas, multirrelevancia y seis casos del adaptador de embeddings sin red. Dos contratos arquitectónicos aprobados. El dataset de 42 casos se evalúa por separado.
- `make test` usa contenedores aislados para Java/pgvector y exige JUnit sin skips para **Java y Python**, local y CI con el mismo comando. **80 pruebas de aplicación** más **tres pruebas de scripts**. La espera PostgreSQL usa TCP para no aceptar el servidor temporal antes de terminar init; el mismo criterio está en readiness Compose/kind. `make verify` añade higiene/contratos/documentación.
- `make eval` reproduce recuperación offline; no mide un LLM real.
- `make terraform-check`: cuatro tests mock, ninguna conexión cloud. CLI fijada sin actualizar instalación host.
- `actionlint 1.7.7` valida el workflow. La ejecución inicial de GitHub Actions (39036928123) terminó con éxito en sus tres jobs: verify, python-security y java-security. Incluyó pruebas/evaluación, Terraform mock, build de imágenes, Compose desde vacío y smoke autenticado. Esta ejecución corresponde al commit inicial; no se atribuye CI de los cambios posteriores, verificados localmente. Un pipeline verde no demuestra cobertura de escenarios ausentes.

Tests consultables: [CommerceIntegrationTest](../apps/commerce-api/src/test/java/dev/reference/commerce/CommerceIntegrationTest.java), [recuperación Analytics](../apps/order-analytics/src/test/java/com/reference/analytics/KafkaRecoveryTest.java), [workflows AI](../apps/ai-service/tests/test_workflows.py).

La nomenclatura funcional se corresponde con el código en el [mapa canónico](repository-design.md#component-map). `ManageOrderService` y `ManageReturnService` nombran los ciclos cohesivos de pedido y devolución; `OrderSummaryTopology`, el resumen de pedidos; `CommerceController`, el adaptador HTTP; `JwtVerifier`, la verificación local del token. Python distingue `DocumentRetriever`, `AnswerProvider` y `ReturnApprovalWorkflow`. Se conservan rutas, eventos, configuración e identificadores de despliegue. La ejecución actual confirma 24 Commerce, 10 Streams y 46 Python, incluidas PostgreSQL/Kafka/pgvector, sin omisiones; dos contratos import-linter aprobados. `scripts/test.sh` limpia el build Java para no sumar informes JUnit obsoletos después de renombrar clases.

El [maestro draw.io](diagrams/architecture.drawio) contiene diez vistas: contexto, aplicaciones/datos, identidad, señales, puertos/adaptadores, secuencia de pedido, procesamiento de resúmenes, respuestas con evidencia, secuencia de devolución y estados. Las siete fuentes Mermaid anteriores se retiraron al insertar sus SVG. Las 11 relaciones principales y tres de identidad se conservan; señales agrupa explícitamente las aplicaciones. No se ha añadido otro servicio por cada módulo o biblioteca.

Revisión documental del 17 de octubre de 2026: las diez exportaciones se midieron en navegador mediante `getBBox` y muestreo de rutas cada tres unidades o menos. **318 líneas de texto y 71 relaciones etiquetadas**, sin incidencias detectadas de texto/texto, texto fuera del lienzo o de su componente, etiqueta/componente, ruta/componente ajeno, ruta/etiqueta ajena, cruces o tramos compartidos. En secuencias, las líneas verticales representan participantes y el tiempo; los mensajes se anclan a puntos absolutos dentro de sus grupos, no a cajas que representen otros procesos. Se inspeccionaron a 1000 px y al ancho de inserción de 688 px; los detalles también se abren como SVG a tamaño completo. La inspección completa distingue llamadas, retornos y tareas internas. Estos controles no miden comprensión con lectores ni certifican ausencia de cualquier defecto visual.

El maestro se abrió en draw.io en modo dispositivo con sus diez páginas y objetos nativos. En la copia de prueba, mover el grupo Comercio desplazó forma, textos, extremos y etiquetas de sus relaciones; la copia se descartó sin guardar. Los resultados locales de medición permanecen ignorados; la documentación conserva las decisiones y la evidencia consolidada. Higiene de enlaces, ausencia de fuentes duplicadas y build MkDocs estricto aprobados. Posteriormente se creó el remoto privado autorizado y se subió el commit inicial. El contenido privado local continúa excluido de Git.

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

Ejecución canónica final: `make verify eval`, 80 pruebas de aplicación sin omisiones, tres pruebas de scripts, dos contratos import-linter, higiene y build estricto aprobados. `make terraform-check`: cuatro planes mock aprobados. Los transportes y plazos pequeños de tests IA no consumen APIs reales. El guard de migración se prueba con PostgreSQL/Flyway reales, incluidos rollback y preservación de registros.

La demo usa imágenes ARM64 construidas de las fuentes actualizadas y un proyecto Compose de pruebas separado, sin reutilizar volúmenes V1. El último arranque desde volúmenes vacíos con las probes TCP actuales y su smoke correlacionado terminó correctamente. Se verificaron el flujo autorizado completo, la comprobación negativa de Analytics y el runbook de broker caído. No se ha ejecutado reset sobre entornos anteriores. Los entornos temporales propios se retiran al terminar.

Los diagramas mantienen los mismos componentes, contratos y secuencias; no se reexportan sin cambio semántico de vista. Kind/HPA y señales completas de Tempo/Prometheus conservan su evidencia anterior; no se atribuye repetición actual de esos laboratorios. La ruta `make kind-smoke` fija contexto/kubeconfig del clúster y el backend de inspección, pero su ejecución actual aún no está acreditada. Terraform no ha creado infraestructura. El modelo real sigue optativo.

Higiene final: fuente pública y enlaces comprobados, diff sin errores de whitespace y Trivy 0.70.0 sobre 174 archivos públicos: cero secretos detectados. Sin nuevas dependencias ni cambios de versiones; los scans de dependencias mantienen el alcance y fecha anteriores. Se retiraron las copias temporales de experimentos; informes regenerables ignorados. No se ha añadido un segundo maestro de diagramas ni documentación privada a Git.

Fuentes primarias contrastadas: [asyncio timeout/cancelación](https://docs.python.org/3.12/library/asyncio-task.html#timeouts), [Spring Kafka recuperación](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html), [GKE cuentas de nodos](https://docs.cloud.google.com/kubernetes-engine/docs/how-to/service-accounts), [Terraform cidrnetmask](https://developer.hashicorp.com/terraform/language/functions/cidrnetmask), [pytest exit codes](https://docs.pytest.org/en/stable/reference/exit-codes.html), [OIDC sub](https://openid.net/specs/openid-connect-core-1_0.html#IDToken). No son avales de la implementación.

## Valoración vigente

**91/100**, propuesta del validador independiente y síntesis conforme a la rúbrica existente tras cerrar R1–R8. Todas las dimensiones alcanzan 8/10. Es juicio interno de ingeniería sobre una referencia didáctica local, sin certificación externa ni afirmación de preparación productiva universal.

| Dimensión | Puntos | Evidencia y límite |
|---|---:|---|
| Corrección | 24/25 | Ámbitos, carreras, rollback, migración, deadline y recuperación comprobados; dominio pequeño |
| Arquitectura | 18/20 | Separación proporcionada y responsabilidades precisas; guards y gateway aún pueden refinarse |
| Seguridad/IA | 13/15 | Aislamiento, aprobación, cancelación y cupos; sin pentest ni fidelidad de un modelo real |
| Didáctica | 14/15 | Ocho microprácticas, rutas, diagramas y ADR; sin estudio formal con lectores |
| Pruebas | 9/10 | 80 pruebas más tres de scripts, cuatro planes y negativos; escenarios distribuidos no exhaustivos |
| Operación | 9/10 | Compose y recuperación actuales; kind/HPA/señales con evidencia anterior, cloud sin desplegar |
| DevEx | 4/5 | Gate local/CI equivalente y comandos guiados; requiere recursos y descargas iniciales |

No quedan hallazgos altos o medios abiertos en el alcance reparado. Como evolución P3, valorar refresco JWKS acotado ante kid nuevo, guards más precisos y gateway de comercio por intención si simplifica el coordinador; no son requisitos ocultos para esta entrega. Los mocks de embeddings ya están incluidos. No añadir más servicios, proveedores o agentes autónomos por amplitud de temario: mantener ejemplos pequeños y garantías comprobables.
