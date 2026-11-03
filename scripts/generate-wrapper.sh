#!/usr/bin/env bash
set -euo pipefail
# Run from project root where Gradle 8.9 is installed.
gradle wrapper --gradle-version 8.9 --distribution-type bin
