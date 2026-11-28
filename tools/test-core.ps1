$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
$java = 'java.exe'; $javac = 'javac.exe'
if ($env:JAVA_HOME) { $java = Join-Path $env:JAVA_HOME 'bin\java.exe'; $javac = Join-Path $env:JAVA_HOME 'bin\javac.exe' }
$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ('odc-test-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tmp | Out-Null
try {
    & $javac -source 8 -target 8 -encoding UTF-8 -d $tmp 'src/main/java/odc/ComputeEngine.java' 'src/main/java/odc/RackLayout.java' 'src/main/java/odc/TopologySearch.java' 'src/test/java/odc/ComputeEngineTest.java' 'src/main/java/odc/PortMap.java' 'src/main/java/odc/Part.java' 'src/main/java/odc/ServerSpec.java' 'src/test/java/odc/ServerSpecTest.java' 'src/main/java/odc/PatchCordMesh.java' 'src/test/java/odc/PatchCordMeshTest.java' 'src/main/java/odc/Workload.java' 'src/main/java/odc/TaskEngine.java' 'src/main/java/odc/EconomyEngine.java' 'src/test/java/odc/TaskEngineTest.java' 'src/test/java/odc/EconomyEngineTest.java' 'src/main/java/odc/TaskActions.java' 'src/test/java/odc/TaskActionsTest.java' 'src/main/java/odc/PoolResources.java' 'src/test/java/odc/PoolResourcesTest.java' 'src/main/java/odc/FixedResources.java' 'src/test/java/odc/FixedResourcesTest.java' 'src/main/java/odc/DataOSUiState.java' 'src/test/java/odc/DataOSUiStateTest.java' 'src/main/java/odc/EventJournal.java' 'src/test/java/odc/EventJournalTest.java' 'src/main/java/odc/DashboardStats.java' 'src/test/java/odc/DashboardStatsTest.java' 'src/main/java/odc/NetworkEquipment.java' 'src/main/java/odc/PhysicalTopology.java' 'src/test/java/odc/PhysicalTopologyTest.java' 'src/main/java/odc/LogicalLinks.java' 'src/main/java/odc/LanPorts.java' 'src/test/java/odc/LogicalLinksTest.java' 'src/main/java/odc/SwitchLimits.java' 'src/main/java/odc/BandwidthEngine.java' 'src/main/java/odc/BandwidthTopology.java' 'src/main/java/odc/BandwidthCache.java' 'src/test/java/odc/BandwidthEngineTest.java' 'src/main/java/odc/NetworkMonitor.java' 'src/main/java/odc/NetworkMapLayout.java' 'src/test/java/odc/NetworkMonitorTest.java' 'src/main/java/odc/MapEdits.java' 'src/test/java/odc/MapEditsTest.java' 'src/main/java/odc/NetworkAdmission.java' 'src/test/java/odc/NetworkAdmissionTest.java' 'src/test/java/odc/SwitchLimitsTest.java' 'src/main/java/odc/TerminalPower.java' 'src/test/java/odc/TerminalPowerTest.java' 'src/main/java/odc/PowerEngine.java' 'src/test/java/odc/PowerEngineTest.java' 'src/main/java/odc/TransformerCore.java' 'src/test/java/odc/TransformerCoreTest.java' 'src/test/java/odc/HardwarePowerTest.java'
    if ($LASTEXITCODE -ne 0) { throw 'Core compilation failed.' }
    & $java -cp $tmp odc.ComputeEngineTest
    if ($LASTEXITCODE -ne 0) { throw 'Core tests failed.' }
    & $java -cp $tmp odc.ServerSpecTest
    if ($LASTEXITCODE -ne 0) { throw 'Server hardware tests failed.' }
    & $java -cp $tmp odc.PatchCordMeshTest
    if ($LASTEXITCODE -ne 0) { throw 'Patch mesh tests failed.' }
    & $java -cp $tmp odc.TaskEngineTest
    if ($LASTEXITCODE -ne 0) { throw 'Task engine tests failed.' }
    & $java -cp $tmp odc.EconomyEngineTest
    if ($LASTEXITCODE -ne 0) { throw 'Economy tests failed.' }
    & $java -cp $tmp odc.TaskActionsTest
    if ($LASTEXITCODE -ne 0) { throw 'Task action tests failed.' }
    & $java -cp $tmp odc.PoolResourcesTest
    if ($LASTEXITCODE -ne 0) { throw 'Pool catalog tests failed.' }
    & $java -cp $tmp odc.FixedResourcesTest
    if ($LASTEXITCODE -ne 0) { throw 'Fixed resource tests failed.' }
    & $java -cp $tmp odc.DataOSUiStateTest
    if ($LASTEXITCODE -ne 0) { throw 'DataOS UI tests failed.' }
    & $java -cp $tmp odc.EventJournalTest
    if ($LASTEXITCODE -ne 0) { throw 'Event journal tests failed.' }
    & $java -cp $tmp odc.DashboardStatsTest
    if ($LASTEXITCODE -ne 0) { throw 'Dashboard stats tests failed.' }
    & $java -cp $tmp odc.PhysicalTopologyTest
    if ($LASTEXITCODE -ne 0) { throw 'Physical topology tests failed.' }
    & $java -cp $tmp odc.LogicalLinksTest
    if ($LASTEXITCODE -ne 0) { throw 'Logical links tests failed.' }
    & $java -cp $tmp odc.BandwidthEngineTest
    if ($LASTEXITCODE -ne 0) { throw 'Bandwidth engine tests failed.' }
    & $java -cp $tmp odc.NetworkMonitorTest
    if ($LASTEXITCODE -ne 0) { throw 'Network monitor tests failed.' }
    & $java -cp $tmp odc.MapEditsTest
    if ($LASTEXITCODE -ne 0) { throw 'Map editor tests failed.' }
    & $java -cp $tmp odc.NetworkAdmissionTest
    if ($LASTEXITCODE -ne 0) { throw 'Network admission tests failed.' }
    & $java -cp $tmp odc.SwitchLimitsTest
    if ($LASTEXITCODE -ne 0) { throw 'Switch catalogue tests failed.' }
    & $java -cp $tmp odc.TerminalPowerTest
    if ($LASTEXITCODE -ne 0) { throw 'Terminal power tests failed.' }
    & $java -cp $tmp odc.PowerEngineTest
    if ($LASTEXITCODE -ne 0) { throw 'Power engine tests failed.' }
    & $java -cp $tmp odc.TransformerCoreTest
    if ($LASTEXITCODE -ne 0) { throw 'Transformer tests failed.' }
    & $java -cp $tmp odc.HardwarePowerTest
    if ($LASTEXITCODE -ne 0) { throw 'Hardware power tests failed.' }
} finally { Remove-Item -Recurse -Force $tmp }
