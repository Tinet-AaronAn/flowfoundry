#!/usr/bin/env bash
# 端到端冒烟：启动 CallCampaignWorkflow
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export PATH="/Applications/OrbStack.app/Contents/MacOS:/opt/homebrew/bin:$PATH"

CAMPAIGN_ID="${1:-campaign-smoke-$(date +%s)}"
WORKFLOW_ID="call-campaign-${CAMPAIGN_ID}"
TEMPORAL_ADDRESS="${TEMPORAL_ADDRESS:-127.0.0.1:7233}"
NAMESPACE="${TEMPORAL_NAMESPACE:-call-campaign}"

echo "Starting workflow id=$WORKFLOW_ID namespace=$NAMESPACE"

temporal workflow start \
  --address "$TEMPORAL_ADDRESS" \
  --namespace "$NAMESPACE" \
  --task-queue call-campaign \
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
