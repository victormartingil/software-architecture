import asyncio
import io
import json
import logging
import socket
from contextlib import asynccontextmanager

import httpx
import pytest
import uvicorn
from fastapi import FastAPI, Request
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from opentelemetry.instrumentation.httpx import HTTPXClientInstrumentor
from opentelemetry.sdk.trace import Event, ReadableSpan
from opentelemetry.sdk.trace.export.in_memory_span_exporter import InMemorySpanExporter
from opentelemetry.trace import SpanContext, SpanKind, Status, StatusCode

from ai_service.domain import Principal, ServiceError
from ai_service.infrastructure.diagnostics import ServerErrorFilter, install_server_log_filter
from ai_service.infrastructure.settings import Settings
from ai_service.infrastructure.telemetry import SafeSpanExporter
from ai_service.main import ACCESS_LOG, create_app

SECRET = 'private-ZAFIRO-8391'


def test_server_filter_removes_exception_formatting_inputs():
    record = logging.LogRecord('uvicorn.error', logging.ERROR, __file__, 1,
        SECRET + '%s', (SECRET,), (ValueError, ValueError(SECRET), None))
    record.exc_text = record.stack_info = SECRET
    assert ServerErrorFilter().filter(record)
    assert record.getMessage() == 'ASGI server error (ValueError)'
    assert record.exc_info is record.exc_text is record.stack_info is None
    assert record.args == ()
    lifecycle = logging.LogRecord('uvicorn.error', logging.INFO, __file__, 1, 'Application startup complete.', (), None)
    ServerErrorFilter().filter(lifecycle)
    assert lifecycle.getMessage() == 'Application startup complete.'
    install_server_log_filter()
    install_server_log_filter()
    assert sum(isinstance(item, ServerErrorFilter) for item in logging.getLogger('uvicorn.error').filters) == 1


def test_exporter_copies_public_span_and_delegates_lifecycle():
    class Sink(InMemorySpanExporter):
        def force_flush(self, timeout_millis=30000):
            self.timeout = timeout_millis
            return True
        def shutdown(self):
            self.closed = True
    app = FastAPI()
    app.add_api_route('/safe/{id}', lambda: None)
    sink = Sink()
    wrapper = SafeSpanExporter(sink, app)
    context, parent = SpanContext(1, 2, False), SpanContext(1, 3, True)
    span = ReadableSpan(SECRET, context=context, parent=parent, kind=SpanKind.SERVER,
        start_time=10, end_time=20, attributes={'http.method': 'POST', 'http.route': '/safe/{id}',
            'http.status_code': 503, 'url.full': 'https://example/' + SECRET, 'error.type': 'ValueError'},
        events=[Event('exception', {'exception.type': 'ValueError', 'exception.message': SECRET,
            'exception.stacktrace': SECRET}, timestamp=15), Event(SECRET, {'body': SECRET})],
        status=Status(StatusCode.ERROR, SECRET))
    wrapper.export([span])
    result, = sink.get_finished_spans()
    assert result is not span and result.context == context and result.parent == parent
    assert result.start_time == 10 and result.end_time == 20
    assert result.name == 'POST /safe/{id}'
    assert result.status.status_code == StatusCode.ERROR and result.status.description is None
    assert result.events[0].attributes == {'exception.type': 'ValueError'}
    assert result.resource.attributes == {'service.name': 'ai-service'}
    assert SECRET not in result.to_json() and SECRET in span.to_json()
    malformed = ReadableSpan(SECRET, attributes={'http.method': ['POST'],
        'http.route': ['/safe/{id}'], 'error.type': ['ValueError']})
    wrapper.export([malformed])
    bounded = sink.get_finished_spans()[-1]
    assert bounded.name == 'OTHER'
    assert bounded.attributes == {'http.method': 'OTHER', 'error.type': 'Error'}
    assert wrapper.force_flush(123) and sink.timeout == 123
    wrapper.shutdown()
    assert sink.closed


