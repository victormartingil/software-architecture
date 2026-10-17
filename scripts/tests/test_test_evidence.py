"""A passing pytest process alone must not accept an omitted integration."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("test_evidence", Path(__file__).parents[1] / "check_test_evidence.py")
evidence = importlib.util.module_from_spec(spec)
spec.loader.exec_module(evidence)


class EvidenceGateTest(unittest.TestCase):
    def test_rejects_zero_skipped_failed_and_broken_reports(self):
        reports = [
            '<testsuite tests="0"/>',
            '<testsuites><testsuite tests="2" skipped="1"/></testsuites>',
            '<testsuite tests="2" failures="1"/>',
            '<testsuite tests="2" errors="1"/>',
            '<broken',
        ]
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "junit.xml"
            for xml in reports:
                with self.subTest(xml=xml):
                    report.write_text(xml)
                    with self.assertRaises(ValueError):
                        evidence.passing_tests(report)
            report.unlink()
            with self.assertRaises(ValueError):
                evidence.passing_tests(report)

    def test_accepts_passing_python_and_java_shapes(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "junit.xml"
            for xml in ('<testsuite tests="2"/>', '<testsuites><testsuite tests="2"/><testsuite tests="3"/></testsuites>'):
                report.write_text(xml)
                self.assertIn(evidence.passing_tests(report), (2, 5))
