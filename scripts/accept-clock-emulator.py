#!/usr/bin/env python3
"""Move only the owned AVD's wall clock while an alarm is already ringing."""
from datetime import datetime, timezone
from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path
import json
import sys
import time

sys.dont_write_bytecode = True
spec = spec_from_file_location("process_acceptance", Path(__file__).with_name("accept-process-loss-emulator.py"))
process = module_from_spec(spec)
spec.loader.exec_module(process)
call, require, instrument = process.call, process.require, process.instrument
if sys.argv[1:] not in ([], ["--backward"]):
    raise SystemExit("Usage: accept-clock-emulator.py [--backward]")
SHIFT_SECONDS = -300 if sys.argv[1:] else 300
OUTPUT = Path("build/sunrise-evidence/active-clock-backward" if SHIFT_SECONDS < 0 else "build/sunrise-evidence/active-clock-change")


def device_seconds():
    return int(call("shell", "date", "+%s%3N").strip()) / 1000


def main():
    require(b"Sunrise_API36" in call("emu", "avd", "name"), "The owned AVD is required")
    require(call("shell", "getprop", "ro.kernel.qemu").strip() == b"1", "A disposable emulator is required")
    process.ALARM_UID = call("shell", "run-as", process.PACKAGE, "id", "-u").decode().strip()
    require(process.ALARM_UID.isdigit(), "Expected the installed app UID")
    original_auto = call("shell", "settings", "get", "global", "auto_time").decode().strip()
    original_offset = device_seconds() - time.time()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    (OUTPUT / "result.json").unlink(missing_ok=True)
    prepared = shifted = False
    try:
        print("Step 1: Save an actual alarm and wait for its first audio attempt.", flush=True)
        instrument("prepareProcessLossFixture", OUTPUT / "prepare.txt")
        prepared = True
        fixture = json.loads(call("exec-out", "run-as", process.PACKAGE, "cat", "cache/cadence-fixture.json"))
        require(fixture["scheduleId"].startswith("cadence-process-loss-"), "Unexpected fixture")
        (OUTPUT / "fixture.json").write_text(json.dumps(fixture, indent=2))
        process.wait_for("first alarm audio", process.audio_active)
        before = process.snapshot(fixture["campaignId"])
        require(before["kind"] == "ringing" and before["attempt"] == 1, "Expected first ringing attempt")
        (OUTPUT / "before.json").write_text(json.dumps(before, indent=2))
        print("Step 2: Move the emulator wall clock five minutes " + ("backward." if SHIFT_SECONDS < 0 else "forward."), flush=True)
        call("shell", "settings", "put", "global", "auto_time", "0")
        target = device_seconds() + SHIFT_SECONDS
        changed = call("shell", "cmd", "alarm", "set-time", str(int(target * 1000)))
        shifted = True
        require(abs(device_seconds() - target) < 5, "Android did not apply the clock change")
        (OUTPUT / "clock-change.txt").write_bytes(changed)
        time.sleep(8)
        after = process.snapshot(fixture["campaignId"])
        audio = process.audio_active()
        evidence = {"before": before, "after": after, "alarmAudioActive": audio,
                    "wallShiftSeconds": SHIFT_SECONDS, "observedAt": datetime.now(timezone.utc).isoformat(),
                    "physicalSpeakerEvidence": False}
        (OUTPUT / "observation.json").write_text(json.dumps(evidence, indent=2))
        (OUTPUT / "services.txt").write_bytes(call("shell", "dumpsys", "activity", "services", process.PACKAGE))
        require(after == before, "A wall-clock jump advanced or replaced the active ringing attempt")
        require(audio, "A wall-clock jump stopped current alarm audio")
        (OUTPUT / "alarms-after-change.txt").write_bytes(call("shell", "dumpsys", "alarm"))
        print("Step 3: Wait for the original two-minute ringing boundary in real elapsed time.", flush=True)
        quiet_deadline = time.monotonic() + 130
        while time.monotonic() < quiet_deadline:
            quiet = process.snapshot(fixture["campaignId"])
            if quiet["kind"] == "quiet" and not process.audio_active():
                break
            time.sleep(1)
        else:
            raise RuntimeError("The wall-clock change delayed the actual quiet interval")
        expected_quiet = datetime.fromisoformat(before["ringEndsAt"].replace("Z", "+00:00")).timestamp() - original_offset
        evidence["quiet"] = quiet
        evidence["quietObservedAtHost"] = datetime.now(timezone.utc).isoformat()
        evidence["expectedQuietAtHostEpochSeconds"] = expected_quiet
        (OUTPUT / "observation.json").write_text(json.dumps(evidence, indent=2))
        require(abs(time.time() - expected_quiet) < 5, "The quiet interval did not follow the original elapsed boundary")
        require(quiet["completedAttempt"] == 1, "The clock change skipped an attempt")
        evidence["quiet"] = quiet
        evidence["quietObservedAtHost"] = datetime.now(timezone.utc).isoformat()
        (OUTPUT / "result.json").write_text(json.dumps(evidence, indent=2))
        print("Step 4: The same attempt reached quiet at its original elapsed boundary.", flush=True)
    finally:
        try:
            if prepared:
                instrument("cleanupInterruptedFixture", OUTPUT / "cleanup.txt")
        finally:
            if shifted:
                call("shell", "cmd", "alarm", "set-time", str(int((time.time() + original_offset) * 1000)))
            if original_auto == "null":
                call("shell", "settings", "delete", "global", "auto_time")
            else:
                call("shell", "settings", "put", "global", "auto_time", original_auto)
            (OUTPUT / "restored-clock.json").write_text(json.dumps({"autoTime": call("shell", "settings", "get", "global", "auto_time").decode().strip(), "deviceHostOffsetSeconds": device_seconds() - time.time()}, indent=2))
        print("Step 5: Remove the controlled alarm and restore the emulator clock.", flush=True)


if __name__ == "__main__":
    main()
