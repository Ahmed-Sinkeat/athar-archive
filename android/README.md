# android/ — أهل الأثر companion app

**Plan of record:** [`docs/main-plan.md`](../docs/main-plan.md) — selected baseline, revised
19 Aug 2026.
**M0 results:** [`docs/android/m0-results.md`](../docs/android/m0-results.md).
**M5 results:** [`docs/android/m5-results.md`](../docs/android/m5-results.md).
**M6 results:** [`docs/android/m6-results.md`](../docs/android/m6-results.md).

**Product boundary:** Athar is a general Arabic scholarly-library app. Tafsir and Hadith
works use the ordinary book pipeline; there is no specialist Qurʾan, tafsir-fragment, or
Hadith subsystem. See `main-plan.md` §0 and D13.

Status: **M6 downloads/cache implementation complete; final device-instrumentation acceptance
is waiting only for HyperOS “Install via USB” permission (20 Aug 2026).** The dedicated
`athar-app-content` R2 bucket serves the signed four-entry slice at
`https://app-content.arthurarchive.com/app/v2/`. Books, articles, poetry, and their native
readers now observe Room Flows; search, annotations, and Media3 playback remain M7–M9 work.
`core/data` verifies the signed root and complete hash chain, owns separate
user/content Room databases, performs exact verified HTTP Range reads, imports frames
transactionally, evicts only unpinned cache data, and rebuilds retained packages offline.
Explicit downloads are WorkManager-backed, resumable and durably retained; cache limits,
low-storage eviction and completion notifications are wired. The real staging book passed
download, unpin/re-download, process-death recovery and a cold fully-offline open on the
Android 15 phone. See the M6 results for the one remaining test-runner permission gate.

## Build and test

The Gradle 9.6.1 wrapper is committed; no system Gradle installation is required.

Then verify the skeleton, cheapest check first:

```sh
./gradlew :core:athar-text:test    # 203 golden vectors — pure JVM, no SDK needed
./gradlew :core:data:test          # signed root, hash chain and framed-package vectors
./gradlew projects                 # module graph resolves
./gradlew :app:assembleDebug       # needs the Android SDK + ANDROID_HOME
```

Debug builds trust the checked-in **public** M5 staging key and use the staging URL above;
the private key is never stored in Git. Release and benchmark builds stay unconfigured unless
CI supplies all three values together:

```sh
ATHAR_CONTENT_BASE_URL=https://content.example.com/app/v2/ \
ATHAR_CONTENT_SIGNING_KEY_ID=release-key-id \
ATHAR_CONTENT_PUBLIC_KEY_DER_BASE64=... \
./gradlew :app:assembleRelease
```

The base URL is the contract root, not merely the bucket origin, and must be HTTPS with a
trailing `/app/v2/`; Gradle rejects partial or malformed configurations.

The dependency set is resolved and verified by the M0 build. Lifecycle stays at
2.10.0 because 2.11.0 requires compileSdk 37 while Athar currently compiles with 36.

## Layout

```
android/
├── settings.gradle.kts      module graph
├── build.gradle.kts         plugin declarations only
├── gradle/libs.versions.toml   ← the ONLY place versions live
├── build-logic/             convention plugins (compileSdk, minSdk, Compose)
├── core/
│   ├── athar-text/          pure JVM: normalizer, normalizeWithMap, query builder
│   └── data/                signed sync, Room 3 databases, frame import/cache/rebuild
├── app/                     production shell — Application, Activity, backup rules
├── m0/                      temporary certification harness
└── benchmark/               macrobenchmarks; targets :m0 until the real M5 reader
```

Modules are created **when a stable boundary needs them**, never as empty scaffolding. M2–M3
may keep fixture-driven final UI in `:app` packages while the design settles. Extract
`core/ui` and stable feature boundaries after that UI is real; `core/data` arrives with M5,
search behavior with M7, annotations with M8, and the Media3 audio implementation with M9.

Downloads get **no module of their own**: the logic belongs in `core/data`, the UI
starts inside `feature/library`. Split it out only if that screen actually grows.

