# Criterios de aceptación

2026-10-21. Cada fila es un AC obligatorio salvo LIVE y CLOUD-DEPLOY. Estado y evidencia por ID en [verificación](verification.md). Una implementación sin prueba/evidencia no está verificada.

| ID | Condición y resultado observable | Verificación |
|---|---|---|
| SPEC-01 | Cada capacidad tiene contrato, error y test asociado antes del código | Revisión especificación/matriz |
| ARCH-01 | Dominio no importa framework; aplicación no importa adaptadores | ArchUnit e import-linter |
| ARCH-02 | Módulos no usan persistencia ajena; puertos input/output explícitos | Reglas y revisión independiente |
| ARCH-03 | Las mismas reglas que verifican producción rechazan dependencias prohibidas representativas (framework en dominio, persistencia ajena, aplicación → adaptador), identificando origen y destino; comunicación por input permitida | Negativos ejecutables sin reglas alternativas ni fallos por selección vacía |
| EVOL-01 | Controladores por módulo y adaptadores por tecnología; parámetros descriptivos; raíz Java dev.reference uniforme; sin cambiar contratos existentes | ArchUnit, compilación y regresión HTTP/eventos |
| EVOL-02 | Errores de negocio por significado sin status HTTP en dominio; estados tipados y Quantity protegen invariantes también sin controlador | Tests de dominio y traducción Problem Details |
| EVOL-03 | Gateway de Comercio por intención y resultados tipados; traduce transporte/JSON inválido sin filtrar datos; conserva JWT efímero, claves y reanudación | Mock HTTP y workflow/reinicio |
| EVOL-04 | OpenAPI canónico de Comercio comprobado contra rutas, seguridad, entradas y respuestas reales; quantity/version HTTP rechazan coerción string/decimal, tolerancia extra/path legado y restricción wire explícitas; negativos independientes, sin modelos generados duplicados | Tests contractuales positivos/negativos y API |
| EVOL-05 | Listado de pedidos con límite 1..50, filtro status opcional, cursor UUID y orden id ASC; tenant/propietario en SQL, sin total ni snapshot prometido | Páginas completas/finales, filtros, límites e aislamiento en PostgreSQL |
| EVOL-06 | Pruebas demuestran proxy transaccional efectivo y negativo de llamada directa; soporte Testcontainers compartido solo al reutilizarse | Rollback real y negativo sin proxy; integraciones sin skips |
| EVOL-07 | Streams prueba ciclo de inicio/cierre acotado y readiness, con consumo acotado; documentación relaciona lag/capacidad y señala límites | Pruebas de lifecycle y Kafka real, sin nueva plataforma |
| ORD-01 | Crear pedido descuenta stock y calcula precio servidor | Integración PostgreSQL/API |
| ORD-02 | Dos pedidos de última unidad: uno acepta, otro conflicto, stock cero | Test concurrente BD real |
| ORD-03 | Repetir clave/cuerpo no repite efecto; cambiar cuerpo da 409; tenant/actor/operación/clave no colisionan aunque contengan delimitadores | Integración idempotencia |
| ORD-04 | Fallar operación no deja pedido, stock ni outbox parciales | Rollback BD real |
| RET-01 | Propuesta pertenece a pedido visible PLACED; no duplicar activa | Integración API |
| RET-02 | Solo MANAGER tenant aprueba versión exacta | Autorización y conflicto |
| RET-03 | Expirada/cambiada/no aprobada no ejecuta | Reloj controlado y tests negativos |
| RET-04 | Ejecutar/reintentar devuelve resultado único y repone una vez | Concurrencia e idempotencia |
| EVT-01 | Commit sin broker conserva outbox pendiente; recuperar publica | Fallo/reinicio real |
| EVT-02 | Duplicado produce un efecto inbox | Consumidor PostgreSQL/Kafka |
| EVT-03 | Publicador confirma antes de marcar; caída admite repetición | Test integración y revisión |
| EVT-04 | Schema evento real coincide con contrato versionado | JSON Schema contra fixture real |
| EVT-05 | UUID completos, admitiendo mayúsculas/minúsculas, conservan identidad; ambos consumidores rechazan abreviados y malformados antes del efecto. Inválido termina en DLT; si falla ese envío no avanza el offset; recuperación y reproceso conservan identidad | Corpus UUID compartido, negativos de consumidores y Kafka real para DLT/recuperación |
| STR-01 | Ventana 1min agrega tenant/SKU y montos correctos | TopologyTestDriver |
| STR-02 | Duplicados y fuera de orden dentro grace no inflan | TopologyTestDriver |
| STR-03 | Tardío fuera grace se descarta y cuenta | Avance stream-time |
| STR-05 | occurredAt más de 30 s adelantado respecto al reloj de proceso se envía a DLT antes de avanzar tiempo de evento; tampoco un timestamp Kafka futuro de un inválido puede cerrar ventanas válidas. La DLT recibe timestamp del reloj de cuarentena para conservar retención | Reloj fijo, límite exacto, inválido futuro y pedido normal posterior |
| STR-04 | Reinicio recupera state/changelog; identidad repetida en particiones distintas no duplica resumen | Kafka real/reinicio |
| AI-01 | Respuesta tiene citas existentes o abstiene sin evidencia | Dataset >=40 casos |
| AI-02 | Filtro tenant previo a recuperación y caché aislada | Casos de acceso cruzado |
| AI-03 | Fake está identificado; CI no requiere claves | API y CI offline |
| AI-04 | Adaptador real valida sobre, tipos y referencias de salida completa antes de declarar éxito; JSON inesperado, rechazo/incompleto/timeout devuelve error controlado | Mock HTTP con respuestas válidas y adversas |
| AI-05 | Plazo total incluye cola; límites concurrencia/tokens/intentos y breaker cuentan fallos por deadline y contrato una sola vez, sin penalizar cancelación cliente; salida inválida no cierra half-open | Tests deterministas, apertura y recuperación del breaker |
| AI-06 | SSE termina en resultado/error y cancela trabajo al desconectar | Test streaming/cancelación |
| AI-07 | Lexical recall@3 >=0.90 con fórmula válida para múltiples relevantes; hit-rate/MRR separados; referencias 100% | Evaluación fija reproducible |
| AI-08 | Vector filtra tenant, valida dimensión y ordena por distancia | pgvector real, vectores fixture |
| WF-01 | Crear propuesta persiste workflow; restart permite continuar | Persistencia/reinicio |
| WF-02 | Aprobar/reintentar no duplica efecto; no guarda JWT | Integración gateway y checkpoints |
| WF-03 | Mutaciones del workflow tienen plazo total de 12 s incluida espera del lock y admisión máxima de 16 operaciones por proceso; exceso/timeout devuelve 503, cancelación libera cupo sin perder claves de reanudación | Dependencia bloqueada, cola, capacidad, cancelación y retry con misma clave |
| SEC-01 | Sin token 401, role insuficiente 403, ajeno 404 | Tokens OIDC reales y negativos |
| SEC-02 | JWT inválido/expirado/issuer/audience erróneos rechaza | Tests seguridad |
| SEC-03 | Prompt injection no llama herramienta privilegiada | Adversarial tests |
| SEC-04 | No secretos/PII en logs; sin token minting en APIs | Scan y revisión logs |
| OBS-01 | HTTP-evento-consumidor correlacionados | Escenario y trazas visibles |
| OBS-02 | Dashboard lag/outbox/errors/IA y alerta accionable; éxitos, abstenciones, errores y cancelaciones IA contabilizados una sola vez | Inspección y runbook |
| OBS-03 | Un comando optativo verifica targets de las tres aplicaciones, incremento de métricas y traza de una operación nueva; no acepta señales históricas. Dashboard local permite consultar métricas y trazas sin cuenta externa | Escenario real sobre Compose con telemetría activada y pruebas negativas |
| OBS-04 | Logs operativos estructurados con correlación cuando existe; sin cuerpos, JWT, preguntas ni identificadores personales como labels. Explicación breve de cuándo usar métricas, logs y trazas y enlace al código | Tests de privacidad/correlación y revisión de configuración |
| K8S-03 | Comprobación optativa acotada observa HPA de una a dos réplicas disponibles bajo carga y verifica idempotencia concurrente entre pods; restaura su carga/configuración temporal sin afectar otros clústeres | Escenario kind real, no inferido de manifests ni de carga GET aislada |
| OPS-01 | Compose arranca desde vacío, seed y demo terminan | make up/demo/smoke |
| OPS-04 | Pedido recién creado observado en outbox, inbox y actualización Kafka de su tenant/SKU/ventana; no aceptar primer histórico | Escenario integrado con plazo y aserciones |
| OPS-02 | Down conserva datos; reset explícito y acotado | Reinicio y consulta persistencia |
| OPS-03 | Builds ARM64 y probes/recursos/no-root | Docker inspect/smoke |
| K8S-01 | Namespace dedicado, rollout y probes correctos | kind smoke |
| K8S-02 | HPA escala 1->2 con carga sin duplicar efecto | kind/metrics-server/load |
| TF-01 | fmt/validate/tests plan mocks aprueban sin cloud; rol mínimo GKE y permisos de imágenes por intención; IPv6 rechazado en operator_cidr IPv4 /32 | CLI fijada |
| DOC-01 | Cada ficha explica problema, decisión, alternativa y prueba; los conceptos no obvios para un lector junior reciben una introducción breve y una distinción clara | Revisión editorial independiente |
| DOC-02 | Links/código/comandos/diagramas coinciden con sistema; el README ofrece un temario jerárquico visible con enlaces válidos a las secciones | Build estricto, verificación de enlaces y revisión editorial |
| DOC-03 | C4, identidad/señales, dependencias internas, secuencias/estados, RAG/Streams, modelo SQL y despliegue local/cloud trazan a implementación o propuesta explícita; los diagramas de aprendizaje aclaran una relación concreta sin duplicar vistas existentes | Revisión de código/configuración, SVG insertados y maestro draw.io editable; inspección visual de cada SVG modificado |
| DOC-05 | Cada ficha incluye ruta de lectura, micropráctica ejecutable, salida esperada, fallo y alternativa | Revisión especializada y comandos comprobados |
| DOC-04 | Nombre funcional y nombre técnico se corresponden; se distinguen aplicaciones, módulos, bibliotecas, servidores y protocolos | Mapa canónico, diagramas y referencias a código |
| DOC-06 | Cada tema enlaza código y prueba, documentación oficial específica y, solo cuando aporta, una lectura o repositorio público pertinente. Los repositorios externos llevan enlace a código/README concreto y estrellas con fecha de consulta; las estrellas no se presentan como aval | Revisión de fuentes y enlaces; comprobación de estrellas y fecha |
| DOC-07 | Las nueve fichas aclaran vocabulario y un ejemplo concreto donde exista ambigüedad; vistas de topología Kafka, concurrencia SQL y circuito IA trazan a código y conviven en el maestro único. Ejemplos numéricos son ilustrativos, no mediciones nuevas | Revisión por ficha, contraste con código, exportación e inspección de los tres SVG; enlaces y MkDocs estricto |
| DOC-08 | El manual ofrece preparación inicial y nueve capítulos ordenados, con conocimientos previos, objetivos, práctica guiada, comprobación razonada y navegación anterior/índice/siguiente. La lectura introduce los términos necesarios en su primer uso, muestra ejemplos SQL/JSON reconocibles y usa español natural de España; conserva contratos y separa mantenimiento de aprendizaje | Revisión editorial independiente, enlaces/anclas y build estricto |
| QA-01 | Tests significativos y revisiones sin hallazgos altos abiertos | Informes especialistas |
| QA-03 | Verificación canónica local y CI rechazan skips Java/Python; una omisión deliberada demuestra el gate | JUnit y experimento negativo reversible |
| QA-04 | Acciones CI mantenidas con Node24, fijadas a SHA verificado; checkout sin persistir credenciales y permisos read mínimos | action.yml oficial y ejecución remota de los tres jobs |
| QA-02 | >=90/100 y cada dimensión >=8/10 con evidencia | Rúbrica final, máximo dos reparaciones |
| LIVE | Opcional: modelo real con presupuesto y dataset medidos | Solo con configuración explícita |
| CLOUD-DEPLOY | Fuera de entrega: despliegue real GCP | No ejecutar ni afirmar validado |

