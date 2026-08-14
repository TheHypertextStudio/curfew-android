# Google Play declarations

The `gms` artifact is intended for Play distribution. The `plain` artifact is a direct signed APK and contains no Google runtime dependency.

## Exact alarms

Curfew declares `SCHEDULE_EXACT_ALARM`, not `USE_EXACT_ALARM`. The core, user-visible function is waking the user at an explicitly chosen time. The readiness flow sends the user to the system grant screen, checks `canScheduleExactAlarms()` every time it arms, and reacts to permission-state changes by rescheduling persisted work.

## Foreground service

Curfew declares the `mediaPlayback` foreground-service type and its matching permission. It starts playback only from a delivered user-visible alarm clock, not directly from boot. Reboot recovery schedules an immediate alarm-clock transition before restarting playback on supported paths. The ongoing alarm notification describes active playback and opens the alarm status screen.

## Full-screen intent

Curfew requests full-screen intent access only for an actively ringing, time-sensitive alarm. Android 14+ capability is checked before use. If unavailable, the app falls back to a high-priority public lock-screen notification.

## Notifications, audio, and data safety

Notification permission is requested in context during readiness. The app creates an audible `USAGE_ALARM` channel, requests audio focus, and never changes device volume. The Data safety form should disclose optional account metadata, user-configured callback traffic, purchase metadata, and optional FCM device routing while describing settings content as end-to-end encrypted.
