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
package_service=""
activity_service=""
for attempt in {1..6}; do
    if timeout 20 adb wait-for-device; then
        actual_api=$(timeout 10 adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || true)
        package_service=$(timeout 10 adb shell service check package 2>/dev/null | tr -d '\r' || true)
        activity_service=$(timeout 10 adb shell service check activity 2>/dev/null | tr -d '\r' || true)
    fi

    if [[ "$actual_api" == "$expected_api" ]] &&
        [[ "$package_service" == *"found"* ]] &&
        [[ "$activity_service" == *"found"* ]]; then
        break
    fi

    echo "adb health check $attempt failed; expected API $expected_api, got '${actual_api:-no response}'" >&2
    adb kill-server || true
    adb start-server
    sleep 5
done

if [[ "$actual_api" != "$expected_api" ]] ||
    [[ "$package_service" != *"found"* ]] ||
    [[ "$activity_service" != *"found"* ]]; then
    echo "The emulator did not expose healthy API, package, and activity services for API $expected_api." >&2
    exit 1
fi

distribution_path=${distribution,,}
application_apk="app/build/outputs/apk/$distribution_path/debug/app-$distribution_path-debug.apk"
test_apk="app/build/outputs/apk/androidTest/$distribution_path/debug/app-$distribution_path-debug-androidTest.apk"

test -f "$application_apk"
test -f "$test_apk"

# A second Gradle configuration can starve newer emulator images until
# system_server restarts. Install the APKs that the first build produced and
# invoke the standard AndroidJUnitRunner without another JVM competing for CPU.
timeout 180 adb install --no-streaming -r -t "$application_apk"
timeout 180 adb install --no-streaming -r -t "$test_apk"
adb logcat -c

instrumentation_output=$(
    timeout 300 adb shell am instrument -w -r \
        studio.hypertext.curfew.test/androidx.test.runner.AndroidJUnitRunner |
        tr -d '\r'
)
printf '%s\n' "$instrumentation_output"

if ! grep -Eq '^OK \([1-9][0-9]* tests?\)$' <<<"$instrumentation_output"; then
    echo "AndroidJUnitRunner did not report a successful non-empty test run." >&2
    timeout 30 adb logcat -d -v threadtime '*:E' >&2 || true
    exit 1
fi
