# Android launch boundary

Android currently delivers the local Sunrise alarm workflow. Bedtime and
protected-work app interruption are not implemented. See the concrete
[phone-enforcement design](ANDROID_ENFORCEMENT.md) for the smallest next slice,
permission boundary, emergency exit and physical-device acceptance.

## Account and sync routing

The canonical production authorities are `https://account.thecurfew.app` and
`https://sync.thecurfew.app`, matching the macOS product's
`deployment/environments.json`. Use the account authority for native OAuth and
account management, the sync authority for encrypted records, wake status,
device proof and sockets. Do not follow retired-authority redirects with bearer
tokens or proofs. Existing local schedules, callback secrets and encrypted data
must survive this routing repair. An account created on a retired authority needs an explicit native sign-in
again. Session flags, bearer tokens and account root/recovery-key readiness
are scoped to the current issuer, so the existing sign-in action remains
available. Legacy secrets and flags stay stored; no alarm data is cleared.
The existing Continue setup action resumes native enrollment after a process
restart if recovery is incomplete. Recovery network failure also leaves native
retry reachable. Background peer-key distribution and encrypted outbox uploads wait for current
enrollment and recovery to finish. Reauthentication must not certify an old
root key as ready or replay a legacy pending Recovery Key acknowledgment.

Production OAuth metadata and sync health can establish routing only. They do
not establish native passkey, Apple or Google account creation, key recovery,
signed proof acceptance, background synchronization or cross-device actuation.
The native account client still launches a browser rather than a native
credential-provider passkey flow; that remains a Studio account-standard gap.

## Distribution

`plain` is the direct APK distribution and must remain free of Google runtime
dependencies. `gms` adds optional FCM. Both production variants retain
`studio.hypertext.curfew` and a single established signing lineage. Never replace
an installed production app with an unrelated debug signing key or uninstall it
to work around a signature mismatch.

Use `-Pcurfew.acceptance=true` for an installable, debug-signed test APK with
package `studio.hypertext.curfew.acceptance`. It is isolated from real alarm
storage, and account sign-in is deliberately disabled. It is not a production
upgrade, a production-signed release or a phone-enforcement build. The existing
Plain verification job retains this isolated APK as
`curfew-android-plain-acceptance` after its checks succeed, so an authenticated
repository user can download the test artifact from that workflow run. An unsigned
release APK can verify R8 packaging but cannot be installed as a signed update.

Before a production launch: provide the existing production keystore inputs,
verify certificate lineage and APK identity/checksum, pass affected tests/lint,
verify actual account enrollment if advertised, and retain the candidate boundaries and remaining broader launch checks in
[P1 acceptance](P1_ACCEPTANCE.md). Technical local Sunrise P1 is accepted there
with the owner’s explicit Pixel Doze exclusion; that waiver is neither a passed
Doze test nor authorization to reinstate that test. Store availability
requires a real Play listing/track and the applicable alarm/full-screen/media
and data-safety declarations; a repository artifact is not a store release.

## Scope of this repair

No sensitive permission, service grant, personal-phone installation, lock,
account enrollment, cross-device command or store publication is performed.
The API34 shared runtime must be coordinated with its resource owner; this
project currently has CI emulator jobs and explicit Sunrise API36 drivers,
without a Gradle managed API34 task. Do not borrow another product's device task
or invent a second runtime to report acceptance.

Current configuration supports Android 13+ (min SDK 33), targets API 36 and
compiles against API 37. The [current Play target requirement](https://support.google.com/googleplay/android-developer/answer/11926878)
requires API 36+ for new apps and updates from August 31, 2026; the configured
target meets that requirement alone. Android 14+ restricts default
[full-screen intent access](https://developer.android.com/about/versions/14/behavior-changes-14#secure-fsi)
to qualifying calling/alarm use cases. Preserve Sunrise’s alarm-only use; it is
not a general app-blocking entitlement. Special alarm and notification access
remain user-controlled, and release review must include denial and revocation.
