# Test strategy

The unit suite uses a fake clock and deterministic instants to cover every state transition, retry, persistent ring/quiet recurrence, authorized terminal outcomes, process snapshot recovery, elapsed-versus-wall-clock recovery, DST gaps and overlaps, recurrence bounds, callback golden vectors, tamper/replay rejection, selected-device convergence, exact-alarm permission fallback, OAuth PKCE, and distribution transport parity.

Instrumented tests cover schedule editing, wake-condition save/verification readiness, Room persistence, Android Keystore encryption under no-backup storage, manifest export/permission posture, and the no-dismiss/no-snooze ringing screen with a 48 dp action target.

CI builds and lints both distributions and runs unit tests for each. Emulator jobs exercise API 33–36 where hosted acceleration is available. `verifyPlainRuntimeIsolation` resolves the plain release graph and rejects Firebase or Google Play Services. The behavioral verifier at `verification/curfew-wake.json` lets Cello invoke alarm and wake-proof suites without a shell.

Before release, run the alarm on a current Pixel and at least one non-Pixel device, including locked-screen delivery, Doze, DND, full-screen denial, exact-alarm revocation, reboot, package replacement, timezone change, audio focus loss, and manufacturer battery management. Those physical-device results cannot be substituted by host tests.

The current P1 implementation and acceptance ledger is [SUNRISE_P1.md](SUNRISE_P1.md). A unit result, an emulator observation, physical sound, and a signed production release are distinct evidence. Do not infer physical acceptance from a compiled or installed APK.

The local P1 concurrency regressions include first-terminal-state preservation, forward-only ring/quiet persistence, old-occurrence playback isolation, and callback work-chain continuity. Run `AlarmRuntimeRepositoryInstrumentedTest`, `AlarmServiceIsolationInstrumentedTest`, and `CallbackPollSchedulerInstrumentedTest` on an installed build. `SunriseReleaseJourneyInstrumentedTest` uses a loopback TLS peer and a test-only certificate to exercise actual HTTPS callback authentication. The certificate never changes production trust configuration. This fixture does not prove external service availability or physical audio.

Owner recovery uses `OwnerRecoveryAuthorizationTest` for the reason, elapsed
hold, single-use confirmation, and expiry rules. Run
`OwnerRecoveryRepositoryInstrumentedTest` for atomic audit storage, competing
wake approval, rollback, future-alarm rejection, and backward clock recovery.
`RecoveryAuditMigrationInstrumentedTest` preserves schema 1 schedules and
callback configuration while opening schema 2. `CurfewSurfaceRolesTest` checks
bright/dim luminance ordering. `CurfewThemeInstrumentedTest` checks the actual
Compose light/dark roles rather than inspecting token source alone.

`AlarmRecoveryScreenInstrumentedTest` measures accessible confirmation with
elapsed time. Waiting for Compose idle can itself exceed three seconds, so
an assertion after that wait cannot prove that confirmation was immediate.
Foreground cancellation, physical key repeat, and the native credential
handoff remain acceptance gaps. The screen is not connected to alarm release
until those authorization and lifecycle paths are implemented and verified.


The consumer destinations use `CurfewAppNavigationInstrumentedTest.destinationsExposeTheirNamesToAssistiveTechnology` to check pane titles and heading semantics without duplicate visible headings. Its installed red run found the missing Sunrise pane title. Navigation, form retry, draft handling, and seven recovery input cases passed in that run. Capture fixtures cover the native light/dark, narrow-width, enlarged-text, and keyboard layouts; their images require visual review rather than an automatic design-quality claim.
