#!/usr/bin/env bash
# Local plugin runtime: build runner image + redeploy platform with K8s runtime enabled.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export FLOWFOUNDRY_PLUGIN_RUNTIME_ENABLED=true
export FLOWFOUNDRY_PLUGIN_PLATFORM_URL="${FLOWFOUNDRY_PLUGIN_PLATFORM_URL:-http://host.docker.internal:8081}"
"$ROOT/scripts/build-plugin-runner-image.sh"
"$ROOT/scripts/redeploy-worker.sh"
echo "Plugin runtime enabled. Upload a plugin jar via POST /api/admin/plugins then POST .../start"
