# A code away from bed

The owner enrolls a printed QR code in a place such as the bathroom. The same
code can release each active Sunrise occurrence on this phone without an account,
network connection, operator or signing-key export. A code proves possession,
not distance or wakefulness; a copied image also works. The app must never claim
otherwise.

## Setup and use

Generate a new random code and give the place a name. Save or print it, put it
away from bed, then scan the saved copy to finish enrollment. A pending draft
never makes an alarm ready. Keep the code private. Ordinary setup does not
request an endpoint or export a signing key. The existing HTTPS verifier stays
under Advanced integration.

When an occurrence rings or enters its quiet interval, Scan code opens the
camera. Only a matching enrolled code can satisfy that exact active occurrence.
Cancelling, denying camera access, decoding another image or losing the camera
leaves the alarm active and retains the separate authenticated recovery flow.
No camera frame is stored or uploaded. There is no image-import shortcut.

## Persistence and authority

Each enrollment has a fresh identity and 256-bit random token. The active phone
stores only the token digest; a pending printable draft is encrypted with the
existing Android Keystore store. The local printed format is not a Sync,
account, AI-host or offline paired-device credential. Protocol 0.0.13 is unchanged.

Changing the selected check-in does not rebind a saved or ringing occurrence.
Removing a code revokes its local authority and turns off future alarms bound to
it. It never releases a ringing occurrence. Replacing a code requires another
completed enrollment and explicit application to the saved alarm. A lost code
can be handled through the existing deliberate owner recovery.

Approval commits through the existing transactional alarm store, so duplicate
or late scanner results cannot overwrite another terminal outcome or release a
different morning. Release publication and next-occurrence recovery reuse the
existing local completion/outbox paths. A local success is not an acknowledgment
from another device.

## Implementation and acceptance

- [x] Generated-code enrollment and occurrence-bound approval domain tests.
- [ ] Encrypted persistence, revocation, replacement and crash recovery.
- [ ] Offline camera scanner, permission failure and camera lifecycle.
- [ ] Printable export, complete enrollment and advanced integration navigation.
- [ ] Actual active/quiet release, cancellation, duplicate and lost-code recovery.
- [ ] Responsive rendered workflow and isolated native accessibility checks.

Production activation and public installer publication remain held. Explicit
Pixel Doze testing is owner-waived, not passed. Physical UX, TalkBack, signing,
P1b device networking and bedtime are separate acceptance work.
