# Security model

Curfew treats wake controls and callback credentials as security-sensitive.

## Local protection

- Callback secrets, account tokens, the device private key, the account root key, and Recovery Key material are encrypted with AES-256-GCM under a non-exportable Android Keystore key.
- Encrypted secret blobs live under `noBackupFilesDir`. Application backup and device-transfer extraction are disabled for all app data.
- Room stores runtime state, encrypted outbox payloads, and non-secret callback configuration. No secret values are logged.
- Exported flags are explicit. Only the launcher/OAuth callback activity is exported; alarm receivers and services are not.
- Pending intents are immutable, and normal alarm notifications contain no stop, dismiss, or snooze action.

## Callback authentication

Callbacks use the generated protocol v2 definitions. Curfew derives independent request and response keys with HKDF-SHA256, authenticates RFC 8785 canonical JSON with HMAC-SHA256, and binds every receipt to one campaign and one random nonce. It rejects redirects, malformed bodies, invalid MACs, replayed nonces, mismatched campaigns, future observations, stale observations, and expired receipts. Network failures stay pending.

The default poll interval is 15 seconds, the request timeout is 5 seconds, and transport backoff is capped at 60 seconds.

## Account encryption

The coordinator receives ciphertext and routing metadata, never account setting plaintext. A random 256-bit account root key is device-held. New devices require authenticated enrollment or AAL2 plus the separate Curfew Recovery Key. Better Auth recovery codes restore sign-in only and do not decrypt Curfew settings.

Account sync uses optimistic record versions and device writer counters. Android rejects stale counters and accepts wake success only from selected devices within the campaign’s deterministic time window.

## Platform limits

Android offers no supported way to make an app literally unstoppable. Force-stop, uninstall, power-off, exact-alarm revocation, notification changes, audio routing, Do Not Disturb, and manufacturer battery controls can interfere. Curfew checks the supported readiness surface, uses `setAlarmClock()`, and recovers persisted work after reboot, replacement, time changes, and timezone changes. It never silently changes system volume.
