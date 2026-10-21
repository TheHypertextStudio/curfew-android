#!/usr/bin/env python3
"""Keep failed native capture diagnostics without accepting a stale screen."""
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('welcome_capture', Path(__file__).resolve().parents[1] / 'scripts/capture-welcome-matrix.py')
capture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(capture)
WELCOME = b'<hierarchy><node text="Your nights.&#10;Your mornings." /></hierarchy>'
OTHER = b'<hierarchy><node text="Unrelated screen" /></hierarchy>'


class WelcomeRootCaptureTest(unittest.TestCase):
    def test_failure_keeps_the_last_root_screenshot_and_activity_diagnostics(self):
        calls = []
        def adb(*parts):
            calls.append(parts)
            if parts[:2] == ('exec-out', 'cat'): return OTHER
            if parts[:2] == ('exec-out', 'screencap'): return b'PNG fixture'
            if parts[:3] == ('shell', 'dumpsys', 'activity'): return b'Activity exit information'
            return b''
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory)
            with self.assertRaisesRegex(RuntimeError, 'fresh accessibility root'):
                capture.capture_accessibility_root(adb, 'fixture-dark', out)
            self.assertEqual(OTHER, (out / 'fixture-dark-failed.xml').read_bytes())
            self.assertEqual(b'PNG fixture', (out / 'fixture-dark-failed.png').read_bytes())
            self.assertIn(b'Activity exit information', (out / 'fixture-dark-exit-info.txt').read_bytes())
            self.assertIn(b'Activity exit information', (out / 'fixture-dark-system-ui-exit-info.txt').read_bytes())
            self.assertTrue((out / 'fixture-dark-crash-log.txt').is_file())
            self.assertIn(('shell', 'logcat', '-b', 'crash', '-d', '-v', 'threadtime'), calls)
            self.assertEqual(5, sum(p[:3] == ('shell', 'rm', '-f') for p in calls))

    def test_null_dump_cannot_reuse_an_old_root_and_a_fresh_root_can_succeed(self):
        attempts = 0
        def adb(*parts):
            nonlocal attempts
            if parts[:2] == ('exec-out', 'cat'):
                attempts += 1
                if attempts == 1: raise subprocess.CalledProcessError(1, 'adb')
                return WELCOME
            return b''
        with tempfile.TemporaryDirectory() as directory:
            xml, root = capture.capture_accessibility_root(adb, 'fixture-light', Path(directory))
            self.assertEqual(WELCOME, xml)
            self.assertEqual(2, attempts)
            self.assertEqual('Your nights.\nYour mornings.', next(root.iter('node')).get('text'))


if __name__ == '__main__': unittest.main()
