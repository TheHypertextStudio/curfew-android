# Connect a trusted wake verifier

Reader: The owner and operator connecting an existing HTTPS wake verifier.
Action: Provision and test one verifier before enabling a Sunrise alarm.

P1 uses an external service to confirm the owner's check-in. Curfew does not
supply a hosted keyholder portal. The operator must provide an HTTPS endpoint
that implements the pinned callback contract and decides when that particular
wake occurrence has satisfied its condition.

## Provision the connection

1. The owner opens Settings, then Check-in, on the phone. The owner opens the
   connection flow through Connect a service. The owner enters the operator's
   HTTPS service address. Optional details hold the display name and the link
   for opening the service when the alarm rings. The owner saves the connection.
2. The owner chooses Save setup file and saves the private file through Android's
   document picker. The owner transfers that file directly to the trusted
   operator. It contains the generated signing secret. Do not put it in a
   public issue, log, screenshot, or shared source repository.
3. The operator imports that exact CallbackDefinition on the verifier. The
   callback identity and key must match the phone's stored connection. The
   endpoint must accept a direct HTTPS POST with a certificate the phone
   trusts. Curfew rejects redirects. Saving an HTTPS-looking address alone
   does not establish a working connection.
4. The owner returns to Check-in and chooses Test connection. A valid signed
   pending or satisfied response can establish connectivity. This exchange
   uses a new setup-only identity and cannot release a saved alarm. The owner
   must not enable the routine until the connection test succeeds and alarm
   access and sound are ready.
5. The owner saves a controlled one-night alarm. The operator first keeps that
   occurrence pending, then approves it through the real condition. The phone
   must produce alarm audio while pending and stop only after authenticating
   the current approval. The optional action link opens the operator's flow;
   opening it alone does not release the alarm.

The phone supports one selected local schedule. Accounts and another Curfew
device are not prerequisites. Setup changes do not dismiss an occurrence
already underway.

## Implement the existing contract

Use the [callback schema at v0.0.13](https://github.com/TheHypertextStudio/curfew-protocols/blob/v0.0.13/schemas/callback.json).
The definition, challenge, receipt, canonicalization, and key derivation come
from that version. Do not add Android-only fields or reuse an account-login
secret. The schema defines separate request and response HMAC purposes.

The operator must authenticate the challenge before acting on it. Each reply
must echo its campaign and fresh nonce, carry a current observation and bounded
expiry, and use the response signing purpose. The operator returns pending
until the configured authority confirms that same wake occurrence. Approval
must not become an unconditional response for every future morning. A service
that can sign satisfied receipts has authority to release this phone's alarm.

The phone rejects malformed, unauthenticated, stale, future-dated, expired,
replayed, and wrong-occurrence receipts. Keep the phone and verifier clocks
correct. A backward phone-clock change can prevent a fresh challenge while
its wall time predates the original occurrence start. The alarm continues;
correcting the clock or using deliberate recovery remains separate from
satisfying the check-in. Do not weaken receipt freshness to hide that state.

## Handle failure and recovery

An unreachable service, lost internet, or failed test supplies no approval.
The phone continues the saved two-minute ring and one-minute quiet cadence.
The owner can use [deliberate recovery](OWNER_RECOVERY.md) with a reason and
fresh Android screen-lock confirmation. Curfew records that as an override,
not a successful check-in. Before-first-unlock playback after a reboot is
unsupported; the owner must unlock the phone after restarting.

If the private setup file is exposed, stop using that connection and provision
a fresh one. Remove temporary exports after the operator has securely stored
the definition. Keep a deliberate recovery path available while replacing a
connection. Do not delete the app's data to repair a single failed connection.

## Verification boundary

The installed SunriseReleaseJourneyInstrumentedTest uses a loopback TLS peer
and the real phone scheduler, callback authentication, and alarm audio. It
checks pending, forged, malformed, wrong-occurrence, expired, future-dated,
replayed, and HTTP503 responses before valid release and a harmless retry.
Each invalid approval leaves audio active. Its test-only trust configuration
does not alter production TLS. Host tests also cover authentication vectors
and receipt freshness.

Those checks prove the controlled integration, not an external operator's
production deployment or physical speaker volume. The owner and operator must
complete step 5 with their selected service. Record physical-device results
separately in the P1 acceptance record.

## Returning to setup

Set up later retains the prepared wake time without enabling the alarm. A saved
connection stays unverified until its connection test succeeds. Saving or
exporting its setup file does not establish readiness.

When a saved alarm exists, replacing the selected connection does not rename
or rebind that alarm. After verifying the replacement, choose Use for alarm
and explicitly save the alarm to apply it. The active occurrence keeps its
original authenticated release binding.
