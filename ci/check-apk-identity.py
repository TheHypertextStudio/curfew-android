#!/usr/bin/env python3
"""Verify that a physical acceptance APK cannot impersonate the normal install."""
import argparse
import re
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--aapt', required=True)
parser.add_argument('--apk', required=True)
parser.add_argument('--acceptance', action='store_true')
args = parser.parse_args()
badging = subprocess.check_output([args.aapt, 'dump', 'badging', args.apk], text=True)
package = re.search(r"^package: name='([^']+)'", badging, re.MULTILINE).group(1)
label = re.search(r"^application-label:'([^']+)'", badging, re.MULTILINE).group(1)
expected = ('studio.hypertext.curfew.acceptance', 'Curfew Test') if args.acceptance else ('studio.hypertext.curfew', 'Curfew')
assert (package, label) == expected, f'Expected {expected}, got {(package, label)}'
manifest = subprocess.check_output([args.aapt, 'dump', 'xmltree', args.apk, 'AndroidManifest.xml'], text=True)
expected_scheme = expected[0]
schemes = re.findall(r'android:scheme[^\n]*="([^"]+)"', manifest)
assert schemes == [expected_scheme], f'Unexpected OAuth handlers: {schemes}'
print(f'Verified {package}: {label}; callback scheme {expected_scheme}')
