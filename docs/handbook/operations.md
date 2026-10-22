# 8. Observar el sistema y diagnosticar fallos

[← Anterior](security.md) · [Índice de la referencia](../index.md) · [Siguiente →](delivery.md)

El capítulo 7 limitó errores y esperas. Para investigar un fallo real hace falta observar qué ocurrió. Este capítulo relaciona una operación con sus métricas, su traza y sus logs, y muestra cómo actuar ante eventos pendientes.

**Objetivo:** elegir la señal adecuada, seguir una operación entre servicios y distinguir una alerta simulada de una comprobación del sistema completo.

<a id="topic-senales-con-una-pregunta"></a>

<a id="ref-topic-r28"></a>

## Elegir entre métricas, trazas y logs

Las métricas resumen cantidades y tendencias; las trazas muestran el recorrido de una petición; los logs registran sucesos concretos. Se complementan para responder a preguntas distintas.

Prometheus recoge y consulta métricas; Tempo almacena y consulta trazas; OpenTelemetry Collector recibe y reenvía telemetría; Grafana permite consultar y visualizar esas señales. Las aplicaciones generan los datos que utilizan estas herramientas.

Una métrica debe ayudar a decidir algo. `commerce_outbox_pending` responde si hay hechos confirmados todavía sin publicar. Los contadores de auditoría distinguen efectos nuevos y duplicados. Streams expone lag máximo observado, eventos inválidos y descartes por tardanza. AI registra una sola vez cada operación iniciada como `answered`, `abstained`, `error` o `cancelled`, tanto JSON como SSE; HTTP 503 también incrementa errores. La duración y esas etiquetas acotadas permiten distinguir resultados sin identificar usuarios. Estas señales no son equivalentes: lag cero no demuestra que una respuesta sea correcta. Un contador crece mientras vive el proceso y puede reiniciarse; una tasa necesita una ventana de observación. Un gauge expresa un valor actual, como pendientes, y un histograma agrupa observaciones de duración. No sumar el último valor acumulado de cada scrape como si fueran eventos nuevos. Antes de copiar una consulta del dashboard, localizar qué tipo de métrica consulta y qué intervalo usa.

```bash
make observability
make observability-smoke
make demo
```

