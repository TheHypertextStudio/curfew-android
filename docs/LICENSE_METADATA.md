# Restore verified dependency license metadata

Reader: The Android maintainer changing dependencies or the license gate.
Action: Recheck the exact upstream POM before adding or changing a metadata entry.

The license report for Material Adaptive 1.3.0 omitted licenses for `adaptive`,
`adaptive-android`, `adaptive-layout`, and `adaptive-layout-android`. On October
21, 2026, all four official Google Maven POMs declared The Apache Software
License, Version 2.0. Each entry in
[`config/license-metadata.json`](../config/license-metadata.json) records its
exact coordinate, license, and source POM.

The license-report filter supplies this verified metadata only when every POM
and manifest has no licenses and the dependency has no embedded license files.
It preserves any license metadata the plugin does discover. A different artifact
or version receives no fallback. The existing allowed-license policy remains
unchanged, and unknown licenses still fail the gate.

The plugin's `LicenseBundleNormalizer` transforms existing license records. It
does not create records when their collection is empty, so a transformation
bundle alone cannot fix this failure. The filter uses the plugin's public
`DependencyFilter`, `PomData`, and `License` API instead.

Run `:app:checkLicense` after changes. Remove an entry when the plugin recovers
the upstream metadata without it. Removing this filter restores the original
failing license gate and does not affect app runtime behavior.
