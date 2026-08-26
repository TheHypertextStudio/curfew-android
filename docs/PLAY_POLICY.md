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
when the wake condition the user configured reports a verified release or when
a fresh account-authorized override targets the device.
It does not end because the user tapped a button while still in bed, which is
the failure this product exists to remove.

The review submission must describe the release-only behavior without claiming
that every campaign terminates on its own:

- **The user chooses the wake condition in advance.** Curfew shows the ring and
  quiet cadence and the verified release path before arming.
- **The app states its limits before arming.** Power-off, force-stop,
  uninstall, revoked permissions, and some manufacturer battery policies can
  prevent delivery. Curfew does not claim to defeat them.
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
