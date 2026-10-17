# Commerce API

API de comercio: aplicación Java que mantiene pedidos, stock y devoluciones. Sus módulos `orders`, `inventory` y `returns` se ejecutan juntos. `OrderController`, `InventoryController` y `ReturnController` adaptan HTTP; `ManageReturnService` gestiona crear, aprobar y ejecutar propuestas.

[Mapa de aplicaciones y sistemas de apoyo](../../docs/repository-design.md#component-map).

Referencia Java 21/Spring Boot 4.1.1: pedidos, inventario y devoluciones con puertos de entrada/salida. PostgreSQL decide concurrencia; Kafka distribuye hechos confirmados. No hay pagos reales.

## Ejecución y pruebas

Desde esta carpeta: `./mvnw test` y `./mvnw spring-boot:run`. Las pruebas de integración requieren Docker y levantan PostgreSQL 17.7/Kafka 4.0.2 reales. En Rancher Desktop puede ser necesario `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./mvnw test` para montar el socket de Ryuk dentro de su VM. No se omiten integraciones cuando falta Docker.

Los números JSON de quantity/version deben ser literales enteros: strings y decimales producen 400; no se truncan. La entrada tolera campos extra por compatibilidad. La descripción OpenAPI y los tests conservan explícitos los límites de representación.

La ejecución completa y el seed OIDC se gestionan desde Compose en la raíz. Puerto HTTP: **8080**. `/actuator/health/liveness`, `/actuator/health/readiness` y `/actuator/prometheus` están disponibles sin JWT para operación local; restringir métricas en una instalación pública.

| Variable | Predeterminado local |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/reference` |
| `DB_USER`, `DB_PASSWORD` | `reference`, `reference` |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `OIDC_ISSUER` | `http://localhost:8088/realms/reference` |
| `OIDC_JWKS` | issuer + `/protocol/openid-connect/certs` |
| `OIDC_AUDIENCE` | `reference-api` |
| `EVENTS_ENABLED` | `true` |
| `OUTBOX_DELAY_MS` | `1000` |

`OIDC_JWKS` puede usar hostname interno Docker mientras `OIDC_ISSUER` conserva el issuer público del token. Se verifican firma, issuer, audiencia y vencimiento; roles proceden de `realm_access.roles`, tenant de `tenant`. CUSTOMER accede a sus pedidos; MANAGER administra solo su tenant. Las credenciales de demostración se definen en el realm local, nunca en esta API.

## Contrato HTTP

[OpenAPI canónico](../../contracts/http/commerce.openapi.json) y [explicación de API](../../docs/handbook/api-contracts.md). El listado `GET /api/v1/orders` usa cursor UUID, límite 1..50 y filtro status; aplica tenant/propietario en SQL, sin prometer snapshot ni orden temporal.

## Decisiones verificables

- `domain` no depende de frameworks. Casos de uso transaccionales coordinan puertos; los adaptadores JDBC encapsulan SQL. Los módulos se comunican por puertos de entrada, sin consultar tablas ajenas.
- Reserva condicional `UPDATE ... WHERE stock >= quantity`: una última unidad solo puede aceptar un pedido. Precio calculado por servidor y restricciones PostgreSQL completan la protección.
- La clave idempotente se vincula a tenant, actor, operación y hash SHA-256. Un advisory lock transaccional serializa peticiones simultáneas; efecto, resultado y outbox comparten commit. Estas claves se conservan sin TTL en esta referencia: producción necesita una política de retención compatible con la ventana de reintento.
- Aprobar incrementa `version`: ejecutar debe usar la versión devuelta por aprobación, no la inicial. La aprobación vence a los diez minutos. Un lock de propuesta, actualización condicional de pedido y transacción impiden dos reposiciones aun usando claves distintas.
- Outbox usa `SKIP LOCKED`, confirmación del broker y contadores persistentes de intentos. Una caída tras envío puede repetir un evento. Inbox y efecto de auditoría comparten transacción con identidad única. El offset se confirma después del listener, dejando segura una repetición posterior al commit.
- Los mensajes inválidos tienen dos reintentos y DLT; el fallo al publicar DLT impide considerarlos resueltos. Reprocesar exige corregir la causa y reenviar el **sobre original**, conservando `eventId`, `aggregateId` y correlación. Un JSON inválido no se corrige mágicamente reintentándolo.
- El contexto de traza HTTP se persiste junto al outbox y se restaura al publicar; Spring Kafka propaga headers W3C. La correlación de negocio permanece también en el sobre. Métricas: `commerce_outbox_pending`, `commerce_audit_processed_total`, `commerce_audit_duplicates_total`.

Pruebas: concurrencia de última unidad, retry concurrente de misma clave, rollback provocado en outbox, aislamiento, vencimiento con reloj controlado, devolución concurrente, validación HTTP, firma JWT/issuer/audience/expiry, broker pausado y recuperación, duplicados y DLT. ArchUnit verifica límites y demuestra que una infracción deliberada es detectada.

Referencias: [locking PostgreSQL](https://www.postgresql.org/docs/current/explicit-locking.html), [resource server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html), [errores y DLT Spring Kafka](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html). No hay transacción distribuida ni promesa de exactamente una entrega.

## Baseline y mantenimiento de dependencias

El análisis de los JAR desplegables encontró avisos que el análisis del POM no había identificado. La referencia pasó de Boot 3.5.16/Spring 6.2.19 a **Boot 4.1.1/Spring 7.0.9**: los parches 6.2.20 de [CVE-2026-47884](https://spring.io/security/cve-2026-47884/) y [CVE-2026-47890](https://spring.io/security/cve-2026-47890/) requieren soporte Enterprise, mientras 7.0.9 está disponible como OSS. Las condiciones de explotación descritas por Spring —vistas XSLT y fragmentos SSE— no forman parte de esta API; esto no sustituye la actualización ni oculta los resultados del scanner.

Se mantienen Java 21, los contratos HTTP y las garantías transaccionales. Se fijan PostgreSQL JDBC 42.7.12, Tomcat 11.0.27 y lz4-java 1.11.4 como actualizaciones de seguridad compatibles. Boot 4 usa Jackson 3.1.7 para HTTP; un bean **Jackson 2.21.7** explícito conserva el codec de eventos/idempotencia y la validación JSON Schema existente. No se depende del módulo de compatibilidad Jackson 2 de Boot, marcado como temporal. Los starters y anotaciones de prueba siguen la [guía oficial de migración](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide).
