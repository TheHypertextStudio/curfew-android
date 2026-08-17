# Google Play declarations

The `gms` artifact is intended for Play distribution. The `plain` artifact is a direct signed APK and contains no Google runtime dependency.

## Exact alarms

Curfew declares `SCHEDULE_EXACT_ALARM`, not `USE_EXACT_ALARM`. The core, user-visible function is waking the user at an explicitly chosen time. The readiness flow sends the user to the system grant screen, checks `canScheduleExactAlarms()` every time it arms, and reacts to permission-state changes by rescheduling persisted work.

## Foreground service

Curfew declares the `mediaPlayback` foreground-service type and its matching permission. It starts playback only from a delivered user-visible alarm clock, not directly from boot. Reboot recovery schedules an immediate alarm-clock transition before restarting playback on supported paths. The ongoing alarm notification describes active playback and opens the alarm status screen.

## Full-screen intent

Curfew requests full-screen intent access only for an actively ringing, time-sensitive alarm. Android 14+ capability is checked before use. If unavailable, the app falls back to a high-priority public lock-screen notification.

## Why the ringing screen has no dismiss or snooze

Expect a reviewer to ask, because every other alarm app has one. The alarm ends
when the wake condition the user configured reports satisfied, when the attempt
budget is spent, or when the campaign deadline passes — whichever comes first.
It does not end because the user tapped a button while still in bed, which is
the failure this product exists to remove.

The three points that make this a bounded, user-controlled feature rather than a
trap, and the ones worth putting in front of a reviewer:

- **It always terminates without user action.** `AlarmConfiguration` derives
  `campaignDurationSeconds` from the attempt count, ring duration, and quiet
  interval, and the schema caps it at 7200 seconds. Every terminal outcome,
  including exhaustion, releases. `WakeOutcome` records exhaustion as a fact; it
  never strands a device.
- **The user chose the terms in advance.** Attempt count, ring length, quiet
  interval, and the wake condition are all configured by the user before the
  alarm is armed, on a device they hold.
- **The system controls remain available.** Curfew never changes device volume
  (`AlarmReadiness.mayChangeSystemVolume` is hardwired false and tested), holds
  no device-admin role, and does not prevent the user from silencing the device,
  force-stopping the app, or uninstalling it. `docs/SECURITY.md` states plainly
  that Android offers no supported way to make an app unstoppable, and Curfew
  does not attempt one.

`AlarmRingingScreenInstrumentedTest` asserts the absence of the controls so the
behavior cannot regress silently into an ordinary alarm.

## Notifications, audio, and data safety

Notification permission is requested in context during readiness. The app creates an audible `USAGE_ALARM` channel, requests audio focus, and never changes device volume. The Data safety form should disclose optional account metadata, user-configured callback traffic, purchase metadata, and optional FCM device routing while describing settings content as end-to-end encrypted.
