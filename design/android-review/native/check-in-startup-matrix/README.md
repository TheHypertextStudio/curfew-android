# Check-in setup after startup and keyboard correction

Reader: The Curfew Android designer and maintainer.
Action: Review the supported integration and keep consumer launch blocked until a consumer approval method exists.

This gallery refreshes all ten states across eighteen native emulated window
profiles after the system-managed splash and explicit keyboard-resize change.
It contains 180 state PNGs, four controlled shared wake-time transition frames,
and thirty contact sheets. The manifest records application source hash
`1bc755cb6437b7d57dc90b758353b700036425aa5673e00e976ef695c53450f1`
and installed GMS APK hash
`ac445269e540f0e1667f454a840b88c723748d78bc898b6f77e73ace27c61ff2`.

Open [the gallery](index.html) at
`http://127.0.0.1:4611/native/check-in-startup-matrix/index.html`.
The [full profile sheet](matrix-all-profiles.png) includes phone, tablet,
fold cover/inner, both orientations/themes, and narrow 200% text. These are
emulated windows on an owned API36 Play Store ARM64 image. The driver verifies
actual display dimensions, every PNG, keyboard field bounds, error/retry
visibility, and exact settings restoration. CI Google APIs x86_64 and physical
hinge callbacks remain separate evidence.

The fixture controls persistence failures and service responses. It does not
schedule a real alarm or supply a consumer check-in provider. Saving a draft
leaves the alarm off. Saving a setup file never establishes authenticated
verification. Ordinary users still need a supplied consumer approval method
before this app can launch. Pixel Doze, manual TalkBack, production signing,
and store distribution are not established by these images.

Codex reviewed all ten light contact sheets and the shared transition. An
independent design agent reviewed all twenty dark/enlarged-text sheets and
four full images. The reviewers found no rendered regression. This accepts
the advanced setup presentation and does not approve consumer launch.
