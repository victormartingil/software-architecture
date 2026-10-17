# ADR 001 — Límites y dependencias

**Estado:** aceptado. **Contexto:** mostrar invariantes y aislamiento con pocas reglas de dominio, evitando servicios por entidad.

**Decisión:** pedidos, inventario y devoluciones son módulos de Commerce, con puertos input/output y adaptadores JDBC. Se permite Spring transaccional en aplicación; el dominio permanece independiente. AI y analytics tienen procesos propios por sus dependencias, ciclo de vida y modelos de ejecución distintos.

**Alternativas:** monolito completo simplifica operación pero oculta fallos entre procesos; servicios de pedido/inventario añaden reserva distribuida y compensaciones sin necesidad del caso. Un núcleo sin ninguna anotación Spring exigiría wrappers transaccionales adicionales.

**Consecuencias:** una transacción protege stock y pedido; hay acoplamiento local deliberado a Spring. ArchUnit/import-linter verifican límites. No compartir entidades entre Java/Python. Revisar si aparece necesidad real de escalado o propiedad de equipos independientes.

**Comprobación:** ARCH-01..03; [arquitectura](../handbook/architecture.md).
