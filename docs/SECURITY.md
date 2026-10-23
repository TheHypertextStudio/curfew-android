# Security model

Curfew treats wake controls and callback credentials as security-sensitive.

## Local protection

- Callback secrets, account tokens, the account root key, and Recovery Key material are encrypted with AES-256-GCM under a non-exportable Android Keystore key. Device signing and agreement private keys remain in Android Keystore.
- Encrypted secret blobs live under `noBackupFilesDir`. Application backup and device-transfer extraction are disabled for all app data.
- Room stores runtime state, encrypted outbox payloads, and non-secret callback configuration. No secret values are logged.
- Exported flags are explicit. Only the launcher/OAuth callback activity is exported; alarm receivers and services are not.
- Pending intents are immutable, and normal alarm notifications contain no stop, dismiss, or snooze action.

The app runs its secret users in one Android process. All secret-store instances
share one monitor for key lookup/creation and encrypted-file reads, writes, and
deletes. Android Keystore aliases are scoped to the app UID; instance monitors
do not prevent two clients from replacing the same fresh alias. Replacing that
key makes ciphertext written by the other client unreadable.

Device identity uses one read-or-create operation under that monitor. Concurrent
startup therefore returns one persisted ID. Existing aliases, ciphertext, and
IDs keep their format and value. Failed authentication does not silently create
a new identity or discard credentials. A move to multiple Android processes
requires cross-process coordination before those processes share this store.

## Callback authentication

Callbacks use the generated protocol v2 definitions. Curfew derives independent request and response keys with HKDF-SHA256 and authenticates each message with HMAC-SHA256 over a canonical serialization: keys sorted, string values only, no whitespace. That agrees with RFC 8785 for the flat all-string field sets these two messages define, and `canonicalObject` accepts nothing else, so a future schema change that introduces a number or a nested object must revisit it rather than silently diverge. Golden vectors shared with `curfew-protocols` pin the bytes across the Kotlin, Swift, and TypeScript implementations.

Every receipt is bound to one campaign and one nonce. The nonce is 128 bits of fresh randomness per poll and is checked within the request that issued it; there is no durable cross-poll replay ledger, so the security rests on that freshness plus the receipt lifetime ceiling rather than on persistent bookkeeping. Curfew rejects redirects, non-HTTPS endpoints, malformed bodies, invalid MACs, mismatched campaigns, future observations, stale observations, and expired receipts. Network failures stay pending.

The owner can explicitly export the selected generated `CallbackDefinition` through Android’s document picker to provision a trusted verifier. That file contains the shared secret and must remain private. It is not exported automatically, logged, or copied to the clipboard. Saving a new condition clears its persisted verification marker. A signed setup exchange with a fresh non-persisted campaign UUID establishes readiness without releasing an alarm. The export and setup test do not require a Curfew account.

The default poll interval is 15 seconds, the request timeout is 5 seconds, and transport backoff is capped at 60 seconds.

## Account encryption

The coordinator receives ciphertext and routing metadata, never account setting plaintext. A random 256-bit account root key is device-held. New devices require authenticated enrollment or AAL2 plus the separate Curfew Recovery Key. Better Auth recovery codes restore sign-in only and do not decrypt Curfew settings.

Account sync uses optimistic record versions and device writer counters. Android rejects stale counters and accepts wake success only from selected devices within the campaign’s deterministic time window.

## Platform limits

Android offers no supported way to make an app literally unstoppable. Force-stop, uninstall, power-off, exact-alarm revocation, notification changes, audio routing, Do Not Disturb, and manufacturer battery controls can interfere. Curfew checks the supported readiness surface, uses `setAlarmClock()`, and recovers persisted work after reboot, replacement, time changes, and timezone changes. It never silently changes system volume.
