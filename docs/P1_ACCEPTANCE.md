# Sunrise P1 acceptance record

Reader: The Curfew owner and Android maintainer accepting Docket project `01M5AXW727EJSE5FM2H8WXJRWS`.
Action: Check each requirement against its evidence before accepting any of the four feature tasks.
Current technical P1 decision: accepted with explicit Pixel Doze testing excluded by the owner.

The scope comes from `Documentation/alarm-delivery-plan.md` and
`Documentation/android-mvp.md` in the Curfew repository. P1 means local
Sunrise scheduling, persistent ringing, authenticated release, and recovery.
P1b networking and the required bedtime MVP remain separate.

P1 acceptance uses the connected Pixel. Non-Pixel testing belongs to later
compatibility work and does not gate this milestone. This scope correction
removes a hardware requirement that exceeded the first working-alarm goal.

## Current decision

Technical local Sunrise P1 is accepted with the owner's explicit exclusion of
physical Pixel Doze testing. This is a scope waiver, not a passed Doze test.
The earlier interrupted attempts remain rejected. No further explicit Pixel
Doze run is required or scheduled.

All seven jobs pass in CI `39905434602` at `5d35c93`. Application and test
sources remain identical to `172d951`; subsequent changes record acceptance
and the handoff. Both isolated GMS acceptance APKs also assemble successfully
from this continuation checkout against protocol `0.0.13`. They have not been
installed or run by this continuation.

The recorded physical checks below retain their source boundaries: audible
sound, locked-screen/fallback presentation, offline audio and reconnection,
process recovery, and authenticated release. The owner has removed only the
remaining explicit Pixel Doze test. This does not establish natural overnight
standby, deep Doze, manual TalkBack, a fresh complete physical UX review, or
production signing. The release APKs remain unsigned.

Consumer check-in availability, P1b networking, bedtime enforcement, and launch
validation remain separate incomplete deliverables. P1's external verifier
path is not a supplied consumer service. Production activation and public
installer publication remain held.

## Earlier candidate evidence boundaries

Application runtime `204cfbf` contains the lock-screen notification fix.
Later commits through `07ceb3f` add acceptance tests, drivers, documentation,
and CI time budgeting. Their app runtime source is unchanged. The subsequent
daily accessibility change adds occurrence date/time semantics without
changing alarm scheduling or playback. The following recovery correction
makes occurrence updates atomic and serializes lifecycle timer registration
with schedule edits. Its installed concurrency and process-loss results are
recorded below. Each installed driver records its
APK digest where supported. Earlier lifecycle observations retain their
candidate boundaries in [the implementation record](SUNRISE_P1.md).

CI runs `39436437575` (`21f3c9e`) and `39437430362` (`07ceb3f`) passed every
job: both verification variants, release evidence, and instrumentation on
API33/34/35/36. Run `39440146825` also passed every job at `23e4963`,
including the occurrence accessibility change. The later recovery race fix
passes both local host/lint/build variants and all 119 reported API33 Plain
cases in 167.074s. Its run `39443372382` at `902ffd7` also passed
every job. A subsequent consumer-language and check-in completion pass
passes 121 installed API33 Plain cases in 167.768s and four GMS
check-in workflow cases in 7.416s. Its CI run `39447168226` at `3906ac7`
passed all seven jobs. The responsive follow-up passes 182 host tests per
variant, both app/test APK builds, and both Android lint variants. Its CI
run `39490702571` at `40c9351` passed all seven jobs. The subsequent
identity-race correction passed its matrix. The full-screen setup and icon candidate `f224056` passed all seven CI jobs in run `39504123161`. Local logs live in `build/sunrise-evidence/`, which is
not published as part of the source tree.

An active alarm audio track does not establish perceived speaker output.
An emulator is not a Pixel or non-Pixel physical acceptance run. A passing
test fixture does not establish real hearing, TalkBack speech, or manufacturer
battery behavior. Before-first-unlock playback is explicitly unsupported.

The earlier Pixel 11 Pro/API37 run was connected. The owner confirmed hearing the brief
alarm. Isolated probes also recorded screen-off delivery, process-loss recovery,
offline audio, and a denied-full-screen locked notification. These retain the
pre-icon runtime boundary recorded below. The owner-authorized normal Curfew
app reset succeeded after reconnecting. Display-scale checks use emulators;
the owner's display settings were not changed. Manual TalkBack, physical deep
idle, and allowed-full-screen automatic presentation remain open.

