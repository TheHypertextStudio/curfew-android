# Android screenshot catalog

This catalog targets PR #18 (`codex/sunrise-p1`), including welcome/onboarding, wake-time
editing, service connection and transfer, readiness, review, daily alarms, settings,
account recovery, sound checks, pickers, ringing, and owner recovery. It uses production
composables with synthetic inputs and Google's Compose Preview Screenshot Testing.
The source revision and reviewed UI fingerprint are recorded with the output.

## Generate and browse

Use JDK 21, Python 3, Android SDK 37.0, and the protocol dependency required by this branch
(`v0.0.13`). Set `CURFEW_PROTOCOLS_CHECKOUT` to that protocol checkout for source substitution.

```sh
python3 scripts/screenshot_catalog.py
```

Open `artifacts/screenshot-catalog/index.html`. Light and dark are mutually exclusive.
Twelve previews appear per page, with one page scroll. **Present screenshots** fits one
whole capture onscreen; Tab/Right advances, Shift+Tab/Left goes back, and Escape closes.
Filters apply across all matching captures, including those on other preview pages.

The matrix contains phone, tablet, fold cover and fold inner windows in both orientations,
plus a narrow 200% text profile, each in light and dark. These are emulated window dimensions,
not physical fold/hinge evidence. Screen-content captures call production composables;
app navigation chrome, keyboard, system bars and animated transitions have separate native
captures. Top/end views cover long connection, account, disclosure and recovery content.
The catalog does not prove provider connectivity or alarm delivery.

Each full run replaces the stable artifact only after every declared capture succeeds.
The last successful artifact survives clean, focused and failed runs. The previous successful
version is kept at `artifacts/screenshot-catalog.previous`. Individual batch logs and a combined
renderer log accompany the HTML and manifest. Batches use fresh JVMs to bound memory.

```sh
python3 scripts/screenshot_catalog.py --state checkin_invalid_action --device large_text
python3 scripts/screenshot_catalog.py --refresh-viewer
```

When adding views without changing UI sources, `--resume` reuses only captures whose
reviewed UI fingerprint and complete case inputs still match; added/changed cases render
normally. A UI source change requires a new full render.

Focused output stays in `app/build/reports/screenshot-catalog`. HTML-only refresh validates
and reuses a successful full artifact, preserving pixels and capture timestamps.

## Native onboarding and setup

Use a disposable, task-owned `curfew-onboarding-*` emulator, never a user's device. Build and
install both normal Plain debug APKs on it. The capture drivers verify ownership and restore
size, density, font scale and theme. Welcome resets app data only on the guarded emulator.

```sh
python3 scripts/capture-welcome-matrix.py --serial SERIAL --expected-avd AVD --output artifacts/native-welcome
python3 scripts/capture-setup-matrix.py --serial SERIAL --expected-avd AVD --output artifacts/native-setup
python3 scripts/package_native_catalog.py
```

If capture stops, `capture-setup-matrix.py --resume` reuses completed profiles only after
validating the same APK, emulator identity, restoration and PNG dimensions. Failed-attempt
logs remain in the packaged diagnostics. It never skips an unsuccessful profile.

Packaging adds a **Native onboarding and setup** link to the maintained catalog. The native
viewer has the same exclusive themes, pagination and presentation controls. Native capture
manifests retain installed APK hashes, reviewed UI fingerprints, source metadata,
profile results and restoration status. The drivers require the installed APK to match
the current local build. If source timestamps are newer while Gradle leaves the APK
unchanged, rebuild with `assemblePlainDebug --rerun-tasks` before capturing.
The package step rejects incomplete profile matrices and missing images. Native images are
viewport samples; enlarged-text transfer, test-ready and review also include action-position
captures so their lower primary controls are visible.

## Maintain

Update `CatalogFixtures.kt` and `config/screenshots/matrix.json` together. Review UI source
changes and add missing states before updating the SHA-256 values in `coverage.json`.
The coverage check fails on changed/new/removed UI sources to prevent an old fixture inventory
from claiming coverage of new UI. Arbitrary text and all combinations of independent flags
are represented by meaningful visual branches and boundaries rather than an infinite matrix.

```sh
python3 scripts/screenshot_catalog.py --check-coverage
python3 -m unittest discover -s scripts/tests
node scripts/tests/catalog_interactions.cjs
```

`.github/workflows/screenshot-catalog.yml` generates and uploads the Compose matrix on relevant
PR/main changes or a manual run, retaining successful artifacts for 30 days. This configuration
becomes active after integration. Native capture remains an explicit emulator workflow; the
existing Android CI also runs its native capture suite. Download and extract the entire
artifact directory, then open `index.html` offline. Generated artifacts are ignored by Git.

The official preview testing plugin/API are pinned to `0.0.1-alpha16`. Catalog recording is
redirected under `app/build`; ordinary regression references are never approved or replaced.

[Official Compose screenshot testing documentation](https://developer.android.com/studio/preview/compose-screenshot-testing).


## Review in Codex

The inline catalog keeps the screen, state, form-factor, appearance, viewport,
and search filters. Light and dark are alternatives. Presentation mode uses
Tab or Right to advance, Shift+Tab or Left to go back, and Escape to return.
Neither view creates a second scrolling region.

Generate the inline viewer from the complete maintained catalog with a Python
environment that provides Pillow:

```sh
python3 scripts/build_inline_catalog.py --output /absolute/path/curfew-catalog.html
```

Codex limits inline content to 1 MB. The viewer embeds a selection of previews
and indexes every capture. CI also retains this fragment as `inline.html`. Choose the maintained `artifacts/screenshot-catalog`
folder in its file picker to connect every full-resolution image. That grants
access only to the chosen files; it does not upload them. The viewer rejects a
folder from a different capture run or native capture package. File access must be selected again
after reopening the view; filters can be restored without persisting images.

The inline fragment is response content. Keep it in the chat’s durable
visualization directory and display it using Codex’s inline visualization
reference. The portable HTML report remains available alongside the original
PNG captures for offline use.

## Printed-code check-in

The matrix includes code enrollment, printable draft, enrolled readiness, export failure, creation in progress and camera access required. Fixtures contain a nonfunctional example credential and render production setup content without persisting enrollment or opening the camera. Native permission-denied captures and CameraX start/stop tests remain separate evidence. The shared source fingerprint covers both consumer setup and camera screens; a changed fingerprint requires fresh full renders.
