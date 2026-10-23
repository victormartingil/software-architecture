"""Opt-in OTLP tracing with a bounded, technical export vocabulary."""
from opentelemetry.sdk.trace import Event, ReadableSpan, TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor, SpanExporter
from opentelemetry.sdk.resources import Resource
from opentelemetry.trace import Status
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from opentelemetry.instrumentation.httpx import HTTPXClientInstrumentor

METHODS = {"GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"}
ERROR_TYPES = {"ValueError", "TypeError", "RuntimeError", "Exception", "TimeoutError",
    "ConnectionError", "CancelledError", "httpx.ConnectError", "httpx.ReadError",
    "httpx.ConnectTimeout", "httpx.ReadTimeout", "httpx.PoolTimeout", "httpx.WriteTimeout"}


def error_type(value):
    return value if isinstance(value, str) and value in ERROR_TYPES else "Error"


class SafeSpanExporter(SpanExporter):
    """Copy spans through public SDK APIs before any exporter sees them.

    Routes must come from the application's registered templates. HTTPX spans
    have no route template and retain their method and timing only.
    """
    def __init__(self, delegate, app):
        self.delegate = delegate
        self.app = app

    def export(self, spans):
        routes = {route.path for route in self.app.routes if hasattr(route, "path")}
        cleaned = []
        for span in spans:
            source = span.attributes or {}
            attributes = {}
            method = source.get("http.request.method", source.get("http.method"))
            if method is not None:
                method = method if isinstance(method, str) and method in METHODS else "OTHER"
                key = "http.request.method" if "http.request.method" in source else "http.method"
                attributes[key] = method
            route = source.get("http.route")
            if isinstance(route, str) and route in routes:
                attributes["http.route"] = route
            else:
                route = None
            for key in ("http.status_code", "http.response.status_code"):
                value = source.get(key)
                if isinstance(value, int) and not isinstance(value, bool) and 100 <= value <= 599:
                    attributes[key] = value
            if "error.type" in source:
                attributes["error.type"] = error_type(source["error.type"])
            events = [Event("exception", {"exception.type": error_type((event.attributes or {}).get("exception.type"))},
                timestamp=event.timestamp) for event in span.events if event.name == "exception"]
            # Neither original span names nor status descriptions are safe inputs.
            name = " ".join(part for part in (method, route) if part) or span.kind.name
            cleaned.append(ReadableSpan(name=name, context=span.context, parent=span.parent,
                resource=Resource({"service.name": "ai-service"}), attributes=attributes,
                events=events, kind=span.kind, status=Status(span.status.status_code),
                start_time=span.start_time, end_time=span.end_time))
        return self.delegate.export(tuple(cleaned))

    def force_flush(self, timeout_millis=30000):
        return self.delegate.force_flush(timeout_millis=timeout_millis)

    def shutdown(self):
        return self.delegate.shutdown()


def instrument(app, endpoint: str, *, exporter=None):
    provider = TracerProvider(resource=Resource.create({'service.name': 'ai-service'}))
    delegate = exporter if exporter is not None else OTLPSpanExporter(
        endpoint=endpoint.rstrip('/') + '/v1/traces', timeout=2)
    provider.add_span_processor(BatchSpanProcessor(SafeSpanExporter(delegate, app),
        max_queue_size=256, max_export_batch_size=64))
    FastAPIInstrumentor.instrument_app(app, tracer_provider=provider, excluded_urls='health,metrics',
        http_capture_headers_server_request=[], http_capture_headers_server_response=[])
    HTTPXClientInstrumentor().instrument(tracer_provider=provider)
    return provider