## P1.1: Scheduling

| Requirement | Authoritative evidence | Boundary |
| --- | --- | --- |
| Create, edit, enable, and disable one-night and weekly alarms | `SunriseScheduleControllerTest`, `SunriseSchedulePlannerTest`, installed editor/navigation tests | Automated and installed emulator |
| Preserve a one-night date and keep it distinct from recurrence | `SunriseScheduleTest`, native picker Done/Cancel/Save cases, explicit-date regression | Automated and installed emulator |
| Keep one occurrence through edits and restoration | Controller/planner identity and active-edit tests; persisted recovery driver snapshots | Automated; installed lifecycle candidates are recorded separately |
| Define passed-time and DST gap/overlap behavior | Schedule/controller/resolver tests | Host tests; no physical seasonal-clock experiment |
| Follow device-local time for future alarms | `SunriseScheduleTest`; actual Los Angeles/Anchorage round trip | Host and API35 emulator |
| Preserve enabled state through recovery | Relaunch, permission, package-replacement, and reboot snapshots | Installed emulator; before-first-unlock limitation remains |
| Save offline without coordinator access | Offline cadence fixture checks no active default network before setup, save, and delivery | Installed emulator; loopback verifier provisioning is local |
| Identify incomplete readiness and release configuration | Controller rejection tests, callback verification preferences, readiness tests and native repair XML | Automated and installed emulator |
| Expose the actual occurrence date and local time | One-night date rendering; daily time accessibility state uses the stored occurrence instant | Visible repeating days stay compact; new semantics have failing-first installed proof |

## P1.2: Persistent ringing and readiness

| Requirement | Authoritative evidence | Boundary |
| --- | --- | --- |
| Deliver while locked and outside the app | External secure-lock drivers on API33 and API36 | Installed emulators; no retained instrumentation during delivery |
| Continue 120-second ring / 60-second quiet intervals | Engine tests and the actual API33 offline ring/quiet/ring timeline | Atomic recovery runtime passed in 185.124s with unchanged occurrence and restored network settings |
| Offer real sound rehearsal and require hearing confirmation | Playback/fallback tests and installed sound-confirmation journey | Automated playback; no automatic hearing assertion |
| Check permission, channel, and effective volume readiness | Readiness tests, exact-access revocation, installed DND no-interruptions/alarms/off checks | Installed emulators; physical/OEM policy still open |
| Support denied full-screen access | API36 actual notification tap, Android PIN prompt, and unchanged ringing state | Installed emulator; original access and credential state restored |
| Keep the named check-in action and omit dismiss/snooze | Native ringing-screen tests and controlled HTTPS journey | Automated/native controls; physical lock-screen usability still open |
| Keep quiet and unavailable verification active | Engine tests and actual offline cadence with a closed verifier | Automated and installed emulator |
| Handle audio-focus loss and regain | Installed `AlarmAudioFocusInstrumentedTest` | Emulator audio-track behavior; physical routing remains open |
| Preserve accessibility and adaptive controls | Native semantics, keyboard/hold tests, actual picker profiles, 320/390dp captures and 200% text | Manual TalkBack and physical lock-screen review remain open |

## P1.3: Authenticated release

| Requirement | Authoritative evidence | Boundary |
| --- | --- | --- |
| Provision and test a controlled verifier | Actual TLS setup and provisioning tests; [operator instructions](VERIFIER_SETUP.md) | Local controlled peer; no hosted keyholder portal claimed |
| Stop audio and record a valid current approval | Installed TLS journey for Plain and GMS; persisted satisfied outcome and consumed occurrence | Real TLS exchange inside instrumentation; no production trust bypass |
| Reject pending, forged, malformed, wrong-occurrence, expired, future, replayed, and unavailable replies | Strengthened TLS journey checks ringing, notification, and audio after every case; authenticator golden-vector tests | Invalid approvals claim satisfaction; unavailable uses HTTP503 |
| Keep retried completion harmless | TLS worker retry plus controller duplicate-consumption tests | Automated and installed emulator |
| Display the condition and open its supported action | Native condition/action tests; saved verifier definition | Real external provider action availability depends on the chosen operator |
| Record deliberate recovery separately | Real Android credential success/cancellation, reason and hold tests, Room audit assertions | Latest consumer-pass GMS APK passes both actual API36 credential cases in 14.856s; original credential-free/unlocked state restored; no physical acceptance claimed |

