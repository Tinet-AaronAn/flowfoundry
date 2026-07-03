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
    echo "[temporal] UI: http://127.0.0.1:8080 (Docker) or http://127.0.0.1:$TEMPORAL_UI (start-dev)"
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
      echo "[temporal] ready gRPC 127.0.0.1:7233"
      echo "[temporal] UI: http://127.0.0.1:8080 (Docker) or http://127.0.0.1:$TEMPORAL_UI (start-dev)"
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
  echo "[flowfoundry] building scenario ai-collection-strategy..."
  (cd "$ROOT" && mvn -q -pl flowfoundry-app/modules/ai-collection-strategy -am -DskipTests package)
}

start_worker() {
  if curl -sf "http://127.0.0.1:$WORKER_PORT/actuator/health" >/dev/null 2>&1; then
    echo "[flowfoundry] already running on :$WORKER_PORT"
    echo "[flowfoundry] after code changes run: ./scripts/redeploy-worker.sh"
    return
  fi
  build_worker
  echo "[flowfoundry] starting on :$WORKER_PORT..."
  nohup java -jar "$ROOT/flowfoundry-app/modules/ai-collection-strategy/target/ai-collection-strategy-demo-"*.jar \
    --server.port="$WORKER_PORT" \
    --platform.activity-registry.path="file:$ROOT/flowfoundry-app/modules/ai-collection-strategy/config/activities-registry.yaml" \
    --temporal.host=127.0.0.1:7233 \
    --temporal.namespace=call-campaign \
    --temporal.task-queue=ai-collection-strategy \
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
