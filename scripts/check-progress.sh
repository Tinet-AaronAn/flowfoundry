#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export PATH="/Applications/OrbStack.app/Contents/MacOS:/opt/homebrew/bin:/opt/homebrew/opt/openjdk@17/bin:$PATH"

MODE="${CHECK_MODE:-auto}"
if [[ "$MODE" == "auto" ]]; then
  if docker info >/dev/null 2>&1 && docker compose -f "$ROOT/deploy/docker-compose.local.yml" ps --status running 2>/dev/null | grep -q worker; then
    MODE=docker
  else
    MODE=local
  fi
fi

FAIL=0
REPORT=()

check() {
  local name="$1"
  shift
  if "$@"; then
    REPORT+=("OK   $name")
  else
    REPORT+=("FAIL $name")
    FAIL=1
  fi
}

check "java" command -v java >/dev/null
check "maven" command -v mvn >/dev/null
check "temporal-cli" command -v temporal >/dev/null
check "docker" docker info >/dev/null
check "orbstack" test -d /Applications/OrbStack.app

check "worker-compile" bash -c "cd '$ROOT/worker' && mvn -q -DskipTests package"
check "worker-jar" test -f "$ROOT/worker/target/call-campaign-worker-1.0.0-SNAPSHOT.jar"
check "bpmn-file" test -f "$ROOT/bpmn/multi-round-call-campaign.bpmn20.xml"
check "registry-file" test -f "$ROOT/registry/activities-registry.yaml"
check "helm-temporal" test -f "$ROOT/deploy/helm/temporal/values-production.yaml"
check "helm-flowfoundry" test -f "$ROOT/deploy/helm/flowfoundry/values-production.yaml"

if [[ "$MODE" == "docker" ]]; then
  check "compose-redis" docker compose -f "$ROOT/deploy/docker-compose.local.yml" ps redis | grep -q Up
  check "compose-temporal" docker compose -f "$ROOT/deploy/docker-compose.local.yml" ps temporal | grep -q Up
  check "compose-worker" docker compose -f "$ROOT/deploy/docker-compose.local.yml" ps worker | grep -q Up
  check "worker-health" curl -sf http://127.0.0.1:8081/actuator/health
  check "temporal-ui" curl -sf -o /dev/null http://127.0.0.1:8080
  if docker compose -f "$ROOT/deploy/docker-compose.local.yml" --profile full ps flowfoundry-devserver 2>/dev/null | grep -q Up; then
    check "flowfoundry-ui" curl -sf -o /dev/null http://127.0.0.1:9060
  else
    REPORT+=("SKIP flowfoundry-ui (start with: docker compose --profile full up -d flowfoundry-devserver)")
  fi
  check "temporal-health" temporal operator cluster health --address 127.0.0.1:7233
  check "namespace-call-campaign" temporal operator namespace describe call-campaign --address 127.0.0.1:7233
else
  check "redis-up" redis-cli -p "${REDIS_PORT:-6379}" ping
  check "temporal-up" temporal operator cluster health --address 127.0.0.1:7233
  check "namespace-call-campaign" temporal operator namespace describe call-campaign --address 127.0.0.1:7233
  check "worker-health" curl -sf "http://127.0.0.1:${WORKER_PORT:-8081}/actuator/health"
fi

echo "=== Progress Check [mode=$MODE] $(date '+%Y-%m-%d %H:%M:%S') ==="
printf '%s\n' "${REPORT[@]}"
echo "================================"

if [[ $FAIL -eq 0 ]]; then
  echo "ALL_GREEN: compile + runtime stack ready"
else
  echo "PENDING: see FAIL items above"
fi
exit $FAIL
