#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
JAVA=java; JAVAC=javac
if [[ -n "${JAVA_HOME:-}" ]]; then JAVA="$JAVA_HOME/bin/java"; JAVAC="$JAVA_HOME/bin/javac"; fi
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
"$JAVAC" -source 8 -target 8 -encoding UTF-8 -d "$TMP" src/main/java/odc/ComputeEngine.java src/main/java/odc/RackLayout.java src/main/java/odc/TopologySearch.java src/test/java/odc/ComputeEngineTest.java src/main/java/odc/ServerSpec.java src/test/java/odc/ServerSpecTest.java
"$JAVA" -cp "$TMP" odc.ComputeEngineTest

"$JAVA" -cp "$TMP" odc.ServerSpecTest
