#!/usr/bin/env python3
"""Observe actual AlarmManager delivery after entering deep Doze on the owned AVD."""
from datetime import datetime, timezone
from pathlib import Path
from importlib.util import spec_from_file_location, module_from_spec
import json
import sys
import time

sys.dont_write_bytecode = True
spec = spec_from_file_location("process_acceptance", Path(__file__).with_name("accept-process-loss-emulator.py"))
process = module_from_spec(spec)
spec.loader.exec_module(process)
call, require, instrument = process.call, process.require, process.instrument
PACKAGE = process.PACKAGE
OUTPUT = Path("build/sunrise-evidence/doze")


def main():
    require(b"Sunrise_API36" in call("emu", "avd", "name"), "The owned AVD is required")
    require(call("shell", "getprop", "ro.kernel.qemu").strip() == b"1", "A disposable emulator is required")
    original = call("shell", "dumpsys", "deviceidle").decode()
    require("mForceIdle=false" in original and "mScreenOn=true" in original and "mCharging=true" in original,
            "The owned emulator must begin awake without forced idle")
    process.ALARM_UID = call("shell", "run-as", PACKAGE, "id", "-u").decode().strip()
    require(process.ALARM_UID.isdigit(), "Expected the installed application UID")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    (OUTPUT / "result.json").unlink(missing_ok=True)
    (OUTPUT / "original-idle.txt").write_text(original)
    apk_path = call("shell", "pm", "path", PACKAGE).decode().strip().splitlines()[0].removeprefix("package:")
    (OUTPUT / "installed-apk.txt").write_bytes(call("shell", "sha256sum", apk_path))
    prepared = False
    try:
        print("Step 1: Save a real one-night alarm and end instrumentation.", flush=True)
        instrument("prepareProcessLossFixture", OUTPUT / "prepare.txt", {"fixtureDelaySeconds": "3900"})
        prepared = True
        fixture = json.loads(call("exec-out", "run-as", PACKAGE, "cat", "cache/cadence-fixture.json"))
        require(fixture["scheduleId"].startswith("cadence-process-loss-"), "Unexpected fixture")
        starts_at = datetime.fromisoformat(fixture["startsAt"].replace("Z", "+00:00"))
        require(datetime.now(timezone.utc) < starts_at, "The fixture must not have rung before idle")
        print("Step 2: Disconnect emulated power, turn the screen off, and enter deep Doze.", flush=True)
        call("shell", "dumpsys", "battery", "unplug")
        call("shell", "input", "keyevent", "223")
        process.wait_for("screen off", lambda: "mScreenOn=false" in call("shell", "dumpsys", "deviceidle").decode(), timeout=5)
        forced = call("shell", "dumpsys", "deviceidle", "force-idle", "deep").decode()
        idle = call("shell", "dumpsys", "deviceidle").decode()
        (OUTPUT / "forced-idle.txt").write_text(forced + "\n" + idle)
        require("mState=IDLE " in idle and "mScreenOn=false" in idle, "Deep idle was not established")
        entered_at = datetime.now(timezone.utc)
        require(entered_at < starts_at, "The alarm rang before deep idle was established")
        print("Step 3: Observe the unchanged 65-minute wall-clock schedule. The driver remains live.", flush=True)
        deadline = time.monotonic() + 3_945
        while time.monotonic() < deadline:
            if process.audio_active():
                break
            (OUTPUT / "live-status.json").write_text(json.dumps({"observedAt": datetime.now(timezone.utc).isoformat(), "scheduledAt": fixture["startsAt"], "deepState": call("shell", "dumpsys", "deviceidle", "get", "deep").decode().strip()}))
            time.sleep(5)
        else:
            raise RuntimeError("The saved alarm did not deliver after deep Doze")
        observed_at = datetime.now(timezone.utc)
        state = process.snapshot(fixture["campaignId"])
        require(state["kind"] == "ringing" and state["attempt"] == 1, "Expected the original first ringing interval")
        require(state["campaignId"] == fixture["campaignId"], "Occurrence identity changed")
        require(datetime.fromisoformat(state["ringStartedAt"].replace("Z", "+00:00")) == starts_at,
                "Doze changed the scheduled start")
        (OUTPUT / "after-idle.txt").write_bytes(call("shell", "dumpsys", "deviceidle"))
        (OUTPUT / "services.txt").write_bytes(call("shell", "dumpsys", "activity", "services", PACKAGE))
        (OUTPUT / "screen.png").write_bytes(call("exec-out", "screencap", "-p"))
        result = {"enteredDeepIdleAt": entered_at.isoformat(), "scheduledAt": fixture["startsAt"],
                  "observedAudioAt": observed_at.isoformat(), "deliveryDelaySeconds": (observed_at - starts_at).total_seconds(),
                  "state": state, "alarmAudioActive": True, "physicalSpeakerEvidence": False}
        (OUTPUT / "result.json").write_text(json.dumps(result, indent=2))
        print("Step 4: Alarm delivery and audio passed after observed deep Doze. Evidence is in " + str(OUTPUT), flush=True)
    finally:
        print("Step 5: Leave forced idle, restore normal battery reporting, and clean up the fixture.", flush=True)
        try:
            call("shell", "dumpsys", "deviceidle", "unforce")
            call("shell", "dumpsys", "battery", "reset")
            call("shell", "input", "keyevent", "224")
        finally:
            if prepared:
                instrument("cleanupInterruptedFixture", OUTPUT / "cleanup.txt")
        (OUTPUT / "restored-idle.txt").write_bytes(call("shell", "dumpsys", "deviceidle"))


if __name__ == "__main__":
    main()
