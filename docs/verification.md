# Verificación y evaluación de la referencia

Fecha de corte: **23-10-2026**. Entorno local ARM64, Java 21, Python 3.12 y Docker con 2 CPU/10 GiB. Las ejecuciones actuales se distinguen de las evidencias heredadas, fechadas explícitamente; no equivalen a una certificación de producción. Informes regenerables y revisiones simuladas se conservan ignorados en `reports/` y `target/`; la historia anterior se consulta en Git.

**Estado de entrega: mejoras de auditoría en [PR #2](https://github.com/victormartingil/software-architecture/pull/2), fusionada en `main`.** Verificación local funcional y documental aprobada; [CI de `main` completada para `07b22a5`](https://github.com/victormartingil/software-architecture/actions/runs/39918968036), con verificación funcional, laboratorio Java 25 y seguridad Java/Python aprobados; Grafana 12.4.13 conserva ocho coincidencias HIGH en el análisis de imagen. El gate falla y no hay excepciones ni supresiones. Se aplaza la corrección de esta herramienta local opcional; no se introduce una compilación propia de Grafana. [ADR006](decisions/006-maintenance.md) registra versiones, contraste de alternativa y límites. No interpretar esos ocho registros como ocho vulnerabilidades distintas o explotación demostrada.

## Resultado por capacidad

| AC | Evidencia y resultado | Estado |
|---|---|---|
| SPEC-01 | Contratos/errores/AC fijados antes de implementar; ADR006 registra cambio de baseline por seguridad | Verificado |
| ARCH-01, ARCH-02 | Dominio Java solo JDK/dominio propio; aplicación sin adaptadores; output privado de módulo. Reglas reales e import-linter | Verificado |
| ARCH-03 | Negativos Java identifican origen/destino con ArchitectureRules.ALL; positivo de colaboración input y fuente productiva sin fixtures. Negativos Python de dominio y aplicación identifican los módulos reales y el alcance transitivo; Quantity, comando y pedido verifican INVALID_INPUT | Verificado |
| REF-ARCH-01..04 | Doce pruebas ArchitectureTest: producción selecciona los tres módulos completos; dos ciclos deliberados fallan con clases/aristas reales mientras pasan las demás guardas, colaboración input/shared acíclica permitida. ALL compartida; sin cambios productivos de módulos ni Maven | Verificado |
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
| SEC-01 | Tokens Keycloak reales: 401/403/404; regresiones de desafío WWW-Authenticate: Bearer en 401 de ambas APIs, sin desafío en 403; ámbito estructurado evita recuperar resultados de otra identidad con delimitadores | Verificado |
| SEC-02 | RSA/JWKS: firma/issuer/audiencia/expiración y claims inválidos; rechazo sin exp | Verificado |
| SEC-03 | Corpus/preguntas adversariales; generador sin herramienta privilegiada y autorización backend | Verificado para amenazas declaradas |
| SEC-04 | Higiene pública, scan Trivy de fuente sin secretos y pruebas de logs sin cuerpos/JWT; no equivale a pentest | Verificado en alcance revisado |
| OBS-01 | Evidencia previa Boot4: HTTP/outbox/Kafka/consumidor en un traceId. Escenario actual: traceId nuevo contiene AI y Commerce | Verificado, alcances diferenciados |
| OBS-02 | Dashboard de doce paneles y reglas accionables; resultados REST/SSE se cuentan una sola vez, incluidos error/cancelación; lag ausente se omite, cero y máximo observados se conservan | Verificado |
| OBS-03 | Tres targets distintos con scrape posterior al inicio, incremento actual y traza exacta con ambos servicios; negativos rechazan histórico/incompleto | Verificado en Compose |
| OBS-04 | Logs JSON de ambas APIs con mismo traceId/correlationId, sin token/cuerpo; ruta plantilla y etiquetas acotadas; diagnóstico de causa HTTP/SSE y salida Uvicorn sanitizados; spans sin URL/query concreta ni texto de excepción, con secreto sintético y servidor real | Verificado en Compose y tests |
| OPS-01 | Imágenes finales ARM64, Compose desde volúmenes vacíos y smoke completo con Keycloak | Verificado |
| OPS-04 | Demo correlaciona pedido actual con outbox/inbox/resumen exacto; Analytics detenido no pasa y recuperado sí | Verificado en Compose |
| OPS-02 | Down/up conserva pedido REFUNDED y workflow EXECUTED; reset solo proyecto explícito | Verificado |
| OPS-03 | Aplicaciones no-root, probes y heaps acotados; imágenes ARM64 | Verificado |
| K8S-01 | kind 0.33 / Kubernetes 1.36.4: clúster nuevo, probes/rollout y smoke actual correlacionado aprobados; retirado al terminar | Verificado, 23-10-2026 |
| K8S-02 | Transición CPU real 1→2, dos pods/endpoints listos; 89.955 GET correctos y cero errores en 300 s (cap 300/s, 16 workers) | Verificado, laboratorio 23-10-2026 |
| K8S-03 | Dos POST simultáneos por port-forward a pods distintos, misma respuesta y decremento único; devolución restaura stock | Verificado en kind, 23-10-2026 |
| TF-01 | fmt/validate y cuatro planes mock; rol GKE mínimo, imágenes por repositorio, rechazo IPv6/IPv4 inválida/red abierta; mutación Owner rechazada | Verificado, sin cloud |
| DOC-01 | Nueve capítulos con objetivos, conceptos introducidos, ejemplos, alternativas, prácticas y fuentes; preparación inicial separada. Revisión editorial independiente del recorrido y sus explicaciones | Verificado documentalmente, 23-10-2026 |
| DOC-02 | Índice jerárquico y recorrido 0–9; anterior/índice/siguiente. Navegación renderizada local: 26 HTML y 1711 enlaces internos, sin IDs duplicados; higiene y build estricto aprobados | Verificado documentalmente, 23-10-2026 |
| DOC-03 | Maestro de dieciséis vistas; vocabulario corregido en contexto y cloud, sin cambiar relaciones ni geometría. Exportación de 16 páginas coincide con sus SVG; 14 páginas intactas. Dos SVG finales inspeccionados a 720 px | Verificado; diagramas conservan evidencia del 18-10 |
| DOC-05 | Nueve microprácticas con archivos/comando/resultado/fallo/alternativa; runbook autenticado con broker detenido ejecutado literalmente | Verificado |
| DOC-04 | Mapa funcional/código, servicios de apoyo y distinción de niveles en explicaciones y dieciséis vistas | Verificado |
| DOC-06 | Fichas seleccionadas enlazan libros y código/README externo pertinente; páginas oficiales concretas, fragmentos fijados y propósito de consulta. Revisión de contenido y enlaces en `make verify` | Verificado documentalmente |
| DOC-07 | Nueve fichas revisadas contra implementación y fuentes; tres SVG inspeccionados a 720 px. Revisión independiente y precisión TLS corregida con verificación de CA/hostname y fuente libpq | Verificado documentalmente, 2026-10-17 |
| QA-03 | JUnit obligatorio local/CI; 25 pruebas de scripts y omisión deliberada rechazada aunque pytest termine 0 | Verificado |
| DOC-08 | Preparación y nueve capítulos repasados: definiciones en primer uso, ejemplo OrderPage válido contra OpenAPI, SQL idéntico a reserva JDBC salvo formato, búsqueda léxica 2/0 términos, ventanas y versiones aclaradas. Funciones de observabilidad y GCP introducidas sin nuevas capacidades. Revisión independiente, ajuste de explicación 503 reparado; comandos y navegación conservados | Verificado, 2026-10-21 |
| DOC-09 | Tres ejercicios con pista y solución: Quantity, carrera SQL y caso léxico propio; cierre de dos réplicas con seis criterios. Comando de dominio: dos pruebas aprobadas; caso léxico inicial rechaza etiqueta vacía y completado obtiene el documento/métricas previstos | Verificado, 2026-10-21 |
| DOC-10 | Dos escenarios de calidad trazados a EVT/ARCH y tres respuestas preparadas con rúbrica de respaldo/citas/abstención. Diagnósticos dentro de solución desplegable; no se atribuyen resultados a modelos | Revisado documentalmente, sin modelo |
| DOC-11 | Extractos cotejados con fuentes; relaciones de datos/estados/secuencias en texto. Recorrido esencial/opcional y referencias orientadas, nueve ejemplos externos fijados a commits consultados. MIT para material original, licencias de terceros conservadas | Verificación documental y revisión independiente |
| DOC-12 | Portada y entrada priorizan criterios, decisiones, implementación y evidencia; comprobaciones reproducibles, casos de aplicación y preguntas de diseño conservan el recorrido completo. Nueve cuerpos técnicos y 23 bloques de código comparados con el commit anterior sin pérdida de explicaciones ni soluciones | Verificado documentalmente, 2026-10-21 |
| QA-01 | Revisor independiente detectó dos P2: cola de workflow y timestamp futuro; reparados y validados con negativos | Verificado en alcance revisado |
| QA-04 | Preparación CI conserva Node24, SHA e inputs contrastados con origen oficial; el resultado se atribuye a cada ejecución | Revisado independientemente; resultado por commit en Actions |
| QA-02 | Revisiones funcionales con hallazgos reparados; CI del commit fusionado completada; únicamente el control de imágenes falla por Grafana, que sigue pendiente | Bloqueado en seguridad de Grafana |
| LIVE | No se ha llamado a un modelo ni servicio de embeddings real | No ejecutado, optativo |
| CLOUD-DEPLOY | Sin apply ni recursos cloud creados | Fuera de alcance |
| REF-HTTP-01, REF-LOG-01 | Cuatro pruebas ProblemAdvice y caso MVC integrado: 404/405/406/415. AI añade cinco regresiones de 404/405 con Problem Details, correlación y Allow; cabeceras y detalles sensibles no sustituyen los metadatos del cuerpo. Regresión completa | Verificado |
| REF-ARCH-05 | Doce pruebas de arquitectura: producción, negativos de servicio ajeno y positivos input/modelos/shared | Verificado |
| REF-OBS-05 | Cuatro releases oficiales ARM64/AMD64; validación, promtool y smoke integrado pasan. Trivy 0.76.1 por digest: tres imágenes sin HIGH/CRITICAL, Grafana con ocho HIGH | **Bloqueado en seguridad de Grafana** |
| REF-NAV-01, REF-DOC-01 | 104 alias previos conservados, 35 anclas nuevas, nueve bloques de lectura; checker HTML con ocho pruebas, revisión especializada y navegador | Verificado documentalmente |
| REF-STUDY-01 | Tres sesiones Java/Spring conservadas; ejercicios nuevos contrastados en copia temporal: Kafka/grace (1 prueba), Python/Protocol/cancelación y reutilización (1), tres consultas PromQL con promtool 3.15.1; ArchUnit directo sin Docker (12 pruebas) | Verificado; no medido con alumnos |
| REF-BIB-01 | 17 volúmenes, 35 temas, 50 asociaciones, nueve repositorios; índices/extractos y fragmentos consultados, una lectura inicial por capítulo | Verificado; no lectura íntegra de libros de pago |
| REF-JAVA25-01 | JDK 25+36-3489 local: make java25-lab aprueba seis comprobaciones explícitas, sin preview. Scope, anidación, excepción, aislamiento y propagación explícita; aplicaciones Java 21 conservadas | Verificado localmente; job CI independiente, resultado por SHA |
| REF-EVIDENCE-01 | Resultados consolidados por capacidad; revisión de agentes diferenciada de ejecución. CI del commit fusionado completada y enlazada en el estado de entrega | Verificación local y CI completadas; Grafana pendiente |


<a id="verificacion-vigente"></a>
<a id="verificación-vigente"></a>
<a id="seguridad-higiene-y-automatizacion"></a>
<a id="seguridad-higiene-y-automatización"></a>

## Comprobaciones de esta entrega

| Comprobación | Ejecución y alcance |
|---|---|
| Aplicaciones | `make verify eval`: 45 pruebas Commerce, 20 Analytics y 153 Python (218 en total), cero fallos/errores/omitidas. PostgreSQL, Kafka y pgvector reales mediante fixtures efímeras; arquitectura Java e import-linter incluidos. |
| Scripts | 25 pruebas unitarias tras actualizar el scanner a 0.76.1, incluida consistencia de versión con CI. Incluyen navegación negativa, provisioning/PromQL e informes malformados, obsoletos o de otro digest. |
| Documentación | Higiene sobre 33 Markdown, MkDocs estricto y verificador: 26 HTML/1711 enlaces locales, sin destinos, fragmentos ni IDs duplicados erróneos. Evidencia previa PR #1, no repetida: GitHub GFM con 26 documentos renderizados por API oficial y 224 enlaces con fragmento contrastados. Inspección previa de portada y bibliografía en navegador local; no acredita accesibilidad WCAG. |
| Ejercicios | Nuevas soluciones: Kafka/grace (1 prueba), Python/Protocol/cancelación (1) y tres consultas PromQL con series sintéticas en promtool 3.15.1, aprobadas. ArchUnit directo sin Docker: 12 pruebas. Se conserva evidencia previa de cuatro pruebas de las soluciones Java de paginación/proxy. Variantes de aprendizaje en copias temporales. |
| Privacidad Python | Cuatro regresiones nuevas: filtro de servidor, copia de spans y Uvicorn real con telemetría desactivada/activada, HTTPX loopback y propagación. Junto a cancelación SSE existente: cinco comprobaciones aprobadas. Exportador en memoria, sin servicio externo. |
| Java 25 | make java25-lab con JDK 25+36-3489: seis comprobaciones aprobadas, sin preview ni dependencias; job CI propio. No migra aplicaciones, imágenes ni Maven de Java 21. |
| Evaluación IA | 42 casos/32 respondibles; recall@3=1, hit-rate@3=1, MRR@3=0,953125, aislamiento=abstención=1. Corpus sintético; recuperación offline, sin modelo real ni coste API. |
| Alertas | Validación y fixtures con Prometheus 3.15.1; ambas reglas comprueban umbral, plazo y resolución. Las cuatro mutaciones de umbral/for conservan su evidencia anterior; no se repitieron en esta entrega. |
| Compose | Imágenes reconstruidas; proyecto de prueba nuevo y aislado: smoke autenticado de negocio, RAG fake, aprobación/reintento/SSE y evento actual hasta outbox/inbox/resumen aprobado. El proyecto y sus volúmenes efímeros se retiraron al terminar. |
| Telemetría | make observability-smoke aprobado: tres scrapes frescos, incremento HTTP actual, traza nueva AI→Commerce, logs JSON correlacionados seguros y proxy de datasource Grafana. Se conserva la inspección visual parcial previa del dashboard; no se atribuye una nueva inspección visual a esta entrega. |
| Persistencia | Evidencia previa PR #1, no repetida localmente en esta entrega: Down/up del mismo proyecto sin borrar volúmenes: pedido REFUNDED y workflow EXECUTED recuperados por HTTP autenticado. |
| Terraform | `make terraform-check`: formato, init sin backend, validate y cuatro planes mock aprobados; sin apply ni llamadas cloud. |
| Kubernetes/HPA | Evidencia previa PR #1, no repetida localmente en esta entrega: Clúster nuevo y smoke aprobados. Escalado CPU real 1→2 con dos pods/endpoints listos; 89.955 GET correctos y cero errores en 300 s, cap 300/s y 16 workers. POST simultáneos en pods distintos: mismo resultado, decremento único y stock restaurado. Sin parchear réplicas; no es benchmark ni prueba de HA. |
| Dependencias Python | Evidencia previa PR #1, no repetida localmente en esta entrega: pip-audit 2.9.0 sobre exportaciones completas y congeladas de runtime/desarrollo y documentación: sin vulnerabilidades conocidas. |
| Fuente y Java | Evidencia previa PR #1, no repetida localmente en esta entrega: Trivy 0.70.0 de fuente/manifiestos/secretos y archivos Java empaquetados más dependencias runtime Analytics: sin HIGH/CRITICAL conocidos ni secretos detectados. No es pentest ni garantía de ausencia de datos sensibles. |
| Imágenes de observabilidad | Evidencia previa PR #1, no repetida localmente en esta entrega: Nuevo análisis con Trivy 0.76.1 de las referencias exactas de Compose e inventario digest/plataforma ARM64: Collector, Prometheus y Tempo sin HIGH/CRITICAL; Grafana ocho HIGH. Alternativa oficial 13.2.4: 75 coincidencias HIGH/CRITICAL, descartada. No se infiere estado AMD64 a partir del scan ARM64. |

El arranque inicial sobre los volúmenes locales preexistentes fue rechazado por la guarda V2: existían registros de idempotencia legados. Se conservó esa protección y sus datos; la comprobación actual se realizó en un proyecto Compose nuevo, sin reinterpretar claves ni resetear volúmenes anteriores.

[Pruebas HTTP](../apps/commerce-api/src/test/java/dev/reference/commerce/shared/infrastructure/rest/ProblemAdviceTest.java) · [integración Commerce](../apps/commerce-api/src/test/java/dev/reference/commerce/CommerceIntegrationTest.java) · [Kafka y ciclo de vida](../apps/order-analytics/src/test/java/dev/reference/analytics/StreamsLifecycleTest.java) · [evaluación](../apps/ai-service/src/ai_service/evaluate.py) · [señales](../scenarios/observability.py) · [scanner](../scripts/scan-observability-images.py).

<a id="documentacion-y-diagramas"></a>
<a id="documentación-y-diagramas"></a>

## Revisión y trazabilidad

El cierre acotado añade cinco regresiones HTTP y cuatro de privacidad, incluidas Uvicorn real y exportación en memoria; revisión independiente enfocada aprobada. Los cambios de esta entrega recibieron revisión técnica independiente de HTTP, métricas, diagnóstico, pruebas, transacciones y Kafka, además de revisión final didáctica y bibliográfica. Se corrigieron referencias del sitio generado y una explicación heredada de lag cero. El laboratorio Java 25 recibió revisión separada de código, CI y límites. No quedan hallazgos materiales de esas revisiones. La entrega previa también se revisó por bloques, por agentes diferentes de los autores. Se contrastaron HTTP/logs, límites Java, mantenimiento operativo, navegación, ejercicios y bibliografía. La primera ronda corrigió aceptación de JSON malformado/obsoleto, correspondencias bibliográficas demasiado amplias y un destino sin explicación de concurrencia. La reparación del scanner recibió a su vez revisión independiente y pruebas. Estas son revisiones técnicas de agentes, no auditorías humanas ni pruebas de aprendizaje con lectores.

Los índices editoriales y extractos permiten verificar títulos y capítulos; no demuestran lectura integral ni respaldo de los autores. Los nueve ejemplos GitHub se recuperaron y fijaron a commits; no se ejecutaron esos proyectos externos. Los dieciséis diagramas y su maestro no se modificaron; conservan su verificación anterior de exportación e inspección. El verificador HTML no consulta Internet ni renderiza GitHub Markdown. Los anchors explícitos y los enlaces relativos mantienen la navegación del repositorio; el contraste GFM corresponde al contenido renderizado por la API oficial, sin atribuir inspección visual de la PR remota.

La CI separa verificación funcional Java 21, laboratorio Java 25, dependencias Python, fuente/Java e imágenes de telemetría. La PR #2 reúne todos los commits de esta entrega y está fusionada en `main` mediante `07b22a595f5d92405e544c0cb7a79bc29254644b`; la CI de ese commit está completada, con `verify`, `java25-lab`, `java-security` y `python-security` aprobados. El resultado global es fallido únicamente por `observability-security`, debido a las ocho coincidencias HIGH conocidas de Grafana. El mantenedor autorizó expresamente la integración de las PR #1 y #2 con el pendiente de Grafana documentado en [ADR006](decisions/006-maintenance.md). El control de imágenes conserva su resultado fallido; esta aceptación puntual no acredita seguridad completa ni modifica los controles o las protecciones de rama.

<a id="decisiones-y-reparaciones-que-deben-conservarse"></a>
<a id="valoracion-y-uso-productivo"></a>
<a id="valoración-y-uso-productivo"></a>

## Límites que deben conservarse

- Transacción SQL, outbox e inbox no dan entrega universal exactamente una vez: deduplicación limitada a siete días y retención finita. Una DLT exige investigar el hecho original antes de reprocesar.
- El ámbito idempotente tipado y la migración V2 rechazan historial ambiguo; requieren parada de escritores. No se promete rolling upgrade.
- Plazos y cupos son por proceso; SQLite serializa workflows y requiere una réplica. Un deadline no garantiza cancelar trabajo remoto ni facturación.
- Una cita válida no prueba fidelidad semántica. La IA simulada y la evaluación de recuperación no miden un modelo generativo real.
- Dependencias locales sin HA, PostgreSQL sin TLS local, ausencia de capacidad representativa/SLO y restauración de backups demostrada. Un HPA local no resuelve estos límites.
- Terraform con mocks no comprueba IAM efectivo, cuotas, disponibilidad ni conectividad cloud. No se contrataron servicios ni se aplicó infraestructura cloud.

[Delivery](handbook/delivery.md#topic-reutilizar-la-referencia-en-un-sistema-real) explica qué adaptar según los requisitos del producto. [ADR002](decisions/002-consistency.md), [ADR003](decisions/003-ai-evidence.md) y [ADR006](decisions/006-maintenance.md) conservan decisiones y riesgos. Ampliar herramientas sin necesidad demostrada no mejora por sí solo esta referencia.
