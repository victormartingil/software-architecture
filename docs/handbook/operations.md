# Observar, diagnosticar y recuperar

## Señales con una pregunta

Una métrica debe ayudar a decidir algo. `commerce_outbox_pending` responde si hay hechos confirmados todavía sin publicar. Los contadores de auditoría distinguen efectos nuevos y duplicados. Streams expone lag máximo observado, eventos inválidos y descartes por tardanza. AI registra una sola vez cada operación iniciada como `answered`, `abstained`, `error` o `cancelled`, tanto JSON como SSE; HTTP 503 también incrementa errores. La duración y esas etiquetas acotadas permiten distinguir resultados sin identificar usuarios. Estas señales no son equivalentes: lag cero no demuestra que una respuesta sea correcta.

```bash
make observability
make demo
```

Grafana está en `http://localhost:3000`, Prometheus en `http://localhost:9090`. El dashboard de referencia reúne señales sin IDs de usuario como labels. Tempo recibe trazas mediante OTel Collector. El identificador de correlación viaja en HTTP y sobre del evento; la traza HTTP se conserva en el outbox para enlazar publicación posterior. Una tarea asíncrona no mantiene mágicamente el contexto de la petición: hay que transportarlo explícitamente.

El dashboard es una ayuda de diagnóstico local. La infraestructura de telemetría es efímera y no HA. El colector limita memoria y exporta por lotes. Las APIs no registran tokens ni cuerpos; al añadir instrumentación revisar atributos y exclusiones para no convertir trazas en otro almacén de secretos.

`make observability` activa exportadores y muestreo; el arranque básico mantiene exportación desactivada para no intentar enviar a un colector ausente. Para seguir una operación, consultar su `trace_id` y abrirlo en Grafana → Explore → Tempo:

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

El escenario de fallo automatizado en la integración Java pausa el broker y prueba recuperación. El entorno debe disponer de disco: una dependencia caída por tiempo indefinido puede llenar outbox aunque los pedidos sigan respondiendo.

## Runbook: DLT y mensajes tardíos

Inspeccionar una muestra acotada de `orders.placed.v1.dlt` o `orders.analytics.v1.dlt` con kafka-console-consumer. Identificar si el error es de contrato, despliegue o dato. Corregir en un archivo local el evento original sin cambiar eventId, tenant ni el hecho de negocio. Validar contra schema y reprocesar explícitamente:

```bash
uv run --project tools/docs python scripts/replay_event.py --file /ruta/evento-reparado.json --confirm-replay
```

No renombrar identidades para eludir deduplicación. Un evento tardío fuera de grace no debe recuperarse como si hubiera llegado a tiempo: reconstruir una proyección histórica con applicationId y topics separados, después reconciliar. Retención y siete días de deduplicación delimitan qué replay está soportado.

## SLI, SLO y capacidad

Un servicio real necesita medir éxito de operaciones válidas, latencia y frescura de proyecciones; IA añade fidelidad y abstención correcta. Basar el SLO en necesidad de negocio y medición. Estas alertas son umbrales didácticos, sin disponibilidad histórica atribuida.

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

Fuentes: [OTel configuración](https://opentelemetry.io/docs/collector/configuration/), [Prometheus alertas](https://prometheus.io/docs/prometheus/latest/configuration/alerting_rules/), [SRE SLO](https://sre.google/workbook/implementing-slos/).
