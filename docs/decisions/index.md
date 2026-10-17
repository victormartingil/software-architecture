# Registro de decisiones

Decisiones aceptadas para esta referencia. Revisar al cambiar las restricciones, sin borrar la razón original ni conservar propuestas obsoletas como instrucciones actuales.

| ADR | Decisión | Consecuencia y criterio de revisión |
|---|---|---|
| [001](001-boundaries.md) | Monolito modular de negocio, servicios AI/analytics separados | Menos transacciones distribuidas; separar inventario solo con necesidad independiente |
| [002](002-consistency.md) | SQL local + outbox/inbox e idempotencia | Entrega repetible; retención y replay son responsabilidad operativa |
| [003](003-ai-evidence.md) | Baseline lexical, fake explícito y Responses optativo | CI sin coste; calidad de modelo real requiere otra evaluación |
| [004](004-authorization.md) | Backend autoriza efectos y versión aprobada | El workflow coordina sin ser fuente de permisos |
| [005](005-delivery.md) | Compose primero, kind acotado y Terraform mocks | No atribuir HA ni cloud; AI singlewriter explícito |
| [006](006-maintenance.md) | Baseline mantenido y scan de artefactos/transitivas | Actualizar con evidencia; no ocultar avisos por compatibilidad histórica |
