# Arquitectura de software e IA aplicada

Una tienda sintética permite estudiar transacciones, eventos y herramientas de IA con código pequeño y garantías explícitas. Cada tema conecta una decisión, su alternativa y una prueba que puede fallar.

Antes del recorrido técnico, consultar [qué hace cada pieza](repository-design.md#component-map): aplicaciones, módulos, servidores y protocolos.

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
2. [Datos y concurrencia](handbook/transactions.md): invariantes antes de distribución.
3. [Eventos y ventanas](handbook/events.md): entrega, repetición y tiempo.
4. [RAG y evaluación](handbook/retrieval.md): evidencia antes de generación.
5. [Workflow aprobado](handbook/workflows.md): efectos autorizados y reanudación.
6. [Seguridad y resiliencia](handbook/security.md): amenazas y fallos concretos.
7. [Operación](handbook/operations.md) y [delivery](handbook/delivery.md): señales, despliegue y límites.

La [matriz de aceptación](acceptance.md) expresa lo exigido. La [evidencia](verification.md) indica qué se ejecutó realmente y las limitaciones pendientes. El modo `fake` permite verificar integración; no mide calidad de un modelo generativo real. Terraform se valida con mocks, sin crear recursos cloud.

Código e identificadores en inglés; explicación en español. English: Small reference implementations connecting transactional Java, event processing and reliable AI integration, with explicit trade-offs and reproducible checks.
