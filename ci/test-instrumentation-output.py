#!/usr/bin/env python3
"""Check runner output handling without starting Android or waiting on a timeout."""
from pathlib import Path
import subprocess
import unittest


class InstrumentationOutputTest(unittest.TestCase):
    def run_fixture(self, result, status):
        source = Path(__file__).with_name("run-instrumented-tests.sh").read_text()
        script = source[source.index("instrumentation_output=$("):]
        # Replace only external commands; exercise the runner's actual result handling.
        fixture = f"""
set -euo pipefail
timeout() {{ shift; "$@"; }}
adb() {{
    if [[ "$1" == shell ]]; then
        printf '%s\\n' '{result}'
        return {status}
    fi
    printf '%s\\n' 'fixture diagnostic'
}}
"""
        return subprocess.run(["bash", "-c", fixture + script], capture_output=True, text=True)

    def test_timeout_preserves_partial_test_output_and_collects_diagnostics(self):
        result = self.run_fixture("INSTRUMENTATION_STATUS: test=unfinishedAlarm", 124)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("unfinishedAlarm", result.stdout)
        self.assertIn("fixture diagnostic", result.stderr)

    def test_nonempty_success_is_accepted(self):
        result = self.run_fixture("OK (115 tests)", 0)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("OK (115 tests)", result.stdout)


if __name__ == "__main__":
    unittest.main()
