"""Opt-in OTLP tracing. Never capture request/response bodies or credentials."""
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.sdk.resources import Resource
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from opentelemetry.instrumentation.httpx import HTTPXClientInstrumentor


def instrument(app, endpoint: str):
    provider = TracerProvider(resource=Resource.create({'service.name':'ai-service'}))
    provider.add_span_processor(BatchSpanProcessor(
        OTLPSpanExporter(endpoint=endpoint.rstrip('/') + '/v1/traces', timeout=2),
        max_queue_size=256, max_export_batch_size=64))
    FastAPIInstrumentor.instrument_app(app, tracer_provider=provider, excluded_urls='health,metrics',
        http_capture_headers_server_request=[], http_capture_headers_server_response=[])
    HTTPXClientInstrumentor().instrument(tracer_provider=provider)
