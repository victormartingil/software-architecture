# ADR 003 — Evidencia y proveedor controlado

**Estado:** aceptado. **Contexto:** corpus pequeño, necesidad de permisos y reproducción sin gasto ni credenciales externas.

**Decisión:** lexical top-3 primero; pgvector como variante controlada con filtro tenant y dimensión validada. Proveedor fake identificado por defecto. Responses real optativo, esquema validado, modelo explícito, timeout/intentos/concurrencia/cupo y circuit breaker. Sin evidencia se abstiene antes de llamar al modelo.

**Alternativas:** embeddings como requisito inicial mezclan recuperación y coste del proveedor; un agente con herramientas autónomas añade riesgo sin tarea abierta. Spring AI se reserva para comparación JVM futura de una operación, sin duplicar servicios.

**Consecuencias:** calidad de recuperación y del modelo se miden por separado. Citas estructuradas no prueban fidelidad semántica. El presupuesto USD declarado no es facturación medida; el cap de solicitudes limita un proceso. Una evaluación real requiere precio/modelo/prompt/dataset y consentimiento de gasto.

**Comprobación:** AI-01..08; [recuperación](../handbook/retrieval.md).
