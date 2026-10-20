# Android launcher icon

Reader: The Android maintainer changing Curfew branding.
Action: Preserve the Curfew sun while checking Android launcher masks and themed icons.

The Android icon uses the orange sun and purple-to-orange dusk colors from
`Curfew/AppIcon.icon` in the macOS repository. It uses flat vector layers
rather than Apple's glass, shadows, or a rounded tile baked into the image.
Android's launcher supplies the outer mask and motion.

Both launcher entries reference a full-bleed 108dp background, a transparent
108dp foreground, and a separate white monochrome silhouette. The setting sun has
a 20dp radius, centered at (60, 50), clipped below y=59. The horizon mark
runs from x=26 to x=82 at y=64–67. All foreground pixels remain within the
centered 66dp safe circle. The full-bleed background has a lower dusk band
that meets the launcher mask. The off-center sun and horizon distinguish
the icon from a plain dot. The monochrome layer lets the launcher apply wallpaper
colors. The Android 13 minimum means pre-adaptive bitmap fallbacks are
unnecessary; the previous stock Android bitmap resources were removed.

`LauncherIconInstrumentedTest` checks the packaged adaptive drawable,
opaque gradient background, transparent foreground, sun color, horizon separation, safe circle,
and matching themed silhouette. It also renders a native contact sheet
with circle, squircle, rounded-square, and themed treatments. Review that
image after geometry or palette changes. The test first failed against the
stock robot before the replacement resources were written.

This change adds no permission, persistence migration, schedule behavior,
or release protocol. Reverting the icon resources restores the old launcher
art without changing saved alarms. Launchers may cache icons after updates.

The sizing follows [Android's adaptive icon guidance](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).
