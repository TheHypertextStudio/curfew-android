#!/usr/bin/env python3
"""Revoke and restore exact alarms on the owned disposable Android 13 AVD."""
from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path
import json
import sys

sys.dont_write_bytecode = True
spec = spec_from_file_location("process_acceptance", Path(__file__).with_name("accept-process-loss-emulator.py"))
process = module_from_spec(spec)
spec.loader.exec_module(process)
process.cadence.SERIAL = "emulator-5558"
call, require, instrument = process.call, process.require, process.instrument
OUTPUT = Path("build/sunrise-evidence/exact-permission-api33")


def main():
    require(call("emu", "avd", "name").decode().splitlines()[0].strip() == "Sunrise_API33", "The owned API33 AVD is required")
    require(call("shell", "getprop", "ro.kernel.qemu").strip() == b"1", "A disposable emulator is required")
    process.ALARM_UID = call("shell", "run-as", process.PACKAGE, "id", "-u").decode().strip()
    require(process.ALARM_UID.isdigit(), "Expected the installed app UID")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    (OUTPUT / "result.json").unlink(missing_ok=True)
    prepared = False
    try:
        print("Step 1: Save an actual one-night alarm two minutes ahead.", flush=True)
        instrument("prepareProcessLossFixture", OUTPUT / "prepare.txt", {"fixtureDelaySeconds": "120"})
        prepared = True
        fixture = json.loads(call("exec-out", "run-as", process.PACKAGE, "cat", "cache/cadence-fixture.json"))
        require(fixture["scheduleId"].startswith("cadence-process-loss-"), "Unexpected fixture")
        before = process.snapshot(fixture["campaignId"])
        require(before["kind"] == "scheduled", "Expected a future saved alarm")
        print("Step 2: Revoke exact-alarm access and reopen Curfew.", flush=True)
        call("shell", "appops", "set", process.PACKAGE, "SCHEDULE_EXACT_ALARM", "deny")
        (OUTPUT / "revoked-launch.txt").write_bytes(call("shell", "am", "start", "-W", "-n", process.PACKAGE + "/.MainActivity"))
        call("shell", "uiautomator", "dump", "/sdcard/curfew-permission-revoked.xml")
        xml = call("exec-out", "cat", "/sdcard/curfew-permission-revoked.xml")
        (OUTPUT / "revoked-screen.xml").write_bytes(xml)
        require(b"Needs attention" in xml and b"Alarm on" not in xml, "The revoked alarm must not present a ready status")
        require(b"Alarm access is off." in xml, "The repair screen must name the missing access without repeating its status")
        (OUTPUT / "revoked-screen.png").write_bytes(call("exec-out", "screencap", "-p"))
        (OUTPUT / "revoked-alarm-manager.txt").write_bytes(call("shell", "dumpsys", "alarm"))
        instrument("assertExactAlarmRevokedFixture", OUTPUT / "revoked-readiness.txt")
        require(not process.audio_active(), "A future alarm must not start early during permission repair")
        print("Step 3: Restore access and return through ordinary activity launch.", flush=True)
        call("shell", "appops", "set", process.PACKAGE, "SCHEDULE_EXACT_ALARM", "allow")
        (OUTPUT / "restored-launch.txt").write_bytes(call("shell", "am", "start", "-W", "-n", process.PACKAGE + "/.MainActivity"))
        process.wait_for("original scheduled alarm audio after permission restoration", process.audio_active, timeout=150)
        after = process.snapshot(fixture["campaignId"])
        require(after["kind"] == "ringing" and after["attempt"] == 1, "Expected the same first attempt")
        require(after["campaignId"] == before["campaignId"] and after["ringStartedAt"] == before["startsAt"], "Permission repair changed the occurrence or chosen boundary")
        (OUTPUT / "result.json").write_text(json.dumps({"before": before, "after": after,
            "alarmAudioActive": True, "physicalSpeakerEvidence": False}, indent=2))
        print("Step 4: The original occurrence rang after permission restoration.", flush=True)
    finally:
        call("shell", "appops", "set", process.PACKAGE, "SCHEDULE_EXACT_ALARM", "allow")
        if prepared:
            instrument("cleanupInterruptedFixture", OUTPUT / "cleanup.txt")
        call("shell", "rm", "-f", "/sdcard/curfew-permission-revoked.xml")
        print("Step 5: Remove the controlled alarm and restore the emulator's alarm access.", flush=True)


if __name__ == "__main__":
    main()
