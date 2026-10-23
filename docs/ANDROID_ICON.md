# Android launcher icon

Reader: The Android maintainer changing Curfew branding.
Action: Preserve the established macOS icon and verify Android launcher masks.

The Android icon keeps the Mac's complete orange sun on its dusk gradient.
It adapts that artwork to Android's background, foreground, and monochrome
layers. It does not add a horizon, sea, lower color band, tile, shadow, or glass.
Android supplies the launcher mask and adaptive motion.

## Source geometry and color

The source is `Curfew/AppIcon.icon/icon.json` and its `Assets/sun.png` in the
macOS Curfew repository. The 1024px transparent asset contains a full disc with
bounds (302, 335)–(722, 755), center (512, 545), and radius 210px. The asset's
solid RGBA color is (238, 135, 59, 255), or `#EE873B`. Its small vertical offset
is part of that source artwork; the sun remains horizontally centered.

Android's full layer is 108dp. The visible 72dp launcher viewport maps the
source canvas between x/y=18 and x/y=90. That mapping gives center
(54, 56.3203125) and radius 14.765625dp. The complete disc therefore occupies
41.016% of the visible icon width, matching the Mac asset's 420/1024 ratio.
All foreground pixels stay inside the centered 66dp safe circle.

The Mac's normal gradient has Display P3 endpoints (0.30, 0.24, 0.42) and
(0.93, 0.46, 0.22). The Android sRGB vector uses `#4F3C6E` and `#FF6C1E`.
Do not interpret the source P3 component values directly as sRGB hex colors.
The native test independently converts the source endpoints through Android's
ColorSpace API and checks the packaged gradient. The gradient spans the 72dp
viewport and extends opaquely to the full layer edges.

The monochrome foreground is the same complete circle in white. Android's
launcher applies wallpaper colors. It does not use a different horizon mark.
The normal launcher artwork keeps the source's normal palette independently
of the app's light or dark appearance. The Android 13 minimum requires no
pre-adaptive bitmap fallback.

## Verification

`LauncherIconInstrumentedTest` checks the packaged adaptive drawable, both
halves of the full disc, source orange, centered geometry, source proportions,
transparent foreground, safe circle, continuous background, source gradient,
and matching monochrome silhouette. The corrected test must fail against the
previous clipped sun and horizon before the replacement resources are applied.

`renderLauncherMaskReview` produces eight native-rendered preview cells:
circle, squircle, rounded square, and teardrop in normal and themed treatments.
These represent common masks. They do not claim exhaustive coverage of every
manufacturer's launcher. Review the generated contact sheet after geometry or
palette changes. Launcher icon caches can delay a visible update after install.

The icon changes no permission, onboarding, persistence, schedule behavior,
or release protocol. Reverting these three drawable resources changes the
launcher artwork without altering saved alarms.

The sizing follows [Android's adaptive icon guidance](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).

The October 20 correction's failing-first native run used API36 and the old
clipped-sun resources. All three cases ran. The geometry assertion failed on
the missing lower disc, and the background assertion failed on the invented
lower band. The render case passed. The corrected resources require their own
build, native green run, and reviewed eight-cell sheet before acceptance.
