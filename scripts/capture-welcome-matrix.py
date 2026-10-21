#!/usr/bin/env python3
"""Capture the installed Welcome on a disposable Sunrise emulator; restore its profile."""
import argparse
import html
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

PROFILES = [
    ('phone-portrait', 'Phone · portrait', 1080, 2400, 480, 1.0),
    ('phone-landscape', 'Phone · landscape', 2400, 1080, 480, 1.0),
    ('tablet-portrait', 'Tablet · portrait', 1600, 2560, 320, 1.0),
    ('tablet-landscape', 'Tablet · landscape', 2560, 1600, 320, 1.0),
    ('cover-portrait', 'Fold cover · portrait', 1080, 2520, 480, 1.0),
    ('cover-landscape', 'Fold cover · landscape', 2520, 1080, 480, 1.0),
    ('inner-portrait', 'Fold inner · portrait', 1536, 1720, 320, 1.0),
    ('inner-landscape', 'Fold inner · landscape', 1720, 1536, 320, 1.0),
    ('large-text', 'Phone · 200% text', 960, 2100, 480, 2.0),
]


def validate_portrait_placement(nodes, width, height, density, scale, name):
    if width < height and width * 160 / density < 600 and height * 160 / density >= 750 and scale == 1.0:
        brand = next(n for n in nodes.iter('node') if n.get('text') == 'Curfew')
        brand_bounds = list(map(int, re.findall(r'\d+', brand.get('bounds'))))
        if brand_bounds[1] < height * 0.10:
            raise RuntimeError(name + ': portrait introduction is pinned near the top')


def capture_accessibility_root(adb, name, output):
    # A display change can briefly leave Android without an accessibility root.
    # Each attempt removes the dump so an old screen cannot validate this one.
    xml = None
    failure_message = 'Android did not provide a fresh accessibility root'
    for _ in range(5):
        adb('shell', 'rm', '-f', '/data/local/tmp/curfew-welcome.xml')
        try:
            adb('shell', 'uiautomator', 'dump', '/data/local/tmp/curfew-welcome.xml')
            xml = adb('exec-out', 'cat', '/data/local/tmp/curfew-welcome.xml')
            root = ET.fromstring(xml)
            labels = {n.get('text') for n in root.iter('node')}
            expected = 'Get started' if name.endswith('-actions') else 'Your nights.\nYour mornings.'
            if expected in labels:
                crash = adb('shell', 'logcat', '-b', 'crash', '-d', '-v', 'threadtime')
                if b'FATAL EXCEPTION' in crash and re.search(rb'Process: (com\.android\.systemui|studio\.hypertext\.curfew),', crash):
                    failure_message = 'System UI or Curfew crashed during capture'
                    break
                return xml, root
        except (subprocess.CalledProcessError, ET.ParseError):
            continue
    if xml is not None:
        (output / (name + '-failed.xml')).write_bytes(xml)
    diagnostics = {
        '-failed.png': ('exec-out', 'screencap', '-p'),
        '-windows.txt': ('shell', 'dumpsys', 'window', 'windows'),
        '-exit-info.txt': ('shell', 'dumpsys', 'activity', 'exit-info', 'studio.hypertext.curfew'),
        '-system-ui-exit-info.txt': ('shell', 'dumpsys', 'activity', 'exit-info', 'com.android.systemui'),
        '-crash-log.txt': ('shell', 'logcat', '-b', 'crash', '-d', '-v', 'threadtime'),
    }
    for suffix, command in diagnostics.items():
        try:
            (output / (name + suffix)).write_bytes(adb(*command))
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as failure:
            (output / (name + suffix + '.error.txt')).write_text(str(failure))
    raise RuntimeError(name + ': ' + failure_message + '; diagnostics saved')


