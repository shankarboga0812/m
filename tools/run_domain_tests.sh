#!/usr/bin/env bash
# Compiles ONLY the pure-Kotlin domain layer (no Android dependencies) and runs its JUnit 4 tests on a plain JVM.
# Works without network access by using the Kotlin compiler and JUnit jars bundled inside a Gradle distribution.
#   GRADLE_LIB=/opt/gradle-8.14.3/lib tools/run_domain_tests.sh
# On a normal dev machine just run:  ./gradlew :app:testDebugUnitTest
set -euo pipefail
cd "$(dirname "$0")/.."
G="${GRADLE_LIB:-/opt/gradle-8.14.3/lib}"
OUT="${OUT_DIR:-build/domain-test}"
rm -rf "$OUT"; mkdir -p "$OUT/main" "$OUT/test"
KC="$G/kotlin-compiler-embeddable-2.0.21.jar:$G/kotlin-stdlib-2.0.21.jar:$G/kotlin-script-runtime-2.0.21.jar:$G/kotlin-reflect-2.0.21.jar:$G/kotlin-daemon-embeddable-2.0.21.jar:$G/trove4j-1.0.20200330.jar:$G/annotations-24.0.1.jar:$G/kotlinx-coroutines-core-jvm-1.6.4.jar"
STD="$G/kotlin-stdlib-2.0.21.jar"
HAM="$G/hamcrest-core-1.3.jar"; [ -f "$HAM" ] || HAM="$G/plugins/hamcrest-core-1.3.jar"
JUNIT="$G/junit-4.13.2.jar:$HAM"
kotlinc() { java -cp "$KC" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect "$@"; }
echo "== compiling domain =="
kotlinc -cp "$STD" -d "$OUT/main" -jvm-target 17 $(find app/src/main/java -path '*ai2027/domain/*' -name '*.kt')
echo "== compiling tests =="
kotlinc -cp "$STD:$JUNIT:$OUT/main" -d "$OUT/test" -jvm-target 17 $(find app/src/test/java -name '*.kt')
CLASSES=$(cd "$OUT/test" && find . -name '*Test.class' | sed 's#^\./##; s#\.class$##; s#/#.#g' | grep -v '\$')
echo "== running tests =="
java -cp "$STD:$JUNIT:$OUT/main:$OUT/test:$G/kotlin-reflect-2.0.21.jar" org.junit.runner.JUnitCore $CLASSES
