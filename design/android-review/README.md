# Android product design review

Reader: The Curfew product owner and Android maintainers.
Action: Review the daily alarm, first-run setup, and wake-condition connection flows before implementing the replacement native UI.

The owner rejected the installed Android UI on October 19, 2026. These files are design proposals. Native implementation continues in the Android app; the browser proposal does not establish native functionality or release readiness.

## Pass 1: Product structure

The rejected screen exposes the integration protocol as the ordinary product. It mixes alarm editing, permission repair, callback configuration, account enrollment, and technical limitations in one scroll. Reflowing that scroll into two columns does not resolve the task hierarchy.

Use a daily Sunrise screen for the selected time, recurrence, saved wake condition, and armed state. Put time and recurrence in one editor. Put permission readiness in a finite setup flow. Put account/network integration and connection diagnostics in Settings. Retain Curfew bedtime as a distinct MVP capability; this Sunrise prototype does not claim bedtime enforcement already exists.

The existing P1 callback path requires someone to operate a trusted verifier. The owner currently types an endpoint and exports a private key. A visual cleanup cannot make that a consumer setup. The proposed connection flow imports a verifier-issued setup file or scans a code, reviews the service identity and release authority, then verifies the signed connection. The existing CallbackDefinition can carry those fields; QR/file import and provider-issued setup still require implementation and a supported verifier. Do not imply an existing keyholder portal or invent exercise, NFC, or camera verification.

## Pass 2: Brand and control hierarchy

Use Curfew's existing Calistoga and Hanken Grotesk fonts, cream/ink/ember roles, and horizon mark. Keep one primary action per screen. Use tonal grouping for related settings, native switches for armed state, and one descriptive row for a saved wake condition. Reserve warning/error roles for actual conditions. Do not silently replace the brand with wallpaper colors.

The Android implementation should retain Material 3 semantics, native pickers, system bars, back behavior, focus behavior, accessible targets, and adaptive panes. This browser prototype reviews hierarchy and visual direction; it cannot prove native platform or Material component behavior.

## Pass 3: State and adaptation

The proposal includes unconfigured, ready, editing, permission setup, connection import, connection review, ringing, and unavailable-connection states. A rejected or unavailable condition never becomes verified. Connection review names the authority granted before confirmation. Importing is a proposal, not a shipped action. The ringing screen has one wake-condition action and a separate deliberate recovery path; it has no ordinary dismiss or snooze.

Review at 360px and 390px, enlarged text, short/landscape windows, light/dark appearance, long condition names, and failed import/verification. Keep the current time and action readable without fixed-height clipping. Secondary details may scroll; actions must remain reachable. Test native TalkBack, keyboard/IME, 48dp targets, and system insets after implementation.

## Alternatives

Reskinning the current form retains the wrong product structure. A mandatory account/keyholder portal expands P1 and falsely suggests a service that does not exist. The proposed dedicated Sunrise flow preserves local use while moving integration complexity to a deliberate connection task. A turnkey built-in wake proof would require a separate product decision and implementation; this proposal does not silently invent one.

## Open decisions and evidence

The trust and distribution mechanism for verifier-issued setup needs implementation review. The prototype cannot establish that a scan, imported file, emergency recovery, native time picker, or signed connection works. Keep the accepted P1 engine and authority checks when replacing the native UI. Preserve the in-progress concurrency changes separately; they are paused and unaccepted.

The review uses Curfew's `Documentation/copy-guidelines.md`, `Documentation/settings-ui.md`, and the fetched shared design assets. Those documents establish precise copy, task separation, progressive disclosure, native controls, and honest verification boundaries. They are the source of the corresponding requirements here.

## Pass 4: Consumer language and visual schedule

The October 19 follow-up removes “armed” from the interface. Daily use now says “Alarm on” or “Alarm off.” A seven-day row shows selected days without a paragraph. The daily time sits directly on the page rather than inside another card. The check-in row no longer repeats its purpose in supporting copy. Setup uses “check-in,” “connection,” and “try again”; protocol details stay outside the main task.

The independent review identified provisional edits that changed the saved schedule before Save. The prototype now keeps a separate draft and discards it on Back. Expanded editing shows a small current-alarm summary rather than another enabled switch. The ringing mark is smaller so the time and wake action take precedence.

Browser checks passed for draft cancellation/save, disabled scheduling, active-alarm isolation, prerequisite preservation, unconfirmed connection selection, and the recovery reason gate. All 64 combinations of 360px/390px width, normal/double text, and 16 task states had no horizontal overflow. These checks cover the HTML proposal only. Native accessibility, system permissions, real sound, secure enrollment, and credential confirmation remain unverified.

## Pass 5: Material controls and containment

The owner rejected the flattened daily view. The alarm time, seven-day row, and on/off switch now share one tonal surface. The check-in occupies a separate surface. The prototype uses the official Google Material Symbols Rounded SVG assets, including navigation and confirmation icons. Native implementation must use Material 3 Expressive components and theme roles rather than reproduce the HTML controls.

Remove self-narrating copy and keep control labels, observed status, and material consequences. Review commentary remains outside the product screen. The HTML checks still pass after these changes; they do not establish native Material behavior.

Sources: [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3) and [Google Material Symbols](https://github.com/google/material-design-icons). Icon license lives beside the downloaded SVG assets.


## Pass 6: Copy restraint

The October 19 copy pass removes repeated setup headings, instructions that restate scan/file actions, the recovery heading that repeats its navigation title, the reason placeholder that repeats its label, and decorative account/appearance headings. The check-in action now has one label. Consequences, connection state, prerequisites, and recovery requirements remain visible where they affect a decision. Native screens follow the same rule in `docs/DESIGN.md`.
