import asyncio
import json
import logging
import time
from datetime import datetime, timezone
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Literal
from uuid import UUID, uuid4
import httpx
from fastapi import Depends, FastAPI, Header, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, StreamingResponse
from pydantic import BaseModel, ConfigDict, Field
from prometheus_client import Counter, Histogram, generate_latest, CONTENT_TYPE_LATEST
from starlette.responses import Response
from langgraph.checkpoint.sqlite.aio import AsyncSqliteSaver
from ai_service.application.answers import AnswerService
from ai_service.domain import ServiceError
from ai_service.infrastructure.diagnostics import install_server_log_filter
from ai_service.infrastructure.settings import Settings
from ai_service.infrastructure.security import JwtVerifier
from ai_service.infrastructure.providers import FakeProvider, OpenAIProvider, OpenAIEmbedder
from ai_service.infrastructure.retrieval import LexicalRetriever, VectorRetriever
from ai_service.infrastructure.commerce import HttpCommerceGateway
from ai_service.infrastructure.workflows import ReturnApprovalWorkflow

install_server_log_filter()

REQUESTS = Counter("ai_requests_total", "Answer outcomes", ["outcome", "mode"])
HTTP_REQUESTS = Counter("ai_http_requests_total", "HTTP responses", ["method", "route", "status"])
HTTP_LATENCY = Histogram("ai_http_request_seconds", "Time until response headers", ["method", "route"])
ACCESS_LOG = logging.getLogger("ai_service.access")
if not ACCESS_LOG.handlers:
    ACCESS_LOG.addHandler(logging.StreamHandler())
ACCESS_LOG.setLevel(logging.INFO)
ACCESS_LOG.propagate = False
LATENCY = Histogram("ai_answer_seconds", "Answer duration")
DATA = Path(__file__).resolve().parents[2] / "data"


class AnswerRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    question: str = Field(min_length=1, max_length=2000)
    retrievalMode: Literal["lexical", "vector"] = "lexical"


class AnswerResponse(BaseModel):
    answer: str
    citations: list[str]
    abstained: bool
    mode: str


class ReturnRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    orderId: UUID
    reason: str = Field(min_length=1, max_length=500)


