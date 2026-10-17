# Seguridad, límites y resiliencia

## Autenticación y autorización

OIDC establece quién llama; las reglas de negocio establecen qué puede hacer. Commerce y AI validan firma, issuer, audiencia y caducidad del JWT, además de subject y tenant válidos. El claim de rol debe tener la estructura esperada. Un token firmado pero sin vencimiento no es una credencial aceptable en esta referencia.

El tenant siempre procede del token. Buscar un pedido exige tenant y propietario, salvo MANAGER del mismo tenant. Un identificador UUID difícil de adivinar no sustituye esa condición. Se devuelve 404 para recursos ajenos para limitar divulgación. Los tests incluyen firma, issuer y audiencia incorrectos, token caducado, claims ausentes y acceso cruzado.

Keycloak local tiene credenciales sintéticas y password grant para automatizar escenarios. Solo se publica en loopback. Es una excepción de laboratorio; un cliente público productivo debe usar Authorization Code + PKCE, TLS y configuración endurecida. Las aplicaciones no ofrecen un endpoint para inventar tokens ni elegir un rol en el cuerpo de login.

## Modelo de amenazas mínimo

| Amenaza | Control ejecutable | Límite |
|---|---|---|
| Leer otro tenant | Filtro servidor antes de recuperación/consulta | Depende de identidad OIDC confiable |
| Ejecutar devolución sin permiso | Rol, tenant, versión y caducidad en comercio | Aprobación humana también puede ser errónea |
| Repetir un efecto tras timeout | Clave idempotente y transacción | Retención debe cubrir ventana de replay |
| Instrucciones maliciosas en documentos | Sin herramienta privilegiada en generación; validación de citas | No elimina respuestas engañosas |
| Filtrar tokens en estado/log | Token solo en memoria, errores sanitizados | Revisar telemetría al añadir instrumentación |
| Agotar proveedor o workers | Timeout, intentos/cupo y concurrencia acotados | Rate limit por cliente requiere gateway productivo |

La prompt injection es contenido no confiable intentando cambiar instrucciones. No se resuelve con «ignora instrucciones maliciosas» como única defensa. El generador solo recibe documentos autorizados y no tiene la herramienta de ejecutar devoluciones. El workflow usa endpoints explícitos que vuelven a autorizar la acción. El dataset adversarial verifica controles concretos; no prueba ausencia de todos los ataques posibles.

## Fallos de dependencias

Una llamada remota necesita un plazo. El adaptador IA usa timeout de diez segundos, como máximo dos intentos para errores transitorios y circuito abierto tras tres fallos, con recuperación de prueba a treinta segundos. Errores de entrada, permisos o esquema no deben reintentarse ciegamente. Las pruebas usan reloj/transporte controlados, sin esperar a un proveedor real.

El semáforo limita cuatro tareas de respuesta. El plazo total evita acumular trabajo indefinidamente; cancelar SSE cancela su tarea. El circuit breaker evita insistir cuando la dependencia falla, pero no ofrece capacidad adicional ni sustituye una cola limitada. Backoff, jitter y presupuesto total deben revisarse juntos: varios niveles reintentando multiplican llamadas.

Fallback sin evidencia significa abstenerse, no inventar una respuesta más barata. Una salida estructurada garantiza forma cuando se valida; no garantiza verdad. Las citas deben pertenecer al contexto autorizado y la fidelidad semántica se evalúa aparte.

## Secretos y supply chain

No guardar claves ni credenciales reales en Git. El override live exige configuración explícita; el arranque normal y CI usan fake. Las credenciales locales conocidas solo sirven para este entorno y deben reemplazarse junto a su mecanismo de distribución en un despliegue real. Las aplicaciones usan usuarios de base separados y contenedores no-root.

Lockfiles, versiones explícitas y análisis de dependencias reducen deriva; un build reproducible no implica ausencia de vulnerabilidades. Revisar advisories, compatibilidad y licencias antes de actualizar. Las verificaciones automáticas y sus resultados constan en la evidencia, sin afirmar un pentest completo.

```bash
make test
python3 scripts/check_repository.py
```

Fuentes: [OWASP API Security](https://owasp.org/API-Security/), [OWASP GenAI](https://genai.owasp.org/), [Keycloak contenedores](https://www.keycloak.org/server/containers), [RFC 9700 OAuth Security BCP](https://www.rfc-editor.org/rfc/rfc9700.html).
