#!/usr/bin/env python3
"""Kill a real foreground alarm process on the owned disposable AVD."""
from datetime import datetime, timezone
from pathlib import Path
import json
import re
import sqlite3
import sys
import tempfile
import time

from importlib.util import spec_from_file_location, module_from_spec

sys.dont_write_bytecode = True
spec = spec_from_file_location("cadence", Path(__file__).with_name("accept-cadence-emulator.py"))
cadence = module_from_spec(spec)
spec.loader.exec_module(cadence)
call, require, instrument = cadence.call, cadence.require, cadence.instrument
PACKAGE = cadence.PACKAGE
OUTPUT = Path("build/sunrise-evidence/process-loss")


def pid():
    return call("shell", "pidof", PACKAGE).decode().strip()


def audio_active():
    dump = call("shell", "dumpsys", "audio").decode()
    players = dump.split("  players:", 1)[-1].split("  ducked players", 1)[0]
    return any("state:started" in line and "usage=USAGE_ALARM" in line and
               re.search(r"u/pid:" + ALARM_UID + r"/", line) for line in players.splitlines())


def snapshot(campaign_id):
    with tempfile.TemporaryDirectory() as directory:
        database = Path(directory) / "curfew-private.db"
        for suffix in ["", "-wal"]:
            destination = Path(str(database) + suffix)
            destination.write_bytes(call("exec-out", "run-as", PACKAGE, "cat", "databases/curfew-private.db" + suffix))
        with sqlite3.connect(database) as connection:
            row = connection.execute("SELECT protocolJson FROM alarm_campaigns WHERE campaignId = ?", (campaign_id,)).fetchone()
        require(row is not None, "The saved occurrence disappeared")
        return json.loads(row[0])


def wait_for(description, predicate, timeout=45):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            if predicate():
                return
        except cadence.subprocess.CalledProcessError:
            pass  # pidof returns nonzero while the killed process is absent.
        time.sleep(0.25)
    raise RuntimeError("Timed out waiting for " + description)


def main():
    global ALARM_UID, OUTPUT
    require(sys.argv[1:] in [[], ["--api33"]], "Supported option: --api33")
    api = 33 if sys.argv[1:] else 36
    cadence.SERIAL = "emulator-5558" if api == 33 else "emulator-5554"
    if api == 33:
        OUTPUT = Path("build/sunrise-evidence/process-loss-api33")
    require(call("emu", "avd", "name").decode().splitlines()[0].strip() == f"Sunrise_API{api}", "The matching owned AVD is required")
    require(call("shell", "getprop", "ro.kernel.qemu").strip() == b"1", "A disposable emulator is required")
    ALARM_UID = call("shell", "run-as", PACKAGE, "id", "-u").decode().strip()
    require(ALARM_UID.isdigit(), "Expected the installed application UID")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    (OUTPUT / "result.json").unlink(missing_ok=True)
    apk_path = call("shell", "pm", "path", PACKAGE).decode().strip().removeprefix("package:")
    require(apk_path.startswith("/data/app/") and apk_path.endswith("/base.apk"), "Expected one installed APK")
    (OUTPUT / "installed-apk.txt").write_bytes(call("shell", "sha256sum", apk_path))
    prepared = False
    try:
        print("Step 1: Save an actual alarm and let instrumentation finish before delivery.", flush=True)
        instrument("prepareProcessLossFixture", OUTPUT / "prepare.txt")
        prepared = True
        fixture = json.loads(call("exec-out", "run-as", PACKAGE, "cat", "cache/cadence-fixture.json"))
        require(fixture["scheduleId"].startswith("cadence-process-loss-"), "Unexpected fixture")
        (OUTPUT / "fixture.json").write_text(json.dumps(fixture, indent=2))
        wait_for("scheduled alarm audio outside instrumentation", audio_active)
        before = snapshot(fixture["campaignId"])
        require(before["kind"] == "ringing" and before["attempt"] == 1, "The first ringing interval is required")
        old_pid = pid()
        require(re.fullmatch(r"[0-9]+", old_pid) is not None, "Expected one exact application PID")
        boundary = datetime.fromisoformat(before["ringEndsAt"].replace("Z", "+00:00"))
        require((boundary - datetime.now(timezone.utc)).total_seconds() > 60, "Too little time remains to prove sticky recovery")
        print("Step 2: Kill the actual playback PID " + old_pid + ".", flush=True)
        process_status = call("shell", "cat", "/proc/" + old_pid + "/status").decode()
        require(re.search(r"^Uid:\s+" + ALARM_UID + r"\s", process_status, re.MULTILINE) is not None,
                "The target PID must still belong to Curfew")
        killed_at = time.monotonic()
        # API33 denies runas_app signals to the app. Root is confined to the guarded AVD.
        signal_context = ("su", "0") if api == 33 else ("run-as", PACKAGE)
        call("shell", *signal_context, "kill", "-9", old_pid)
        wait_for("a replacement process and active alarm audio", lambda: pid() != old_pid and audio_active())
        new_pid = pid()
        after = snapshot(fixture["campaignId"])
        for key in ["campaignId", "kind", "attempt", "ringStartedAt", "ringEndsAt", "ringMillis", "quietMillis"]:
            require(before[key] == after[key], "Recovery changed " + key)
        require(datetime.now(timezone.utc) < boundary, "Recovery only occurred at the next boundary")
        evidence = {"oldPid": old_pid, "newPid": new_pid, "recoverySeconds": time.monotonic() - killed_at,
                    "before": before, "after": after, "alarmAudioActive": True,
                    "physicalSpeakerEvidence": False}
        services = call("shell", "dumpsys", "activity", "services", PACKAGE).decode()
        (OUTPUT / "services.txt").write_text(services)
        require(new_pid + ":" + PACKAGE in services and "isForeground=true" in services,
                "The replacement process must own foreground playback")
        log = call("shell", "logcat", "-d", "-v", "brief").decode()
        relevant = [line for line in log.splitlines() if
                    ("Process " + PACKAGE + " (pid " + old_pid + ") has died" in line or
                     "Scheduling restart of crashed service " + PACKAGE in line or
                     "Start proc " + new_pid + ":" + PACKAGE in line)]
        (OUTPUT / "actual-process-restart.txt").write_text("\n".join(relevant) + "\n")
        require(any("Scheduling restart of crashed service " + PACKAGE in line for line in relevant),
                "Android must report a crashed-service restart")
        (OUTPUT / "result.json").write_text(json.dumps(evidence, indent=2))
        print("Step 3: The replacement process restored the same ringing interval. Evidence is in " + str(OUTPUT), flush=True)
    finally:
        if prepared:
            print("Step 4: Clean up the controlled occurrence.", flush=True)
            instrument("cleanupInterruptedFixture", OUTPUT / "cleanup.txt")


if __name__ == "__main__":
    main()
