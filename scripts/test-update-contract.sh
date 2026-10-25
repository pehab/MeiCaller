#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/update-contract
java -m jdk.compiler/com.sun.tools.javac.Main -Xlint:all -Werror -d build/update-contract app/src/main/java/de/meiapps/updates/UpdatePolicy.java app/src/main/java/de/meiapps/updates/UpdateMetadata.java tests/updates/UpdateContractChecks.java
java -cp build/update-contract de.meiapps.updates.UpdateContractChecks
