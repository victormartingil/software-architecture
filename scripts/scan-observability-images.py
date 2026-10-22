"""Scan the four Compose-selected telemetry images by digest, without starting services.

Requires Docker and network access for images and advisory databases. Reports describe
the daemon's platform, not every platform in a multi-architecture image index.
"""
import json
from pathlib import Path
import subprocess

SERVICES = ('otel-collector', 'prometheus', 'tempo', 'grafana')
SCANNER = 'aquasec/trivy:0.70.0'


def vulnerabilities(report, expected_artifact):
    """Validate the consumed report shape before it can count as scan evidence."""
    if not isinstance(report, dict) or not isinstance(report.get('ArtifactName'), str) or not report['ArtifactName']:
        raise ValueError('Expected an image report with an artifact name')
    if report['ArtifactName'] != expected_artifact:
        raise ValueError('Report artifact does not match the requested image digest')
    results = report.get('Results')
    if not isinstance(results, list):
        raise ValueError('Expected a results array')
    findings = []
    for target in results:
        if not isinstance(target, dict) or not isinstance(target.get('Vulnerabilities', []), list):
            raise ValueError('Expected target objects with vulnerability arrays')
        for finding in target.get('Vulnerabilities', []):
            if not isinstance(finding, dict) or any(
                    not isinstance(finding.get(field), str) or not finding[field]
                    for field in ('Severity', 'VulnerabilityID', 'PkgName', 'InstalledVersion')):
                raise ValueError('Invalid vulnerability entry')
            if not isinstance(finding.get('FixedVersion', ''), str):
                raise ValueError('Invalid fixed version')
            findings.append(finding)
    return findings


def scan(repository):
    config = json.loads(subprocess.check_output(
        ['docker', 'compose', '--profile', 'observability', 'config', '--format', 'json'],
        cwd=repository))
    images = {service: config['services'][service]['image'] for service in SERVICES}
    reports = repository / 'reports' / 'image-security'
    reports.mkdir(parents=True, exist_ok=True)
    subprocess.run(['docker', 'compose', '--profile', 'observability', 'pull', *SERVICES],
                   cwd=repository, check=True)
    inventory = []
    failed = False
    for service, image in images.items():
        details = json.loads(subprocess.check_output(['docker', 'image', 'inspect', image]))[0]
        digests = details.get('RepoDigests', [])
        if not digests or '@sha256:' not in digests[0]:
            raise ValueError('Pulled image has no immutable registry digest: ' + service)
        platform = details['Os'] + '/' + details['Architecture']
        if details.get('Variant'):
            platform += '/' + details['Variant']
        report = reports / (service + '.json')
        # A new scan must never reuse evidence left by an earlier invocation.
        report.unlink(missing_ok=True)
        result = subprocess.run([
            'docker', 'run', '--rm', '--mount',
            'type=volume,src=architecture-reference-trivy-cache,dst=/root/.cache/trivy',
            '--mount', f'type=bind,src={reports},dst=/reports', SCANNER,
            'image', '--image-src', 'remote', '--platform', platform,
            '--scanners', 'vuln', '--severity', 'HIGH,CRITICAL', '--ignore-unfixed=false',
            '--exit-code', '1', '--timeout', '10m', '--format', 'json',
            '--output', f'/reports/{service}.json', digests[0]], check=False)
        valid_report = False
        try:
            findings = vulnerabilities(json.loads(report.read_text()), digests[0])
            for finding in findings:
                print(service, finding['Severity'], finding['VulnerabilityID'],
                      finding['PkgName'], finding['InstalledVersion'],
                      'fixed=' + finding.get('FixedVersion', 'unavailable'))
            valid_report = True
        except (OSError, ValueError, TypeError, KeyError) as failure:
            print(service, 'scan report unavailable or malformed:', failure)
        inventory.append({'service': service, 'image': image, 'digest': digests[0],
                          'platform': platform, 'scanner': SCANNER,
                          'exitCode': result.returncode, 'validReport': valid_report})
        failed = failed or result.returncode != 0 or not valid_report
    (reports / 'inventory.json').write_text(json.dumps(inventory, indent=2) + '\n')
    print(json.dumps({'reports': 'reports/image-security', 'images': inventory}, indent=2))
    return int(failed)


def main():
    return scan(Path(__file__).resolve().parents[1])


if __name__ == '__main__':
    raise SystemExit(main())