## P1.4: Recovery and complete device acceptance

| Requirement | Authoritative evidence | Boundary |
| --- | --- | --- |
| Restore after actual process death | External SIGKILL drivers; atomic recovery runtime also passes API33 in 1.643s | Guarded disposable AVDs; no app root permission |
| Restore after force-stop and reopening | API33 relaunch driver observes actual audio and original bounds | Force-stop prevents unattended alarms until reopening, as disclosed |
| Preserve an upcoming occurrence across package replacement | Actual PackageManager replacement and audio at the original boundary | API33 emulator |
| Recover after reboot and distinguish first unlock | Secure reboot driver and setup disclosure | No audio before first unlock; recovery observed after unlock |
| Preserve active cadence through clock changes | Actual five-minute forward/backward adjustments through the original two-minute boundary | Earlier runtime; clock engine source remains unchanged |
| Reconcile timezone changes | Actual future timezone round trip | API35 emulator |
| Retain state through permission revocation and restoration | Native readiness XML plus same-occurrence audio after repair | API33 emulator |
| Preserve concurrent disable and time edits during recovery | Installed Room concurrency regressions fail with the old split read/save and pass with one transaction; timer restoration holds the schedule mutex | API33 installed proof; independent read-only review confirmed the lock order |
| Avoid duplicate ringing and unrelated release | Service-isolation tests, completion interruption tests, controller and Room guards | Automated and installed emulator |
| Deliver after standby on the corrected notification runtime | Completed 65-minute API36 driver: scheduled `2026-10-20T15:13:31.268582Z`, audio observed at `15:13:35.992585Z`, same first-attempt bounds | Notification-runtime candidate before the later atomic-recovery and consumer changes; five-second polling, successful cleanup, no physical hearing claim |
| Complete the required connected-Pixel checks | Physical records below establish heard sound, secure locked presentation, full-screen Settings return, process recovery, offline audio/reconnection, and authenticated release at their recorded candidate boundaries | Explicit Pixel Doze testing waived by the owner; no Doze pass claimed. Deep-idle delivery and manual TalkBack are not established |
| Publish task updates with Codex attribution through Docket | Connector update `01M5DFQN5WBS8JWWZ8D4J5V3HC`, verified by `get_updates` | Origin reports `performerKind=agent`, `performerName=Codex`, and no human author; task acceptance remains open |

## Remaining acceptance work

Explicit physical Pixel Doze testing is excluded by the owner. Historical
interrupted observations remain rejected and cannot be cited as a pass.
Manual TalkBack, current-build physical UX, natural overnight standby, and
production signing/store release remain broader launch gates. Consumer
check-in, P1b networking, and bedtime enforcement remain required product
work. The technical P1 decision does not complete the Android MVP.

## Short Pixel release journey — October 20, 2026

The owner authorized a brief audible run while in Starbucks. The prepared
isolated GMS application and test APKs used the `.acceptance` application ID.
`SunriseReleaseJourneyInstrumentedTest` passed on the Pixel 11 Pro/API37 in
6.936s. The driver completed in 7.973s. A separate 12-second force-stop
cutoff was armed and did not trigger. The test scheduled an occurrence,
observed alarm audio and its notification, rejected invalid approvals,
and verified that a valid current TLS approval stopped playback.

The driver force-stopped and uninstalled both isolated packages afterward.
The ordinary Curfew package and its saved settings were preserved. The log
is `build/sunrise-evidence/physical/starbucks-short-test/instrumentation.txt`.
This establishes an instrumented physical-device release journey. The
owner's hearing confirmation is pending. It does not establish delivery
while locked, Doze, lifecycle recovery, or manual accessibility acceptance.

CI run `39495696389` passed all seven jobs on documentation commit
`25a1742`. The installed application runtime remains `514370d`.

