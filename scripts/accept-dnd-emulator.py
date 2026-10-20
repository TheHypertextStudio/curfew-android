#!/usr/bin/env python3
"""Observe system alarm mute and Curfew readiness on the owned API33 emulator."""
from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path
import json
import subprocess
import sys

sys.dont_write_bytecode = True
spec = spec_from_file_location("process_acceptance", Path(__file__).with_name("accept-process-loss-emulator.py"))
process = module_from_spec(spec)
spec.loader.exec_module(process)
process.cadence.SERIAL = "emulator-5558"
call, require = process.call, process.require
OUTPUT = Path("build/sunrise-evidence/dnd-api33")


def main():
    require(call("emu", "avd", "name").decode().splitlines()[0].strip() == "Sunrise_API33", "The owned API33 AVD is required")
    require(call("shell", "getprop", "ro.kernel.qemu").strip() == b"1", "A disposable emulator is required")
    original = call("shell", "settings", "get", "global", "zen_mode").strip()
    require(original == b"0", "The fixture requires the original DND-off state")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    (OUTPUT / "result.json").unlink(missing_ok=True)
    apk_path = call("shell", "pm", "path", process.PACKAGE).decode().strip().removeprefix("package:")
    require(apk_path.startswith("/data/app/") and apk_path.endswith("/base.apk"), "Expected one installed APK")
    (OUTPUT / "installed-apk.txt").write_bytes(call("shell", "sha256sum", apk_path))
    observed = []
    try:
        for mode, filter_value, zen_value, muted in [("none", 3, b"2", True), ("alarms", 4, b"3", False), ("off", 1, b"0", False)]:
            print("Observe DND " + mode + " and the actual Android readiness result.", flush=True)
            call("shell", "cmd", "notification", "set_dnd", mode)
            process.wait_for("DND " + mode, lambda: call("shell", "settings", "get", "global", "zen_mode").strip() == zen_value, timeout=10)
            result = subprocess.run([process.cadence.ADB, "-s", process.cadence.SERIAL, "shell", "am", "instrument", "-w",
                "-e", "dndAcceptance", "true", "-e", "expectedInterruptionFilter", str(filter_value),
                "-e", "expectedAlarmMuted", str(muted).lower(), "-e", "class",
                process.PACKAGE + ".platform.AlarmDndReadinessInstrumentedTest", process.cadence.RUNNER],
                capture_output=True, timeout=60, check=True)
            output = result.stdout + result.stderr
            (OUTPUT / (mode + "-instrumentation.txt")).write_bytes(output)
            require(b"OK (1 test)" in output or b"OK (1 tests)" in output, "The native DND readiness assertion did not pass")
            (OUTPUT / (mode + "-audio.txt")).write_bytes(call("shell", "dumpsys", "audio"))
            observed.append({"mode": mode, "interruptionFilter": filter_value, "alarmMuted": muted})
    finally:
        call("shell", "cmd", "notification", "set_dnd", "off")
        process.wait_for("the original DND-off state", lambda: call("shell", "settings", "get", "global", "zen_mode").strip() == original, timeout=10)
        (OUTPUT / "restored-notifications.txt").write_bytes(call("shell", "dumpsys", "notification"))
    (OUTPUT / "result.json").write_text(json.dumps({"observations": observed, "originalDndRestored": True, "physicalSpeakerEvidence": False}, indent=2))


if __name__ == "__main__":
    main()
