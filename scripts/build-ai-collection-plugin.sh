#!/usr/bin/env bash
# Build the official ai-collection-strategy plugin jar (classifier: plugin).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
mvn -q -pl examples/ai-collection-strategy -Pplugin -am package -DskipTests
JAR="$(ls examples/ai-collection-strategy/target/*-plugin.jar | head -1)"
echo "$JAR"
