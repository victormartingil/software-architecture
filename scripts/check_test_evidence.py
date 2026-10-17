"""A skipped integration is not accepted as a successful full verification."""
from pathlib import Path
import xml.etree.ElementTree as ET

for app in ("commerce-api", "order-analytics"):
    reports = list(Path("apps", app, "target/surefire-reports").glob("TEST-*.xml"))
    assert reports, f"Missing JUnit evidence: {app}"
    total = 0
    for report in reports:
        suite = ET.parse(report).getroot()
        assert int(suite.get("skipped", "0")) == 0, f"Skipped tests: {report}"
        assert int(suite.get("failures", "0")) + int(suite.get("errors", "0")) == 0, f"Failed: {report}"
        total += int(suite.get("tests", "0"))
    print(f"{app}: {total} passing tests, zero skipped")
