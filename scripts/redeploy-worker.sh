#!/usr/bin/env bash
# 部署 flowfoundry-core 平台（建模器 + Workflow API + 合并 Activity Registry）— http://127.0.0.1:8081/
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export PATH="/opt/homebrew/bin:/opt/homebrew/opt/openjdk@17/bin:$PATH"
# shellcheck source=lib/java-daemon.sh
source "$ROOT/scripts/lib/java-daemon.sh"

SCENARIO="${SCENARIO:-ai-collection-strategy}"
PLATFORM_PORT="${PLATFORM_PORT:-8081}"
MAVEN_MODULE="flowfoundry-core"
JAR="$ROOT/flowfoundry-core/target/flowfoundry-core-1.0.2-exec.jar"
REGISTRY="$ROOT/flowfoundry-app/modules/${SCENARIO}/config/activities-registry.yaml"
RUN_DIR="$ROOT/.local/run"
LOG="$RUN_DIR/platform.log"
PIDFILE="$RUN_DIR/platform.pid"

mkdir -p "$RUN_DIR"

stop_port_listener() {
  if [[ -f "$PIDFILE" ]]; then
    pid="$(tr -d '\n' < "$PIDFILE" || true)"
    if [[ -n "${pid:-}" ]] && kill -0 "$pid" 2>/dev/null; then
      echo "[flowfoundry] stopping platform pid $pid..."
      kill "$pid" 2>/dev/null || true
    fi
    rm -f "$PIDFILE"
  fi

  pid="$(lsof -tiTCP:"$PLATFORM_PORT" -sTCP:LISTEN 2>/dev/null || true)"
  if [[ -n "${pid:-}" ]]; then
    echo "[flowfoundry] freeing :$PLATFORM_PORT (pid $pid)..."
    kill "$pid" 2>/dev/null || true
  fi

  for _ in $(seq 1 20); do
    if ! lsof -nP -iTCP:"$PLATFORM_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
      echo "[flowfoundry] port :$PLATFORM_PORT is free"
      return 0
    fi
    sleep 1
  done

  echo "[flowfoundry] port :$PLATFORM_PORT still busy"
  lsof -nP -iTCP:"$PLATFORM_PORT" -sTCP:LISTEN || true
  exit 1
}

build_app() {
  echo "[flowfoundry] mvn clean install (module=${MAVEN_MODULE})..."
  if ! (cd "$ROOT" && mvn -pl "$MAVEN_MODULE" -am -DskipTests clean install); then
    echo "[flowfoundry] build failed — fix compilation errors above"
    exit 1
  fi
  [[ -f "$JAR" ]] || { echo "[flowfoundry] jar not found: $JAR"; exit 1; }
}

start_app() {
  echo "[flowfoundry] starting platform (flowfoundry-core) on :$PLATFORM_PORT..."
  # 平台（core）没有自己的业务 namespace：业务 workflow 落在各 app 部署契约声明的业务 namespace，
  # 后台建模器调试运行落在系统 namespace（flowfoundry.namespace.system）。--temporal.namespace 仅是
  # 「无任何 Worker 注册」时的中性回退默认值，不能写成某个 app 的 namespace（如 call-campaign）。
  start_java_daemon "$PIDFILE" "$LOG" java -jar "$JAR" \
    --server.port="$PLATFORM_PORT" \
    --flowfoundry.run-mode=platform \
    --flowfoundry.activity-registry.path="file:$REGISTRY" \
    --platform.activity-registry.path="file:$REGISTRY" \
    --flowfoundry.security.dev-namespace="${FLOWFOUNDRY_DEV_NAMESPACE:-default}" \
    --flowfoundry.security.api-keys[0].id=platform-admin \
    --flowfoundry.security.api-keys[0].key="${FLOWFOUNDRY_API_KEY:-local-admin-key}" \
    --flowfoundry.security.api-keys[0].admin=true \
    --flowfoundry.modeler.allow-frame-embedding=true \
    --temporal.host="${TEMPORAL_HOST:-127.0.0.1:7233}" \
    --temporal.namespace="${TEMPORAL_NAMESPACE:-default}" \
    --temporal.task-queue="${TEMPORAL_TASK_QUEUE:-flowfoundry-platform}" \
    --spring.data.redis.host="${REDIS_HOST:-127.0.0.1}" \
    --spring.data.redis.port="${REDIS_PORT:-6379}"

  for _ in $(seq 1 45); do
    if curl -sf --noproxy '*' "http://127.0.0.1:$PLATFORM_PORT/actuator/health" >/dev/null 2>&1; then
      echo "[flowfoundry] ready http://127.0.0.1:$PLATFORM_PORT/actuator/health"
      verify_daemon_listener "$PIDFILE" "$PLATFORM_PORT" "platform" "$LOG"
      return 0
    fi
    sleep 1
  done

  echo "[flowfoundry] failed to become healthy — see $LOG"
  tail -40 "$LOG" || true
  exit 1
}

build_app
stop_port_listener
start_app

echo ""
echo "平台部署完成（scenario=${SCENARIO}），请刷新："
echo "  http://127.0.0.1:$PLATFORM_PORT/"
echo "业务 Worker + iframe 壳: http://127.0.0.1:${APP_PORT:-8082}/app/workflow-admin.html — ./scripts/redeploy-app.sh"
echo "Temporal UI: http://127.0.0.1:8080/ — 见 docs/service-urls.md"
