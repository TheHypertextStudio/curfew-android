#!/usr/bin/env python3
"""Review real dialog windows on the disposable Sunrise_API33 emulator."""
from pathlib import Path
import json
import re
import subprocess

ADB = "/Users/williecubed/Library/Android/sdk/platform-tools/adb"
SERIAL = "emulator-5558"
PACKAGE = "studio.hypertext.curfew"
OUTPUT = Path("build/sunrise-evidence/material-pickers/profiles")


def call(*arguments, timeout=30):
    return subprocess.check_output([ADB, "-s", SERIAL, *arguments], timeout=timeout)


def setting(namespace, name, value):
    if value == "null":
        call("shell", "settings", "delete", namespace, name)
    else:
        call("shell", "settings", "put", namespace, name, value)


def main():
    if call("emu", "avd", "name").decode().splitlines()[0].strip() != "Sunrise_API33":
        raise RuntimeError("The owned Sunrise_API33 emulator is required")
    if call("shell", "getprop", "ro.kernel.qemu").strip() != b"1":
        raise RuntimeError("A disposable emulator is required")
    density = call("shell", "wm", "density").decode()
    override = re.search(r"Override density: (\d+)", density)
    original_density = override.group(1) if override else "reset"
    values = [("system", "font_scale"), ("system", "time_12_24"),
              ("global", "window_animation_scale"), ("global", "transition_animation_scale"),
              ("global", "animator_duration_scale")]
    original = {(namespace, name): call("shell", "settings", "get", namespace, name).decode().strip()
                for namespace, name in values}
    OUTPUT.mkdir(parents=True, exist_ok=True)
    (OUTPUT / "result.json").unlink(missing_ok=True)
    (OUTPUT / "original.json").write_text(json.dumps({"density": density,
        "settings": {f"{namespace}/{name}": value for (namespace, name), value in original.items()}}, indent=2))
    apk_path = call("shell", "pm", "path", PACKAGE).decode().strip().removeprefix("package:")
    if not apk_path.startswith("/data/app/") or not apk_path.endswith("/base.apk"):
        raise RuntimeError("Expected one installed application APK")
    (OUTPUT / "installed-apk.txt").write_bytes(call("shell", "sha256sum", apk_path))
    completed = []
    try:
        for name in ["window_animation_scale", "transition_animation_scale", "animator_duration_scale"]:
            setting("global", name, "0")
        for name, density_value, scale, clock in [
            ("normal", "420", "1.0", "12"),
            ("narrow", "540", "1.0", "12"),
            ("large", "540", "2.0", "12"),
            ("large-24h", "540", "2.0", "24"),
        ]:
            print(f"Checking {name}: density={density_value} fontScale={scale} clock={clock}", flush=True)
            directory = OUTPUT / name
            directory.mkdir(exist_ok=True)
            call("shell", "wm", "density", density_value)
            setting("system", "font_scale", scale)
            setting("system", "time_12_24", clock)
            result = call("shell", "am", "instrument", "-w", "-e", "class",
                "studio.hypertext.curfew.ui.AlarmPickerLayoutInstrumentedTest,studio.hypertext.curfew.ui.SunriseScheduleEditorInstrumentedTest",
                PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner", timeout=180)
            (directory / "instrumentation.txt").write_bytes(result)
            for item in call("shell", "run-as", PACKAGE, "ls", "cache/alarm-pickers").decode().splitlines():
                (directory / item).write_bytes(call("exec-out", "run-as", PACKAGE, "cat", "cache/alarm-pickers/" + item))
            profile = (directory / "profile.txt").read_text()
            if f"fontScale={float(scale)}" not in profile or f"screenWidthDp={320 if density_value == '540' else 411}" not in profile:
                raise RuntimeError(f"The actual dialog profile did not match {name}: {profile}")
            if not re.search(rb"^OK \(18 tests\)$", result, re.MULTILINE):
                raise RuntimeError(f"Picker acceptance failed for {name}; inspect {directory}")
            completed.append(name)
    finally:
        call("shell", "wm", "density", original_density)
        for (namespace, name), value in original.items():
            setting(namespace, name, value)
        restored = call("shell", "wm", "density").decode() == density
        actual = {(namespace, name): call("shell", "settings", "get", namespace, name).decode().strip()
                  for namespace, name in values}
        # Android may materialize the default font scale after deleting an absent key.
        restored = restored and all(actual[namespace, name] == value or
            (namespace == "system" and name == "font_scale" and value == "null" and actual[namespace, name] == "1.0")
            for (namespace, name), value in original.items())
        (OUTPUT / "result.json").write_text(json.dumps({"completed": completed, "originalProfileRestored": restored,
            "restoredSettings": {f"{namespace}/{name}": value for (namespace, name), value in actual.items()},
            "physicalDeviceEvidence": False}, indent=2))
        if not restored:
            raise RuntimeError("The original emulator profile was not restored")
        print("The original emulator density, text scale, clock format, and animations are restored.", flush=True)


if __name__ == "__main__":
    main()