The owner subsequently confirmed hearing the alarm. That supplies perceived
sound evidence for the brief run, without asserting sufficient wake volume.
A separate isolated probe exited instrumentation before scheduled delivery.
The Pixel reported `Dozing`; UID-scoped alarm audio then appeared in 11.072s.
The driver immediately force-stopped the isolated package and removed both
test APKs. The 14-second cutoff did not trigger. This establishes delivery
from screen-off ambient display, not deep-idle/Doze or locked UI usability.
The first probe aborted before delivery after an early screen-state sample;
its cleanup passed. Logs are in `build/sunrise-evidence/physical/pixel-screen-off/`.

## Physical recovery and readiness follow-up

The isolated Pixel process-loss probe restored alarm audio in 2.031s after
SIGKILL in a replacement process. It preserved occurrence, attempt, and ring
boundaries. The isolated offline probe observed alarm audio with no active
default network, then stopped playback and verified original network settings
and an active connection returned. A secure-keyguard probe with full-screen
access denied retained audio and the notification content action without an
automatic foreground launch. Each driver removed both isolated packages.
These probes used the prepared pre-icon GMS runtime. Logs live under
`build/sunrise-evidence/physical/pixel-process-loss`, `pixel-offline`, and
`pixel-presentation-deny`.

The short deep-idle probe was inconclusive: Android refused deep idle with
an alarm due within its one-hour minimum. Battery updates and ACTIVE idle
state were restored. An allowed-fullscreen presentation probe aborted before
delivery when secure-keyguard preconditions were absent. Neither is counted
as passing deep-idle or automatic presentation evidence.

Docket scheduling and authenticated-release tasks are accepted. Playback and
readiness was reopened for the owner's full-screen onboarding requirement.
The complete recovery task remains open. Acceptance criteria now use one
Markdown checkbox per independently recorded criterion. The owner requested
an app-state reset. It succeeded after the Pixel reconnected.


## Current full-screen setup observation

The installed `03db94e` GMS candidate passed the real Android Settings return
path on the owned API36 emulator. The driver denied full-screen access, opened
Curfew's Alarm setup task, launched the package-specific system page through
Allow alarm screen, changed its actual toggle, and returned. Curfew advanced
to Check the sound after re-reading the real grant. The driver played no audio
and restored the original package and UID app-op modes. Its result and native
XML/PNG captures are under
`build/sunrise-evidence/full-screen-setup-03db94e/`.

Installed APK SHA-256:
`319b2fd7bea78c777043e87864376683025a6d277cb5c740a19634d9b9aa5dfa`.
This is installed emulator evidence, not physical onboarding acceptance.


## CI and published artifact verification

