# Current native daily fixtures

Reader: Curfew's product owner and Android maintainer.
Action: Review the daily alarm's proportions and narrow-screen behavior.

These images show the installed Compose screen on the owned API33 emulator.
The fixture supplies an example 7:00 AM weekday alarm and check-in. It changes
content width and Compose text scale for each capture. The JSON manifest
records those parameters. These are synthetic layout fixtures; they do not
establish device sound, authorization, or release readiness.

Review covers light/dark appearance, 320/390dp width, and 100/200% text. The
larger chosen time stays visually dominant and fits by measurement. The daily
column is limited to 420dp on expanded windows. The switch sits beside the time, and ordinary on/off text no longer
consumes a separate row. The cards remain below the app bar; whole-page centering failed visual review and was removed.

The ordinary-size hierarchy test failed against the previous 68sp ceiling.
The replacement permits 100sp where width allows it. Narrow windows retain
measured fitting and the existing localized AM/PM behavior. The layout suite
also covers weekday separation, one-night dates, short windows, large text,
and proximity between the alarm and check-in. All 24 native layout/navigation/capture
cases passed on API33. Both debug builds, both unit
test suites, and both Android lint variants passed locally.

The lower viewport still has unused space. The change improves the primary
alarm's proportion; it does not establish a new purpose for that area.
Rollback restores the previous clock ceiling and daily column width. No
stored alarm, permissions, or wire contract changes are part of this pass.
