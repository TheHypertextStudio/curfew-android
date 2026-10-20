# Native first-use review

Reader: The Curfew Android reviewer.
Action: Compare the first-use, permission, review, and adaptive layouts against the product flow.
Date: October 20, 2026.

`fresh-main.png` shows the real MainActivity after resetting only the owned
API36 emulator. The first screen requests no permission and starts no alarm.

`welcome.png`, `permission.png`, `review.png`, and `sound.png` are native Compose
fixtures on API36 at normal text size. They contain synthetic data. These are
not HTML mockups or evidence of physical hearing.

`permission-dark-200.png` shows the 320dp/200% dark profile. The label wraps and
the controls remain reachable by scrolling. `wide-alarm.png` shows the official
Expressive navigation rail in a 1080dp window. The layout driver passed all
21 cases and verified restoration of the original emulator configuration.

The app uses the existing Curfew type and color roles. The focused permission
surface gives its main action one purpose. The time shares bounds through
Navigation 3 between daily, editor, and review. Screenshots establish static
layout only; manual motion and TalkBack acceptance remain separate.

The full flow and current service limitation are in
[the first-use design](../../../../docs/ONBOARDING_DESIGN.md).
