# Sunrise P1 acceptance record

Reader: The Curfew owner and Android maintainer accepting Docket project `01M5AXW727EJSE5FM2H8WXJRWS`.
Action: Check each requirement against its evidence before accepting any of the four feature tasks.
Snapshot: October 20, 2026. P1 remains unaccepted.

The scope comes from `Documentation/alarm-delivery-plan.md` and
`Documentation/android-mvp.md` in the Curfew repository. P1 means local
Sunrise scheduling, persistent ringing, authenticated release, and recovery.
P1b networking and the required bedtime MVP remain separate.

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
cases in 167.074s. Its current-head CI result remains pending.
Both latest host suites pass 181 tests; both app/test APK builds and Android
lint variants pass. Local logs live in `build/sunrise-evidence/`, which is
not published as part of the source tree.

An active alarm audio track does not establish perceived speaker output.
An emulator is not a Pixel or non-Pixel physical acceptance run. A passing
test fixture does not establish real hearing, TalkBack speech, or manufacturer
battery behavior. Before-first-unlock playback is explicitly unsupported.

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
| Record deliberate recovery separately | Real Android credential success/cancellation, reason and hold tests, Room audit assertions | API36 emulator; no physical credential acceptance claimed |

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
| Deliver after standby on the corrected runtime | Live 65-minute driver, target `2026-10-20T15:13:31.268582Z` | Pending; an earlier-runtime success cannot accept this run |
| Complete the required Pixel and non-Pixel journeys | No current complete physical record exists | Open: locked delivery, perceived sound, Doze, fallback, offline/reconnection/release, and manufacturer behavior |
| Publish task updates with Codex attribution through Docket | No callable Docket connector is exposed | Open; no browser or account impersonation substitute |

## Remaining acceptance work

Finish the current standby observation and latest verification
before updating this snapshot. Then complete the full physical-device matrix
and manual accessibility review. The local and WillieStudio ADB inventories
currently contain only emulators. A discovered phone's wireless endpoint
refused the attempted trusted ADB connection on October 20; discovery alone
does not provide test access. Docket remains unavailable through the connector.
None of these gaps is waived by a green build or an earlier physical TLS test.
