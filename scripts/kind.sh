#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
CLUSTER=architecture-reference
NAMESPACE=architecture-reference
KIND_BIN="${KIND_BIN:-kind}"
export KUBECONFIG="$ROOT/reports/kind.kubeconfig"
mkdir -p "$ROOT/reports"
# Every kubectl invocation targets the dedicated file AND context, never the user's current context.
k() { kubectl --kubeconfig "$KUBECONFIG" --context "kind-$CLUSTER" "$@"; }
load_image() {
  # Docker's containerd store may retain a multi-platform index without all its blobs.
  # Export only the daemon's platform so kind never requests an absent foreign blob.
  if docker image save --help | grep -q -- --platform; then
    local archive_dir platform
    archive_dir="$(mktemp -d "$ROOT/reports/kind-image.XXXXXX")"
    platform="$(docker version --format '{{.Server.Os}}/{{.Server.Arch}}')"
    docker image save --platform "$platform" -o "$archive_dir/image.tar" "$1"
    "$KIND_BIN" load image-archive --name "$CLUSTER" "$archive_dir/image.tar"
    python3 - "$archive_dir" <<'PY'
from pathlib import Path
import sys
directory = Path(sys.argv[1])
(directory / 'image.tar').unlink()
directory.rmdir()
PY
  else
    "$KIND_BIN" load docker-image --name "$CLUSTER" "$1"
  fi
}
case "${1:-}" in
  up)
    for executable in docker "$KIND_BIN" kubectl python3; do command -v "$executable" >/dev/null || { echo "Missing $executable" >&2; exit 1; }; done
    "$KIND_BIN" version | grep -q 'v0.33.0' || { echo 'Use kind v0.33.0 for the pinned node image (KIND_BIN can select a local binary).' >&2; exit 1; }
    if ! "$KIND_BIN" get clusters | grep -qx "$CLUSTER"; then
      python3 - <<'PY'
import socket
for port in (8080,8081,8088):
    with socket.socket() as sock:
        try: sock.bind(('127.0.0.1',port))
        except OSError: raise SystemExit(f'Port {port} occupied. Stop the Compose reference before kind-up.')
PY
      "$KIND_BIN" create cluster --name "$CLUSTER" --config "$ROOT/infra/kubernetes/kind.yaml" --kubeconfig "$KUBECONFIG" --wait 180s
    else
      "$KIND_BIN" export kubeconfig --name "$CLUSTER" --kubeconfig "$KUBECONFIG"
    fi
    for image in architecture-reference/commerce-api:local architecture-reference/ai-service:local architecture-reference/order-analytics:local; do
      docker image inspect "$image" >/dev/null
      load_image "$image"
    done
    for image in pgvector/pgvector:0.8.1-pg16 apache/kafka:4.0.2 quay.io/keycloak/keycloak:26.4.7; do
      docker image inspect "$image" >/dev/null 2>&1 || docker pull "$image"
      load_image "$image"
    done
    k apply -f "$ROOT/infra/kubernetes/namespace.yaml"
    k -n "$NAMESPACE" create configmap postgres-init --from-file=01-init.sql="$ROOT/infra/local/init.sql" --dry-run=client -o yaml | k apply -f -
    k -n "$NAMESPACE" create configmap keycloak-realm --from-file=reference-realm.json="$ROOT/infra/local/realm.json" --dry-run=client -o yaml | k apply -f -
    k apply -f "$ROOT/infra/kubernetes/dependencies.yaml"
    k -n "$NAMESPACE" rollout status statefulset/postgres --timeout=300s
    k -n "$NAMESPACE" rollout status statefulset/kafka --timeout=300s
    k -n "$NAMESPACE" wait --for=condition=complete job/create-topics --timeout=300s
    k -n "$NAMESPACE" rollout status deployment/keycloak --timeout=300s
    k apply -f "$ROOT/infra/kubernetes/apps.yaml"
    # The local tag is mutable: force existing pods to use the freshly loaded image.
    k -n "$NAMESPACE" rollout restart deployment/commerce-api deployment/ai-service deployment/order-analytics
    k apply -f "$ROOT/infra/kubernetes/metrics-server.yaml"
    k -n kube-system rollout status deployment/metrics-server --timeout=180s
    for app in commerce-api ai-service order-analytics; do
      k -n "$NAMESPACE" rollout status "deployment/$app" --timeout=300s
    done
    k -n "$NAMESPACE" get pods,hpa,pdb
    ;;
  down)
    "$KIND_BIN" delete cluster --name "$CLUSTER" --kubeconfig "$KUBECONFIG"
    ;;
  status)
    k -n "$NAMESPACE" get pods,hpa,pdb,pvc
    ;;
  *) echo 'Usage: scripts/kind.sh up|down|status' >&2; exit 2 ;;
esac
