# Test strategy

The unit suite uses a fake clock and deterministic instants to cover every state transition, retry, final exhaustion, process snapshot recovery, elapsed-versus-wall-clock recovery, DST gaps and overlaps, recurrence bounds, callback golden vectors, tamper/replay rejection, selected-device convergence, exact-alarm permission fallback, OAuth PKCE, and distribution transport parity.

Instrumented tests cover Room persistence, Android Keystore encryption under no-backup storage, manifest export/permission posture, and the no-dismiss/no-snooze ringing screen with a 48 dp action target.

CI builds and lints both distributions and runs unit tests for each. Emulator jobs exercise API 33–36 where hosted acceleration is available. `verifyPlainRuntimeIsolation` resolves the plain release graph and rejects Firebase or Google Play Services. The behavioral verifier at `verification/curfew-wake.json` lets Cello invoke alarm and wake-proof suites without a shell.

Before release, run the alarm on a current Pixel and at least one non-Pixel device, including locked-screen delivery, Doze, DND, full-screen denial, exact-alarm revocation, reboot, package replacement, timezone change, audio focus loss, and manufacturer battery management. Those physical-device results cannot be substituted by host tests.