def create_app(settings: Settings | None = None):
    settings = settings or Settings()

    @asynccontextmanager
    async def lifespan(app):
        Path(settings.workflow_db).parent.mkdir(parents=True, exist_ok=True)
        async with httpx.AsyncClient() as client, AsyncSqliteSaver.from_conn_string(settings.workflow_db) as saver:
            await saver.setup()
            if settings.ai_provider == "fake":
                provider = FakeProvider()
            elif settings.ai_provider == "openai":
                if settings.ai_live_budget_usd <= 0:
                    raise ValueError("AI_LIVE_BUDGET_USD must be explicitly positive")
                provider = OpenAIProvider(client, settings.openai_api_key, settings.openai_model, settings.ai_max_requests)
            else:
                raise ValueError("Unknown AI_PROVIDER")
            vector = None
            if settings.vector_dsn and settings.embedding_model:
                if not settings.openai_api_key or settings.ai_live_budget_usd <= 0 or settings.ai_max_requests <= 0:
                    raise ValueError("Live embeddings require key, explicit request cap and declared budget")
                embedder = OpenAIEmbedder(client, settings.openai_api_key, settings.embedding_model,
                    settings.embedding_dimensions, settings.ai_max_requests)
                vector = VectorRetriever(settings.vector_dsn, embedder, settings.embedding_dimensions)
            app.state.answers = AnswerService(provider, LexicalRetriever(DATA / "corpus.json"), vector)
            app.state.verifier = JwtVerifier(client, settings.oidc_issuer, settings.oidc_audience, settings.oidc_jwks_url)
            app.state.workflows = ReturnApprovalWorkflow(HttpCommerceGateway(client, settings.commerce_url), saver)
            yield

    app = FastAPI(title="Policy Assistant and Return Approval API", lifespan=lifespan)
    if settings.otel_exporter_otlp_endpoint:
        from ai_service.infrastructure.telemetry import instrument
        instrument(app, settings.otel_exporter_otlp_endpoint)

    @app.middleware("http")
    async def correlation(request, call_next):
        incoming = request.headers.get("X-Correlation-ID", "")
        try:
            request.state.correlation = str(UUID(incoming))
        except ValueError:
            request.state.correlation = str(uuid4())
        started = time.monotonic()
        status = 500
        try:
            response = await call_next(request)
            status = response.status_code
            response.headers["X-Correlation-ID"] = request.state.correlation
            return response
        except Exception:
            status = 503  # Matches the outer sanitized unexpected_error handler.
            raise
        finally:
            if request.url.path not in {"/health", "/metrics"}:
                from opentelemetry import trace
                route = getattr(request.scope.get("route"), "path", "unmatched")
                method = request.method if request.method in {"GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"} else "OTHER"
                elapsed = time.monotonic() - started
                HTTP_REQUESTS.labels(method, route, str(status)).inc()
                HTTP_LATENCY.labels(method, route).observe(elapsed)
                span = trace.get_current_span().get_span_context()
                # Fixed fields only: no JWT, user/tenant, prompt, body, raw URL or exception text.
                ACCESS_LOG.info(json.dumps({"event": "http_request", "service": "ai-service",
                    "timestamp": datetime.now(timezone.utc).isoformat(), "level": "INFO",
                    "route": route, "method": method, "status": status,
                    "durationMs": round(elapsed * 1000, 3),
                    "correlationId": request.state.correlation,
                    "traceId": format(span.trace_id, "032x") if span.is_valid else None}))

    def diagnose(request, exc):
        from opentelemetry import trace
        span = trace.get_current_span().get_span_context()
        method = request.method if request.method in {"GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"} else "OTHER"
        # Never serialize the exception, its message, traceback or request data.
        ACCESS_LOG.error(json.dumps({"event": "unexpected_error", "service": "ai-service",
            "timestamp": datetime.now(timezone.utc).isoformat(), "level": "ERROR",
            "errorType": type(exc).__name__,
            "route": getattr(request.scope.get("route"), "path", "unmatched"), "method": method,
            "correlationId": request.state.correlation,
            "traceId": format(span.trace_id, "032x") if span.is_valid else None}))

    def problem(request, status, detail):
        return JSONResponse(status_code=status, content={"status": status,
            "title": "Request failed", "detail": detail, "correlationId": request.state.correlation},
            media_type="application/problem+json",
            headers={"X-Correlation-ID": request.state.correlation,
                **({"WWW-Authenticate": "Bearer"} if status == 401 else {})})

    @app.exception_handler(ServiceError)
    async def service_error(request, exc):
        return problem(request, exc.status, exc.detail)

    @app.exception_handler(RequestValidationError)
    async def validation_error(request, exc):
        return problem(request, 400, "Invalid request data")

    @app.exception_handler(Exception)
    async def unexpected_error(request, exc):
        diagnose(request, exc)
        return problem(request, 503, "Dependency temporarily unavailable")

    async def identity(request: Request, authorization: str = Header(default="")):
        scheme, _, token = authorization.partition(" ")
        if scheme.lower() != "bearer" or not token or len(token) > 16384:
            raise ServiceError(401, "Bearer token required")
        return await app.state.verifier.verify(token)

    def token(request):
        return request.headers["Authorization"].partition(" ")[2]

    def idempotency(key):
        if not key or not key.strip() or len(key) > 128:
            raise ServiceError(400, "Idempotency-Key required (maximum 128 characters)")
        return key

    async def answer(actor, body):
        outcome = "error"
        mode = "openai" if settings.ai_provider == "openai" else "fake-simulation"
        try:
            with LATENCY.time():
                result = await app.state.answers.answer(actor, body.question, body.retrievalMode)
            outcome = "abstained" if result["abstained"] else "answered"
            return result
        except TimeoutError:
            raise ServiceError(503, "Answer deadline exceeded") from None
        except asyncio.CancelledError:
            outcome = "cancelled"
            raise
        finally:
            # Shared by JSON and SSE: exactly one outcome for each started answer operation.
            REQUESTS.labels(outcome, mode).inc()

    @app.get("/health")
    async def health():
        return {"status": "UP"}

    @app.get("/metrics", include_in_schema=False)
    async def metrics():
        return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)

    @app.post("/api/v1/answers", response_model=AnswerResponse)
    async def answers(body: AnswerRequest, actor=Depends(identity)):
        return await answer(actor, body)

    @app.post("/api/v1/answers/stream")
    async def stream(body: AnswerRequest, request: Request, actor=Depends(identity)):
        async def events():
            yield 'event: start\ndata: {}\n\n'
            task = asyncio.create_task(answer(actor, body))
            try:
                while not task.done():
                    if await request.is_disconnected():
                        return
                    await asyncio.wait({task}, timeout=0.05)
                try:
                    result = await task
                    yield 'event: result\ndata: ' + json.dumps(result, ensure_ascii=False) + '\n\n'
                except ServiceError as exc:
                    yield 'event: error\ndata: ' + json.dumps({"status": exc.status, "detail": exc.detail}) + '\n\n'
                except Exception as exc:
                    diagnose(request, exc)
                    yield 'event: error\ndata: {"status":503,"detail":"Dependency unavailable"}\n\n'
            finally:
                if not task.done():
                    task.cancel()
                await asyncio.gather(task, return_exceptions=True)
        return StreamingResponse(events(), media_type="text/event-stream", headers={"Cache-Control": "no-cache"})

    @app.post("/api/v1/workflows/returns", status_code=201)
    async def create_workflow(body: ReturnRequest, request: Request, actor=Depends(identity),
                              key: str = Header(default="", alias="Idempotency-Key")):
        return await app.state.workflows.create(actor, token(request), body.model_dump(mode="json"),
            idempotency(key), request.state.correlation)

    @app.get("/api/v1/workflows/{workflow_id}")
    async def get_workflow(workflow_id: str, actor=Depends(identity)):
        return await app.state.workflows.get(workflow_id, actor)

    @app.post("/api/v1/workflows/{workflow_id}/approve")
    async def approve_workflow(workflow_id: str, request: Request, actor=Depends(identity)):
        return await app.state.workflows.approve(workflow_id, actor, token(request), request.state.correlation)

    return app


app = create_app()
