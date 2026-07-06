#!/usr/bin/env bash
# 前端/后端改动后一键重新部署 FlowFoundry（测试地址 http://127.0.0.1:8081/）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export PATH="/opt/homebrew/bin:/opt/homebrew/opt/openjdk@17/bin:$PATH"

SCENARIO="${SCENARIO:-ai-collection-strategy}"
WORKER_PORT="${WORKER_PORT:-8081}"
MAVEN_MODULE="flowfoundry-app/modules/${SCENARIO}"
SCENARIO_DIR="$ROOT/flowfoundry-app/modules/${SCENARIO}"
JAR="$SCENARIO_DIR/target/ai-collection-strategy-demo-1.0.0-SNAPSHOT.jar"
RUN_DIR="$ROOT/.local/run"
LOG="$RUN_DIR/worker.log"
PIDFILE="$RUN_DIR/worker.pid"
REGISTRY="$SCENARIO_DIR/config/activities-registry.yaml"

mkdir -p "$RUN_DIR"

stop_port_listener() {
  if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx 'flowfoundry-worker-local'; then
    echo "[flowfoundry] stopping docker container flowfoundry-worker-local..."
    docker stop flowfoundry-worker-local >/dev/null
  fi

  if [[ -f "$PIDFILE" ]]; then
    pid="$(tr -d '\n' < "$PIDFILE" || true)"
    if [[ -n "${pid:-}" ]] && kill -0 "$pid" 2>/dev/null; then
      echo "[flowfoundry] stopping pid $pid..."
      kill "$pid" 2>/dev/null || true
    fi
    rm -f "$PIDFILE"
  fi

  pid="$(lsof -tiTCP:"$WORKER_PORT" -sTCP:LISTEN 2>/dev/null || true)"
  if [[ -n "${pid:-}" ]]; then
    echo "[flowfoundry] freeing :$WORKER_PORT (pid $pid)..."
    kill "$pid" 2>/dev/null || true
  fi

  for _ in $(seq 1 20); do
    if ! lsof -nP -iTCP:"$WORKER_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
      echo "[flowfoundry] port :$WORKER_PORT is free"
      return 0
    fi
    sleep 1
  done

  echo "[flowfoundry] port :$WORKER_PORT still busy"
  lsof -nP -iTCP:"$WORKER_PORT" -sTCP:LISTEN || true
  exit 1
}

build_app() {
  echo "[flowfoundry] mvn package (scenario=${SCENARIO})..."
  if ! (cd "$ROOT" && mvn -pl "$MAVEN_MODULE" -am -DskipTests package); then
    echo "[flowfoundry] build failed — fix compilation errors above"
    exit 1
  fi
  [[ -f "$JAR" ]] || { echo "[flowfoundry] jar not found: $JAR"; exit 1; }
}

start_app() {
  echo "[flowfoundry] starting scenario=${SCENARIO} on :$WORKER_PORT..."
  nohup java -jar "$JAR" \
    --server.port="$WORKER_PORT" \
    --platform.activity-registry.path="file:$REGISTRY" \
    --temporal.host="${TEMPORAL_HOST:-127.0.0.1:7233}" \
    --temporal.namespace="${TEMPORAL_NAMESPACE:-call-campaign}" \
    --temporal.task-queue="${TEMPORAL_TASK_QUEUE:-ai-collection-strategy}" \
    --spring.data.redis.host="${REDIS_HOST:-127.0.0.1}" \
    --spring.data.redis.port="${REDIS_PORT:-6379}" \
    > "$LOG" 2>&1 &
  echo $! > "$PIDFILE"

  for _ in $(seq 1 45); do
    if curl -sf --noproxy '*' "http://127.0.0.1:$WORKER_PORT/actuator/health" >/dev/null 2>&1; then
      echo "[flowfoundry] ready http://127.0.0.1:$WORKER_PORT/actuator/health"
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
echo "部署完成，请刷新测试页面："
echo "  http://127.0.0.1:$WORKER_PORT/"
echo "场景模块: flowfoundry-app/modules/${SCENARIO}"
echo "Temporal UI: http://127.0.0.1:8080/ — 见 docs/service-urls.md"
