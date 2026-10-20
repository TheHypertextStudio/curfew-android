#!/usr/bin/env python3
"""Move the owned UI AVD's timezone around a real future alarm."""
from datetime import datetime, timedelta
from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path
import json
import sys

sys.dont_write_bytecode = True
spec = spec_from_file_location("process_acceptance", Path(__file__).with_name("accept-process-loss-emulator.py"))
process = module_from_spec(spec)
spec.loader.exec_module(process)
process.cadence.SERIAL = "emulator-5556"
call, require, instrument = process.call, process.require, process.instrument
OUTPUT = Path("build/sunrise-evidence/future-timezone-change")


def current_zone():
    return call("shell", "getprop", "persist.sys.timezone").decode().strip()


def instant(value):
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def main():
    require(b"Sunrise_UI_API35" in call("emu", "avd", "name"), "The owned UI AVD is required")
    require(call("shell", "getprop", "ro.kernel.qemu").strip() == b"1", "A disposable emulator is required")
    original_zone = current_zone()
    require(original_zone == "America/Los_Angeles", "This fixture requires its original Los Angeles timezone")
    original_auto = call("shell", "settings", "get", "global", "auto_time_zone").decode().strip()
    process.ALARM_UID = call("shell", "run-as", process.PACKAGE, "id", "-u").decode().strip()
    require(process.ALARM_UID.isdigit(), "Expected the installed app UID")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    (OUTPUT / "result.json").unlink(missing_ok=True)
    prepared = changed = False
    try:
        print("Step 1: Save a real local one-night alarm 90 seconds ahead.", flush=True)
        instrument("prepareProcessLossFixture", OUTPUT / "prepare.txt", {"fixtureDelaySeconds": "90"})
        prepared = True
        fixture = json.loads(call("exec-out", "run-as", process.PACKAGE, "cat", "cache/cadence-fixture.json"))
        require(fixture["scheduleId"].startswith("cadence-process-loss-"), "Unexpected fixture")
        (OUTPUT / "fixture.json").write_text(json.dumps(fixture, indent=2))
        before = process.snapshot(fixture["campaignId"])
        apk = call("shell", "pm", "path", process.PACKAGE).decode().strip().splitlines()[0].removeprefix("package:")
        (OUTPUT / "installed-apk.txt").write_bytes(call("shell", "sha256sum", apk))
        print("Step 2: Change to Anchorage and require the same local time one hour later.", flush=True)
        call("shell", "settings", "put", "global", "auto_time_zone", "0")
        changed = True
        call("shell", "cmd", "alarm", "set-timezone", "America/Anchorage")
        require(current_zone() == "America/Anchorage", "Android did not change timezone")
        def shifted():
            state = process.snapshot(fixture["campaignId"])
            return state["kind"] == "scheduled" and instant(state["startsAt"]) == instant(before["startsAt"]) + timedelta(hours=1)
        process.wait_for("future alarm following Anchorage local time", shifted, timeout=30)
        moved = process.snapshot(fixture["campaignId"])
        print("Step 3: Restore Los Angeles and require the original future boundary.", flush=True)
        call("shell", "cmd", "alarm", "set-timezone", original_zone)
        process.wait_for("original future alarm boundary", lambda: process.snapshot(fixture["campaignId"]) == before, timeout=30)
        print("Step 4: Observe actual audio at the original chosen boundary.", flush=True)
        process.wait_for("alarm audio after timezone round trip", process.audio_active, timeout=100)
        after = process.snapshot(fixture["campaignId"])
        require(after["kind"] == "ringing" and after["attempt"] == 1, "Expected the first ringing attempt")
        require(after["campaignId"] == before["campaignId"] and after["ringStartedAt"] == before["startsAt"], "Timezone recovery changed the occurrence or chosen time")
        (OUTPUT / "result.json").write_text(json.dumps({"before": before, "anchorage": moved, "after": after,
            "alarmAudioActive": True, "physicalSpeakerEvidence": False}, indent=2))
        print("Step 5: The same local alarm survived both real timezone changes and rang.", flush=True)
    finally:
        try:
            if prepared:
                instrument("cleanupInterruptedFixture", OUTPUT / "cleanup.txt")
        finally:
            if changed:
                call("shell", "cmd", "alarm", "set-timezone", original_zone)
                if original_auto == "null":
                    call("shell", "settings", "delete", "global", "auto_time_zone")
                else:
                    call("shell", "settings", "put", "global", "auto_time_zone", original_auto)
            (OUTPUT / "restored-zone.json").write_text(json.dumps({"timezone": current_zone(), "autoTimeZone": call("shell", "settings", "get", "global", "auto_time_zone").decode().strip()}, indent=2))
        print("Step 6: Remove the controlled alarm and restore timezone settings.", flush=True)


if __name__ == "__main__":
    main()
