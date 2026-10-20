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
Native credential success and cancellation now have separate controlled-emulator evidence below. Physical key repeat and physical-device acceptance remain open. The recovery screen is connected to the authenticated local release path.


The consumer destinations use `CurfewAppNavigationInstrumentedTest.destinationsExposeTheirNamesToAssistiveTechnology` to check pane titles and heading semantics without duplicate visible headings. Its installed red run found the missing Sunrise pane title. Navigation, form retry, draft handling, and seven recovery input cases passed in that run. Capture fixtures cover the native light/dark, narrow-width, enlarged-text, and keyboard layouts; their images require visual review rather than an automatic design-quality claim.


`OwnerRecoveryJourneyInstrumentedTest` exercises the real Android credential UI, audio, terminal outcome, and audit. It runs only with an explicit fixture PIN on an emulator. `scripts/accept-owner-recovery-emulator.py` guards the owned Sunrise_API36 AVD, refuses an existing credential, installs a known temporary PIN, saves diagnostic captures, and clears the PIN in a finally block. The normal test suite skips these two opt-in cases; a skipped run is not acceptance. Its first two runs were red at alarm entry, first due to the stale System UI ANR and then the automatic PIN prompt caused by keyguard dismissal.

`RecoveryCompletionResumeInstrumentedTest` models process loss after the atomic recovery commit but before cleanup. Its first installed run failed because the next morning was never scheduled. `SunriseOccurrenceCompletionTest` checks that neither old repair trigger is canceled before consume and next-schedule persistence succeed. `AlarmNotificationInstrumentedTest` first failed on the posted wake-campaign title; its accepted presentation names the saved check-in instead of the internal attempt counter. Current green results remain pending.


The real credential fixture now passes both cases on the controlled API36 emulator in `credential-journey-back-navigation.txt`: authenticated override/audit/audio stop and native cancellation with continuing audio/no audit. This is native emulator evidence, not physical speaker or device acceptance. The script clears its PIN and cycles the screen before dismissing the keyguard so subsequent ordinary UI tests start unlocked. The installed notification and interrupted-recovery continuation cases pass in `daily-notification-resume-green.txt`; only the then-current text-layout cases failed in that run.

`SunriseDailyLayoutInstrumentedTest` covers narrow large-text day separation, text allocation, clock formatting, omission of redundant recurring dates, preservation of a one-night date, and proximity between the schedule and check-in. `CurfewDesignCaptureInstrumentedTest#captureDaily` supplies eight native light/dark, 320/390dp, 100/200% fixtures for visual review.

`AlarmSoundCheckTest` covers the six sound-confirmation rules. `SoundConfirmationPreferencesInstrumentedTest` checks that an old automatic playback flag cannot satisfy hearing confirmation. `AlarmSoundCheckJourneyInstrumentedTest` uses the real activity and alarm audio stream to verify explicit confirmation, replay, cancellation, and background cleanup. `AlarmSoundCheckDialogInstrumentedTest` checks text allocation and captures both themes at 100/200% text for confirmation, playback failure, and storage failure. See [SOUND_CHECK.md](SOUND_CHECK.md).

Sound-dialog captures assert `TextLayoutResult.layoutInput.density.fontScale` against the device configuration. Run them under actual normal and narrow/enlarged-text device profiles; a local density wrapper does not establish dialog scale coverage. `setupHeaderStaysWithinOneToolbarLine` catches a long task title painting into setup content. The real activity fixture grants its own exact-alarm and notification preconditions, then recreates the activity, so it does not depend on earlier tests granting access.

`PersistentCadenceJourneyInstrumentedTest` is an opt-in wall-clock acceptance case. Run `python3 scripts/accept-cadence-emulator.py` after installing the current plain debug app and test APKs on the owned `Sunrise_API36` AVD at `emulator-5554`. The driver refuses other AVDs, disconnects the emulator, records its original network settings, and requires the same occurrence to produce ring/quiet/ring audio evidence under the default 120/60-second policy. Native setup validates a loopback HTTPS check-in before closing that verifier. A failed callback must leave ringing active. The driver checks fresh timeline phases, exact occurrence identity, offline state, and audio transitions. It runs scoped fixture cleanup and restores the original network settings. A skipped default-CI case does not establish cadence acceptance.
