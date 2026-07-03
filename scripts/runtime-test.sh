#!/usr/bin/env bash
# FlowInterpreter 端到端运行时测试：POST /api/flows/run → Temporal COMPLETED
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export PATH="/opt/homebrew/bin:/Applications/OrbStack.app/Contents/MacOS:$PATH"

BASE_URL="${BASE_URL:-http://127.0.0.1:8081}"
TEMPORAL_ADDRESS="${TEMPORAL_ADDRESS:-127.0.0.1:7233}"
NAMESPACE="${TEMPORAL_NAMESPACE:-call-campaign}"
TASK_QUEUE="${TEMPORAL_TASK_QUEUE:-ai-collection-strategy}"
WF_ID="${1:-workflow_runtime_smoke_$(date +%s)}"
CAMPAIGN_ID="${2:-campaign-runtime-$(date +%s)}"

echo "==> Health"
if ! curl -sf --noproxy '*' "$BASE_URL/actuator/health" | grep -q UP; then
  echo "FlowFoundry not running — auto redeploy..."
  "$ROOT/scripts/redeploy-worker.sh" >/dev/null
fi
curl -sf --noproxy '*' "$BASE_URL/actuator/health" | grep -q UP || {
  echo "FlowFoundry still not healthy at $BASE_URL"
  exit 1
}

echo "==> Start FlowInterpreterWorkflow id=$WF_ID campaign=$CAMPAIGN_ID"
curl -sf --noproxy '*' -X POST "$BASE_URL/api/flows/run" \
  -H 'Content-Type: application/json' \
  -d "{
  \"flow\": {
    \"dslVersion\": \"1.0\",
    \"flow\": { \"id\": \"RuntimeSmoke\", \"name\": \"Runtime Smoke\", \"version\": \"1.0.0\" },
    \"inputs\": {},
    \"variables\": {},
    \"nodes\": [
      { \"id\": \"Start\", \"kind\": \"START\" },
      { \"id\": \"Import\", \"kind\": \"ACTIVITY\", \"activityType\": \"import-numbers\", \"taskQueue\": \"$TASK_QUEUE\", \"timeout\": \"120s\", \"maxAttempts\": 3, \"inputMapping\": { \"campaignId\": \"campaignId\" } },
      { \"id\": \"Filter\", \"kind\": \"ACTIVITY\", \"activityType\": \"filter-and-split-batches\", \"taskQueue\": \"$TASK_QUEUE\", \"timeout\": \"120s\", \"maxAttempts\": 3, \"inputMapping\": { \"campaignId\": \"campaignId\" } },
      { \"id\": \"End\", \"kind\": \"END\" }
    ],
    \"edges\": [
      { \"from\": \"Start\", \"to\": \"Import\", \"condition\": \"default\" },
      { \"from\": \"Import\", \"to\": \"Filter\", \"condition\": \"default\" },
      { \"from\": \"Filter\", \"to\": \"End\", \"condition\": \"default\" }
    ]
  },
  \"workflowId\": \"$WF_ID\",
  \"businessKey\": \"bk-$CAMPAIGN_ID\",
  \"input\": { \"campaignId\": \"$CAMPAIGN_ID\" }
}" | tee /tmp/flowfoundry-runtime-start.json
echo ""

echo "==> Poll interpreter state"
for i in $(seq 1 45); do
  STATE=$(curl -sf --noproxy '*' "$BASE_URL/api/flows/runs/$WF_ID")
  STATUS=$(echo "$STATE" | sed -n 's/.*"status":"\([^"]*\)".*/\1/p' | head -1)
  NODE=$(echo "$STATE" | sed -n 's/.*"currentNodeId":"\([^"]*\)".*/\1/p' | head -1)
  echo "  [$i] status=$STATUS node=${NODE:-<done>}"
  if [[ "$STATUS" == "COMPLETED" ]]; then
    echo "$STATE" | python3 -m json.tool 2>/dev/null || echo "$STATE"
    break
  fi
  if [[ "$STATUS" == "FAILED" ]]; then
    echo "$STATE"
    exit 1
  fi
  sleep 2
done

if [[ "${STATUS:-}" != "COMPLETED" ]]; then
  echo "Timeout waiting for COMPLETED"
  exit 1
fi

echo ""
echo "==> Temporal workflow (summary)"
temporal workflow describe --address "$TEMPORAL_ADDRESS" --namespace "$NAMESPACE" --workflow-id "$WF_ID"

echo ""
echo "==> Temporal event history (last 20 lines)"
temporal workflow show --address "$TEMPORAL_ADDRESS" --namespace "$NAMESPACE" --workflow-id "$WF_ID" | tail -20

echo ""
echo "OK — open Temporal UI: http://127.0.0.1:8080/namespaces/$NAMESPACE/workflows/$WF_ID (Docker) or :8233 (start-dev)"
