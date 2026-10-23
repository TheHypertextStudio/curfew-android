# Full-screen alarm onboarding

Reader: The Android maintainer reviewing first setup.
Action: Verify the permission request and return path before physical acceptance.

These captures show the real MainActivity on the owned API36 emulator after
an app-state reset. Exact-alarm and notification prerequisites were granted
for the controlled check; full-screen access started denied. The permission
button opens Android's package-specific settings page. Enabling the system
switch and returning advances setup to the sound test. No sound check is
silently confirmed. The normal Pixel reset remains pending reconnection.

The launcher preview in the sibling icon directory shows the off-center
setting sun and horizon under four Android masks.

The status icons use Google's official Material Symbols Rounded
[check circle](https://github.com/google/material-design-icons/blob/master/symbols/android/check_circle/materialsymbolsrounded/check_circle_fill1_24px.xml)
and [unchecked circle](https://github.com/google/material-design-icons/blob/master/symbols/android/radio_button_unchecked/materialsymbolsrounded/radio_button_unchecked_24px.xml).
Compose supplies their tint instead of the AppCompat XML theme attribute.
They use the same Apache 2.0 license as the existing Material Symbols assets.
