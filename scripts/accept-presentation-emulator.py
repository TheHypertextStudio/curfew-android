#!/usr/bin/env python3
"""Observe an alarm waking a locked, owned Android emulator."""
import argparse
from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path
import json
import re
import sys
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True
spec = spec_from_file_location("process_acceptance", Path(__file__).with_name("accept-process-loss-emulator.py"))
process = module_from_spec(spec)
spec.loader.exec_module(process)
process.cadence.SERIAL = "emulator-5558"
call, require, instrument = process.call, process.require, process.instrument
OUTPUT = Path("build/sunrise-evidence/locked-presentation-api33")
FIXTURE_PIN = "824691"


def main():
    global OUTPUT
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api", type=int, choices=[33, 36], default=33)
    parser.add_argument("--full-screen", choices=["allow", "deny"], default="allow")
    args = parser.parse_args()
    require(args.api >= 34 or args.full_screen == "allow", "Full-screen app-op denial requires Android 14 or later")
    process.cadence.SERIAL = {33: "emulator-5558", 36: "emulator-5554"}[args.api]
    suffix = "-denied" if args.full_screen == "deny" else ""
    OUTPUT = Path(f"build/sunrise-evidence/locked-presentation-api{args.api}{suffix}")
    require(call("emu", "avd", "name").decode().splitlines()[0].strip() == f"Sunrise_API{args.api}", "The matching owned AVD is required")
    require(call("shell", "getprop", "ro.build.version.sdk").strip() == str(args.api).encode(), "The API must match the fixture")
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
    original_full_screen_mode = None
    try:
        if args.api >= 34:
            original_op = call("shell", "appops", "get", process.PACKAGE, "USE_FULL_SCREEN_INTENT").decode()
            match = re.search(r"USE_FULL_SCREEN_INTENT: ([a-z_]+)", original_op)
            require(match is not None or "No operations" in original_op, "Could not read the original full-screen access mode")
            original_full_screen_mode = match.group(1) if match else "default"
            (OUTPUT / "original-full-screen-op.txt").write_text(original_op)
            call("shell", "appops", "set", process.PACKAGE, "USE_FULL_SCREEN_INTENT", "allow")
        require(b"set to" in call("shell", "locksettings", "set-pin", FIXTURE_PIN), "The fixture PIN was not set")
        pin_set = True
        require(b"verified successfully" in call("shell", "locksettings", "verify", "--old", FIXTURE_PIN), "The fixture PIN did not verify")
        print("Step 1: Save a scoped one-night alarm 90 seconds ahead, then end instrumentation.", flush=True)
        instrument("prepareProcessLossFixture", OUTPUT / "prepare.txt", {"fixtureDelaySeconds": "90"})
        prepared = True
        if args.api >= 34:
            call("shell", "appops", "set", process.PACKAGE, "USE_FULL_SCREEN_INTENT", args.full_screen)
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
        print("Step 3: Observe scheduled audio and " + ("automatic foreground presentation without waking the device." if args.full_screen == "allow" else "the lock-screen notification fallback."), flush=True)
        process.wait_for("the scheduled alarm audio", process.audio_active, timeout=120)
        if args.full_screen == "deny":
            notification_dump = call("shell", "dumpsys", "notification", "--noredact")
            (OUTPUT / "fallback-notifications.txt").write_bytes(notification_dump)
            records = [record for record in notification_dump.decode().split("NotificationRecord(")
                if f"pkg={process.PACKAGE} " in record and "id=4100 " in record]
            require(len(records) == 1, "Expected the actual ringing notification")
            require("fullscreenIntent=PendingIntent" not in records[0], "Denied full-screen access still has a full-screen intent")
            require("contentIntent=PendingIntent" in records[0], "The fallback notification cannot open its alarm")
            focus = call("shell", "dumpsys", "window")
            require(not any(b"mCurrentFocus=" in line and process.PACKAGE.encode() in line for line in focus.splitlines()),
                "Denied full-screen access still took foreground focus automatically")
            print("Step 3b: Wake the display, tap the actual alarm notification, and enter the fixture credential.", flush=True)
            call("shell", "input", "keyevent", "KEYCODE_WAKEUP")
            call("shell", "uiautomator", "dump", "/sdcard/curfew-presentation.xml")
            fallback_xml = call("exec-out", "cat", "/sdcard/curfew-presentation.xml")
            (OUTPUT / "fallback.xml").write_bytes(fallback_xml)
            (OUTPUT / "fallback.png").write_bytes(call("exec-out", "screencap", "-p"))
            titles = [node for node in ET.fromstring(fallback_xml).iter("node") if node.get("text") == "Time to get up."]
            require(len(titles) == 1, "Expected one visible lock-screen alarm notification")
            bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", titles[0].get("bounds", ""))
            require(bounds is not None, "The notification has no tap bounds")
            left, top, right, bottom = map(int, bounds.groups())
            call("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
            def credential_prompt_visible():
                call("shell", "uiautomator", "dump", "/sdcard/curfew-presentation.xml")
                prompt_xml = call("exec-out", "cat", "/sdcard/curfew-presentation.xml")
                (OUTPUT / "notification-tap.xml").write_bytes(prompt_xml)
                return any(node.get("class") == "android.widget.EditText" and
                    node.get("package") == "com.android.systemui" for node in ET.fromstring(prompt_xml).iter("node"))
            process.wait_for("the system credential prompt after tapping the notification", credential_prompt_visible, timeout=15)
            # Android owns this credential prompt; the PIN belongs only to this disposable fixture.
            call("shell", "input", "text", FIXTURE_PIN)
            call("shell", "input", "keyevent", "KEYCODE_ENTER")
        process.wait_for("the awake display", lambda: b"mWakefulness=Awake" in call("shell", "dumpsys", "power"), timeout=15)
        process.wait_for("Curfew taking foreground focus", lambda: any(b"mCurrentFocus=" in line and b"studio.hypertext.curfew" in line
            for line in call("shell", "dumpsys", "window").splitlines()), timeout=15)
        after = process.snapshot(fixture["campaignId"])
        require(after["kind"] == "ringing" and after["attempt"] == 1, "Expected the same first ringing attempt")
        require(after["campaignId"] == before["campaignId"] and after["ringStartedAt"] == before["startsAt"], "Presentation changed the occurrence or boundary")
        require(process.audio_active(), "Presentation must not stop alarm audio")
        call("shell", "uiautomator", "dump", "/sdcard/curfew-presentation.xml")
        xml = call("exec-out", "cat", "/sdcard/curfew-presentation.xml")
        (OUTPUT / "ringing.xml").write_bytes(xml)
        require(b"Time to get up." in xml and b"Alarm recovery" in xml, "The active alarm screen did not render")
        (OUTPUT / "ringing.png").write_bytes(call("exec-out", "screencap", "-p"))
        (OUTPUT / "after-window.txt").write_bytes(call("shell", "dumpsys", "window"))
        (OUTPUT / "after-power.txt").write_bytes(call("shell", "dumpsys", "power"))
        result = {"before": before, "after": after,
            "apiLevel": args.api,
            "alarmAudioActive": process.audio_active(), "automaticForegroundPresentation": args.full_screen == "allow",
            "fullScreenAccess": args.full_screen, "notificationTapPresentation": args.full_screen == "deny",
            "secureKeyguardBeforeAlarm": True, "physicalSpeakerEvidence": False}
        print("Step 4: " + ("The scheduled alarm woke the display and rendered its ringing screen." if args.full_screen == "allow" else "The notification tap reached the ringing screen without releasing the alarm."), flush=True)
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
        cleanup_failures = []
        def clean_up(name, action):
            try:
                action()
            except Exception as cleanup_error:
                cleanup_failures.append(name + ": " + str(cleanup_error))
                print("Cleanup failed for " + name + ": " + str(cleanup_error), flush=True)

        def restore_access():
            if original_full_screen_mode is None:
                return
            call("shell", "appops", "set", process.PACKAGE, "USE_FULL_SCREEN_INTENT", original_full_screen_mode)
            restored_op = call("shell", "appops", "get", process.PACKAGE, "USE_FULL_SCREEN_INTENT").decode()
            require(f"USE_FULL_SCREEN_INTENT: {original_full_screen_mode}" in restored_op or
                original_full_screen_mode == "default" and "No operations" in restored_op,
                "The original full-screen access mode did not restore")
            (OUTPUT / "restored-full-screen-op.txt").write_text(restored_op)

        def restore_screen():
            if pin_set:
                require(b"cleared" in call("shell", "locksettings", "clear", "--old", FIXTURE_PIN), "The fixture PIN did not clear")
                process.wait_for("the original credential-free state", lambda: b"      secure=false" in call("shell", "dumpsys", "window", "policy"), timeout=10)
                call("shell", "input", "keyevent", "KEYCODE_SLEEP")
            call("shell", "input", "keyevent", "KEYCODE_WAKEUP")
            if pin_set:
                call("shell", "wm", "dismiss-keyguard")
                call("shell", "input", "keyevent", "KEYCODE_MENU")
                process.wait_for("the restored unlocked screen", lambda: b"      showing=false" in call("shell", "dumpsys", "window", "policy"), timeout=10)

        clean_up("full-screen access", restore_access)
        clean_up("fixture credential and screen", restore_screen)
        if prepared:
            clean_up("scoped alarm", lambda: instrument("cleanupInterruptedFixture", OUTPUT / "cleanup.txt"))
        clean_up("temporary UI dump", lambda: call("shell", "rm", "-f", "/sdcard/curfew-presentation.xml"))
        require(not cleanup_failures, "; ".join(cleanup_failures))
        print("Step 5: Clear the fixture PIN, remove the controlled alarm, and restore the awake display.", flush=True)
    if result is not None:
        (OUTPUT / "result.json").write_text(json.dumps({**result, "fixturePinCleared": True, "scopedAlarmCleaned": True}, indent=2))


if __name__ == "__main__":
    main()
