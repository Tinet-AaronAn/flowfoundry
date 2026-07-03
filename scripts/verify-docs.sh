#!/usr/bin/env bash
# 检查文档/脚本中是否仍引用已删除目录或错误路径
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FAIL=0

RG_GLOBS=(
  --glob '*.md'
  --glob '*.sh'
  --glob '*.js'
  --glob '*.yml'
  --glob '!graphify-out/**'
  --glob '!getstarted/**'
  --glob '!.git/**'
  --glob '!**/target/**'
  --glob '!node_modules/**'
  --glob '!scripts/verify-docs.sh'
)

ban() {
  local label="$1"
  local pattern="$2"
  local hits
  hits=$(rg -n "$pattern" "$ROOT" "${RG_GLOBS[@]}" 2>/dev/null || true)
  if [[ -n "$hits" ]]; then
    echo "FAIL $label"
    echo "$hits"
    FAIL=1
  else
    echo "OK   $label"
  fi
}

echo "=== Doc path verify ==="
ban 'no worker/src paths' 'worker/src/main'
ban 'no flowfoundry-app/src' 'flowfoundry-app/src/main'
ban 'no old app jar path' 'flowfoundry-app/target/flowfoundry-app'
ban 'no FlowFoundryApplication' 'FlowFoundryApplication'
ban 'no root registry yaml' 'registry/activities-registry\.yaml'
ban 'no bpmn imports' 'bpmn/multi-round'
ban 'no cd worker module' 'cd worker[^/]'
ban 'no old java package' 'com\.example\.platform'

if [[ $FAIL -eq 0 ]]; then
  echo "DOC_OK"
else
  echo "DOC_FAIL: fix paths; see docs/service-urls.md"
  exit 1
fi
