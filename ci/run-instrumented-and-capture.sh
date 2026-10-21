#!/usr/bin/env bash
set -euo pipefail

./ci/run-instrumented-tests.sh "$@"

if [[ "${2:?Expected API level}" == "35" ]]; then
    python3 -B scripts/capture-welcome-matrix.py \
        --serial emulator-5554 --output build/welcome-ci --include-native-fixtures
fi
