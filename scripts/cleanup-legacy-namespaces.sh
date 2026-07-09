#!/usr/bin/env bash
# 清理旧 namespace 及关联数据（Postgres、Redis、Temporal）。仅保留 ai-collection-strategy。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEEP_NAMESPACE="${KEEP_NAMESPACE:-ai-collection-strategy}"
TEMPORAL_ADDRESS="${TEMPORAL_HOST:-127.0.0.1:7233}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-flowfoundry-platform-postgres-1}"
REDIS_CONTAINER="${REDIS_CONTAINER:-flowfoundry-platform-redis-1}"

LEGACY_PLATFORM_NAMESPACES=(
  default
  flowfoundry-system
  call-campaign
  cc-call
  cc-paas
  vcs
)

LEGACY_API_KEY_IDS=(
  cc
  vcs
  system2
  platform-modeler
  my-app
  app-mra03ecj
  addd1
  test-key-debug
)

LEGACY_TEMPORAL_NAMESPACES=(
  flowfoundry-system
  call-campaign
  cc-call
  cc-paas
  vcs
)

legacy_api_key_sql="$(printf "'%s'," "${LEGACY_API_KEY_IDS[@]}")"
legacy_api_key_sql="${legacy_api_key_sql%,}"

legacy_platform_ns_sql="$(printf "'%s'," "${LEGACY_PLATFORM_NAMESPACES[@]}")"
legacy_platform_ns_sql="${legacy_platform_ns_sql%,}"

echo "[cleanup] retaining FlowFoundry namespace: ${KEEP_NAMESPACE}"

if docker ps --format '{{.Names}}' | grep -qx "$POSTGRES_CONTAINER"; then
  echo "[cleanup] purging legacy rows in Postgres (${POSTGRES_CONTAINER})..."
  docker exec -i "$POSTGRES_CONTAINER" psql -U flowfoundry -d flowfoundry <<SQL
DELETE FROM workflow_definition WHERE namespace <> '${KEEP_NAMESPACE}';
DELETE FROM platform_id_registry
  WHERE kind = 'workflow' AND id NOT IN (SELECT id FROM workflow_definition);
DELETE FROM platform_api_key_namespace WHERE namespace <> '${KEEP_NAMESPACE}';
DELETE FROM platform_api_key WHERE id IN (${legacy_api_key_sql});
DELETE FROM platform_api_key k
  WHERE k.id <> 'platform-admin'
    AND k.admin_flag = false
    AND NOT EXISTS (SELECT 1 FROM platform_api_key_namespace n WHERE n.api_key_id = k.id);
DELETE FROM platform_audit_log
  WHERE namespace IS NULL OR namespace <> '${KEEP_NAMESPACE}';
DELETE FROM platform_audit_log
  WHERE resource_id IN (${legacy_api_key_sql}, ${legacy_platform_ns_sql});
DELETE FROM platform_namespace WHERE id <> '${KEEP_NAMESPACE}';
SQL
  echo "[cleanup] Postgres namespaces:"
  docker exec "$POSTGRES_CONTAINER" psql -U flowfoundry -d flowfoundry -c \
    "SELECT id FROM platform_namespace ORDER BY id;"
  echo "[cleanup] Postgres API keys:"
  docker exec "$POSTGRES_CONTAINER" psql -U flowfoundry -d flowfoundry -c \
    "SELECT id, display_name, admin_flag FROM platform_api_key ORDER BY id;"
  echo "[cleanup] Postgres audit log counts:"
  docker exec "$POSTGRES_CONTAINER" psql -U flowfoundry -d flowfoundry -c \
    "SELECT COALESCE(namespace, '<null>') AS namespace, COUNT(*) FROM platform_audit_log GROUP BY 1 ORDER BY 1;"
else
  echo "[cleanup] Postgres container not running — skip DB (Flyway V9/V10 applies on next platform start)"
fi

if docker ps --format '{{.Names}}' | grep -qx "$REDIS_CONTAINER"; then
  echo "[cleanup] flushing Redis run-namespace cache and legacy contracts..."
  run_keys=$(docker exec "$REDIS_CONTAINER" redis-cli --scan --pattern 'flowfoundry:run-ns:*' 2>/dev/null || true)
  if [[ -n "${run_keys:-}" ]]; then
    while IFS= read -r key; do
      [[ -n "$key" ]] && docker exec "$REDIS_CONTAINER" redis-cli DEL "$key" >/dev/null
    done <<< "$run_keys"
  fi
  for key in $(docker exec "$REDIS_CONTAINER" redis-cli --scan --pattern 'flowfoundry:contract:*' 2>/dev/null || true); do
    case "$key" in
      "flowfoundry:contract:${KEEP_NAMESPACE}") ;;
      *) docker exec "$REDIS_CONTAINER" redis-cli DEL "$key" >/dev/null ;;
    esac
  done
  echo "[cleanup] Redis flowfoundry keys:"
  docker exec "$REDIS_CONTAINER" redis-cli --scan --pattern 'flowfoundry:*' | head -20 || true
else
  echo "[cleanup] Redis container not running — skip"
fi

if command -v temporal >/dev/null 2>&1; then
  echo "[cleanup] removing legacy Temporal namespaces..."
  for ns in "${LEGACY_TEMPORAL_NAMESPACES[@]}"; do
    if [[ "$ns" == "$KEEP_NAMESPACE" ]]; then
      continue
    fi
    if temporal operator namespace describe --address "$TEMPORAL_ADDRESS" --namespace "$ns" >/dev/null 2>&1; then
      echo "  deleting Temporal namespace: $ns"
      printf '%s\n' "$ns" | temporal operator namespace delete --address "$TEMPORAL_ADDRESS" --namespace "$ns" -y || true
    fi
  done
  echo "[cleanup] remaining Temporal namespaces:"
  temporal operator namespace list --address "$TEMPORAL_ADDRESS" 2>/dev/null | rg 'NamespaceInfo.Name' || true
else
  echo "[cleanup] temporal CLI not found — skip Temporal namespace delete"
fi

echo "[cleanup] done. Restart platform if it was running: ./scripts/redeploy-worker.sh"
