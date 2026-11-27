param([switch]$RunClient,[switch]$RunServer)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if ($env:JAVA_HOME) { $java = Join-Path $env:JAVA_HOME 'bin\java.exe' } else { $java = (Get-Command java.exe -ErrorAction Stop).Source }
if (!(Test-Path $java)) { throw 'JAVA_HOME does not point to a JDK.' }
$javaHome = Split-Path (Split-Path $java -Parent) -Parent
if (!(Test-Path (Join-Path $javaHome 'bin\javac.exe'))) { throw 'Use JDK 8, not a JRE. Set JAVA_HOME to the JDK folder.' }
$old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
$version = (& $java -version 2>&1 | Out-String)
$ErrorActionPreference = $old
if ($version -notmatch 'version\s+"1\.8\.') { throw "Java 8 required. Set JAVA_HOME to a JDK 8 directory. Detected: $version" }
$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;$env:Path"
if (!(Test-Path '.\gradlew.bat') -or !(Test-Path '.\gradle\wrapper\gradle-wrapper.jar')) {
    $mdkUrl = 'https://maven.minecraftforge.net/net/minecraftforge/forge/1.12.2-14.23.5.2860/forge-1.12.2-14.23.5.2860-mdk.zip'
    $cache = Join-Path $PSScriptRoot '.odc-mdk'
    New-Item -ItemType Directory -Force -Path $cache | Out-Null
    $zip = Join-Path $cache 'forge-mdk.zip'
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -UseBasicParsing -Uri $mdkUrl -OutFile $zip
    $extract = Join-Path $cache 'extracted'
    Expand-Archive -Path $zip -DestinationPath $extract -Force
    Copy-Item (Join-Path $extract 'gradlew.bat') '.\gradlew.bat' -Force
    Copy-Item (Join-Path $extract 'gradlew') '.\gradlew' -Force
    New-Item -ItemType Directory -Force -Path '.\gradle\wrapper' | Out-Null
    Copy-Item (Join-Path $extract 'gradle\wrapper\*') '.\gradle\wrapper' -Force
}
if ($RunClient -and $RunServer) { throw "Choose one launch target." }
if ($RunClient) { & .\gradlew.bat --no-daemon runClient; exit $LASTEXITCODE }
if ($RunServer) { & .\gradlew.bat --no-daemon runServer; exit $LASTEXITCODE }
& .\gradlew.bat --no-daemon build
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host 'Build finished. Find the reobfuscated mod in build\libs.'
Get-ChildItem '.\build\libs\*.jar' | Select-Object Name,Length