Grafana permite consultar las señales en una interfaz local: [dashboard](http://localhost:3000/d/architecture-reference), [Prometheus](http://localhost:9090) y Tempo mediante OTel Collector. El dashboard combina tráfico, errores, latencia p95 y resultados de negocio, sin IDs como labels. El identificador de correlación viaja en HTTP y sobre del evento; la traza HTTP se conserva en el outbox para enlazar publicación posterior. Una tarea asíncrona no mantiene mágicamente el contexto de la petición: hay que transportarlo explícitamente.

**Código y configuración:** [configuración y dashboard](../../infra/observability/dashboard.json), [instrumentación HTTP Python](../../apps/ai-service/src/ai_service/main.py) y [registro Java](../../apps/commerce-api/src/main/java/dev/reference/commerce/shared/infrastructure/rest/CorrelationFilter.java). Los logs JSON contienen plantilla de ruta, status, duración y correlación; usar `docker compose logs --since=5m commerce-api ai-service` y buscar el `correlationId`. No se añade Loki: consultar stdout basta para este caso. La latencia HTTP Python termina al enviar cabeceras; la métrica de operación IA incluye el trabajo de la respuesta SSE.

[El escenario](../../scenarios/observability.py) verifica lecturas de métricas de los tres servicios, un incremento nuevo, una traza W3C exacta AI → Commerce y logs JSON correlacionados sin JWT ni cuerpo. Imprime `traceId`: abrir Grafana → Explore → Tempo → Trace ID. No basta encontrar una traza antigua o ver el dashboard vacío.

Un span representa un tramo de trabajo; una traza reúne spans relacionados bajo un traceId. correlationId es el identificador de correlación de la aplicación y no sustituye el contexto de trazado. Propagar contexto permite enlazar el trabajo asíncrono con su origen; el [modelo de OpenTelemetry](https://opentelemetry.io/docs/concepts/context-propagation/) explica esa relación.

El dashboard es diagnóstico local: retención corta, muestreo 100% para la práctica, infraestructura efímera y sin alta disponibilidad (HA): no hay réplicas de esas herramientas que mantengan el servicio ante su caída. En producción, ajustar muestreo, retención, autenticación y presupuesto según tráfico. El colector limita memoria y exporta por lotes. Las APIs no registran tokens ni cuerpos; al añadir instrumentación revisar atributos y exclusiones para no convertir trazas en otro almacén de secretos.

`make observability` activa exportadores; el arranque básico los mantiene desactivados. También puede recuperarse el `trace_id` persistido con el evento:

```bash
docker compose exec postgres psql -U commerce -d commerce -c 'SELECT event_id, trace_id, published_at FROM outbox LIMIT 5'
```

## Procedimiento de recuperación: outbox pendiente

1. Confirmar que el incremento persiste y no es solo el intervalo normal de publicación. La regla Prometheus espera dos minutos.
2. Revisar `docker compose ps` y `docker compose logs --tail=80 kafka commerce-api`. No copiar logs completos sin revisar datos.
3. Comprobar conectividad del broker y errores de publicación; no borrar outbox para limpiar el dashboard.
4. Recuperar broker y observar que los pendientes disminuyen y la auditoría recibe efectos. Una publicación repetida es admisible; un efecto duplicado es un defecto.

Con el núcleo arrancado (`make up`) y al menos una unidad disponible, ejecutar desde la raíz. El ejemplo reutiliza el cliente stdlib de la demostración, obtiene un JWT sin imprimirlo y restaura el broker incluso si falla el pedido:

```bash
python3 - <<'PYTHON'
import subprocess
import sys
from uuid import uuid4
sys.path.insert(0, "scenarios")
from smoke import token, request, COMMERCE
from event_flow import verify_event_flow
actor = token("alice")
subprocess.run(["docker", "compose", "stop", "kafka"], check=True)
try:
    order = request(COMMERCE, "/api/v1/orders", "POST",
                    {"sku": "BOOK-1", "quantity": 1}, actor,
                    "outage-" + str(uuid4()), expected=201)
    subprocess.run(["docker", "compose", "exec", "-T", "postgres", "psql",
                    "-U", "commerce", "-d", "commerce", "-c",
                    "SELECT count(*) FROM outbox WHERE published_at IS NULL"], check=True)
finally:
    subprocess.run(["docker", "compose", "start", "kafka"], check=True)
print(verify_event_flow(order["id"]))
PYTHON
```

Se espera 201 aunque Kafka esté detenido, al menos un pendiente y después verificación del pedido actual en outbox, inbox y resumen. Este ejercicio reserva una unidad; no completa una devolución ni restaura stock. No ejecuta `make demo` con Kafka parado porque ese escenario sí espera el resumen.

La integración Java automatiza el fallo y recuperación. Vigilar disco: una caída prolongada puede llenar outbox aunque los pedidos sigan respondiendo.

## Procedimiento de recuperación: DLT y mensajes tardíos

Con el núcleo arrancado (`make up`), leer como máximo un mensaje de la DLT de auditoría, sin utilizar su grupo de consumidores:

```bash
docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 --topic orders.placed.v1.dlt \
  --from-beginning --max-messages 1 --timeout-ms 5000 \
  --consumer-property enable.auto.commit=false \
  --property print.headers=true --property print.key=true
```

Si no hay mensajes, el consumidor termina por timeout; no significa que haya fallado la aplicación. Para Analytics, sustituir el topic por `orders.analytics.v1.dlt` y añadir `--isolation-level read_committed`. La muestra puede ser histórica: identificar evento y causa antes de actuar, sin presentarla como fallo de la operación actual.

El valor contiene el evento original. En auditoría, los headers añadidos por Spring pueden aportar origen y excepción; Analytics conserva el valor sin añadir ese diagnóstico. No copiar headers ni clave Kafka dentro del JSON de reproceso. Por ejemplo, `data.quantity=0` incumple el rango 1..100. No cambiarlo a 1 por conveniencia: solo repararlo si el hecho confirmado demuestra que esa era la cantidad real; si la causa fue un consumidor incompatible, corregir primero el consumidor. Una DLT vacía no requiere fabricar un incidente para continuar el recorrido.

Guardar **solo el JSON completo del evento** en `reports/repaired-event.json`, conservando eventId, tenant y hecho de negocio. El siguiente comando valida el esquema y después publica; ejecutarlo únicamente tras investigar la causa y decidir el reproceso:

```bash
uv run --project tools/docs python scripts/replay_event.py --file reports/repaired-event.json --confirm-replay
```

El [esquema](../../contracts/events/orders.placed.v1.schema.json) y el [ejemplo completo de sobre](../../contracts/events/fixtures/orders.placed.v1.json) ayudan a reconocer la forma; el ejemplo no sustituye el evento investigado. Las [opciones del consumidor Kafka 4.0.2](https://github.com/apache/kafka/blob/4.0.2/tools/src/main/java/org/apache/kafka/tools/consumer/ConsoleConsumerOptions.java) documentan el límite de mensajes, timeout y lectura de headers.

No renombrar identidades para eludir deduplicación. Un evento tardío fuera de grace no debe recuperarse como si hubiera llegado a tiempo: reconstruir una proyección histórica con applicationId y topics separados, después reconciliar. Retención y siete días de deduplicación delimitan qué replay está soportado.

<a id="topic-sli-slo-y-capacidad"></a>

## SLI, SLO y capacidad

SLI es la medida observada, por ejemplo la proporción de operaciones válidas completadas; SLO es el objetivo de esa medida en una ventana temporal acordada. Un presupuesto de error expresa el margen de incumplimiento admitido por ese objetivo. Como ejemplo hipotético, un SLO del 99 % de éxito sobre 1000 operaciones válidas permite hasta 10 fallos en la ventana acordada. No es un objetivo medido ni establecido para esta referencia.

El percentil p95 describe el valor bajo el que cae aproximadamente el 95 % de las observaciones; la media puede ocultar la cola lenta. Prometheus estima percentiles de histogramas con buckets: su precisión depende de esos intervalos y no permite promediar p95 de varios pods como si fueran una media. Véase [histogramas y cuantiles](https://prometheus.io/docs/practices/histograms/).

Cardinalidad es el número de series distintas generadas por combinaciones de etiquetas. `route=/orders/{id}` mantiene un conjunto acotado; etiquetar cada UUID multiplicaría series y expondría identificadores.

Medir éxito de operaciones válidas, latencia y frescura; IA añade fidelidad y abstención correcta. El SLO depende del negocio. Las alertas son umbrales didácticos, sin disponibilidad histórica atribuida.

Antes de escalar, localizar saturación SQL, bloqueos, lag Kafka y cola del proveedor. Más réplicas pueden trasladar el cuello de botella. El coste por tarea completada incluye errores, reintentos y recursos ociosos, no solo tokens.

<a id="ref-topic-r30"></a>

## Prueba rápida de las reglas de alerta

`make observability-rules-test` ejecuta [series sintéticas](../../infra/observability/alerts.test.yaml) con `promtool test rules`: ambas alertas se mantienen inactivas en condiciones normales, esperan su plazo `for`, disparan con etiquetas/anotaciones previstas y se resuelven al desaparecer la condición. Por ejemplo, un pendiente aparece al minuto uno; `OutboxBacklog` dispara al minuto tres tras dos minutos continuos. No basta un pico aislado.

El comando forma parte de `make verify`, reutiliza la imagen Prometheus fijada en Compose, monta reglas y datos de prueba en solo lectura y ejecuta un contenedor efímero sin red. Requiere Docker y la imagen disponible; para la primera descarga usar `docker compose --profile observability pull prometheus` (requiere red). No arranca servidores. Las pruebas de reglas no acreditan lectura de métricas, entrega de notificaciones ni recuperación real: para señales actuales se conserva `make observability-smoke`. Véase [pruebas unitarias de reglas](https://prometheus.io/docs/prometheus/latest/configuration/unit_testing_rules/).

<a id="ref-topic-r35"></a>

## Ciclo de vida local

`make down` apaga sin borrar volúmenes. `make up` recupera datos. `make reset CONFIRM=delete-local-data` elimina únicamente volúmenes del proyecto Compose y exige intención explícita. Un reset no sustituye una migración ni una prueba de restauración. Ejecutar kind después de apagar Compose para evitar puertos y memoria competidos.

Dentro de Analytics, [StreamsLifecycle](../../apps/order-analytics/src/main/java/dev/reference/analytics/StreamsLifecycle.java) coordina callbacks de Kafka y el hilo que arranca o cierra el proceso. `synchronized` protege el estado compartido; `CompletableFuture` comunica que llegó a RUNNING o falló, con espera de arranque acotada. Al cerrar se retira readiness antes de esperar a Kafka, sin retener el monitor: un callback que necesitara ese monitor podría impedir terminar el cierre. `volatile` hace visible el fallo de cierre entre hilos; no convierte varias operaciones en una transacción. [StreamsLifecycleTest](../../apps/order-analytics/src/test/java/dev/reference/analytics/StreamsLifecycleTest.java) comprueba callbacks cruzados, rebalance y plazos. Es el caso concreto para consultar concurrencia en [Core Java I](../bibliography.md#refs-r35).

<a id="practica-guiada-contar-el-resultado-de-cada-operacion-una-vez"></a>
<a id="práctica-guiada-contar-el-resultado-de-cada-operación-una-vez"></a>

## Comprobación reproducible: contar el resultado de cada operación una vez

### 1. Seguir el código

Leer [main.py](../../apps/ai-service/src/ai_service/main.py), [test_api.py](../../apps/ai-service/tests/test_api.py) y [dashboard.json](../../infra/observability/dashboard.json). Desde la raíz:

### 2. Predecir el resultado

Una respuesta SSE comienza con HTTP 200 y termina con un error. ¿Basta contar los códigos HTTP para medir respuestas correctas? Anotar una respuesta antes de ejecutar los comandos.

### 3. Ejecutar y comparar

```bash
(cd apps/ai-service && uv run pytest tests/test_api.py \
  -k 'every_started_answer_records_exactly_one_outcome or sse_disconnect_cancels_provider_task' -q)
```

**Qué observar:** las pruebas JSON/SSE deben cubrir errores, vencimiento de plazo, respuesta, abstención y cancelación; cada operación iniciada incrementa exactamente un resultado. No necesita Docker ni proveedor real. `make verify` conserva además JUnit local igual que CI y rechaza pruebas omitidas.

**Alternativa:** contar solo HTTP 2xx oculta abstenciones y errores SSE posteriores al inicio de la respuesta. Elegir el SLI según resultado de negocio, sin prometer disponibilidad desde este laboratorio.

<a id="documentacion-del-capitulo"></a>
<a id="documentación-del-capítulo"></a>

## Documentación de la sección

[Spring logging estructurado](https://docs.spring.io/spring-boot/reference/features/logging.html), [Tempo API](https://grafana.com/docs/tempo/latest/api_docs/), [OTel configuración](https://opentelemetry.io/docs/collector/configuration/), [Prometheus alertas](https://prometheus.io/docs/prometheus/latest/configuration/alerting_rules/), [SRE SLO](https://sre.google/workbook/implementing-slos/).

<a id="topic-ejemplo-de-observabilidad"></a>

<a id="ref-topic-r29"></a>

## Para profundizar

**Primero:** [Learning OpenTelemetry, capítulo 8](../bibliography.md#book-b6), para seguir el pipeline del Collector. **Alternativa:** [Prometheus: Up & Running, 2.ª, capítulo 18](../bibliography.md#book-b14), para relacionar reglas y estados de alerta. Terminado cuando se explica qué comprueba una serie sintética y qué exige observar una operación actual. [Correspondencias de observabilidad](../bibliography.md#chapters-operations).

La [configuración del OpenTelemetry Demo](https://github.com/open-telemetry/opentelemetry-demo/blob/3ee33db6f17a7482f63553c514c81c53ba8a696f/src/otel-collector/otelcol-config.yml) permite seguir receptores, procesadores y exportadores hasta los pipelines de señales. Comparar ese recorrido con `infra/observability/collector.yaml`: ¿qué recibe y a dónde reenvía cada uno? Es un sistema mucho mayor que este laboratorio; úsalo para seguir la configuración de señales, no como topología mínima recomendada.

<a id="comprobar-lo-aprendido"></a>

## Pregunta de diseño

Hay eventos pendientes durante un minuto y después se publican. ¿Debe activarse OutboxBacklog?

<details markdown="1">
<summary>Ver la explicación</summary>
<p>No. La regla exige que la condición se mantenga durante dos minutos. Un pico más corto no debe activar esa alerta; los datos sintéticos permiten comprobar ese plazo.</p>
</details>

Las señales permiten investigar el comportamiento. El último capítulo utiliza esa base para razonar sobre despliegue, escalado y cambios.

---

[← 7. Aplicar permisos y limitar los fallos](security.md) · [Índice](../index.md) · [9. Desplegar, escalar y entregar cambios →](delivery.md)
