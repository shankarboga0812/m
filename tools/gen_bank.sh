#!/usr/bin/env bash
# Regenerates app/src/main/assets/question_bank_v1.json (250 questions per subject, answers computed by code).
set -euo pipefail
cd "$(dirname "$0")/.."
G="${GRADLE_LIB:-/opt/gradle-8.14.3/lib}"; OUT="${OUT_DIR:-build/genbank}"
rm -rf "$OUT"; mkdir -p "$OUT"
KC="$G/kotlin-compiler-embeddable-2.0.21.jar:$G/kotlin-stdlib-2.0.21.jar:$G/kotlin-script-runtime-2.0.21.jar:$G/kotlin-reflect-2.0.21.jar:$G/kotlin-daemon-embeddable-2.0.21.jar:$G/trove4j-1.0.20200330.jar:$G/annotations-24.0.1.jar:$G/kotlinx-coroutines-core-jvm-1.6.4.jar"
STD="$G/kotlin-stdlib-2.0.21.jar"
java -cp "$KC" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -cp "$STD" -d "$OUT" -jvm-target 17 $(find app/src/main/java -path '*ai2027/domain/*' -name '*.kt') tools/genbank/GenBank.kt
java -cp "$STD:$OUT" GenBankKt app/src/main/assets/plan_114.json app/src/main/assets/question_bank_v1.json
