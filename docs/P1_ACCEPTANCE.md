# Sunrise P1 acceptance record

Reader: The Curfew owner and Android maintainer accepting Docket project `01M5AXW727EJSE5FM2H8WXJRWS`.
Action: Check each requirement against its evidence before accepting any of the four feature tasks.
Snapshot: October 20, 2026. P1 remains unaccepted.

The scope comes from `Documentation/alarm-delivery-plan.md` and
`Documentation/android-mvp.md` in the Curfew repository. P1 means local
Sunrise scheduling, persistent ringing, authenticated release, and recovery.
P1b networking and the required bedtime MVP remain separate.

P1 acceptance uses the connected Pixel. Non-Pixel testing belongs to later
compatibility work and does not gate this milestone. This scope correction
removes a hardware requirement that exceeded the first working-alarm goal.

## Evidence boundaries

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
identity-race correction requires its own CI result. Local logs live in `build/sunrise-evidence/`, which is
not published as part of the source tree.

An active alarm audio track does not establish perceived speaker output.
An emulator is not a Pixel or non-Pixel physical acceptance run. A passing
test fixture does not establish real hearing, TalkBack speech, or manufacturer
battery behavior. Before-first-unlock playback is explicitly unsupported.

A Pixel 11 Pro is now connected. The responsive GMS candidate was installed
with an in-place update, and its installed APK digest matches the built APK.
This establishes installation only. Physical speaker, locked-delivery,
and TalkBack acceptance remain open. Non-Pixel coverage is later work.
Display scaling checks use emulators; the owner's display settings and app data were not reset.

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
| Complete the required connected-Pixel journey | No current complete physical record exists | Open: locked delivery, perceived sound, Doze, fallback, and offline/reconnection/release |
| Publish task updates with Codex attribution through Docket | Connector update `01M5DFQN5WBS8JWWZ8D4J5V3HC`, verified by `get_updates` | Origin reports `performerKind=agent`, `performerName=Codex`, and no human author; task acceptance remains open |

## Remaining acceptance work

Complete the current candidate's CI matrix, full physical-device matrix,
and manual accessibility review. The connected Pixel 11 Pro/API37 has the
in-place GMS update and ordinary layout evidence. The owner cannot perform a
speaker check now. Pixel acceptance remains open; another manufacturer's
phone is not required. The corrected-notification standby observation retains
its exact earlier candidate boundary above.

Docket is reconnected. Its project update read-back identifies Codex as the
agent performer. The four feature tasks remain in progress and unaccepted.
A fresh isolated install then exposed concurrent encrypted-key and device-ID
creation races. The new native regressions failed before correction. The
reliability follow-up and its installed results are recorded in TESTING.md.

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
an app-state reset; the Pixel disconnected before that operation.
