"""Test alert expressions and timing offline using the Compose-pinned Prometheus image."""
import json
from pathlib import Path
import subprocess


def main():
    repository = Path(__file__).resolve().parents[1]
    config = json.loads(subprocess.check_output(
        ['docker', 'compose', '--profile', 'observability', 'config', '--format', 'json'], cwd=repository))
    image = config['services']['prometheus']['image']
    return subprocess.run([
        'docker', 'run', '--rm', '--pull=never', '--network', 'none',
        '--entrypoint', '/bin/promtool', '--mount',
        f'type=bind,src={repository / "infra/observability"},dst=/rules,readonly',
        image, 'test', 'rules', '/rules/alerts.test.yaml'], check=False).returncode


if __name__ == '__main__':
    raise SystemExit(main())
