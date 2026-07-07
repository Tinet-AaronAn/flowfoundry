#!/usr/bin/env bash
# 本地调试：Docker 基础设施（Postgres / Redis / Temporal）+ 宿主机应用（:8081）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export PATH="/Applications/OrbStack.app/Contents/MacOS:/opt/homebrew/bin:/opt/homebrew/opt/openjdk@17/bin:$PATH"

COMPOSE_FILE="$ROOT/deploy/docker-compose.local.yml"
INFRA_SERVICES=(postgres redis temporal temporal-init temporal-ui)

WORKER_PORT="${WORKER_PORT:-8081}"
APP_PORT="${APP_PORT:-8082}"
RUN_DIR="$ROOT/.local/run"

mkdir -p "$RUN_DIR"

ensure_docker() {
  if ! docker info >/dev/null 2>&1; then
    echo "[docker] starting OrbStack..."
    open -a OrbStack
    for _ in $(seq 1 60); do
      docker info >/dev/null 2>&1 && break
      sleep 2
    done
  fi
  docker info >/dev/null 2>&1 || { echo "[docker] not available"; exit 1; }
}

stop_host_worker() {
  if [[ -f "$RUN_DIR/worker.pid" ]]; then
    pid="$(tr -d '\n' < "$RUN_DIR/worker.pid" || true)"
    if [[ -n "${pid:-}" ]] && kill -0 "$pid" 2>/dev/null; then
      echo "[flowfoundry] stopping host worker pid $pid..."
      kill "$pid" 2>/dev/null || true
    fi
    rm -f "$RUN_DIR/worker.pid"
  fi

  pid="$(lsof -tiTCP:"$WORKER_PORT" -sTCP:LISTEN 2>/dev/null || true)"
  if [[ -n "${pid:-}" ]]; then
    echo "[flowfoundry] freeing :$WORKER_PORT (pid $pid)..."
    kill "$pid" 2>/dev/null || true
    sleep 1
  fi
}

stop_docker_worker() {
  if docker compose -f "$COMPOSE_FILE" ps --status running worker 2>/dev/null | grep -q worker; then
    echo "[docker] stopping worker container (local debug uses host JAR on :$WORKER_PORT)..."
    docker compose -f "$COMPOSE_FILE" stop worker >/dev/null
  fi
}

stop_legacy_host_infra() {
  for f in temporal redis; do
    pidfile="$RUN_DIR/$f.pid"
    if [[ -f "$pidfile" ]]; then
      kill "$(cat "$pidfile")" 2>/dev/null || true
      rm -f "$pidfile"
    fi
  done
}

wait_postgres() {
  for _ in $(seq 1 30); do
    if docker compose -f "$COMPOSE_FILE" exec -T postgres pg_isready -U temporal >/dev/null 2>&1; then
      echo "[postgres] ready localhost:5432"
      return 0
    fi
    sleep 1
  done
  echo "[postgres] failed to become ready"
  docker compose -f "$COMPOSE_FILE" logs --tail 20 postgres || true
  exit 1
}

wait_redis() {
  for _ in $(seq 1 20); do
    if redis-cli -p "${REDIS_PORT:-6379}" ping >/dev/null 2>&1; then
      echo "[redis] ready 127.0.0.1:${REDIS_PORT:-6379}"
      return 0
    fi
    sleep 1
  done
  echo "[redis] failed to become ready"
  exit 1
}

wait_temporal() {
  for _ in $(seq 1 60); do
    if temporal operator cluster health --address 127.0.0.1:7233 >/dev/null 2>&1; then
      echo "[temporal] ready gRPC 127.0.0.1:7233"
      return 0
    fi
    sleep 2
  done
  echo "[temporal] failed to become ready"
  docker compose -f "$COMPOSE_FILE" logs --tail 30 temporal || true
  exit 1
}

start_infra() {
  ensure_docker
  stop_legacy_host_infra
  stop_docker_worker
  echo "[docker] starting infrastructure: ${INFRA_SERVICES[*]}"
  docker compose -f "$COMPOSE_FILE" up -d "${INFRA_SERVICES[@]}"
  wait_postgres
  wait_redis
  wait_temporal
  temporal operator namespace describe call-campaign --address 127.0.0.1:7233 >/dev/null 2>&1 \
    || temporal operator namespace create call-campaign --address 127.0.0.1:7233
  echo "[temporal] UI: http://127.0.0.1:8080/"
}

start_worker() {
  if curl -sf --noproxy '*' "http://127.0.0.1:$WORKER_PORT/actuator/health" >/dev/null 2>&1 \
    && curl -sf --noproxy '*' "http://127.0.0.1:$APP_PORT/actuator/health" >/dev/null 2>&1; then
    echo "[flowfoundry] platform :$WORKER_PORT and app :$APP_PORT already healthy"
    echo "[flowfoundry] after code changes run: ./scripts/redeploy-worker.sh && ./scripts/redeploy-app.sh"
    return 0
  fi
  "$ROOT/scripts/redeploy-worker.sh"
  "$ROOT/scripts/redeploy-app.sh"
}

stop_all() {
  stop_host_worker
  stop_legacy_host_infra
  if docker info >/dev/null 2>&1; then
    echo "[docker] stopping compose stack..."
    docker compose -f "$COMPOSE_FILE" down
  fi
  echo "stopped local debug stack"
}

case "${1:-up}" in
  up)
    start_infra
    start_worker
    echo ""
    echo "Local debug stack is up:"
    echo "  Platform    : http://127.0.0.1:$WORKER_PORT/  (flowfoundry-core 建模器 + API Keys)"
    echo "  App shell   : http://127.0.0.1:$APP_PORT/app/workflow-admin.html  (iframe 业务壳)"
    echo "  Temporal UI : http://127.0.0.1:8080/"
    echo "  Health      : http://127.0.0.1:$WORKER_PORT/actuator/health"
    echo "  Postgres    : 127.0.0.1:5432 (flowfoundry)"
    echo "  Redis       : 127.0.0.1:${REDIS_PORT:-6379}"
    echo ""
    echo "After code changes: ./scripts/redeploy-worker.sh && ./scripts/redeploy-app.sh"
    ;;
  infra)
    start_infra
    ;;
  redeploy)
    exec "$ROOT/scripts/redeploy-worker.sh"
    ;;
  down)
    stop_all
    ;;
  status)
    "$ROOT/scripts/check-progress.sh"
    ;;
  *)
    echo "Usage: $0 {up|down|infra|status|redeploy}"
    echo ""
    echo "  up       Docker infra + host app (:8081)"
    echo "  down     Stop host app and docker compose stack"
    echo "  infra    Start Docker infra only (postgres/redis/temporal/ui)"
    echo "  redeploy Rebuild and restart host app"
    exit 1
    ;;
esac
