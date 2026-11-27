#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
JAVA=java; JAVAC=javac
if [[ -n "${JAVA_HOME:-}" ]]; then JAVA="$JAVA_HOME/bin/java"; JAVAC="$JAVA_HOME/bin/javac"; fi
if ! "$JAVA" -version 2>&1 | grep -q 'version "1\.8\.' || ! "$JAVAC" -version 2>&1 | grep -q '1\.8\.'; then
    echo 'JDK 8 required. Set JAVA_HOME to the Java 8 JDK folder.' >&2; exit 1
fi
if [[ ! -f gradlew || ! -f gradle/wrapper/gradle-wrapper.jar ]]; then
    mkdir -p .odc-mdk/extracted gradle/wrapper
    curl -fL --retry 2 'https://maven.minecraftforge.net/net/minecraftforge/forge/1.12.2-14.23.5.2860/forge-1.12.2-14.23.5.2860-mdk.zip' -o .odc-mdk/forge-mdk.zip
    unzip -oq .odc-mdk/forge-mdk.zip -d .odc-mdk/extracted
    cp .odc-mdk/extracted/gradlew .odc-mdk/extracted/gradlew.bat .
    cp .odc-mdk/extracted/gradle/wrapper/* gradle/wrapper/
fi
chmod +x gradlew
if [[ $# -eq 0 ]]; then set -- build; fi
exec ./gradlew --no-daemon "$@"
