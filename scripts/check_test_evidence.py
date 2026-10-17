"""Reject missing, empty, skipped or failed JUnit evidence in the canonical test command."""
from pathlib import Path
import argparse
import os
import sys
import xml.etree.ElementTree as ET


def passing_tests(report: Path) -> int:
    try:
        root = ET.parse(report).getroot()
        suites = [root] if root.tag == "testsuite" else list(root.iter("testsuite"))
        total = sum(int(s.get("tests", "0")) for s in suites)
        bad = sum(int(s.get(k, "0")) for s in suites for k in ("skipped", "errors", "failures"))
    except (OSError, ET.ParseError, ValueError) as exc:
        raise ValueError(f"Missing or invalid JUnit evidence: {report}") from exc
    if total == 0 or bad:
        raise ValueError(f"JUnit evidence has zero tests or skipped/failed cases: {report}")
    return total


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--python", type=Path, default=Path(os.environ.get("VERIFY_PYTHON_JUNIT", "reports/python-tests.xml")))
    args = parser.parse_args()
    try:
        for app in ("commerce-api", "order-analytics"):
            reports = list(Path("apps", app, "target/surefire-reports").glob("TEST-*.xml"))
            if not reports:
                raise ValueError(f"Missing JUnit evidence: {app}")
            total = sum(passing_tests(report) for report in reports)
            print(f"{app}: {total} passing tests, zero skipped")
        print(f"ai-service: {passing_tests(args.python)} passing tests, zero skipped")
    except ValueError as exc:
        print(exc, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
