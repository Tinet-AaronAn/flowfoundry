#!/usr/bin/env bash
# 前端/后端改动后一键重新部署本地 Worker（测试地址 http://127.0.0.1:8081/）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export PATH="/opt/homebrew/bin:/opt/homebrew/opt/openjdk@17/bin:$PATH"

WORKER_PORT="${WORKER_PORT:-8081}"
JAR="$ROOT/worker/target/call-campaign-worker-1.0.0-SNAPSHOT.jar"
RUN_DIR="$ROOT/.local/run"
LOG="$RUN_DIR/worker.log"
PIDFILE="$RUN_DIR/worker.pid"

mkdir -p "$RUN_DIR"

stop_port_listener() {
  if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx 'flowfoundry-worker-local'; then
    echo "[worker] stopping docker container flowfoundry-worker-local..."
    docker stop flowfoundry-worker-local >/dev/null
  fi

  if [[ -f "$PIDFILE" ]]; then
    pid="$(tr -d '\n' < "$PIDFILE" || true)"
    if [[ -n "${pid:-}" ]] && kill -0 "$pid" 2>/dev/null; then
      echo "[worker] stopping pid $pid..."
      kill "$pid" 2>/dev/null || true
    fi
    rm -f "$PIDFILE"
  fi

  pid="$(lsof -tiTCP:"$WORKER_PORT" -sTCP:LISTEN 2>/dev/null || true)"
  if [[ -n "${pid:-}" ]]; then
    echo "[worker] freeing :$WORKER_PORT (pid $pid)..."
    kill "$pid" 2>/dev/null || true
  fi

  for _ in $(seq 1 20); do
    if ! lsof -nP -iTCP:"$WORKER_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
      echo "[worker] port :$WORKER_PORT is free"
      return 0
    fi
    sleep 1
  done

  echo "[worker] port :$WORKER_PORT still busy"
  lsof -nP -iTCP:"$WORKER_PORT" -sTCP:LISTEN || true
  exit 1
}

build_worker() {
  echo "[worker] mvn package..."
  (cd "$ROOT/worker" && mvn -q -DskipTests package)
  [[ -f "$JAR" ]] || { echo "[worker] jar not found: $JAR"; exit 1; }
}

start_worker() {
  echo "[worker] starting on :$WORKER_PORT..."
  nohup java -jar "$JAR" \
    --server.port="$WORKER_PORT" \
    --temporal.host="${TEMPORAL_HOST:-127.0.0.1:7233}" \
    --temporal.namespace="${TEMPORAL_NAMESPACE:-call-campaign}" \
    --spring.data.redis.host="${REDIS_HOST:-127.0.0.1}" \
    --spring.data.redis.port="${REDIS_PORT:-6379}" \
    > "$LOG" 2>&1 &
  echo $! > "$PIDFILE"

  for _ in $(seq 1 45); do
    if curl -sf --noproxy '*' "http://127.0.0.1:$WORKER_PORT/actuator/health" >/dev/null 2>&1; then
      echo "[worker] ready http://127.0.0.1:$WORKER_PORT/actuator/health"
      return 0
    fi
    sleep 1
  done

  echo "[worker] failed to become healthy — see $LOG"
  tail -40 "$LOG" || true
  exit 1
}

build_worker
stop_port_listener
start_worker

echo ""
echo "部署完成，请刷新测试页面："
echo "  http://127.0.0.1:$WORKER_PORT/"
