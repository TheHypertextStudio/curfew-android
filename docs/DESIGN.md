# Product and interface design

The Android companion uses stock Material 3 components with a restrained visual vocabulary: strong state hierarchy, warm wake accents, and no ornamental motion. Dynamic color is used when the user enables system support; the Curfew palette remains the fallback. It does not yet adopt the Material 3 Expressive APIs — there is no `MaterialExpressiveTheme`, and no expressive shape or motion scheme — so treat the expressive shape and motion work as outstanding rather than shipped.

## Wake flow

1. Configure a local wake time, selected devices, and optional generic callback.
2. Complete readiness: exact-alarm access, notifications or lock-screen fallback, an audible alarm channel, a real test sound, and limitation acknowledgement.
3. Arm a visible alarm-clock schedule.
4. During ringing, show attempt and condition status plus only the callback-provided opaque action. There is no dismiss or snooze control.
5. On a verified release or a fresh authorized override, release only the named devices immediately. Failed and unavailable checks leave the campaign active.

The pilot cadence is two minutes ringing and one minute quiet. Attempts are unbounded and every ring is at least 30 seconds. Offline, socket, network, process, and reboot failures preserve the active campaign rather than inventing a release.

## Adaptive and accessible behavior

- Compact windows stack configuration, readiness, callback, and account content.
- Medium, expanded, tablet, foldable, and desktop-sized windows use balanced two-column panes.
- Interactive targets are at least 48 dp with meaningful semantics. Headings preserve screen-reader structure.
- Configuration uses saveable state across rotation, folding, sleep, and process restoration.
- The UI uses no essential animation, satisfying reduced-motion expectations by construction.
- Predictive back is enabled, edge-to-edge layout respects scaffold insets, and contrast comes from Material color roles.

The alarm foreground experience deliberately avoids coercive or shame-based language. A miss is recorded as a fact, not a moral judgment.
