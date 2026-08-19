# 0003 — Authenticate the content root with a signed envelope

**Date:** 2026-08-19
**Status:** accepted

## Context

Android updates its catalog and readable-content packages without an app release. The existing SHA-256
chain detects corruption after the app has trusted `index.json`, but it does not authenticate
that root: anyone able to replace both the root and the referenced objects can publish a
self-consistent malicious archive.

TLS authenticates the current endpoint in transit. It does not protect against a compromised
storage credential, deployment path or origin that serves a replacement root. Athar's text is
the product, so content provenance is a release requirement rather than an optional transport
detail.

The app supports API 26. Android exposes Ed25519 through the platform `Signature` provider
only from API 33, so choosing it would add a cryptographic provider solely for verification.
`SHA256withRSA` is available across Athar's supported API range.

## Decision

`app/v2/index.json` is one signed envelope:

```json
{
  "envelope": 1,
  "payload": "<base64url exact UTF-8 index bytes>",
  "signatures": [
    { "keyId": "athar-content-rsa-2026-01", "alg": "SHA256withRSA",
      "value": "<base64url RSA-3072 signature over payload bytes>" }
  ]
}
```

The app embeds trusted public keys and accepts the payload only when at least one listed
signature verifies. It verifies before parsing or acting on the inner index. Envelope and
decoded payload are independently limited to 64 KiB.

The signed payload contains the hashes and content-addressed paths for
`catalog/<sha256>.json` and `tombstones/<sha256>.json`; catalog entries contain package,
sidecar and audio hashes, while each verified sidecar contains its frame hashes. This
produces one authenticated chain from the embedded app key to every imported record or
played media object. Every artifact hash in that chain
is a full 32-byte SHA-256 digest; the retired pipeline's 16-hex-character truncation is not a
security boundary and is not accepted by the v2 client.

The private signing key is never committed or shipped. It lives in a protected signing job.
Key rotation is an overlap release: ship an app trusting old and new keys, dual-sign the
envelope, then retire the old signature only after the supported client window has moved.

On verification failure the app keeps serving its last verified local data, performs no
dependent fetch or destructive recovery, and exposes a non-blocking sync-integrity error.

## Alternatives rejected

- **Hashes only:** detect accidental corruption but authenticate nothing above the hash root.
- **TLS only:** protects transport to the endpoint, not a compromised publisher credential or
  origin serving a coherent replacement tree.
- **Ed25519 through the platform provider:** compact and attractive, but unavailable before
  API 33 while Athar supports API 26.
- **Bundle a cryptographic provider for Ed25519:** adds code, update surface and APK weight for
  one verification operation when the platform already supplies an adequate algorithm.
- **Detached `index.sig` request:** simple, but turns every unchanged daily check into two
  requests. The envelope preserves the one-request sync property.

## Consequences

- The root index grows slightly because the payload is base64url-encoded and carries a
  signature. At roughly 3 KiB compressed this is immaterial.
- Signing becomes a required content-pipeline release step and needs protected key handling,
  tamper tests and a documented recovery/rotation procedure.
- An old app that trusts none of the active keys stops syncing but remains fully usable from
  its last verified local state.
- A valid signature proves publication by a trusted key; it does not by itself prevent replay
  of an older, previously signed generation. Rollback resistance can add a monotonic signed
  sequence later if the threat model requires it.
