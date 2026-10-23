#!/usr/bin/env python3
"""Capture real setup fixtures only on a task-owned disposable emulator."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import struct
import subprocess
import time

SCENES = ('time', 'introduction', 'defer-error', 'connection', 'keyboard', 'transfer', 'test-ready', 'connected', 'permissions', 'review')


def validate_display_size(output, width, height):
    sizes = re.findall(r'(?:Physical|Override) size: (\d+)x(\d+)', output)
    actual = tuple(map(int, sizes[-1])) if sizes else None
    if actual != (width, height):
        raise RuntimeError(f'Android display is {actual[0]}x{actual[1]}, requested {width}x{height}' if actual else 'Android display size is unavailable')


def validate_png_size(png, width, height):
    if not png.startswith(b'\x89PNG\r\n\x1a\n') or len(png) < 24:
        raise RuntimeError('Screenshot is not a PNG with dimensions')
    actual = struct.unpack('>II', png[16:24])
    if actual != (width, height):
        raise RuntimeError(f'Native screenshot is {actual[0]}x{actual[1]}, requested {width}x{height}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', required=True)
    parser.add_argument('--expected-avd', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--refresh-profile', action='append', default=[], help='Recapture both themes of this profile during resume')
    parser.add_argument('--resume', action='store_true', help='Reuse verified completed profiles for the same installed APK')
    args = parser.parse_args()
    if not args.expected_avd.startswith('curfew-onboarding-'):
        raise RuntimeError('Only a task-owned Curfew onboarding AVD is permitted.')

    def adb(*parts):
        return subprocess.check_output([args.adb, '-s', args.serial, *parts], timeout=90)

    def guard():
        if adb('shell', 'getprop', 'ro.kernel.qemu').strip() != b'1' or adb('emu', 'avd', 'name').decode().splitlines()[0].strip() != args.expected_avd:
            raise RuntimeError('This tool refuses physical devices and other emulators.')

    def settings():
        return dict(size=adb('shell', 'wm', 'size').decode(), density=adb('shell', 'wm', 'density').decode(),
                    font=adb('shell', 'settings', 'get', 'system', 'font_scale').decode().strip(),
                    night=adb('shell', 'cmd', 'uimode', 'night').decode().split(':')[-1].strip())

    spec = importlib.util.spec_from_file_location('welcome', Path(__file__).with_name('capture-welcome-matrix.py'))
    welcome = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(welcome)
    guard()
    args.output.mkdir(parents=True, exist_ok=True)
    original = settings()
    records = []
    files = []
    path = adb('shell', 'pm', 'path', 'studio.hypertext.curfew').decode().splitlines()[0].removeprefix('package:').strip()
    digest = adb('shell', 'sha256sum', path).decode().split()[0]
    coverage_digest = welcome.capture_fingerprint(digest)
    test_path = adb('shell', 'pm', 'path', 'studio.hypertext.curfew.test').decode().splitlines()[0].removeprefix('package:').strip()
    test_digest = adb('shell', 'sha256sum', test_path).decode().split()[0]
    if not set(args.refresh_profile) <= {row[0] for row in welcome.PROFILES}:
        raise RuntimeError('Unknown refresh profile.')
    if args.refresh_profile and not args.resume:
        raise RuntimeError('Refreshing a profile requires --resume.')
    if args.resume:
        previous = json.loads((args.output / 'manifest.json').read_text())
        if previous.get('coverageContractSha256') != coverage_digest or previous.get('installedApkSha256') != digest or previous.get('avd') != args.expected_avd or not previous.get('restored'):
            raise RuntimeError('Resume requires the same installed APK and owned AVD after successful restoration.')
        expected = {key+'-'+theme for key, *_ in welcome.PROFILES for theme in ('light','dark')}
        records = [r for r in previous['captures'] if not any(r['name'] == key+'-'+theme for key in args.refresh_profile for theme in ('light','dark'))]
        if len({r['name'] for r in records}) != len(records) or not {r['name'] for r in records} <= expected:
            raise RuntimeError('Resume profile inventory is invalid.')
        for record in records:
            for scene in SCENES:
                file = record['name']+'-'+scene+'.png'
                validate_png_size((args.output/file).read_bytes(), *record['pixels'])
                files.append(file)
        for log in args.output.glob('*.txt'):
            if 'FAILURES!!!' in log.read_text():
                archive = log.with_suffix('.failed-attempt.txt')
                if not archive.exists(): archive.write_bytes(log.read_bytes())
    completed = {record['name'] for record in records}
    try:
        for key, label, width, height, density, scale in welcome.PROFILES:
            for theme in ('light', 'dark'):
                if key+'-'+theme in completed:
                    continue
                guard()
                adb('shell', 'am', 'force-stop', 'studio.hypertext.curfew')
                adb('shell', 'wm', 'size', f'{width}x{height}')
                adb('shell', 'wm', 'density', str(density))
                adb('shell', 'settings', 'put', 'system', 'font_scale', str(scale))
                adb('shell', 'cmd', 'uimode', 'night', 'yes' if theme == 'dark' else 'no')
                display_size = adb('shell', 'wm', 'size').decode()
                validate_display_size(display_size, width, height)
                name = key + '-' + theme
                scenes = SCENES + (('transfer-actions', 'test-ready-actions', 'review-actions') if scale >= 1.5 else ()) + (('motion-16', 'motion-96', 'motion-240', 'motion-800') if name == 'phone-portrait-light' else ())
                for scene in scenes:
                    adb('shell', 'run-as', 'studio.hypertext.curfew', 'rm', '-f', f'cache/setup-{scene}.png')
                result = adb('shell', 'am', 'instrument', '-w', '-e', 'class',
                    'studio.hypertext.curfew.ui.SetupCaptureInstrumentedTest',
                    'studio.hypertext.curfew.test/androidx.test.runner.AndroidJUnitRunner')
                (args.output / (name + '.txt')).write_bytes(result)
                if not re.search(rb'^OK \(1 test\)$', result, re.MULTILINE):
                    raise RuntimeError(name + ': native setup failed; output retained')
                for scene in scenes:
                    pixels = adb('exec-out', 'run-as', 'studio.hypertext.curfew', 'cat', f'cache/setup-{scene}.png')
                    validate_png_size(pixels, width, height)
                    file = name + '-' + scene + '.png'
                    (args.output / file).write_bytes(pixels)
                    files.append(file)
                records.append(dict(name=name, label=label, theme=theme, pixels=[width, height], dp=[width * 160 / density, height * 160 / density], fontScale=scale, observedDisplaySize=display_size, pngDimensionsVerified=True, instrumentationApkSha256=test_digest))
                (args.output / 'progress.json').write_text(json.dumps(records, indent=2) + '\n')
                print(name + ' passed', flush=True)
    finally:
        adb('shell', 'am', 'force-stop', 'studio.hypertext.curfew')
        for key in ('size', 'density'):
            override = re.search(r'Override ' + key + r': ([^\n]+)', original[key])
            adb('shell', 'wm', key, override.group(1) if override else 'reset')
        if original['font'] == 'null':
            adb('shell', 'settings', 'delete', 'system', 'font_scale')
        else:
            adb('shell', 'settings', 'put', 'system', 'font_scale', original['font'])
        adb('shell', 'cmd', 'uimode', 'night', original['night'])
        restored = False
        for _ in range(20):
            after = settings()
            restored = after == original
            if restored:
                break
            time.sleep(.25)
        (args.output / 'manifest.json').write_text(json.dumps(dict(
            avd=args.expected_avd, apiLevel=int(adb('shell', 'getprop', 'ro.build.version.sdk')),
            installedApkSha256=digest, coverageContractSha256=coverage_digest, physicalDevice=False, postureSimulation=False,
            captures=records, files=files, restored=restored, originalSettings=original, restoredSettings=after,
        ), indent=2) + '\n')
        if not restored:
            raise RuntimeError('Emulator settings did not restore; captures are rejected.')


if __name__ == '__main__':
    main()