@pytest.mark.parametrize('tracing', [False, True])
async def test_real_uvicorn_sanitizes_json_sse_and_httpx(tmp_path, monkeypatch, capfd, tracing):
    from ai_service.infrastructure import telemetry
    sink, providers = InMemorySpanExporter(), []
    propagated = []
    original = telemetry.instrument
    def instrument(app, endpoint):
        provider = original(app, endpoint, exporter=sink)
        providers.append(provider)
        return provider
    monkeypatch.setattr(telemetry, 'instrument', instrument)
    app = create_app(Settings(workflow_db=str(tmp_path / 'diagnostics.db'),
        otel_exporter_otlp_endpoint='http://unused' if tracing else ''))
    @app.get('/fixture-downstream/{value}')
    async def downstream(value: str, request: Request):
        propagated.append(request.headers.get('traceparent'))
        return {'status': 'ok'}
    lifespan = app.router.lifespan_context
    class Verified:
        async def verify(self, token):
            return Principal('fixture', 'demo', frozenset({'CUSTOMER'}))
    async def failure(actor, question, mode):
        if question == 'expected':
            raise ServiceError(503, 'Dependency unavailable')
        if tracing:
            async with httpx.AsyncClient() as client:
                await client.get(base_url + '/fixture-downstream/' + SECRET + '?token=' + SECRET,
                    headers={'Authorization': 'Bearer ' + SECRET})
        raise ValueError(question + ' Bearer ' + SECRET)
    @asynccontextmanager
    async def configured(app):
        async with lifespan(app):
            app.state.verifier = Verified()
            app.state.answers.answer = failure
            yield
    app.router.lifespan_context = configured
    output = io.StringIO()
    handler = logging.StreamHandler(output)
    server_logger = logging.getLogger('uvicorn.error')
    server_logger.addHandler(handler)
    ACCESS_LOG.addHandler(handler)
    sock = socket.socket()
    sock.bind(('127.0.0.1', 0))
    server = uvicorn.Server(uvicorn.Config(app, log_config=None, access_log=False, lifespan='on'))
    task = asyncio.create_task(server.serve(sockets=[sock]))
    try:
        async with asyncio.timeout(5):
            while not server.started:
                if task.done():
                    await task
                await asyncio.sleep(.01)
        headers = {'Authorization': 'Bearer ' + SECRET,
            'X-Correlation-ID': '00000000-0000-4000-8000-000000000001'}
        base_url = f'http://127.0.0.1:{sock.getsockname()[1]}'
        async with httpx.AsyncClient(base_url=base_url) as client:
            response = await client.post('/api/v1/answers?secret=' + SECRET, json={'question': SECRET}, headers=headers)
            assert response.status_code == 503 and SECRET not in response.text
            stream = await client.post('/api/v1/answers/stream?secret=' + SECRET, json={'question': SECRET}, headers=headers)
            assert stream.status_code == 200 and 'event: error' in stream.text and SECRET not in stream.text
            before_expected = output.getvalue().count('unexpected_error')
            expected = await client.post('/api/v1/answers', json={'question': 'expected'}, headers=headers)
            assert expected.status_code == 503
            assert output.getvalue().count('unexpected_error') == before_expected
    finally:
        server.should_exit = True
        await asyncio.wait_for(task, timeout=5)
        sock.close()
        for provider in providers:
            assert provider.force_flush(timeout_millis=2000)
            provider.shutdown()
        if tracing:
            FastAPIInstrumentor.uninstrument_app(app)
            HTTPXClientInstrumentor().uninstrument()
        server_logger.removeHandler(handler)
        ACCESS_LOG.removeHandler(handler)
    logs = output.getvalue()
    captured = capfd.readouterr()
    assert SECRET not in logs + captured.err + captured.out
    assert 'ASGI server error (ValueError)' in logs
    records = [json.loads(line) for line in logs.splitlines() if line.startswith('{')]
    errors = [record for record in records if record['event'] == 'unexpected_error']
    assert len(errors) == 2
    assert all(record['errorType'] == 'ValueError' and record['correlationId'] == headers['X-Correlation-ID'] for record in errors)
    if tracing:
        spans = sink.get_finished_spans()
        assert spans and SECRET not in ''.join(span.to_json() for span in spans)
        assert any(span.status.status_code == StatusCode.ERROR for span in spans)
        assert any(span.kind == SpanKind.CLIENT for span in spans)
        assert len(propagated) == 2 and all(propagated)
        assert all(header.split('-')[1] in {format(span.context.trace_id, '032x') for span in spans} for header in propagated)
        assert any(span.attributes.get('http.route') == '/api/v1/answers' for span in spans)
        trace_ids = {format(span.context.trace_id, '032x') for span in spans}
        assert all(record['traceId'] in trace_ids for record in errors)
