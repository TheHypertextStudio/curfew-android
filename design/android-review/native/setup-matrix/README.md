# Native Android setup review

Reader: The Curfew Android maintainer and designer.
Action: Review every setup state and responsive profile before accepting the repaired flow.

The gallery contains 144 native state captures across 18 profiles and four
controlled shared wake-time transition frames. Application source is `8feb007`.
The [manifest](manifest.json) records its source hash and the installed Plain
APK hash. This evidence change does not alter the captured application source.

Open [the gallery](index.html) through the local preview server at
`http://127.0.0.1:4611/native/setup-matrix/index.html`. The filters select a
window profile and theme. The [full profile sheet](matrix-all-profiles.png)
shows the check-in introduction across every profile. The 24 `matrix-*.png`
sheets show each state across light, dark, and 200% text groups.

The eight states are wake time, check-in introduction, service address,
keyboard entry, setup-file transfer, connected check-in, lock-screen access,
and Review. The native fixture drives the actual app composition with controlled
persistence, service, and readiness results. It never schedules a real alarm.
Example addresses belong to the fixture and do not identify a hosted service.

The owned API34 ARM64 emulator covers phone, tablet, fold cover, and fold inner
windows in both orientations and themes, plus a narrow 200% text profile.
The driver verifies Android's reported display size and each PNG's dimensions
against the requested profile. It asserts full field visibility above the IME,
checks the intended destination, and restores the exact baseline settings.
Earlier clamped, obscured, or stale captures are rejected and are not included.

The [motion strip](shared-time-motion.png) and [animated frames](shared-time-motion.gif)
show Review to wake-time navigation at controlled 16, 96, 240, and 800ms offsets.
The GIF holds its last frame longer for inspection; it is not a real-time recording.

These are emulated windows. Synthetic book/tabletop region tests establish
layout geometry rather than physical fold callbacks. Screenshots do not prove
physical sound, Doze delivery, manual TalkBack, production signing, or release.
At 200% text, content can require scrolling. The connected Pixel was unavailable.

Codex reviewed the corrected layouts, and an independent design agent reviewed
all 24 corrected contact sheets and five full images without a visible blocker
in the 144 captured states. Unpictured states remain outside this acceptance.
