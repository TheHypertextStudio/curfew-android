# Android app ownership

`MainActivity` translates Android intents and dispatches feature commands. The
account model exchanges an OAuth callback, the alarm model presents an
occurrence, and navigation handles a protection entry. No ViewModel parses an
Intent or selects an app destination. The OAuth redirect is shared by the
parser, browser enrollment, client registration, and token exchange; the
manifest uses a separate scheme for the isolated acceptance build.

`CurfewRoot` resolves its shared models with Hilt. Account sign-in and ringing
presentation have Activity lifetime because an external callback or alarm can
arrive from any screen. The setup model shares the schedule, selected check-in,
and readiness across the setup journey. It does not own navigation, check-in
form state, sound playback, or protection state.

`CurfewApp` is the adaptive navigation shell. The setup journey keeps only the
draft and progress needed across its screens. Navigation 3 gives check-in,
settings, protection, and the readiness sound check their own entry stores.
Each entry collects what it renders. The check-in editor and protection banner
keep their own UI state; a system picker saves only the check-in ID across
recreation and reloads the setup file contents after the user chooses a
destination.

Android window flags and result launchers are effects at the UI boundary.
Hilt binds narrow interfaces for readiness, clock readings, sound output,
account exchange, and protection control. A sound check awaits its durable
confirmation and registers its playback controller with `addCloseable`.
Leaving the readiness screen also stops an unconfirmed preview. Notification
channel reads have no setup side effects; channel creation belongs to alarm
readiness or actual playback.

Place check-in setup and alarm approval have separate interfaces. Their
repository shares the schedule lock with Sunrise and delegates polling,
completion, and wake publication to an Android adapter. The terminal local
write still precedes those side effects.

The next data boundary is the platform work still orchestrated by
`SunriseScheduler`: clock scheduling, callback polling, and wake sync. Extract
one consumer-facing port at a time and reuse the existing scheduling and
recovery tests. Keep a single owner for the persisted schedule and its mutation
lock. The remaining older screen copy can migrate to resources as those
screens change.
