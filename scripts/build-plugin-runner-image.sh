#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
mvn -q -pl flowfoundry-sdk/flowfoundry-plugin-runner -am package -DskipTests
docker build -t flowfoundry-plugin-runner:local flowfoundry-sdk/flowfoundry-plugin-runner
echo "Built image flowfoundry-plugin-runner:local"
