# Native welcome review

Reader: The Curfew Android designer and reviewer.
Action: Inspect the complete responsive welcome before accepting its presentation.
Date: October 20, 2026.

Open `index.html` for full-resolution captures. The light and dark sheets cover
phone, tablet, fold cover, and fold inner windows in both orientations. The
accessibility sheet adds both themes at 200% text. All 18 installed MainActivity
profiles show both actions in the first viewport. An independent design review
checked every profile and found no clipping, collision, or unresolved placement.

CI run [39529921160](https://github.com/TheHypertextStudio/curfew-android/actions/runs/39529921160)
passed all seven jobs for runtime `f867f51d974a4b60358f658246901decc7b5121f`.
The captured merge commit has the same source tree as that runtime. The manifest
records the API35 fixture, source commit, dimensions, and installed APK SHA256.
The downloaded APK matches that hash. All PNGs decode, and device size, density,
font scale, and night mode were restored after capture.

These are emulated window sizes, not physical devices. The four book/tabletop
images inject Android Posture/HingeInfo to check geometry. They do not establish
hardware WindowManager callbacks. The icon sheet checks eight adaptive masks.
The entrance GIF uses native test frames and loops for inspection; the app's
entrance runs once. The design reviewer also checked those fixtures and masks.

`scripts/capture-welcome-matrix.py --include-native-fixtures` runs after native
tests on an owned, disposable, allowlisted emulator. CI produced this capture;
no physical phone was reset. Manual TalkBack, physical reduced motion, hardware
posture changes, and remaining physical alarm acceptance stay open.
