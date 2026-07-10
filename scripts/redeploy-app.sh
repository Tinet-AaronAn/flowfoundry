#!/usr/bin/env bash
# 部署 flowfoundry-app 业务 Worker（Temporal + iframe 壳，无平台 API）— http://127.0.0.1:8082/
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export PATH="/opt/homebrew/bin:/opt/homebrew/opt/openjdk@17/bin:$PATH"
# shellcheck source=lib/java-daemon.sh
source "$ROOT/scripts/lib/java-daemon.sh"

SCENARIO="${SCENARIO:-ai-collection-strategy}"
APP_PORT="${APP_PORT:-8082}"
PLATFORM_URL="${FLOWFOUNDRY_PLATFORM_URL:-http://127.0.0.1:8081}"
SCENARIO_DIR="${SCENARIO_DIR:-$ROOT/examples/${SCENARIO}}"
MAVEN_MODULE="${MAVEN_MODULE:-examples/${SCENARIO}}"
JAR="$SCENARIO_DIR/target/ai-collection-strategy-demo-1.0.4.jar"
RUN_DIR="$ROOT/.local/run"
LOG="$RUN_DIR/app.log"
PIDFILE="$RUN_DIR/app.pid"
REGISTRY="$SCENARIO_DIR/config/activities-registry.yaml"

mkdir -p "$RUN_DIR"

require_infra() {
  if ! redis-cli -p "${REDIS_PORT:-6379}" ping >/dev/null 2>&1; then
    echo "[flowfoundry] Redis not reachable on :${REDIS_PORT:-6379} — run: ./scripts/local-dev.sh infra"
    exit 1
  fi
  if ! temporal operator cluster health --address "${TEMPORAL_HOST:-127.0.0.1:7233}" >/dev/null 2>&1; then
    echo "[flowfoundry] Temporal not reachable — run: ./scripts/local-dev.sh infra"
    exit 1
  fi
}

verify_listener() {
  verify_daemon_listener "$PIDFILE" "$APP_PORT" "Worker" "$LOG"
}

stop_port_listener() {
  if [[ -f "$PIDFILE" ]]; then
    pid="$(tr -d '\n' < "$PIDFILE" || true)"
    if [[ -n "${pid:-}" ]] && kill -0 "$pid" 2>/dev/null; then
      echo "[flowfoundry] stopping app pid $pid..."
      kill "$pid" 2>/dev/null || true
    fi
    rm -f "$PIDFILE"
  fi

  pid="$(lsof -tiTCP:"$APP_PORT" -sTCP:LISTEN 2>/dev/null || true)"
  if [[ -n "${pid:-}" ]]; then
    echo "[flowfoundry] freeing :$APP_PORT (pid $pid)..."
    kill "$pid" 2>/dev/null || true
  fi

  for _ in $(seq 1 20); do
    if ! lsof -nP -iTCP:"$APP_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
      echo "[flowfoundry] port :$APP_PORT is free"
      return 0
    fi
    sleep 1
  done

  echo "[flowfoundry] port :$APP_PORT still busy"
  lsof -nP -iTCP:"$APP_PORT" -sTCP:LISTEN || true
  exit 1
}

build_app() {
  echo "[flowfoundry] mvn package (scenario=${SCENARIO}, app-only — does not rebuild :8081 platform)..."
  if ! (cd "$ROOT" && mvn -pl "$MAVEN_MODULE" -am -DskipTests package); then
    echo "[flowfoundry] build failed — run ./scripts/redeploy-worker.sh first if core changed"
    exit 1
  fi
  [[ -f "$JAR" ]] || { echo "[flowfoundry] jar not found: $JAR"; exit 1; }
}

start_app() {
  if ! curl -sf --noproxy '*' "${PLATFORM_URL}/actuator/health" >/dev/null 2>&1; then
    echo "[flowfoundry] warning: platform ${PLATFORM_URL} is not healthy — run ./scripts/redeploy-worker.sh first"
  fi
  echo "[flowfoundry] starting scenario=${SCENARIO} on :$APP_PORT (platform=${PLATFORM_URL})..."
  start_java_daemon "$PIDFILE" "$LOG" java -jar "$JAR" \
    --server.port="$APP_PORT" \
    --flowfoundry.run-mode=worker \
    --flowfoundry.platform.base-url="${PLATFORM_URL}" \
    --flowfoundry.platform.api-key="${FLOWFOUNDRY_API_KEY:-local-admin-key}" \
    --flowfoundry.platform.namespace="${FLOWFOUNDRY_NAMESPACE:-ai-collection-strategy}" \
    --platform.activity-registry.path="file:$REGISTRY" \
    --flowfoundry.activity-registry.path="file:$REGISTRY" \
    --temporal.host="${TEMPORAL_HOST:-127.0.0.1:7233}" \
    --spring.data.redis.host="${REDIS_HOST:-127.0.0.1}" \
    --spring.data.redis.port="${REDIS_PORT:-6379}"

  for _ in $(seq 1 45); do
    if curl -sf --noproxy '*' "http://127.0.0.1:$APP_PORT/actuator/health" >/dev/null 2>&1; then
      echo "[flowfoundry] ready http://127.0.0.1:$APP_PORT/actuator/health"
      verify_listener
      return 0
    fi
    sleep 1
  done

  echo "[flowfoundry] failed to become healthy — see $LOG"
  tail -40 "$LOG" || true
  exit 1
}

build_app
require_infra
stop_port_listener
start_app

echo ""
echo "业务 Worker 部署完成（Worker 模式，平台 API 在 ${PLATFORM_URL}）："
echo "  iframe 业务壳: http://127.0.0.1:$APP_PORT/app/workflow-admin.html"
echo "  平台建模器:    ${PLATFORM_URL}/"
echo "场景模块: examples/${SCENARIO}"
