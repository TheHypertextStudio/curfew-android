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

The platform CI suite uses full `google_apis` images with SystemUI and LatinIME. ATD deliberately removes those components and cannot validate the keyboard, lock-screen, and complete alarm-media journey. Plain dependency isolation remains a separate release-graph check; a Google APIs emulator is not proof of physical behavior without Google services. CI uses an explicit Pixel 2 profile, two cores, and 1536 MiB of guest memory. Navigation fixtures scroll actions into view before real touch input so a short window does not turn an offscreen tap into a false behavior failure. The loopback PKCS12 test container uses Android 13-compatible wrapping without changing its TLS certificate; see the fixture's [README](../app/src/androidTest/assets/README.md).


The daily density regression first fails with the previous time sizing at
267.4dp. The later density pass tightens the ordinary 390dp limit to 220dp.
The previous layout fails that limit at 238.9dp.
This is a visual grouping constraint, not a limit on enlarged-text height.
Run the whole `SunriseDailyLayoutInstrumentedTest` class and
`CurfewDesignCaptureInstrumentedTest#captureDaily` after changing its spacing.


## Actual process-loss acceptance

Run `python3 -B scripts/accept-process-loss-emulator.py` only after installing
the plain debug application and instrumentation APK on the owned `Sunrise_API36`
AVD at `emulator-5554`. The driver refuses other AVDs and real devices.
The opt-in native fixture refuses existing pending or upcoming alarms, validates
its loopback verifier, closes that verifier, and saves a one-night alarm ten
seconds ahead. Instrumentation ends before Android delivers the alarm.

The external driver observes the application's active alarm audio, reads the
stored occurrence through a temporary Room database snapshot, and sends SIGKILL
to that exact application PID. It requires a different process, foreground
playback, active alarm audio, and Android's crashed-service restart record.
The occurrence ID, attempt, and ringing boundaries must remain unchanged.
Recovery must happen before the original two-minute boundary. Native cleanup
removes only the controlled occurrence and callback. The script does not use
force-stop, shorten the cadence, or grant a new production capability.

Evidence lives in `build/sunrise-evidence/process-loss/`. This establishes an
installed emulator observation, not a manufacturer's battery policy or physical
speaker acceptance. The default suite skips this opt-in fixture.


The held-key recovery fixture explicitly requests keyboard input mode and
asserts focus before dispatching Enter. A semantic focus request from touch mode
is not evidence of a physical keyboard path. The initial key, repeat, release,
and three-second checks remain unchanged. The release journey's timeout now
reports pending state, notification presence, observed alarm audio, and exact
alarm access separately so a compound predicate does not hide the failed stage.
The corrected recovery and delivery fixtures pass locally in ten cases
(25.796s). Their remote result still requires CI.


## Wall-clock Doze acceptance

Run `python3 -B scripts/accept-doze-emulator.py` on the owned API36 AVD only.
Install the current plain app and test APK first. The script refuses an existing
pending or upcoming alarm. It saves a real one-night alarm 65 minutes ahead,
ends instrumentation, disconnects emulated charging, turns the screen off, and
requires observed deep idle before waiting for alarm audio. Do not run another
native test, install, reboot, or change the emulator during that wait.

