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

actual_api=""
package_service=""
activity_service=""
user_unlocked=""
for attempt in {1..6}; do
    if timeout 20 adb wait-for-device; then
        actual_api=$(timeout 10 adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || true)
        package_service=$(timeout 10 adb shell service check package 2>/dev/null | tr -d '\r' || true)
        activity_service=$(timeout 10 adb shell service check activity 2>/dev/null | tr -d '\r' || true)
        user_unlocked=$(timeout 10 adb shell cmd user is-user-unlocked 0 2>/dev/null | tr -d '\r' || true)
    fi

    if [[ "$actual_api" == "$expected_api" ]] &&
        [[ "$package_service" == *"found"* ]] &&
        [[ "$activity_service" == *"found"* ]] &&
        [[ "$user_unlocked" == "true" ]]; then
        break
    fi

    echo "adb health check $attempt failed; expected API $expected_api, got '${actual_api:-no response}'" >&2
    adb kill-server || true
    adb start-server
    sleep 5
done

if [[ "$actual_api" != "$expected_api" ]] ||
    [[ "$package_service" != *"found"* ]] ||
    [[ "$activity_service" != *"found"* ]] ||
    [[ "$user_unlocked" != "true" ]]; then
    echo "The emulator did not expose a healthy unlocked user and required services for API $expected_api." >&2
    exit 1
fi

distribution_path=${distribution,,}
application_apk="app/build/outputs/apk/$distribution_path/debug/app-$distribution_path-debug.apk"
test_apk="app/build/outputs/apk/androidTest/$distribution_path/debug/app-$distribution_path-debug-androidTest.apk"

test -f "$application_apk"
test -f "$test_apk"

# The workflow builds before starting the emulator. Device startup and test
# execution therefore never compete with a Gradle JVM on the two-core runner.
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
