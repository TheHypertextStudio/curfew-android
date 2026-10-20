#!/usr/bin/env python3
"""Observe an alarm waking the locked, owned Android 13 emulator."""
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
OUTPUT = Path("build/sunrise-evidence/locked-presentation-api33")
FIXTURE_PIN = "824691"


def main():
    require(call("emu", "avd", "name").decode().splitlines()[0].strip() == "Sunrise_API33", "The owned API33 AVD is required")
    require(call("shell", "getprop", "ro.kernel.qemu").strip() == b"1", "A disposable emulator is required")
    require(b"mWakefulness=Awake" in call("shell", "dumpsys", "power"), "The owned emulator must start awake")
    require(b"      secure=false" in call("shell", "dumpsys", "window", "policy"), "The owned emulator must have no existing credential")
    process.ALARM_UID = call("shell", "run-as", process.PACKAGE, "id", "-u").decode().strip()
    require(process.ALARM_UID.isdigit(), "Expected the installed app UID")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    (OUTPUT / "result.json").unlink(missing_ok=True)
    apk_path = call("shell", "pm", "path", process.PACKAGE).decode().strip().removeprefix("package:")
    require(apk_path.startswith("/data/app/") and apk_path.endswith("/base.apk"), "Expected one installed APK")
    (OUTPUT / "installed-apk.txt").write_bytes(call("shell", "sha256sum", apk_path))
    prepared = False
    pin_set = False
    result = None
    try:
        require(b"set to" in call("shell", "locksettings", "set-pin", FIXTURE_PIN), "The fixture PIN was not set")
        pin_set = True
        require(b"verified successfully" in call("shell", "locksettings", "verify", "--old", FIXTURE_PIN), "The fixture PIN did not verify")
        print("Step 1: Save a scoped one-night alarm 90 seconds ahead, then end instrumentation.", flush=True)
        instrument("prepareProcessLossFixture", OUTPUT / "prepare.txt", {"fixtureDelaySeconds": "90"})
        prepared = True
        fixture = json.loads(call("exec-out", "run-as", process.PACKAGE, "cat", "cache/cadence-fixture.json"))
        require(fixture["scheduleId"].startswith("cadence-process-loss-"), "Unexpected fixture")
        before = process.snapshot(fixture["campaignId"])
        require(before["kind"] == "scheduled" and not process.audio_active(), "Expected a silent future occurrence")
        print("Step 2: Leave Curfew and put the display to sleep.", flush=True)
        call("shell", "input", "keyevent", "KEYCODE_HOME")
        call("shell", "input", "keyevent", "KEYCODE_SLEEP")
        process.wait_for("the sleeping display", lambda: b"mWakefulness=Asleep" in call("shell", "dumpsys", "power"), timeout=10)
        process.wait_for("keyguard covering the sleeping device", lambda: b"      showing=true" in call("shell", "dumpsys", "window", "policy"), timeout=10)
        locked_policy = call("shell", "dumpsys", "window", "policy")
        require(b"      secure=true" in locked_policy, "The sleeping keyguard must require the fixture credential")
        (OUTPUT / "before-window-policy.txt").write_bytes(locked_policy)
        print("Step 3: Observe scheduled audio and automatic foreground presentation without waking the device.", flush=True)
        process.wait_for("the scheduled alarm audio", process.audio_active, timeout=120)
        process.wait_for("the display waking itself", lambda: b"mWakefulness=Awake" in call("shell", "dumpsys", "power"), timeout=15)
        process.wait_for("Curfew taking foreground focus", lambda: any(b"mCurrentFocus=" in line and b"studio.hypertext.curfew" in line
            for line in call("shell", "dumpsys", "window").splitlines()), timeout=15)
        after = process.snapshot(fixture["campaignId"])
        require(after["kind"] == "ringing" and after["attempt"] == 1, "Expected the same first ringing attempt")
        require(after["campaignId"] == before["campaignId"] and after["ringStartedAt"] == before["startsAt"], "Presentation changed the occurrence or boundary")
        call("shell", "uiautomator", "dump", "/sdcard/curfew-presentation.xml")
        xml = call("exec-out", "cat", "/sdcard/curfew-presentation.xml")
        (OUTPUT / "ringing.xml").write_bytes(xml)
        require(b"Time to get up." in xml and b"Alarm recovery" in xml, "The active alarm screen did not render")
        (OUTPUT / "ringing.png").write_bytes(call("exec-out", "screencap", "-p"))
        (OUTPUT / "after-window.txt").write_bytes(call("shell", "dumpsys", "window"))
        (OUTPUT / "after-power.txt").write_bytes(call("shell", "dumpsys", "power"))
        result = {"before": before, "after": after,
            "alarmAudioActive": process.audio_active(), "automaticForegroundPresentation": True,
            "secureKeyguardBeforeAlarm": True, "physicalSpeakerEvidence": False}
        print("Step 4: The scheduled alarm woke the display and rendered its ringing screen.", flush=True)
    except Exception:
        # Preserve the sleeping failure before cleanup changes keyguard and power.
        for name, command in {
            "power": ("shell", "dumpsys", "power"),
            "window": ("shell", "dumpsys", "window"),
            "notifications": ("shell", "dumpsys", "notification", "--noredact"),
            "activity": ("shell", "dumpsys", "activity", "activities"),
            "logcat": ("logcat", "-d", "-t", "2000"),
        }.items():
            try:
                (OUTPUT / ("failure-" + name + ".txt")).write_bytes(call(*command))
            except Exception as diagnostic_error:
                print("Could not capture " + name + ": " + str(diagnostic_error), flush=True)
        raise
    finally:
        if pin_set:
            require(b"cleared" in call("shell", "locksettings", "clear", "--old", FIXTURE_PIN), "The fixture PIN did not clear")
            process.wait_for("the original credential-free state", lambda: b"      secure=false" in call("shell", "dumpsys", "window", "policy"), timeout=10)
        call("shell", "input", "keyevent", "KEYCODE_WAKEUP")
        if pin_set:
            call("shell", "wm", "dismiss-keyguard")
            process.wait_for("the restored unlocked screen", lambda: b"      showing=false" in call("shell", "dumpsys", "window", "policy"), timeout=10)
        if prepared:
            instrument("cleanupInterruptedFixture", OUTPUT / "cleanup.txt")
        call("shell", "rm", "-f", "/sdcard/curfew-presentation.xml")
        print("Step 5: Clear the fixture PIN, remove the controlled alarm, and restore the awake display.", flush=True)
    if result is not None:
        (OUTPUT / "result.json").write_text(json.dumps({**result, "fixturePinCleared": True, "scopedAlarmCleaned": True}, indent=2))


if __name__ == "__main__":
    main()
