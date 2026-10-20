#!/usr/bin/env python3
"""Reopen Curfew after a real force-stop on its owned disposable AVD."""
from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path
import argparse
import json
import sys

sys.dont_write_bytecode = True
spec = spec_from_file_location("process_acceptance", Path(__file__).with_name("accept-process-loss-emulator.py"))
process = module_from_spec(spec)
spec.loader.exec_module(process)
call, require, instrument = process.call, process.require, process.instrument
OUTPUT = Path("build/sunrise-evidence/active-app-relaunch")


def main():
    global OUTPUT
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api33", action="store_true", help="Use the owned Sunrise_API33 AVD on port 5558")
    args = parser.parse_args()
    expected_avd = "Sunrise_API33" if args.api33 else "Sunrise_API36"
    if args.api33:
        process.cadence.SERIAL = "emulator-5558"
        OUTPUT = Path("build/sunrise-evidence/active-app-relaunch-api33")
    require(call("emu", "avd", "name").decode().splitlines()[0].strip() == expected_avd, "The owned AVD is required")
    require(call("shell", "getprop", "ro.kernel.qemu").strip() == b"1", "A disposable emulator is required")
    process.ALARM_UID = call("shell", "run-as", process.PACKAGE, "id", "-u").decode().strip()
    require(process.ALARM_UID.isdigit(), "Expected the installed app UID")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    (OUTPUT / "result.json").unlink(missing_ok=True)
    prepared = False
    try:
        print("Step 1: Save an actual alarm and wait for first-attempt audio.", flush=True)
        instrument("prepareProcessLossFixture", OUTPUT / "prepare.txt")
        prepared = True
        fixture = json.loads(call("exec-out", "run-as", process.PACKAGE, "cat", "cache/cadence-fixture.json"))
        require(fixture["scheduleId"].startswith("cadence-process-loss-"), "Unexpected fixture")
        process.wait_for("first-attempt audio", process.audio_active)
        before = process.snapshot(fixture["campaignId"])
        require(before["kind"] == "ringing" and before["attempt"] == 1, "Expected first ringing attempt")
        old_pid = process.pid()
        print("Step 2: Force-stop the disposable app and confirm audio stops.", flush=True)
        call("shell", "am", "force-stop", process.PACKAGE)
        process.wait_for("force-stopped audio", lambda: not process.audio_active())
        print("Step 3: Reopen Curfew without manually starting the playback service.", flush=True)
        (OUTPUT / "launch.txt").write_bytes(call("shell", "am", "start", "-W", "-n", process.PACKAGE + "/.MainActivity"))
        try:
            process.wait_for("restored alarm audio after reopening", process.audio_active, timeout=30)
        finally:
            after = process.snapshot(fixture["campaignId"])
            evidence = {"before": before, "after": after, "oldPid": old_pid, "newPid": process.pid(),
                "alarmAudioActive": process.audio_active(), "physicalSpeakerEvidence": False}
            (OUTPUT / "observation.json").write_text(json.dumps(evidence, indent=2))
            (OUTPUT / "services.txt").write_bytes(call("shell", "dumpsys", "activity", "services", process.PACKAGE))
            (OUTPUT / "screen.png").write_bytes(call("exec-out", "screencap", "-p"))
        require(after == before, "Reopening advanced or replaced the original attempt")
        require(evidence["newPid"] != old_pid, "Reopening did not create a new app process")
        (OUTPUT / "result.json").write_text(json.dumps(evidence, indent=2))
        print("Step 4: Reopening restored audio for the same first attempt.", flush=True)
    finally:
        if prepared:
            instrument("cleanupInterruptedFixture", OUTPUT / "cleanup.txt")
        print("Step 5: Remove the controlled alarm.", flush=True)


if __name__ == "__main__":
    main()
