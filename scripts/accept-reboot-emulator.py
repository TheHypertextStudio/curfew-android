#!/usr/bin/env python3
"""Observe the first-unlock boundary on Curfew's owned disposable AVD."""
from datetime import datetime, timezone
from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path
import json
import subprocess
import sys
import time

sys.dont_write_bytecode = True
spec = spec_from_file_location("process_acceptance", Path(__file__).with_name("accept-process-loss-emulator.py"))
process = module_from_spec(spec)
spec.loader.exec_module(process)
call, require, instrument = process.call, process.require, process.instrument
OUTPUT = Path("build/sunrise-evidence/reboot-first-unlock")
PIN = "616161"


def user_state():
    return call("shell", "am", "get-started-user-state", "0").decode().strip()


def unlock():
    call("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    call("shell", "input", "keyevent", "KEYCODE_MENU")
    time.sleep(1)
    call("shell", "input", "text", PIN)
    call("shell", "input", "keyevent", "KEYCODE_ENTER")
    process.wait_for("first user unlock", lambda: user_state() == "RUNNING_UNLOCKED", timeout=20)


def main():
    require(b"Sunrise_API36" in call("emu", "avd", "name"), "The owned AVD is required")
    require(call("shell", "getprop", "ro.kernel.qemu").strip() == b"1", "A disposable emulator is required")
    require(user_state() == "RUNNING_UNLOCKED", "Start with the owned user unlocked")
    require(b"verified successfully" in call("shell", "locksettings", "verify"), "An existing credential must not be replaced")
    process.ALARM_UID = call("shell", "run-as", process.PACKAGE, "id", "-u").decode().strip()
    require(process.ALARM_UID.isdigit(), "Expected the installed app UID")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    (OUTPUT / "result.json").unlink(missing_ok=True)
    apk_path = call("shell", "pm", "path", process.PACKAGE).decode().strip().splitlines()[0].removeprefix("package:")
    (OUTPUT / "installed-apk.txt").write_bytes(call("shell", "sha256sum", apk_path))
    prepared = locked = False
    try:
        print("Step 1: Save a real alarm two minutes ahead and retain its identity.", flush=True)
        instrument("prepareProcessLossFixture", OUTPUT / "prepare.txt", {"fixtureDelaySeconds": "120"})
        prepared = True
        fixture = json.loads(call("exec-out", "run-as", process.PACKAGE, "cat", "cache/cadence-fixture.json"))
        require(fixture["scheduleId"].startswith("cadence-process-loss-"), "Unexpected fixture")
        (OUTPUT / "fixture.json").write_text(json.dumps(fixture, indent=2))
        starts_at = datetime.fromisoformat(fixture["startsAt"].replace("Z", "+00:00"))
        before = process.snapshot(fixture["campaignId"])
        old_boot = call("shell", "cat", "/proc/sys/kernel/random/boot_id").decode().strip()
        print("Step 2: Set a temporary PIN and reboot the disposable emulator.", flush=True)
        require(b"set to" in call("shell", "locksettings", "set-pin", PIN), "Could not set fixture PIN")
        locked = True
        call("reboot")
        subprocess.run([process.cadence.ADB, "-s", process.cadence.SERIAL, "wait-for-device"], check=True, timeout=90, capture_output=True)
        process.wait_for("a new boot", lambda: call("shell", "cat", "/proc/sys/kernel/random/boot_id").decode().strip() != old_boot, timeout=90)
        process.wait_for("locked user boot", lambda: user_state() == "RUNNING_LOCKED", timeout=90)
        require(datetime.now(timezone.utc) < starts_at, "Boot missed the planned locked-state observation")
        print("Step 3: Observe the chosen time before first unlock.", flush=True)
        while datetime.now(timezone.utc).timestamp() < starts_at.timestamp() + 5:
            require(user_state() == "RUNNING_LOCKED", "The user unexpectedly unlocked before the observation")
            time.sleep(1)
        before_unlock_audio = process.audio_active()
        require(not before_unlock_audio, "The documented before-first-unlock limitation changed")
        (OUTPUT / "before-unlock-alarms.txt").write_bytes(call("shell", "dumpsys", "alarm"))
        print("Step 4: Unlock and require the original occurrence to recover audible playback.", flush=True)
        unlock()
        unlocked_at = datetime.now(timezone.utc).isoformat()
        process.wait_for("same alarm audio after unlock", process.audio_active, timeout=80)
        audio_observed_at = datetime.now(timezone.utc).isoformat()
        after = process.snapshot(fixture["campaignId"])
        (OUTPUT / "playback-service.txt").write_bytes(call("shell", "dumpsys", "activity", "services", process.PACKAGE))
        (OUTPUT / "boot-playback-logcat.txt").write_bytes(call("shell", "logcat", "-d", "-v", "brief", "-s", "SunrisePlayback", "AndroidRuntime", "ActivityManager"))
        require(after["campaignId"] == before["campaignId"], "Reboot replaced the occurrence")
        require(after["kind"] == "ringing" and after["attempt"] == 1, "Expected the first ringing interval")
        require(after["ringStartedAt"] == fixture["startsAt"], "Recovery changed the original boundary")
        (OUTPUT / "result.json").write_text(json.dumps({
            "before": before, "after": after, "beforeFirstUnlockAudio": before_unlock_audio,
            "afterFirstUnlockAudio": True, "unlockedAt": unlocked_at, "audioObservedAt": audio_observed_at,
            "physicalSpeakerEvidence": False, "beforeFirstUnlockSupported": False,
        }, indent=2))
        print("Step 5: First-unlock recovery passed; pre-unlock ringing remains unsupported.", flush=True)
    finally:
        if locked:
            if user_state() != "RUNNING_UNLOCKED":
                unlock()
            require(b"cleared" in call("shell", "locksettings", "clear", "--old", PIN), "Could not clear fixture PIN")
            require(b"verified successfully" in call("shell", "locksettings", "verify"), "The original no-PIN state was not restored")
        if prepared:
            instrument("cleanupInterruptedFixture", OUTPUT / "cleanup.txt")
        print("Step 6: Restore the no-PIN state and remove the controlled alarm.", flush=True)


if __name__ == "__main__":
    main()
