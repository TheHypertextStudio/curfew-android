#!/usr/bin/env python3
"""Exercise real Android credentials only on Curfew's disposable acceptance AVD."""

from pathlib import Path
import os
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
ADB = Path(os.environ.get("ANDROID_HOME", str(Path.home() / "Library/Android/sdk"))) / "platform-tools/adb"
SERIAL = "emulator-5554"
FIXTURE_PIN = "616161"
OUTPUT = ROOT / "build/sunrise-evidence/credential-journey"


def adb(*arguments, timeout=30):
    return subprocess.run([str(ADB), "-s", SERIAL, *arguments], capture_output=True, timeout=timeout)


def require(result, expected):
    output = result.stdout.decode(errors="replace") + result.stderr.decode(errors="replace")
    if result.returncode != 0 or expected not in output:
        raise RuntimeError(output.strip())
    return output


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    print("Step 1: Verify the owned disposable emulator.", flush=True)
    require(adb("emu", "avd", "name"), "Sunrise_API36")
    require(adb("shell", "getprop", "ro.kernel.qemu"), "1")
    if sys.argv[1:] == ["--cleanup"]:
        require(adb("shell", "locksettings", "verify", "--old", FIXTURE_PIN), "verified successfully")
        require(adb("shell", "locksettings", "clear", "--old", FIXTURE_PIN), "cleared")
        print("The fixture screen lock is cleared.", flush=True)
        return
    if sys.argv[1:]:
        raise RuntimeError("Supported option: --cleanup")
    require(adb("shell", "locksettings", "verify"), "verified successfully")
    adb("shell", "wm", "dismiss-keyguard")
    print("Step 2: Install a temporary fixture PIN on the emulator.", flush=True)
    lock_installed = False
    try:
        require(adb("shell", "locksettings", "set-pin", FIXTURE_PIN), "set to")
        lock_installed = True
        require(adb("shell", "locksettings", "verify", "--old", FIXTURE_PIN), "verified successfully")
        adb("shell", "input", "keyevent", "KEYCODE_SLEEP")
        adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
        print("Step 3: Run real credential success and cancellation journeys.", flush=True)
        result = adb("shell", "am", "instrument", "-w", "-e", "ownerRecoveryFixturePin", FIXTURE_PIN,
                     "-e", "class", "studio.hypertext.curfew.platform.OwnerRecoveryJourneyInstrumentedTest",
                     "studio.hypertext.curfew.test/androidx.test.runner.AndroidJUnitRunner", timeout=240)
        (OUTPUT / "installed.txt").write_bytes(result.stdout + result.stderr)
        print((result.stdout + result.stderr).decode(errors="replace"), flush=True)
        print("Step 4: Save native screenshots and observed accessibility state.", flush=True)
        capture = adb("exec-out", "run-as", "studio.hypertext.curfew", "tar", "-C", "cache/owner-recovery-journey", "-cf", "-", ".")
        if capture.returncode == 0:
            (OUTPUT / "captures.tar").write_bytes(capture.stdout)
        require(result, "OK (2 tests)")
    finally:
        if lock_installed:
            print("Step 5: Clear the fixture PIN and verify the original unlocked state.", flush=True)
            require(adb("shell", "locksettings", "clear", "--old", FIXTURE_PIN), "cleared")
            require(adb("shell", "locksettings", "verify"), "verified successfully")
            adb("shell", "input", "keyevent", "KEYCODE_SLEEP")
            adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
            adb("shell", "wm", "dismiss-keyguard")
            adb("shell", "input", "keyevent", "KEYCODE_MENU")


if __name__ == "__main__":
    main()
