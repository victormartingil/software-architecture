# Observar, diagnosticar y recuperar

## Señales con una pregunta

Una métrica debe ayudar a decidir algo. `commerce_outbox_pending` responde si hay hechos confirmados todavía sin publicar. Los contadores de auditoría distinguen efectos nuevos y duplicados. Streams expone lag máximo observado, eventos inválidos y descartes por tardanza. AI distingue respuestas, abstenciones y duración. Estas señales no son equivalentes: lag cero no demuestra que una respuesta sea correcta.

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

```bash
docker compose stop kafka
# Crear pedido autenticado: el commit no depende del broker.
docker compose start kafka
```

El escenario de fallo automatizado en la integración Java pausa el broker y prueba recuperación. El entorno debe disponer de disco: una dependencia caída por tiempo indefinido puede llenar outbox aunque los pedidos sigan respondiendo.

## Runbook: DLT y mensajes tardíos

Inspeccionar una muestra acotada de `orders.placed.v1.dlt` o `orders.analytics.v1.dlt` con kafka-console-consumer. Identificar si el error es de contrato, despliegue o dato. Corregir en un archivo local el evento original sin cambiar eventId, tenant ni el hecho de negocio. Validar contra schema y reprocesar explícitamente:

```bash
uv run --project tools/docs python scripts/replay_event.py --file /ruta/evento-reparado.json --confirm-replay
```

No renombrar identidades para eludir deduplicación. Un evento tardío fuera de grace no debe recuperarse como si hubiera llegado a tiempo: reconstruir una proyección histórica con applicationId y topics separados, después reconciliar. Retención y siete días de deduplicación delimitan qué replay está soportado.

## SLI, SLO y capacidad

Para un servicio real medir fracción de operaciones válidas exitosas, latencia por operación y frescura de la proyección, no solo CPU. Un SLO inicial debe basarse en necesidad de negocio y medición. Aquí las alertas son umbrales didácticos; no se atribuye disponibilidad histórica ni rendimiento industrial. La calidad IA necesita además fidelidad y abstención correcta, evaluadas fuera de los probes.

Antes de optimizar, localizar saturación: cola de conexiones SQL, tiempo de bloqueo, lag Kafka, concurrencia y duración del proveedor. Aumentar réplicas puede trasladar el cuello de botella a la base o al cupo del modelo. El costo por tarea completada necesita incluir errores/reintentos y recursos ociosos, no solo precio por token.

## Ciclo de vida local

`make down` apaga sin borrar volúmenes. `make up` recupera datos. `make reset CONFIRM=delete-local-data` elimina únicamente volúmenes del proyecto Compose y exige intención explícita. Un reset no sustituye una migración ni una prueba de restauración. Ejecutar kind después de apagar Compose para evitar puertos y memoria competidos.

Fuentes: [OTel configuración](https://opentelemetry.io/docs/collector/configuration/), [Prometheus alertas](https://prometheus.io/docs/prometheus/latest/configuration/alerting_rules/), [SRE SLO](https://sre.google/workbook/implementing-slos/).
