# Verificación y evaluación de la referencia

Ejecución local del **2026-10-17**, ARM64, Docker con 10 GiB y Java 21. Evidencia resumida y reproducible; los informes de herramientas se generan en `reports/` y `target/`, excluidos de publicación. No se atribuye una ejecución de GitHub Actions ni un despliegue cloud.

## Resultado por capacidad

| AC | Evidencia y resultado | Estado |
|---|---|---|
| SPEC-01 | Contratos/errores/AC fijados antes de implementar; ADR006 registra cambio de baseline por seguridad | Verificado |
| ARCH-01, ARCH-02 | ArchUnit y dos contratos import-linter; aplicación no conoce adaptadores, dominio sin frameworks | Verificado |
| ARCH-03 | Infracción Java comprobada; copia Python temporal con import prohibido falla y se elimina | Verificado |
| ORD-01 | `priceAndIdempotency`: precio servidor y reserva persistida | Verificado |
| ORD-02 | `concurrentLastUnit`: dos transacciones reales por la última unidad | Verificado |
| ORD-03 | Repetición, cuerpo distinto y peticiones concurrentes con misma clave | Verificado |
| ORD-04 | `rollbackIncludesStockAndOrder`: fallo revierte stock/pedido/outbox | Verificado |
| RET-01 | Propuesta sobre pedido visible y constraint único por pedido; smoke comprueba segunda propuesta409 | Verificado |
| RET-02 | Manager/tenant/versión comprobados en integración y API403/404 | Verificado |
| RET-03 | Reloj controlado: aprobación vencida y versión incorrecta rechazadas | Verificado |
| RET-04 | `concurrentReturnExecutionRestoresOnce`, reintento estable y stock final idéntico | Verificado |
| EVT-01 | Broker pausado, commit conserva pendiente y recuperación publica | Verificado |
| EVT-02 | Duplicado UUID normalizado; espera incremento del contador de duplicados y un efecto inbox | Verificado |
| EVT-03 | Confirmación broker precede marcado; revisión del publicador y prueba integración | Verificado |
| EVT-04 | Evento realmente producido validado con JSON Schema, además de fixtures | Verificado |
| EVT-05 | Kafka real entrega inválido a DLT; replay real conserva eventId, contador duplicados 1 y un único efecto inbox | Verificado |
| STR-01 | TopologyTestDriver comprueba ventana, tenant/SKU, cantidad e importe | Verificado |
| STR-02 | Duplicados, formas UUID y llegada desordenada dentro de grace | Verificado |
| STR-03 | Avance explícito de stream-time descarta tardíos e incrementa contador | Verificado |
| STR-04 | Kafka real: reinicio, eliminación de RocksDB y restauración changelog/dedup | Verificado |
| AI-01, AI-07 | Dataset de 41 casos, 31 respondibles, recall@3=1.0, aislamiento=1.0, abstención=1.0 | Verificado, corpus sintético |
| AI-02 | Filtro tenant previo, pregunta por contenido ajeno abstiene | Verificado |
| AI-03 | `fake-simulation` visible en respuesta; tests y smoke sin claves | Verificado |
| AI-04 | Mock HTTP Responses: estructura, rechazo/incompleto, errores y timeout | Verificado, sin modelo real |
| AI-05 | Concurrencia, dos intentos, breaker y propiedad de half-open bajo carrera | Verificado |
| AI-06 | SSE inicio/resultado y prueba ASGI de desconexión que libera trabajo | Verificado |
| AI-08 | pgvector real: dimensiones, filtro tenant y orden con vectores fixture | Verificado |
| WF-01 | Cierre/reapertura SQLite permite continuar workflow | Verificado |
| WF-02 | Pérdida de respuesta, reintento, renovación explícita y ausencia de JWT en checkpoint | Verificado |
| SEC-01 | Tokens Keycloak reales:401 sin token,403 sin rol suficiente,404 tenant ajeno | Verificado |
| SEC-02 | RSA/JWKS: firma/issuer/audiencia/expiración y claims inválidos; rechazo sin exp | Verificado |
| SEC-03 | Corpus/preguntas adversariales; generador sin herramienta privilegiada y autorización backend | Verificado para amenazas declaradas |
| SEC-04 | Higiene de fuente, Trivy secreto0, revisión logs/instrumentación sin cuerpos/tokens | Verificado en alcance revisado |
| OBS-01 | Tempo sobre Boot 4 final contiene HTTP, outbox.publish, Kafka send y process en el mismo traceId | Verificado |
| OBS-02 | Prometheus scrape 3/3; dashboard de ocho paneles inspeccionado y alerta con runbook cargada | Verificado |
| OPS-01 | Imágenes finales ARM64, Compose desde volúmenes vacíos y smoke completo con Keycloak | Verificado |
| OPS-02 | Down/up conserva pedido REFUNDED y workflow EXECUTED; reset solo proyecto explícito | Verificado |
| OPS-03 | Aplicaciones no-root, probes y heaps acotados; imágenes ARM64 | Verificado |
| K8S-01 | kind 0.33 / Kubernetes 1.36.4: ejecución limpia, probes/rollout y smoke de imágenes finales aprobados | Verificado |
| K8S-02 | HPA1→2 réplicas listas;43.592 GET correctos/0 fallos en 150 s, cap de 300 peticiones/s; smoke con dos réplicas | Verificado, laboratorio |
| TF-01 | Terraform 1.10.5: fmt, init sin backend, validate y dos planes mock aprobados | Verificado, sin cloud |
| DOC-01 | Ocho fichas, ejemplos/alternativas/límites/fuentes y revisión editorial independiente | Verificado |
| DOC-02 | MkDocs estricto; enlaces internos comprobados y diagramas renderizados en navegador | Verificado |
| DOC-03 | C4 contexto/contenedores, secuencias, estado y seis ADR conectados a código | Verificado |
| DOC-04 | Mapa funcional/código, servicios de apoyo y distinción de niveles en explicaciones y diez vistas | Verificado |
| QA-01, QA-02 | Revisiones sin hallazgos altos abiertos en alcance;90/100 y todas las dimensiones >=8/10 | Verificado |
| LIVE | No se ha llamado a un modelo ni servicio de embeddings real | No ejecutado, optativo |
| CLOUD-DEPLOY | Sin apply ni recursos cloud creados | Fuera de alcance |

