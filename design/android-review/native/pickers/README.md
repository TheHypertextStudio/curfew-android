# Alarm picker fixtures

Reader: Curfew's product owner and Android maintainer.
Action: Review the alarm editor's date/time dialogs before accepting P1.

These are full-display native captures from the owned API33 emulator.
The normal profile is 411dp with 100% text. The enlarged profile is 320dp
with 200% text and the keyboard open. The accompanying manifest records
the installed APK checksum, four actual OS profiles, native test results,
and settings restoration. The test host supplies the modal; these images
do not prove physical sound or the full installed alarm journey.

The dialogs retain Curfew's fonts and colors. The normal time dial and
calendar use Material controls. Numeric time entry uses Material fields
at every size. The calendar's existing body inset is not applied twice.
The title and action area retain 24dp padding. The content can scroll
without moving Cancel and Done below the keyboard.

The native tests first rejected unthemed framework actions, missing
backdrop cancellation, an enabled mode switch during invalid input, and
forced date input on an ordinary phone. The corrected dialogs preserve
the draft until Done, and only the editor's Save stores the alarm.
Reverting these dialogs requires no saved-data or protocol migration.
