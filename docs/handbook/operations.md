# 8. Observar el sistema y diagnosticar fallos

[← Anterior](security.md) · [Índice del manual](../index.md) · [Siguiente →](delivery.md)

El capítulo 7 limitó errores y esperas. Para investigar un fallo real hace falta observar qué ocurrió. Este capítulo relaciona una operación con sus métricas, su traza y sus logs, y muestra cómo actuar ante eventos pendientes.

**Objetivo:** elegir la señal adecuada, seguir una operación entre servicios y distinguir una alerta simulada de una comprobación del sistema completo.

<a id="topic-senales-con-una-pregunta"></a>

## Elegir entre métricas, trazas y logs

Las métricas resumen cantidades y tendencias; las trazas muestran el recorrido de una petición; los logs registran sucesos concretos. Se complementan para responder a preguntas distintas.

Una métrica debe ayudar a decidir algo. `commerce_outbox_pending` responde si hay hechos confirmados todavía sin publicar. Los contadores de auditoría distinguen efectos nuevos y duplicados. Streams expone lag máximo observado, eventos inválidos y descartes por tardanza. AI registra una sola vez cada operación iniciada como `answered`, `abstained`, `error` o `cancelled`, tanto JSON como SSE; HTTP 503 también incrementa errores. La duración y esas etiquetas acotadas permiten distinguir resultados sin identificar usuarios. Estas señales no son equivalentes: lag cero no demuestra que una respuesta sea correcta.

```bash
make observability
make observability-smoke
make demo
```

