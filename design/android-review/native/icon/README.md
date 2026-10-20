# Adaptive icon review

Reader: The Android maintainer reviewing launcher artwork.
Action: Compare the same Curfew sun under Android launcher masks.

`launcher-icon-review.png` is rendered by Android Canvas from packaged
adaptive vector resources on API36. It shows circle, squircle, rounded-square,
and sample wallpaper-themed masks. The themed colors are a preview palette;
the installed launcher chooses its own wallpaper colors. No Apple tile,
glass, or shadow is baked into the resources. Geometry stays inside Android's
safe circle. `LauncherIconInstrumentedTest` also checks the packaged layers.
