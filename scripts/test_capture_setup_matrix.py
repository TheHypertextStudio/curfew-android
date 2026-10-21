"""Reject screenshot evidence when Android clamps a requested profile."""
import importlib.util
from pathlib import Path
import struct
import unittest

spec = importlib.util.spec_from_file_location('capture', Path(__file__).with_name('capture-setup-matrix.py'))
capture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(capture)


class CaptureGeometryTest(unittest.TestCase):
    def test_rejects_android_size_clamping(self):
        with self.assertRaisesRegex(RuntimeError, '1080x1920'):
            capture.validate_display_size('Physical size: 320x640\nOverride size: 1080x1920\n', 1080, 2400)

    def test_accepts_matching_override_or_physical_size(self):
        capture.validate_display_size('Physical size: 1080x1920\nOverride size: 1600x2560\n', 1600, 2560)
        capture.validate_display_size('Physical size: 1080x1920\n', 1080, 1920)

    def test_rejects_wrong_png_dimensions(self):
        png = b'\x89PNG\r\n\x1a\n' + b'\x00\x00\x00\rIHDR' + struct.pack('>II', 1080, 1920)
        with self.assertRaisesRegex(RuntimeError, '1080x1920'):
            capture.validate_png_size(png, 1080, 2400)

    def test_accepts_matching_png_dimensions(self):
        png = b'\x89PNG\r\n\x1a\n' + b'\x00\x00\x00\rIHDR' + struct.pack('>II', 1080, 2400)
        capture.validate_png_size(png, 1080, 2400)


if __name__ == '__main__':
    unittest.main()
