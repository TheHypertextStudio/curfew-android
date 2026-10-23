# Responsive alarm review

Reader: The Android designer and maintainer reviewing scaled windows.
Action: Judge readable labels and reachable controls in these native captures.

The API36 emulator uses actual Android window size, display density, and text
scale. The alarm and account values are synthetic test state. These images
prove neither physical alarm delivery nor hearing. The manifest records the
installed Plain APK and display configurations. Root captures omit system
bars. Editor captures follow scrolling the last weekday into view; a cropped
heading in landscape is the scroll position, not missing content.

The phone editor fits all seven weekday choices in a row. Narrow windows
wrap the repeat choices instead of clipping their labels. Enlarged daily
rows omit their decorative leading icon when it would split normal words.
Landscape retains a scrollable task. Expanded windows retain permanent
navigation when height and text size leave enough room for it.

The five-profile local matrix also includes 320dp at 200% text. All five
journeys traverse editing and Account without saving. The driver restores
its original display size, density, and text scale. Full local logs and the
pre-correction captures remain in `build/sunrise-evidence/responsive/`.
