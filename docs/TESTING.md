# Test strategy

The unit suite uses a fake clock and deterministic instants to cover every state transition, retry, persistent ring/quiet recurrence, authorized terminal outcomes, process snapshot recovery, elapsed-versus-wall-clock recovery, DST gaps and overlaps, recurrence bounds, callback golden vectors, tamper/replay rejection, selected-device convergence, exact-alarm permission fallback, OAuth PKCE, and distribution transport parity.

Instrumented tests cover schedule editing, wake-condition save/verification readiness, Room persistence, Android Keystore encryption under no-backup storage, manifest export/permission posture, and the no-dismiss/no-snooze ringing screen with a 48 dp action target.

CI builds and lints both distributions and runs unit tests for each. Emulator jobs exercise API 33–36 where hosted acceleration is available. `verifyPlainRuntimeIsolation` resolves the plain release graph and rejects Firebase or Google Play Services. The behavioral verifier at `verification/curfew-wake.json` lets Cello invoke alarm and wake-proof suites without a shell.

For P1, run the alarm on the connected Pixel, including locked-screen delivery, DND, full-screen denial, exact-alarm revocation, reboot, package replacement, timezone change, audio focus loss, and manufacturer battery management. Those physical-device results cannot be substituted by host tests. Non-Pixel testing belongs to later compatibility work and does not gate P1.

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

The platform CI suite uses full `google_apis` images with SystemUI and LatinIME. ATD deliberately removes those components and cannot validate the keyboard, lock-screen, and complete alarm-media journey. Plain dependency isolation remains a separate release-graph check; a Google APIs emulator is not proof of physical behavior without Google services. CI uses an explicit Pixel 2 profile, two cores, and 1536 MiB of guest memory. Navigation fixtures scroll actions into view before real touch input so a short window does not turn an offscreen tap into a false behavior failure. Date and time entry share a native-input readiness helper: each field establishes actual focus, visible keyboard insets, and a settled dialog before text replacement or the next picker tap. They wait for the editor window after dismissal and verify the selected date before changing the time. The loopback PKCS12 test container uses Android 13-compatible wrapping without changing its TLS certificate; see the fixture's [README](../app/src/androidTest/assets/README.md).

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
turn the earlier timeout into a passing result.

Run `39434561792` then exhausted the five-minute limit on API34 after
reaching case 111 of 115 and on API36 after reaching case 103. Neither log
reported an assertion failure before timeout. The complete local API36 run
takes 262.452s. CI now permits ten minutes for the expanded full suite on
its two-core emulator. It retains all cases, requires the final nonempty
success result, and still fails and collects diagnostics after timeout.
The failed runs remain failed until a fresh full matrix passes.

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

Run the same presentation driver with `--api 36` on the owned API36 AVD
at `emulator-5554`. It temporarily allows full-screen access, records the
original app-op, and restores that exact mode in cleanup. The October 20
fixture at 13:57:53.323938 UTC passed automatic display wake,
foreground focus, rendered ringing controls, active audio, and unchanged
occurrence bounds. Cleanup restored the original `default` access mode and
the unlocked, credential-free screen. The timestamp in `result.json` is
authoritative; local emulator status-bar time uses its configured timezone.

Use `--api 36 --full-screen deny` to test the fallback. The October 20
fixture at 14:02:59.375979 UTC passed: active audio, a real alarm notification
with a content intent and no full-screen intent, no automatic Curfew focus,
and entry to the unchanged ringing screen through a notification tap and
Android's PIN prompt. The driver waits for that actual prompt before typing
its fixture PIN. An earlier attempt typed before the prompt appeared and
did not establish fallback failure in the app. The final driver records
`automaticForegroundPresentation=false` and `notificationTapPresentation=true`.
Cleanup restores access and removes the fixture even if another cleanup
step fails; it reports every cleanup failure rather than skipping later steps.
The original `default` access mode and unlocked, credential-free state were
verified after the passing run. Physical-device fallback remains open.

## Do Not Disturb and actual readiness

Run `python3 -B scripts/accept-dnd-emulator.py` on the owned API33 AVD.
The driver requires the original DND-off state. It tests no interruptions,
alarms only, and off through the real notification service, then restores
the original off state in `finally`. It records the installed app digest,
audio-service dumps, native test output, and cleanup evidence.

`AlarmDndReadinessInstrumentedTest` requires this driver's opt-in argument.
Its installed assertions passed for all three modes on October 20. No
interruptions reports effective alarm volume zero and Curfew requires sound
repair and refuses readiness. Alarms-only and off report nonzero effective
alarm volume and do not require volume repair. The test supplies completed
hearing/acknowledgement preferences in memory; it does not overwrite saved
preferences or claim that a person heard sound. The driver changes no volume
setting and requests no notification-policy permission for the app. Both
distribution instrumentation builds and lint checks passed after adding
this observational test. Physical DND and manufacturer behavior remain open.

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

## Authenticated approval rejection through real TLS

The installed `SunriseReleaseJourneyInstrumentedTest` now exercises pending,
forged, malformed, wrong-occurrence, expired, future-dated, replay-seed,
replayed-approval, and HTTP503 responses before the final valid approval.
Each case checks persisted ringing, the alarm notification, and actual
alarm-track activity. The final approval must stop both presentation and
audio, consume the one-night occurrence, record the condition, and remain
harmless when the worker retries. The journey requires at least eleven
actual TLS exchanges, including setup verification.

Earlier forged and wrong-occurrence fixtures also returned pending status.
That combination did not prove rejection of an invalid response claiming
success. Invalid fixture responses now claim satisfied. Expired approval
uses the current challenge timestamp and an expiry one nanosecond later,
so it is already expired on receipt without predating its challenge. The
replay seed returns pending while retaining a separately signed satisfied
approval for that old nonce; the next request receives that old approval.
The unavailable response uses HTTP503 even though its body claims success.
These are test-only changes using the existing published callback protocol.

Both instrumentation builds and Android lint variants pass. The strengthened
journey passed on the owned API33 emulator in 6.004s for Plain and 6.036s
for GMS. The driver restored the Plain app and test APK afterward. These
observations do not replace physical hearing or current-platform lifecycle
acceptance. The final-runtime Doze driver on API36 remained untouched.

## Exact occurrence information for assistive technology

`SunriseDailyLayoutInstrumentedTest#editableTimeExposesTheActualOccurrenceWithoutAnotherVisibleDateLabel`
first failed because the editable time had no occurrence accessibility state.
It now exposes the stored occurrence's local date and time on that same
control. The test intentionally gives future settings and the stored
occurrence different times, then requires the stored value and no extra
visible date label. This covers an edited future schedule or a DST-adjusted
occurrence without adding another row. The focused installed test passes
in 1.362s. Both distributions pass 181 host tests, app/test builds, and lint.

`scripts/accept-process-loss-emulator.py --api33` uses only the owned API33
AVD at `emulator-5558` and records its installed APK digest. API33's run-as
security context denied the signal, so that preset uses the disposable AVD's
existing root shell after checking the exact target PID and app UID. This
does not add a production permission or change SELinux. The observed kill
of PID4993 restored playback in PID5092 in 1.632s with unchanged occurrence,
attempt, and ringing bounds. Android logged the crashed-service restart;
active audio and scoped cleanup passed. The default API36 preset retains
its original signal path. The API36 standby observation remained untouched.

