# Curfew Android product and interface design

Reader: Curfew's Android designer and maintainer.
Action: Implement and review the complete local Sunrise experience before accepting P1.
Status: The native replacement is in progress. The older configuration scroll is not the accepted product design.

Curfew supplies the cream, ink, ember, Calistoga, Hanken Grotesk, and horizon identity. Material 3 Expressive supplies the native controls, shapes, type roles, motion, and interaction conventions. Use official Material Symbols Rounded. Do not draw substitute platform icons or recreate native controls to make a browser mockup look Android-like.

The replacement uses Material 3 `1.5.0-beta01` and its required Compose `1.12.0` dependency line. The stable Material 3 `1.4.0` artifact keeps Expressive APIs internal. The public beta therefore replaces the old baseline rather than bypassing Kotlin visibility. Its compile SDK is 37; minimum and target SDK decisions remain separate. Build and installed checks must cover both distributions after this dependency change.

Use the shared Curfew design roles from `curfew/landing/public/design/tokens.css`. Fill the Android color roles rather than retaining unassigned stock colors. Use the brand by default. Wallpaper colors require an explicit user choice; the app currently exposes no such choice. Keep platform type sizing, locale, screen-reader semantics, focus, reduced motion, and system insets.

## Screens and tasks

Daily use centers on the saved time, seven visual alarm days or one-night date, the on/off control, and the named wake check. Group the time, days, and switch on one tonal surface. Give the check-in its own row. Do not add a status dashboard or explain each visible control in a paragraph. Use “Alarm on” and “Alarm off,” not “armed.”

Alarm editing uses native time and date controls and one Save action. Keep a draft until Save. Back cancels changes. Show the actual next occurrence in the user's locale. A completed one-night alarm does not become a recurring alarm. Future edits and toggles cannot dismiss an active occurrence.

First setup handles required access, an audible channel, a real sound check, and the wake check in focused steps. Show the current blocker and its next action. Keep Android enforcement limits accurate. A notification or stored test flag does not prove that the person heard audio.

Copy names the task, current state, or action. Do not narrate the layout, describe visible controls, or repeat a heading in supporting text. Keep guidance only when it explains a prerequisite, consequence, recovery step, or non-obvious choice. Expose destination names to assistive technology through pane and heading semantics instead of adding visible labels.

Check-in setup belongs in its own destination. The current P1 HTTPS implementation generates and exports a private setup file for the trusted operator, then tests an authenticated response. It is an integration path, not a consumer-facing endpoint form on the daily alarm screen. The browser proposal's QR and verifier-issued import are not implemented capabilities. Do not advertise them until they work. Account setup remains separate and optional.

During ringing, show the chosen alarm time and named wake check. Give the check-in action priority. Quiet intervals still represent an active alarm. Lost connectivity does not stop the saved alarm or establish success. Do not offer ordinary dismiss or snooze. A verified current approval stops only its occurrence.

[Alarm recovery](OWNER_RECOVERY.md) is a separate deliberate task. It requires a reason, a three-second confirmation, and fresh Android screen-lock authentication. It records an override without calling the check-in complete. Keep the reason after cancellation or failure. Show visible progress and an equivalent keyboard and assistive-technology path.

P1 is local Sunrise. Multi-device release belongs to P1b. Bedtime Curfew remains a separate required MVP capability. Do not imply that this alarm completes the Android MVP.

## Adaptation and review

Compact screens show one focused task. Expanded screens may show a related saved-alarm summary beside its editor. Do not distribute unrelated permissions, account forms, and integration details into columns. Keep ordinary controls at least 48dp, and retain meaningful labels when using visual day indicators.

Respect keyboard and system insets. Keep every action reachable on short windows and at 200% text. Retain native 12/24-hour and date formatting, RTL navigation, predictable Back behavior, and focus order. Honor reduced motion without shortening an authorization requirement.

Review rendered daily, editing, setup, active, quiet, offline, rejected-check, recovery, cancellation, and storage-error states in both themes. Check 360dp phones, short windows, expanded layouts, and enlarged text. Judge whether the complete workflow makes sense as well as whether each screen fits. Source changes, a successful render, and passing tests alone do not establish product quality.

Sources: [Material 3 Compose releases](https://developer.android.com/jetpack/androidx/releases/compose-material3), [Material theming](https://developer.android.com/develop/ui/compose/designsystems/material3), and [Android adaptive layouts](https://developer.android.com/develop/adaptive-apps/guides/canonical-layouts).


## Remaining native review findings

The October 19 native daily captures at 320dp and 200% text expose crowded weekday markers and a time label that shrinks too far when AM/PM shares the same line. The next pass must preserve weekday separation and numeric time hierarchy without adding explanatory copy. The saved check-in name now identifies its own daily action; only an unresolved connection needs supporting status.

The platform time-picker capture shows an unbranded teal dialog beneath a System UI ANR. The overlay invalidates that capture as visual acceptance evidence. The picker still needs a valid full-display capture and a brand-aligned native component. The current Compose theme does not style an Android framework dialog.