Android's default upcoming-alarm guard prevents entering deep idle within an
hour of an alarm on this image. AlarmManager also leaves idle before alarm-clock
delivery. The 65-minute case preserves those policies and the real clock. It
uses no idle-threshold override and does not shorten Curfew's ring/quiet cadence.
[Android's Doze guide](https://developer.android.com/training/monitoring-device-state/doze-standby)
describes alarm-clock delivery and the shell idle controls.

Evidence lives in `build/sunrise-evidence/doze/`. The driver records deep-idle
entry, a live status file, installed APK digest, delivery time, unchanged stored
occurrence, audio observation, and cleanup. A live status file alone does not
prove that the process is running or that the alarm passed. Revalidate the
specific exec session or process before waiting on it. Cleanup restores idle,
battery reporting, and the awake screen, then removes the controlled fixture.
This is emulator acceptance; physical sleep, speaker output, and manufacturer
policy remain separate.

## Secure lock-screen presentation

Run `python3 -B scripts/accept-presentation-emulator.py` only on the owned
`Sunrise_API33` AVD at `emulator-5558`. The driver requires an awake,
credential-free emulator, installs a temporary fixture PIN, saves an alarm
90 seconds ahead, ends instrumentation, and sleeps the display. It requires
actual alarm-track activity, automatic display wake, Curfew window focus,
the rendered ringing controls, and unchanged occurrence boundaries. Cleanup
clears the fixture PIN and deletes only the scoped alarm even after failure.
Failure diagnostics are captured before cleanup changes the lock state.

The first two observations on `9ec905b` failed at automatic display wake.
The second captured a high-importance, unsuppressed alarm notification with
a full-screen intent while the display stayed asleep. Android 13's
[notification activity starter](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-13.0.0_r1/packages/SystemUI/src/com/android/systemui/statusbar/phone/StatusBarNotificationActivityStarter.java)
evaluates full-screen launch on entry addition. Curfew had updated the
silent foreground-start entry. The correction gives the startup and ringing
notifications distinct identities, promotes the ringing notification to the
foreground, and cancels the startup entry. A fresh installed observation must
pass before accepting this correction. No new permission or direct activity
launch bypass is introduced.

The corrected plain APK passed the external driver on October 20 at
13:46:27.860787 UTC. Alarm audio was active, the display woke automatically,
Curfew gained window focus, and the ringing controls rendered over the
secure keyguard. The same occurrence kept its original first-attempt bounds.
The driver captured a screenshot and verified PIN and scoped-alarm cleanup
before writing `locked-presentation-api33/result.json`. This is API33
emulator evidence; physical speaker output and newer-platform full-screen
permission behavior require separate acceptance.

The corrected GMS APK passed all 115 reported API36 installed cases locally
in 262.452s. These include the normal HTTPS-release, service-isolation, and
UI cases; opt-in lifecycle cases still require their separate drivers.
The earlier `9ec905b` API36 CI suite timed out without printing its partial
test output. `python3 -B ci/test-instrumentation-output.py` first reproduced
that lost-output failure, then passed after guarding the shell command's
exit. CI now retains partial runner output and collects error logcat when
instrumentation times out or fails. This improves diagnosis; it does not
turn the earlier timeout into a passing result or increase its time limit.

The first API33 suite after the secure-lock driver could not find Compose
hierarchies because credential-free keyguard still covered its test activity.
The PIN had cleared, but that did not dismiss keyguard. The driver cleanup
now explicitly dismisses it after clearing the fixture credential and
requires `showing=false`. The interrupted suite is preserved separately;
its failed UI cases do not establish an app regression or a passing run.
After restoring the unlocked test screen, the complete corrected Plain API33
suite passed all 115 reported cases in 163.046s. The raw first run and the
fresh complete result remain separate files.
The final driver rerun passed at 13:54:28.816287 UTC with automatic
presentation and active audio again. Cleanup passed and a subsequent
read-back confirmed both `secure=false` and `showing=false`.


## Local transition and optional publication

`LocalAlarmTransitionTest` checks local delivery before coordinator publication,
network failure isolation, local failure propagation, cancellation, and release
of the broadcast result before publication. The previous publish-first ordering
fails three behavior assertions. The new broadcast-finish requirement also
failed before implementation. Both distribution host suites now pass 153 tests.

Run the installed HTTPS journey, service isolation, cadence, and process-loss
checks after installing the local-first APK. The in-progress Doze observation
uses the previous runtime and cannot establish those new installed results.


## Default sound failure and fallback

[Alarm audio](ALARM_AUDIO.md) defines the shared persistent/rehearsal path.
`AlarmSoundPlaybackTest` has nine host cases for preference, failure, cancellation,
and stale source ownership. Its late-null-source regression failed before the
extra generation guard. Run `AlarmSoundFallbackInstrumentedTest` for real bundled
playback from an unreadable preferred URI and the two-second preview limit.
The test preserves the original alarm-stream volume. It changes no default URI
or other global sound setting. `AlarmAudioProbeInstrumentedTest` retains the
known-PCM probe and observes Curfew's actual preferred-or-fallback playback.
The HTTPS journey retains its audio-start and audio-stop requirements.


`SunriseScheduleEditorInstrumentedTest.changingTimeKeepsAnExplicitOneNightDate`
opens the actual date picker, chooses fourteen days ahead, changes the actual
time picker to 08:15, and saves the editor draft. It checks the chosen date and
time independently. The original editor resets a new alarm's date when its time
changes; native failing-first evidence is still pending. Both test APKs and both
lint variants compile this regression. A missing widget or failed interaction
would be a fixture failure, not proof of the date-reset defect.


The one-night regression now has native red/green evidence on the isolated API35
UI AVD. The red assertion changed November 3 to October 20 after a time edit.
The fix preserves the explicit date while the midnight case still rolls an
inferred date to tomorrow. Five editor and seven recovery cases pass together
(31.564s). The full default installed suite then passes with 91 reported cases
(147.602s); opt-in cases remain separate. The final themed editor fixture and
capture pass five cases in 9.989s. Screenshot evidence is
`build/sunrise-evidence/one-night-date-draft.png`.

The early-release recovery fixture records its cancellation counter before the
press. Disposal after entering the reason can already increment that counter,
so waiting for any nonzero value does not establish that the press was handled.
The corrected fixture waits for a new cancellation and requires a visible,
enabled button before touching it. It retains the start and no-confirmation
assertions.


AlarmServiceIsolationInstrumentedTest also invokes real worker completion
for an old terminal occurrence while current alarm audio plays. The test
requires that current audio and its ringing notification remain active.
It does not exercise network authentication or claim P1b acceptance.


The delayed-service-command regression starts the actual service for an old
terminal occurrence while a newer occurrence owns alarm audio. It requires
the newer audio and presentation to survive. AlarmPlaybackPlannerTest also
covers known versus unknown active occurrences, terminal state, quiet phase,
and continuation of an in-flight sticky recovery. These cases do not permit
a stale explicit command to select an unrelated alarm.

Run `python3 -B scripts/accept-reboot-emulator.py` only on the owned
Sunrise_API36 AVD at emulator-5554 after installing the current app and test
APKs. It refuses an existing credential, saves a controlled alarm two minutes
ahead, installs a temporary PIN, and reboots. It observes the chosen time
while the credential-encrypted user remains locked, then unlocks and checks
the original occurrence's recovered audio and boundary. Its finally block
clears the temporary PIN and removes only the controlled fixture. A missing
pre-unlock alarm is recorded as a support limitation, not a passed ringing
case. The result remains emulator evidence without physical speaker proof.


The reboot driver allows an 80-second post-unlock observation but requires
attempt 1 and the original start boundary. It does not accept playback that
only begins on a later cadence attempt. Retain the failed initial boot run
and the shorter observation rather than overwriting them with a green result.
The setup disclosure case fails if the first-unlock instruction is absent
from the required device-limit acknowledgement. It also captures the native
readiness screen for visual review.


Run `python3 -B scripts/accept-package-replacement-emulator.py` after building
the plain app and test APKs and installing both on the owned Sunrise_UI_API35
AVD at emulator-5556. This driver refuses other AVDs and real devices. It saves
a local one-night alarm 45 seconds ahead, replaces the APK through
PackageManager before the chosen time, and requires the same occurrence's
first audio attempt at the original boundary outside instrumentation. Its
finally block removes the controlled fixture. A missing original app process
is valid background behavior, not a failed process-restart assertion.
The shared external-lifecycle fixture uses the actual device timezone because
recovery resolves saved local times again. This does not alter production
schedule resolution or the default offline cadence fixture.


Run `python3 -B scripts/accept-clock-emulator.py` only on the owned
Sunrise_API36 AVD at emulator-5554 after installing matching current app/test
APKs. It disables automatic time temporarily, moves the emulator wall clock
five minutes forward during actual audio, and requires the same first attempt
to reach quiet at its original two-minute elapsed boundary. A short unchanged
snapshot alone does not pass. The driver restores the original clock offset
and automatic-time setting and removes its scoped fixture. The callback worker
must use elapsed recovery too; an early green observation missed that writer.
See [active clock rules](ALARM_CLOCK.md) for the required domain invariants.


Use `scripts/accept-clock-emulator.py --backward` for the corresponding
five-minute reverse adjustment. Both directions require the real two-minute
boundary and preserve their records in separate directories.

`scripts/accept-timezone-emulator.py` requires the owned Sunrise_UI_API35
AVD on port 5556 with its initial Los Angeles zone. It saves a future local
alarm, observes an Anchorage round trip, requires the original occurrence's
audio, and restores the zone and automatic-zone settings during cleanup.

`scripts/accept-relaunch-emulator.py` requires Sunrise_API36 on port 5554.
Its `--api33` preset requires Sunrise_API33 on port 5558. Both presets refuse
other AVDs and real devices. The driver saves an alarm, waits for actual audio,
force-stops the disposable app, and reopens MainActivity without starting a
service by hand. It requires the same first attempt and boundary to regain
audio within 30 seconds. A notification or persisted Ringing state alone cannot
pass. Cleanup removes only the scoped occurrence. Android 13 must pass directly;
newer-platform reopen broadcasts cannot stand in for oldest-platform evidence.


`scripts/accept-permission-emulator.py` requires the owned Sunrise_API33 AVD
on port 5558. It saves an alarm two minutes ahead, revokes exact-alarm access,
and reopens Curfew. Its opt-in native probe requires Android and readiness to
report revoked access while preserving the enabled schedule and occurrence.
It then restores access, returns through MainActivity, and requires the same
occurrence's first audio at the original boundary. The driver restores alarm
access and removes its fixture in cleanup. This is permission recovery on one
emulator, not a manufacturer or physical-device acceptance result.


The package-replacement driver's `--api33` preset uses the owned Sunrise_API33
AVD on port 5558 and writes a separate evidence directory. Its default still
requires Sunrise_UI_API35 on port 5556. Neither preset accepts arbitrary
serials or real devices. The permission driver additionally reads the native
accessibility XML. It requires Needs attention, rejects Alarm on, and requires
the specific Alarm access is off message before restoring access. Its native
probe verifies platform readiness and durable state independently from copy.

## Compact daily composition

The October 20 density pass removes the repeated normal on/off status row.
`SunriseDailyLayoutInstrumentedTest#ordinaryAlarmDoesNotSpendARowRepeatingItsSwitchState`
failed against the earlier layout before production edits. It checks that
the switch sits beside the time and that the ordinary 390dp alarm plus
check-in fits within 280dp. Existing tests retain enlarged-text allocation,
weekday separation, a one-night date, missing-readiness status, and clock
formatting. Native captures cover both themes at 320/390dp and 100/200% text.

This changes presentation only. The saved schedule, alarm delivery, and
release authority do not change. Reverting the composition restores the
previous spacing without a migration. The HTML review frame now fits the
available viewport instead of forcing an 844px phone. Short review windows retain internal scrolling. The review remains a
proposal and cannot establish native functionality.

The final compact-layout run passed 24 installed API33 checks in 44.15s,
including fourteen layout cases, nine navigation cases, and eight captures
from one capture case. Plain/GMS builds, both 181-case unit suites, and both
Android lint tasks passed. The source diff and preview JavaScript syntax
checks passed. The then-current API36 CI alarm-presentation failure was independent of
these visual checks. The later `c83bb57` CI matrix passes all four API levels.

## Late-transition playback evidence

The API36 run at runtime head `843ac01` failed before the old transition
was delivered. That case used a five-second notification-only startup
window. The two adjacent isolation cases used a ten-second condition wait
for both notification and actual alarm audio. The late-transition case now
uses the same bounded condition wait, records persisted state, notification
channels, and audio status on failure, and requires audio before and after
the old broadcast. API33 GMS passes all four isolation cases in 9.477s.
This strengthens test observation without changing service behavior. CI
at `c83bb57` subsequently passes API33/34/35/36 and both verification jobs.

## Branded alarm pickers

`SunriseScheduleEditorInstrumentedTest` first failed the two themed-action
cases against framework dialogs. The replacement uses stock Material time
and date controls inside a Curfew modal. Tests exercise Done versus Save,
cancellation, Back, invalid values, midnight/noon, inferred versus explicit
one-night dates, recurring selection, and saved identity. The date tests
enter actual localized input and assert the resulting calendar date. They
do not set internal picker state.

`AlarmPickerLayoutInstrumentedTest` checks actual dialog-window font scale,
text allocation, both themes, and a real touch on Done with the keyboard
open. It allows at most one physical pixel for text rounding and checks each
line against its allocated constraints and line limit. Stock picker text
can retain a wide paragraph while reporting a tighter glyph box; that
empty paragraph width is not glyph clipping.
It does not clamp font scale. `scripts/accept-picker-layout-emulator.py`
refuses any device except the owned Sunrise_API33 emulator. It runs normal,
320dp, and 320dp/200% profiles with both clock formats, exports full-display
captures, and restores density, text scale, clock format, and animations
in its finally block. Android can materialize an absent font-scale key as
the equivalent default `1.0`; the driver records read-back settings and
checks that effective default. This evidence covers native layout and interaction;
it does not establish physical speaker volume or wakefulness.

Picker changes require no storage migration or protocol release. A revert
restores framework dialogs while preserving saved schedules.

The final picker candidate passes all four owned API33 profiles. Each
reports 18 checks; the dial and calendar cases skip where those controls
are intentionally replaced by narrow/enlarged input. Six such skips occur
across the three constrained profiles. The normal profile exercises both
controls, including a real dial tap. The driver verifies cleanup and records
the installed APK checksum. Both 181-case host suites, both app/test builds,
and both Android lint variants pass. Final native captures and their actual
profile manifest are in `design/android-review/native/pickers/`.
