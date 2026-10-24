# Sunrise device-network delivery

The phone owns its local audible occurrence. Other devices enforce their own
confirmed participation and require valid authority to release the matching
wake gate. Ordinary bedtime restrictions keep their independent release rules.

## Current safety correction

An active override label alone is insufficient. The phone validates its status,
reason, duration, selected target set, occurrence timing and current time before
persisting a release. Expiry is derived from startsAt plus durationMinutes; the
interval includes its start and excludes its end. Future or expired grants do
not release an occurrence. Both the direct active-grant poll and the overridden
status path use this rule. The protocol remains 0.0.13 and no wire fields change.

The correction is reversible independently. Reverting it restores the previous
unsafe acceptance of future/expired grants and is not recommended. It adds no
storage migration, permissions, production configuration or publication action.

## Remaining delivery

- [ ] Consumer check-in enrollment, acceptance, bounded approval, revocation and recovery.
- [ ] Turnkey device pairing, default future participation and persistent exclusions.
- [ ] Confirmed local application and truthful per-device acknowledgments.
- [ ] Authenticated release delivery, duplication safety and reconnect convergence.
- [ ] Occurrence-scoped offline paired codes with rotation and revocation limits.
- [ ] Installed Android-to-Mac online and partitioned acceptance.

These are required capabilities, not claims established by the bounded-grant
fix. Shared contract additions must be released in curfew-protocols before Sync
and native consumers adopt them. No ordinary consumer flow should require an
endpoint or private-key export; those remain advanced integration controls.

## Acceptance boundaries

Technical local P1 has its separate accepted scope in P1_ACCEPTANCE.md. Explicit
Pixel Doze testing was waived, not passed. Bedtime enforcement, physical UX,
accessibility and production signing remain distinct work. Production activation
and public installer publication remain held. Accountability calls are separate.
