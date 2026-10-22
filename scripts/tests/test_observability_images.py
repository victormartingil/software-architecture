"""Image gate command contract; no Docker or network is used by these tests."""
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location(
    'image_scan', Path(__file__).parents[1] / 'scan-observability-images.py')
image_scan = importlib.util.module_from_spec(spec)
spec.loader.exec_module(image_scan)
DIGEST = 'example/image@sha256:' + 'a' * 64


class ImageSecurityTest(unittest.TestCase):
    def invoke(self, failed=False, digest=True, reports=True, report_payloads=None, stale=False):
        config = {'services': {name: {'image': 'example/' + name + ':fixture'}
                               for name in image_scan.SERVICES}}
        inspected = [{'RepoDigests': [DIGEST] if digest else [],
                      'Os': 'linux', 'Architecture': 'arm64'}]
        outputs = [json.dumps(config).encode()] + [json.dumps(inspected).encode()] * 4
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / 'reports/image-security'
            if stale:
                target.mkdir(parents=True)
                for service in image_scan.SERVICES:
                    (target / (service + '.json')).write_text(json.dumps(
                        {'ArtifactName': 'old-fixture', 'Results': []}))

            def run_command(command, **kwargs):
                if command[:2] != ['docker', 'run']:
                    return subprocess.CompletedProcess(command, 0)
                service = Path(command[command.index('--output') + 1]).stem
                output = target / (service + '.json')
                self.assertFalse(output.exists(), 'Old reports must be removed before scanning')
                if reports:
                    payload = (report_payloads or {}).get(service, {'ArtifactName': DIGEST, 'Results': []})
                    output.write_text(json.dumps(payload))
                return subprocess.CompletedProcess(command, int(failed and service == image_scan.SERVICES[0]))

            with patch('builtins.print'), \
                 patch.object(image_scan.subprocess, 'check_output', side_effect=outputs), \
                 patch.object(image_scan.subprocess, 'run', side_effect=run_command) as run:
                result = image_scan.scan(root)
                inventory = json.loads((root / 'reports/image-security/inventory.json').read_text())
                return result, run.call_args_list, inventory

    def test_ci_uses_the_same_maintained_scanner_release(self):
        workflow = (Path(__file__).parents[2] / '.github/workflows/verify.yml').read_text()
        versions = [line.strip().split(': ', 1)[1]
                    for line in workflow.splitlines() if line.strip().startswith('version: v0.')]
        self.assertEqual(len(versions), 2)
        self.assertEqual(set(versions), {'v' + image_scan.SCANNER.rsplit(':', 1)[1]})

    def test_compose_is_source_of_images_and_digest_platform_are_scanned(self):
        result, calls, inventory = self.invoke()
        self.assertEqual(result, 0)
        self.assertEqual(len(inventory), 4)
        self.assertEqual(calls[0].args[0][-4:], list(image_scan.SERVICES))
        for call in calls[1:]:
            command = call.args[0]
            self.assertEqual(command[command.index('--platform') + 1], 'linux/arm64')
            self.assertEqual(command[-1], DIGEST)
            self.assertIn('--ignore-unfixed=false', command)
            self.assertEqual(command[command.index('--severity') + 1], 'HIGH,CRITICAL')
            self.assertNotIn('/var/run/docker.sock', ' '.join(command))

    def test_failure_does_not_skip_other_images_and_is_propagated(self):
        result, calls, inventory = self.invoke(failed=True)
        self.assertEqual(result, 1)
        self.assertEqual(len(calls), 5)
        self.assertEqual([item['exitCode'] for item in inventory], [1, 0, 0, 0])

    def test_missing_digest_cannot_fall_back_to_mutable_tag(self):
        with self.assertRaisesRegex(ValueError, 'immutable registry digest'):
            self.invoke(digest=False)

    def test_missing_reports_cannot_count_as_success(self):
        result, calls, inventory = self.invoke(reports=False)
        self.assertEqual(result, 1)
        self.assertEqual(len(calls), 5)
        self.assertTrue(all(not item['validReport'] for item in inventory))

    def test_malformed_report_fails_closed_and_continues_other_images(self):
        malformed = [None, [], {}, {'ArtifactName': ''},
                     {'ArtifactName': DIGEST},
                     {'ArtifactName': DIGEST, 'Results': None},
                     {'ArtifactName': DIGEST, 'Results': 'invalid'},
                     {'ArtifactName': DIGEST, 'Results': [None]},
                     {'ArtifactName': DIGEST, 'Results': [{'Vulnerabilities': None}]},
                     {'ArtifactName': DIGEST, 'Results': [{'Vulnerabilities': [{}]}]},
                     {'ArtifactName': DIGEST, 'Results': [{'Vulnerabilities': [{
                         'Severity': 'HIGH', 'VulnerabilityID': 'CVE-fixture', 'PkgName': 'fixture',
                         'InstalledVersion': '1.0', 'FixedVersion': None}]}]}]
        for payload in malformed:
            with self.subTest(payload=payload):
                result, calls, inventory = self.invoke(report_payloads={image_scan.SERVICES[0]: payload})
                self.assertEqual(result, 1)
                self.assertEqual(len(calls), 5)
                self.assertEqual([item['validReport'] for item in inventory], [False, True, True, True])

    def test_report_for_a_different_artifact_cannot_count_as_evidence(self):
        result, calls, inventory = self.invoke(report_payloads={
            image_scan.SERVICES[0]: {'ArtifactName': 'other/image@sha256:' + 'b' * 64, 'Results': []}})
        self.assertEqual(result, 1)
        self.assertEqual(len(calls), 5)
        self.assertEqual([item['validReport'] for item in inventory], [False, True, True, True])

    def test_stale_reports_cannot_replace_missing_current_output(self):
        result, calls, inventory = self.invoke(reports=False, stale=True)
        self.assertEqual(result, 1)
        self.assertEqual(len(calls), 5)
        self.assertTrue(all(not item['validReport'] for item in inventory))
