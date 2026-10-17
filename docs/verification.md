# Verificación y evaluación de la referencia

Estado local del **2026-10-17**, ARM64, Java 21 y Docker con 2 CPU/10 GiB. Una única evidencia vigente por capacidad; los informes regenerables quedan ignorados en `reports/` y `target/`. Las pruebas rápidas no sustituyen el gate completo. Las valoraciones son juicio técnico de agentes independientes, no auditoría humana ni certificación de producción.

## Resultado por capacidad

| AC | Evidencia y resultado | Estado |
|---|---|---|
| SPEC-01 | Contratos/errores/AC fijados antes de implementar; ADR006 registra cambio de baseline por seguridad | Verificado |
| ARCH-01, ARCH-02 | Dominio Java solo JDK/dominio propio; aplicación sin adaptadores; output privado de módulo. Reglas reales e import-linter | Verificado |
| ARCH-03 | Cinco negativos Java identifican origen/destino con ArchitectureRules.ALL; positivo de colaboración input y fuente productiva sin fixtures. Negativo Python real conservado | Verificado |
| REF-ARCH-01..04 | Diez pruebas ArchitectureTest: producción selecciona los tres módulos completos; dos ciclos deliberados fallan con clases/aristas reales mientras pasan las demás guardas, colaboración input/shared acíclica permitida. ALL compartida; sin cambios productivos Java/Maven | Verificado |
| REF-AI-01..05 | IDs únicos en los mismos 42 casos, comparación de preguntas/etiquetas sin cambios; informe optativo con hashes/detalle/null y baseline idéntico. Ranking degradado afecta detalle y agregado; rutas absolutas/traversal/symlinks externos rechazados. Informe ignorado y generado también ante gate fallido | Verificado, recuperación offline |
| REF-OBS-01..04 | promtool sobre ambas reglas: normal/umbral/plazo/resolución y etiquetas/anotaciones. Cuatro mutaciones en copias temporales (umbral y for de cada regla) rechazadas; originales intactos. Gate propaga fallo y usa imagen Compose sin red/mount readonly; CI descarga antes del gate | Verificado, series sintéticas |
| EVOL-01 | Controladores Order/Inventory/Return en módulo.rest, adaptadores por tecnología, raíz dev.reference y parámetros por intención; ArchUnit/compilación/regresión | Verificado |
| EVOL-02 | Quantity 1..100 sin controlador, enums y guardas de ReturnProposal; Kind sin códigos HTTP, ProblemAdvice mapea status | Verificado |
| EVOL-03 | CommerceGateway por intención + ReturnResult; MockTransport, validación id/estado/versión y cancelación/reinicio sin JWT checkpoint | Verificado, incluido smoke integrado |
| EVOL-04 | OpenAPI canónico contra rutas y respuestas reales, campos extra tolerados, números HTTP sin coerción y path legado descrito; negativos independientes | Verificado, con límite de representación integer documentado |
| EVOL-05 | PostgreSQL real: límite 1..50, sobrelectura de una fila, cursor UUID completo, orden UUID y aislamiento owner/tenant/filtro | Verificado |
| EVOL-06 | Proxy real revierte frente a target directo con efectos parciales y cleanup; único propietario de contenedores sin soporte duplicado | Verificado |
| EVOL-07 | Kafka real restaura estado tras cierre; seis lifecycle: HTTP/archivo, callbacks cruzados, timeout de inicio/cierre y rebalance; poll/buffer acotados por intención | Verificado, incluida parada de contenedor |
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
| STR-05 | Clock fijo: futuro de 2099/31 s y timestamp Kafka inválido no cierran ventanas; límite 30 s y clasificación única probados | Verificado |
| AI-01, AI-07 | Dataset 42 casos/32 respondibles, incluye multirrelevancia: recall@3=1, hit-rate@3=1, MRR@3=0,953125, aislamiento=abstención=1 | Verificado, corpus sintético |
| AI-02 | Filtro tenant previo, pregunta por contenido ajeno abstiene | Verificado |
| AI-03 | `fake-simulation` visible en respuesta; tests y smoke sin claves | Verificado |
| AI-04 | Sobre/tipos/referencias suministradas validados antes del éxito; 28 casos nuevos, HTTP simulado y errores 503 controlados | Verificado, sin modelo real |
| AI-05 | Fallo de contrato cuenta una vez sin retry; tres fallos abren, probe inválido reabre y válido recupera. Deadlines/cancelación/epoch conservados | Verificado |
| AI-06 | SSE inicio/resultado y prueba ASGI de desconexión que libera trabajo | Verificado |
| AI-08 | pgvector real: dimensiones, filtro tenant y orden con vectores fixture | Verificado |
| WF-01 | Cierre/reapertura SQLite permite continuar workflow | Verificado |
| WF-02 | Pérdida de respuesta, reintento, renovación explícita y ausencia de JWT en checkpoint | Verificado |
| WF-03 | Gateway bloqueado: plazo total incluye cola; admisión 16, rechazo 503, cancelación libera cupo y retry conserva clave | Verificado |
| SEC-01 | Tokens Keycloak reales: 401/403/404; ámbito estructurado evita recuperar resultados de otra identidad con delimitadores | Verificado |
| SEC-02 | RSA/JWKS: firma/issuer/audiencia/expiración y claims inválidos; rechazo sin exp | Verificado |
| SEC-03 | Corpus/preguntas adversariales; generador sin herramienta privilegiada y autorización backend | Verificado para amenazas declaradas |
| SEC-04 | Higiene pública, scan Trivy de fuente sin secretos y pruebas de logs sin cuerpos/JWT; no equivale a pentest | Verificado en alcance revisado |
| OBS-01 | Evidencia previa Boot4: HTTP/outbox/Kafka/consumidor en un traceId. Escenario actual: traceId nuevo contiene AI y Commerce | Verificado, alcances diferenciados |
| OBS-02 | Dashboard de doce paneles y reglas accionables; resultados REST/SSE se cuentan una sola vez, incluidos error/cancelación | Verificado |
| OBS-03 | Tres targets distintos con scrape posterior al inicio, incremento actual y traza exacta con ambos servicios; negativos rechazan histórico/incompleto | Verificado en Compose |
| OBS-04 | Logs JSON de ambas APIs con mismo traceId/correlationId, sin token/cuerpo; ruta plantilla y etiquetas acotadas | Verificado en Compose y tests |
| OPS-01 | Imágenes finales ARM64, Compose desde volúmenes vacíos y smoke completo con Keycloak | Verificado |
| OPS-04 | Demo correlaciona pedido actual con outbox/inbox/resumen exacto; Analytics detenido no pasa y recuperado sí | Verificado en Compose |
| OPS-02 | Down/up conserva pedido REFUNDED y workflow EXECUTED; reset solo proyecto explícito | Verificado |
| OPS-03 | Aplicaciones no-root, probes y heaps acotados; imágenes ARM64 | Verificado |
| K8S-01 | kind 0.33 / Kubernetes 1.36.4: clúster nuevo, probes/rollout y smoke actual correlacionado aprobados; retirado al terminar | Verificado |
| K8S-02 | Transición CPU real 1→2, dos pods/endpoints listos; 89.974 GET correctos y cero errores en 300 s (cap 300/s, 16 workers) | Verificado, laboratorio |
| K8S-03 | Dos POST simultáneos por port-forward a pods distintos, misma respuesta y decremento único; devolución restaura stock | Verificado en kind |
| TF-01 | fmt/validate y cuatro planes mock; rol GKE mínimo, imágenes por repositorio, rechazo IPv6/IPv4 inválida/red abierta; mutación Owner rechazada | Verificado, sin cloud |
| DOC-01 | Nueve fichas, ejemplos/alternativas/límites/fuentes y definiciones breves de conceptos no obvios; revisión editorial independiente de la ampliación | Verificado |
| DOC-02 | 35 enlaces con fragmento del README a 29 destinos contrastados en HTML de GitHub GFM y MkDocs; higiene y build estricto aprobados | Verificado, 2026-10-17 |
| DOC-03 | Dieciséis vistas; topología Kafka, concurrencia SQL y circuito IA añadidos al maestro. Exportación de 16 páginas coincide con sus SVG; 13 páginas anteriores preservadas | Verificado, 2026-10-17 |
| DOC-05 | Nueve microprácticas con archivos/comando/resultado/fallo/alternativa; runbook autenticado con broker detenido ejecutado literalmente | Verificado |
| DOC-04 | Mapa funcional/código, servicios de apoyo y distinción de niveles en explicaciones y dieciséis vistas | Verificado |
| DOC-06 | Fichas seleccionadas enlazan libros y código/README externo pertinente; páginas oficiales concretas y conteos de estrellas fechados. Revisión de contenido y enlaces en `make verify` | Verificado, 2026-10-17 |
| DOC-07 | Nueve fichas revisadas contra implementación y fuentes; tres SVG inspeccionados a 720 px. Revisión independiente y precisión TLS corregida con verificación de CA/hostname y fuente libpq | Verificado documentalmente, 2026-10-17 |
| QA-03 | JUnit obligatorio local/CI; siete pruebas de scripts y omisión deliberada rechazada aunque pytest termine 0 | Verificado |
| QA-01 | Revisor independiente detectó dos P2: cola de workflow y timestamp futuro; reparados y validados con negativos | Verificado en alcance revisado |
| QA-04 | Preparación CI migrada de acciones obsoletas a Node24, SHA e inputs contrastados con origen oficial; runtime de apps sin cambios | Revisado independientemente; resultado por commit en Actions |
| QA-02 | Dos revisores coinciden en 92/100 didáctico, cada dimensión ≥8/10; gate completo y escenarios reales aprobados | Verificado |
| LIVE | No se ha llamado a un modelo ni servicio de embeddings real | No ejecutado, optativo |
| CLOUD-DEPLOY | Sin apply ni recursos cloud creados | Fuera de alcance |

