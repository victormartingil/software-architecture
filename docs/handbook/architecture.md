# Límites, puertos y patrones

## Problema y decisión

Una regla como «no vender más unidades de las disponibles» debe sobrevivir a un cambio de transporte o base de datos. Una regla de HTTP, en cambio, pertenece al borde del sistema. Separar estas responsabilidades reduce el número de conceptos que hay que entender para modificar cada una.

Commerce agrupa `orders`, `inventory` y `returns`. Dentro de cada módulo, `domain` contiene conceptos e invariantes; `application` coordina casos de uso y transacciones; `infrastructure` traduce HTTP, SQL y Kafka. Un puerto de entrada expresa una operación disponible. Un puerto de salida expresa una capacidad que esa operación necesita. Un adaptador implementa la traducción concreta. «Entrada» y «salida» describen la dirección de la llamada desde el caso de uso, no si el protocolo transmite bytes hacia dentro o fuera.

En [ManageOrderService](../../apps/commerce-api/src/main/java/dev/reference/commerce/orders/application/service/ManageOrderService.java), seguir la operación desde el comando hasta inventario, pedido, idempotencia y outbox. Comparar después [OrderRepository](../../apps/commerce-api/src/main/java/dev/reference/commerce/orders/application/port/output/OrderRepository.java) con su adaptador JDBC. La interfaz representa lo que necesita el negocio; no expone `ResultSet` ni métodos genéricos de un framework.

[![Pedidos: dependencias entre puertos y adaptadores](../diagrams/ports-and-adapters.svg)](../diagrams/ports-and-adapters.svg)

[Maestro draw.io](../diagrams/architecture.drawio). Editar el `.drawio` con draw.io y volver a exportar el SVG.

Las dependencias de código apuntan al núcleo. En ejecución el caso de uso invoca un adaptador mediante su puerto: inversión de dependencias no significa invertir todas las llamadas. Se permite `@Transactional` en aplicación para que la frontera de commit sea fácil de localizar. Es un acoplamiento deliberado a Spring, descrito en el ADR; el dominio sigue independiente.

## Por qué tres servicios

Commerce tiene una única transacción de negocio. Dividir pedidos e inventario en servicios introduciría consistencia eventual y compensaciones sin mejorar este ejemplo. Analytics escala y reconstruye una proyección independiente. AI tiene dependencias, costes y ciclo de ejecución distintos; solo accede a comercio por HTTP. Estas diferencias justifican procesos separados. El tamaño de una clase o una entidad no los justifica por sí solo.

Un monolito modular sería una alternativa razonable para una tienda real pequeña. La referencia separa procesos para poder observar fallos de red, replay y permisos entre sistemas. No propone copiar esta topología en cualquier producto.

## Patrones con una responsabilidad concreta

| Patrón | Dónde | Beneficio y coste |
|---|---|---|
| Adapter | HTTP/JDBC/proveedor IA | Traducción sustituible; exige mantener el contrato |
| Repository | Puertos de datos de comercio | Oculta SQL al caso de uso; evitar repositorio genérico universal |
| Strategy | Recuperación lexical/vector | Mismo objetivo, implementaciones comparables |
| Composición | Ensamblado Spring/FastAPI | Colabora por contratos; evita herencia por reutilización incidental |
| Outbox/Inbox | Publicación/auditoría | Resuelve ventanas de fallo; requiere retención y operación |
| Circuit Breaker | Proveedor IA | Acota fallos repetidos; no corrige una dependencia caída |

Command y Query nombran intenciones; no implican CQRS con almacenes separados. Un evento publicado tampoco implica event sourcing. Factory, Observer o Decorator se explican cuando resuelven un caso concreto; no se añaden capas para demostrar nombres. Para patrones adicionales, consultar el [catálogo de Refactoring.Guru](https://refactoring.guru/design-patterns) y contrastar ejemplos antes de reutilizar código.

## Comprobación y evolución

```bash
cd apps/commerce-api && ./mvnw verify
cd ../ai-service && uv run lint-imports
```

ArchUnit verifica independencia del dominio y restricciones entre capas; import-linter comprueba equivalentes en Python. La prueba negativa introduce una dependencia prohibida en una copia temporal: una regla que nunca falla no acredita un límite. La revisión humana sigue siendo necesaria para detectar un puerto que filtra conceptos de infraestructura o una abstracción sin utilidad.

Fuentes: [arquitectura hexagonal original](https://alistair.cockburn.us/hexagonal-architecture), [ArchUnit](https://www.archunit.org/userguide/html/000_Index.html), [import-linter](https://import-linter.readthedocs.io/en/stable/contract_types/layers/).