The complete API33 Plain suite after the accessibility change passed 117
reported cases in 163.524s. The captured light/normal and dark/large daily
screens retain the compact composition without an extra date row.

`scripts/accept-cadence-emulator.py --api33` then passed the real offline
cadence on the corrected notification runtime in 185.338s. The first ring
began at 14:28:36.884839 UTC; quiet began at the original two-minute boundary;
the second attempt began at 14:31:36.884839 with the same occurrence ID.
Actual alarm-track states were on/off/on and no active default network was
available throughout. The driver removed the scoped fixture and restored
airplane mode off, Wi-Fi on, and mobile data on. It records the installed
APK digest. The default API36 driver remains available, and its live
standby observation was not modified by this API33 run.

## Concurrent lifecycle recovery and schedule changes

The independent P1 review found that recovery could save a stale Scheduled
snapshot after a disable deleted it or a same-day edit changed its time.
`AlarmRecoveryConcurrencyInstrumentedTest` pauses recovery after reading
that snapshot and queues each competing write. The preserved split-read/save
implementation failed both installed cases on API33: it recreated the
removed occurrence and restored the old time. The transaction fix passed
both regressions plus the three existing repository guards in 0.612s.
The edit case also checks that the release-condition identifier survives.
A missing row cannot enter the update callback.

The initial compiler failure established the missing atomic-update contract.
The separate installed red run reproduced the actual two data races before
transactional behavior changed. Logs are `recovery-race-red-build.txt`,
`recovery-race-runtime-red.txt`, and `recovery-race-green-plain-api33.txt`
under `build/sunrise-evidence/`. An initial unauthenticated dependency fetch
and an offline cache miss were setup failures, not failing-test evidence.
Authenticated dependency resolution restored the build.

Recovery now reads and writes one existing occurrence inside a Room
transaction. Lifecycle timer restoration holds the schedule mutex through
registration, so a later disable cancels that timer. The scheduler releases
its own restore lock before runtime reconciliation acquires it. The
independent reviewer checked this order and the nested Room transaction.
Both distributions pass all 181 host tests, app/test APK assembly, and
Android lint after the change. The full API33 Plain suite reports
`OK (119 tests)` in 167.074s; opt-in cases still require their drivers.
No protocol, schema, permission, or migration changes are required. Rolling
back restores the stale-write risk; preserve the concurrency tests when
changing this path.

The fixed runtime also passed all five repository cases under GMS on API33
in 0.581s. A fresh external SIGKILL observation restored foreground alarm
audio in 1.643s with the original occurrence and first-attempt bounds.
The driver exited successfully and removed its scoped fixture. Prior API33
process evidence is preserved under
`process-loss-api33-before-atomic-recovery/`; the new result is in
`process-loss-api33/`. Neither observation establishes physical hearing.

The fixed runtime passed a fresh offline ring/quiet/ring observation in
185.124s. Audio stopped at the original two-minute boundary and resumed at
the one-minute quiet boundary without a new occurrence or network access.
The prior result is preserved under
`cadence-offline-api33-before-atomic-recovery/`.

The initial cleanup sample recorded mobile data off while Android was still
applying its restore request. A later device read confirmed the original
value. The driver now waits up to five seconds for every original network
setting and fails if any value remains different. Two host regressions
first failed for the missing restoration check, then passed for delayed
success and persistent failure. Both run in the CI verification jobs.
The revised helper was also run on the guarded API33 AVD and confirmed the
original values: airplane mode off, Wi-Fi on, and mobile data on. The
initial sample remains in `restored-network-initial-sample.json`; the
confirmed state is in `restored-network.json`. This driver correction adds
no app permission or runtime behavior. It does not turn the pending API36
Doze observation into a success.

## Consumer alarm labels

The installed navigation and accessibility expectations now use “Edit alarm”
and the “Alarm” pane instead of requiring the feature name in ordinary tasks.
Both existing assertions first failed against the old installed labels in
`consumer-labels-red.txt`. The update preserves the occurrence's date/time
accessibility state and the native switch's checked semantics. Other native
fixtures use the same labels to exercise the real controls.
Both distributions pass their 181 host tests, app/test APK builds, and lint.
The review-page scripts pass Node syntax checks. Installed and rendered
results remain separately recorded after the complete suite finishes.

The initial label pass reports all 119 installed cases green in 170.796s.
The subsequent design review found a redundant Done in unchanged connected
detail, cryptography terms in file guidance, and duplicate save/connection
success messages. Two new installed CheckInScreen regressions failed before
that correction in `check-in-completion-red.txt`. They require the ordinary
detail to retain a file-save error without another exit button, and setup to
keep an operative file-save action and completion after verification.
The second test also enters replacement, keeps the existing connection,
and then completes setup. It caught a candidate that cleared completion
intent too early (`check-in-detour-red.txt`). Successful new selection now
starts setup; canceling replacement preserves the prior intent. This adds
no extra backup state and does not suppress errors on verified connections.
Both distributions pass host/build/lint checks after the final correction.
The final API33 Plain suite reports `OK (121 tests)` in 167.768s.
The GMS check-in class also passes all four installed cases in 7.416s.
Fresh native captures are retained under
`design/android-review/native/consumer/`, with the APK digest and fixture
parameters. Visual inspection found no overlap or clipping in the normal
connected detail, 320dp enlarged-text detail, editor, or initial form.
These results do not establish physical-device hearing or manual TalkBack.

## Completed corrected-notification standby observation

The externally observed 65-minute API36 run entered deep idle at
14:08:32.897704 UTC on October 20. Its unchanged alarm was scheduled for
15:13:31.268582. The five-second observer found active alarm audio at
15:13:35.992585, a 4.724-second observation delay. The stored occurrence
retained attempt 1 and the original 15:13:31–15:15:31 ringing bounds.
The driver exited successfully; native fixture cleanup reports OK (1 test).
Read-back confirmed force idle off, normal charging, screen on, and both
idle state machines ACTIVE. The emulator had no credential afterward.

The installed APK digest is
`24587bc2ebd649044880ff86cc4ff95f025f11cee12789ac4ab87b802885981e`.
This candidate contains the notification correction and predates the later
atomic-recovery and consumer changes. Those changes retain their separate
installed/CI evidence. Do not relabel this run as physical acceptance or as
a byte-identical test of the later APK. Results and cleanup live in
`build/sunrise-evidence/doze/`; the driver log is
`presentation-runtime-doze-driver.txt`. The previous runtime's standby result
remains in its archived directory.

After the standby driver exited and restored the AVD, the latest GMS app
and test APKs passed both real Android credential journeys on API36 in
14.856s. Successful screen-lock authorization stops audio and records the
override; cancellation keeps audio and records no recovery audit. The
fixture recognizes the shortened cancellation feedback. The installed APK
matches the latest built GMS SHA256:
`960180b46f4e35b43db9e19638abeb52a1b236b6ccb42745926994c0056f22e7`.
The driver exited successfully. Independent read-back confirmed no credential
and no showing keyguard. The fixture PIN never touches a physical device.
Results are in `credential-journey/` and
`consumer-final-credential-journey.txt`; the earlier record remains archived.

