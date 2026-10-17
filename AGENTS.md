# Guía del repositorio

## Propósito y estado

Monorepositorio de referencia de arquitectura de software e integración fiable de IA: explicaciones breves, decisiones justificadas e implementaciones pequeñas y reproducibles.

La implementación del plan está autorizada; su estado y evidencia vigentes están en `docs/verification.md`. Para cambios posteriores, conservar contratos, actualizar AC antes de ampliar alcance y verificar la capacidad afectada. No contratar servicios, publicar ni crear infraestructura cloud de pago. El repositorio Java externo es solo referencia de lectura.

## Lectura mínima

1. `README.md`: alcance y navegación.
2. `docs/specification.md` y `docs/acceptance.md`: contratos y criterios canónicos.
3. `docs/verification.md`: evidencia real, revisión y límites.
4. `docs/repository-design.md`, `docs/reference-map.md` y ADR relevantes: estructura y razones, sin reimplementar capacidades ya verificadas.
5. Solo para consultas de contexto privado autorizadas, si existe, `.local/README.md` y los documentos que indique. Nunca incorporar su contenido a entregables públicos.

## Privacidad y alcance

- Todo archivo fuera de `.local/` debe poder publicarse sin datos personales, contratos, credenciales ni fuentes privadas.
- Mantener contexto privado exclusivamente en `.local/`. El material público describe problemas técnicos, decisiones y resultados comprobables.
- `.local/` es contexto privado local, excluido de Git; nunca usar `git add -f`, enlazar su contenido desde el README público ni copiarla a paquetes de publicación. No es almacenamiento cifrado.
- Solo escribir dentro de este repositorio. Las carpetas externas autorizadas como contexto se leen, no se modifican. No importar permisos de envío o publicación de otros proyectos.
- No publicar, contactar personas ni modificar sistemas externos por inferencia del contexto local.

## Estilo y arquitectura

- Español para la explicación inicial; código, identificadores y nombres de archivos en inglés. Bilingüismo completo pendiente de decisión, sin duplicar documentos por defecto.
- Cada unidad explica problema, solución mínima, alternativa, coste o límite y verificación observable.
- Los diagramas tienen un único maestro editable de diez páginas en `docs/diagrams/architecture.drawio`; cada SVG se exporta desde su página y se inserta en su explicación. Actualizar el maestro, reexportar e inspeccionar; no reintroducir un maestro Mermaid de la misma vista. Mantener el límite funcional separado del despliegue y de los sistemas de apoyo.
- Nomenclatura: título funcional y después identificador/tecnología. El mapa canónico está en `docs/repository-design.md#component-map`. Distinguir aplicaciones, módulos internos, bibliotecas, servidores y protocolos; conservar IDs de despliegue y contratos salvo migración explícita.
- Preferir un dominio ficticio compartido y pocos componentes. Introducir Kafka, Kubernetes o un agente solo cuando el caso muestre para qué hacen falta.
- Enseñar corrección, datos, fiabilidad, seguridad, evaluación, costes y comunicación técnica además de frameworks.
- No presentar demos como sistemas listos para producción ni prácticas como soluciones universales.
- Voz pública: profesional, impersonal y concreta. Usar «implementación de referencia», «criterio de diseño» y «caso reproducible». No atribuir experiencia, resultados ni métricas sin evidencia. Mantener visibles alcance, estado y limitaciones técnicas materiales: explicitar un trade-off demuestra criterio.
- Separar dominio, aplicación y adaptadores dentro de módulos funcionales cuando el caso lo justifique. Un árbol global de capas no demuestra vertical slices; no crear interfaces, DTO o mappers sin una responsabilidad concreta.
- Convención Java: módulo funcional con `domain`, `application` e `infrastructure`; `application.port.input` y `application.port.output`. Nombres por intención (`CreateXService`, `CreateXCommand`, `XResult`; `ManageXService` si gestiona un ciclo completo); un puerto puede agrupar operaciones cohesivas de una capacidad. Adaptadores `JdbcXRepositoryAdapter` en la implementación actual; `Jpa`/`Entity` solo si realmente se adopta JPA. No crear interfaces por clase. La especificación vigente prevalece sobre propuestas anteriores.
- RAG no exige una base vectorial; event-driven, CQRS y event sourcing son conceptos distintos. Un proceso multipaso no exige autonomía ni multiagente. Los prompts y filtros no son una frontera de autorización.
- Añadir ArchUnit e import-linter para límites relevantes cuando exista código. Verificar que una infracción intencional hace fallar la comprobación; una prueba vacía no acredita arquitectura.
- Seguridad y fiabilidad se prueban con fallos y abusos concretos; no adoptar plataformas de caos, portales de desarrollo ni nuevas certificaciones por amplitud de temario.
- Antes de implementar, consultar documentación oficial vigente y fijar versiones compatibles. No seleccionar versiones desde esta investigación de mercado.

