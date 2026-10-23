# Local verifier certificate

`verifier-fixture.p12` contains only a public test fixture for the loopback HTTPS acceptance peer. Its password is `fixture-only`. It is never included in production assets or trusted by the production application.

The PKCS12 container uses a SHA-1 integrity MAC and three-key Triple DES wrapping so Android 13's PKCS12 provider can load it. OpenSSL 3's default PBES2/SHA-256 container fails on that provider with `No installed provider supports this key: PKCS12Key`. Repackaging the container does not change the TLS certificate, its signature, or its private key. This compatibility choice applies only to this test file; it does not change callback cryptography or production trust.
