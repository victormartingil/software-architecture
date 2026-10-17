#!/usr/bin/env python3
"""Validate and replay a repaired synthetic event, preserving its identity."""
import argparse
import json
from pathlib import Path
import subprocess
from jsonschema import Draft202012Validator, FormatChecker

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--file", required=True, type=Path)
parser.add_argument("--confirm-replay", action="store_true", required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
event = json.loads(args.file.read_text())
schema = json.loads((root / "contracts/events/orders.placed.v1.schema.json").read_text())
Draft202012Validator(schema, format_checker=FormatChecker()).validate(event)
line = event["data"]["orderId"] + "\t" + json.dumps(event, separators=(",", ":")) + "\n"
subprocess.run(["docker", "compose", "exec", "-T", "kafka", "/opt/kafka/bin/kafka-console-producer.sh",
    "--bootstrap-server", "kafka:9092", "--topic", "orders.placed.v1", "--property", "parse.key=true", "--property", "key.separator=\t"],
    input=line.encode(), cwd=root, check=True)
print("Replayed eventId=" + event["eventId"] + "; duplicates remain subject to consumer deduplication")
