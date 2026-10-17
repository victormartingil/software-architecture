import copy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('kind_scale', Path(__file__).parents[1] / 'kind-scale-check.py')
scale = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scale)


class HpaEvidenceTest(unittest.TestCase):
    def test_manifest_desired_or_unready_endpoint_cannot_pass_as_scaling(self):
        hpa = {'status': {'desiredReplicas': 2, 'currentMetrics': [
            {'resource': {'name': 'cpu', 'current': {'averageUtilization': 78}}}]}}
        deployment = {'status': {'readyReplicas': 2}}
        pods = {'items': [{'metadata': {'name': name, 'labels': {'app': 'commerce-api'}},
                           'status': {'conditions': [{'type': 'Ready', 'status': 'True'}]}}
                          for name in ('commerce-a', 'commerce-b')]}
        endpoints = {'items': [{'endpoints': [
            {'targetRef': {'kind': 'Pod', 'name': name}, 'conditions': {'ready': True}}
            for name in ('commerce-a', 'commerce-b')]}]}
        self.assertTrue(scale.scaled(hpa, deployment, pods, endpoints))
        negatives = []
        missing_metric = copy.deepcopy(hpa)
        missing_metric['status']['currentMetrics'] = []
        negatives.append((missing_metric, deployment, pods, endpoints))
        negatives.append((hpa, {'status': {'readyReplicas': 1}}, pods, endpoints))
        unready = copy.deepcopy(endpoints)
        unready['items'][0]['endpoints'][1]['conditions']['ready'] = False
        negatives.append((hpa, deployment, pods, unready))
        terminating = copy.deepcopy(pods)
        terminating['items'][1]['metadata']['deletionTimestamp'] = '2026-10-17T12:00:00Z'
        negatives.append((hpa, deployment, terminating, endpoints))
        wrong_backend = copy.deepcopy(endpoints)
        wrong_backend['items'][0]['endpoints'][1]['targetRef']['name'] = 'old-pod'
        negatives.append((hpa, deployment, pods, wrong_backend))
        for case in negatives:
            with self.subTest(case=case):
                self.assertFalse(scale.scaled(*case))

    def test_cross_pod_failure_closes_every_forward_and_requires_same_result(self):
        # Real cross-pod execution is verified by kind-scale-check; here prove its
        # comparison rejects two independently successful but duplicated orders.
        from unittest.mock import patch
        from contextlib import contextmanager
        closed = []
        @contextmanager
        def forward(pod):
            try:
                yield pod
            finally:
                closed.append(pod)
        def response(base, path, *args):
            return {'available': 10} if 'inventory' in path else {'id': base}
        with patch.object(scale, 'authenticate', return_value='synthetic'), \
             patch.object(scale, 'port_forward', side_effect=forward), \
             patch.object(scale, 'request', side_effect=response):
            with self.assertRaisesRegex(RuntimeError, 'different orders'):
                scale.cross_pod_idempotency(['pod-a', 'pod-b'])
        self.assertCountEqual(closed, ['pod-a', 'pod-b'])
