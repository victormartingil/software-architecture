FROM ghcr.io/astral-sh/uv:0.13.4 AS uv
FROM python:3.12.12-slim
COPY --from=uv /uv /usr/local/bin/uv
WORKDIR /app
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project
COPY src ./src
COPY data ./data
RUN uv sync --frozen --no-dev && useradd --uid 10001 --create-home app && mkdir /data && chown app:app /data
USER app
ENV PATH="/app/.venv/bin:$PATH" PYTHONUNBUFFERED=1
EXPOSE 8081
HEALTHCHECK --interval=10s --timeout=3s CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8081/health',timeout=2)"
CMD ["uvicorn", "ai_service.main:app", "--host", "0.0.0.0", "--port", "8081", "--workers", "1", "--no-access-log"]
