# Contenedores, Kubernetes y Terraform

## Tres niveles distintos de prueba

Compose verifica colaboración local de servicios. kind verifica manifiestos y comportamiento de Kubernetes en un clúster local dedicado. Terraform con mocks verifica forma y decisiones del plan. Ninguno de estos niveles demuestra por sí solo un despliegue cloud correcto o disponible.

Las aplicaciones usan imágenes separadas y usuarios no-root, probes y memoria acotada. Las dependencias locales son deliberadamente simples: un PostgreSQL/pgvector, un broker KRaft y Keycloak de desarrollo. Las bases tienen usuarios distintos por dueño. Los puertos host se enlazan a loopback; no exponer esta configuración a Internet.

## Laboratorio Kubernetes

```bash
make down
make kind-up
make smoke
python3 scripts/kind-load.py
make kind-down
```

El script crea únicamente el clúster `architecture-reference` y conserva su kubeconfig en `reports/`, sin cambiar el contexto global. Namespace, volúmenes e imágenes pertenecen al laboratorio. La carga usa consultas autenticadas de inventario: no crea miles de pedidos para forzar CPU. Verificar el HPA y el resultado real en la evidencia.

Requests permiten al scheduler reservar capacidad; limits acotan consumo. Un probe de vida detecta proceso bloqueado; uno de readiness indica si debe recibir tráfico. No hacer reiniciar todos los pods porque una dependencia temporal cayó: puede amplificar el incidente. PDB ayuda en interrupciones voluntarias, sin proteger frente a fallos de nodo ni sustituir varias réplicas.

Commerce puede escalar de una a dos réplicas porque la idempotencia, stock y transiciones viven en PostgreSQL. El HPA usa métricas de CPU con requests explícitas. Añadir pods no añade CPU al único nodo kind: sobrecargarlo puede producir errores aunque el HPA funcione. El generador limita ritmo, reutiliza conexiones y clasifica fallos para observar el mecanismo sin confundirlo con un benchmark de capacidad. En kind se permite TLS inseguro de kubelet para metrics-server, excepción local documentada. No trasladar esa opción a producción.

AI mantiene una réplica con estrategia Recreate. SQLite y un lock de proceso no coordinan varios pods; un volumen RWO tampoco es un protocolo de exclusión de negocio. Para escalar se requiere estado compartido y control concurrente distribuido. El sistema debe reconocer esta restricción antes de configurar un autoscaler que la viole.

## Fundación cloud

```bash
make terraform-check
```

La [configuración GCP](../../infra/cloud/main.tf) define red, subredes, salida NAT, Artifact Registry, identidad de nodos y GKE Autopilot. Fija CLI y provider. El plano de control limita acceso a un `/32` de operador; nodos privados y Workload Identity forman parte de la base. DB, Kafka gestionado, OIDC, TLS, secretos y sus permisos son entradas o trabajo de despliegue explícito. No se crea un cluster con `apply`.

Las pruebas mock comprueban Autopilot, protección de borrado, nodos privados y rechazo de una red de control abierta. No verifican cuotas, IAM real, conectividad, facturación ni disponibilidad de recursos. Antes de producción harían falta backend de estado remoto con bloqueo, pipelines de plan/aprobación, política de secretos, backup probado y presupuesto. NAT y GKE generan costes incluso sin usuarios.

## Delivery, cambios y reversión

CI ejecuta pruebas, reglas de dependencia, contratos, evaluación offline y documentación. Un scan de dependencias añade señales de vulnerabilidad; no acredita ausencia de vulnerabilidades ni una auditoría de seguridad completa. Actualizar lockfiles en cambios revisables, probar compatibilidad y retirar versiones obsoletas con criterio.

Migraciones deben ser compatibles durante despliegues con versiones mezcladas: añadir antes de eliminar, medir uso y retirar después. Un rollback de imagen no revierte automáticamente una migración destructiva ni un evento ya publicado. Las versiones del contrato y las claves idempotentes necesitan convivir durante el plazo de replay.

La alternativa inicial a Kubernetes es un servicio gestionado de contenedores con datos gestionados. Reduce operación cuando no se necesita el control del cluster. Kubernetes se muestra aquí por sus mecanismos y restricciones, sin declarar que toda aplicación los necesite.

Fuentes: [kind](https://kind.sigs.k8s.io/docs/user/quick-start/), [HPA](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale-walkthrough/), [Terraform mocks](https://developer.hashicorp.com/terraform/language/tests/mocking), [GKE Autopilot](https://cloud.google.com/kubernetes-engine/docs/concepts/autopilot-overview).
