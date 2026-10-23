#!/usr/bin/env python3
"""Reject failed task instrumentation and missing images during matrix capture."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('matrix', Path(__file__).resolve().parents[1] / 'scripts/capture-welcome-matrix.py')
matrix = importlib.util.module_from_spec(spec)
spec.loader.exec_module(matrix)


class TaskMatrixCaptureTest(unittest.TestCase):
    def test_instrumentation_failure_cannot_reuse_older_images(self):
        reads = []
        def adb(*parts):
            reads.append(parts)
            return b'FAILURES!!!\nTests run: 1, Failures: 1\n'
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(RuntimeError, 'task navigation failed'):
                matrix.capture_task_windows(adb, 'phone-light', Path(directory))
        self.assertFalse(any(p[:2] == ('exec-out', 'run-as') for p in reads))

    def test_missing_png_is_rejected(self):
        def adb(*parts):
            if parts[:3] == ('shell', 'am', 'instrument'): return b'OK (1 test)\n'
            return b''
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(RuntimeError, 'missing native PNG'):
                matrix.capture_task_windows(adb, 'tablet-dark', Path(directory))

    def test_success_saves_each_task_with_its_profile(self):
        def adb(*parts):
            if parts[:3] == ('shell', 'am', 'instrument'): return b'OK (1 test)\n'
            return b'\x89PNG\r\n\x1a\nimage'
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            files = matrix.capture_task_windows(adb, 'cover-light', output)
            self.assertEqual(['cover-light-' + scene + '.png' for scene in ('daily', 'editor', 'settings', 'account', 'check-in', 'readiness')], files)
            self.assertTrue(all((output / name).is_file() for name in files))


if __name__ == '__main__': unittest.main()