## Responsive window regression

`ResponsiveAlarmLayoutInstrumentedTest` reproduces the repeat-choice failure
at 240dp and 200% text. The original row breaks “Weekly” into five lines.
The corrected layout must render both complete labels and keep Save reachable by scrolling.
`SunriseDailyLayoutInstrumentedTest` also checks every weekday at that width.
`CurfewLayoutModeTest` covers short windows and text-aware navigation selection.

`CurfewAppNavigationInstrumentedTest.scaledWindowKeepsDailyAndEditorActionsReachable`
uses Android's actual window and font configuration. It traverses daily,
editing, weekly selection, Settings, and Account without saving. It captures
the daily, editor, and account surfaces in the test app's private cache. Run
this fixture on an emulator, not against an owner's saved phone state. The
local matrix driver restores its original display size, density, and text
scale in a finally block. Evidence belongs in
`build/sunrise-evidence/responsive/`. These layout checks do not establish
physical alarm delivery or hearing acceptance.

The first rendered matrix exposed a second failure: the leading icon squeezed
“Morning” onto two lines at 240dp/200% text.
`scaledNarrowCheckInDoesNotSplitItsFirstWord` failed before the icon adaptation.
The regression checks the word's first and last character on the same line;
it does not mistake the absence of an overflow flag for readable wrapping.

The initial regression used `hasVisualOverflow`, which also reports a wider
intrinsic paragraph when its visible glyphs fit. The corrected regression
checks line count, actual glyph bounds, and containment in the window. It was
rerun against the original horizontal row and failed with five “Weekly” lines
before restoring the wrapping implementation. The earlier failed full-suite
log retains that test-diagnostic boundary rather than claiming a clean run.

The final targeted suites pass 32 API33 Plain cases in 120.967s and 32 API36
GMS cases in 153.174s. Both 182-case host suites, app/test builds, Android lint,
and `git diff --check` pass. The five actual-profile journeys pass. The captured
Plain APK still matches the final built APK byte for byte. The physical
Pixel's installed GMS digest also matches the final built GMS APK. Local logs
remain private build artifacts. New CI evidence must be read separately.

## Concurrent first-start identity and encryption

A fresh `.acceptance` GMS install failed before scheduling with
`AEADBadTagException`. Its package and instrumentation target were checked
with AAPT; it used a separate app UID and private storage. It was installed
only on the owned API36 emulator, not on the owner's phone.

`AndroidKeystoreSecretStoreInstrumentedTest` then reproduced two failures:
two stores could replace a fresh shared key alias and make a written secret
unreadable; two authenticators could return different device IDs. The secret
case uses two writers across 12 new aliases. The identity case uses a scoped
private directory and verifies both returned IDs against the persisted ID.
Both failed before the shared monitor and atomic read-or-create correction.
The unchanged round-trip case and both regressions now pass together.

A clean isolated reinstall then passed the TLS schedule-to-release journey
and all three secret tests: four cases in 15.406s. A separate probe scheduled
an alarm, ended instrumentation, observed actual UID-scoped alarm audio and
the same first ringing interval, and cleaned up its occurrence. This is
emulator audio-track evidence, not physical speaker evidence. The temporary
isolated packages were removed after their cleanup passed.

The normal Plain API33 and GMS API36 apps pass 13 targeted secret-store,
TLS, repository, polling, and service-isolation cases in 29.634s and 33.673s.
Both 182-case host suites, app/test APK builds, and Android lint pass. Logs
are in `build/sunrise-evidence/physical/`. That directory name does not turn
its isolated-emulator results into physical acceptance. One earlier Plain
command used the wrong repository test package; its class-loading failure
is retained separately from the corrected passing run.

## Adaptive launcher icon

`LauncherIconInstrumentedTest` renders the packaged adaptive icon and checks
sun color, transparency, full-bleed dusk gradient, mask-safe geometry, and
monochrome silhouette. It first failed against the stock robot, then passed
two native cases on API36 GMS in 0.166s and API33 Plain in 0.056s.
Both distributions pass 182 host tests, APK builds, and Android lint. The native contact sheet is
`design/android-review/native/icon/launcher-icon-review.png`. See
[the icon design and rollback notes](ANDROID_ICON.md).

## Full-screen onboarding access

Full-screen access is required for setup readiness on Android 14 and newer.
Android 13 reports the capability as available without a separate settings
step. `AlarmReadinessEvaluatorTest` first failed with denied full-screen
access incorrectly returning ready. It now requires the access action while
retaining notification fallback presentation for an already-active alarm.
`CurfewAppNavigationInstrumentedTest` checks the missing-access button,
request callback, and transition to Done after the capability is granted.
MainActivity opens the package-specific Android full-screen access setting
and re-evaluates capabilities on resume. No setting is silently granted.

The user requested a Pixel app-state reset. The device disconnected before
installation/reset, so that reset has not occurred yet. Verify the fresh
installed onboarding separately from synthetic navigation evidence.

The full-screen follow-up passes 182 host tests per distribution, both
app/test APK builds, and both Android lint checks. Before compacting the
status rows, API33 Plain passed 13 navigation/icon cases in 159.542s and
API36 GMS passed 13 in 196.204s. A new native assertion first reproduced
six redundant Complete labels, then checks that only status icons carry
those completion states. After compaction, API33 Plain passes four targeted
icon, permission-navigation, and actual TLS-release cases in 21.506s.
API36 GMS passes the same four cases in 18.895s.

A real fresh API36 app-state reset was used to inspect onboarding. Exact
alarm and notification prerequisites were granted as controlled setup;
full-screen access was denied. The real MainActivity button opened
Android's Curfew-specific full-screen notifications settings. Enabling its
system switch and returning advanced the app to the sound-test step. Native
screenshots live in `design/android-review/native/onboarding/`.

The updated notification fallback driver grants full-screen access for
setup, then revokes it before delivery. Its old ordering first failed the
new readiness gate. The corrected 90-second API36 driver passes actual
notification delivery, notification tap, fixture PIN entry, unchanged ringing
state, and cleanup. It restores the original access mode and clears its
owned-emulator credential. This driver never targets the owner's phone.

CI run `39501292679` on the earlier `46e5ace` icon commit passed six jobs
and failed the API34 GMS release journey's readiness setup. The log did not
identify the missing capability. The release fixture now explicitly grants
full-screen access, waits for actual readiness, and reports missing actions
if readiness stays false. This is fixture setup and diagnostic work, not a
claim that the earlier CI failure's exact cause was established. The updated
commit still requires its own CI result.

## First-use flow and native navigation — October 20, 2026

The onboarding follow-up replaces the first-use empty-screen/checklist sequence
with Welcome, a separate persisted schedule draft, connection verification,
focused Android access and rehearsal tasks, and explicit activation review.
The design and remaining consumer-service gap are in ONBOARDING_DESIGN.md.

