# Consumer alarm and check-in captures

Reader: Curfew's product owner and Android maintainer.
Action: Review ordinary labels, setup completion, and adaptive layout.

These native Compose fixtures come from the complete 121-case API33 Plain
run on October 20, 2026. The manifest records the APK digest, content width,
Compose text scale, theme, and bitmap bounds. These are synthetic layout
captures; they do not establish physical sound or device acceptance.

The connected detail retains its saved name and Connected status. It uses
Back without a redundant Done. Initial setup retains Done after verification,
including after canceling replacement. Separate installed behavior tests
verify that distinction and preserve failure feedback.

The editor uses Edit alarm, and check-in setup omits the valid-link hint.
The selected examples cover 390dp normal text and 320dp enlarged text in
both themes. Review the whole image, including the remaining viewport;
these captures do not claim that the unfinished bedtime MVP exists.
