# ADR 005 — Despliegue proporcional y límites de escalado

**Estado:** aceptado. **Contexto:** ejecución completa local con recursos limitados, sin cuenta cloud ni gasto automático.

**Decisión:** Compose es el arranque principal; observabilidad optativa. kind dedicado verifica probes, recursos y HPA Commerce1..2. AI/SQLite y Streams con store local conservan una réplica y Recreate. Terraform fija CLI/provider y valida la fundación GCP con mocks; no `apply`.

**Alternativas:** comenzar por cloud dificulta reproducción y añade costes. Escalar AI mediante HPA sin control distribuido del workflow es incorrecto. Solo sustituir SQLite por PostgreSQL no resuelve carreras: hace falta registro idempotente y transiciones CAS o locks compartidos. Un servicio gestionado de contenedores puede reducir operación en un producto pequeño.

**Consecuencias:** se corrige el requisito preliminar de HPA para ambas APIs antes de desplegar: el autoscaler se aplica a la capacidad realmente segura de escalar. No se declara HA de dependencias ni calidad cloud. El TLS inseguro de metrics-server es una excepción kind, nunca un ajuste productivo.

**Comprobación:** OPS/K8S/TF; [delivery](../handbook/delivery.md).
