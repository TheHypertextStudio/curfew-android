#!/usr/bin/env python3
"""Check native-test/capture sequencing without Android or Gradle."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

WRAPPER = Path(__file__).with_name('run-instrumented-and-capture.sh').resolve()


class WelcomeCaptureRunnerTest(unittest.TestCase):
    def run_fixture(self, api, native_status=0):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'ci').mkdir()
            (root / 'bin').mkdir()
            native = root / 'ci/run-instrumented-tests.sh'
            native.write_text('#!/usr/bin/env bash\nexit "$NATIVE_FIXTURE_STATUS"\n')
            native.chmod(0o755)
            capture = root / 'bin/python3'
            capture.write_text('#!/usr/bin/env bash\nprintf "%s\\n" "$@" > "$CAPTURE_FIXTURE_OUTPUT"\n')
            capture.chmod(0o755)
            marker = root / 'capture-arguments.txt'
            env = dict(os.environ, PATH=str(root / 'bin') + os.pathsep + os.environ['PATH'],
                       NATIVE_FIXTURE_STATUS=str(native_status), CAPTURE_FIXTURE_OUTPUT=str(marker))
            result = subprocess.run(['bash', str(WRAPPER), 'Plain', str(api)], cwd=root,
                                    env=env, capture_output=True, text=True)
            return result, marker.read_text() if marker.exists() else None

    def test_api35_captures_only_after_native_success(self):
        result, arguments = self.run_fixture(35)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('--include-native-fixtures', arguments)
        self.assertIn('--include-task-fixtures', arguments)
        self.assertIn('emulator-5554', arguments)

    def test_other_api_does_not_run_capture(self):
        result, arguments = self.run_fixture(34)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIsNone(arguments)

    def test_native_failure_prevents_capture(self):
        result, arguments = self.run_fixture(35, 23)
        self.assertEqual(23, result.returncode, result.stderr)
        self.assertIsNone(arguments)


if __name__ == '__main__':
    unittest.main()
