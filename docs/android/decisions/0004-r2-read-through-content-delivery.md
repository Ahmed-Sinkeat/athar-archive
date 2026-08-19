# 0004 — Deliver immutable content from R2 with a read-through cache

**Date:** 2026-08-19
**Status:** accepted

## Context

Git is Athar's canonical authoring store, but it is not an application delivery contract.
Fetching repository files at runtime would couple Android to branch layout, GitHub
availability and raw Markdown parsing. The existing R2 bucket is also a poor public app
origin because it contains unrelated `pages/` and `build-data/` objects alongside content.

Two user actions have different intent. Opening a work means “let me read now”; it does not
mean “keep the whole work forever.” Pressing Download means durable offline retention. A
large-work design that treats both actions as a full permanent download wastes bandwidth and
storage, while a pure network stream gives slow reopen and no useful offline residue.

The framed `.athar` format already supplies independent compressed members, but a
package-only digest cannot authenticate one HTTP Range response. Each frame therefore needs its own
digest below the signed catalog root.

## Decision

Production Android content is served from a dedicated public R2 bucket such as
`athar-app-content` through an Athar-controlled custom domain. The Android app never fetches
GitHub or website pages. Its object layout is:

```text
/app/v2/index.json
/app/v2/catalog/<sha256>.json
/app/v2/tombstones/<sha256>.json
/app/v2/content/<coll>/<id>/<sha256>.athar
/app/v2/content/<coll>/<id>/<sha256>.athar.idx
/app/v2/audio/<audio-id>/<sha256>.<ext>
```

The signed `index.json` is the only mutable stable name and uses `Cache-Control: no-cache`.
All referenced objects are immutable, named by their own full SHA-256 digest and use
`Cache-Control: public, max-age=31536000, immutable`. An explicit custom-domain Cache Rule
caches the artifact paths (including arbitrary `.athar`/`.idx` content types) while the root
revalidates. Publication uploads packages, sidecars and audio first, then the catalog and
tombstones, and replaces the signed index last. Production does not use the
development-only `r2.dev` origin. Cloudflare documents both the
[custom-domain/cache requirement](https://developers.cloudflare.com/r2/buckets/public-buckets/)
and [ranged reads](https://developers.cloudflare.com/r2/api/workers/workers-api-reference/).

Every `.athar.idx` frame entry includes the full SHA-256 digest of its exact compressed byte
range. Opening an unpinned entry fetches and verifies the sidecar, Range-fetches the frame
containing the desired block, verifies that frame before import, and may prefetch one
adjacent frame only while the reader is active. Verified blocks and FTS rows remain as an
LRU-managed read-through cache. Leaving starts no new frame; at most the already-in-flight
frame finishes verification/import. It does not create a pin or continue to a full
background download.

Small single-frame entries may be fetched whole when measurement shows that this is faster
than another ranged request. This optimization changes neither intent nor retention: without
a pin, the imported rows remain evictable and the transient package is discarded.

Download first writes durable pin intent, then completes and verifies the package, imports
all frames, and retains the package in `filesDir/content/` for offline rebuild. Resumable
`.part` files live in `filesDir/transfers/`. Only disposable Range members live in
`cacheDir`, because [Android may remove cache files under storage pressure](https://developer.android.com/training/data-storage/app-specific).

Content availability (`absent | partial | complete`), transfer activity and pin intent are
separate facts. The UI distinguishes “some pages cached”, evictable “cached”, and durable
“Downloaded”, and describes a partial cache honestly at an uncached offline boundary.

## Alternatives rejected

- **Fetch raw Markdown from GitHub:** couples runtime to repository layout and requires a
  second parser on Android; GitHub is source control, not the app's availability boundary.
- **Expose the existing mixed R2 bucket:** makes unrelated page/build objects part of the
  public app-origin blast radius.
- **Use `r2.dev` in production:** it is intended for non-production access and does not give
  the production custom-domain caching path.
- **Download the full package on every open:** simple, but silently spends bandwidth and
  storage without user retention intent.
- **Stream and discard everything:** makes every reopen pay the network cost and provides no
  opportunistic offline reading.
- **Continue downloading after the reader closes:** converts opening into an implicit
  Download and obscures the user's storage choice.
- **Keep resumable downloads in `cacheDir`:** the OS may remove them, so a supposedly durable
  transfer could disappear independently of Athar's state machine.

## Consequences

- R2 bucket/domain provisioning, cache rules, CORS/Range verification and objects-first
  publishing become release prerequisites.
- The sidecar is slightly larger because every frame carries a 32-byte digest.
- The content database must track imported frames, not only whole works, and FTS eviction
  must delete frame blocks atomically.
- A partially cached work is only partly available offline; the reader needs a clear boundary
  state and retry path.
- The app gets fast first text and cheap reopen without weakening signed integrity or making
  ordinary opening synonymous with permanent storage.
