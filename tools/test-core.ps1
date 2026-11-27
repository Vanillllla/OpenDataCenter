$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
$java = 'java.exe'; $javac = 'javac.exe'
if ($env:JAVA_HOME) { $java = Join-Path $env:JAVA_HOME 'bin\java.exe'; $javac = Join-Path $env:JAVA_HOME 'bin\javac.exe' }
$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ('odc-test-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tmp | Out-Null
try {
    & $javac -source 8 -target 8 -encoding UTF-8 -d $tmp 'src/main/java/odc/ComputeEngine.java' 'src/main/java/odc/RackLayout.java' 'src/main/java/odc/TopologySearch.java' 'src/test/java/odc/ComputeEngineTest.java' 'src/main/java/odc/PortMap.java' 'src/main/java/odc/Part.java' 'src/main/java/odc/ServerSpec.java' 'src/test/java/odc/ServerSpecTest.java' 'src/main/java/odc/PatchCordMesh.java' 'src/test/java/odc/PatchCordMeshTest.java' 'src/main/java/odc/Workload.java' 'src/main/java/odc/TaskEngine.java' 'src/main/java/odc/EconomyEngine.java' 'src/test/java/odc/TaskEngineTest.java' 'src/test/java/odc/EconomyEngineTest.java' 'src/main/java/odc/TaskActions.java' 'src/test/java/odc/TaskActionsTest.java' 'src/main/java/odc/PoolResources.java' 'src/test/java/odc/PoolResourcesTest.java' 'src/main/java/odc/FixedResources.java' 'src/test/java/odc/FixedResourcesTest.java'
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
} finally { Remove-Item -Recurse -Force $tmp }
