# Fundación GCP, sin despliegue automático

`make terraform-check` fija Terraform 1.10.5 y Google provider 6.30.0. Ejecuta formato, inicialización sin backend, validación y planes con proveedor simulado. No requiere cuenta cloud ni llama al proveedor real durante los tests. `init` descarga el proveedor y conserva su checksum en el lockfile.

Incluye VPC/subredes, NAT para salida de nodos privados, Artifact Registry y GKE Autopilot con IAM de nodos. La cuenta de nodos recibe `roles/container.defaultNodeServiceAccount` en el proyecto; `roles/artifactregistry.reader` se limita al repositorio de imágenes. Las aserciones comprueban los roles efectivos y su alcance, no solo cantidades. El enlace a la cuenta de nodos se revisa en el código: Terraform 1.10 deja su email calculado como desconocido durante plan; no se atribuye validación IAM real al mock. `operator_cidr` acepta únicamente IPv4 /32; se rechazan IPv6, direcciones inválidas y una red abierta. La identidad de aplicaciones se debe vincular por Workload Identity por servicio; no se conceden permisos de negocio globales. DB, Kafka, identidad OIDC, DNS/TLS y gestión de secretos son dependencias externas. Los manifiestos kind son exclusivamente locales y no constituyen un overlay productivo.

Antes de un despliegue real: habilitar APIs, backend remoto cifrado y bloqueo, acceso privado a datos, TLS/SASL Kafka, secret manager, políticas de red, backups/restauración, cuotas/presupuesto y aprobación de costes. Revisar IAM con el proyecto destino. GKE, NAT y almacenamiento generan cargos incluso sin tráfico. No ejecutar `apply` como parte de esta referencia.

Los tests de plan comprueban decisiones declaradas; no acreditan permisos, conectividad, disponibilidad de capacidad ni éxito de un despliegue. [Terraform mocks](https://developer.hashicorp.com/terraform/language/tests/mocking), [GKE Autopilot](https://cloud.google.com/kubernetes-engine/docs/concepts/autopilot-overview).

Fuente de permisos mínimos: [cuentas de nodos GKE](https://docs.cloud.google.com/kubernetes-engine/docs/how-to/service-accounts).