## Verificación vigente

La ejecución local vigente de `make verify eval` del 17-10-2026 incluye las mejoras REF-ARCH/AI/OBS. Los escenarios Compose, observabilidad, HPA y Terraform que siguen conservan su evidencia anterior por capacidad: no se repitieron localmente porque no cambian su configuración ni comportamiento. El último cierre CI anterior fue `02d963f`, [Actions 39069495895](https://github.com/victormartingil/software-architecture/actions/runs/39069495895); cada ejecución posterior se identifica por su commit en Actions.

- **`make verify eval`: aprobado.** 38 pruebas Commerce (23 integración PostgreSQL/Kafka, diez arquitectura, dos dominio, dos JWT y una matriz de consumidor), 19 Analytics (ocho topología, seis lifecycle, dos contratos, dos métricas y una recuperación Kafka), 134 AI con pgvector real: **191 pruebas de aplicación, cero errores/fallos/omisiones**. Ocho pruebas de scripts, dos contratos import-linter, fixtures promtool, higiene/enlaces y MkDocs estricto aprobados. La revisión independiente Java, Python/IA e infraestructura no encontró hallazgos materiales; fue estática, la ejecución corresponde al gate local.
- **Diagnóstico RAG:** `make eval EVAL_REPORT=reports/ai-evaluation.json` aprobado; `retrieval-006`, `retrieval-012` y `retrieval-017` tienen rango recíproco 0,5 y explican MRR=0,953125. Se comprobaron hashes, referencias, métricas no aplicables nulas y confinamiento del informe a `reports/`; preguntas y etiquetas permanecen idénticas. Sin modelo, red ni coste de API.
- **Reglas de alertas:** pruebas reales con Prometheus v3.2.1 fijado en Compose, contenedor sin red y montaje de solo lectura. Cambiar umbral o `for` de cualquiera de las dos reglas hizo fallar sus expectativas (cuatro experimentos); solo se alteraron copias temporales, retiradas al terminar. El script propaga código de error. No acredita notificaciones, scrapes ni recuperación del sistema.
- **Evaluación offline:** 42 casos/32 respondibles, recall@3=1, hit-rate@3=1, MRR@3=0,953125, aislamiento=abstención=1. Corpus pequeño sintético; no mide fidelidad, coste o generalización de un modelo real.
- **Compose (base `2c86d4f`, también aprobado en CI `02d963f`):** tres imágenes actuales; arranque temporal, smoke OIDC/stock/idempotencia/aislamiento/paginación/RAG fake/aprobación/reintento/SSE y pedido actual correlacionado con outbox/inbox/resumen Kafka. No se acepta un mensaje histórico. Sin claves de IA.
- **Observabilidad (base `2c86d4f`, también aprobada en CI `02d963f`):** `make observability-smoke` aprobado. Tres scrapes frescos, delta HTTP de 1, traza nueva AI→Commerce y dos logs JSON con igual traceId/correlationId, sin JWT ni motivo del cuerpo. El escenario consulta Tempo a través de Grafana; el dashboard de doce paneles se inspeccionó renderizado. Los negativos rechazan targets duplicados/viejos/caídos y traza parcial. Proyecto temporal y volúmenes retirados, sin tocar otros entornos.
- **HPA (base `2c86d4f`):** clúster nuevo y smoke aprobados. Escalado CPU real 1→2, dos pods y dos endpoints listos; 89.974 GET correctos/0 errores en 300 s, cap 300/s y dieciséis workers. Mismo POST simultáneo a ambos pods: idéntico pedido y decremento único; devolución autorizada restauró stock. No se parchearon réplicas. Se eliminó exclusivamente el clúster creado para la prueba. No es un benchmark ni acredita HA.
- **Terraform (base `2c86d4f`, también aprobado en CI `02d963f`):** `make terraform-check` aprobado: cuatro planes mock, sin apply ni llamadas cloud. No verifica IAM efectivo, cuotas ni conectividad real.

[Pruebas Commerce](../apps/commerce-api/src/test/java/dev/reference/commerce/CommerceIntegrationTest.java) · [topología y reloj](../apps/order-analytics/src/test/java/dev/reference/analytics/OrderSummaryTopologyTest.java) · [workflows](../apps/ai-service/tests/test_workflows.py) · [escenario de señales](../scenarios/observability.py) · [escalado](../scripts/kind-scale-check.py).

La parada SIGTERM de Analytics se verificó en la entrega de organización:0,605 s local, exit 143, readiness retirada y cierre completo, sin OOM/SIGKILL; margen del orquestador de 30 s frente a cierre solicitado de 20 s. Se conservan esos límites, sin convertir una observación en SLA. El ciclo de recuperación Kafka se repite en el gate actual.

## Decisiones y reparaciones que deben conservarse

| Riesgo comprobado | Resolución y límite vigente |
|---|---|
| Ámbitos idempotentes ambiguos | Scope tipado, array JSON V2 y migración que rechaza historial V1 ambiguo sin borrar datos; [ADR002](decisions/002-consistency.md). Requiere parada de escritores, sin prometer rolling upgrade. |
| Deadline excluía cola o se confundía con cancelación | Respuestas IA incluyen cola en 12 s; epoch protege breaker y se cuenta cada resultado una sola vez. Cupos por adaptador/proceso, sin facturación distribuida. |
| Parsing IA declaraba éxito antes de validar | Sobre, tipos y citas se comprueban antes de cerrar breaker. HTTP 200 inválido cuenta fallo sin retry. Cita existente no demuestra fidelidad semántica. |
| Identidades/guardas inconsistentes | Corpus UUID compartido; abreviados rechazados antes del efecto. ArchitectureRules.ALL aplica a producción y negativos con origen/destino real; fixtures fuera de producción. |
| Fallos distribuidos ocultos por tests felices | Broker/DLT fallidos, offset conservado, duplicados por particiones, pérdida de RocksDB y recuperación real; lector integrado verifica el pedido actual. Deduplicación limitada a siete días. |
| Contrato HTTP y tolerancia divergían | OpenAPI contrastado con rutas/respuestas; Jackson3 HTTP rechaza string/decimal en quantity/version; Jackson2 conserva codec propio. JSON Schema considera 2.0 entero matemático: diferencia wire descrita y probada. |
| Organización confusa | Controladores por módulo, adaptadores por responsabilidad, raíz dev.reference, errores semánticos y gateway tipado. No se extrae una base Testcontainers sin segundo consumidor real del setup. |
| Workflow acumulaba espera sin límite | Admisión de 16 por proceso y plazo de 12 s incluido lock/grafo; dos regresiones bloquean gateway, saturan/cancelan cola y reintentan con identidad estable. SQLite mantiene capacidad serializada y una réplica. |
| Timestamp de 2099 hacía descartar pedidos actuales | Política de adelanto máximo de 30 s con Clock antes de extraer tiempo; inválido conserva tiempo previo y va a DLT. Clasificación única evita doble ruta; solo DLT recibe hora de cuarentena para conservar su retención. Requiere reloj sincronizado. |
| F5: observabilidad/HPA solo manuales | Escenarios acotados y aserciones de operación actual; telemetría incorporada al workflow CI. No se añade Loki, Datadog ni otra plataforma. HPA y efectos cruzados comprobados en el laboratorio actual. |

Los AC de organización y contratos precedieron su implementación. La ampliación operativa y dos reparaciones P2 se registran en especificación/AC. Especialistas distintos de autores revisaron código por piezas, módulos y conjunto. El revisor independiente reprodujo la cola global y el envenenamiento temporal; validó reparación y pruebas, incluidos límites de reloj y trazas parciales. Máximo dos rondas de reparación por entrega; no se rebajan AC para elevar la puntuación.

## Documentación y diagramas

El README organiza nueve áreas y 22 subtemas. Sus enlaces a las fichas usan identificadores explícitos para evitar diferencias de anclas con tildes entre GitHub y MkDocs. Los 55 enlaces entre secciones se contrastaron en ambos HTML. Las nueve fichas mantienen código, micropráctica, alternativa, límite y fuentes, con ejemplos ilustrativos de cursor, stock, tiempo de evento y ranking. Los ejemplos nuevos no son mediciones de rendimiento ni una evaluación de modelo real.

Un [maestro draw.io](diagrams/architecture.drawio) contiene dieciséis vistas. Las tres nuevas explican lectores Kafka, competencia por stock y estados del circuito del proveedor; se exportaron e inspeccionaron a 720 px con un renderizador SVG local. Las trece páginas anteriores se conservaron sin cambios y la exportación de las dieciséis coincide byte a byte con los SVG versionados. La medición geométrica en navegador de la entrega previa se limita a sus trece vistas; no se atribuye esa medición a las nuevas.

Una revisión independiente de las nueve fichas y las tres vistas contrastó la explicación con código. Detectó que el párrafo TLS podía atribuir validación automática de identidad: se precisaron CA, hostname y `verify-full`, conservando explícito que el laboratorio no configura TLS para PostgreSQL. Higiene, enlaces locales, anclas de ambos renderizados y MkDocs estricto aprobados; runtime, contratos y dependencias no cambian. Las pruebas de aplicación y escenarios conservan la evidencia por capacidad indicada arriba y en Actions. Las mejoras selectivas añaden únicamente las microcomprobaciones de ciclos, diagnóstico RAG y reglas de alerta a sus fichas existentes; no requieren cambiar diagramas. El plan provisional queda integrado en especificación, AC, ADR001 y esta evidencia, sin otra lista de estado paralela.

## Seguridad, higiene y automatización

Scan de fuente previo a la ampliación documental: Trivy 0.70.0 sobre 202 archivos, cero secretos detectados; snapshot temporal eliminado. Higiene revisa rutas personales, emails no sintéticos, material de clave y enlaces privados; `.local`, informes, caches, binarios y estado Terraform no se versionan. Contraseñas incluidas son exclusivamente sintéticas de laboratorio. Revisión adicional de 329 blobs de texto del historial: cero rutas de usuario de máquina o marcadores de clave privada; no equivale a un detector exhaustivo de información personal. Estos controles reducen riesgo, sin garantizar ausencia absoluta de datos sensibles.

Scans de dependencias anteriores del mismo baseline/lockfiles (2026-10-17): pip-audit 2.9.0 sin vulnerabilidades conocidas en runtime/docs; Trivy fuente/manifiestos y 135 paquetes de JAR/runtime sin HIGH/CRITICAL. Las mejoras selectivas no cambian dependencias ni versiones de aplicaciones; las acciones de preparación CI conservan Node24 y commits fijados, con motivo en ADR006. [ADR006](decisions/006-maintenance.md) conserva avisos reparados y compatibilidad Boot 4/Spring 7/Jackson. No se atribuye scan de SO de las imágenes ni pentest actual.

[GitHub Actions 39066921172](https://github.com/victormartingil/software-architecture/actions/runs/39066921172) sobre 2c86d4f terminó sus tres jobs: pruebas/eval, Terraform mock, build/demo, señales y scans Python/Java, con las acciones de preparación Node24. La ampliación documental conserva runtime y tareas; cada ejecución remota queda vinculada a su commit en Actions, sin atribuir éxito a uno distinto. Un pipeline verde no demuestra escenarios ausentes.

## Valoración y uso productivo

Valoraciones independientes tras las reparaciones y el cierre integrado: **92/100 como referencia didáctica** en ambos revisores; **88 y 91/100 como referencia arquitectónica para producción**. Sustituyen las notas previas; son juicios con incertidumbre aproximada de ±3 puntos, no mediciones estadísticas.

| Rúbrica didáctica | Puntos |
|---|---:|
| Corrección |23/25|
| Arquitectura |19/20|
| Seguridad/IA |14/15|
| Didáctica |14/15|
| Pruebas |9/10|
| Operación |9/10|
| DevEx |4/5|

Rúbrica arquitectónica del primer revisor (88): cohesión/cambio 19/20, corrección 18/20, aislamiento/seguridad 18/20, resiliencia 16/20 y operabilidad 17/20. El segundo (91) puntúa 19/19/18/17/18 sobre veinte por dimensión. El desacuerdo es de alcance: el primero pesa más la ausencia de capacidad/HA/restauración demostradas; el segundo evalúa la transferibilidad de decisiones, pruebas y guía de adaptación, sin penalizar automáticamente una restricción local explícita. Se conservan ambos juicios, sin promediarlos ni presentarlos como garantía operativa. El umbral QA-02 (90 y cada dimensión ≥8/10) aplica a la referencia didáctica.

Es una referencia transferible de límites, transacciones, entrega, autorización y señales; no un despliegue empresarial certificado. Las dependencias locales no son HA, SQLite serializa y requiere una réplica, no hay SLO/capacidad representativa ni restauración de backups demostrados, y no se evalúa modelo real. [Delivery](handbook/delivery.md#topic-reutilizar-la-referencia-en-un-sistema-real) identifica qué adaptar y probar según requisitos del producto. Añadir plataformas para subir una nota contrariaría la proporcionalidad del ejemplo. No se contrataron servicios, publicaron recursos ni ejecutó IA de pago.
