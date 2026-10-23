#!/usr/bin/env python3
"""Require observed network restoration rather than successful shell commands."""
from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = spec_from_file_location("cadence", Path(__file__).resolve().parents[1] / "scripts/accept-cadence-emulator.py")
cadence = module_from_spec(spec)
spec.loader.exec_module(cadence)


class NetworkCleanupTest(unittest.TestCase):
    def test_cleanup_waits_for_android_to_apply_the_original_settings(self):
        expected = {"airplane_mode_on": "0", "wifi_on": "1", "mobile_data": "1"}
        reads = []

        def call(*args):
            if args[:4] == ("shell", "settings", "get", "global"):
                name = args[4]
                reads.append(name)
                return ("0" if name == "mobile_data" and len(reads) <= 3 else expected[name]).encode()
            return b""

        with tempfile.TemporaryDirectory() as output, patch.object(cadence, "OUTPUT", Path(output)), patch.object(cadence, "call", call), patch.object(cadence.time, "sleep"):
            cadence.restore_network(expected)
            self.assertGreater(len(reads), 3)
            self.assertEqual(expected, cadence.json.loads((Path(output) / "restored-network.json").read_text()))

    def test_cleanup_rejects_a_setting_that_never_returns_to_its_original_value(self):
        expected = {"airplane_mode_on": "0", "wifi_on": "1", "mobile_data": "1"}
        with tempfile.TemporaryDirectory() as output, patch.object(cadence, "OUTPUT", Path(output)), patch.object(cadence, "call", return_value=b"0"), patch.object(cadence.time, "sleep"):
            with self.assertRaisesRegex(RuntimeError, "Network settings did not return"):
                cadence.restore_network(expected)
            self.assertNotEqual(expected, cadence.json.loads((Path(output) / "restored-network.json").read_text()))


if __name__ == "__main__":
    unittest.main()
