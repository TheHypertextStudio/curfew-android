#!/usr/bin/env bash

set -euo pipefail

distribution=${1:?Usage: run-instrumented-tests.sh Plain|Gms API_LEVEL}
expected_api=${2:?Usage: run-instrumented-tests.sh Plain|Gms API_LEVEL}

case "$distribution" in
    Plain|Gms) ;;
    *)
        echo "Unsupported distribution: $distribution" >&2
        exit 64
        ;;
esac

case "$expected_api" in
    33|34|35|36) ;;
    *)
        echo "Unsupported API level: $expected_api" >&2
        exit 64
        ;;
esac

# Building while the emulator boots can starve adb on GitHub's two-core
# runners. Build both APKs first, then prove that the device is healthy before
# Android Gradle Plugin performs device discovery.
./gradlew \
    "assemble${distribution}Debug" \
    "assemble${distribution}DebugAndroidTest" \
    --max-workers=2 \
    --no-daemon

actual_api=""
for attempt in {1..6}; do
    if timeout 20 adb wait-for-device; then
        actual_api=$(timeout 10 adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || true)
    fi

    if [[ "$actual_api" == "$expected_api" ]]; then
        break
    fi

    echo "adb health check $attempt failed; expected API $expected_api, got '${actual_api:-no response}'" >&2
    adb kill-server || true
    adb start-server
    sleep 5
done

if [[ "$actual_api" != "$expected_api" ]]; then
    echo "The emulator did not become healthy for API $expected_api." >&2
    exit 1
fi

./gradlew \
    "connected${distribution}DebugAndroidTest" \
    --max-workers=1 \
    --no-daemon
