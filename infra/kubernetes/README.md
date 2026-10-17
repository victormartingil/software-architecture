# Kubernetes local con kind

Este entorno reproduce los servicios del Compose en un clúster dedicado de un nodo. Demuestra probes, recursos, volúmenes, rollout, presupuesto de interrupción y escalado de una API stateless. No acredita alta disponibilidad de PostgreSQL, Kafka, Keycloak ni del nodo.

## Arranque y aislamiento

Versiones del laboratorio: kind **0.33.0**, Kubernetes **1.36.4** con digest oficial de la release y kubectl **1.36.x**. Usar `KIND_BIN=/ruta/al/binario make kind-up` permite seleccionar kind sin reemplazar otra instalación. [Release y checksums oficiales](https://github.com/kubernetes-sigs/kind/releases/tag/v0.33.0).

Construir previamente las tres imágenes con Compose y detener ese entorno para liberar los puertos 8080, 8081 y 8088. El script rechaza puertos ocupados al crear el clúster.

El cargador exporta únicamente la plataforma del daemon cuando Docker soporta `save --platform`; evita referencias a blobs de otra arquitectura ausentes en el almacén containerd. Los archivos de transferencia se eliminan tras cargar y las imágenes conservan sus tags locales.

```sh
make down
make kind-up
./scripts/kind.sh status
```

El script utiliza exclusivamente el clúster `architecture-reference` y el archivo `reports/kind.kubeconfig`. Todas las llamadas kubectl especifican también `--context kind-architecture-reference`. No modifica el kubeconfig global ni consulta el contexto activo del usuario. Solo los NodePort declarados se publican en loopback.

`postgres-init` y `keycloak-realm` se generan desde los archivos canónicos de `infra/local` usando `kubectl create configmap --dry-run=client`. Las contraseñas del Secret son sintéticas y públicas para esta demostración. El realm usa password grant para automatizar pruebas locales; una aplicación real usaría Authorization Code con PKCE.

Las APIs están disponibles en las mismas URLs del Compose. El issuer público conserva `http://localhost:8088/realms/reference`; JWKS se resuelve mediante el Service interno. Las imágenes de aplicaciones se cargan desde Docker, con `imagePullPolicy: Never`, evitando descargar una imagen ajena por accidente.

## Probar rollout y HPA

```sh
export KUBECONFIG="$PWD/reports/kind.kubeconfig"
kubectl --context kind-architecture-reference -n architecture-reference get hpa
python3 scripts/kind-load.py --seconds 180 --workers 16
kubectl --context kind-architecture-reference -n architecture-reference get hpa,pods
kubectl --context kind-architecture-reference -n architecture-reference rollout restart deployment/commerce-api
kubectl --context kind-architecture-reference -n architecture-reference rollout status deployment/commerce-api
```

La carga solo hace GET de inventario autenticado: no crea pedidos ni altera stock. El HPA usa CPU, requests de 200m y objetivo de 50%, con rango 1–2. Se ajustó la request tras observar que 50m provocaba escalado incluso con la carga de fondo del laboratorio. El generador reutiliza conexiones y limita globalmente a 300 peticiones/s por defecto; las rota para que reciban tráfico las réplicas nuevas. Estos parámetros requieren medición en cada entorno. El script dura como máximo diez minutos, usa como máximo64 workers y permite `--rps` hasta1000; clasifica los errores y falla si encuentra alguno.

Para registrar el paso de una a dos réplicas, observar el HPA mientras corre la carga. No inferir éxito de la mera existencia del manifiesto: comprobar métricas disponibles, réplicas deseadas y pods listos. `metrics-server` necesita unos ciclos de muestreo tras el arranque. Tras cesar la carga existe una ventana de estabilización de sesenta segundos.

El PDB exige una réplica disponible para interrupciones voluntarias. No garantiza disponibilidad frente a caída del nodo. El rollout usa `maxUnavailable:0` y `maxSurge:1`; hay capacidad para una réplica temporal adicional durante la actualización.

## Persistencia y seguridad de procesos

Las aplicaciones ejecutan como UID/GID 10001 con `fsGroup:10001`, sin privilegios añadidos ni capacidades Linux. AI monta `/data`, Streams `/state`. Readiness y liveness están separadas cuando la aplicación las ofrece; startup probes permiten el primer arranque sin reinicios prematuros.

AI mantiene una réplica y estrategia Recreate: SQLite y el bloqueo en memoria no coordinan escritores entre procesos. Streams también mantiene una réplica con estado local para este laboratorio. Escalar AI exige persistencia compartida y exclusión/idempotencia distribuida, no solamente cambiar el número de réplicas. Kafka y PostgreSQL tienen PVC; reiniciar pods conserva sus datos. El storage provisioner de kind almacena datos dentro del nodo.

```sh
make kind-down
```

Este comando elimina únicamente el clúster de referencia y sus datos. No equivale al `down` no destructivo de Compose. Conservar el clúster para conservar sus volúmenes; exportar datos relevantes antes de eliminarlo.

## Procedencia y limitaciones

`metrics-server.yaml` procede del [release oficial v0.9.0](https://github.com/kubernetes-sigs/metrics-server/releases/tag/v0.9.0), con un único ajuste funcional: `--kubelet-insecure-tls` para los certificados kubelet del clúster local. Esta excepción no es una configuración recomendada para cloud. La licencia Apache 2.0 está en `LICENSE.metrics-server`.

El nodo usa `kindest/node:v1.36.4` con digest fijado; las dependencias mantienen las versiones concretas de Compose. No se instala observabilidad completa en este segundo entorno: la pila Grafana/Tempo/Prometheus se verifica en Compose. No ejecutar Terraform desde este script. Referencias: [configuración kind](https://kind.sigs.k8s.io/docs/user/configuration/), [HPA](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/), [PDB](https://kubernetes.io/docs/tasks/run-application/configure-pdb/).
