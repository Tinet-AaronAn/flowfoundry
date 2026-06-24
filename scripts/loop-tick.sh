#!/usr/bin/env bash
# 动态 Loop 唤醒脚本 — 检查进度，全部通过后退出 0 并停止后续 tick
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if "$ROOT/scripts/check-progress.sh"; then
  echo "AGENT_LOOP_WAKE_PLATFORM {\"prompt\":\"/loop 持续检查进度：运行 scripts/check-progress.sh；若 ALL_GREEN 则报告完成并停止 loop；若有 FAIL 则运行 scripts/local-dev.sh up 修复后重试\",\"status\":\"complete\"}"
  exit 0
fi
echo "AGENT_LOOP_WAKE_PLATFORM {\"prompt\":\"/loop 持续检查进度：运行 scripts/check-progress.sh；若 ALL_GREEN 则报告完成并停止 loop；若有 FAIL 则运行 scripts/local-dev.sh up 修复后重试\",\"status\":\"pending\"}"
exit 1