def capture_task_windows(adb, name, output):
    for scene in ('daily', 'editor', 'settings', 'account', 'check-in', 'readiness'):
        adb('shell', 'run-as', 'studio.hypertext.curfew', 'rm', '-f', f'cache/responsive-{scene}.png')
    result = adb('shell', 'am', 'instrument', '-w', '-e', 'class',
        'studio.hypertext.curfew.ui.CurfewAppNavigationInstrumentedTest#scaledWindowKeepsDailyAndEditorActionsReachable',
        'studio.hypertext.curfew.test/androidx.test.runner.AndroidJUnitRunner')
    (output / (name + '-tasks.txt')).write_bytes(result)
    if not re.search(rb'^OK \(1 tests?\)$', result, re.MULTILINE):
        raise RuntimeError(name + ': task navigation failed')
    files = []
    for scene in ('daily', 'editor', 'settings', 'account', 'check-in', 'readiness'):
        pixels = adb('exec-out', 'run-as', 'studio.hypertext.curfew', 'cat', f'cache/responsive-{scene}.png')
        if not pixels.startswith(b'\x89PNG\r\n\x1a\n'):
            raise RuntimeError(name + ': missing native PNG for ' + scene)
        filename = name + '-' + scene + '.png'
        (output / filename).write_bytes(pixels)
        files.append(filename)
    return files


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--include-native-fixtures', action='store_true')
    parser.add_argument('--include-task-fixtures', action='store_true')
    parser.add_argument('--only', action='append', choices=[p[0] for p in PROFILES])
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)

    def adb(*parts):
        return subprocess.check_output([args.adb, '-s', args.serial, *parts], timeout=60)

    avd = adb('emu', 'avd', 'name').decode().splitlines()[0].strip()
    if avd not in ('Sunrise_API33', 'Sunrise_API36', 'Sunrise_Design_API35') or adb('shell', 'getprop', 'ro.kernel.qemu').strip() != b'1':
        raise RuntimeError('This reset-and-capture tool only accepts the owned disposable Sunrise emulators.')
    apk_path = adb('shell', 'pm', 'path', 'studio.hypertext.curfew').decode().splitlines()[0].removeprefix('package:').strip()
    apk_digest = adb('shell', 'sha256sum', apk_path).decode().split()[0]
    source_commit = subprocess.check_output(['git', 'rev-parse', 'HEAD']).decode().strip()
    source_dirty = bool(subprocess.check_output(['git', 'status', '--porcelain', '--untracked-files=no']).strip())
    api_level = int(adb('shell', 'getprop', 'ro.build.version.sdk').strip())
    native_files = []
    if args.include_native_fixtures:
        native_files = [f'entrance-{time}.png' for time in (0, 80, 180, 320, 600, 1000)]
        native_files += [f'welcome-posture-{kind}-{theme}.png' for kind in ('book', 'tabletop') for theme in ('light', 'dark')]
        native_files += ['launcher-icon-review.png']
        for name in native_files:
            location = 'files/' if name == 'launcher-icon-review.png' else 'cache/'
            pixels = adb('exec-out', 'run-as', 'studio.hypertext.curfew', 'cat', location + name)
            if not pixels.startswith(b'\x89PNG\r\n\x1a\n'):
                raise RuntimeError(name + ': missing native PNG evidence')
            (args.output / name).write_bytes(pixels)
    original = {key: adb('shell', 'wm', key).decode() for key in ('size', 'density')}
    font = adb('shell', 'settings', 'get', 'system', 'font_scale').decode().strip()
    night = adb('shell', 'cmd', 'uimode', 'night').decode().split(':')[-1].strip()
    records = []
    task_files = []

    def capture(name):
        xml, root = capture_accessibility_root(adb, name, args.output)
        (args.output / (name + '.xml')).write_bytes(xml)
        labels = {n.get('text') for n in root.iter('node')}
        (args.output / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p'))
        return labels

    try:
        adb('shell', 'am', 'force-stop', 'studio.hypertext.curfew')
        adb('shell', 'pm', 'clear', 'studio.hypertext.curfew')
        for key, label, width, height, density, scale in PROFILES:
            if args.only and key not in args.only:
                continue
            for theme in ('light', 'dark'):
                adb('shell', 'am', 'force-stop', 'studio.hypertext.curfew')
                adb('shell', 'wm', 'size', f'{width}x{height}')
                adb('shell', 'wm', 'density', str(density))
                adb('shell', 'settings', 'put', 'system', 'font_scale', str(scale))
                adb('shell', 'cmd', 'uimode', 'night', 'yes' if theme == 'dark' else 'no')
                adb('shell', 'am', 'start', '-W', '-n', 'studio.hypertext.curfew/.MainActivity')
                name = key + '-' + theme
                labels = capture(name)
                if not {'Curfew', 'Your nights.\nYour mornings.'} <= labels:
                    raise RuntimeError(name + ': welcome did not load')
                if width * 160 / density >= 600 and scale == 1.0:
                    nodes = ET.fromstring((args.output / (name + '.xml')).read_bytes())
                    title = next(n for n in nodes.iter('node') if n.get('text') == 'Your nights.\nYour mornings.')
                    action = next(n for n in nodes.iter('node') if n.get('text') == 'Get started')
                    title_bounds = list(map(int, re.findall(r'\d+', title.get('bounds'))))
                    action_bounds = list(map(int, re.findall(r'\d+', action.get('bounds'))))
                    if title_bounds[2] > action_bounds[0]:
                        raise RuntimeError(name + ': introduction and actions did not adapt into distinct panes')
                validate_portrait_placement(ET.fromstring((args.output / (name + '.xml')).read_bytes()), width, height, density, scale, name)
                actions_visible = {'Get started', 'Not now'} <= labels
                if scale == 1.0 and not actions_visible:
                    raise RuntimeError(name + ': setup actions require unnecessary scrolling')
                if not actions_visible:
                    adb('shell', 'input', 'swipe', str(width // 2), str(height * 4 // 5), str(width // 2), str(height // 5), '400')
                    if not {'Get started', 'Not now'} <= capture(name + '-actions'):
                        raise RuntimeError(name + ': setup actions are unreachable')
                records.append(dict(name=name, label=label, theme=theme, pixels=[width, height],
                                    dp=[width * 160 / density, height * 160 / density], fontScale=scale,
                                    firstViewportActions=actions_visible))
                (args.output / 'progress.json').write_text(json.dumps(records, indent=2) + '\n')
                if args.include_task_fixtures:
                    task_files += capture_task_windows(adb, name, args.output)
                print(name + ' passed', flush=True)
    finally:
        adb('shell', 'am', 'force-stop', 'studio.hypertext.curfew')
        for key in ('size', 'density'):
            override = re.search(r'Override ' + key + r': ([^\n]+)', original[key])
            adb('shell', 'wm', key, override.group(1) if override else 'reset')
        if font == 'null':
            adb('shell', 'settings', 'delete', 'system', 'font_scale')
        else:
            adb('shell', 'settings', 'put', 'system', 'font_scale', font)
        adb('shell', 'cmd', 'uimode', 'night', night)
        restored = all(adb('shell', 'wm', key).decode() == original[key] for key in original)
        restored = restored and adb('shell', 'settings', 'get', 'system', 'font_scale').decode().strip() == font
        restored = restored and adb('shell', 'cmd', 'uimode', 'night').decode().split(':')[-1].strip() == night
        manifest = dict(avd=avd, apiLevel=api_level, sourceCommit=source_commit, sourceDirty=source_dirty, installedApkSha256=apk_digest, nativeFixtures=native_files, taskFixtures=task_files, physicalDevice=False, postureSimulation=False, restored=restored, captures=records)
        (args.output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
        if not restored:
            raise RuntimeError('Emulator profile restoration failed')
        adb('shell', 'am', 'start', '-W', '-n', 'studio.hypertext.curfew/.MainActivity')
    cards = ''.join(f'<figure><figcaption>{html.escape(r["label"])} · {r["theme"]} · '
                    f'{r["dp"][0]:.0f} × {r["dp"][1]:.0f} dp</figcaption>'
                    f'<a href="{r["name"]}.png"><img src="{r["name"]}.png" alt="{html.escape(r["label"])}"></a></figure>'
                    for r in records)
    task_link = '<p><a href="tasks.html">Task screenshot matrix</a></p>' if task_files else ''
    scene_titles = {'daily': 'Alarm', 'editor': 'Wake time', 'settings': 'Settings', 'account': 'Account', 'check-in': 'Check-in', 'readiness': 'Alarm setup'}
    if task_files:
        task_cards = ''.join(
            '<section id="' + scene + '"><h2>' + html.escape(scene_titles[scene]) + '</h2><main>' +
            ''.join('<figure><figcaption>' + html.escape(r['label']) + ' · ' + r['theme'] +
                f" · {r['dp'][0]:.0f} × {r['dp'][1]:.0f} dp</figcaption>" +
                '<a href="' + r['name'] + '-' + scene + '.png"><img loading="lazy" src="' +
                r['name'] + '-' + scene + '.png" alt="' + html.escape(r['label'] + ' ' + scene) + '"></a></figure>'
                for r in records) + '</main></section>'
            for scene in ('daily', 'editor', 'settings', 'account', 'check-in', 'readiness'))
        (args.output / 'tasks.html').write_text('<!doctype html><meta charset="utf-8"><title>Curfew task review</title>'
            '<style>body{margin:32px;background:#eee9e1;color:#29221f;font:16px system-ui}'
            'main{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:24px}'
            'figure{margin:0;background:#fbf7ef;padding:16px}img{width:100%;height:640px;object-fit:contain}'
            'figcaption{margin-bottom:16px}@media(max-width:720px){main{grid-template-columns:1fr}}</style>'
            '<h1>Curfew task review</h1><p>Native Compose task fixtures on emulated windows. '
            'These captures do not establish physical fold posture, system permission behavior, or hearing.</p>' + task_cards)
    (args.output / 'index.html').write_text('<!doctype html><meta charset="utf-8"><title>Curfew welcome review</title>'
        '<style>body{margin:32px;background:#eee9e1;color:#29221f;font:16px system-ui}main{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:24px}'
        'figure{margin:0;background:#fbf7ef;padding:16px}figcaption{margin-bottom:16px}img{width:100%;height:640px;object-fit:contain}'
        '@media(max-width:720px){main{grid-template-columns:1fr}img{height:auto}}</style>'
        '<h1>Curfew welcome review</h1>' + task_link + '<p>Actual installed Android captures across emulated windows. '
        'Fold cover and inner sizes do not establish hinge posture behavior or physical-device acceptance. Click an image for its full resolution.</p><main>' + cards + '</main>')


if __name__ == '__main__':
    main()