## Mejoras selectivas de la referencia

| ID | Condición y resultado observable | Verificación |
|---|---|---|
| REF-ARCH-01 | el código productivo pasa con los tres módulos realmente seleccionados; un patrón que no seleccione clases no acredita éxito. | Pruebas enfocadas, negativos y gate integrado |
| REF-ARCH-02 | las dos fixtures cíclicas fallan por el ciclo real e identifican sus dos módulos/clases, no por otra regla o una importación vacía. | Pruebas enfocadas, negativos y gate integrado |
| REF-ARCH-03 | la colaboración acíclica por input y el uso permitido de tipos compartidos pasan; siguen fallando los negativos de frameworks y repositorios ajenos. | Pruebas enfocadas, negativos y gate integrado |
| REF-ARCH-04 | no cambia ningún endpoint, evento, transacción, paquete productivo o dependencia Maven. | Pruebas enfocadas, negativos y gate integrado |
| REF-AI-01 | el dataset conserva 42 casos y sus etiquetas de relevancia; todos tienen IDs únicos y referencias válidas. Cualquier corrección material de etiquetas exige justificación separada. | Pruebas enfocadas, negativos y gate integrado |
| REF-AI-02 | sin cambios del recuperador, los agregados mantienen el baseline registrado. El detalle permite localizar qué casos explican un MRR inferior a uno. | Pruebas enfocadas, negativos y gate integrado |
| REF-AI-03 | una fixture de ranking degradado cambia el caso afectado y el agregado coherentemente. Duplicados, múltiples relevantes y relevantes fuera del top3 siguen midiendo las propiedades actuales. | Pruebas enfocadas, negativos y gate integrado |
| REF-AI-04 | el informe identifica corpus/dataset y no incluye credenciales, datos de usuarios reales ni afirmaciones de calidad generativa. Datos ausentes o evaluación no aplicada no se convierten en ceros/unos engañosos. | Pruebas enfocadas, negativos y gate integrado |
| REF-AI-05 | `make eval` sigue sin red, Docker, claves ni facturación; el informe se genera solo cuando se solicita y permanece ignorado. | Pruebas enfocadas, negativos y gate integrado |
| REF-OBS-01 | promtool valida las dos reglas existentes y sus estados temporales, incluidos umbral exacto y recuperación, sin servidores ni datos reales. | Pruebas enfocadas, negativos y gate integrado |
| REF-OBS-02 | una mutación reversible del umbral y otra del `for` hacen fallar los casos pertinentes. Registrar el resultado y restaurar las reglas; no conservar mutaciones ni un framework de mutation testing. | Pruebas enfocadas, negativos y gate integrado |
| REF-OBS-03 | el comando devuelve fallo cuando promtool falla, reutiliza la versión fijada y no modifica servicios ni requiere credenciales. Con la imagen disponible, se ejecuta sin red. | Pruebas enfocadas, negativos y gate integrado |
| REF-OBS-04 | la verificación automatizada ejecuta los fixtures y la documentación distingue pruebas de reglas de observabilidad extremo a extremo. | Pruebas enfocadas, negativos y gate integrado |

## Protocolo de entrega

Por fase: verificar contratos y AC, implementar el mínimo, ejecutar tests/fallos, escribir explicación y ADR, revisar con especialista independiente, reparar como máximo dos rondas, actualizar evidencia. Un bloqueo material mantiene la entrega incompleta; no rebajar criterio. Los cambios a alcance exigen motivo explícito.

Rúbrica: corrección25, arquitectura20, seguridad/IA15, didáctica15, tests10, operación10, DevEx5. No confundir cobertura o popularidad con calidad. No puntuar como excelente antes de ejecutar escenarios obligatorios. Verificación local de Kubernetes no acredita HA y Terraform mocks no acredita cloud.