Failing-first records include absent draft/persistence/review contracts, the old
launcher theme, missing focused permission headings, cached connection callbacks,
a competing sound action during permission setup, and continuation after Back
to a verified connection. The installed Activity suite also stalled behind a
splash condition driven through Compose before that condition was removed.
No stalled or interrupted run counts as passing evidence.

Both Plain and GMS pass 195 host cases with zero failures/errors, app/test APK
assembly, and Android lint. The dependency license check and plain runtime
isolation pass. Gradle uses one worker and a 1GiB heap. The post-format command
repeats both variants' test/build/lint checks. Local logs are
`build/onboarding-final-verification.txt`,
`build/onboarding-post-format-verify.txt`, and
`build/onboarding-dependency-checks.txt`.

The installed API33 Plain and API36 GMS suites each pass 25 cases. They cover
navigation, fresh choice-to-review without premature saving, current capability
and connection callback values, denied access, verified-connection continuation,
read-error retry, native launch theme, and the actual Activity sound rehearsal.
They completed in 79.581s and 108.578s. The actual authenticated TLS release
journey additionally passes on API36 in 9.225s. These are emulator observations.

Seven native onboarding/navigation cases pass on each of three additional
profiles: 320dp at 200% text, the same profile in dark mode, and a 1080dp expanded
window at 100% text. All 21 profile cases pass. The driver verifies restoration
of original density, text size, and night mode. The retained result is
`build/onboarding-layout/result.json`.

An external driver resets only the owned API36 emulator, launches the real
MainActivity, chooses a weekly draft, and advances to service setup. After an
actual process stop and cold launch, the app resumes service setup. Back returns
to a daily screen with no enabled alarm. The driver then resets that disposable
fixture and leaves it at Welcome. The result is
`build/onboarding-review/first-use-resume-result.json`. The fresh Activity cold
launch reached Welcome in 1.585s on this emulator. No permission prompt or alarm
started on that first screen.

Native captures are in `design/android-review/native/first-use/`. The fresh
MainActivity image is an actual reset installation. The focused component
captures use native Compose fixtures with synthetic labels. Rendered review
covers the warm palette, clear primary action, enlarged text wrapping, dark
contrast, and official wide navigation rail.

The Pixel disconnected before installing this follow-up. Its earlier heard
alarm and lifecycle probes retain their earlier APK boundaries. Manual TalkBack,
physical full-screen presentation, and physical deep idle remain open. The
first-use preference file is separate from the runtime schedule and introduces
no database or protocol migration. Rolling back this UI follow-up makes its
unfinished draft unavailable to the old UI but must not erase saved alarms.

A final deferral regression failed before correction: an existing draft forced
Review even after the introduction choice was deferred. The shell now resumes
a draft automatically only when setup was started, and keeps the draft available
for deliberate continuation after Not now. Both variants again pass the full
195-case host/build/lint checks. Three final installed cases per variant cover
deferral, fresh explicit activation, and ordinary saved-alarm startup. They pass
in 10.395s on API33 Plain and 14.322s on API36 GMS. Their logs are
`build/onboarding-final-deferral-plain.txt` and `-gms.txt`. The final build log is
`build/onboarding-deferral-final.txt`.

The `03db94e` full-screen setup observation also exercises Android Settings
itself. On the owned API36 emulator the system toggle starts denied, Curfew
opens its package-specific page, the driver grants it through the visible
system control, and Back advances Curfew to Check the sound. No sound plays.
Original UID/package app-op modes are restored. The source/installed candidate
and digest are recorded in P1_ACCEPTANCE.md. This does not replace physical
first-use acceptance.

CI run 39516628313 subsequently passed all seven jobs on the `03db94e` runtime
source. Each API33/34/35/36 job reports 141 successful instrumentation cases,
including final deferral and verified-connection Back behavior. Both verification
jobs and published release evidence pass. All three published artifact hashes
match, and the PR merge source tree matches the branch head. APKs are unsigned;
this is build evidence rather than a production-signing claim.

## Welcome composition, adaptive layout, and device appearance

The welcome presents bedtime and waking up together. One introduction and one
commitment/action group stack on narrow windows and use Material 3 Adaptive
panes on wide windows. Official posture information excludes vertical hinges.
A separating horizontal hinge places useful content above and below it. Native
night-qualified resources follow the device theme alongside Compose.

The logo grows continuously with readable pane width and available height.
Compact portraits and short windows retain their compact treatment. Portrait
layout measures the scene and commitment first, then centers the introduction
in the remaining height. Enlarged text can expand into the existing scroller.

Failing-first logs include `build/welcome-red-native.txt`,
`build/welcome-centered-red-native.txt`, `build/welcome-motion-red-build.txt`,
`build/welcome-landscape-red.txt`, and `build/welcome-adaptive-red-build.txt`.
The source-icon correction failed two assertions in
`build/icon-brand-red-native.txt`. The native night theme failed first in
`build/device-theme-red-native.txt`. Larger-window brand emphasis failed first
in `build/welcome-brand-red-native.txt` and passed before the subsequent
posture-test process exit.

The portrait guard first fails against an earlier accepted native phone XML
in `build/welcome-portrait-baseline-red.txt`. Its wordmark starts at 200px in a
2400px-tall capture. The guard rejects that top-pinned placement on tall,
normal-text portrait phones. This is a saved native baseline, not a new live
capture or final acceptance.

The current portrait candidate builds both app/test distributions and passes
195 host tests per distribution. Logs are `build/welcome-portrait-build.txt`
and `build/welcome-portrait-host-checks.txt`. Both lint tasks passed before the
last portrait adjustment in `build/welcome-brand-final-build.txt`. License and
plain-runtime isolation pass in `build/welcome-adaptive-license-check.txt`.
CI run 39529921160 passes all seven jobs for runtime
`f867f51d974a4b60358f658246901decc7b5121f`, including both lint tasks and
API33/34/35/36 instrumentation. The API35 runner reports 150 cases: 143 pass
and seven guarded opt-in acceptance cases skip. The captured merge commit
`8e49fcbb52c074eac4daea0e70528233a3477bfe` has the same source tree as the
runtime head (`d071807875086eec3ad3748aaca6cd35df16cbc2`).

Before the final brand/portrait adjustments, API36 Plain passed 32 native
cases in `build/welcome-adaptive-native.txt` and API36 GMS passed 11 focused
cases in `build/welcome-adaptive-gms-native.txt`. Those results retain their
source boundary. They do not verify the final layout.

The API35 CI job now exports native motion frames, four synthetic book/tabletop
fixtures, eight launcher-mask previews, and 18 installed MainActivity windows.
Phone, tablet, fold cover, and fold inner windows cover both orientations and
themes. Narrow 200% text covers both themes. The driver checks fresh loaded
accessibility roots, pane separation, portrait placement, and action
reachability. It verifies restoration of size, density, font scale, and night
mode. Its manifest records the installed APK hash, source commit, and API.
The uploaded artifact also includes the matching app/test APKs.

