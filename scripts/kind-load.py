#!/usr/bin/env python3
"""Bounded authenticated GET workload with connection reuse and a global rate cap."""
import argparse
from collections import Counter
import concurrent.futures
import http.client
import json
import threading
import time
import urllib.parse
import urllib.request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--seconds', type=int, default=180)
    parser.add_argument('--workers', type=int, default=16)
    parser.add_argument('--rps', type=int, default=300)
    args = parser.parse_args()
    if not 1 <= args.seconds <= 600 or not 1 <= args.workers <= 64 or not 1 <= args.rps <= 1000:
        parser.error('seconds1..600, workers1..64, rps1..1000')
    data = urllib.parse.urlencode(dict(client_id='reference-cli', grant_type='password',
                                      username='alice', password='local-demo-only')).encode()
    def authenticate():
        request = urllib.request.Request('http://localhost:8088/realms/reference/protocol/openid-connect/token', data=data)
        with urllib.request.urlopen(request, timeout=10) as response:
            return json.load(response)
    initial = authenticate()
    credentials = {'token': initial['access_token'], 'expires': time.monotonic() + initial['expires_in'] - 10}
    token_lock = threading.Lock()
    def current_token():
        with token_lock:
            if time.monotonic() >= credentials['expires']:
                refreshed = authenticate()
                credentials.update(token=refreshed['access_token'], expires=time.monotonic()+refreshed['expires_in']-10)
            return credentials['token']
    deadline = time.monotonic() + args.seconds
    rate_lock, result_lock = threading.Lock(), threading.Lock()
    next_slot = time.monotonic()
    counts = Counter()
    def reserve_slot():
        nonlocal next_slot
        with rate_lock:
            slot = max(next_slot, time.monotonic())
            next_slot = slot + 1 / args.rps
        delay = slot - time.monotonic()
        if delay > 0:
            time.sleep(delay)
        return time.monotonic() < deadline
    def worker():
        local_counts = Counter()
        connection = http.client.HTTPConnection('localhost', 8080, timeout=10)
        used = 0
        try:
            while reserve_slot():
                try:
                    connection.request('GET', '/api/v1/inventory/BOOK-1',
                        headers={'Authorization': 'Bearer ' + current_token()})
                    response = connection.getresponse()
                    response.read()
                    local_counts['ok' if response.status == 200 else 'HTTP_' + str(response.status)] += 1
                    used += 1
                    # Rotate periodically so a newly added pod can receive connections.
                    if used == 100:
                        connection.close()
                        connection = http.client.HTTPConnection('localhost', 8080, timeout=10)
                        used = 0
                except (OSError, http.client.HTTPException) as error:
                    local_counts[type(error).__name__] += 1
                    connection.close()
                    connection = http.client.HTTPConnection('localhost', 8080, timeout=10)
                    time.sleep(0.1)
        finally:
            connection.close()
            with result_lock:
                counts.update(local_counts)
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        list(pool.map(lambda _: worker(), range(args.workers)))
    errors = {key: value for key, value in counts.items() if key != 'ok'}
    print(json.dumps({'seconds': args.seconds, 'workers': args.workers, 'rpsCap': args.rps,
                      'ok': counts['ok'], 'failed': sum(errors.values()), 'errors': errors}))
    if errors or not counts['ok']:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