[Run 39516628313](https://github.com/TheHypertextStudio/curfew-android/actions/runs/39516628313)
passed all seven jobs for runtime source `03db94e`. Each of the four API33,
API34, API35, and API36 instrumentation jobs reports 141 passing cases. Their
logs include the final deferral and verified-connection continuation cases.
Both verification jobs include minified release builds.

The three published APK/AAB checksums match their files. Provenance records PR
merge commit `e3c0e4754133c4954d0c41a3395ad2c3f75c548d`. Its parent includes
`03db94eac035f41ac5695d42b52df13bd8c2a9e7`, and their complete source-tree IDs
match. The APKs remain unsigned verification builds. These artifacts do not
establish production signing or store readiness. Local verification is in
`build/ci-release-03db94e/verification.json`.

USB inventory, adb, and wireless discovery show no connected Pixel. The owner
has a pending reconnection request. This remains a physical acceptance blocker;
the passing CI matrix does not replace the phone's remaining checks.


## Welcome design follow-up

The welcome introduces both bedtime Curfew and morning Sunrise. Each connected
Android device is an enforcement endpoint. This follow-up changes presentation;
it does not implement bedtime enforcement or coordinated network actuation.

One shared introduction and commitment/action group adapt through Material 3
Adaptive panes. Book and tabletop hinge arrangements keep each region useful.
The scene stays continuous, the entrance runs once, and the native window now
follows the device theme alongside Compose. The launcher preserves the Mac
icon’s full sun and dusk palette within Android adaptive layers.

The final runtime `f867f51` passes all seven jobs in CI run 39529921160.
Both distributions build and pass 195 host tests each, both lint tasks pass,
and API33/34/35/36 instrumentation succeeds. API35 reports 143 passing cases
and seven guarded opt-in skips. See TESTING.md for failing-first evidence.

The reviewed matrix in `design/android-review/native/welcome-matrix/` contains
18 installed window/theme profiles, four synthetic posture fixtures, eight
launcher-mask previews, and native entrance frames. Both actions fit in the
first viewport in all 18 profiles, including 200% text. PNG decoding, source-tree
identity, installed APK hash, and device-settings restoration checks pass.
Independent visual review found no clipping, collision, or unresolved placement.

Emulator proof does not replace physical alarm, TalkBack, reduced-motion, or
hardware hinge acceptance. The connected-Pixel recovery criterion above remains
open. This evidence update changes no runtime source or alarm behavior.

## Current Docket and device audit

On October 20, Codex read the live project and all four task descriptions.
Scheduling, playback/readiness, and authenticated release are done. Recovery
remains in progress with its connected-Pixel acceptance criterion unchecked.
The source plans still require that physical journey; CI does not waive it.

The Docket connector accepted project update `01M5E3S2VK981QWDWNFHAJ8QAM`
and recovery-task comment `01M5E3SEEQBPXB49GM73BYXFPQ`. Read-back confirms
`performerKind=agent`, `performerName=Codex`, and null human author fields for
both records. The update includes runtime f867f51, CI run 39529921160, and the
reviewed matrix published by evidence commit 7926fa9.

Local adb and wireless discovery list no phone. The reachable WillieStudio
host also lists only emulators and no wireless devices. Physical USB inventory
on this host shows no Pixel. Current-candidate onboarding, allowed-full-screen
locked presentation, deep idle, and manual accessibility therefore remain open.
No alarm was sounded, phone state changed, or local emulator started during
this audit. Application source remains unchanged from the verified runtime.

## Reconnected current-source Pixel acceptance

The Pixel reconnected through wireless debugging on October 20. The isolated
current-source GMS build passes the physical schedule/audio/TLS release journey
in 8.44s. Invalid approvals retain the occurrence and valid approval stops it.
The allowed-full-screen probe observes automatic foreground presentation from a
secure keyguard, active alarm audio, and the notification content action.
A process-loss run after normal first launch restores the same ring in 1.83s.
The actual Android Settings full-screen grant/return path also advances to the
sound check without playing audio.

The accepted offline probe observes no default network before delivery and
while alarm audio is active. Its phone-local driver stops playback, restores
network values, and wireless adb reconnects. Bluetooth remains enabled. The
first network probe still had a default network and is rejected. A first
keyguard attempt lacked secure-lock preconditions. A first process-loss attempt
timed out before audio. Those attempts do not count as passing evidence.

The normal package now matches the current normal GMS APK; its settings were
not cleared. Completed isolated probes removed both isolated packages.
Sanitized observations and APK hashes are in `docs/evidence/pixel-2026-10-20/`.
The test APK changes only the application ID suffix; app source is unchanged
from runtime f867f51. The owner has not supplied a new hearing confirmation.

The longer Doze driver used run 989d625e3d5d4b5d9a6f61fb6eab6f50.
It scheduled 2026-10-21T05:58:16.194891Z with an independent phone-local
cutoff ten seconds later. Codex stopped it before delivery, as recorded below.
Deep idle was established at 04:50:09Z. A later check
confirms IDLE with screen off, no charging, and the original one-hour minimum
alarm distance. This does not accept delivery yet. An earlier short attempt
could not enter idle; Android rejected the DeviceConfig override, and the
original setting remains unchanged. Natural overnight standby and manual
TalkBack are not established by this forced-Doze run.

Native Docket sign-in completed. The existing tool connection still rejected
calls, so a fresh MCP connection used the same authorized Docket credential
without browser control. Project update `01M5EDED40EGB3146H3C7XH45Z` records the
physical results and the then-live Doze check. Read-back confirms agent performer Codex
and null human author fields. No credential material is included in this record.

CI run 39545633058 passes all seven jobs at 1a68060, including both verification
jobs and all four native API jobs. Its API35 artifact contains 18 complete
profiles with both first-viewport actions, restored device settings, and a
matching installed APK hash. The application source is unchanged from f867f51.
The preceding capture failure remains recorded in TESTING.md.


## Interrupted long Pixel sleep check

The 70-minute alarm was scheduled for 05:58:16Z on October 21. The phone
entered forced deep idle at 04:50:09Z, with a separate screen-off confirmation.
The log retained IDLE observations through 05:06:08Z, then recorded ACTIVE at 05:06:41Z.
Later snapshots showed an awake display. At 05:34Z a screen-off restoration
and stock deep-idle retry did not establish another deep-idle interval.
No alarm time, clock, idle threshold, or application state was changed.

Codex stopped the controlled driver at 05:40Z before the scheduled sound.
The run is rejected as Doze-delivery acceptance. It establishes only the
observed deep-idle interval with a saved upcoming alarm. The driver cleanup
removes its isolated packages and restores ordinary idle policy. Cleanup was independently checked: both isolated packages, their alarms, and
the driver/cutoff processes are gone. Normal Curfew remains installed, ordinary
idle is restored, and the idle threshold remains unchanged. The outstanding
Pixel acceptance checkbox stays open.

The requirement says Doze rather than a specific sleep duration. A separately
observed, accurately labeled light-Doze delivery can satisfy that scope.
It must show screen-off idle followed by unattended, UID-scoped alarm audio.
Deep-idle delivery and natural overnight standby would remain unverified.
[Android distinguishes the two Doze modes](https://source.android.com/docs/core/power/platform_mgmt).


The consumer navigation runtime at 79705cc and its test-only follow-up 530a4cf
pass all seven jobs in CI 39549812707. API35 passed on a fresh-emulator retry
after window-focus failures. Its native suite reports 153 cases, including
seven guarded assumption skips; those skips are not executed acceptance.
The screenshot review found three short-window/text defects that are corrected
with failing-first native proof in TESTING.md. The revised 108-capture task matrix passes all 18 native profile journeys
and the full independent visual review. Its API34 emulator boundary and
restored settings are recorded beside the gallery. The current normal GMS app installation is recorded in
consumer-normal-install.json; it preserved settings and did not take focus.


Project update 01M5EHVM4K326XG9A8PMSYR9D5 records the app-bar navigation,
reviewed native task matrix, current CI boundary, Pixel installation, and
remaining Doze criterion. Read-back confirms Codex agent origin and null human
author fields. The normal Pixel installation now matches runtime 961ae1f and
preserved settings without taking the foreground. No test alarm was started.


## Interrupted short Pixel Doze check

The current-source isolated GMS APK entered forced light Doze at
2026-10-21T06:28:08Z with screen off and no charging. The log retained IDLE
through 06:29:08Z. Android recorded `WAKE_REASON_TAP` at 06:29:11Z, before
the chosen 06:29:21.265860Z alarm. The driver canceled before delivery and
observed no alarm audio. The power event does not identify who or what tapped.
This attempt does not close the recovery criterion.

The cleanup record confirms both isolated packages are gone, ordinary idle
is restored, and the minimum-alarm-distance setting is unchanged. Normal
Curfew remains installed and its settings were not cleared. The sanitized
record is `docs/evidence/pixel-2026-10-20/light-doze-interruption.json`.
Current-head CI and capture verification are recorded in
`docs/evidence/native-2026-10-20/ci-current.json`. No application code changes
in this evidence update.


Docket update 01M5EK34C797WD1RZBKETBP9KJ records both green CI runs, the
rejected short Doze check, successful cleanup, and the disconnected Pixel.
Read-back confirms Codex agent origin with null human author fields. The four
task descriptions retain their templates and checkbox criteria. Only obsolete
planning-only footer text changed; their three-done/one-in-progress states did
not change. The CI artifact merge source matches the branch's application
source, verified after fetching the PR merge ref.

## Setup repair acceptance — October 21, 2026

The subsequent usability review establishes that this integration does not
provide a consumer approval method. An ordinary user cannot finish it without
an existing operator-run compatible service. Consumer launch remains blocked
even if the technical P1 gates pass. The correction states that requirement,
retains a draft without turning on an alarm, and makes the private-file handoff
explicit. It does not add trusted-person approval or a hosted service.

The application source at `8feb007` passes 400 host tests across Plain and GMS,
both lint and debug-build checks, and 87 focused native API34 tests. The setup
repair separates route restoration from transient operations, waits for durable
connection selection, keeps existing alarm binding truthful, restores export
identity, and gives Back a reversible path. The short keyboard arrangement
reuses the task header beside the field and consumes frame insets once.

The native setup gallery contains eight states across 18 emulated window
profiles. The capture driver verifies Android's actual display dimensions and
every native PNG before accepting a profile. Its manifest records the APK and
source hashes. Earlier captures with clamped dimensions remain rejected. Native
test outputs and build identifiers are in [setup evidence](evidence/setup-2026-10-21/).
The complete physical Doze acceptance criterion remains unchecked. No current
Pixel install, production release, hosted check-in service, P1b networking, or
bedtime implementation is implied by the setup repair.


## Complete-suite CI boundary — October 21, 2026

CI run `39565347115` passes both host verification jobs and release evidence.
Its API33–36 native jobs fail on obsolete capture selectors, inherited completed
schedule state in cold-start fixtures, and a sound-check startup timing race.
The release artifact checksums match their files. The fetched CI merge
`a3eb7b8` has the same application-source tree as `8feb007`, and the protocol
revision matches the published `v0.0.13` tag. The artifacts remain unsigned.

The fixture corrections pass all 17 focused native cases after the controlled
TLS release journey seeds the same completed schedule. The complete API34 run
reports 177 cases with 170 passes, seven guarded skips, and no failures. A
separate pending-campaign reset-safety regression fails before its guard and
passes afterward; all 18 affected cases pass with the final guard. Replacement
API33–36 CI still requires acceptance. This changes test isolation and
selectors, not the application or alarm contract. The disconnected Pixel's
physical Doze criterion remains open.


## Notification fixture acceptance — October 21, 2026

Run `39582873446` passes five jobs but remains failed. API35 exposes a
recreation-readiness assertion; API36 exposes notification ownership and
foreground-start teardown races in fixtures. Their correction changes tests
only. The application and reviewed startup matrix remain at `085d3b2`.

The intermediate local complete suite reports 186 passes and seven guarded
skips. The exact final fixture revision passes all seven affected cases. Both
Android lint variants and test APK builds pass. The
[fixture record](evidence/check-in-2026-10-21/notification-fixture-verification.json)
separates those runs. Replacement CI still requires acceptance. Consumer
check-in availability and uninterrupted physical Pixel Doze remain open.


## Handoff continuation verification

- [x] The Android 13 retry of CI `39587495741` is terminal green. Its
  instrumentation console reports `OK (193 tests)`; guarded opt-in skips do not
  establish physical acceptance. All seven jobs pass. The run remains bound to
  `10f6a03`, not a new executable revision.
- [x] The committed handoff at `7b7142f` retains the application and test source
  from `172d951`; the source diff was checked before accepting the retry.
- [x] Native Docket access is restored and a Codex-attributed continuation
  comment was published and read back. Recovery remains in progress, with its
  physical criterion unchecked.
- [ ] Uninterrupted physical Pixel Doze delivery and scoped cleanup remain
  unaccepted. Fresh ADB and mDNS discovery on both available Macs found no
  physical device. Existing emulators do not satisfy this criterion.

The [retry record](evidence/check-in-2026-10-22/ci-retry-39587495741.json)
retains the job boundaries. The handoff ZIP and original planning documents
were transferred over SSH into ignored local build storage; both machines
reported the same SHA-256. The reference Pixel driver remains unexecuted.
No alarm was armed, package installed, device setting changed, or application
source modified by this continuation. PR18 remains a draft. Consumer check-in,
P1b, bedtime, signing, and physical UX remain distinct outstanding work.


## Owner-directed Doze exclusion — October 23, 2026

The owner directed “forget about pixel doze explicit testing” and requested
continuation. Only that explicit test is waived. No idle state, phone setting,
package, or audible alarm was changed by this continuation. The phone-local
driver remains unexecuted in ignored build storage. Its cutoff was not armed.
The source and release constraints above remain in effect.
