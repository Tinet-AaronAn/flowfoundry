#!/usr/bin/env bash
# 端到端冒烟：启动 Demo 内置 CallCampaignWorkflow（直连 Temporal CLI）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export PATH="/Applications/OrbStack.app/Contents/MacOS:/opt/homebrew/bin:$PATH"

CAMPAIGN_ID="${1:-campaign-smoke-$(date +%s)}"
WORKFLOW_ID="ai-collection-${CAMPAIGN_ID}"
TEMPORAL_ADDRESS="${TEMPORAL_ADDRESS:-127.0.0.1:7233}"
NAMESPACE="${TEMPORAL_NAMESPACE:-ai-collection-strategy}"
TASK_QUEUE="${TEMPORAL_TASK_QUEUE:-ai-collection-strategy}"

echo "==> Ensure FlowFoundry app is up (worker polls $TASK_QUEUE)"
curl -sf --noproxy '*' http://127.0.0.1:8081/actuator/health >/dev/null || {
  echo "Start app first: ./scripts/redeploy-worker.sh"
  exit 1
}

echo "Starting CallCampaignWorkflow id=$WORKFLOW_ID namespace=$NAMESPACE queue=$TASK_QUEUE"

temporal workflow start \
  --address "$TEMPORAL_ADDRESS" \
  --namespace "$NAMESPACE" \
  --task-queue "$TASK_QUEUE" \
  --type CallCampaignWorkflow \
  --workflow-id "$WORKFLOW_ID" \
  --input "\"$CAMPAIGN_ID\""

echo "Waiting for result..."
for i in $(seq 1 60); do
  STATUS=$(temporal workflow describe --address "$TEMPORAL_ADDRESS" --namespace "$NAMESPACE" --workflow-id "$WORKFLOW_ID" --output json 2>/dev/null \
    | grep -oE '"status"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 || true)
  if echo "$STATUS" | grep -q "WORKFLOW_EXECUTION_STATUS_COMPLETED"; then
    echo "Workflow COMPLETED"
    temporal workflow show --address "$TEMPORAL_ADDRESS" --namespace "$NAMESPACE" --workflow-id "$WORKFLOW_ID" | tail -20
    exit 0
  fi
  if echo "$STATUS" | grep -q "WORKFLOW_EXECUTION_STATUS_FAILED"; then
    echo "Workflow FAILED"
    temporal workflow show --address "$TEMPORAL_ADDRESS" --namespace "$NAMESPACE" --workflow-id "$WORKFLOW_ID" | tail -30
    exit 1
  fi
  sleep 2
done

echo "Timeout waiting for workflow"
temporal workflow show --address "$TEMPORAL_ADDRESS" --namespace "$NAMESPACE" --workflow-id "$WORKFLOW_ID" | tail -20
exit 1
