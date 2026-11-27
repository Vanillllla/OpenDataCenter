#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
JAVA=java; JAVAC=javac
if [[ -n "${JAVA_HOME:-}" ]]; then JAVA="$JAVA_HOME/bin/java"; JAVAC="$JAVA_HOME/bin/javac"; fi
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
"$JAVAC" -source 8 -target 8 -encoding UTF-8 -d "$TMP" src/main/java/odc/ComputeEngine.java src/main/java/odc/RackLayout.java src/main/java/odc/TopologySearch.java src/test/java/odc/ComputeEngineTest.java src/main/java/odc/PortMap.java src/main/java/odc/Part.java src/main/java/odc/ServerSpec.java src/test/java/odc/ServerSpecTest.java src/main/java/odc/PatchCordMesh.java src/test/java/odc/PatchCordMeshTest.java src/main/java/odc/Workload.java src/main/java/odc/TaskEngine.java src/main/java/odc/EconomyEngine.java src/test/java/odc/TaskEngineTest.java src/test/java/odc/EconomyEngineTest.java src/main/java/odc/TaskActions.java src/test/java/odc/TaskActionsTest.java src/main/java/odc/PoolResources.java src/test/java/odc/PoolResourcesTest.java src/main/java/odc/FixedResources.java src/test/java/odc/FixedResourcesTest.java
"$JAVA" -cp "$TMP" odc.ComputeEngineTest

"$JAVA" -cp "$TMP" odc.ServerSpecTest

"$JAVA" -cp "$TMP" odc.PatchCordMeshTest

"$JAVA" -cp "$TMP" odc.TaskEngineTest
"$JAVA" -cp "$TMP" odc.EconomyEngineTest

"$JAVA" -cp "$TMP" odc.TaskActionsTest

"$JAVA" -cp "$TMP" odc.PoolResourcesTest

"$JAVA" -cp "$TMP" odc.FixedResourcesTest
