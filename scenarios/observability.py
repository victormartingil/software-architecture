#!/usr/bin/env python3
"""Prove current-operation metrics, cross-service trace and sanitized logs, without paid AI."""
import datetime
import json
import os
import subprocess
import time
import urllib.parse
from uuid import uuid4
from smoke import AI, COMMERCE, request, token

GRAFANA = os.getenv("GRAFANA_URL", "http://localhost:3000")
PROMETHEUS = os.getenv("PROMETHEUS_URL", "http://localhost:9090")


def metric(expression):
    result = request(PROMETHEUS, "/api/v1/query?" + urllib.parse.urlencode({"query": expression}))
    assert result["status"] == "success"
    return sum(float(series["value"][1]) for series in result["data"]["result"])


def eventually(check, description, timeout=90):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            result = check()
            if result:
                return result
        except (OSError, AssertionError):
            pass
        time.sleep(2)
    raise AssertionError("Timed out: " + description)


def services_in_trace(value):
    """Tempo's OTLP JSON uses batches/resource attributes, regardless of scope naming."""
    if isinstance(value, dict):
        if value.get("key") == "service.name":
            yield value.get("value", {}).get("stringValue")
        for child in value.values():
            yield from services_in_trace(child)
    elif isinstance(value, list):
        for child in value:
            yield from services_in_trace(child)


def healthy_targets(payload, now=None, not_before=None):
    now = now or datetime.datetime.now(datetime.timezone.utc)
    targets = payload.get("data", {}).get("activeTargets", [])
    expected = {"commerce", "ai", "analytics"}
    selected = [target for target in targets if target.get("labels", {}).get("job") in expected]
    if len(selected) != 3 or {target["labels"]["job"] for target in selected} != expected:
        return False
    for target in selected:
        try:
            scraped = datetime.datetime.fromisoformat(target["lastScrape"].replace("Z", "+00:00"))
            age = (now - scraped).total_seconds()
        except (KeyError, ValueError):
            return False
        if target.get("health") != "up" or not 0 <= age < 20 or (not_before and scraped < not_before):
            return False
    return True


def complete_trace(value):
    return value if {"ai-service", "commerce-api"} <= set(services_in_trace(value)) else None


def main():
    started = datetime.datetime.now(datetime.timezone.utc)
    health = request(GRAFANA, "/api/health")
    assert health["database"] == "ok"
    eventually(lambda: healthy_targets(request(PROMETHEUS, "/api/v1/targets?state=active"), not_before=started), "three fresh healthy scrapes")
    expression = 'sum(ai_http_requests_total{route="/api/v1/workflows/returns",status="201"})'
    before = metric(expression)
    alice = token("alice")
    order = request(COMMERCE, "/api/v1/orders", "POST", {"sku": "BOOK-1", "quantity": 1},
                    alice, "observability-order-" + str(uuid4()), 201)
    trace_id, correlation = uuid4().hex, str(uuid4())
    since = datetime.datetime.now(datetime.timezone.utc).isoformat()
    # A W3C sampled parent fixes the exact trace being asserted; old stored traces cannot pass.
    import urllib.request
    headers = {"Content-Type": "application/json", "Authorization": "Bearer " + alice,
               "Idempotency-Key": "observability-return-" + str(uuid4()),
               "X-Correlation-ID": correlation, "traceparent": "00-" + trace_id + "-" + uuid4().hex[:16] + "-01"}
    req = urllib.request.Request(AI + "/api/v1/workflows/returns", method="POST", headers=headers,
        data=json.dumps({"orderId": order["id"], "reason": "Synthetic telemetry scenario"}).encode())
    with urllib.request.urlopen(req, timeout=20) as response:
        assert response.status == 201 and response.headers["X-Correlation-ID"] == correlation
        workflow = json.load(response)
    after = eventually(lambda: (value if (value := metric(expression)) > before else None), "new HTTP metric")
    trace = eventually(lambda: complete_trace(request(GRAFANA, "/api/datasources/proxy/uid/tempo/api/traces/" + trace_id)), "complete exact trace exported")
    services = set(services_in_trace(trace))
    assert {"ai-service", "commerce-api"} <= services, "Trace must include both real services"
    serialized = json.dumps(trace)
    assert alice not in serialized and "Synthetic telemetry scenario" not in serialized
    compose = ["docker", "compose"]
    if os.getenv("COMPOSE_PROJECT_NAME"):
        compose += ["-p", os.environ["COMPOSE_PROJECT_NAME"]]
    logs = subprocess.run(compose + ["logs", "--no-log-prefix", "--since", since, "ai-service", "commerce-api"],
                          text=True, capture_output=True, check=True, timeout=20).stdout
    events = []
    for line in logs.splitlines():
        try:
            value = json.loads(line)
            if value.get("event") == "http_request" and value.get("correlationId") == correlation:
                events.append(value)
        except json.JSONDecodeError:
            continue
    assert len(events) >= 2, "Current request must produce correlated JSON logs in both services"
    assert all(event.get("traceId") == trace_id for event in events), "Both service logs must link to exact trace"
    assert alice not in logs and "Synthetic telemetry scenario" not in logs
    # Return stock and leave no unapproved workflow behind; credentials remain in memory only.
    request(AI, "/api/v1/workflows/" + workflow["workflowId"] + "/approve", "POST", {}, token("manager"))
    print(json.dumps({"result": "PASS", "traceId": trace_id, "correlationId": correlation,
        "services": sorted(services), "httpMetricDelta": after - before, "correlatedJsonLogs": len(events),
        "checks": ["three fresh scrapes", "current metric increment", "exact W3C cross-service trace",
                   "safe correlated JSON logs", "Grafana datasource proxy"],
        "dashboard": GRAFANA + "/d/architecture-reference"}, indent=2))


if __name__ == "__main__":
    main()
