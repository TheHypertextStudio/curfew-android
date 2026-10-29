# Android bedtime and protected work time

Status: design for the next opt-in implementation. No app-blocking service or
sensitive permission is added by the origin migration. The installable test
build still provides Sunrise alarms only.

## Supported scope

Current Android implements saved Sunrise schedules, place-code or authenticated
HTTPS check-ins, persistent ring/quiet campaigns, readiness, and credential-gated
local owner recovery. Accounts and encrypted coordination have source clients;
changing their destination does not prove a real enrolled cross-device journey.
There is no bedtime or protected-work schedule actuator, installed-app selection,
blocking service, or blocking-permission onboarding. The macOS daemon and Chrome
extension cannot control arbitrary apps on this phone.

The smallest useful personal-phone implementation is an explicitly enabled,
local app-opening interruption for a small set of chosen entertainment apps.
Use the same local session mechanism for a bedtime interval or a protected work
interval. State the actual limit: Curfew can interrupt supported launches while
its service is enabled. It cannot make Android unbreakable, secure an enterprise
kiosk, block every web page inside an allowed app, or defeat force-stop,
uninstall, reboot, permission revocation, or manufacturer restrictions.

## API choice and limits

| Approach                        | Actual capability                                                                                                                                                                                                          | Decision                                                                                                                                                |
| ------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| AccessibilityService            | Receives configured window events and can request the Home global action. It requires explicit system enablement; success and OEM behavior need device tests.                                                              | Smallest candidate for app-opening interruption, after separate in-app disclosure and consent.                                                          |
| VpnService                      | Routes network traffic after system consent, with per-app routing. An app excluded from the VPN uses the system network. It does not prevent launching an app or offline content. Only one VPN is active per user/profile. | Do not add for this first app-opening feature. It would also conflict with an existing VPN and require a packet-forwarding design and Play declaration. |
| DevicePolicyManager / lock task | Package suspension is restricted to device/profile owners or authorized delegates. Lock task is intended for managed dedicated devices.                                                                                    | Do not provision a personal phone or request device admin for this feature.                                                                             |
| UsageStats or overlays alone    | Observation or a window does not confer general authority to suspend another app.                                                                                                                                          | Do not claim either is an OS lock.                                                                                                                      |

Official sources reviewed 2026-10-29:
[AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService),
[Play Accessibility API policy](https://support.google.com/googleplay/android-developer/answer/10964491),
[Android VPN](https://developer.android.com/develop/connectivity/vpn),
[Play VPN policy](https://support.google.com/googleplay/android-developer/answer/12564964),
[package suspension](<https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setPackagesSuspended(android.content.ComponentName,%20java.lang.String[],%20boolean)>),
and [lock task](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode).
This is an implementation choice derived from those APIs, not a claim of Play
approval. Recheck policy and target-device restrictions before adding the service.

## Smallest complete implementation contract

1. Keep Sunrise data and alarm services independent. Add local session storage
   with an explicit start/end instant, purpose (bedtime or work), chosen app
   identifiers, protected apps, consent revision, and a cancelled/expired state.
   A session never starts merely because a desktop schedule arrived.
2. Offer only a narrowly reviewed set of entertainment apps initially. Do not
   expose a broad installed-app inventory or request QUERY_ALL_PACKAGES. Avoid
   generic-browser blocking: the browser may carry tickets, banking, emergency
   information or authentication. Unknown packages remain available.
3. Keep phone/emergency dialing, messages, contacts, maps/navigation, transport
   and ticket apps, banking/payment apps, authenticators/password managers,
   Settings, system UI, launcher and Curfew available. Never intercept these
   packages, system/credential windows, or in-call/ongoing-navigation contexts.
   Resolve actual device packages/roles and let the owner add protected apps;
   category guesses or a short hardcoded package list are not sufficient proof.
   If classification or active safety context is uncertain, permit access.
4. Present a separate disclosure before opening Android Accessibility settings:
   Curfew receives the foreground app identity to interrupt only the owner's
   chosen apps during active sessions; it does not read screen text, collect
   browsing history, send app activity to a server, or enable itself. Declining
   leaves Sunrise usable. Do not declare isAccessibilityTool=true for a general
   focus app. Play requires its declaration, review video and affirmative consent.
5. Configure only the minimum window-state event information, no window-content
   retrieval, screenshots, gestures, text input or key filtering. Evaluate a
   deterministic local policy on each event. When enabled and eligible, request
   Home and show the current session in Curfew; if the action fails, report
   protection unavailable. No repeated foreground-stealing loop or system UI trap.
6. Provide an always-reachable emergency stop in Curfew and the ongoing session
   notification. It immediately persists cancellation before any further
   interception, without account access, network, PIN, waiting period, secret,
   or another device. Keep Android disable/uninstall controls reachable. A
   failed cancellation write disarms the in-memory actuator and reports the
   persistence failure; never assert success while interception continues.
7. Use elapsed-time bounds for a running session and a conservative wall-clock
   expiry for persistence. Clock rollback, stale restore, missing consent,
   revoked access, app upgrade or unresolved state must not silently prolong or
   re-arm it. Reboot resumes disarmed until a deliberate local confirmation.
8. Keep all decisions local. No activity-history log, package-event upload,
   remote permission grants, hidden monitoring or remote lock command. Any later
   shared schedule/actuation schema must go through curfew-protocols first,
   then coordinator and clients; no parallel Android wire format.

## Acceptance before calling phone protection usable

Use failing-first policy tests for start/end boundaries, clock rollback,
permission loss, stale restore, emergency-stop races, protected-app precedence,
unknown packages and missing safety context. Add an installed service test that
proves actual event delivery and Home-action success on the owned API34 runtime;
no production phone grant is part of unattended automation.

After the owner explicitly consents to installation and each sensitive access,
verify on the actual phone: blocked chosen app, untouched protected apps, active
call/navigation behavior, locked-screen and split-screen cases, reboot and
force-stop disclosure, revoked service, clock/timezone change, offline use,
notification stop, emergency stop during an interception and after process
recreation. Verify the phone's existing VPN and Sunrise alarms still work.

Review both decline and revoke flows and the complete phone/tablet/foldable
window and large-text matrix under AGENTS.md. Host tests and emulator success do
not establish real-phone acceptance. Until these results exist, the launch claim
must remain “Sunrise alarm companion; Android app interruption not released.”
