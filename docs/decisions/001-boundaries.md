# ADR 001 — Límites y dependencias

**Estado:** aceptado. **Contexto:** mostrar invariantes y aislamiento con pocas reglas de dominio, evitando servicios por entidad.

**Decisión:** pedidos, inventario y devoluciones son módulos de Commerce, con puertos input/output y adaptadores JDBC. Se permite Spring transaccional en aplicación; el dominio permanece independiente. AI y analytics tienen procesos propios por sus dependencias, ciclo de vida y modelos de ejecución distintos.

**Alternativas:** monolito completo simplifica operación pero oculta fallos entre procesos; servicios de pedido/inventario añaden reserva distribuida y compensaciones sin necesidad del caso. Un núcleo sin ninguna anotación Spring exigiría wrappers transaccionales adicionales.

Los controladores REST pertenecen al módulo funcional; los adaptadores se organizan por tecnología. Quantity y estados tipados protegen reglas locales, mientras SQL conserva la autoridad concurrente. Los errores del dominio expresan significado y se traducen a HTTP solo en infraestructura. El puerto CommerceGateway usa intenciones de negocio y ReturnResult; su adaptador valida/traduce JSON externo. Se mantienen las peticiones HTTP canónicas y sus respuestas; Jackson3 HTTP deja de convertir silenciosamente cadenas/decimales a enteros. Campos extra y conversión de identificadores de ruta anteriores conservan su tolerancia, documentada en OpenAPI; el cursor nuevo valida UUID completo. Jackson2 de eventos/idempotencia permanece separado.

**Consecuencias:** una transacción protege stock y pedido; hay acoplamiento local deliberado a Spring. ArchUnit/import-linter verifican límites. El dominio Java permite dependencias del JDK y del dominio propio; los puertos output solo se usan en su módulo y la colaboración cruzada usa input. Las reglas reales se reutilizan en negativos identificables, excluyendo fixtures del import productivo. La guarda de ciclos agrupa los módulos funcionales completos orders/inventory/returns; shared y composición quedan fuera de ese grafo, manteniendo sus otras restricciones. Dos contraejemplos verifican el ciclo real y un positivo conserva colaboración acíclica por input y tipos compartidos. No compartir entidades entre Java/Python. Revisar si aparece necesidad real de escalado o propiedad de equipos independientes.

**Comprobación:** ARCH-01..03 y REF-ARCH-01..04; [arquitectura](../handbook/architecture.md).
