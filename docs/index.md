# Arquitectura de software e IA aplicada

Una tienda sintética permite estudiar transacciones, eventos y herramientas de IA con código pequeño y garantías explícitas. Cada tema conecta una decisión, su alternativa y una prueba que puede fallar.

Antes del recorrido técnico, consultar [qué hace cada pieza](repository-design.md#component-map): aplicaciones, módulos, servidores y protocolos.

Los diagramas están integrados en cada tema. El [índice de vistas](reference-map.md#vistas-visuales) permite elegir contexto, estructura, datos, comportamiento o despliegue sin mezclar sus niveles.

## Punto de entrada

Desde la raíz del repositorio, con Docker Compose, Java 21, Python 3.12 y uv:

```bash
make up
make demo
make down
```

`up` construye y espera servicios; la demo obtiene tokens de usuarios sintéticos, crea un pedido, verifica idempotencia y permisos, consulta políticas y ejecuta una devolución aprobada. No necesita clave de IA. La primera construcción descarga dependencias e imágenes. Reservar 10 GiB a Docker para núcleo y observabilidad; ejecutar kind después de apagar Compose.

## Recorrido recomendado

1. [Límites y patrones](handbook/architecture.md): responsabilidades y dependencias.
2. [Contratos HTTP](handbook/api-contracts.md): errores semánticos, OpenAPI y paginación acotada.
3. [Datos y concurrencia](handbook/transactions.md): invariantes antes de distribución.
4. [Eventos y ventanas](handbook/events.md): entrega, repetición y tiempo.
5. [RAG y evaluación](handbook/retrieval.md): evidencia antes de generación.
6. [Workflow aprobado](handbook/workflows.md): efectos autorizados y reanudación.
7. [Seguridad y resiliencia](handbook/security.md): amenazas y fallos concretos.
8. [Operación](handbook/operations.md) y [delivery](handbook/delivery.md): señales, despliegue y límites.

## Cuándo aplicar cada mecanismo

| Necesidad | Por qué y cómo se resuelve |
|---|---|
| Cambiar transporte o persistencia | [Puertos por responsabilidad](handbook/architecture.md), sin interfaces por clase. |
| Mantener una integración compatible | [Contrato HTTP y errores](handbook/api-contracts.md), verificados contra respuestas reales. |
| Proteger stock o una devolución | [Transacción y constraints](handbook/transactions.md), antes de distribuir servicios. |
| Publicar un hecho tras guardar | [Outbox/inbox](handbook/events.md), admitiendo entregas repetidas y fallos. |
| Responder con documentos autorizados | [RAG y evaluación](handbook/retrieval.md), con abstención y límites de proveedor. |
| Coordinar un efecto aprobado | [Workflow persistente](handbook/workflows.md), sin delegar autoridad a la IA. |
| Defender fronteras de confianza | [Identidad y autorización](handbook/security.md), con negativos ejecutables. |
| Explicar un fallo o una degradación | [Métricas, trazas y logs](handbook/operations.md), correlacionados sin contenido sensible. |
| Escalar una capacidad medida | [HPA y entrega](handbook/delivery.md), comprobando estado compartido e idempotencia. |

Cada ficha enlaza la implementación, una prueba, una alternativa y su límite. El [mapa por pregunta](reference-map.md) lleva directamente al código; la presencia de una herramienta no obliga a usarla en otro sistema.

La [matriz de aceptación](acceptance.md) expresa lo exigido. La [evidencia](verification.md) indica qué se ejecutó realmente y las limitaciones pendientes. El modo `fake` permite verificar integración; no mide calidad de un modelo generativo real. Terraform se valida con mocks, sin crear recursos cloud.

Código e identificadores en inglés; explicación en español. English: Small reference implementations connecting transactional Java, event processing and reliable AI integration, with explicit trade-offs and reproducible checks.
