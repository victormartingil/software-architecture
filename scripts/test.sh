#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export DOCKER_HOST="${DOCKER_HOST:-$(docker context inspect --format '{{.Endpoints.docker.Host}}')}"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="${TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE:-/var/run/docker.sock}"
docker info >/dev/null
export VERIFY_PYTHON_JUNIT="${VERIFY_PYTHON_JUNIT:-$PWD/reports/python-tests.xml}"
mkdir -p "$(dirname "$VERIFY_PYTHON_JUNIT")"
python3 -m unittest discover -s scripts/tests
# Docker >=25, including Docker29's newer minimum API; no daemon configuration change.
for application in commerce-api order-analytics; do
  (cd "apps/$application" && ./mvnw -B -ntp -Dapi.version=1.44 clean verify)
done
vector_container="$(docker run -d --rm -e POSTGRES_PASSWORD=fixture-only -e POSTGRES_DB=ai -p 127.0.0.1::5432 pgvector/pgvector:0.8.1-pg16)"
trap 'docker rm -f "$vector_container" >/dev/null 2>&1 || true' EXIT
vector_ready=false
for attempt in {1..30}; do
  # Initialization uses a temporary Unix-socket server before POSTGRES_DB exists.
  # TCP becomes ready only on the final server, after database initialization.
  if docker exec "$vector_container" pg_isready -h 127.0.0.1 -U postgres -d ai >/dev/null 2>&1; then
    vector_ready=true
    break
  fi
  sleep 1
done
"$vector_ready" || { echo 'Dedicated pgvector test database did not become ready' >&2; exit 1; }
docker exec "$vector_container" psql -h 127.0.0.1 -U postgres -d ai -c 'CREATE EXTENSION IF NOT EXISTS vector' >/dev/null
vector_port="$(docker port "$vector_container" 5432/tcp | sed 's/.*://')"
export TEST_VECTOR_DSN="postgresql://postgres:fixture-only@127.0.0.1:$vector_port/ai"
(cd apps/ai-service && "${UV:-uv}" sync --frozen && "${UV:-uv}" run pytest --junitxml="$VERIFY_PYTHON_JUNIT" && "${UV:-uv}" run lint-imports)
python3 scripts/check_test_evidence.py
