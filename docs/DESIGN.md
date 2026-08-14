# Product and interface design

The Android companion uses a restrained Material 3 Expressive vocabulary: strong state hierarchy, warm wake accents, generous shapes, and no ornamental motion. Dynamic color is used when the user enables system support; the Curfew palette remains the fallback.

## Wake flow

1. Configure a local wake time, finite recurrence, selected devices, and optional generic callback.
2. Complete readiness: exact-alarm access, notifications or lock-screen fallback, an audible alarm channel, a real test sound, and limitation acknowledgement.
3. Arm a visible alarm-clock schedule.
4. During ringing, show attempt and condition status plus only the callback-provided opaque action. There is no dismiss or snooze control.
5. On success, release selected devices immediately. On exhaustion, silence, release, record a missed wake, and show factual subdued status.

Campaign duration is exact: `attempts × ring + (attempts − 1) × quiet`, capped at two hours. Attempts are capped at 24 and every ring is at least 30 seconds. Devices can independently derive the same deadline for offline release.

## Adaptive and accessible behavior

- Compact windows stack configuration, readiness, callback, and account content.
- Medium, expanded, tablet, foldable, and desktop-sized windows use balanced two-column panes.
- Interactive targets are at least 48 dp with meaningful semantics. Headings preserve screen-reader structure.
- Configuration uses saveable state across rotation, folding, sleep, and process restoration.
- The UI uses no essential animation, satisfying reduced-motion expectations by construction.
- Predictive back is enabled, edge-to-edge layout respects scaffold insets, and contrast comes from Material color roles.

The alarm foreground experience deliberately avoids coercive or shame-based language. A miss is recorded as a fact, not a moral judgment.
