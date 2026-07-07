#!/usr/bin/env bash
# 一键部署本地平台 (:8081) + 业务 Worker (:8082)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

"$ROOT/scripts/redeploy-worker.sh"
"$ROOT/scripts/redeploy-app.sh"

echo ""
echo "本地栈已就绪："
echo "  平台建模器:  http://127.0.0.1:${PLATFORM_PORT:-8081}/"
echo "  业务 iframe: http://127.0.0.1:${APP_PORT:-8082}/app/workflow-admin.html"
echo "  健康检查:    curl --noproxy '*' http://127.0.0.1:8081/actuator/health"
echo "               curl --noproxy '*' http://127.0.0.1:8082/actuator/health"
