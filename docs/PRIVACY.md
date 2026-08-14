# Android privacy notes

Curfew works without an account and without Google services. In that mode, schedules, alarm history, callback configuration, and licenses remain on the device unless the user explicitly sends a callback request.

With an optional account, the service stores identity and entitlement metadata, device public keys and revocation state, ciphertext headers and timing, routing handles, OAuth grants, and remote-override audits. Wake settings and callback definitions are end-to-end encrypted. FCM, when present in the `gms` distribution, is an acceleration hint and carries no authoritative setting plaintext.

Generic callbacks disclose the campaign identifiers, timestamps, nonce, and authenticated status fields defined by protocol v2 to the user-configured HTTPS endpoint. The optional action URL is opened as an opaque user action and is not interpreted as a product integration.

Removing the app erases its Keystore keys and makes remaining encrypted local blobs unreadable. Account deletion/export is handled through [the Curfew account portal](https://curfew.hypertext.studio/account).
