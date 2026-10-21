# 9. Desplegar, escalar y entregar cambios

[← Anterior](operations.md) · [Índice de la referencia](../index.md) · [Volver al índice →](../index.md)

El capítulo 8 mostró cómo observar una operación. El último capítulo estudia dónde se ejecuta el sistema y qué hay que comprobar antes de cambiar su despliegue. Se parte de Compose y se comparan las prácticas de Kubernetes y Terraform.

**Objetivo:** distinguir ejecución local, escalado comprobado y validación de un plan de infraestructura sin atribuirles las mismas garantías.

**Recorrido esencial:** entender los tres niveles de comprobación y estudiar [CI, migraciones y reversión](#topic-delivery-cambios-y-reversion). **Laboratorios opcionales:** ejecutar kind/HPA o Terraform; se pueden leer sus límites sin levantarlos. Terminar con el [caso integrador](#topic-caso-integrador), que no necesita infraestructura adicional.

<a id="topic-tres-niveles-distintos-de-prueba"></a>

## Comparar Compose, Kubernetes y Terraform

Kubernetes administra contenedores en un conjunto de máquinas o clúster; cada máquina se denomina nodo. Busca mantener el estado declarado en archivos de configuración, llamados manifiestos. kind permite ejecutar un clúster de aprendizaje dentro de contenedores Docker. Terraform describe recursos de infraestructura como código y calcula qué cambios harían falta para obtenerlos.

Compose verifica colaboración local de servicios. kind verifica manifiestos y comportamiento de Kubernetes en un clúster local dedicado. Terraform con respuestas simuladas (mocks) verifica forma y decisiones del plan. Ninguno de estos niveles demuestra por sí solo un despliegue cloud correcto o disponible.

Las aplicaciones usan imágenes separadas y usuarios sin privilegios de administrador, comprobaciones de salud y memoria limitada. Las dependencias locales son deliberadamente simples: un PostgreSQL/pgvector, un broker Kafka único y Keycloak de desarrollo. Las bases tienen usuarios distintos por dueño. Los puertos publicados solo escuchan en el propio equipo; no exponer esta configuración a Internet.

[![Comparación de despliegue local con Compose y kind](../diagrams/deployment-local.svg)](../diagrams/deployment-local.svg)

Compose ejecuta las tres aplicaciones y sus apoyos en contenedores del equipo, con volúmenes persistentes. kind los distribuye en pods de un clúster local: Commerce admite HPA 1–2; AI y Streams mantienen una réplica y estrategia Recreate. `make down` conserva volúmenes Compose; `make kind-down` elimina el nodo local y sus datos. Las cajas agrupan recursos de despliegue; las conexiones entre aplicaciones están en el [C4](../repository-design.md).

<a id="topic-laboratorio-kubernetes"></a>

## Laboratorio Kubernetes

```bash
make down
make kind-up
make kind-smoke
make kind-scale-check
make kind-down
```

El script crea únicamente el clúster `architecture-reference` y conserva su kubeconfig en `reports/`, sin cambiar el contexto global. Namespace, volúmenes e imágenes pertenecen al laboratorio. La carga usa consultas autenticadas de inventario: no crea miles de pedidos para forzar CPU. El [verificador](../../scripts/kind-scale-check.py) exige transición real de una réplica en reposo a dos bajo GET autenticados: cinco minutos, máximo 300/s y 16 workers. Comprueba CPU, réplicas deseadas/listas y endpoints. No fuerza el escalado; falla si faltan recursos o métricas.

Un namespace agrupa recursos dentro del clúster; kubeconfig indica cómo conectarse a él. En Kubernetes, un pod ejecuta uno o varios contenedores; un Deployment mantiene sus réplicas y un Service dirige tráfico a los pods listos.

Una reserva de recursos (request) declara la cantidad que utiliza Kubernetes para ubicar el pod; en los manifiestos aparece bajo requests. Esa reserva ayuda al planificador (scheduler) a decidir dónde cabe el pod; no es el consumo medido. Un límite de CPU puede provocar throttling (espera forzada para respetar la cuota) y superar el límite de memoria puede terminar el contenedor.

| Comprobación | Pregunta | Consecuencia del fallo |
|---|---|---|
| Readiness | ¿Puede recibir tráfico ahora? | Retirar el pod de los destinos listos del Service |
| Liveness | ¿Debe reiniciarse el contenedor? | Reinicio al superar el umbral configurado |
| Startup, cuando se configura | ¿Terminó el arranque? | Posponer liveness/readiness hasta que pase; reiniciar si agota su margen |

HPA significa Horizontal Pod Autoscaler: ajusta el número de réplicas a partir de métricas configuradas, no crea capacidad de nodo ni garantiza que el estado de la aplicación admita varias réplicas. No hacer reiniciar todos los pods porque una dependencia temporal cayó: puede amplificar el incidente. PDB (PodDisruptionBudget) limita cuántos pods pueden quedar indisponibles en interrupciones voluntarias, sin proteger frente a fallos de nodo ni sustituir varias réplicas.

Commerce puede escalar de una a dos réplicas porque la idempotencia, stock y transiciones viven en PostgreSQL. El HPA usa métricas de CPU con requests explícitas. Añadir pods no añade CPU al único nodo kind: sobrecargarlo puede producir errores aunque el HPA funcione. El generador limita ritmo, reutiliza conexiones y clasifica fallos para observar el mecanismo sin confundirlo con un benchmark de capacidad. En kind se permite TLS inseguro de kubelet para metrics-server, excepción local documentada. No trasladar esa opción a producción.

Después envía el mismo pedido concurrentemente a ambos pods mediante port-forwards distintos. Respuestas iguales y stock reducido una vez prueban idempotencia compartida. Una devolución autorizada restaura stock, conservando auditoría sintética. Cierra carga y port-forwards incluso ante fallo; conserva el clúster. El informe queda en `reports/kind-scale-check.json`. Sin carga, el HPA puede volver a una réplica tras sesenta segundos de estabilización; la prueba no modifica esa política.

AI mantiene una réplica con estrategia Recreate. SQLite y un bloqueo de proceso no coordinan varios pods; un volumen RWO (ReadWriteOnce: lectura/escritura desde un nodo) tampoco es un protocolo de exclusión de negocio. Para escalar se requiere estado compartido y control concurrente distribuido. El sistema debe reconocer esta restricción antes de configurar un autoscaler que la viole.

<a id="topic-fundacion-cloud"></a>

## Infraestructura en Google Cloud

```bash
make terraform-check
```

GCP se refiere a Google Cloud. Para leer el diagrama basta distinguir estas funciones:

| Componente | Función |
|---|---|
| Red privada VPC y salida NAT | La VPC separa la red del proyecto; NAT permite conexiones salientes desde nodos sin IP pública. |
| GKE Autopilot | Servicio gestionado de Kubernetes con gestión de infraestructura a cargo de Google. |
| Artifact Registry | Almacén de imágenes que utilizan los despliegues. |
| IAM | Define qué identidades pueden realizar qué acciones sobre los recursos. |

Un prefijo CIDR indica el rango de direcciones de una red; en IPv4, `/32` identifica una única dirección, aquí la del operador autorizado.

La [configuración GCP](../../infra/cloud/main.tf) define red, subredes, salida NAT, Artifact Registry, identidad de nodos y GKE Autopilot. Fija las versiones de la herramienta y de su proveedor de recursos. El plano de control limita acceso a un `/32` de operador; nodos privados y Workload Identity forman parte de la base. DB, Kafka gestionado, OIDC, TLS, secretos y sus permisos son entradas o trabajo de despliegue explícito. No se crea un clúster con `apply`.

[![Diseño de la infraestructura de Google Cloud: red, GKE e identidades](../diagrams/cloud-foundation.svg)](../diagrams/cloud-foundation.svg)

Esta vista describe configuración comprobada con mocks, no infraestructura desplegada.

El plan propone cambios y `apply` los ejecutaría. El archivo de estado (state) de Terraform registra la correspondencia entre configuración y recursos administrados; no es una copia de seguridad de sus datos. Un mock sustituye respuestas del proveedor para comprobar decisiones sin consultar infraestructura real.

Cuatro pruebas mock comprueban la base y rechazan CIDR abierto, IPv6 e IPv4 inválida. La identidad de nodos recibe el rol mínimo GKE a nivel de proyecto y lectura de imágenes acotada al repositorio; las aserciones verifican intención y ámbito, no un número de roles. No verifican cuotas, IAM real, conectividad, facturación ni disponibilidad de recursos. Antes de producción harían falta backend de estado remoto con bloqueo, pipelines de plan/aprobación, política de secretos, backup probado y presupuesto. NAT y GKE generan costes incluso sin usuarios.

<a id="topic-delivery-cambios-y-reversion"></a>

## Entregar cambios y preparar la reversión

CI ejecuta pruebas, reglas de dependencia, contratos, evaluación offline y documentación. Un análisis de dependencias añade señales de vulnerabilidad; no acredita ausencia de vulnerabilidades ni una auditoría de seguridad completa. Actualizar lockfiles en cambios revisables, probar compatibilidad y retirar versiones obsoletas con criterio.

Si se necesita un despliegue con versiones mezcladas, las migraciones deben ser compatibles: añadir antes de eliminar, medir uso y retirar después. Volver a una imagen anterior no revierte automáticamente una migración destructiva ni un evento ya publicado. Las versiones del contrato y las claves idempotentes necesitan convivir durante el plazo de replay. V2 de idempotencia exige parada de escritores y rechaza datos V1 ambiguos: es una excepción explícita, sin prometer actualización gradual; véase [ADR002](../decisions/002-consistency.md).

<a id="topic-reutilizar-la-referencia-en-un-sistema-real"></a>

## Reutilizar la referencia en un sistema real

Trasladar decisiones y pruebas, no copiar el Compose como despliegue en producción. Fijar requisitos del producto antes de añadir componentes:

| Decisión local | Adaptación y prueba exigida según el producto |
|---|---|
| PostgreSQL/Kafka/OIDC únicos | Servicio gestionado o topología redundante; probar caída y restauración con objetivos acordados de pérdida de datos (RPO) y tiempo de recuperación (RTO). |
| Credenciales sintéticas y HTTP local | Identidad real, TLS y gestor de secretos; verificar permisos mínimos y rotación. |
| SQLite y cupos por proceso | Mantener una réplica si basta; para escalar, estado persistente compartido y concurrencia distribuida probada. |
| HPA CPU y dashboard del laboratorio | Medir carga representativa, saturación, latencia y SLO; dimensionar datos y proveedor, no solo pods. |
| Terraform mock y migración V2 con parada | Revisar plan/IAM reales, estado remoto y procedimiento de reversión; probar migraciones con datos representativos. |

La alternativa inicial a Kubernetes es un servicio gestionado de contenedores con datos gestionados. Reduce operación cuando no se necesita el control del clúster. Kubernetes se muestra aquí por sus mecanismos y restricciones, sin declarar que toda aplicación los necesite.

<a id="topic-tls-en-conexiones-a-postgresql"></a>

## TLS en conexiones a PostgreSQL

TLS (el nombre anterior SSL suele usarse coloquialmente) cifra el canal entre aplicación y PostgreSQL. El cliente debe además verificar la identidad del servidor: en libpq, `sslmode=verify-full` comprueba la cadena de confianza hasta una CA (autoridad certificadora) y la coincidencia del nombre del servidor. `require` exige cifrado, pero no garantiza esa verificación completa. Configurar la opción equivalente en el cliente utilizado y sus certificados de confianza; véase [verificación de certificados y modos de libpq](https://www.postgresql.org/docs/17/libpq-ssl.html#LIBPQ-SSL-PROTECTION).

El laboratorio local no configura TLS para PostgreSQL. TLS no cifra por sí solo los datos almacenados ni reemplaza la autenticación SQL; la [configuración del servidor](https://www.postgresql.org/docs/17/ssl-tcp.html) también debe revisarse antes de un despliegue real.

<a id="practica-guiada-comprobar-permisos-en-un-plan-terraform"></a>
<a id="práctica-guiada-comprobar-permisos-en-un-plan-terraform"></a>

## Comprobación reproducible: comprobar permisos en un plan Terraform

### 1. Seguir el código

Leer [main.tf](../../infra/cloud/main.tf), [variables.tf](../../infra/cloud/variables.tf) y [plan.tftest.hcl](../../infra/cloud/tests/plan.tftest.hcl). Desde la raíz, con Docker:

### 2. Predecir el resultado

¿Basta comprobar cuántos roles IAM tiene una identidad para saber que sus permisos son los adecuados? Anotar una respuesta antes de ejecutar los comandos.

### 3. Ejecutar y comparar

```bash
make terraform-check
```

**Qué observar:** el formato y la validación deben pasar, junto con los cuatro planes con respuestas simuladas, sin credenciales cloud ni `apply`. El fallo controlado introduce direcciones de operador inválidas y exige que las rechace. El plan válido conserva Autopilot, nodos privados y permisos concretos de nodo e imágenes.

**Alternativa:** ejecutar solo `validate` prueba sintaxis y tipos, sin comprobar esa política. Mocks tampoco acreditan IAM efectivo: un despliegue autorizado requeriría pruebas reales. En local, `make kind-smoke` enlaza el pedido de su ejecución con SQL y Kafka del clúster; no reutiliza conexiones Compose. No es necesario levantar kind para esta práctica.

<a id="documentacion-del-capitulo"></a>
<a id="documentación-del-capítulo"></a>

## Documentación de la sección

[kind](https://kind.sigs.k8s.io/docs/user/quick-start/), [HPA](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale-walkthrough/), [probes](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/), [requests y limits](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/), [Terraform mocks](https://developer.hashicorp.com/terraform/language/tests/mocking), [GKE Autopilot](https://cloud.google.com/kubernetes-engine/docs/concepts/autopilot-overview).

<a id="topic-ejemplo-de-kubernetes"></a>

## Para profundizar

Los [manifiestos de Kubernetes de Online Boutique](https://github.com/GoogleCloudPlatform/microservices-demo/tree/99d6025d494fd2ad9cd49a7881d590e7d8a773e5/kubernetes-manifests) permiten comparar un `Deployment` y su `Service`: seguir selector, labels y puertos; después revisar probes y recursos. El demo incluye más servicios y tecnologías; no asumir que su escala o sus decisiones hacen falta aquí.

<a id="topic-caso-integrador"></a>

## Caso integrador: atender más solicitudes del asistente

**Nuevo requisito hipotético:** el asistente recibe más solicitudes y se propone pasar de una a dos réplicas. Decidir si es necesario y qué habría que cambiar para conservar una sola devolución por pedido. No implementar ni desplegar la variante.

**Entregable:** un ADR de una página como máximo y una secuencia de cinco pasos. Incluir necesidad y evidencia de saturación, alternativa más simple, decisión, riesgos y comprobaciones. Usar esta rúbrica; una propuesta solo está completa si responde a todas las filas:

| Criterio | Pregunta de comprobación |
|---|---|
| Autoridad y aislamiento | ¿Quién aprueba, de dónde procede el tenant y quién revalida permisos? |
| Datos y concurrencia | ¿Dónde vive el progreso y cómo se impide que dos procesos cambien a la vez la misma operación? |
| Repetición | ¿Qué clave conserva un reintento después de perder una respuesta? |
| Fallos | ¿Qué ocurre tras confirmar Comercio y antes de guardar el checkpoint? |
| Operación | ¿Qué cola, latencia y errores justificarían escalar, y cómo se limita la carga sobre las dependencias? |
| Evidencia | ¿Qué prueba concurrente, de reinicio y de acceso cruzado podría refutar la propuesta? |

**Pista:** un checkpointer compartido resuelve persistencia, pero no basta para coordinar dos ejecutores. Admitir una sola réplica sigue siendo válido si la necesidad se resuelve de forma más simple y medible.

<details markdown="1">
<summary>Ver una propuesta razonada, no una solución única</summary>

Primero medir cola y latencia: si la dependencia remota limita el caudal, dos réplicas pueden empeorarlo. Mantener una réplica y ajustar límites dentro de su capacidad es una alternativa que debe compararse.

Si se justifican dos ejecutores, conservar progreso durable compartido y un mecanismo de exclusión o transición condicionada por versión que coordine **todo** el cambio de cada workflow entre procesos. Diseñar también el registro idempotente del flujo, los cupos compartidos y la recuperación de un ejecutor caído. No basta añadir una BD ni un lock en cada proceso.

Secuencia posible: (1) autenticar y comprobar propietario/tenant; (2) obtener el derecho exclusivo a avanzar el flujo y leer su estado; (3) consultar y aprobar en Comercio con el token actual del responsable; (4) ejecutar con `execute:returnId`, dejando que Comercio revalide; (5) guardar progreso y liberar la exclusión. Un fallo entre 4 y 5 se recupera consultando Comercio y conservando la clave, sin reponer stock otra vez. Nunca persistir JWT.

Las comprobaciones deben intentar dos reanudaciones simultáneas, pérdida de respuesta, reinicio del ejecutor y acceso de otro tenant. Medir efectos únicos, no solo dos HTTP 200. Esta propuesta es un diseño de ejercicio; la referencia mantiene una réplica y no acredita haber implementado ni probado esta variante.

</details>

<a id="comprobar-lo-aprendido"></a>

## Pregunta de diseño

¿Bastaría cambiar AI de una a dos réplicas para atender el doble de flujos con seguridad?

<details markdown="1">
<summary>Ver la explicación</summary>
<p>No. El bloqueo actual coordina un único proceso y SQLite conserva el progreso. Antes de añadir réplicas harían falta estado compartido y control concurrente entre procesos, con pruebas de sus carreras.</p>
</details>

El recorrido ha unido código, datos, eventos, IA y despliegue. Para repasar una decisión concreta, volver al índice o al mapa de referencia.

---

[← 8. Observar el sistema y diagnosticar fallos](operations.md) · [Índice](../index.md) · [Consultar la referencia →](../index.md)
