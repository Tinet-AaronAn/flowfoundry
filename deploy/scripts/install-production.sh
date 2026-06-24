#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

echo "==> Creating namespaces"
kubectl apply -f "$ROOT/deploy/k8s/namespaces.yaml"

echo "==> Apply secrets (edit deploy/k8s/secrets.example.yaml first)"
if [[ "${SKIP_SECRETS:-}" != "1" ]]; then
  kubectl apply -f "$ROOT/deploy/k8s/secrets.example.yaml"
fi

echo "==> Adding Temporal Helm repo"
helm repo add temporalio https://go.temporal.io/helm-charts 2>/dev/null || true
helm repo update

echo "==> Installing Temporal"
helm upgrade --install temporal temporalio/temporal \
  -n temporal \
  -f "$ROOT/deploy/helm/temporal/values-production.yaml"

echo "==> Creating Temporal namespace: call-campaign"
kubectl exec -n temporal deploy/temporal-admintools -- \
  tctl --namespace call-campaign namespace register || true

echo "==> Building Activity Worker image (local)"
cd "$ROOT/worker"
mvn -q -DskipTests package
docker build -t call-campaign-worker:1.0.0 .

echo "==> Deploy Activity Worker"
kubectl create configmap activities-registry \
  --from-file=activities-registry.yaml="$ROOT/registry/activities-registry.yaml" \
  -n bpm --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f "$ROOT/deploy/k8s/call-campaign-worker.yaml"

echo "==> QuantumBPM Enterprise"
echo "    Configure license + OIDC in deploy/helm/quantumbpm/values-production.yaml"
echo "    helm upgrade --install quantumbpm <enterprise-chart> -n bpm -f deploy/helm/quantumbpm/values-production.yaml"

echo "Done. Import BPMN: bpmn/multi-round-call-campaign.bpmn20.xml into QuantumBPM Modeler"
