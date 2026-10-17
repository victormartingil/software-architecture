# Observar, diagnosticar y recuperar

## Señales con una pregunta

Una métrica debe ayudar a decidir algo. `commerce_outbox_pending` responde si hay hechos confirmados todavía sin publicar. Los contadores de auditoría distinguen efectos nuevos y duplicados. Streams expone lag máximo observado, eventos inválidos y descartes por tardanza. AI registra una sola vez cada operación iniciada como `answered`, `abstained`, `error` o `cancelled`, tanto JSON como SSE; HTTP 503 también incrementa errores. La duración y esas etiquetas acotadas permiten distinguir resultados sin identificar usuarios. Estas señales no son equivalentes: lag cero no demuestra que una respuesta sea correcta.

```bash
make observability
make observability-smoke
make demo
```

**Cuándo y por qué:** métricas para detectar tendencias, trazas para localizar el salto lento y logs para diagnosticar una operación concreta. Grafana ofrece una experiencia local similar a las vistas de un APM como Datadog, sin sus agentes ni servicios de pago: [dashboard](http://localhost:3000/d/architecture-reference), [Prometheus](http://localhost:9090) y Tempo mediante OTel Collector. El dashboard combina tráfico, errores, latencia p95 y resultados de negocio, sin IDs como labels. El identificador de correlación viaja en HTTP y sobre del evento; la traza HTTP se conserva en el outbox para enlazar publicación posterior. Una tarea asíncrona no mantiene mágicamente el contexto de la petición: hay que transportarlo explícitamente.

**Cómo:** [configuración y dashboard](../../infra/observability/dashboard.json), [instrumentación HTTP Python](../../apps/ai-service/src/ai_service/main.py) y [registro Java](../../apps/commerce-api/src/main/java/dev/reference/commerce/shared/infrastructure/rest/CorrelationFilter.java). Los logs JSON contienen ruta plantilla, status, duración y correlación; usar `docker compose logs --since=5m commerce-api ai-service` y buscar el `correlationId`. No se añade Loki: consultar stdout basta para este caso. La latencia HTTP Python termina al enviar cabeceras; la métrica de operación IA incluye el trabajo de la respuesta SSE.

[El escenario](../../scenarios/observability.py) verifica scrapes de los tres servicios, un incremento nuevo, una traza W3C exacta AI → Commerce y logs JSON correlacionados sin JWT ni cuerpo. Imprime `traceId`: abrir Grafana → Explore → Tempo → Trace ID. No basta encontrar una traza antigua o ver el dashboard vacío.

El dashboard es diagnóstico local: retención corta, muestreo 100% para la práctica, infraestructura efímera y sin HA. En producción, ajustar muestreo, retención, autenticación y presupuesto según tráfico. El colector limita memoria y exporta por lotes. Las APIs no registran tokens ni cuerpos; al añadir instrumentación revisar atributos y exclusiones para no convertir trazas en otro almacén de secretos.

`make observability` activa exportadores; el arranque básico los mantiene desactivados. También puede recuperarse el `trace_id` persistido con el evento:

```bash
docker compose exec postgres psql -U commerce -d commerce -c 'SELECT event_id, trace_id, published_at FROM outbox LIMIT 5'
```

## Runbook: outbox pendiente

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

## Runbook: DLT y mensajes tardíos

Inspeccionar una muestra acotada de `orders.placed.v1.dlt` o `orders.analytics.v1.dlt` con kafka-console-consumer. Identificar si el error es de contrato, despliegue o dato. Corregir en un archivo local el evento original sin cambiar eventId, tenant ni el hecho de negocio. Validar contra schema y reprocesar explícitamente:

```bash
uv run --project tools/docs python scripts/replay_event.py --file /ruta/evento-reparado.json --confirm-replay
```

No renombrar identidades para eludir deduplicación. Un evento tardío fuera de grace no debe recuperarse como si hubiera llegado a tiempo: reconstruir una proyección histórica con applicationId y topics separados, después reconciliar. Retención y siete días de deduplicación delimitan qué replay está soportado.

## SLI, SLO y capacidad

Medir éxito de operaciones válidas, latencia y frescura; IA añade fidelidad y abstención correcta. El SLO depende del negocio. Las alertas son umbrales didácticos, sin disponibilidad histórica atribuida.

Antes de escalar, localizar saturación SQL, bloqueos, lag Kafka y cola del proveedor. Más réplicas pueden trasladar el cuello de botella. El coste por tarea completada incluye errores, reintentos y recursos ociosos, no solo tokens.

## Ciclo de vida local

`make down` apaga sin borrar volúmenes. `make up` recupera datos. `make reset CONFIRM=delete-local-data` elimina únicamente volúmenes del proyecto Compose y exige intención explícita. Un reset no sustituye una migración ni una prueba de restauración. Ejecutar kind después de apagar Compose para evitar puertos y memoria competidos.

## Micropráctica: un error visible una sola vez

**Pregunta:** ¿un circuito abierto desaparece del dashboard o cuenta dos veces en SSE? Leer [main.py](../../apps/ai-service/src/ai_service/main.py), [test_api.py](../../apps/ai-service/tests/test_api.py) y [dashboard.json](../../infra/observability/dashboard.json). Desde la raíz:

```bash
(cd apps/ai-service && uv run pytest tests/test_api.py \
  -k 'every_started_answer_records_exactly_one_outcome or sse_disconnect_cancels_provider_task' -q)
```

**Resultado:** casos JSON/SSE aprobados, con errores, timeout, respuesta, abstención y cancelación controlados; cada operación iniciada incrementa exactamente un resultado. No necesita Docker ni proveedor real. `make verify` conserva además JUnit local igual que CI y rechaza pruebas omitidas.

**Alternativa:** contar solo HTTP 2xx oculta abstenciones y errores SSE posteriores al inicio de la respuesta. Elegir el SLI según resultado de negocio, sin prometer disponibilidad desde este laboratorio.

Fuentes: [Spring logging estructurado](https://docs.spring.io/spring-boot/reference/features/logging.html), [Tempo API](https://grafana.com/docs/tempo/latest/api_docs/), [OTel configuración](https://opentelemetry.io/docs/collector/configuration/), [Prometheus alertas](https://prometheus.io/docs/prometheus/latest/configuration/alerting_rules/), [SRE SLO](https://sre.google/workbook/implementing-slos/).
