#!/usr/bin/env bash
# Type-checks the data layer (Room entities/DAOs, repositories, transport, container) against tiny stubs. No Android SDK needed.
# This proves the Kotlin compiles; it does NOT prove Room's annotation processing, SQL, or runtime behaviour.
set -euo pipefail
cd "$(dirname "$0")/.."
G="${GRADLE_LIB:-/opt/gradle-8.14.3/lib}"; OUT="${OUT_DIR:-build/typecheck}"
rm -rf "$OUT"; mkdir -p "$OUT"
KC="$G/kotlin-compiler-embeddable-2.0.21.jar:$G/kotlin-stdlib-2.0.21.jar:$G/kotlin-script-runtime-2.0.21.jar:$G/kotlin-reflect-2.0.21.jar:$G/kotlin-daemon-embeddable-2.0.21.jar:$G/trove4j-1.0.20200330.jar:$G/annotations-24.0.1.jar:$G/kotlinx-coroutines-core-jvm-1.6.4.jar"
CO="$G/kotlinx-coroutines-core-jvm-1.6.4.jar"
java -cp "$KC" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -cp "$G/kotlin-stdlib-2.0.21.jar:$CO" -d "$OUT" -jvm-target 17 \
  $(find app/src/main/java -path '*ai2027/domain/*' -name '*.kt') \
  $(find app/src/main/java -path '*ai2027/data/*' -name '*.kt') \
  $(find app/src/main/java -path '*ai2027/net/*' -name '*.kt') \
  $(find app/src/main/java -path '*ai2027/ui/vm/*' -name '*.kt') \
  app/src/main/java/com/chandu/gatesystem/ai2027/AppContainer.kt \
  $(find tools/stubs -name '*.kt') 2>&1 | grep -v "Picked up" || true
echo "typecheck finished (errors, if any, are listed above)"
