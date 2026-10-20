#!/usr/bin/env python3
"""Replace the installed APK before a saved alarm on the owned UI AVD."""
from datetime import datetime, timezone
from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path
import hashlib
import json
import subprocess
import sys

sys.dont_write_bytecode = True
spec = spec_from_file_location("process_acceptance", Path(__file__).with_name("accept-process-loss-emulator.py"))
process = module_from_spec(spec)
spec.loader.exec_module(process)
process.cadence.SERIAL = "emulator-5556"
call, require, instrument = process.call, process.require, process.instrument
APK = Path("app/build/outputs/apk/plain/debug/app-plain-debug.apk")
OUTPUT = Path("build/sunrise-evidence/package-replacement")


def main():
    require(b"Sunrise_UI_API35" in call("emu", "avd", "name"), "The owned UI AVD is required")
    require(call("shell", "getprop", "ro.kernel.qemu").strip() == b"1", "A disposable emulator is required")
    require(APK.is_file(), "Build the plain debug APK first")
    process.ALARM_UID = call("shell", "run-as", process.PACKAGE, "id", "-u").decode().strip()
    require(process.ALARM_UID.isdigit(), "Expected the installed app UID")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    (OUTPUT / "result.json").unlink(missing_ok=True)
    prepared = False
    try:
        print("Step 1: Save a real one-night alarm 45 seconds ahead.", flush=True)
        instrument("prepareProcessLossFixture", OUTPUT / "prepare.txt", {"fixtureDelaySeconds": "45"})
        prepared = True
        fixture = json.loads(call("exec-out", "run-as", process.PACKAGE, "cat", "cache/cadence-fixture.json"))
        require(fixture["scheduleId"].startswith("cadence-process-loss-"), "Unexpected fixture")
        (OUTPUT / "fixture.json").write_text(json.dumps(fixture, indent=2))
        before = process.snapshot(fixture["campaignId"])
        prior_process = subprocess.run([process.cadence.ADB, "-s", process.cadence.SERIAL, "shell", "pidof", process.PACKAGE], capture_output=True, timeout=30)
        old_pid = prior_process.stdout.decode().strip() or None
        print("Step 2: Replace the APK through PackageManager before delivery.", flush=True)
        replaced = subprocess.run([process.cadence.ADB, "-s", process.cadence.SERIAL, "install", "-r", str(APK)], check=True, capture_output=True, timeout=120)
        (OUTPUT / "install.txt").write_bytes(replaced.stdout + replaced.stderr)
        starts_at = datetime.fromisoformat(fixture["startsAt"].replace("Z", "+00:00"))
        require(datetime.now(timezone.utc) < starts_at, "Replacement crossed the planned wake time")
        print("Step 3: Observe actual saved alarm audio outside instrumentation.", flush=True)
        process.wait_for("alarm audio after package replacement", process.audio_active, timeout=65)
        after = process.snapshot(fixture["campaignId"])
        require(after["campaignId"] == before["campaignId"], "Replacement changed the occurrence")
        require(after["kind"] == "ringing" and after["attempt"] == 1, "Expected the first ringing interval")
        require(after["ringStartedAt"] == fixture["startsAt"], "Replacement changed the chosen wake boundary")
        new_pid = process.pid()
        require(old_pid is None or old_pid != new_pid, "Package replacement did not replace the old app process")
        (OUTPUT / "services.txt").write_bytes(call("shell", "dumpsys", "activity", "services", process.PACKAGE))
        (OUTPUT / "result.json").write_text(json.dumps({
            "before": before, "after": after, "oldPid": old_pid, "newPid": new_pid,
            "alarmAudioActive": True, "physicalSpeakerEvidence": False,
            "apkSha256": hashlib.sha256(APK.read_bytes()).hexdigest(),
        }, indent=2))
        print("Step 4: The same saved occurrence rang after a real package replacement.", flush=True)
    finally:
        if prepared:
            instrument("cleanupInterruptedFixture", OUTPUT / "cleanup.txt")
        print("Step 5: Remove the controlled alarm.", flush=True)


if __name__ == "__main__":
    main()
