#!/usr/bin/env python3
"""Observe genuine CPU-driven HPA scale-up, then test idempotency across both pods."""
import concurrent.futures
from contextlib import ExitStack, contextmanager
import json
from pathlib import Path
import re
import signal
import socket
import subprocess
import sys
import threading
import time
import urllib.parse
import urllib.request
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[1]
NAMESPACE = 'architecture-reference'
CONTEXT = 'kind-architecture-reference'
KUBE = ['kubectl', '--kubeconfig', str(ROOT / 'reports/kind.kubeconfig'),
        '--context', CONTEXT, '-n', NAMESPACE]


def kube(*args):
    return subprocess.check_output([*KUBE, *args], text=True, timeout=30)


def document(kind):
    return json.loads(kube('get', kind, '-o', 'json'))


def ready_pods(pods):
    return sorted(pod['metadata']['name'] for pod in pods['items']
                  if pod['metadata'].get('labels', {}).get('app') == 'commerce-api'
                  and not pod['metadata'].get('deletionTimestamp')
                  and any(condition['type'] == 'Ready' and condition['status'] == 'True'
                          for condition in pod.get('status', {}).get('conditions', [])))


def scaled(hpa, deployment, pods, endpoints):
    status = hpa.get('status', {})
    metric = next((item['resource']['current'].get('averageUtilization')
                   for item in status.get('currentMetrics', [])
                   if item.get('resource', {}).get('name') == 'cpu'), None)
    addresses = {address['targetRef']['name']
                 for item in endpoints.get('items', []) for address in item.get('endpoints', [])
                 if address.get('conditions', {}).get('ready') is True
                 and address.get('targetRef', {}).get('kind') == 'Pod'}
    pods_ready = ready_pods(pods)
    return (metric is not None and status.get('desiredReplicas') == 2
            and deployment.get('status', {}).get('readyReplicas') == 2
            and len(pods_ready) == 2 and set(pods_ready) == addresses)


def wait_for(description, operation, seconds):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        result = operation()
        if result:
            return result
        time.sleep(3)
    raise RuntimeError(f'Timed out waiting for {description}')


def request(base, path, token=None, body=None, key=None, expected=200):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if key:
        headers['Idempotency-Key'] = key
    req = urllib.request.Request(base + path, data=json.dumps(body).encode() if body is not None else None,
                                 headers=headers)
    with urllib.request.urlopen(req, timeout=20) as response:
        if response.status != expected:
            raise RuntimeError(f'Unexpected HTTP status {response.status}')
        return json.load(response)


def authenticate(username):
    data = urllib.parse.urlencode(dict(client_id='reference-cli', grant_type='password',
                                      username=username, password='local-demo-only')).encode()
    req = urllib.request.Request('http://localhost:8088/realms/reference/protocol/openid-connect/token', data=data)
    with urllib.request.urlopen(req, timeout=15) as response:
        return json.load(response)['access_token']


