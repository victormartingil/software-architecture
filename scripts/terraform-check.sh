#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# No host CLI upgrade, credentials, backend, or cloud resources.
docker run --rm -v "$PWD/infra/cloud:/workspace" -w /workspace \
  --entrypoint sh hashicorp/terraform:1.10.5 -ec \
  'terraform fmt -check -recursive && terraform init -backend=false -input=false && terraform validate && terraform test'
