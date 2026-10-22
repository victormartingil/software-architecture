"""Negative checks prevent stale or incomplete telemetry from counting as evidence."""
import datetime
import copy
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'scenarios'))
from observability import complete_trace, healthy_targets, provisioned_dashboard


class ObservabilityEvidenceTest(unittest.TestCase):
    def test_dashboard_requires_expected_uid_panels_queries_and_datasource(self):
        expected = {"uid": "reference", "panels": [
            {"id": 1, "type": "timeseries", "datasource": {"uid": "prometheus"},
             "targets": [{"expr": "up"}]}]}
        payload = {"dashboard": copy.deepcopy(expected), "meta": {"provisioned": True}}
        self.assertTrue(provisioned_dashboard(payload, expected))
        self.assertFalse(provisioned_dashboard({}, expected))
        for field, value in (("id", 2), ("type", "text"),
                             ("datasource", {"uid": "wrong"}),
                             ("targets", [{"expr": "wrong"}])):
            modified = copy.deepcopy(payload)
            modified["dashboard"]["panels"][0][field] = value
            self.assertFalse(provisioned_dashboard(modified, expected), field)
        payload["dashboard"]["uid"] = "old-dashboard"
        self.assertFalse(provisioned_dashboard(payload, expected))

    def test_targets_require_one_fresh_healthy_scrape_for_each_service(self):
        now = datetime.datetime.now(datetime.timezone.utc)
        targets = [{'labels': {'job': job}, 'health': 'up', 'lastScrape': now.isoformat()}
                   for job in ('commerce', 'ai', 'analytics')]
        payload = {'data': {'activeTargets': targets}}
        self.assertTrue(healthy_targets(payload, now))
        targets[2]['labels']['job'] = 'ai'
        self.assertFalse(healthy_targets(payload, now))
        targets[2]['labels']['job'] = 'analytics'
        targets[0]['lastScrape'] = (now - datetime.timedelta(seconds=60)).isoformat()
        self.assertFalse(healthy_targets(payload, now))
        targets[0]['lastScrape'] = now.isoformat()
        targets[1]['health'] = 'down'
        self.assertFalse(healthy_targets(payload, now))

    def test_trace_requires_both_services_not_any_old_single_service_span(self):
        def service(name):
            return {'resource': {'attributes': [{'key': 'service.name', 'value': {'stringValue': name}}]}}
        self.assertIsNone(complete_trace({'batches': [service('commerce-api')]}))
        self.assertIsNone(complete_trace({}))
        trace = {'batches': [service('ai-service'), service('commerce-api')]}
        self.assertIs(complete_trace(trace), trace)
