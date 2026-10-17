# ADR 004 — Efectos aprobados y revalidados

**Estado:** aceptado. **Contexto:** checkpoints y LLM no deben convertir contenido recuperado en permiso de negocio.

**Decisión:** validar OIDC en ambas APIs y derivar actor/tenant del JWT. Solo MANAGER del tenant aprueba versión y contenido, con caducidad. Comercio revalida al ejecutar, mantiene idempotencia y registra efecto local. Workflow no persiste JWT y usa clave estable. Renovar una aprobación exige otra petición explícita del manager.

**Alternativas:** aprobación solo en UI o en prompt se elude llamando al backend. Guardar JWT en checkpoint facilita reanudación automática a costa de persistir credenciales. Una propuesta mutable permitiría ejecutar contenido no revisado.

**Consecuencias:** no hay autonomía de devolución; se requiere presencia de un actor autorizado. Reiniciar o perder respuesta no concede nuevos permisos. Si caduca entre aprobar y ejecutar se devuelve conflicto. Datos ajenos producen 404.

**Comprobación:** RET/WF/SEC; [workflow](../handbook/workflows.md).
