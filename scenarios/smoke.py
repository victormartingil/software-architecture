#!/usr/bin/env python3
"""Authenticated, repeatable local flow. Uses synthetic users; never prints JWTs."""
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
from uuid import uuid4

COMMERCE = os.getenv("COMMERCE_URL", "http://localhost:8080")
AI = os.getenv("AI_URL", "http://localhost:8081")
OIDC = os.getenv("OIDC_URL", "http://localhost:8088")


def request(base, path, method="GET", body=None, token=None, key=None, expected=200):
    headers = {"X-Correlation-ID": str(uuid4())}
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    if key:
        headers["Idempotency-Key"] = key
    req = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            status, payload = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, payload = error.code, error.read()
    assert status == expected, f"{method} {path}: expected {expected}, got {status}: {payload[:400]!r}"
    return json.loads(payload) if payload else None


def token(username):
    data = urllib.parse.urlencode({"client_id": "reference-cli", "grant_type": "password",
                                  "username": username, "password": "local-demo-only"}).encode()
    req = urllib.request.Request(OIDC + "/realms/reference/protocol/openid-connect/token", data=data)
    with urllib.request.urlopen(req, timeout=10) as response:
        return json.load(response)["access_token"]


def main():
    for _ in range(90):
        try:
            request(COMMERCE, "/actuator/health/readiness")
            request(AI, "/health")
            alice, manager, bob = [token(name) for name in ("alice", "manager", "bob")]
            break
        except (OSError, AssertionError):
            time.sleep(2)
    else:
        raise SystemExit("Services not ready; inspect docker compose ps/logs")
    request(COMMERCE, "/api/v1/inventory/BOOK-1", expected=401)
    before = request(COMMERCE, "/api/v1/inventory/BOOK-1", token=alice)["available"]
    key = "smoke-" + str(uuid4())
    order = request(COMMERCE, "/api/v1/orders", "POST", {"sku": "BOOK-1", "quantity": 1}, alice, key, 201)
    repeat = request(COMMERCE, "/api/v1/orders", "POST", {"sku": "BOOK-1", "quantity": 1}, alice, key, 201)
    assert repeat == order and order["totalCents"] == 2500
    request(COMMERCE, "/api/v1/orders", "POST", {"sku": "BOOK-1", "quantity": 2}, alice, key, 409)
    request(COMMERCE, "/api/v1/orders/" + order["id"], token=bob, expected=404)
    assert request(COMMERCE, "/api/v1/inventory/BOOK-1", token=alice)["available"] == before - 1
    answer = request(AI, "/api/v1/answers", "POST", {"question": "¿Cuál es el plazo de devolución?"}, alice)
    assert answer["citations"] and not answer["abstained"] and "fake" in answer["mode"]
    alien = request(AI, "/api/v1/answers", "POST", {"question": "ZAFIRO"}, alice)
    assert alien["abstained"] and not alien["citations"]
    workflow = request(AI, "/api/v1/workflows/returns", "POST", {"orderId": order["id"], "reason": "Synthetic reference scenario"}, alice, key + "-return", 201)
    request(COMMERCE, "/api/v1/returns", "POST", {"orderId": order["id"], "reason": "Duplicate proposal"}, alice, key + "-duplicate", 409)
    wid = workflow["workflowId"]
    request(AI, "/api/v1/workflows/" + wid, token=bob, expected=404)
    request(AI, "/api/v1/workflows/" + wid + "/approve", "POST", {}, alice, expected=403)
    completed = request(AI, "/api/v1/workflows/" + wid + "/approve", "POST", {}, manager)
    retried = request(AI, "/api/v1/workflows/" + wid + "/approve", "POST", {}, manager)
    assert completed == retried
    assert request(COMMERCE, "/api/v1/orders/" + order["id"], token=alice)["status"] == "REFUNDED"
    assert request(COMMERCE, "/api/v1/inventory/BOOK-1", token=alice)["available"] == before
    # SSE is deliberately a validated whole result, not partial tool execution.
    req = urllib.request.Request(AI + "/api/v1/answers/stream",
        data=json.dumps({"question": "plazo devolución"}).encode(),
        headers={"Content-Type": "application/json", "Authorization": "Bearer " + alice})
    with urllib.request.urlopen(req, timeout=20) as response:
        stream = response.read().decode()
    assert "event: start" in stream and "event: result" in stream
    print(json.dumps({"result": "PASS", "checks": ["OIDC", "stock", "idempotency", "tenant isolation", "RAG fake", "workflow approval", "retry", "SSE"],
                      "orderId": order["id"], "workflowId": wid}, indent=2))


if __name__ == "__main__":
    main()
