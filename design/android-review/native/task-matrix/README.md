# Native task screenshot matrix

Reader: The Curfew owner and Android reviewer.
Action: Review the full window/theme matrix before accepting the app-bar change.

The matrix contains 108 native Compose task captures: six screens across 18
window/theme/text profiles. Phone, tablet, fold cover, and fold inner windows
appear in portrait and landscape in both themes. Narrow 200% text appears in
both themes. The gallery links each full-resolution capture.

These are instrumented UI fixtures on actual emulated windows. They do not
establish physical hinge callbacks, system permission behavior, or hearing.
Check-in and readiness captures follow a scroll to their primary action. They
show scrolled viewports rather than the complete page. These images do not cover
keyboard-open, error/retry, configured check-in, or every readiness step. The
capture journey verifies the exercised primary actions remain reachable. No real account or alarm is changed.

The manifest records source commit, installed APK SHA-256, API level, restored
device settings, and the capture origin. PNG decoding, all 108 image names, clean source,
and the APK digest were checked before this gallery was generated.

The capture uses the repository's profile definitions and task-capture function
on the exact task-owned API34 ARM64 emulator. A task-only harness records the
before/after display settings in the manifest. All 18 journeys pass. The
preceding combined welcome/task run captured every image but failed its
restoration check; that run is rejected and supplies none of these images.

Codex and an independent design agent reviewed all 18 per-screen contact
sheets and relevant full-resolution captures. The short landscape check-in row,
repeat-day grouping, and 200% permission-button alignment are corrected. No
visible blocker remains in these captured states. This is separate from CI.
