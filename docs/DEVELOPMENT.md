# Development

## Quickstart

Build and test a fresh clone with public tools only.

Install these first:

- JDK 21 (CI uses Temurin 21; the Kotlin code targets JVM 17). `./gradlew` downloads Gradle 9.8.2 itself.
- The Android SDK with platform `android-37.0` and build tools `37.0.0`, for example `sdkmanager "platforms;android-37.0" "build-tools;37.0.0"`. Point `ANDROID_HOME` at it or set `sdk.dir` in `local.properties`.
- Git.

The shared wire types come from the `curfew-protocols` contract, version 0.0.13. Its published artifact sits on a GitHub Packages registry that needs a token, so build against the source instead. Check out the tag next to this repository and point `CURFEW_PROTOCOLS_CHECKOUT` at it:

```sh
git clone --branch v0.0.13 https://github.com/TheHypertextStudio/curfew-protocols.git ../curfew-protocols
export CURFEW_PROTOCOLS_CHECKOUT="$PWD/../curfew-protocols"
```

Then run, from the repository root:

```sh
./gradlew testPlainDebugUnitTest testGmsDebugUnitTest
./gradlew lintPlainDebug lintGmsDebug
./gradlew assemblePlainDebug assembleGmsDebug
```

The `plain` and `gms` distributions differ only in optional Google Play Services support; build and test both. The release tasks, runtime isolation check and license check listed in the [README](../README.md#build-distributions) are not needed for a contribution. Release builds stay unsigned without the protected signing variables.

No device or emulator is needed for these commands. The [testing guide](TESTING.md) describes the instrumented and screenshot suites that do need one.
