# Criterios de aceptación

2026-10-17. Cada fila es un AC obligatorio salvo LIVE y CLOUD-DEPLOY. Estado y evidencia por ID en [verificación](verification.md). Una implementación sin prueba/evidencia no está verificada.

| ID | Condición y resultado observable | Verificación |
|---|---|---|
| SPEC-01 | Cada capacidad tiene contrato, error y test asociado antes del código | Revisión especificación/matriz |
| ARCH-01 | Dominio no importa framework; aplicación no importa adaptadores | ArchUnit e import-linter |
| ARCH-02 | Módulos no usan persistencia ajena; puertos input/output explícitos | Reglas y revisión independiente |
| ARCH-03 | Una dependencia prohibida deliberada falla | Experimento reversible documentado |
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
| EVT-05 | Inválido termina en DLT; si falla ese envío no avanza el offset; recuperación y reproceso conservan identidad | Kafka real y comando reproducible |
| STR-01 | Ventana 1min agrega tenant/SKU y montos correctos | TopologyTestDriver |
| STR-02 | Duplicados y fuera de orden dentro grace no inflan | TopologyTestDriver |
| STR-03 | Tardío fuera grace se descarta y cuenta | Avance stream-time |
| STR-04 | Reinicio recupera state/changelog; identidad repetida en particiones distintas no duplica resumen | Kafka real/reinicio |
| AI-01 | Respuesta tiene citas existentes o abstiene sin evidencia | Dataset >=40 casos |
| AI-02 | Filtro tenant previo a recuperación y caché aislada | Casos de acceso cruzado |
| AI-03 | Fake está identificado; CI no requiere claves | API y CI offline |
| AI-04 | Adaptador real valida esquema/rechazo/incompleto/timeout | Mock HTTP de proveedor |
| AI-05 | Plazo total incluye cola; límites concurrencia/tokens/intentos y breaker cuentan fallos por deadline sin penalizar cancelación cliente | Tests deterministas |
| AI-06 | SSE termina en resultado/error y cancela trabajo al desconectar | Test streaming/cancelación |
| AI-07 | Lexical recall@3 >=0.90 con fórmula válida para múltiples relevantes; hit-rate/MRR separados; referencias 100% | Evaluación fija reproducible |
| AI-08 | Vector filtra tenant, valida dimensión y ordena por distancia | pgvector real, vectores fixture |
| WF-01 | Crear propuesta persiste workflow; restart permite continuar | Persistencia/reinicio |
| WF-02 | Aprobar/reintentar no duplica efecto; no guarda JWT | Integración gateway y checkpoints |
| SEC-01 | Sin token 401, role insuficiente 403, ajeno 404 | Tokens OIDC reales y negativos |
| SEC-02 | JWT inválido/expirado/issuer/audience erróneos rechaza | Tests seguridad |
| SEC-03 | Prompt injection no llama herramienta privilegiada | Adversarial tests |
| SEC-04 | No secretos/PII en logs; sin token minting en APIs | Scan y revisión logs |
| OBS-01 | HTTP-evento-consumidor correlacionados | Escenario y trazas visibles |
| OBS-02 | Dashboard lag/outbox/errors/IA y alerta accionable; éxitos, abstenciones, errores y cancelaciones IA contabilizados una sola vez | Inspección y runbook |
| OPS-01 | Compose arranca desde vacío, seed y demo terminan | make up/demo/smoke |
| OPS-04 | Pedido recién creado observado en outbox, inbox y actualización Kafka de su tenant/SKU/ventana; no aceptar primer histórico | Escenario integrado con plazo y aserciones |
| OPS-02 | Down conserva datos; reset explícito y acotado | Reinicio y consulta persistencia |
| OPS-03 | Builds ARM64 y probes/recursos/no-root | Docker inspect/smoke |
| K8S-01 | Namespace dedicado, rollout y probes correctos | kind smoke |
| K8S-02 | HPA escala 1->2 con carga sin duplicar efecto | kind/metrics-server/load |
| TF-01 | fmt/validate/tests plan mocks aprueban sin cloud; rol mínimo GKE y permisos de imágenes por intención; IPv6 rechazado en operator_cidr IPv4 /32 | CLI fijada |
| DOC-01 | Cada ficha explica problema, decisión, alternativa y prueba | Revisión editorial independiente |
| DOC-02 | Links/código/comandos/diagramas coinciden con sistema | Build estricto y visual QA |
| DOC-03 | C4, identidad/señales, dependencias internas, secuencias/estados, RAG/Streams, modelo SQL y despliegue local/cloud trazan a implementación o propuesta explícita; ADR y patrones justifican decisiones | Matriz, revisión de código/configuración, SVG insertados y maestro draw.io editable |
| DOC-05 | Cada ficha incluye ruta de lectura, micropráctica ejecutable, salida esperada, fallo y alternativa | Revisión especializada y comandos comprobados |
| DOC-04 | Nombre funcional y nombre técnico se corresponden; se distinguen aplicaciones, módulos, bibliotecas, servidores y protocolos | Mapa canónico, diagramas y referencias a código |
| QA-01 | Tests significativos y revisiones sin hallazgos altos abiertos | Informes especialistas |
| QA-03 | Verificación canónica local y CI rechazan skips Java/Python; una omisión deliberada demuestra el gate | JUnit y experimento negativo reversible |
| QA-02 | >=90/100 y cada dimensión >=8/10 con evidencia | Rúbrica final, máximo dos reparaciones |
| LIVE | Opcional: modelo real con presupuesto y dataset medidos | Solo con configuración explícita |
| CLOUD-DEPLOY | Fuera de entrega: despliegue real GCP | No ejecutar ni afirmar validado |

## Protocolo de entrega

Por fase: verificar contratos y AC, implementar el mínimo, ejecutar tests/fallos, escribir explicación y ADR, revisar con especialista independiente, reparar como máximo dos rondas, actualizar evidencia. Un bloqueo material mantiene la entrega incompleta; no rebajar criterio. Los cambios a alcance exigen motivo explícito.

Rúbrica: corrección25, arquitectura20, seguridad/IA15, didáctica15, tests10, operación10, DevEx5. No confundir cobertura o popularidad con calidad. No puntuar como excelente antes de ejecutar escenarios obligatorios. Verificación local de Kubernetes no acredita HA y Terraform mocks no acredita cloud.
