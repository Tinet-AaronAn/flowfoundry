#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export PATH="/Applications/OrbStack.app/Contents/MacOS:/opt/homebrew/bin:/opt/homebrew/opt/openjdk@17/bin:$PATH"

COMPOSE_FILE="$ROOT/deploy/docker-compose.local.yml"

ensure_docker() {
  if ! docker info >/dev/null 2>&1; then
    echo "[docker] starting OrbStack..."
    open -a OrbStack
    for i in $(seq 1 60); do
      docker info >/dev/null 2>&1 && break
      sleep 2
    done
  fi
  docker info >/dev/null 2>&1 || { echo "Docker not available"; exit 1; }
}

case "${1:-up}" in
  up)
    ensure_docker
    echo "[compose] building and starting stack..."
    docker compose -f "$COMPOSE_FILE" up -d --build
    echo ""
    echo "Waiting for services..."
    for i in $(seq 1 90); do
      if curl -sf http://127.0.0.1:8081/actuator/health >/dev/null 2>&1; then
        echo "Worker is healthy"
        break
      fi
      sleep 2
    done
    temporal operator namespace create call-campaign --address 127.0.0.1:7233 2>/dev/null || true
    docker compose -f "$COMPOSE_FILE" ps
    echo ""
    echo "Endpoints:"
    echo "  Temporal UI  : http://localhost:8080"
    echo "  FlowFoundry   : http://localhost:9060"
    echo "  Worker health: http://localhost:8081/actuator/health"
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
    docker compose -f "$COMPOSE_FILE" build --no-cache worker
    docker compose -f "$COMPOSE_FILE" up -d worker
    ;;
  *)
    echo "Usage: $0 {up|down|logs|ps|rebuild}"
    echo ""
    echo "Daily UI work: use ./scripts/redeploy-worker.sh (Java on :8081), not docker rebuild."
    exit 1
    ;;
esac