## Dependency direction — enforced by review until a feature module exists

```
app → feature/* → core/ui → core/data → core/athar-text
```

Forbidden, always:

| Not allowed | Why |
|---|---|
| `feature/reader → feature/search` | features never call features — go through `core/data` |
| `feature/library → feature/audio` | same |
| `core/data → feature/*` | core never depends upward |
| `core/athar-text → Android` | it must stay pure JVM: millisecond tests, no emulator |

`core/athar-text` staying Android-free is the load-bearing one. It is why the
highest-risk logic can be tested in milliseconds, and it is the module that would
become `athar-core` if Rust or iOS ever justified it — obtained without designing
for it.

## Android does not produce content

`.athar` packages, sidecar indexes, the catalog, attached media metadata and the signed root
envelope are **outputs of the TypeScript content pipeline** (`scripts/`), uploaded to the
dedicated app-content R2 bucket and consumed here through its custom domain.

```
canonical Markdown in Git
  → build-only TypeScript AST parser
  → content-addressed .athar + .idx + catalog + signed index
  → dedicated R2 custom domain
  → Android
```

Android never fetches GitHub, parses Markdown or generates a package. TypeScript is absent
from the APK and the reading path, so it cannot affect scrolling or text-layout smoothness.
It preserves visible Unicode and inline semantics at build time; unsupported Markdown fails
the content build instead of disappearing. Android normalizes already-parsed block text for
local FTS, pages Room rows and renders native Compose text (main-plan.md §6, D10).

The first real-device content slice is deliberately short and varied: one published article,
`nawaqid-al-islam` as the book, `swteat-866` as the masʾala, and
`qasidat-madh-al-sunnah-wa-ittiba-al-salaf` with its existing 5:41 Opus audio and 29 timing
cues. Exact source paths and acceptance checks are in `main-plan.md` §16.

Generate that M4 slice from the repository root with `pnpm app:gen:slice`. Immutable output
goes to `dist/app-content/app/v2/`; persistent block identities go to
`content-ids/<collection>/<id>.ids.json`. An unsigned local run emits
`index.payload.json` only. The protected signing job sets
`ATHAR_CONTENT_SIGNING_KEY_FILE` and `ATHAR_CONTENT_SIGNING_KEY_ID` to emit `index.json`.

`pnpm app:publish` is deliberately separate. It requires the dedicated bucket name and
bucket-scoped `ATHAR_APP_R2_ACCESS_KEY_ID` / `ATHAR_APP_R2_SECRET_ACCESS_KEY`, plus
`ATHAR_CONTENT_SIGNING_PUBLIC_KEY_FILE` and the signing key ID. It verifies the signature
and complete artifact hash chain, uploads immutable objects first, then publishes
`index.json` last. `--validate-only` performs the same local signature/hash-chain validation
without R2 credentials. The M5 staging domain is attached with immutable caching on addressed
objects and `no-cache` on `index.json`; its exact Range response was verified as HTTP 206.

Opening without Download uses a verified read-through cache: fetch the needed frame (or a
tiny package whole), import it into Room, prefetch at most one adjacent frame while reading,
and stop when the reader closes. Download is a different action: it records pin intent,
completes the package and retains it for full offline use and rebuild. See D16 and ADR 0004.

## M0

One harness app, `:m0`, carries every prototype that needs a device: R1 selection,
R2/R2b FTS, R3 import, R7 end-to-end. R4 is Node/TypeScript and R5 is pure JVM —
neither needs Android at all, which is why they run first.

`:m0` remains only until its compact/release R7 workload has migrated to the production
reader and the outstanding API-tier certification is recorded. It is then deleted. Anything
worth keeping moves to `:benchmark` or into a module's own tests; nothing graduates to
production by staying put.

⚠️ **Editing Arabic character classes:** always write codepoints as `\u` escapes,
never paste literals — bidi display reorders them silently (this bit us three
times while writing `ArNormalize.kt`).
