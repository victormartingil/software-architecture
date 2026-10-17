"""Verify the current order across SQL and Kafka, using local operator access only."""
import argparse
from datetime import datetime, timedelta, timezone
import json
import os
from pathlib import Path
import subprocess
import time
from uuid import UUID

ROOT = Path(__file__).resolve().parents[1]


def local_command(service, command, timeout=20, check=True):
    runtime = os.getenv("SCENARIO_RUNTIME", "compose")
    if runtime == "compose":
        prefix = ["docker", "compose", "exec", "-T", service]
    elif runtime == "kind":
        prefix = ["kubectl", "--kubeconfig", str(ROOT / "reports/kind.kubeconfig"),
                  "--context", "kind-architecture-reference", "-n", "architecture-reference",
                  "exec", f"statefulset/{service}", "--"]
    else:
        raise ValueError("SCENARIO_RUNTIME must be compose or kind")
    try:
        return subprocess.run(prefix + command, cwd=ROOT, capture_output=True, text=True,
                              timeout=timeout, check=check)
    except subprocess.TimeoutExpired:
        raise AssertionError(f"Local {service} verification command exceeded its deadline") from None


def sql_literal(value):
    return "'" + value.replace("'", "''") + "'"


def remaining_timeout(deadline, maximum=20):
    remaining = deadline - time.monotonic()
    if remaining <= 0:
        raise AssertionError("Current-order event verification deadline exceeded")
    return min(maximum, remaining)


def query(sql, *, timeout=20):
    result = local_command("postgres", ["psql", "-U", "commerce", "-d", "commerce", "-At", "-c", sql], timeout=timeout)
    return json.loads(result.stdout) if result.stdout.strip() else None


def matches_summary(actual, expected):
    """Output records replace a window's totals; never sum successive updates."""
    fields = ("tenantId", "sku", "windowStart", "windowEnd", "orderCount", "totalQuantity", "totalCents")
    return all(actual.get(field) == expected[field] for field in fields)


def verify_event_flow(order_id, timeout=45):
    order_id = str(UUID(order_id))  # Only a validated identifier enters the SQL predicate.
    deadline = time.monotonic() + timeout
    observed = None
    while time.monotonic() < deadline:
        observed = query("SELECT json_build_object('payload', payload::jsonb, "
                         "'published', published_at IS NOT NULL, "
                         "'audited', EXISTS(SELECT 1 FROM inbox i JOIN audit_orders a USING(event_id) "
                         "WHERE i.event_id=o.event_id AND a.order_id=o.aggregate_id)) "
                         f"FROM outbox o WHERE aggregate_id='{order_id}'::uuid", timeout=remaining_timeout(deadline))
        if observed and observed["published"] and observed["audited"]:
            break
        time.sleep(.2)
    else:
        raise AssertionError(f"Order {order_id}: outbox publication/inbox effect not observed before deadline")

    event = observed["payload"]
    start = datetime.fromisoformat(event["occurredAt"].replace("Z", "+00:00")).astimezone(timezone.utc).replace(second=0, microsecond=0)
    end = start + timedelta(minutes=1)
    iso = lambda value: value.isoformat(timespec="seconds").replace("+00:00", "Z")
    # SQL is the source of created-order facts, including orders subsequently refunded.
    # Run the local scenario without concurrent writers so its expected snapshot stays stable.
    expected = query("SELECT json_build_object('orderCount',count(*),'totalQuantity',"
                     "sum((payload::jsonb->'data'->>'quantity')::bigint),'totalCents',"
                     "sum((payload::jsonb->'data'->>'totalCents')::bigint)) FROM outbox WHERE "
                     f"payload::jsonb->>'tenantId'={sql_literal(event['tenantId'])} AND "
                     f"payload::jsonb->'data'->>'sku'={sql_literal(event['data']['sku'])} AND "
                     f"(payload::jsonb->>'occurredAt')::timestamptz >= '{iso(start)}'::timestamptz AND "
                     f"(payload::jsonb->>'occurredAt')::timestamptz < '{iso(end)}'::timestamptz", timeout=remaining_timeout(deadline))
    expected.update(tenantId=event["tenantId"], sku=event["data"]["sku"], windowStart=iso(start), windowEnd=iso(end))
    while time.monotonic() < deadline:
        result = local_command("kafka", ["/opt/kafka/bin/kafka-console-consumer.sh", "--bootstrap-server",
            "kafka:9092", "--topic", "orders.summary.v1", "--from-beginning", "--consumer-property",
            "isolation.level=read_committed", "--max-messages", "10000", "--timeout-ms", "1500"],
            timeout=remaining_timeout(deadline, 15), check=False)
        for line in result.stdout.splitlines():
            try:
                actual = json.loads(line)
            except json.JSONDecodeError:
                continue
            if isinstance(actual, dict) and matches_summary(actual, expected):
                return {"orderId": order_id, "eventId": event["eventId"], "outbox": "published",
                        "inbox": "one-effect", "summary": actual}
    raise AssertionError(f"No committed Kafka update for the current order/window; expected {expected}. "
                         "Inspect order-analytics and broker. The local reader is bounded to 10,000 records.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--order-id", required=True, type=str)
    args = parser.parse_args()
    print(json.dumps(verify_event_flow(args.order_id), indent=2))