## Evidencia y mantenimiento

- Distinguir hecho, proyección de una fuente, inferencia e hipótesis. Toda cifra externa lleva fuente directa, periodo, ámbito y limitación.
- Una vacante indexada no se considera vigente sin abrir su fuente o verificar su catálogo oficial. Remoto no demuestra contratación desde cualquier país ni modalidad B2B.
- No convertir correlaciones salariales, porcentajes globales o crecimiento ocupacional en probabilidades individuales.
- Actualizar la fuente canónica y retirar contenido sustituido; no crear diarios, snapshots, resúmenes duplicados ni documentos temporales persistentes. Mantener solo evidencia y decisiones que cambien acciones futuras.
- Conservar desacuerdos materiales y motivos de cambio. Al comparar investigaciones futuras, contrastar fuentes y métodos, no buscar coincidencia de conclusiones.
- Antes de entregar: comprobar enlaces locales, coherencia de fechas, cifras y estados, privacidad y ausencia de archivos superfluos. Antes de publicar, revisar también el contenido y el historial de Git si existen.
- Leer `docs/specification.md` y `docs/acceptance.md` antes de implementar. Actualizar `docs/verification.md` con evidencia real y estado, nunca inferir aprobación por mera existencia de código.
- Revisiones independientes autorizadas: especialistas Java/eventos, Python/IA/seguridad e infraestructura/documentación. Máximo dos rondas de reparación por entrega; no rebajar AC para declarar éxito.

## Continuidad operativa

- Ámbitos idempotentes: `Idempotency.Scope` se codifica como array JSON con versión 2. Flyway V2 bloquea historial V1 ambiguo; nunca vaciar solo claves ni reinterpretarlas. ADR002 describe la parada y el reset sintético completo explícito.
- `make up/demo/down`: núcleo sin clave IA; `make observability` activa exportadores y pila de señales.
- `make demo/smoke` correlaciona el pedido actual con SQL y Kafka; `make kafka-summary ORDER_ID=...` repite esa comprobación. `make kind-smoke` selecciona explícitamente el clúster local.
- `make verify/eval`: pruebas reales, arquitectura, contratos, higiene y documentación; JUnit Python obligatorio tanto localmente como en CI; no aceptar skips de integraciones obligatorias. El script adapta Testcontainers a Docker moderno sin modificar daemon.
- `make kind-up/down`: clúster exclusivo, kubeconfig ignorado; kind0.33.0/node1.36.4. HPA solo Commerce; AI/SQLite mantiene una réplica. No escalar cambiando solo un número.
- `make terraform-check`: CLI1.10.5 en contenedor y mocks, sin apply.
- Baseline Java actual Boot4.1.1/Spring7: mantener configuración OTLP canónica y Jackson2 codec separado de Jackson3 HTTP; ADR006 explica la migración y scanners.
- Live IA siempre explícito: presupuesto declarado y cupos por adaptador/proceso; no son facturación medida. Nunca ejecutar una evaluación de pago por inferencia.
- Informes regenerables, caches y herramientas locales permanecen ignorados; consolidar resultados en la verificación canónica y retirar intentos obsoletos. Una actualización de versiones exige compatibilidad, pruebas pertinentes y revisión de advisories.
