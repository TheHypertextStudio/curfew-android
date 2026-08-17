# Curfew for Android

Curfew for Android is the wake companion to Curfew for macOS. It runs finite Perpetual Alarm campaigns and participates in the same encrypted wake protocol without blocking unrelated Android apps.

The default campaign rings three times for two minutes, with five quiet minutes between attempts: a deterministic sixteen-minute window. A campaign ends only when its configured condition succeeds, its finite attempts are exhausted, or a time-bounded authorized override arrives. There is no ordinary dismiss or snooze action.

Accounts are optional. Local schedules, generic HTTPS callbacks, and offline licenses continue to work without one. Account enrollment adds end-to-end encrypted settings, device coordination, purchases, and remote controls. The public account portal is [curfew.hypertext.studio/account](https://curfew.hypertext.studio/account).

## Build distributions

Both distributions use application ID `studio.hypertext.curfew`, the same version, and one signing lineage.

- `plain` is a directly distributed APK with no Firebase, Google Play Services, or GMS runtime dependency.
- `gms` produces Play APK/AAB artifacts and adds optional FCM wake-record acceleration only.

Correctness never depends on FCM. Both distributions use visible exact alarms, an active foreground WebSocket, bounded polling, and a WorkManager outbox. Apple and Google sign-in use OAuth 2.1 Authorization Code + PKCE in the system browser, so the plain distribution can sign in without Play Services.

Set `CURFEW_PROTOCOLS_CHECKOUT` to a `curfew-protocols` checkout while developing locally. Its generated `:generated:kotlin` build substitutes the published `studio.hypertext.curfew:curfew-protocols:0.2.3` artifact. Standalone builds read that artifact from GitHub Packages and require `GITHUB_ACTOR` plus a `GITHUB_TOKEN` with `read:packages`, or matching `gpr.user` and `gpr.key` Gradle properties.

The initial interoperable Android release is verified against immutable protocol revision `bddd7c266f3df1b048ef37d83c3270bf24f7cc12`, which carries the published `0.2.3` artifacts.

```sh
export CURFEW_PROTOCOLS_CHECKOUT=/path/to/curfew-protocols
./gradlew testPlainDebugUnitTest testGmsDebugUnitTest
./gradlew lintPlainDebug lintGmsDebug
./gradlew assemblePlainRelease assembleGmsRelease bundleGmsRelease
./gradlew verifyPlainRuntimeIsolation generateCurfewSbom writeReleaseChecksums writeBuildProvenance
./gradlew :app:checkLicense --no-parallel --no-configuration-cache
```

Production signing uses these protected environment variables for both release variants:

- `CURFEW_ANDROID_KEYSTORE`
- `CURFEW_ANDROID_STORE_PASSWORD`
- `CURFEW_ANDROID_KEY_ALIAS`
- `CURFEW_ANDROID_KEY_PASSWORD`

Without them, local release builds use the Android debug key and are not production artifacts.

## Project boundaries

- Shared wire types come only from the pre-1.0 `curfew-protocols` 0.2 release line. Android does not maintain parallel callback or wake DTOs.
- The callback is deliberately generic: label, HTTPS endpoint, random secret, poll policy, and optional opaque action URL.
- Encrypted settings are synchronized through `curfew-sync.hypertext.studio`; FCM never carries decrypted settings or authoritative state.
- Android cannot defeat OS-level force-stop, uninstall, power-off, permission revocation, or every manufacturer battery policy. Readiness discloses those limits before arming.

See [Security](docs/SECURITY.md), [Privacy](docs/PRIVACY.md), [Design](docs/DESIGN.md), [Play policy](docs/PLAY_POLICY.md), and [Testing](docs/TESTING.md).
