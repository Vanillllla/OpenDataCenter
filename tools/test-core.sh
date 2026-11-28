#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
JAVA=java; JAVAC=javac
if [[ -n "${JAVA_HOME:-}" ]]; then JAVA="$JAVA_HOME/bin/java"; JAVAC="$JAVA_HOME/bin/javac"; fi
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
"$JAVAC" -source 8 -target 8 -encoding UTF-8 -d "$TMP" src/main/java/odc/ComputeEngine.java src/main/java/odc/RackLayout.java src/main/java/odc/TopologySearch.java src/test/java/odc/ComputeEngineTest.java src/main/java/odc/PortMap.java src/main/java/odc/Part.java src/main/java/odc/ServerSpec.java src/test/java/odc/ServerSpecTest.java src/main/java/odc/PatchCordMesh.java src/test/java/odc/PatchCordMeshTest.java src/main/java/odc/Workload.java src/main/java/odc/TaskEngine.java src/main/java/odc/EconomyEngine.java src/test/java/odc/TaskEngineTest.java src/test/java/odc/EconomyEngineTest.java src/main/java/odc/TaskActions.java src/test/java/odc/TaskActionsTest.java src/main/java/odc/PoolResources.java src/test/java/odc/PoolResourcesTest.java src/main/java/odc/FixedResources.java src/test/java/odc/FixedResourcesTest.java src/main/java/odc/DataOSUiState.java src/test/java/odc/DataOSUiStateTest.java src/main/java/odc/EventJournal.java src/test/java/odc/EventJournalTest.java src/main/java/odc/DashboardStats.java src/test/java/odc/DashboardStatsTest.java src/main/java/odc/NetworkEquipment.java src/main/java/odc/PhysicalTopology.java src/test/java/odc/PhysicalTopologyTest.java src/main/java/odc/LogicalLinks.java src/main/java/odc/LanPorts.java src/test/java/odc/LogicalLinksTest.java src/main/java/odc/SwitchLimits.java src/main/java/odc/BandwidthEngine.java src/main/java/odc/BandwidthTopology.java src/main/java/odc/BandwidthCache.java src/test/java/odc/BandwidthEngineTest.java src/main/java/odc/NetworkMonitor.java src/main/java/odc/NetworkMapLayout.java src/test/java/odc/NetworkMonitorTest.java src/main/java/odc/MapEdits.java src/test/java/odc/MapEditsTest.java src/main/java/odc/NetworkAdmission.java src/test/java/odc/NetworkAdmissionTest.java src/test/java/odc/SwitchLimitsTest.java src/main/java/odc/TerminalPower.java src/test/java/odc/TerminalPowerTest.java src/main/java/odc/PowerEngine.java src/test/java/odc/PowerEngineTest.java src/main/java/odc/TransformerCore.java src/test/java/odc/TransformerCoreTest.java src/test/java/odc/HardwarePowerTest.java
"$JAVA" -cp "$TMP" odc.ComputeEngineTest

"$JAVA" -cp "$TMP" odc.ServerSpecTest

"$JAVA" -cp "$TMP" odc.PatchCordMeshTest

"$JAVA" -cp "$TMP" odc.TaskEngineTest
"$JAVA" -cp "$TMP" odc.EconomyEngineTest

"$JAVA" -cp "$TMP" odc.TaskActionsTest

"$JAVA" -cp "$TMP" odc.PoolResourcesTest

"$JAVA" -cp "$TMP" odc.FixedResourcesTest

"$JAVA" -cp "$TMP" odc.DataOSUiStateTest

"$JAVA" -cp "$TMP" odc.EventJournalTest

"$JAVA" -cp "$TMP" odc.DashboardStatsTest

"$JAVA" -cp "$TMP" odc.PhysicalTopologyTest

"$JAVA" -cp "$TMP" odc.LogicalLinksTest

"$JAVA" -cp "$TMP" odc.BandwidthEngineTest

"$JAVA" -cp "$TMP" odc.NetworkMonitorTest

"$JAVA" -cp "$TMP" odc.MapEditsTest

"$JAVA" -cp "$TMP" odc.NetworkAdmissionTest

"$JAVA" -cp "$TMP" odc.SwitchLimitsTest

"$JAVA" -cp "$TMP" odc.TerminalPowerTest

"$JAVA" -cp "$TMP" odc.PowerEngineTest

"$JAVA" -cp "$TMP" odc.TransformerCoreTest

"$JAVA" -cp "$TMP" odc.HardwarePowerTest
