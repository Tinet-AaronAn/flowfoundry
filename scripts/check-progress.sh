#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export PATH="/Applications/OrbStack.app/Contents/MacOS:/opt/homebrew/bin:/opt/homebrew/opt/openjdk@17/bin:$PATH"

COMPOSE_FILE="$ROOT/deploy/docker-compose.local.yml"

MODE="${CHECK_MODE:-auto}"
if [[ "$MODE" == "auto" ]]; then
  if docker info >/dev/null 2>&1 && docker compose -f "$COMPOSE_FILE" ps --status running 2>/dev/null | grep -q worker; then
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

check_temporal_ui() {
  curl -sf -o /dev/null http://127.0.0.1:8080/
}

check_app_health() {
  curl -sf --noproxy '*' "http://127.0.0.1:${WORKER_PORT:-8081}/actuator/health" | grep -q UP
}

check_postgres() {
  docker compose -f "$COMPOSE_FILE" exec -T postgres pg_isready -U temporal >/dev/null 2>&1
}

check "java" command -v java >/dev/null
check "maven" command -v mvn >/dev/null
check "temporal-cli" command -v temporal >/dev/null
check "docker" docker info >/dev/null
check "orbstack" test -d /Applications/OrbStack.app

check "app-compile" bash -c "cd '$ROOT' && mvn -q -pl flowfoundry-app/modules/ai-collection-strategy -am -DskipTests package"
check "app-jar" test -f "$ROOT/flowfoundry-app/modules/ai-collection-strategy/target/ai-collection-strategy-demo-1.0.0-SNAPSHOT.jar"
check "demo-registry" test -f "$ROOT/flowfoundry-app/modules/ai-collection-strategy/config/activities-registry.yaml"
bash "$ROOT/scripts/verify-docs.sh" >/dev/null && REPORT+=("OK   doc-paths") || { REPORT+=("FAIL doc-paths"); FAIL=1; }
check "helm-temporal" test -f "$ROOT/deploy/helm/temporal/values-production.yaml"
check "helm-flowfoundry" test -f "$ROOT/deploy/helm/flowfoundry/values-production.yaml"

if [[ "$MODE" == "docker" ]]; then
  check "compose-postgres" docker compose -f "$COMPOSE_FILE" ps postgres | grep -q Up
  check "compose-redis" docker compose -f "$COMPOSE_FILE" ps redis | grep -q Up
  check "compose-temporal" docker compose -f "$COMPOSE_FILE" ps temporal | grep -q Up
  check "compose-worker" docker compose -f "$COMPOSE_FILE" ps worker | grep -q Up
  check "app-health" check_app_health
  check "temporal-ui" check_temporal_ui
  if docker compose -f "$COMPOSE_FILE" --profile full ps flowfoundry-devserver 2>/dev/null | grep -q Up; then
    check "flowfoundry-ui" curl -sf -o /dev/null http://127.0.0.1:9060
  else
    REPORT+=("SKIP flowfoundry-ui (optional: docker compose --profile full up -d flowfoundry-devserver)")
  fi
  check "temporal-health" temporal operator cluster health --address 127.0.0.1:7233
  check "namespace-call-campaign" temporal operator namespace describe call-campaign --address 127.0.0.1:7233
else
  check "compose-postgres" check_postgres
  check "redis-up" redis-cli -p "${REDIS_PORT:-6379}" ping
  check "temporal-up" temporal operator cluster health --address 127.0.0.1:7233
  check "namespace-call-campaign" temporal operator namespace describe call-campaign --address 127.0.0.1:7233
  check "app-health" check_app_health
  check "temporal-ui" check_temporal_ui
fi

echo "=== Progress Check [mode=$MODE] $(date '+%Y-%m-%d %H:%M:%S') ==="
printf '%s\n' "${REPORT[@]}"
echo "================================"

if [[ $FAIL -eq 0 ]]; then
  echo "ALL_GREEN: compile + runtime stack ready"
  echo "Modeler:       http://127.0.0.1:${WORKER_PORT:-8081}/"
  echo "Temporal UI:   http://127.0.0.1:8080/"
  echo "See docs/local-development.md"
else
  echo "PENDING: see FAIL items above"
  echo "Tip: ./scripts/local-dev.sh up  (infra down) or ./scripts/redeploy-worker.sh (app down)"
fi
exit $FAIL
