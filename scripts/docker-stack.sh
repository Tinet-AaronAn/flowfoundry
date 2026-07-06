#!/usr/bin/env bash
# Docker 全栈（含 worker 容器）— 仅用于集成验证，日常本地调试请用 ./scripts/local-dev.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export PATH="/Applications/OrbStack.app/Contents/MacOS:/opt/homebrew/bin:/opt/homebrew/opt/openjdk@17/bin:$PATH"

COMPOSE_FILE="$ROOT/deploy/docker-compose.local.yml"
INFRA_SERVICES=(postgres redis temporal temporal-init temporal-ui)

ensure_docker() {
  if ! docker info >/dev/null 2>&1; then
    echo "[docker] starting OrbStack..."
    open -a OrbStack
    for _ in $(seq 1 60); do
      docker info >/dev/null 2>&1 && break
      sleep 2
    done
  fi
  docker info >/dev/null 2>&1 || { echo "Docker not available"; exit 1; }
}

case "${1:-up}" in
  infra)
    ensure_docker
    echo "[compose] starting infrastructure only..."
    docker compose -f "$COMPOSE_FILE" up -d "${INFRA_SERVICES[@]}"
    docker compose -f "$COMPOSE_FILE" ps
    echo ""
    echo "Infrastructure ready. Start host app with: ./scripts/redeploy-worker.sh"
    echo "  Temporal UI: http://127.0.0.1:8080/"
    ;;
  up)
    ensure_docker
    echo "[compose] building and starting full stack (worker in Docker)..."
    docker compose -f "$COMPOSE_FILE" up -d --build
    echo ""
    echo "Waiting for worker health..."
    for _ in $(seq 1 90); do
      if curl -sf --noproxy '*' http://127.0.0.1:8081/actuator/health >/dev/null 2>&1; then
        echo "Worker is healthy"
        break
      fi
      sleep 2
    done
    temporal operator namespace create call-campaign --address 127.0.0.1:7233 2>/dev/null || true
    docker compose -f "$COMPOSE_FILE" ps
    echo ""
    echo "Endpoints:"
    echo "  Temporal UI   : http://127.0.0.1:8080/"
    echo "  Worker health : http://127.0.0.1:8081/actuator/health"
    echo ""
    echo "Daily local debug: use ./scripts/local-dev.sh (host JAR, faster redeploy)"
    ;;
  down)
    docker compose -f "$COMPOSE_FILE" down
    ;;
  logs)
    docker compose -f "$COMPOSE_FILE" logs -f "${2:-}"
    ;;
  ps)
    docker compose -f "$COMPOSE_FILE" ps
    ;;
  rebuild)
    ensure_docker
    echo "[compose] rebuilding worker image (integration only)..."
    (cd "$ROOT" && mvn -q -pl flowfoundry-app/modules/ai-collection-strategy -am -DskipTests package)
    docker compose -f "$COMPOSE_FILE" build --no-cache worker
    docker compose -f "$COMPOSE_FILE" up -d worker
    ;;
  *)
    echo "Usage: $0 {infra|up|down|logs|ps|rebuild}"
    echo ""
    echo "  infra    Postgres + Redis + Temporal + UI (no worker container)"
    echo "  up       Full stack including worker container (integration / smoke)"
    echo "  rebuild  Rebuild worker image only (slow; not for daily UI work)"
    echo ""
    echo "Local debug (recommended): ./scripts/local-dev.sh up"
    exit 1
    ;;
esac