## Pruebas y herramientas

- **Commerce: 18 pruebas** (14 de integración PostgreSQL/Kafka, 2 ArchUnit, 2 JWT/JWKS), sin omisiones. Suite repetida tras migración a Boot 4 y parches.
- **Analytics: 10 pruebas** (5 de topología, 2 contratos, 2 métricas/probes, 1 de recuperación Kafka), sin omisiones tras Jackson/LZ4 actualizados.
- **AI: 23 pruebas**, incluida integración pgvector real mediante `scripts/test.sh`, sin omisiones. Dos contratos arquitectónicos aprobados. El dataset de 41 casos se evalúa por separado.
- `make test` usa contenedores aislados para Java/pgvector y comprueba que integraciones Java no se hayan omitido. `make verify` añade higiene/contratos/documentación; CI exige también JUnit Python sin skips.
- `make eval` reproduce recuperación offline; no mide un LLM real.
- `make terraform-check`: dos tests mock, ninguna conexión cloud. CLI fijada sin actualizar instalación host.
- `actionlint 1.7.7` valida el workflow. CI está definida con tres gates independientes y aún no ejecutada en GitHub.

Tests consultables: [CommerceIntegrationTest](../apps/commerce-api/src/test/java/dev/reference/commerce/CommerceIntegrationTest.java), [recuperación Analytics](../apps/order-analytics/src/test/java/com/reference/analytics/KafkaRecoveryTest.java), [workflows AI](../apps/ai-service/tests/test_workflows.py).