`scripts/capture-welcome-matrix.py` refuses physical phones and unknown AVDs.
The CI runner names its disposable API35 fixture `Sunrise_Design_API35`.
Synthetic Posture/HingeInfo fixtures establish geometry only; they do not
verify hardware callbacks. The motion test controls the Compose clock with a
known duration scale, checks interaction during entrance, and tests disabled
motion. The GIF preview may loop; the app entrance runs once per composition.

Failed local runs contribute no accepted current captures. API36 reported an
onStartJob ANR with 95.43% CPU pressure, 99% total CPU, and 52% IRQ time.
Android could not collect Java or native stacks. Cold software and host-GPU
boots then showed System UI ANRs. Logs are `build/welcome-brand-exit-info.txt`,
`build/welcome-brand-anr-system.txt`, `build/welcome-portrait-red.txt`, and
`build/welcome-portrait-placement-red.txt`. The Mac then reported 15.4GB swap
and about 6.9GB compressed memory. Local emulators and the idle Gradle daemon
were stopped. The remaining verification and capture ran in CI.

The reviewed current matrix is in
`design/android-review/native/welcome-matrix/`. All 18 installed profiles show
both actions in the first viewport, including both 200% text profiles. Every PNG
decodes, settings restoration succeeds, and the downloaded APK matches manifest
SHA256 `382a4ea668c236c20e25afeb84ba6137aa0f981cb5c7ac1478d01f8dd9d1c6ea`.
The final portrait wordmark starts at 286px instead of the baseline's 200px.
An independent designer reviewed all 18 views, four synthetic posture fixtures,
and eight icon masks without finding a concrete visual defect. Manual TalkBack,
physical reduced motion, hardware posture changes, and remaining physical alarm
acceptance stay open.

The first CI capture attempt completed API34 instrumentation (143 passed and
seven guarded cases skipped), then failed
because emulator-runner executes each input line as a separate shell command.
`ci/run-instrumented-and-capture.sh` now keeps the conditional capture step in
one shell invocation. Three fixture tests fail before the wrapper exists and
then verify API35 capture, other-API exclusion, and failure short-circuiting.
The failing-first log is `build/welcome-ci-wrapper-red.txt`. No app code changed
for this CI correction.

## Failed welcome capture diagnostics

The evidence-only head 185018d passed both verification jobs, release evidence,
and API33/34/36 instrumentation in CI run 39531929779. API35 completed its native
suite (143 passing cases and seven guarded opt-in skips) before capture failed
at inner-landscape-dark. Android returned no matching welcome accessibility
root within the five fresh-dump attempts. Fifteen preceding profiles passed.
The uploaded partial manifest is not a replacement for the reviewed matrix.

The capture driver now retains the last returned XML, a failure screenshot,
window state, and app exit information when that gate fails. It still rejects
the capture and removes the previous dump on every attempt. It does not accept
an unrelated screen or substitute the last successful image. Two fixture tests
first failed because the diagnostic helper did not exist, then passed after
extraction. The red log is `build/welcome-root-diagnostics-red.txt`.
`ci/test-welcome-root-capture.py`, the three capture-sequencing cases, the four
existing driver cases, actionlint, and diff whitespace checks pass. No app source
changed. Further CI capture evidence must establish the new head's result.

## Consumer navigation and test identity

`settingsIsATopBarActionAndBackReturnsToTheAlarm` requires Settings above the
alarm content, removes tab semantics, and checks return without saving.
The API35 capture job runs the task journey in all 18 welcome-matrix profiles.
It saves daily, editor, Settings, account, check-in, and readiness PNGs for each profile, including
both themes and 200% text. These are rendered native UI fixtures on emulated
windows. They do not establish physical fold posture or speaker behavior.
The capture driver rejects failed instrumentation before reading any image.

`AlarmSaveErrorTest` permits the known future-date correction and replaces
unrecognized validation/storage messages with a consumer retry message.
It prevents internal identifiers and storage paths from appearing as errors.

Use `./gradlew assemblePlainDebug assemblePlainDebugAndroidTest
-Pcurfew.acceptance=true --max-workers=1 --no-parallel` for future isolated
physical acceptance builds. The application ID ends in `.acceptance`, and the
launcher label is `Curfew Test`. Normal builds retain `Curfew`. The flag does
not alter release identity. Its callback scheme is isolated from normal Curfew,
and account enrollment stops before opening a browser or registering a client. `ci/check-apk-identity.py` checks actual APK metadata.
Visible acceptance check-ins use `Morning check-in`; technical test identifiers
stay in evidence and logs. Do not relabel or replace an already-running test.

The October 20 long Pixel Doze attempt used its pre-correction APK and was
stopped before delivery. Its cleanup and rejected evidence are recorded in
P1_ACCEPTANCE.md. These UI changes require their own CI and visual review. The alarm engine,
permissions, persistence, protocol, and release authorization do not change.

The audio-focus fixture now requires a started `USAGE_ALARM` player owned by
Curfew's installed UID. Its earlier global playback predicate could accept
SystemUI's alarm-channel notification before Curfew's service player started.
Three evidence-parser cases reject notification, paused, non-alarm, missing
player-section, and UID-prefix matches. The focus test still requires actual
pause, gain, and unchanged occurrence state. It changes no production audio
behavior and does not establish perceived speaker output.

Test-only CI run 39548194457 supplies the navigation red on API35: 150 reported
cases and exactly one failure at the old Alarm tab. Its other API jobs also
failed unrelated keyboard/focus cases. API33 captured a Pixel Launcher ANR,
and API36 did not establish its service-owned playback prerequisite. Those
failures are not silently accepted as passing behavior.

## Short-window layout and fixture cleanup

`TaskLayoutPolishInstrumentedTest` reproduces three reviewed defects on a
native API34 emulator before the corrections. Its 390×235dp content viewport
rejects a partly hidden check-in row. Its 360dp editor rejects six repeat days
followed by Sunday alone. Its 200% text fixture rejects the left-aligned second
line in the permission button. All three fail on the prior implementation and
pass after the height budget, deliberate repeat wrapping, and centered text.
The existing daily-layout suite also passes unchanged.

Keyguard and clock-presentation fixtures now cancel their timer and callback
polling, stop playback, wait for service exit, and remove saved state again
under the scheduler lock. The second pass accounts for a transition delivered
before cancellation. The registered-alarm regression failed first with a
remaining AlarmClockInfo and passes after cleanup. An additional native
regression failed before the helper rejected unrelated pending campaigns.
The helper now refuses app-wide service shutdown around another alarm, and
the keyguard class checks an empty queue before each case. This corrects a
confirmed fixture leak; it does not establish the cause of the earlier CI
window-focus failures and changes no production scheduling or audio code.

The final focused run passes 26 native cases in 47.52s on the owned API34
emulator. The Plain host suite passes 200 tests with no failures, errors, or
skips; Plain lint and app/test APK assembly pass. Exact red/green transcripts
remain in local `build/task-polish-{red,green}.txt`. The fresh responsive matrix is reviewed in
`design/android-review/native/task-matrix/README.md`. It contains 108 native
captures from 18 window/theme/text profiles on an owned API34 emulator. Clean
source, matching installed APK, and exact before/after settings are recorded.
The initial combined run failed restoration and is rejected. A separate
task-only rerun passes every journey and restores every recorded setting.
Root and independent design review find no visible blocker in the captured
states. Keyboard/error/account-recovery states retain their separate boundary.

