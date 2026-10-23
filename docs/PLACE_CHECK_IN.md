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
- [x] Encrypted draft/digest persistence, explicit revocation and replacement source.
- [ ] Isolated native persistence/revocation and terminal-write recovery acceptance.
- [x] Offline QR codec, camera permission handling and lifecycle-owned scanner source.
- [ ] Actual scanner delivery and permission/lifecycle acceptance.
- [x] PNG export, native print adapter, enrollment and advanced integration navigation source.
- [ ] Actual native enrollment/export and advanced navigation acceptance.
- [ ] Actual active/quiet release, cancellation, duplicate and lost-code recovery.
- [ ] Responsive rendered workflow and isolated native accessibility checks.

Production activation and public installer publication remain held. Explicit
Pixel Doze testing is owner-waived, not passed. Physical UX, TalkBack, signing,
P1b device networking and bedtime are separate acceptance work.

## Verification and rollback

Host regression tests cover matching and mismatched codes, malformed or damaged
enrollments, scheduled/terminal rejection, occurrence identity, quiet release,
QR image decoding, stale selection removal, and revocation ordering. Revocation
shares the alarm controller lock: it disables a matching future schedule before
removing the credential, preserves an active gate, and does not touch another
condition. CameraX and ZXing Core work in both Plain and GMS distributions; no
Google Play Services scanner or remote decoder is required.

The native repository fixtures exercise the real encrypted store and Room
transaction without starting alarm audio or arming a live occurrence. The
existing isolated API matrix also exercises ordinary first-use navigation.
The responsive capture driver adds intro, generated code, ready, error and
camera-denied scenes to every existing emulated window/theme profile. Those
fixtures are presentation evidence, not physical camera or TalkBack acceptance.

An unavailable or damaged code fails closed and offers removal and fresh setup.
If completing the next alarm fails after the current terminal write, the app
reports that tomorrow needs attention and schedules the existing repair path.
Rollback requires turning off schedules bound to a local code before returning
to an older app; older builds cannot verify this new local condition. No remote
schema, server deployment, production activation or installer release is needed.


Rendered error recovery keeps its message and Save/Print retry actions in one
scroll target. That target is revealed when an export error arrives so enlarged
text does not leave the retry buttons offscreen. The native matrix asserts both
the error and Save code remain visible.


Printing validates the chosen paper's usable area before opening a PDF page.
Every opened page is finished even if drawing fails, then the document closes.
Unsupported small media reports a setup failure with image export as an
alternative; it cannot leave the PDF renderer in an unfinished-page state.


Setup presents Save, Print, Scan and Cancel together without an on-screen QR
preview. The code belongs in the exported image or printed page, not in the
phone's setup instructions. Copy uses direct action labels and a concise privacy
reminder. Cancel returns to the method choice; the advanced-service link remains
there rather than competing with an in-progress code setup. Native captures
assert that the preview is absent and the landscape actions stay visible.