@contextmanager
def port_forward(pod):
    # A socket chosen locally is immediately handed to kubectl; any bind race fails visibly.
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        port = sock.getsockname()[1]
    process = subprocess.Popen([*KUBE, 'port-forward', '--address=127.0.0.1', 'pod/' + pod,
                                f'{port}:8080'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        def listening():
            if process.poll() is not None:
                raise RuntimeError('Pod port-forward exited unexpectedly')
            with socket.socket() as probe:
                probe.settimeout(0.3)
                return probe.connect_ex(('127.0.0.1', port)) == 0
        wait_for('pod port-forward', listening, 15)
        yield f'http://127.0.0.1:{port}'
    finally:
        stop(process)


def stop(process):
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)


def cross_pod_idempotency(pods):
    alice, manager = authenticate('alice'), authenticate('manager')
    with ExitStack() as stack:
        bases = [stack.enter_context(port_forward(pod)) for pod in pods]
        before = request(bases[0], '/api/v1/inventory/BOOK-1', alice)['available']
        if before < 1:
            raise RuntimeError('Synthetic BOOK-1 needs one available unit; do not reset data automatically')
        key = 'scale-' + str(uuid4())
        barrier = threading.Barrier(2, timeout=15)
        def create(base):
            barrier.wait()
            return request(base, '/api/v1/orders', alice, {'sku': 'BOOK-1', 'quantity': 1}, key, 201)
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            orders = list(pool.map(create, bases))
        if orders[0] != orders[1]:
            raise RuntimeError('Identical concurrent cross-pod requests returned different orders')
        order = orders[0]
        after = request(bases[1], '/api/v1/inventory/BOOK-1', alice)['available']
        if after != before - 1:
            raise RuntimeError('Concurrent order did not decrement stock exactly once')
        # Compensate through business operations, retaining the synthetic order/audit history.
        proposal = request(bases[0], '/api/v1/returns', alice,
                           {'orderId': order['id'], 'reason': 'Synthetic HPA verification'}, key + '-return', 201)
        approved = request(bases[1], '/api/v1/returns/' + proposal['id'] + '/approve', manager,
                           {'version': proposal['version']})
        request(bases[0], '/api/v1/returns/' + proposal['id'] + '/execute', manager,
                {'version': approved['version']}, key + '-execute')
        if request(bases[1], '/api/v1/inventory/BOOK-1', alice)['available'] != before:
            raise RuntimeError('Synthetic compensation did not restore stock')
        return {'pods': pods, 'orderId': order['id'], 'sameResult': True,
                'stockDecrement': 1, 'stockRestored': True}


def main():
    def interrupted(signum, frame):
        raise SystemExit(f'Stopped by signal {signum}; cleaning up owned processes')
    signal.signal(signal.SIGTERM, interrupted)
    # Guard before workload: this kubeconfig must name only the loopback kind API server.
    config = json.loads(subprocess.check_output([*KUBE, 'config', 'view', '--minify', '-o', 'json'], text=True, timeout=15))
    server = config['clusters'][0]['cluster']['server']
    if not re.fullmatch(r'https://127\.0\.0\.1:\d+', server):
        raise RuntimeError('Refusing a non-loopback Kubernetes API server')
    hpa = document('hpa/commerce-api')
    spec = hpa['spec']
    if spec.get('minReplicas') != 1 or spec.get('maxReplicas') != 2:
        raise RuntimeError('Expected canonical HPA range 1..2; no automatic patching')
    print('Waiting for idle baseline: one desired and ready replica.', flush=True)
    def baseline():
        hpa, deployment, pods = document('hpa/commerce-api'), document('deployment/commerce-api'), document('pods')
        return (hpa.get('status', {}).get('desiredReplicas') == 1
                and deployment.get('status', {}).get('readyReplicas') == 1 and len(ready_pods(pods)) == 1)
    wait_for('genuine idle HPA baseline (no manual scaling)', baseline, 300)
    report = {'result': 'FAIL', 'context': CONTEXT, 'baselineReadyReplicas': 1}
    load = None
    report_path = ROOT / 'reports/kind-scale-check.json'
    load_path = ROOT / 'reports/kind-scale-load.json'
    try:
        with load_path.open('w') as output:
            load = subprocess.Popen([sys.executable, str(ROOT / 'scripts/kind-load.py'), '--seconds', '300',
                                     '--workers', '16', '--rps', '300'], stdout=output)
            def scale_up():
                if load.poll() is not None:
                    raise RuntimeError('Load ended before verified scale-up')
                hpa, deployment, pods = document('hpa/commerce-api'), document('deployment/commerce-api'), document('pods')
                endpoints = json.loads(kube('get', 'endpointslices', '-l', 'kubernetes.io/service-name=commerce-api', '-o', 'json'))
                return ready_pods(pods) if scaled(hpa, deployment, pods, endpoints) else None
            pods = wait_for('CPU-driven HPA 1→2 and two ready service endpoints', scale_up, 240)
            report.update(scaledReadyReplicas=2, hpa=document('hpa/commerce-api')['status'],
                          concurrency=cross_pod_idempotency(pods))
            # Keep the workload's bounded run to collect its complete error counters.
            code = load.wait(timeout=330)
        workload = json.loads(load_path.read_text())
        if code != 0 or workload.get('failed') != 0 or not workload.get('ok'):
            raise RuntimeError('Authenticated GET workload had failures; inspect kind-scale-load.json')
        report.update(result='PASS', workload=workload)
    finally:
        if load:
            stop(load)
        report_path.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))
    print('Load stopped. HPA remains active and may return to one replica after cooldown; cluster retained.')


if __name__ == '__main__':
    main()