CI run 39553434419 passes both host/lint/build jobs, release evidence, and the
API33/34/36 native jobs. API35 reports OK for 158 cases, including seven guarded
skips, in 236.952s. Its later welcome screenshot step fails at the landscape
dark profile. The retained PNG and XML show Android's “System UI keeps
stopping” dialog. This is a failed capture run; it is not a failed UI contract
or accepted screenshot evidence. The fresh-emulator retry passes.
The separately reviewed API34 task matrix retains its own source/APK/settings
record. The task-owned remote emulator and cache were removed after capture.

CI 39553434419 now passes all seven jobs after that API35 retry. Current-head
run 39554867813 also passes all seven jobs on its first attempt. Its artifact
contains 18 profiles and 108 task PNGs with clean source, matching installed
APK hash, and restored emulator settings. The earlier failed capture remains
rejected; a green retry does not change what its original result established.

The October 20 short Pixel run enters forced light Doze but ends early when
Android records a tap wake. It never reaches alarm delivery and is rejected.
Its scoped cleanup succeeds. Keep the physical Doze criterion open and do not
restart the phone test without confirmed availability. The pending repeat
harness has a pre-arming cutoff and a separate absolute phone-local deadline;
it has not been executed and establishes no additional device behavior.

## Setup state and adaptive flow repair — October 21, 2026

Application source `8feb007` repairs setup navigation, persistence, loading,
connection selection, export recreation, and short keyboard windows. Both Plain
and GMS host suites pass 200 tests with no failures, errors, or skips. Both
Android lint checks and debug app builds pass with one Gradle worker.

The focused API34 native suite passes 87 cases. It includes cold-start loading,
welcome-write failure, restored review drafts, late activation reconciliation,
delayed callback selection, export through a restored Android result registry,
Back behavior, optional opening links, repeat controls, editor save/cancel,
large labels, adaptive fold regions, and welcome motion. The export test
intercepts DocumentsUI launch because ActivityScenario cannot recreate its
stopped owner while that external picker is foreground. It delivers a controlled
result through Android's real registry and verifies the exported file. This
establishes result restoration rather than a physical picker interaction.

The new regressions failed before their repairs. Earlier broad runs found
obsolete selectors for a single time Text and an unscoped AM selector behind a
dialog. The tests now target the clickable numeric time and the dialog's own
period control. The wrapped-label fixture uses a narrower viewport because the
new form removed nested padding and its previous label fits on one line.

The native setup matrix uses `scripts/capture-setup-matrix.py` on a guarded,
task-owned API34 ARM64 emulator. It covers eight setup states in 18 profiles:
phone, tablet, fold cover, fold inner, both orientations and themes, and narrow
200% text. It asserts the full ordinary-text Save action before scrolling and
the complete input bounds against navigation and the keyboard. It verifies each
capture's destination and waits for its frame to reach the system compositor.
Four controlled clock frames show the real shared wake-time transition.

Earlier matrix attempts were rejected for font-setting normalization, obscured
input geometry, a stale Review frame, or clamped display dimensions. The final
record requires exact display-setting restoration and checks Android's reported
size and every native PNG against the requested pixel dimensions. Four capture
geometry tests exercise matching profiles and rejection of clamped displays
and screenshots. The owned emulator uses a 1080×1920 base display so Android
can apply every requested profile without its previous 1920px cap. Do not treat
a rejected run as accepted visual evidence.
The [gallery manifest](../design/android-review/native/setup-matrix/manifest.json)
records the installed APK and application source hashes. Captures show emulated
windows rather than physical fold hardware. Large-text views may require
scrolling. The gallery does not establish physical hearing, Doze delivery,
manual TalkBack, production signing, or distribution.

The [verification record](evidence/setup-2026-10-21/verification.json) and retained
red/green native outputs record the boundary. The Pixel was disconnected during
this repair, so its earlier install remains historical evidence for its own
recorded source.

## Complete-suite fixture correction — October 21, 2026

CI run `39565347115` passes Plain/GMS host verification and release assembly,
but fails native instrumentation on API33–36. Each reports 177 cases. API34/36
report four failures; API33/35 also expose a sound-check startup race. The
[retained failure record](evidence/setup-2026-10-21/ci-fixture-failures.json)
identifies the cases without treating the failed run as green.

Two capture fixtures still selected the old combined time Text or an initially
visible Name field. They now select the numeric time action and follow Connect
a service before entering its required address. Their captures retain the
original picker and keyboard checks.

The earlier release journey intentionally proves that completion retains its
schedule. The cold-start tests incorrectly assumed that no saved schedule
existed afterward. They now snapshot, clear, and restore the shared journals
around each case, then assert that the no-alarm precondition holds. Isolation
refuses an outstanding occurrence and runs only on disposable emulators or the
isolated acceptance package. Instrumentation accesses the existing singleton
stores rather than creating a conflicting second DataStore or exposing a
production reset API. Restoration also runs after failed assertions, so test
drafts and selected callback IDs cannot escape the fixture. The sound-check
fixture waits for the loaded merged Settings action after recreating Main.

After seeding a completed alarm with the actual TLS release journey, all 17
focused cases pass on the owned API34 emulator in 51.825s. Both distributions'
test APKs compile. The complete API34 run then reports 177 cases: 170 pass,
seven guarded acceptance cases skip, and none fail. A source review found a
further reset-helper safety gap: a pending runtime campaign can exist without
a saved schedule. Its new regression fails before a pending-queue guard and
passes afterward. All 18 affected cases pass with that guard in 51.317s. The
replacement API33–36 CI run remains pending. Application source stays at
`8feb007`; the reviewed setup matrix is unchanged.

## Check-in usability correction — October 21, 2026

The next product review rejects the assumption that a clean service form is a
usable consumer flow. Curfew supplies no check-in service. The prerequisite
screen now discloses that gap, offers Save for later with the alarm off, and
identifies the person who supplies the connection address and installs the
private setup file. Consumer approval remains a launch blocker.

The new native regressions first fail on missing prerequisite disclosure,
missing operator handoff, and a 318dp gap between compact guidance and its
action. A failure-path review then catches invisible deferral errors and no
pending-write feedback. The retry regression also catches Back skipping the
saved time after resuming setup. Each failure precedes its repair. Choice
errors now stay separate from editor errors, actions disable during the write,
and resuming a draft restores the editor beneath the next setup task.

Both distributions pass 200 host tests, Android lint, and debug app/test builds.
The final focused installed API36 Play Store ARM64 run passes 55 tests in
92.736s. Sixteen host capture-tool checks pass. The exact build hashes and
red/green outputs are in [check-in evidence](evidence/check-in-2026-10-21/).
These results do not establish physical Pixel acceptance or consumer readiness.