La nomenclatura funcional se corresponde con el código en el [mapa canónico](repository-design.md#component-map). `ManageOrderService` y `ManageReturnService` nombran los ciclos cohesivos de pedido y devolución; `OrderSummaryTopology`, el resumen de pedidos; `CommerceController`, el adaptador HTTP; `JwtVerifier`, la verificación local del token. Python distingue `DocumentRetriever`, `AnswerProvider` y `ReturnApprovalWorkflow`. Se conservan rutas, eventos, configuración e identificadores de despliegue. Las 51 pruebas se repitieron tras los renombrados: 18 Java/Comercio, 10 Streams y 23 Python, incluidas PostgreSQL/Kafka/pgvector, sin omisiones; dos contratos import-linter aprobados. `scripts/test.sh` limpia el build Java para no sumar informes JUnit obsoletos después de renombrar clases.

El [maestro draw.io](diagrams/architecture.drawio) contiene diez vistas: contexto, aplicaciones/datos, identidad, señales, puertos/adaptadores, secuencia de pedido, procesamiento de resúmenes, respuestas con evidencia, secuencia de devolución y estados. Las siete fuentes Mermaid anteriores se retiraron al insertar sus SVG. Las 11 relaciones principales y tres de identidad se conservan; señales agrupa explícitamente las aplicaciones. No se ha añadido otro servicio por cada módulo o biblioteca.

Revisión documental del 17 de octubre de 2026: las diez exportaciones se midieron en navegador mediante `getBBox` y muestreo de rutas cada tres unidades o menos. **318 líneas de texto y 71 relaciones etiquetadas**, sin incidencias detectadas de texto/texto, texto fuera del lienzo o de su componente, etiqueta/componente, ruta/componente ajeno, ruta/etiqueta ajena, cruces o tramos compartidos. En secuencias, las líneas verticales representan participantes y el tiempo; los mensajes se anclan a puntos absolutos dentro de sus grupos, no a cajas que representen otros procesos. Se inspeccionaron a 1000 px y al ancho de inserción de 688 px; los detalles también se abren como SVG a tamaño completo. La inspección completa distingue llamadas, retornos y tareas internas. Estos controles no miden comprensión con lectores ni certifican ausencia de cualquier defecto visual.

El maestro se abrió en draw.io en modo dispositivo con sus diez páginas y objetos nativos. En la copia de prueba, mover el grupo Comercio desplazó forma, textos, extremos y etiquetas de sus relaciones; la copia se descartó sin guardar. Los resultados locales de medición permanecen ignorados; la documentación conserva las decisiones y la evidencia consolidada. Higiene de enlaces, ausencia de fuentes duplicadas y build MkDocs estricto aprobados. No se ha publicado ni creado un remoto Git.

## Seguridad de dependencias

Revisión previa al commit inicial (2026-10-17): 169 archivos públicos, sin binarios ni enlaces simbólicos; contexto privado, credenciales de entorno, informes y compilados excluidos del índice. Higiene de fuente y enlaces, `git diff --cached --check` y MkDocs estricto aprobados. Trivy 0.70.0 sobre una copia temporal del índice: cero secretos detectados; la copia se eliminó. Las contraseñas incluidas son exclusivamente las del laboratorio sintético documentado. El maestro draw.io y sus SVG tienen funciones distintas; los wrappers Maven repetidos permiten ejecutar cada servicio independientemente. Esta revisión reduce el riesgo de publicación, sin garantizar ausencia absoluta de datos sensibles.

pip-audit 2.9.0 sobre ambos lockfiles: sin vulnerabilidades conocidas. Trivy 0.70.0 sobre cuatro manifiestos y fuente pública: 0 HIGH/CRITICAL y 0 secretos detectados. Segundo scan sobre JARs desplegables y dependencias runtime de Analytics: 135 paquetes detectados y 0 HIGH/CRITICAL. Copiar transitivas sin sombrear evita inferir cobertura del único paquete detectado en un fat JAR.

Se repararon avisos sin exclusiones: base Boot 4.1.1 / Spring 7.0.9, Tomcat 11.0.27, Jackson 2.21.7/3.1.7, driver 42.7.12, LZ4 1.11.4, Material 9.7.7 y pymdown 11.0.1. [ADR006](decisions/006-maintenance.md) conserva motivo y fuentes. Los resultados dependen de la base de advisories de la fecha; no son certificación ni escaneo del SO de imágenes.

## Revisiones y límites

Revisiones por agentes independientes con foco Java/eventos, Python/IA/seguridad e infraestructura/documentación. Hallazgos materiales reparados: JWT sin exp, JWKS de cifrado confundida con firma, propiedad del probe half-open, renovación explícita de aprobación, identidad UUID, permisos del store, liveness/readiness y red/configuración Kafka en kind. No se atribuye una auditoría humana externa.

La base local no es HA; AI/SQLite requiere un proceso y una réplica. El cap live es por adaptador/proceso y presupuesto USD declarado, sin facturación medida. pgvector usa fixtures en tests; la calidad de embeddings/modelo real requiere otra evaluación. El dataset pequeño no prueba generalización ni resistencia universal a prompt injection.

## Valoración final

**90/100**, juicio interno de ingeniería conforme a la rúbrica del proyecto, sin certificación externa. Todas las dimensiones alcanzan 8/10. La puntuación describe una referencia local reproducible, no preparación productiva universal.

| Dimensión | Puntos | Evidencia y límite |
|---|---|---|
| Corrección | 24/25 | Invariantes, carreras, fallos, replay, permisos y persistencia reales; dominio pequeño |
| Arquitectura | 17/20 | Puertos comprobados y propiedad de datos; acoplamiento Spring y puertos de capacidad deliberadamente pragmáticos |
| Seguridad/IA | 13/15 | OIDC real, aislamiento, aprobación y límites; sin pentest ni modelo real evaluado |
| Didáctica | 14/15 | Fichas navegables, C4/secuencias/estado, ADR y referencias a código; sin estudio formal con lectores |
| Pruebas | 9/10 | 51 pruebas sin omisiones más dos planes mock; dataset de 41 casos separado |
| Operación | 9/10 | Docker, señales y HPA reales; dependencias locales no HA y cloud no desplegado |
| DevEx | 4/5 | Comandos, wrappers, locks y cleanup; primera descarga y laboratorio requieren recursos/prerrequisitos |

El generador inicial sin cap produjo errores bajo carga excesiva; se acotó el ritmo, se reutilizaron conexiones y se repitió la medición con clasificación de errores. El resultado de 43.592 peticiones correctas y cero fallos acredita el escenario declarado, sin establecer capacidad garantizada ni percentiles de rendimiento. No se atribuye una causa única a los fallos del estrés inicial.

Se cierra la iteración con los AC obligatorios verificados en su alcance y sin hallazgos altos abiertos de las revisiones/scans ejecutados. Mantener los límites visibles y revalidar las capacidades afectadas al cambiar dependencias o configuración.
