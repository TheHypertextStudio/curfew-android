# Check-in prerequisite and setup review

Reader: The Curfew Android designer and maintainer.
Action: Review the supported custom-service flow and keep consumer launch blocked until a consumer approval method exists.

Curfew does not supply a check-in service. This gallery reviews how the app
discloses that prerequisite, saves an alarm draft without enabling it, and
guides someone who already has an operator-run compatible service. It does
not establish that an ordinary consumer can complete alarm setup.

Open [the gallery](index.html) at
`http://127.0.0.1:4611/native/check-in-matrix/index.html`. The
[profile sheet](matrix-all-profiles.png) shows the prerequisite across all
18 window profiles. Thirty contact sheets cover each state in light, dark,
and enlarged-text groups.

The ten states are wake time, service prerequisite, failed draft save, service
address, keyboard entry, private-file handoff, ready to test, verified
connection, lock-screen access, and review. The 180 native state captures
include phone, tablet, fold cover, and fold inner windows in both orientations
and themes, plus a narrow 200% text profile. Four controlled frames show the
shared wake-time transition. The fixture controls service responses, export
completion, and persistence failures. It does not schedule a real alarm or
connect a hosted service.

The [manifest](manifest.json) records the installed GMS APK and application
source hashes. The driver verifies Android's reported display size and every
PNG's dimensions, checks field bounds above the keyboard, and checks full
visibility of the error and retry after a failed save. It restores the exact
display settings. Captures run on the task-owned API36 Play Store ARM64 AVD.
CI's Google APIs x86_64 images remain a separate verification boundary.

The first visual pass found a partly hidden error and retry at 200% text and
testing emphasized before file handoff. New regressions fail on both findings.
The corrected UI brings the error and retry into view together and emphasizes
saving the private file until the write succeeds. Export does not imply that
the operator installed the file or that authenticated verification succeeded.

An incomplete later run lost its emulator connection while reading a PNG.
The emulator logged a graphics-context failure. That run is rejected. The
task's emulator was restored and restarted before the complete replacement
capture. This event does not establish the cause of CI's separate System UI
crash.

These are emulated windows. Synthetic book/tabletop tests check hinge-region
geometry; the gallery does not establish physical fold callbacks. Enlarged
text can require scrolling, but failure feedback and its retry must appear
together after the action. Physical Pixel Doze, manual TalkBack, production
signing, and store distribution remain outside this visual evidence.

Codex reviewed all ten light contact sheets and the transition strip. An
independent design agent reviewed all twenty dark and enlarged-text sheets
and four full images after correction. The reviewers found no further concrete
rendered defect in these captured states. This accepts the advanced-path
presentation only and does not approve consumer launch.