Grafana permite consultar las señales en una interfaz local: [dashboard](http://localhost:3000/d/architecture-reference), [Prometheus](http://localhost:9090) y Tempo mediante OTel Collector. El dashboard combina tráfico, errores, latencia p95 y resultados de negocio, sin IDs como labels. El identificador de correlación viaja en HTTP y sobre del evento; la traza HTTP se conserva en el outbox para enlazar publicación posterior. Una tarea asíncrona no mantiene mágicamente el contexto de la petición: hay que transportarlo explícitamente.

**Código y configuración:** [configuración y dashboard](../../infra/observability/dashboard.json), [instrumentación HTTP Python](../../apps/ai-service/src/ai_service/main.py) y [registro Java](../../apps/commerce-api/src/main/java/dev/reference/commerce/shared/infrastructure/rest/CorrelationFilter.java). Los logs JSON contienen plantilla de ruta, status, duración y correlación; usar `docker compose logs --since=5m commerce-api ai-service` y buscar el `correlationId`. No se añade Loki: consultar stdout basta para este caso. La latencia HTTP Python termina al enviar cabeceras; la métrica de operación IA incluye el trabajo de la respuesta SSE.

[El escenario](../../scenarios/observability.py) verifica lecturas de métricas de los tres servicios, un incremento nuevo, una traza W3C exacta AI → Commerce y logs JSON correlacionados sin JWT ni cuerpo. Imprime `traceId`: abrir Grafana → Explore → Tempo → Trace ID. No basta encontrar una traza antigua o ver el dashboard vacío.

Un span representa un tramo de trabajo; una traza reúne spans relacionados bajo un traceId. correlationId es el identificador de correlación de la aplicación y no sustituye el contexto de trazado. Propagar contexto permite enlazar el trabajo asíncrono con su origen; el [modelo de OpenTelemetry](https://opentelemetry.io/docs/concepts/context-propagation/) explica esa relación.

El dashboard es diagnóstico local: retención corta, muestreo 100% para la práctica, infraestructura efímera y sin HA. En producción, ajustar muestreo, retención, autenticación y presupuesto según tráfico. El colector limita memoria y exporta por lotes. Las APIs no registran tokens ni cuerpos; al añadir instrumentación revisar atributos y exclusiones para no convertir trazas en otro almacén de secretos.

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

Inspeccionar una muestra acotada de `orders.placed.v1.dlt` o `orders.analytics.v1.dlt` con kafka-console-consumer. Identificar si el error es de contrato, despliegue o dato. Corregir en un archivo local el evento original sin cambiar eventId, tenant ni el hecho de negocio. Validar contra esquema y reprocesar explícitamente:

```bash
uv run --project tools/docs python scripts/replay_event.py --file /ruta/evento-reparado.json --confirm-replay
```

No renombrar identidades para eludir deduplicación. Un evento tardío fuera de grace no debe recuperarse como si hubiera llegado a tiempo: reconstruir una proyección histórica con applicationId y topics separados, después reconciliar. Retención y siete días de deduplicación delimitan qué replay está soportado.

<a id="topic-sli-slo-y-capacidad"></a>

## SLI, SLO y capacidad

SLI es la medida observada, por ejemplo la proporción de operaciones válidas completadas; SLO es el objetivo de esa medida en una ventana temporal acordada. Un presupuesto de error expresa el margen de incumplimiento admitido por ese objetivo. Estos términos no fijan un objetivo de producción para la referencia.

El percentil p95 describe el valor bajo el que cae aproximadamente el 95 % de las observaciones; la media puede ocultar la cola lenta. Prometheus estima percentiles de histogramas con buckets: su precisión depende de esos intervalos y no permite promediar p95 de varios pods como si fueran una media. Véase [histogramas y cuantiles](https://prometheus.io/docs/practices/histograms/).

Cardinalidad es el número de series distintas generadas por combinaciones de etiquetas. `route=/orders/{id}` mantiene un conjunto acotado; etiquetar cada UUID multiplicaría series y expondría identificadores.

Medir éxito de operaciones válidas, latencia y frescura; IA añade fidelidad y abstención correcta. El SLO depende del negocio. Las alertas son umbrales didácticos, sin disponibilidad histórica atribuida.

Antes de escalar, localizar saturación SQL, bloqueos, lag Kafka y cola del proveedor. Más réplicas pueden trasladar el cuello de botella. El coste por tarea completada incluye errores, reintentos y recursos ociosos, no solo tokens.

## Prueba rápida de las reglas de alerta

`make observability-rules-test` ejecuta [series sintéticas](../../infra/observability/alerts.test.yaml) con `promtool test rules`: ambas alertas se mantienen inactivas en condiciones normales, esperan su plazo `for`, disparan con etiquetas/anotaciones previstas y se resuelven al desaparecer la condición. Por ejemplo, un pendiente aparece al minuto uno; `OutboxBacklog` dispara al minuto tres tras dos minutos continuos. No basta un pico aislado.

El comando forma parte de `make verify`, reutiliza la imagen Prometheus fijada en Compose, monta reglas y datos de prueba en solo lectura y ejecuta un contenedor efímero sin red. Requiere Docker y la imagen disponible; para la primera descarga usar `docker compose --profile observability pull prometheus` (requiere red). No arranca servidores. Las pruebas de reglas no acreditan lectura de métricas, entrega de notificaciones ni recuperación real: para señales actuales se conserva `make observability-smoke`. Véase [pruebas unitarias de reglas](https://prometheus.io/docs/prometheus/latest/configuration/unit_testing_rules/).

## Ciclo de vida local

`make down` apaga sin borrar volúmenes. `make up` recupera datos. `make reset CONFIRM=delete-local-data` elimina únicamente volúmenes del proyecto Compose y exige intención explícita. Un reset no sustituye una migración ni una prueba de restauración. Ejecutar kind después de apagar Compose para evitar puertos y memoria competidos.

## Práctica guiada: contar el resultado de cada operación una vez

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

## Documentación del capítulo

[Spring logging estructurado](https://docs.spring.io/spring-boot/reference/features/logging.html), [Tempo API](https://grafana.com/docs/tempo/latest/api_docs/), [OTel configuración](https://opentelemetry.io/docs/collector/configuration/), [Prometheus alertas](https://prometheus.io/docs/prometheus/latest/configuration/alerting_rules/), [SRE SLO](https://sre.google/workbook/implementing-slos/).

<a id="topic-ejemplo-de-observabilidad"></a>

## Para profundizar

La [configuración del OpenTelemetry Demo](https://github.com/open-telemetry/opentelemetry-demo/blob/main/otel-config.yml) muestra un Collector conectado a varias aplicaciones y backends. Es un sistema mucho mayor que este laboratorio; úsalo para seguir la configuración de señales, no como topología mínima recomendada.

## Comprobar lo aprendido

Hay eventos pendientes durante un minuto y después se publican. ¿Debe activarse OutboxBacklog?

<details>
<summary>Ver la explicación</summary>
<p>No. La regla exige que la condición se mantenga durante dos minutos. Un pico más corto no debe activar esa alerta; los datos sintéticos permiten comprobar ese plazo.</p>
</details>

Las señales permiten investigar el comportamiento. El último capítulo utiliza esa base para razonar sobre despliegue, escalado y cambios.

---

[← 7. Aplicar permisos y limitar los fallos](security.md) · [Índice](../index.md) · [9. Desplegar, escalar y entregar cambios →](delivery.md)
