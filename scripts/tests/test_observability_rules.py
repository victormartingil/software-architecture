"""The alert gate reuses the pinned image and propagates promtool failures."""
import importlib.util
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('rules_test', Path(__file__).parents[1]/'observability-rules-test.py')
rules = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rules)


class RulesCommandTest(unittest.TestCase):
    def test_offline_pinned_readonly_and_failure_propagation(self):
        with (
            patch.object(rules.subprocess, 'check_output',
                         return_value=b'{"services":{"prometheus":{"image":"prom/prometheus:fixture"}}}') as config,
            patch.object(rules.subprocess, 'run', return_value=subprocess.CompletedProcess([], 1)) as run,
        ):
            self.assertEqual(rules.main(), 1)
        self.assertEqual(config.call_args.args[0], ['docker', 'compose', '--profile', 'observability', 'config', '--format', 'json'])
        self.assertEqual(config.call_args.kwargs['cwd'], Path(rules.__file__).resolve().parents[1])
        command = run.call_args.args[0]
        self.assertEqual(command[command.index('--entrypoint')+1], '/bin/promtool')
        self.assertIn('--pull=never', command)
        self.assertEqual(command[command.index('--network')+1], 'none')
        self.assertTrue(command[command.index('--mount')+1].endswith(',dst=/rules,readonly'))
        self.assertEqual(command[-4:], ['prom/prometheus:fixture', 'test', 'rules', '/rules/alerts.test.yaml'])
