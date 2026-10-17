import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("event_flow", Path(__file__).parents[2] / "scenarios/event_flow.py")
flow = importlib.util.module_from_spec(spec)
spec.loader.exec_module(flow)


class CurrentSummaryTest(unittest.TestCase):
    def test_stale_other_tenant_and_partial_updates_cannot_pass(self):
        expected = dict(tenantId="demo", sku="BOOK-1", windowStart="2026-10-17T12:00:00Z",
                        windowEnd="2026-10-17T12:01:00Z", orderCount=2, totalQuantity=3, totalCents=7500)
        self.assertTrue(flow.matches_summary(expected, expected))
        for field, wrong in (("tenantId", "other"), ("sku", "OTHER"), ("windowStart", "2026-10-16T12:00:00Z"),
                             ("orderCount", 1), ("totalQuantity", 2), ("totalCents", 5000)):
            with self.subTest(field=field):
                self.assertFalse(flow.matches_summary({**expected, field: wrong}, expected))