CI `39568431033` passes API33 and API34 native jobs. API35 passes its 178
reported native cases, then captures an Android System UI crash dialog over
Curfew. API36 has one keyboard-capture timeout. The previous keyboard fixture
passes three local runs, so that exact CI timeout is not reproduced locally.
The fixture now waits for the old keyboard to close before replacing its
composition, checks actual window IME visibility, and cleans up on failure.
Three focused repaired runs pass. The final 55-case run also passes all nine
design-capture cases. CI uses x86_64 Google APIs images; the local check uses
ARM64 Play Store. Do not erase that distinction.

The cause of the API35 System UI crash remains unknown. Failed captures now
retain System UI exit information and the crash log buffer. This diagnostic
change is not a claim that the System UI crash is fixed.

The rendered follow-up review found a partial error and hidden retry action at
200% text, plus testing emphasized before the private-file handoff. Both new
regressions fail before repair. The error and retry now share a
[BringIntoViewRequester](https://developer.android.com/reference/kotlin/androidx/compose/foundation/relocation/BringIntoViewRequester)
region. Saving the file has priority until Android reports a successful write.
Opening or canceling the picker does not advance that state. The saved file's
nonsecret callback identity survives recreation; the real registry-result test
checks the file contents, action priority, and a second activity recreation.
Export does not imply operator installation or authenticated verification.

The final replacement gallery contains ten states across eighteen emulated
profiles: 180 state PNGs and four controlled transition frames. All 184 match
the requested dimensions, current source hash, and installed GMS APK. Exact
display settings restore. Codex and the independent designer reviewed all
thirty contact sheets between them. The clean replacement follows a rejected
incomplete run with an emulator graphics-context/ADB failure; no partial or
invalid images enter the accepted gallery. See the
[current gallery](../design/android-review/native/check-in-matrix/README.md).

## API36 keyboard and picker fixture investigation — October 21, 2026

CI run `39575965473` reports 178 passes and seven guarded skips on each of
API33, API34, and API35. API36 reports 176 passes, seven guarded skips, and
two failures. Its keyboard capture times out, and its one-night date test
looks for time fields before the dialog exposes them. Neither the release
job nor the other green native jobs makes that run accepted.

The unchanged full suite passes locally on API36 Play Store ARM64. An
input-mode restoration probe also passes, so no input-mode leak is claimed.
Adding focus and active-editor checks then reproduces the keyboard timeout
locally at 320dp and 200% text. Android reports a focused, active editor but
no visible IME or inset. Its ImeTracker records
`PHASE_WM_ABORT_SHOW_IME_POST_LAYOUT`. This establishes that waiting for
editor focus alone does not resolve the repeated capture-window failure.
It does not establish that the earlier CI x86_64 timeout has the same cause.

Each of the eight keyboard profiles now has an independent test activity.
The fixture no longer cycles a live keyboard through synthetic font/width
changes in one window. It keeps real IME visibility and positive-inset
assertions, waits for window and editor readiness, and includes focus, touch
mode, and input-connection details on timeout. The picker helper waits for
the named hour/minute fields and switches modes only when those fields are
absent. No app source, alarm behavior, capture profile, or security gate
changes. The reviewed 18-profile setup gallery retains its original source
and APK hashes. Reverting these test changes does not affect stored data.

The final focused run passes all 33 capture/picker cases in 70.256s. The
complete API36 ARM64 run reports 192 cases in 184.575s: 185 passes, seven
guarded skips, and no failures. Both test APKs build, both Android lint
variants pass, and all 16 host capture checks pass. Independent source review
finds no lost profile coverage or weakened keyboard/picker assertions.
[Retained red/green records](evidence/check-in-2026-10-21/ci-fixture-verification.json)
include the failed readiness experiment and final APK hashes. Replacement
x86_64 CI and physical Pixel Doze retain separate acceptance requirements.

## System splash and real-window keyboard correction — October 21, 2026

Run `39579943603` passes both verification jobs, release evidence, API33, and
API34. API35 passes all 192 reported native cases, then its matrix records
two System UI crashes in `SplashScreenView.onCopied()` on a null
`SurfacePackage`. The retained crash buffer identifies the framework method;
the exact OS race remains unproven. The incomplete capture is rejected.
API36 passes the eight isolated keyboard profiles but fails the real-activity
check when Back moves off screen.

The activity always registered a custom splash exit, including an immediate
remove when animations were disabled. It now calls `installSplashScreen()`
without an exit listener. Android removes the splash when the app can draw.
The branded animated icon and Compose entrance animations remain. Only the
app-owned 180ms exit fade is removed. AndroidX documents this
[default dismissal behavior](https://github.com/androidx/androidx/blob/androidx-main/core/core-splashscreen/src/main/java/androidx/core/splashscreen/SplashScreen.kt).
Independent review confirms that the change avoids requesting the client
exit callback; it does not claim to fix Android's internal implementation.

The real activity also left keyboard adjustment unspecified, while the setup
capture fixture explicitly requested resize. The new installed manifest
regression fails with expected 16 and actual 0 before repair. The manifest
now declares `adjustResize`. The real-activity test waits for a visible IME
and positive inset before checking Back and the complete input field. The
original CI failure and the new deterministic manifest failure precede the
correction. A separate host regression first fails when a valid screen tree
hides a System UI crash; the driver now rejects that capture and keeps its
diagnostics instead of accepting the screen.

Both distributions pass 200 host tests, both Android lint checks, and both
debug app/test builds. Ten installed API36 Play Store ARM64 startup,
recreation, keyboard, entrance-motion, and setup-capture tests pass in
14.069s. All 17 host capture checks pass. See
[startup evidence](evidence/check-in-2026-10-21/startup-verification.json).
The x86_64 CI matrix and physical Pixel Doze still require their own results.
This change adds no persistence or protocol migration. Reverting it restores
the custom splash transfer and unspecified keyboard adjustment.

The [refreshed startup/keyboard gallery](../design/android-review/native/check-in-startup-matrix/README.md)
covers all ten setup states across eighteen profiles with 180 native state
PNGs and four controlled transition frames. Actual Android dimensions and
all PNG dimensions match; exact display settings restore. Its source and APK
hashes differ from the earlier gallery and remain recorded separately.

## Notification ownership and recreated-screen readiness — October 21, 2026

Run `39582873446` passes both verification jobs, release evidence, API33,
and API34. API35 fails one cold-start recreation assertion. API36 first
selects an older same-channel notification, then teardown stops the new
foreground-service start before promotion. Android terminates the process.
The service already promotes before disk work; this record does not establish
a production service defect.

A seeded stale notification reproduces the label mismatch before the selector
correction. The fixture now waits for its occurrence's PendingIntent. Its
stale notification has a separate tag and PendingIntent, and a pending-alarm
guard runs before mutations. Synchronized cleanup waits for service shutdown.
Nested cleanup releases fixture-owned resources even if that guard refuses
cleanup. The audio-focus fixture uses the same synchronized teardown. The
cold-start fixture waits for the merged destination after recreation and keeps
its original navigation assertions.

The intermediate complete API36 Play Store ARM64 suite reports 193 cases in
177.812s: 186 passes, seven guarded skips, and no failures. That run precedes
the final fixture safety revision. All seven affected cases pass on the exact
final source in 4.558s. Both test APKs build and both Android lint variants
pass. Independent source review finds no remaining concrete issue.
[Red, targeted, and complete-suite records](evidence/check-in-2026-10-21/notification-fixture-verification.json)
retain exact source boundaries and test APK hashes. No app code, screenshot,
stored data, or protocol changes. Reverting changes only test isolation.
Replacement x86_64 CI and physical Pixel Doze remain separate requirements.

## Accepted CI candidate — October 21, 2026

CI `39585385215` at `172d951` passes all seven jobs. API33 Plain, API34 GMS,
API35 Plain, and API36 GMS each report 186 passes and seven guarded skips.
Those skips remain opt-in external-driver cases rather than executed default
CI acceptance. Both distribution verification jobs and release evidence pass.
The API35 gallery contains all 18 welcome profiles and 108 task PNGs; all
126 profile dimensions match their manifest, and original settings restore.
The earlier System UI splash-copy capture crash does not recur in this run.

The three release artifact hashes match. Their provenance references the
fetched PR merge whose application tree matches the candidate, and protocols
remain pinned to `v0.0.13`. Both APKs are unsigned. The
[accepted CI record](evidence/check-in-2026-10-21/ci-accepted-39585385215.json)
records exact boundaries. This evidence update changes no app or test code.
Independent completion review finds no additional unimplemented P1 feature
requirement. Uninterrupted physical Pixel Doze remains unchecked, and the final
Codex Docket update remains unpublished because native MCP authentication is
required. No physical test is running.

## Screenshot catalog

See [SCREENSHOT_CATALOG.md](SCREENSHOT_CATALOG.md) for the maintained production-UI matrix,
native onboarding capture, failure handling, and local/CI update commands.

## Bounded network recovery grants

`WakeConvergenceReducerTest` checks that an override advertised as active is
rejected when its start is in the future or its derived end has been reached.
The device uses its current clock as well as the authenticated server response.
Cancelled grants remain rejected. A valid bounded grant releases only its
declared participants. Both polling paths use the same validation before
persisting an override; a failed validation leaves the occurrence pending.

The expiry and future-start cases failed against the previous reducer before
the correction. These host checks do not establish installed multi-device
convergence, revocation propagation, offline paired codes, or service availability.
Explicit physical Pixel Doze testing is excluded by owner direction; this
changes acceptance scope, not Android's standby behavior.

## Printed place check-in

`PlaceCheckInTest` and `PlaceQrTest` cover the local credential and offline QR
codec. `SunriseScheduleControllerTest` verifies atomic future-alarm disablement
before revocation while retaining ringing authority; selection tests protect a
newer method from a late removal. Run these with both Plain and GMS host suites.

The isolated native suite includes `PlaceConsumerEntryInstrumentedTest` for the
consumer destination and `PlaceCheckInRepositoryInstrumentedTest` for encrypted
enrollment, wrong-code rejection, exact occurrence binding and duplicate release.
Repository fixtures refuse live account/pending-alarm state and restore their
own data. `PlaceCheckInCaptureInstrumentedTest` adds five actual Compose scenes
to the existing eighteen-profile task gallery. Review every generated scene
before claiming responsive UI acceptance. Camera capture and physical TalkBack
remain distinct from fixture renders and host tests. See [the feature contract](PLACE_CHECK_IN.md).

`SunriseReleaseJourneyInstrumentedTest.enrolledPlaceCodeReleasesActualPlaybackWithoutAVerifier`
arms the real AlarmManager occurrence with a local enrollment, observes actual
foreground alarm audio and notification state, rejects a wrong code, then applies
the matching code and observes playback termination and durable completion. It
uses an isolated emulator, grants fixture readiness, starts no HTTPS verifier,
and refuses live account or pending-alarm data. This verifies the release path,
not physical camera optics, human wakefulness or audible speaker delivery.

The place capture fixture runs only through its explicit driver argument. The
owned disposable emulator driver revokes camera access before starting that
instrumentation process, rather than revoking permission from inside the app
under test. Android may terminate the app during revocation. Functional suites
can therefore exercise real camera readiness independently, and the capture
still asserts an actual denied permission before rendering its denial scene.

`PlaceCodePrinterInstrumentedTest` exercises actual Android PDF generation. It
requires a one-page readable A4 output and rejects undersized media without
leaving an unfinished page during cleanup. This is native document generation,
not a claim that a physical printer received paper. The print adapter reports
rendering failure through the system callback and retains PNG export as recovery.

`PlaceCameraLifecycleInstrumentedTest` opens the actual CameraX scanner and
observes its package in Android's active camera-client list. Cancelling must
remove that client. It inspects service metadata only, never records frames,
and does not claim that a printed code was physically aimed at the lens.

## Combined main integration

The consumer code flow and bounded recovery grants retain the screenshot catalog and current onboarding/editor behavior from main. Editor save taps wait for native dialog and IME dismissal, then target the current Save alarm action. Welcome backgrounds remain full bleed, with safe insets on the foreground panes only. Catalog tests require consumer enrollment states alongside advanced-service fixtures. Existing API 33–36 instrumentation, Plain/GMS unit/lint/build checks and catalog generation validate the combined candidate before integration. Production activation and installer publication remain held.

The combined API35 native matrix exposed a real landscape regression: all 208 native suite tests passed, but the printed-code scene could not display Scan code beside Save/Print. The shared setup default remains unchanged; the consumer code pane explicitly requests its existing 640 dp landscape threshold. The same full capture assertion requires all four actions to be displayed together and remains unchanged.

## Production service routing

`python3 -B scripts/tests/test_service_origins.py` protects every shipping Kotlin
endpoint from retired first-party hosts and keeps production routing in one
configuration. It failed on the retired endpoints and missing configuration
before the repair. `PkceEnrollmentTest` binds OAuth to the exact production
issuer, current resource and registered redirect, including rejection of retired
authorities, credentials, alternate ports and issuer paths.
`NativeDeviceProofFactoryTest` binds proofs to the current HTTPS sync authority
and rejects retired or lookalike origins. These are routing and authorization
regressions; they do not prove a real account enrollment or device journey.

See [launch boundaries](LAUNCH_READINESS.md) and the
[Android enforcement design](ANDROID_ENFORCEMENT.md) before making release or
phone-protection claims. No blocking service or new sensitive grant is added.

`AccountSessionPreferencesTest` covers the non-destructive issuer upgrade:
legacy flags cannot hide native sign-in, unrelated data and legacy flags remain
stored, current enrollment survives reopen, and an old recovery acknowledgment
cannot enroll a signed-out session. A fresh sign-in clears previous enrollment
until current recovery finishes. Old root/recovery keys and tokens are preserved
under their legacy identifiers but never reused as current-authority credentials.

Recovery failure is also covered: a fresh exchange cannot mark the session
signed in until recovery returns a result, so network failure leaves native
retry reachable and encrypted background work disarmed.

A recreated authenticated session still awaiting recovery routes its existing
Continue setup action into native enrollment again, where recovery can be
reconstructed. Fully enrolled sessions retain account management on the web.
