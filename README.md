# Curfew for Android

Curfew for Android participates in the Curfew device network. Bedtime lockout
(Curfew) and morning wake-up (Sunrise) are equal parts of the product. A connected
Android device must carry out the owner's plans on that device.

This P1 branch implements local Sunrise delivery. Bedtime enforcement and
coordinated cross-device actuation remain separate acceptance work. macOS can
synchronize state and take part in release checks, but it does not claim to wake
a sleeping user.

The default campaign rings for two minutes, stays quiet for one minute, and repeats until a verified callback release or a fresh authorized override arrives. There is no ordinary dismiss or snooze action.

Accounts are optional. Local schedules, generic HTTPS callbacks, and offline licenses continue to work without one. Account enrollment adds end-to-end encrypted settings, device coordination, purchases, and remote controls. The public account portal is [account.thecurfew.app/account](https://account.thecurfew.app/account).

See [Android launch scope](docs/LAUNCH_READINESS.md) for current device, account, signing and enforcement boundaries.

## Build distributions

Both distributions use application ID `studio.hypertext.curfew`, the same version, and one signing lineage.

- `plain` is a directly distributed APK with no Firebase, Google Play Services, or GMS runtime dependency.
- `gms` produces Play APK/AAB artifacts and adds optional FCM wake-record acceleration only.

Correctness never depends on FCM. Both distributions use visible exact alarms, an active foreground WebSocket, bounded polling, and a WorkManager outbox. Apple and Google sign-in use OAuth 2.1 Authorization Code + PKCE in the system browser, so the plain distribution can sign in without Play Services.

Set `CURFEW_PROTOCOLS_CHECKOUT` to a `curfew-protocols` checkout while developing locally. Its generated `:generated:kotlin` build substitutes the published `studio.hypertext.curfew:curfew-protocols:0.0.13` artifact. Standalone builds read that artifact from GitHub Packages and require `GITHUB_ACTOR` plus a `GITHUB_TOKEN` with `read:packages`, or matching `gpr.user` and `gpr.key` Gradle properties.

CI checks out `curfew-protocols` tag `v0.0.13` at `764ec480a5e5424856150be767a33a8ab022ae08`. Use the same immutable source for local acceptance. A substituted local build does not prove GitHub Packages access or a standalone artifact download.

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

Without them, release builds remain unsigned and are not production artifacts. CI release evidence contains unsigned verification artifacts; it does not establish production signing.

## Project boundaries

- Shared wire types come only from the pinned `curfew-protocols` 0.0.13 contract. Android does not maintain parallel callback or wake DTOs.
- The callback is deliberately generic: label, HTTPS endpoint, random secret, poll policy, and optional opaque action URL.
- Encrypted settings are synchronized through `sync.thecurfew.app`; FCM never carries decrypted settings or authoritative state.
- Android cannot defeat OS-level force-stop, uninstall, power-off, permission revocation, or every manufacturer battery policy. Readiness discloses those limits before arming. After a restart, the owner must unlock the phone before alarms can resume.

Connect an existing trusted service using [verifier setup](docs/VERIFIER_SETUP.md).
The local alarm acceptance record is [Sunrise P1](docs/SUNRISE_P1.md).

See [Security](docs/SECURITY.md), [Privacy](docs/PRIVACY.md), [Design](docs/DESIGN.md), [Play policy](docs/PLAY_POLICY.md), and [Testing](docs/TESTING.md).

## Worktree setup

Run `./bootstrap worktree prepare` after creating a checkout. Codex runs this
command through the checked-in local environment. The pinned Studio engine
reuses native dependency caches and keeps installed dependencies and mutable
build outputs inside this checkout. Setup does not build applications or start
services. The first engine download requires GitHub CLI authentication with
access to the private `TheHypertextStudio/bootstrap` repository.

Run `./bootstrap worktree plan --json` to inspect proposed actions or
`./bootstrap worktree check` to inspect readiness. Use `--no-install` to configure
cache reuse without resolving dependencies. Use `--offline` only when the engine
and dependency artifacts already exist locally; missing artifacts are reported.
Preserve shared caches when cleaning a checkout. Continue using the repository's
existing build and test commands.

## Screenshot catalog

Generate the Sunrise/onboarding matrix and browse it offline with presentation controls.
See [the screenshot catalog guide](docs/SCREENSHOT_CATALOG.md) for Compose and native capture workflows.
