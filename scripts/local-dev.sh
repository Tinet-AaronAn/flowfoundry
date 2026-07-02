#!/usr/bin/env bash
# 本地启动（无需 Docker）：Temporal CLI + Redis + Worker
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export PATH="/opt/homebrew/bin:/opt/homebrew/opt/openjdk@17/bin:$PATH"

REDIS_PORT="${REDIS_PORT:-6379}"
WORKER_PORT="${WORKER_PORT:-8081}"
TEMPORAL_UI="${TEMPORAL_UI:-8233}"

mkdir -p "$ROOT/.local/run"

start_redis() {
  if redis-cli -p "$REDIS_PORT" ping >/dev/null 2>&1; then
    echo "[redis] already running on :$REDIS_PORT"
    return
  fi
  echo "[redis] starting..."
  redis-server --port "$REDIS_PORT" --daemonize yes --pidfile "$ROOT/.local/run/redis.pid"
  redis-cli -p "$REDIS_PORT" ping
}

start_temporal() {
  if temporal operator cluster health --address "127.0.0.1:7233" >/dev/null 2>&1; then
    echo "[temporal] already running on :7233"
    return
  fi
  echo "[temporal] starting dev server..."
  nohup temporal server start-dev \
    --namespace call-campaign \
    --namespace default \
    --ip 127.0.0.1 \
    > "$ROOT/.local/run/temporal.log" 2>&1 &
  echo $! > "$ROOT/.local/run/temporal.pid"
  for i in $(seq 1 30); do
    if temporal operator cluster health --address "127.0.0.1:7233" >/dev/null 2>&1; then
      echo "[temporal] ready (UI http://127.0.0.1:$TEMPORAL_UI)"
      return
    fi
    sleep 1
  done
  echo "[temporal] failed to start — see .local/run/temporal.log"
  exit 1
}

ensure_namespace() {
  temporal operator namespace describe call-campaign --address 127.0.0.1:7233 >/dev/null 2>&1 \
    || temporal operator namespace create call-campaign --address 127.0.0.1:7233
}

build_worker() {
  echo "[worker] building..."
  (cd "$ROOT/worker" && mvn -q -DskipTests package)
}

start_worker() {
  if curl -sf "http://127.0.0.1:$WORKER_PORT/actuator/health" >/dev/null 2>&1; then
    echo "[worker] already running on :$WORKER_PORT"
    echo "[worker] after code changes run: ./scripts/redeploy-worker.sh"
    return
  fi
  build_worker
  echo "[worker] starting on :$WORKER_PORT..."
  nohup java -jar "$ROOT/worker/target/call-campaign-worker-"*.jar \
    --server.port="$WORKER_PORT" \
    --temporal.host=127.0.0.1:7233 \
    --temporal.namespace=call-campaign \
    --spring.data.redis.host=127.0.0.1 \
    --spring.data.redis.port="$REDIS_PORT" \
    > "$ROOT/.local/run/worker.log" 2>&1 &
  echo $! > "$ROOT/.local/run/worker.pid"
  for i in $(seq 1 30); do
    if curl -sf "http://127.0.0.1:$WORKER_PORT/actuator/health" >/dev/null 2>&1; then
      echo "[worker] ready http://127.0.0.1:$WORKER_PORT/actuator/health"
      return
    fi
    sleep 1
  done
  echo "[worker] failed — see .local/run/worker.log"
  tail -30 "$ROOT/.local/run/worker.log" || true
  exit 1
}

stop_all() {
  for f in worker temporal redis; do
    pidfile="$ROOT/.local/run/$f.pid"
    if [[ -f "$pidfile" ]]; then
      kill "$(cat "$pidfile")" 2>/dev/null || true
      rm -f "$pidfile"
    fi
  done
  echo "stopped local stack"
}

case "${1:-up}" in
  up)
    start_redis
    start_temporal
    ensure_namespace
    start_worker
    echo ""
    echo "Local stack is up:"
    echo "  Modeler     : http://127.0.0.1:$WORKER_PORT/"
    echo "  Temporal UI : http://127.0.0.1:$TEMPORAL_UI"
    echo "  Worker      : http://127.0.0.1:$WORKER_PORT/actuator/health"
    echo "  Redis       : 127.0.0.1:$REDIS_PORT"
    echo ""
    echo "After code changes, run: ./scripts/redeploy-worker.sh"
    ;;
  redeploy)
    exec "$ROOT/scripts/redeploy-worker.sh"
    ;;
  down) stop_all ;;
  status) "$ROOT/scripts/check-progress.sh" ;;
  *) echo "Usage: $0 {up|down|status|redeploy}"; exit 1 ;;
esac
