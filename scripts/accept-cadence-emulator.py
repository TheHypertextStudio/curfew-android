#!/usr/bin/env python3
"""Observe the real default cadence offline on the owned disposable AVD."""
from pathlib import Path
import json
import shutil
import subprocess
import sys
import time

ADB = shutil.which("adb") or str(Path.home() / "Library/Android/sdk/platform-tools/adb")
SERIAL = "emulator-5554"
PACKAGE = "studio.hypertext.curfew"
RUNNER = PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"
TEST = PACKAGE + ".platform.PersistentCadenceJourneyInstrumentedTest"
OUTPUT = Path("build/sunrise-evidence/cadence-offline")


def call(*args):
    return subprocess.run([ADB, "-s", SERIAL, *args], check=True, capture_output=True, timeout=30).stdout


def instrument(method, destination, arguments=None):
    extra_arguments = [part for key, value in (arguments or {}).items() for part in ["-e", key, value]]
    with destination.open("wb") as output:
        try:
            subprocess.run(
                [ADB, "-s", SERIAL, "shell", "am", "instrument", "-w", "-e",
                 "persistentCadenceAcceptance", "true", *extra_arguments, "-e", "class", TEST + "#" + method, RUNNER],
                stdout=output, stderr=subprocess.STDOUT, check=True, timeout=270,
            )
        except subprocess.TimeoutExpired:
            call("shell", "am", "force-stop", PACKAGE)
            raise
    result = destination.read_text()
    if "OK (1 test)" not in result and "OK (1 tests)" not in result:
        raise RuntimeError("Native acceptance did not pass; inspect " + str(destination))


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def main():
    global SERIAL, OUTPUT
    require(sys.argv[1:] in [[], ["--api33"]], "Supported option: --api33")
    api = 33 if sys.argv[1:] else 36
    SERIAL = "emulator-5558" if api == 33 else "emulator-5554"
    if api == 33:
        OUTPUT = Path("build/sunrise-evidence/cadence-offline-api33")
    require(call("emu", "avd", "name").decode().splitlines()[0].strip() == f"Sunrise_API{api}", "The matching owned AVD is required")
    require(call("shell", "getprop", "ro.kernel.qemu").strip() == b"1", "A disposable emulator is required")
    original = {name: call("shell", "settings", "get", "global", name).decode().strip()
                for name in ["airplane_mode_on", "wifi_on", "mobile_data"]}
    OUTPUT.mkdir(parents=True, exist_ok=True)
    (OUTPUT / "original-network.json").write_text(json.dumps(original, indent=2))
    apk_path = call("shell", "pm", "path", PACKAGE).decode().strip().removeprefix("package:")
    require(apk_path.startswith("/data/app/") and apk_path.endswith("/base.apk"), "Expected one installed APK")
    (OUTPUT / "installed-apk.txt").write_bytes(call("shell", "sha256sum", apk_path))
    try:
        print("Step 1: Disconnect the owned emulator.", flush=True)
        call("shell", "cmd", "connectivity", "airplane-mode", "enable")
        call("shell", "svc", "wifi", "disable")
        call("shell", "svc", "data", "disable")
        for _ in range(50):
            status = call("shell", "dumpsys", "connectivity").decode()
            if "Active default network: none" in status:
                break
            time.sleep(0.2)
        else:
            raise RuntimeError("The emulator still has an active default network")
        (OUTPUT / "offline.txt").write_text("Active default network: none\n")
        call("shell", "run-as", PACKAGE, "rm", "-f", "cache/cadence-timeline.jsonl")
        print("Step 2: Observe actual ring, quiet, and ring intervals. This takes three minutes.", flush=True)
        instrument("scheduledAlarmKeepsDefaultCadenceOffline", OUTPUT / "installed.txt")
        for name in ["cadence-timeline.jsonl", "cadence-fixture.json", "cadence-first-ring.png", "cadence-quiet.png", "cadence-second-ring.png"]:
            try:
                (OUTPUT / name).write_bytes(call("exec-out", "run-as", PACKAGE, "cat", "cache/" + name))
            except subprocess.CalledProcessError:
                if name.endswith(".jsonl"):
                    raise
        events = [json.loads(line) for line in (OUTPUT / "cadence-timeline.jsonl").read_text().splitlines()]
        require([event["phase"] for event in events] == ["first-ring", "quiet", "second-ring"], "Missing cadence phases")
        require(all(event["offline"] for event in events), "A cadence phase had a network")
        require(len({event["campaignId"] for event in events}) == 1, "Occurrence identity changed")
        require([event["alarmAudioActive"] for event in events] == [True, False, True], "Audio did not follow the cadence")
        require(events[0]["attempt"] == 1 and events[2]["attempt"] == 2, "Unexpected ring attempts")
        print("Step 3: Native cadence acceptance passed. Evidence is in " + str(OUTPUT), flush=True)
    finally:
        print("Step 4: Clean up the scoped fixture and restore network settings.", flush=True)
        try:
            instrument("cleanupInterruptedFixture", OUTPUT / "cleanup.txt")
        finally:
            call("shell", "cmd", "connectivity", "airplane-mode", "enable" if original["airplane_mode_on"] == "1" else "disable")
            call("shell", "svc", "wifi", "enable" if original["wifi_on"] == "1" else "disable")
            call("shell", "svc", "data", "enable" if original["mobile_data"] == "1" else "disable")
            (OUTPUT / "restored-network.json").write_text(json.dumps(
                {name: call("shell", "settings", "get", "global", name).decode().strip() for name in original}, indent=2))


if __name__ == "__main__":
    main()
