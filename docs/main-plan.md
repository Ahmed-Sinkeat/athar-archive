# Athar Android — Main Plan

**Status: selected architecture baseline; scope and order revised 19 Aug 2026.** This is the
plan of record. M0 selected the architecture choices below, but the explicit API 26,
production-R7 and backup gates still decide whether those choices are certified for release.
The 16 Aug revision narrowed the product boundary; the 18 Aug revision moved the UI vertical
slice first; the 19 Aug revision clarified UI-first integration, cross-database ownership,
background-transfer policy, signed content roots, content-addressed R2 delivery, read-through
caching, lossless semantic parsing and exact-phrase candidate paging.

**What "selected" covers.** Product constraints in §0 are fixed. Architecture choices are
the default implementation and are not reopened for fashion or preference, but a failed
certification gate or a bounded contradictory prototype is sufficient evidence to revise
them. Scope may always shrink without a technical contradiction.

Corpus measurements were taken from this repository on 12 Aug 2026. M0 prototype
measurements were recorded on 16 Aug 2026 and are cited where they drive a decision. Where a
figure is an estimate rather than a measurement, it says so.

---

## 0 · Fixed product constraints and selected platform baseline

Native Android · Kotlin + Jetpack Compose · minSdk 26 · phone + tablet · offline-first ·
reading-first, aimed at serious Arabic students and researchers · fully standalone from the
website **at runtime**, while remaining a client of the same canonical content system · no
React/RN/Flutter/Capacitor/app-wide WebView · no accounts in v1 · anonymous use always
possible · same repository as `athar-archive` · Google Play + direct APK · static content
delivery only · D1 stays web-only · **the app must never depend on the network to start**.

**Product boundary:** one generic document model and one generic reading experience. Tafsir
and Hadith works may be distributed as ordinary books and receive exactly the standard book
features. Qurʾanic and Hadith quotations inside any book remain ordinary authored text. The
app has no surah/ayah database, mushaf or recitation UI, verse-linked tafsir index, canonical
Hadith-record schema, isnad/matn parser, grading system, or specialist search/navigation for
any of them. Those are separate products, not deferred Athar modules.

**v1 scope:** full catalog metadata; browse by author, topic, century and work type; personal
library status and collections; native reader; book downloads; offline full-text search with
filters; bookmarks, highlights and notes in a central notebook; reading history and position;
source-aware copy/share and local export; audio playback and download for recordings already
attached to catalog entries; download-complete notifications.

**Deferred:** annotation overlays, editorial author biographies beyond catalog metadata,
iOS, Rust.

**Platform relationship:** the website and Android app are different platform clients, not
different content products. Canonical Markdown + Zod metadata in Git remains the source of
truth; the TypeScript build pipeline emits the platform artifacts. Entity identity, Arabic
normalization, query semantics and their golden corpora must agree wherever both clients use
them. Astro/D1 and Compose/local SQLite may differ because their runtimes differ. Adding or
editing a readable entry must continue to update both products without an Android release,
and the app
must never call the website at runtime merely to read already-downloaded content.

**Technology-review guardrail:** a newer engine does not by itself reopen M0. Rust/UniFFI,
Tantivy, a prebuilt per-book database, or an immutable runtime `.athar` store enters the v1
plan only after a bounded prototype demonstrates a technical contradiction in the selected
stack—for example, the compact-FTS production R7 or API 26 import cannot meet §17 after normal
optimization. A shared core must replace duplicated production rules across the build and
client boundaries rather than add a third implementation. Experimental storage or search
features are excluded from v1.

### 0.1 · Phase 0 findings incorporated

M0 changed implementation details, not the product or native-Android direction. The complete
numbers and device limitations are in [`android/m0-results.md`](android/m0-results.md).

| Outcome | Finding | Plan consequence |
|---|---|---|
| **Changed** | Regular Room-managed FTS measured 222.4% of raw text; compact contentless `detail=none` measured 35.2% | Room owns relational data, while raw compact FTS5 DDL owns block search. No prefix index; exact phrases use candidate retrieval plus normalized-source verification. |
| **Changed** | A chapter-heading rename preserved 0% of derived block IDs | `blockId` is a persistent random 128-bit build-side ID maintained by a per-readable-entity sidecar; Android never derives it. |
| **Changed** | Compose exposes no public stable-ID/UTF-16 selection endpoints | Normal reading remains a Compose `LazyColumn`; selection/annotation capture uses a bounded native selectable `TextView` surface embedded in Compose. |
| **Selected** | 2,000-block frames gave 12.73 MiB for the largest package with negligible loss versus 4,000-block frames | Use 2,000 blocks per gzip member and split compressed packages at 25 MiB. |
| **Confirmed** | Navigation state, per-book isolation, process-death restoration and ≥600 dp two-pane behavior worked | Keep Navigation 3; Nav2 fallback is not needed. |
| **Rejected baseline** | Full-detail R7: 479.6 ms first jump, 594 dropped intervals and 378.1 MiB peak reading RSS | Never treat the prototype as production performance. The harness also ran a debug build and an unbounded `PagingConfig` (no `maxSize`), so loaded pages were never dropped — diagnose that before attributing the memory number to the architecture. Integrate compact FTS, then profile a release reader before claiming §17 budgets. |
| **Not yet certified** | No API 26 / 2 GB AVD; multi-tier `bmgr` run unavailable | API 26 performance and API 28–30/31+ backup behavior remain explicit certification gates, not assumed passes. |

---

## 1 · Findings that drive the architecture

Measured before designing. Several contradict reasonable assumptions.

| Finding | Measured | Consequence |
|---|---|---|
| Printed pages already inline | 9,175 in a 60-book sample | `<hr class="page-sep" data-page="N" data-vol="V" />`. Anchors need preserving, not inventing. |
| Heading spine universal | 397 / 400 books | `##` is a reliable semantic chapter boundary. |
| Footnotes common | 115 / 400 (29%) | `[^fn1]` + `[^fn1]: body`. First-class node. |
| Raw HTML is narrow | only `<hr>` and `<sup>` | No HTML parser needed. Two tags, both meaningful. |
| No tables, images, or math | 0 / 400 | Three renderer subsystems cut from v1. |
| Largest single book | 82 MB | Forces streaming import and virtualised reading. |
| Audio is short-form | 854 Q&A / 103 book / 14 poem | Player is built around entry-attached recordings. |
| Verse separator inconsistent, but not rare | **59 / 108** poems mark the caesura: 8 with ` --- `, 51 with `…` (U+2026) in numbered verse lines, no overlap (re-measured 18 Aug 2026) | Hemistich splitting works for over half the corpus. The parser reads both conventions. |
| Per-file hashes already exist | sha256-16, sorted, deterministic | The deterministic hashing discipline is reusable; authenticated v2 artifacts upgrade to full SHA-256. |
| Site search coverage | 186,372 docs; 922/1,239 books | The app will exceed the website's search from day one. |

### 1.1 The finding that decides packaging

`src/lib/strip-md.ts` runs `normalizeArabic()` as its last step, so the `text` field
currently shipped per chapter is **already de-vocalised**, and all HTML — including
`<hr data-page>` — is stripped out of it.

This breaks two requirements. Snippets built from `text` would render Arabic without
tashkeel, against the fidelity priority. And `text` is one flat string per chapter with no
block boundaries, so "tap result → exact paragraph" has nothing to anchor to.

Both are fixed the same way: the build parser emits semantic blocks carrying original
vocalised text; the app derives normalised search text per block on device. The shipped
`text` field then becomes dead weight — roughly half the payload.

---

## 2 · Core decisions

| # | Decision |
|---|---|
| **D1** | **Room 3.0.x** (`androidx.room3`) with `BundledSQLiteDriver` for relational schema and transactions. Block search uses raw compact FTS5 DDL (`content=''`, `contentless_delete=1`, `detail=none`, no prefix index) because Room `@Fts5` cannot express the M0/R2b winner. Bundling keeps tokenizing and `bm25()` identical from API 26 to 36. See ADR 0002. |
| **D2** | **One streamed package per readable entity** (`book`, `article`, `question`/masʾala, `poem`), replacing the 128,466-file fan-out. The package is a transport format; SQLite is the Android runtime format. |
| **D3** | **Drop the shipped `text` field.** Required for correctness (§1.1); halves the payload as a side effect. |
| **D4** | **Two databases, one owner per fact.** `athar_user.db` is sacred and solely owns pin/collection/annotation intent; `athar_content.db` is disposable and owns only catalog, bytes and import availability. Cross-file work is idempotent and reconciled, never described as one Room transaction. |
| **D5** | **Persistent build-side block identity** with a recovery ladder — random 128-bit stable ID, 64-bit content fingerprint and ordinal hint, kept strictly separate. A per-entity ID sidecar survives ordinary source edits (ADR 0001). |
| **D6** | **Pinned entries retain their complete `.athar` package**, so a content-DB rebuild is fully offline. Unpinned package/range files are deleted after verified import; their imported blocks remain an evictable cache. |
| **D7** | **Framed gzip + sidecar index.** Each frame is an independently importable gzip member and carries a full SHA-256 digest in the authenticated sidecar, so a Range response can be verified without first downloading the complete package. |
| **D8** | **Annotations are ranges**, not paragraph bookmarks: start/end block + UTF-16 offsets, multi-block native. |
| **D9** | **One immutable catalog document per generation, fetched whole.** Measured 18 Aug 2026: 6,916 catalog entries ≈ 400 KB gzipped in total, so hash-bucketed shards would have saved ~350 KB per change event in exchange for a fixed bucket count that is a schema bump to alter. Superseded before implementation. |
| **D10** | **Build-time parsing is canonical and lossless at the display-semantic layer.** Android needs no Markdown parser and no `stripMd()` — only `ArNormalize`. Unsupported Markdown fails the content build instead of being silently removed. |
| **D11** | **Navigation 3.** Back-stack-as-state suits an adaptive two-pane layout; Nav2 remains a drop-in fallback. |
| **D12** | **Cloud backup disabled for private data**, with device-to-device transfer preserved where the platform supports it (§13). |
| **D13** | **One generic book contract.** The Android pipeline never emits Qurʾan verses, tafsir fragments, or Hadith records as specialist entities. Tafsir and Hadith works enter only through the same book package, block model, reader and FTS path as every other work. |
| **D14** | **The content root is signed.** `app/v2/index.json` is a signed one-request envelope. Android verifies it against an app-embedded trusted key before accepting its catalog, tombstone or package hashes; ordinary SHA hashes remain the corruption chain below that authenticated root. See ADR 0003. |
| **D15** | **Runtime content comes from a dedicated public R2 bucket through a custom domain.** Git/GitHub is canonical authoring and CI input only; the Android app never fetches it. All objects except the signed root are immutable and content-addressed. See ADR 0004. |
| **D16** | **Open is read-through; Download is durable retention.** Opening an unpinned entry fetches and verifies only the needed frame (or the whole object when measured cheaper), keeps imported blocks as LRU cache and stops adjacent prefetch when the reader closes. Download records pin intent first, completes the package and retains it. See ADR 0004. |

---

## 3 · Repository and module tree

Seven modules. Split only where there is a different reason to change, a real reuse case, or
a build-time win.

```
athar-archive/
├─ src/, scripts/, .github/          existing site + content pipeline
└─ android/
   ├─ settings.gradle.kts
   ├─ build-logic/                   convention plugins
   ├─ core/
   │  ├─ athar-text/     EXISTS. Pure JVM, zero Android deps.
   │  │                  ArNormalize.kt + normalizeWithMap + query builder.
   │  │                  Golden-vector tested against TypeScript.
   │  ├─ data/           Room (both DBs), package importer, R2 client,
   │  │                  sync + download orchestration, repositories.
   │  └─ ui/             theme tokens, typography, shared composables.
   ├─ feature/
   │  ├─ library/  ├─ reader/  ├─ search/  └─ audio/
   └─ app/                 navigation host, DI wiring, manifest
```

`athar-text` is pure JVM so the highest-risk logic runs in millisecond unit tests without an
emulator — and it is the module that would become `athar-core` if Rust or iOS ever justified
it, obtained without designing for it.

`data` is deliberately **one** module. Database, network and download share transactions and
failure modes; separating them buys interface ceremony and costs cross-module refactors.

```
app → feature/* → core/ui → core/data → core/athar-text
feature modules MUST NOT depend on each other
core/athar-text MUST NOT depend on Android
```

---

## 4 · The `app/v2` content contract

`app/v2` is deliberately a catalog-and-readable-content contract, not a canonical
religious-text API. Books, articles, questions/masāʾil and poems use the same transport and
block vocabulary; collection-specific presentation is metadata and UI, not another parser.
It does not consume the site's Qurʾan/tafsir-fragment indexes or produce a Hadith record
dataset. A tafsir or Hadith collection is eligible only as a normal catalogued work with a
normal `.athar` package; no specialist semantics leak into the Android schema.

The prefix stays `app/v2` even though `app/v1` was retired without a shipped client. The old
128,466 objects may still be in R2 until the manual `r2-cleanup` workflow has been run and
verified; reusing a possibly-live prefix to save one character is not worth the collision.

### 4.1 Signed root index — the only file fetched on a sync check

**Runtime origin and object layout.** Production uses a dedicated public bucket such as
`athar-app-content`, attached to an Athar-controlled custom domain. The existing mixed bucket
is not made public because it also contains unrelated `pages/` and `build-data/` objects.
The app knows only the custom-domain base URL and the signed paths below; GitHub is never a
runtime fallback.

```text
/app/v2/index.json
/app/v2/catalog/<catalog-sha256>.json
/app/v2/tombstones/<tombstones-sha256>.json
/app/v2/content/<coll>/<id>/<package-sha256>.athar
/app/v2/content/<coll>/<id>/<index-sha256>.athar.idx
/app/v2/audio/<audio-id>/<audio-sha256>.<ext>
```

`index.json` is the only stable mutable name and uses `Cache-Control: no-cache`. Every
referenced object is immutable, its filename is its own full digest, and uses
`Cache-Control: public, max-age=31536000, immutable`. The custom domain has an explicit Cache
Rule for these paths so arbitrary `.athar`/`.idx` content types are cached intentionally,
while `index.json` revalidates. A release uploads packages, sidecars and audio first, then
catalog/tombstones, and publishes the signed root **last**. A client can therefore observe either the old complete
generation or the new complete generation, never a root that points at objects not yet
published. Production does not use the rate-limited `r2.dev` endpoint. See Cloudflare's
[public-bucket/custom-domain guidance](https://developers.cloudflare.com/r2/buckets/public-buckets/)
and [R2 cache guidance](https://developers.cloudflare.com/cache/interaction-cloudflare-products/r2/).

```json
GET /app/v2/index.json                      // signed envelope, ~3 KB gzipped
{
  "envelope": 1,
  "payload": "<base64url of the exact UTF-8 payload bytes>",
  "signatures": [
    { "keyId": "athar-content-rsa-2026-01", "alg": "SHA256withRSA",
      "value": "<base64url signature over the decoded payload bytes>" }
  ]
}
```

The decoded, signed payload is the index document:

```json
{
  "schema": 2,
  "generationId": "a7f3c9d1",
  "catalog":    { "path": "catalog/<sha256>.json",    "hash": "<sha256>", "size": 409600 },
  "tombstones": { "path": "tombstones/<sha256>.json", "hash": "<sha256>" },
  "minAppSchema": 2
}
```

The envelope and decoded payload are each capped at 64 KiB. Android decodes the payload,
accepts it only if at least one signature matches an app-embedded trusted public key, and
only then parses or acts on the index. An unknown key, unsupported algorithm, malformed
envelope or failed signature leaves all local data untouched and reports a non-blocking sync
integrity error. It must not trigger catalog/package downloads or destructive recovery.

The v1 signing algorithm is RSA-3072 with `SHA256withRSA`: Android's platform provider
supports verification below minSdk 26, so authenticity does not add a cryptographic runtime
dependency. Ed25519 is not a platform algorithm until API 33. The private key never enters
Git or the APK; a protected content-signing job holds it. Rotation uses an overlap release:
the envelope temporarily carries signatures from both old and new keys while the app embeds
both public keys. See ADR 0003.

This authenticates the existing hash chain: signed index payload → catalog/tombstone hashes
→ package/index hashes → framed records. Hashes alone detect accidental corruption but
cannot prove who published a replacement root.

Every hash carried by that authenticated v2 chain is the full 32-byte SHA-256 digest, encoded
as 64 lowercase hexadecimal characters. The retired pipeline's 16-hex-character truncation
may remain an internal change hint, but it is never accepted as an artifact-integrity value.

Daily check with no changes remains one request. When the verified `generationId` moves, the
app fetches the referenced catalog object whole. Measured 18 Aug 2026: 6,916 catalog entries
≈ 400 KB gzipped — less than two of the bucket shards this replaces (D9).

### 4.2 Catalog entry

```json
{
  "id": "adab-al-ishrah", "coll": "book", "v": 7,
  "hash": "3f9a1c22b4e08d71b2b945c64061f2eb749f9618f7a0405731d41f28ca5f80cd",
  "title": "أدب العِشرة",
  "person": "jmal-al-dyn-abn-hsham", "personName": "جمال الدين ابن هشام", "died": 761,
  "topics": ["al-nahw"], "kind": "متن",
  "authoredYear": 761,
  "pkg": {
    "path": "content/book/adab-al-ishrah/<sha256>.athar", "hash": "<sha256>", "size": 184223,
    "idxPath": "content/book/adab-al-ishrah/<sha256>.athar.idx", "idxHash": "<sha256>", "idxSize": 812,
    "uncompressed": 612884, "blocks": 1840, "chapters": 12,
    "pages": { "from": 1, "to": 214, "vols": 1 }
  },
  "audio": [ { "id": "…", "path": "audio/<id>/<sha256>.opus", "hash": "<sha256>",
               "format": "opus", "seconds": 1840, "size": 14882301 } ]
}
```

`pkg` may be present for `book`, `article`, `question` and `poem`; absent means a deliberately
metadata-only entity. `uncompressed` lets the app check free space before downloading. An
audio object is attached by ID and integrity metadata, never scraped from a web page.

**Only ship fields the corpus actually has.** Measured over `src/content` on 18 Aug 2026:

| Field | Coverage | Use |
|---|---|---|
| `kind` | 919 / 1,238 books — **مرجع 483 · متن 222 · كتاب 208** | **the work-type axis.** Browse-by-work-type and the المتون tab filter on this |
| person `died` | 551 / 593 (93%) | **the century axis.** Browse-by-century resolves through the author, not the book |
| `topics` | 1,038 / 1,238 (84%) | topic browse; it is an array — a book may carry several |
| `authoredYear` | 486 / 1,238 (39%) | too sparse to browse by; keep as display metadata only |
| ~~`workType`~~ | **8 / 1,238** (شرح، تفسير) | dropped — never holds متن; that lives in `kind` |
| ~~`genre`~~ | **0 / 1,238** | dropped — the field is empty across the entire corpus |

Books with no `kind` default to `كتاب` at build time, literally. Nothing auto-classifies
متن or مجموع; those are set per book by hand or not at all.

### 4.3 Forward compatibility

1. Parse with `kotlinx.serialization`, `ignoreUnknownKeys = true`.
2. Every entity carries `v` and `hash`; change is never inferred field-by-field.
3. If `schema > MAX_SUPPORTED`, the app keeps running entirely on local data, stops syncing,
   and shows one non-blocking banner. It never blocks startup.

### 4.4 Tombstones

```json
{ "schema": 2, "since": "2026-01-01",
  "deleted": [ { "id": "old-slug", "coll": "book", "at": "2026-07-14",
                 "supersededBy": "new-slug" } ] }
```

Retained 12 months, then pruned; an app offline longer falls back to a full refetch.
`supersededBy` carries the existing `aliases` data so a renamed book migrates annotations
instead of orphaning them.

---

## 5 · Readable-content package format

The package is a **transport format**, not the reader's live document store. Android imports
verified frames into SQLite; random access and virtualised reading then happen from local
rows. A complete download may import the package once, while read-through opening may import
individual frames over time. The wire format must therefore be streamable, independently
verifiable and resumable.

| Option | Verdict |
|---|---|
| ZIP (deflate per entry) | Pays for random access we don't use, in ratio we do miss |
| tar.zst | Best ratio, but a native dependency for a one-time read |
| SQLite file per book | Couples the wire format to the app schema. Rejected. |
| **Framed gzip NDJSON** ✔ | **Chosen** — `java.util.zip` only, streams in constant memory |

### 5.1 Layout

```
content/<coll>/<id>/<package-sha256>.athar   gzip member ‖ gzip member ‖ …
content/<coll>/<id>/<index-sha256>.athar.idx frame index, sidecar
```

Large books use roughly 2,000 blocks per member. Short articles, questions and poems still
use the same format but normally produce a single small frame; the format does not pad them.

Each frame is an **independent gzip member**. Resume opens a bounded stream over
`[off, off+len)` and decompresses that member alone — never a `GZIPInputStream` continuing
through arbitrary remaining members.

```json
// content/book/<id>/<index-sha256>.athar.idx
{ "schema": 2, "coll": "book", "entityId": "adab-al-ishrah", "v": 7,
  "frames": [
    { "off": 0,      "len": 184223, "ord": 0,    "n": 2000, "sha256": "<sha256>" },
    { "off": 184223, "len": 191044, "ord": 2000, "n": 2000, "sha256": "<sha256>" }
  ] }
```

The sidecar does **not** hash itself. Integrity and provenance are rooted in the signed index
envelope: verified root payload → catalog hash → catalog entry → `idxHash` verifies the
sidecar. A complete download verifies `pkgHash`; a Range read verifies the selected frame's
full SHA-256 over its exact compressed byte range before decompression or import. The
sidecar's `coll` + `entityId` + `v` must match the catalog entry; a mismatch catches a stale
index paired with a fresh package, which would otherwise seek to a wrong member boundary and
corrupt an import silently.

### 5.2 Record stream

```
{"t":"header","schema":2,"coll":"book","id":"adab-al-ishrah","v":7,"blocks":1840,
 "chapters":[{"a":"ch-1","title":"المقدمة","block":0}, …],"footnotes":12,
 "pages":{"from":1,"to":214,"vols":1}}
{"t":"h2","a":"ch-1","i":0,"p":1,"vol":1,"x":"المقدمة","id":"<hex32>","fp":"<hex16>"}
{"t":"p","a":"ch-1","i":1,"p":1,"vol":1,"x":"الحمد لله …",
 "sp":[{"k":"strong","s":0,"e":8}],"f":["fn1"],"id":"…","fp":"…"}
{"t":"verse","a":"ch-3","i":842,"p":97,"vol":1,"s":"صدر البيت","j":"عجز البيت","id":"…"}
{"t":"fn","id":"fn1","x":"(روضة المحبين ص111)"}
```

`i` ordinal · `a` chapter anchor · `p`/`vol` printed page carried from the nearest preceding
`<hr data-page>` · `x` exact visible Unicode text **with tashkeel and Qurʾanic marks intact**
· `sp` inline semantic spans using UTF-16 `[s,e)` offsets over `x` · `id` 128-bit block
identity · `fp` 64-bit fingerprint. Markdown delimiters such as `**` are not visible text;
their meaning is retained in `sp`, not discarded. Span kinds in schema 2 are `strong`,
`emphasis`, `link`, `sup` and `entityRef`; `link`/`entityRef` also carry a target.

Books above **25 MiB compressed** split into `.athar.000`, `.001` at chapter boundaries and
are listed in `pkg.parts[]`. M0/R3 selected the threshold; the largest measured package is
12.73 MiB and therefore remains a single part.

---

## 6 · Athar document model

Verified against the corpus, not assumed.

| Node | Source syntax | Evidence |
|---|---|---|
| `Heading(level, anchor)` | `## `, `### ` | 397/400 books |
| `Paragraph(text, spans)` | blank-line separated, with inline Markdown AST children | universal |
| `List(ordered, start)` / `ListItem` | `- item`, `1. item` | required by the real short-book slice |
| `PageBreak(page, vol)` | `<hr class="page-sep" data-page data-vol />` | 9,175 / 60 books |
| `ThematicBreak` | `---` outside frontmatter | supported as a visual section break |
| `FootnoteRef` / `FootnoteBody` | `[^fn1]` / `[^fn1]: …` | 115/400 books |
| `InlineSpan(strong, emphasis, link)` | `**…**`, `*…*`, `[label](target)`, GFM bare URL | retained as UTF-16 ranges over visible text |
| `Sup(text)` | `<sup>…</sup>` | 1,066 / 60 books |
| `EntityRef(target, label)` | `[[target\|label]]`, `[[label]]` | retained as an inline target + label |
| `Anchor(id)` | `{#id}` | rare in books |
| `Quote` | `> ` | 3/400 — support, don't optimise |
| `Verse(sadr, ajuz)` | ` --- ` or `…` (U+2026) inside a numbered verse line | **59/108 poems** (8 + 51, disjoint) |
| HTML comments | `<!-- … -->` | explicitly ignored because they have no visible output |
| Tables / images / math | — | **0/400. Not in schema 2.** Adding one to source must fail the build until its node and native renderer exist. |

**Verse handling.** Re-measured 18 Aug 2026: **59 of 108** poems mark the caesura — 8 with
` --- ` and 51 with `…` (U+2026) inside numbered verse lines (`١ - <sadr> … <ajuz>`), with no
overlap between the two conventions. The earlier "only 8" figure counted the ` --- ` form
alone and understated it by a factor of seven.

The build parser reads both and emits `{"t":"verse","s":…,"j":…}` when the split is known,
`{"t":"verse","x":…}` when it is not; the renderer centres unsplit verses rather than
fabricating a break. Guessing the midpoint would produce wrong poetry, highly visible to the
target user. Fix the remaining 49 poems in source over time; the format accommodates both.

**Fidelity and no reading cap.** The parser walks a real Markdown AST; it does not split on
blank lines and then strip punctuation. Every visible Unicode code point and every supported
semantic node must be represented in the emitted blocks. Encountering an unsupported AST
node fails generation with the source path and node type — it never silently drops content
or degrades the whole document to an unstyled string. Golden coverage tests compare source
AST → package → Android model for visible-text equality, span boundaries and node coverage.
There is no document-length, chapter-count or paragraph-count limit in the contract; frames
bound transfer/import work, and the Android reader pages local rows instead of truncating a
work.

**Parser placement.** The parser is **TypeScript, build-side only** (`gen-app-content.ts`).
TypeScript does not run on the phone, ship in the APK, lay out text or participate in a
scroll. The device consumes compact pre-parsed blocks and renders them with native
Kotlin/Compose primitives. Reader smoothness is therefore governed by frame import, SQLite
paging, Compose text layout and the §17 budgets — not by the language used in CI. Parser
bugs are fixed by regenerating content, not by shipping an APK. Kotlin needs no Markdown
parser and no `stripMd()`.

---

## 7 · Identity, anchors and offsets

### 7.1 Three separate identifiers

```
identity     blockId : random 128-bit, hex32, persisted in the build-side sidecar
fingerprint  fp64    : 64-bit simhash over the normalised token multiset
hint         ordinal : Int — search hint only, never identity
```

M0/R4 rejected the derived hash after a heading rename changed every block ID in that
chapter. Each genuinely new semantic block now receives a cryptographically random ID. On
regeneration, the build aligns blocks to the previous sidecar using exact normalized content,
then fingerprint, surrounding context and ordinal proximity; matched blocks retain IDs.

```json
// content-ids/<coll>/<id>.ids.json — required build input after first generation
{ "schema": 1, "generation": 42,
  "ids": [ { "ord": 0, "blockId": "…", "fp64": "…" }, … ] }
```

The sidecar is committed or stored durably with content build inputs. Losing it is a
contract-breaking event: package generation is not reproducible from Markdown alone. The
generated `.athar` package carries `blockId`; Android stores and compares it but never derives,
reassigns or repairs it. See ADR 0001.

### 7.2 Offset convention

All offsets are **UTF-16 code unit indices into `block.text` exactly as stored**.
`block.text` is byte-exact as authored — **no build-time NFC** — so display fidelity is
absolute and offsets never shift under a normalization-form change. NFC happens inside the
normalizer, which is why its map cannot be 1:1.

One convention across Compose text selection, annotation endpoints, and search highlighting.

### 7.3 Re-attachment ladder

Per endpoint, in order:

1. Exact 128-bit `blockId` on both ends.
2. `fp64` within ±50 ordinals, plus `prefixContext`/`suffixContext` alignment to recover the
   character offset.
3. Chapter + printed page → re-attach to the chapter, mark `approximate`.
4. Chapter only → retain, mark `orphaned`, surface in a "notes that lost their place" list.

**An annotation that cannot be re-attached is never deleted.** Storage is trivial; a lost
note from a scholar is not recoverable.

---

## 8 · Local database schema

Two Room databases in separate files. This is the structural expression of "never destroy
user data".

### 8.1 `athar_user.db` — sacred

```sql
annotation(
  id TEXT PRIMARY KEY,
  entityId TEXT, kind TEXT,                 -- bookmark | highlight | note
  startBlockHi INTEGER, startBlockLo INTEGER, startOffset INT,
  endBlockHi   INTEGER, endBlockLo   INTEGER, endOffset   INT,
  startOrdinalHint INT, endOrdinalHint INT,
  startFp64 INTEGER, endFp64 INTEGER,
  quotedText TEXT,                          -- full selection, original vocalisation
  prefixContext TEXT, suffixContext TEXT,   -- 48 chars either side
  chapterAnchor TEXT, printedPage INT,
  colorId INT, noteText TEXT,
  state TEXT,                               -- exact | approximate | orphaned
  createdAt INTEGER, updatedAt INTEGER)
INDEX(entityId, startOrdinalHint)

readingPosition(entityId PK, chapterAnchor, blockIdHi, blockIdLo,
                ordinalHint, offsetInBlock, progressPct, updatedAt)
readingHistory(id PK, entityId, openedAt, secondsRead)  INDEX(openedAt DESC)
libraryEntry(entityId PK, status, addedAt, updatedAt)   -- readLater | reading | finished
userCollection(id PK, title, createdAt, updatedAt)
collectionItem(collectionId, entityId, addedAt, PK(collectionId,entityId))
pinnedDownload(entityId PK, pinnedAt, pkgHash)
recentSearch(q PK, at)
```

A bookmark is the degenerate zero-length range at a block start, so one table serves all
three kinds and multi-block selection is native.

`libraryEntry` and `userCollection` are intentionally small. They provide useful shelves
without a tag ontology or recommendation engine. Status and collection membership are user
data, survive content-database rebuilds, and are included in SAF export/import with
annotations and reading state.

Hand-written Room migrations, additive only, with `MigrationTestHelper` asserting every
1→N path preserves rows. A failed migration copies the file aside; it never falls back to
destructive recreate.

### 8.2 `athar_content.db` — disposable

```sql
entity(
  id PK, coll, v, hash, title, titleNorm,
  person, personName, died, kind,
  authoredYear, topicsCsv,
  pkgPath, pkgHash, pkgSize, idxPath, idxHash,
  availability,                   -- absent | partial | complete
  transferState,                  -- idle | fetching | verifying | importing | failed
  updateAvailable,
  localVersion, lastOpenedAt, bytesOnDisk, importProgress)
INDEX(coll), (person), (availability), (transferState), (lastOpenedAt)

entityFrame(
  entityId, frameOrdinal,
  firstBlockOrdinal, blockCount,
  compressedBytes, lastOpenedAt,
  PRIMARY KEY(entityId, frameOrdinal))

block(
  rowid INTEGER PRIMARY KEY,
  entityId TEXT, ordinal INT,
  blockIdHi INTEGER, blockIdLo INTEGER,     -- 128-bit identity
  fp64 INTEGER,
  chapterAnchor TEXT, type TEXT,
  printedPage INT, vol INT,
  text TEXT,                                -- exact VISIBLE Unicode, tashkeel intact
  attrs BLOB,                               -- typed block attributes (level/list/verse…)
  inlineSpans BLOB)                         -- compact UTF-16 semantic ranges from §5.2
UNIQUE(entityId, ordinal)
INDEX(blockIdHi, blockIdLo)  INDEX(entityId, chapterAnchor)  INDEX(fp64)

chapter(entityId, anchor, title, firstOrdinal, PK(entityId,anchor))
footnote(entityId, fnId, text, PK(entityId,fnId))
```

`fallbackToDestructiveMigration()` is **correct here** — a schema bump drops the DB and
re-imports from retained packages or re-downloads. That asymmetry is the entire point of the
split.

**One owner per fact.** `pinnedDownload` in `athar_user.db` is the sole source of truth for
the user's retention intent. `entity.availability`, `entityFrame` and
`entity.transferState` in `athar_content.db` describe only disposable content and transfer
lifecycle; none contains `pinned`. Availability is deliberately separate from activity, so
a failed adjacent prefetch cannot turn already-verified readable frames into `absent`. The
repository combines both database Flows for UI state:

```
cached       = entity.availability != absent
fullyCached  = entity.availability == complete
pinned       = pinnedDownload(entityId) exists
busy         = entity.transferState != idle
```

No ordinary Room transaction spans the two database files. Cross-database operations are
therefore ordered, idempotent and reconciled instead of pretending to be atomic:

1. Pin writes the durable user intent first, then starts/resumes content acquisition.
2. A crash after step 1 leaves a visible retryable pin, not a silently forgotten request.
3. Unpin removes the user intent; already-imported content remains cache-eligible.
4. Startup and every completed transfer reconcile pin rows, retained packages and content
   availability. The same coordinator serializes pin/unpin, eviction and catalog apply.

Catalog apply snapshots protected entity IDs (pinned, annotated, in a collection, or with
reading state) from `athar_user.db`, then performs its content-DB transaction. A final
reconciliation closes a race with a user action. Collection-filtered search likewise
resolves entity IDs from the user repository first and supplies them as structured content
query parameters; it does not use `ATTACH` or a cross-file SQL join.

### 8.3 FTS — compact contentless index

FTS returns **rowid and rank only**. Original vocalised text always comes from `block`.
M0/R2b selected raw compact DDL after measuring the index independently of the retained
relational text (ADR 0002).

```sql
CREATE VIRTUAL TABLE block_fts USING fts5(
  norm,
  tokenize='unicode61',
  content='',
  contentless_delete=1,
  detail=none
);

-- rowid IS block.rowid; :candidate contains generated syntax only
SELECT b.rowid, b.entityId, b.ordinal, b.chapterAnchor, b.printedPage, b.text
FROM block_fts f JOIN block b ON b.rowid = f.rowid
WHERE block_fts MATCH :candidate
ORDER BY bm25(block_fts), b.rowid
LIMIT :candidateBatch OFFSET :candidateOffset;

CREATE VIRTUAL TABLE catalog_fts USING fts5(titleNorm, personNorm, topicsNorm,
                                            id UNINDEXED, tokenize='unicode61');
```

`detail=none` stores no token positions, so multi-token quoted phrases are not issued to
`MATCH`. FTS retrieves explicit-`AND` candidates; normalized source text from `block` then
must contain the exact phrase before the result receives phrase rank or reaches the UI. This
post-filter is a correctness rule, not an optimization.

The former fixed `LIMIT 80` was not a correctness boundary: a valid exact phrase can rank
after 80 high-frequency AND-candidates. The repository scans deterministic 80-row candidate
batches in `bm25(), rowid` order until it has the requested number of verified results, the
candidate stream is exhausted, or the search coroutine is cancelled. It may return a page
without calculating an exact total, but it must never report "no phrase match" merely because
the first candidate batch contained none. A real-corpus test places the first exact phrase
after candidate 80 and must still retrieve it.

The table cannot rebuild from itself because it stores neither text nor an external-content
link. Retained packages (D6) are therefore a correctness dependency for pinned content. The
measured offline repair recreates and repopulates the table from those packages.

Eviction is one transaction:
`DELETE FROM block_fts WHERE rowid IN (SELECT rowid FROM block WHERE entityId = ?)`, then the
block rows. Annotations in `athar_user.db` are untouched.

---

## 9 · Reader

```
block table (SQLite, whole book)
   └─ Room PagingSource, window ±N
        └─ ViewModel StateFlow<PagingData>
             ├─ LazyColumn — ordinary reading; only visible blocks composed
             └─ bounded selection window ±N
                  └─ AndroidView(selectable TextView) — copy + annotation endpoints
block_fts — whole book indexed
```

Blocks are read through a Room `PagingSource` keyed on `(entityId, ordinal)`. Compose holds
a window; SQLite holds the book.

**Position restoration** persists `(blockId, ordinalHint, offsetInBlock)` — never a scroll
pixel offset, which any typography change invalidates. On open: look up `blockId`, fall back
to `ordinalHint`. Written on a debounce and in `onStop`.

**Tashkeel hiding** is a render-time transformation only, using the same `TASHKEEL` class as
the normalizer. `block.text` is untouched; copy and share always emit the original.

**Reading controls:** discrete text-size and line-height steps, content-width/margin steps,
light/sepia/dark themes, and optional tashkeel hiding. Every setting changes presentation
only; no setting rewrites stored content or invalidates anchors. The table of contents,
printed volume/page jump, in-book search, and footnote sheet all return to the same logical
block and offset rather than a pixel position.

**Footnotes:** a reference opens its body without navigating away from the reading position.
Back or dismiss returns focus to the invoking reference. Long footnotes scroll independently;
copying the body is explicit, and it is never silently appended to selected prose.

**Text selection:** M0/R1 rejected `SelectionContainer` for annotations because it exposes no
public callback carrying stable block identity and UTF-16 endpoints. When selection begins,
the reader supplies a bounded native `TextView` with semantic text from ±N blocks and a map
from each emitted UTF-16 range back to `(blockId, offset)`. It is embedded in Compose and uses
the same Paging/Room data; ordinary scrolling stays virtualized Compose. The measured
220-block fixture copied 17,964 UTF-16 units exactly with two-`LF` block separators.

### 9.1 Copy semantics

Copied text is the concatenation of **rendered semantic block text** across the selection,
sliced at the UTF-16 endpoints. All tashkeel and Qurʾanic marks preserved. **No
normalization on copy** — no NFC, no stripping, no whitespace collapsing.

| Case | Emitted |
|---|---|
| Between any two blocks | exactly one `\n\n` |
| Partial block at either end | sliced at the UTF-16 offset, no padding |
| Heading block | its text, then the standard `\n\n` |
| Verse block, split known | `sadr + " … " + ajuz` (U+0020, U+2026, U+0020) |
| Verse block, split unknown | its raw text unchanged |
| Page-break block | contributes nothing, and produces no separator |
| Footnote reference marker | omitted |
| Footnote body | included only when explicitly selected |

Attribution — book, author, volume/printed page when present, and stable URL — is appended by
**Share only**, never Copy. Share offers a concise source line and a citation-oriented source
line; both are generated from catalog metadata rather than hand-edited strings.

---

## 10 · Search

### 10.1 Normalisation pipeline

```
display text  ─────────────────────────────────►  shown to user (untouched)
     │
     └─ ArNormalize: NFC · strip tashkeel + Qurʾanic marks + superscript alef + tatweel
                     أإآٱ→ا · ى→ي · ة→ه          [FROZEN — 203 golden vectors]
        └─ indexed as block_fts.norm

query ─ same function ─┬─ literal token match
                       ├─ ال-expansion        ← query-side only
                       └─ compound-name join  ← query-side only
```

The two new rules are **query expansion, not normaliser changes**: `normalizeArabic`'s output
is baked into the website's D1 index, and altering it invalidates every indexed document.
Query expansion gets the same recall, keeps the 203 vectors valid, and lets the website adopt
the identical layer later without a reindex.

### 10.2 Source-range mapping

```kotlin
class Normalized(
  val text: String,
  private val srcStart: IntArray,   // per normalized UTF-16 index
  private val srcEnd:   IntArray)
{ fun sourceRange(normStart: Int, normEndExclusive: Int): IntRange }

fun normalizeWithMap(source: String): Normalized   // core/athar-text
```

**Absorption rule — this is the whole point.** Every deleted source unit (tashkeel, Qurʾanic
mark, superscript alef, tatweel) is absorbed into the **preceding** normalized character's
`srcEnd`; deletions at index 0 attach to the following character's `srcStart`. Without this,
highlighting «الصلاة» inside «الصَّلَاةِ» leaves visual gaps at every diacritic.

Normalization is **not** 1:1 — NFC can compose or decompose — which is why the map records
ranges rather than indices.

Golden tests: heavily vocalised passages; composed vs decomposed hamza where NFC changes
length; superscript alef; Qurʾanic annotation marks; tatweel runs; a block that is entirely
diacritics; empty and single-character blocks.

### 10.3 Query construction

There is no prefix index: R2b measured only a small prefix-latency saving but a large disk
cost. A generated trailing `*` still performs prefix lookup through the ordinary FTS term
index. User input never reaches `MATCH`; tokenize locally and emit only generated syntax.
R5 also requires an explicit `AND` between every top-level term:

```
token       → "<escaped>"                  " doubled inside
last token  → "<escaped>"*                 only when len ≥ 2 and not a closed phrase
terms       → <term1> AND <term2> …         never implicit adjacency
"phrase"    → "<t1>" AND "<t2>" …          candidate query, then exact-text post-filter
ال-expansion→ ("الايمان" OR "ايمان")        token starts ال and len > 3
compound    → (("عبد" AND "الله") OR "عبدالله")
empty       → no query issued at all
```

The exact post-filter normalizes retained `block.text`, verifies contiguous phrase text, then
uses `normalizeWithMap` to recover the vocalised UTF-16 source range. Candidate co-occurrence
alone is never presented as a phrase match.

Adversarial corpus: `"`, `*`, `(`, `)`, `^`, `:`, `-`, bare `OR`/`AND`/`NOT`/`NEAR`,
unbalanced quotes, punctuation-only, 10 KB input, RTL/LTR marks, ZWJ/ZWNJ, mixed
Arabic-Latin.

### 10.4 Ranking

Block FTS has one indexed field (`norm`), so its base order is `bm25(block_fts)`. Exact phrase
rank is a verified application tier, not an FTS phrase query: retrieve AND-candidates, require
contiguous normalized source text, then place verified phrases above token-only hits. Heading
blocks may receive an explicit type bonus after the rowid join.

```
block:   verified exact phrase → heading-type bonus → bm25(norm)
catalog: bm25(catalog_fts, title=0.25, person=0.6, topics=1.0)
```

Catalog and block results are ranked separately and interleaved with catalog first — "which
book is this?" is the more common intent.

Search-as-you-type: minimum 2 characters, 120 ms debounce, each keystroke cancelling the
previous coroutine, run on a read-only connection so typing never blocks on an import.

### 10.5 Scope and filters

Full-text search covers **locally cached readable entities**: books, articles, questions and
poems. Catalog search remains available for the complete metadata catalog. Results can be
constrained by current entity, collection type, fully-offline/pinned content, author, topic,
century, work type, and personal collection; filters are structured SQL predicates and
never interpolated into `MATCH`.

There is no separate Qurʾan, tafsir-fragment, or canonical Hadith index. Tafsir and Hadith
works participate only when downloaded as regular books, with the same block FTS and ranking
as every other work. This prevents a second index and a second set of identity/navigation
rules from entering the app.

---

## 11 · Full-library search without D1

**v1 ships option A**: catalog metadata plus full text of locally cached readable entities. Static
full-text shards are **not** built for v1 — an inverted index over ~700k blocks is a
several-hundred-megabyte artefact with its own sharding and invalidation design, for a
feature that only matters before the user has downloaded anything.

**Deferred to v1.1, only if local-only proves frustrating:** one Bloom filter of normalised
terms per book, `app/v2/termfilter/<id>.bf`, ~8–16 KB each and ~10–20 MB total. Answers
"which books contain this word?" with no false negatives and no query API, turning the gap
into a discovery affordance: *"also appears in 37 books you haven't downloaded."*

Search queries are never logged or transmitted.

---

## 12 · Cache, downloads and sync

### 12.1 Cache vs pinned

```
Disposable availability (`athar_content.db`):
Absent ──open + verified frame──► Partial ──more verified frames──► Partial
Absent | Partial ──complete verified package──► Complete
Partial | Complete + unpinned ──LRU/clear cache──► Absent

Durable retention intent (`athar_user.db`):
Unpinned ──Download──► Pinned
Pinned ──Unpin──► Unpinned

Independent transfer activity (`athar_content.db`):
Idle ─► Fetching ─► Verifying ─► Importing ─► Idle
  └──────────────── failure ───────────────► Failed ──retry──► Fetching
```

`Partial`, `Complete` and `Pinned` mean different things. Partial means only recorded frame
ranges are usable offline; Complete means the entire current entity is imported; Pinned
means the user requested durable retention and the complete verified package must be kept.
The UI says “some pages cached” for Partial, “cached” for an unpinned Complete entry, and
“Downloaded” only when pin intent and a retained complete package agree. Pressing Download
on an already-complete, hash-valid entry inserts the
`pinnedDownload` row and retains/reacquires the package. Pressing it while absent or partial
writes that row first and then completes acquisition. Pinned intent is never evicted, even
while bytes are temporarily missing or a retry is pending.

**Opening without Download is read-through.** Given the target ordinal (first block or saved
position), the repository:

1. fetches the content-addressed `.idx`, verifies `idxHash`, and locates the containing frame;
2. issues an HTTP Range request for exactly that compressed member, then verifies its
   sidecar `sha256` before decompression;
3. imports blocks + FTS rows in one content-DB transaction and records `entityFrame`;
4. renders immediately, then prefetches at most the adjacent frame while that reader remains
   active.

When the reader closes, no new frame starts. The one Range response already in flight may
finish so it can be verified and committed atomically; if it is cancelled or incomplete,
its unverified temporary member is discarded. The app does **not** turn an ordinary open into a
background full download and does not create a pin. Verified imported frames remain under
the normal LRU budget, so reopening is instant when the required range is still present. If
the user reaches an uncached boundary while offline, the reader keeps the visible text and
shows “connect to continue”; it never pretends the whole work was downloaded.

For a one-frame or otherwise tiny package, `.idx` + Range can cost more latency than fetching
the whole package. The client may fetch it whole and mark it Complete, but the cutoff is
selected from the §16 real-slice measurements rather than guessed in the contract.

| Concern | Design |
|---|---|
| Location | Verified blocks + FTS in `athar_content.db`; retained pinned packages in `filesDir/content/`; resumable explicit-download `.part` files in `filesDir/transfers/`; disposable Range members only in `cacheDir/athar-frames/` |
| Eviction | LRU on frame/entity `lastOpenedAt`. Remove `entityFrame`, block and FTS rows together. Exclude pinned IDs, the visible frame and its active adjacent prefetch. |
| Budget | 2 GB default, settable 500 MB – 20 GB, plus "no limit" |
| Low storage | Below 500 MB free: stop caching, evict to half budget, banner. Pinned untouched. |
| Package retention | **Pin row present: complete package kept. No pin row: package/range file deleted after verified import; imported rows remain evictable.** |
| Clear cache | Cache only; the pinned figure is shown beside it so the distinction is legible |

Persistent transfers do not live in `cacheDir`: Android may delete cache files when storage
is low. Only a disposable member that can be requested again goes there. See Android's
[app-specific storage guidance](https://developer.android.com/training/data-storage/app-specific).

### 12.2 Import and resume

```
Read-through:
Frame Range ──frame sha256──► Verified member ──transaction──► Partial | Complete

Explicit Download:
.part ──Range resume──► complete ──pkgHash──► Verified package
Verified ──► Importing(frame k) ──batch commit──► importProgress = ordinal
   ↑ process death → reopen verified .idx, seek to the member containing importProgress,
     decompress that member alone, resume
Importing done ──► FTS built ──► Complete; retain package because pin intent exists
```

### 12.3 Content-DB rebuild — why D6 matters

```
ContentDbLost (schema bump | corruption | user "rebuild")
  → enumerate retained packages in filesDir/content/
  → re-import each, offline, no network            ← pinned entries fully recover
  → refetch the catalog when network returns       ← unpinned cache reappears as Absent
  → athar_user.db untouched; annotations re-attach via the §7.3 ladder
```

### 12.4 Download manager

One persistent `ContentTransferRunner` owns the idempotent state machine below; schedulers
call it but do not duplicate it. **WorkManager is the default**, not a universal rule:

- Catalog sync, automatic refreshes and short/interruption-safe explicit content transfers
  use one `CoroutineWorker` per entity.
- On API 34+, a user-initiated transfer uses a user-initiated data-transfer (UIDT) job only
  when it must start immediately, show progress and avoid a user-harming interruption. A
  transfer expected to finish below WorkManager's ten-minute boundary and safe to resume can
  remain a Worker. Current packages top out at 12.73 MiB, so that is expected to be the normal
  book path unless real-network measurements contradict it.
- On API 26–33 the same operation uses WorkManager. If measurement shows a transfer genuinely
  needs continuous user-visible execution beyond ten minutes, the Worker calls
  `setForeground()`; no separate download service is added by default.
- Audio continues to use Media3's dedicated `DownloadManager` rather than this runner.

This distinction matters on Android 16, where long-running Workers consume job quota; UIDT
jobs are the platform path for qualifying user-started transfers. Long automatic work must
remain interruptible and resumable instead of assuming foreground execution escapes quota.
Both scheduler adapters report into the same Room-backed transfer state, and neither owns
truth in memory. See Android's [data-transfer guidance](https://developer.android.com/develop/background-work/background-tasks/data-transfer-options)
and [long-running Worker guidance](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running).

```
WorkManager tags: "content" · "content:<coll>:<id>" · "group:<kind>:<key>"
constraints: CONNECTED   (UNMETERED only when Wi-Fi-only is on; default OFF)
backoff: EXPONENTIAL, 30 s, max 5 attempts
```

Steps, each idempotent: resolve `pkg` and check free space against `uncompressed` → download
`.part` with Range resume → verify `idxHash` then `pkgHash` → stream-import by frame,
recording `importProgress` → build FTS → set `availability=complete` and
`transferState=idle`, retain the verified package for a pin, delete only transient `.part`
state, then post a **grouped** completion notification.

Corruption at any later point removes the affected verified availability and enqueues a
silent re-download when retention intent requires it. Unpinned entries refresh silently;
entries with a pin row prompt first. No old versions are retained.

Audio has its own tag namespace, screen and notification group, sharing only the HTTP client
and hash helper. Never shown in one list with books.

### 12.5 Catalog sync

```
LocalReady ──daily worker | manual──► Checking
Checking ──generationId unchanged──► UpToDate
Checking ──moved──► Fetching(catalog/<hash>.json) ──► Applying ──commit──► LocalReady
Fetching ──network error──► LocalReady (old catalog kept)
Applying ──parse error──► LocalReady (rolled back)
Checking ──schema > supported──► Degraded ──► LocalReady (local data still served)
```

`LocalReady` is the state the app **starts in**, before any network code is constructed. The
library observes Room directly and renders from disk on the first frame. Airplane mode and
online startup are the same code path.

After snapshotting protected IDs from `athar_user.db` as described in §8.2, applying is one
**content-database** transaction: upsert changed entities, apply tombstones (retain protected
entities as unavailable), and write the new `generationId`. A reconciliation pass follows;
this is deliberately not described as one transaction across both databases.

---

## 13 · Privacy, backup and crash reporting

### 13.1 Backup — three tiers

```xml
<!-- res/xml/backup_rules.xml — effective on API 26–27 (requireFlags unavailable) -->
<full-backup-content>
  <include domain="file" path="datastore/settings.preferences_pb"/>
</full-backup-content>

<!-- res/xml-v28/backup_rules.xml — API 28–30 -->
<full-backup-content>
  <include domain="file"     path="datastore/settings.preferences_pb"/>
  <include domain="database" path="athar_user.db" requireFlags="deviceToDeviceTransfer"/>
</full-backup-content>

<!-- res/xml/data_extraction_rules.xml — API 31+ -->
<data-extraction-rules>
  <cloud-backup>
    <include domain="file"     path="datastore/settings.preferences_pb"/>
    <exclude domain="database" path="athar_user.db"/>
    <exclude domain="database" path="athar_content.db"/>
    <exclude domain="file"     path="books/"/>
  </cloud-backup>
  <device-transfer>
    <include domain="file"     path="datastore/settings.preferences_pb"/>
    <include domain="database" path="athar_user.db"/>
    <exclude domain="database" path="athar_content.db"/>
    <exclude domain="file"     path="books/"/>
  </device-transfer>
</data-extraction-rules>
```

A `full-backup-content` block containing any `<include>` backs up only the included paths, so
`athar_content.db` and `files/books/` are excluded by omission on the legacy tiers.

On API 26–27, private databases are simply never backed up; SAF export/import is the
migration path there, and the general-purpose one everywhere.

### 13.2 Crash reporting

**Nothing in the app. Google Play Console vitals only.** It reports crashes and ANRs for
Play-installed builds with zero code, no SDK, no endpoint to run, and no opportunity to
collect content because the app never assembles a report in the first place.

The privacy rule survives the removal and applies to anything added later: **never** collect
search queries, note or highlight text, block contents, entity IDs being read, reading
history, or a user identifier.

ACRA, self-hosted, was the earlier choice. It is reconsidered only if the direct-APK channel
grows enough that its crashes matter — Play vitals cannot see those installs. Until then it
would be an SDK, a Worker endpoint, an opt-in flow and a CI scrubbing test bought for a
signal Play already gives away.

---

## 14 · Audio

Media3 (`ExoPlayer` + `MediaSessionService`). Given 854 of 971 recordings are short Q&A
answers, the player is a queue of entry-attached tracks, not an audiobook engine.

Audio remains generic and entry-attached. Athar does not become a Qurʾan recitation player
or introduce surah/ayah timing, and it does not create a specialist Hadith-audio model.

| Requirement | Mechanism |
|---|---|
| Background + lock screen | `MediaSessionService` with a foreground notification |
| Playback while reading another book | Player lives in the service, not in any ViewModel |
| Global speed preference | `preference` row, applied on every `MediaItem` transition |
| ±15 s skip | `seekBack`/`seekForward` increments on the player, inherited by system controls |
| Remembered position | Per track id, on pause/stop and every 10 s |
| Downloads | Media3 `DownloadManager`, separate from `ContentTransferRunner` |
| Verse-synced audio | `poem-timing` `[{v,t}]` — 3 poems. Model as an optional track→block cue list; UI limited to highlighting the current verse. |

Playback streams the catalog's hashed R2 audio object and uses Media3's bounded streaming
cache; opening or playing does not silently create a durable audio download. An explicit
audio Download is owned and retained by Media3. Leaving the poem screen does not cancel
playback the user started — the media session continues — but it also does not enqueue the
remaining file merely because the screen was opened.

---

## 15 · State, UI, accessibility

```
Room (Flow) → Repository (Flow, no state) → ViewModel (StateFlow, stateIn WhileSubscribed(5s))
            → Compose (collectAsStateWithLifecycle)

Navigation:    Navigation 3 (D11). Nav2 is the fallback if M0's spike disappoints.
Process death: SavedStateHandle holds route args + transient UI state only;
               everything durable is already in Room.
Long work:     persistent transfer runner via WorkManager or the §12.4 UIDT escalation;
               never a ViewModel-scoped coroutine.
DI:            Hilt — the only real dependency in this list, and the official one.
```

Because every screen observes the database rather than a network result, offline-first stops
being a feature and becomes the only thing the code can do.

**Library experience.** The default library has Continue reading, Read later, Reading,
Finished, Downloaded, and Recent views, plus user-created collections. Catalog-backed browse
supports author, topic, century, work type, and downloaded-only filters. Related-work links
come only from explicit catalog relationships and shared metadata; v1 has no behavioural
recommendations, social layer, or opaque ranking feed.

**Notebook.** Highlights, notes and bookmarks from every book are visible in one local
screen, filterable by book, author, topic, kind and colour. Each item can reopen its exact
range, expose an approximate/orphaned state, and be copied or exported with its source line.
No account or network service is required.

**UI boundary.** Visual identity is deliberately deferred, so no feature module may reference
a colour, dimension or font directly. `core/ui` holds `AtharTheme`, `AtharColors` (semantic
names only, no hex outside this file), `AtharType`, `AtharMotion`. Material 3 is component
infrastructure; **dynamic colour is off** so the system cannot repaint a chosen identity.

**Accessibility, structural from day one:** all reader type in `sp`, with the reader's own
size setting a multiplier *on top of* system scale (test at 200%); line height carried
per-step in the type scale, since Naskh needs more than Latin at the same size; reduced
motion read once in `AtharMotion` and resolved through it by every animation. TalkBack
semantics — text and role per block, decorative page markers marked as such, every icon
button labelled — added now; full polish later. Adding semantics to a finished virtualised
reader is not cheap.

---

## 16 · Testing

| Layer | Where | Must catch |
|---|---|---|
| **Golden vectors** | `core/athar-text`, pure JVM | Kotlin `ArNormalize` diverging from TypeScript. CI regenerates and fails on drift in either direction. |
| **Source-range map** | pure JVM | Highlight spans breaking around composed characters or removed tashkeel (§10.2 cases) |
| **Query parser** | pure JVM | Adversarial input producing malformed `MATCH` |
| Parser unit tests | Node | page markers, lists, links/GFM autolinks/emphasis spans, footnotes, verse splitting, `<sup>`, HTML comments, malformed and unsupported AST nodes |
| Semantic coverage | Node | every visible source AST node is emitted or the build fails with file + node type; zero silent stripping |
| Package round-trip | JVM | md AST → framed `.athar` → Android model preserves exact visible Unicode, inline semantics and UTF-16 span boundaries |
| Signed root | JVM + Node | valid envelope accepted; payload/signature tampering, unknown key, unsupported algorithm, malformed base64 and >64 KiB input rejected without changing local state; dual-key rotation accepted |
| Frame resume | JVM | kill mid-import, resume from the exact member boundary |
| Room migration | instrumented | every 1→N path on `athar_user.db` preserves every row |
| **Annotation survival** | Node | the R4 harness across all five mutations |
| FTS behaviour | instrumented | phrases, ال-expansion, compound names, ranking, delete-by-rowid; exact phrase still found when its first verified hit is after candidate 80 |
| Read-through cache | instrumented | verified frame opens before full download; leaving cancels adjacent prefetch; reopen hits Room; offline boundary is honest; corrupt Range never imports |
| Download recovery | instrumented | kill mid-download / mid-import / mid-index; resume not restart |
| Eviction | instrumented | pinned never evicted; partial frame + block + FTS rows removed atomically; annotations retained |
| Personal library | instrumented | shelves and collections survive content-DB rebuild; removing a collection never removes a book, annotation or download |
| Export round-trip | instrumented | notes, ranges, source metadata, shelves and collections survive SAF export/import without text normalization |
| Offline startup | instrumented | airplane-mode cold start interactive; **zero** network calls opening a downloaded book |
| Macrobenchmark | API 26 profile | §17 budgets on the 82 MB book |

**First real vertical slice — exactly four short readable entries plus the poem's existing
audio, not five placeholder books:**

| Role | Canonical repository source | Current size | What it exercises |
|---|---|---:|---|
| Article | [`src/content/article/byan-kdhb-athr-idha-safr-al-fqr-ila-mkan-ma-qal-al-kfr-khdhny-mak-ala-aby-dhr--v2.md`](../src/content/article/byan-kdhb-athr-idha-safr-al-fqr-ila-mkan-ma-qal-al-kfr-khdhny-mak-ala-aby-dhr--v2.md) | 776 B | frontmatter, invisible HTML comment, Arabic paragraphs |
| Book | [`src/content/book/nawaqid-al-islam.md`](../src/content/book/nawaqid-al-islam.md) | 4,623 B | headings, lists, bold inline spans and printed-page markers |
| Masʾala (`question`) | [`src/content/question/swteat-866.md`](../src/content/question/swteat-866.md) | 1,081 B | question/answer headings, paragraphs and external link |
| Poem | [`src/content/poem/qasidat-madh-al-sunnah-wa-ittiba-al-salaf.md`](../src/content/poem/qasidat-madh-al-sunnah-wa-ittiba-al-salaf.md) | 3,835 B / 29 verses | native verse layout and attached-track relationship |
| Poem audio + cues | [`src/content/audio/qasidat-madh-al-sunnah-wa-ittiba-al-salaf.md`](../src/content/audio/qasidat-madh-al-sunnah-wa-ittiba-al-salaf.md) + [`poem-timing`](../src/content/poem-timing/qasidat-madh-al-sunnah-wa-ittiba-al-salaf.json) | 2,135,488 B / 5:41 / 29 cues | R2 Opus delivery, playback, seeking and current-verse highlight |

The first end-to-end content build publishes only this slice to an R2 staging origin with the
same signed, content-addressed relative layout as production. A debug build points at that
origin; it never reads Markdown, GitHub or an Astro page. On a real phone the slice must pass:
online first open, leave/reopen cache hit, airplane-mode cached reopen, explicit Download then
fully-offline reopen, process death, RTL selection/copy, dark/large text, a 10-second fling,
and poem playback across every cue. Network inspection must show only the configured R2
custom domain.

The short slice decides the whole-object cutoff and visual/interaction quality. Broader
regression fixtures remain corpus-derived: a heavily vocalised passage; both poem caesura
forms; a chapter with 40+ page markers across a volume boundary; a long-footnote book; a
Qurʾanic passage with annotation marks; and the existing 82 MB book for multi-frame cache and
performance stress. That large book is a benchmark input, not another user-facing vertical-
slice item, and is referenced by path/fetched in CI rather than committed as an app fixture.

---

## 17 · Performance budgets

Measured on a low-memory API 26 profile (2 GB RAM emulator), not a flagship.

| Metric | Target | Hard fail |
|---|---|---|
| Cold start → interactive library | < 800 ms | 1.5 s |
| Network calls before interactive | 0 | any |
| Open a downloaded book → first text | < 300 ms | 600 ms |
| Network calls opening a downloaded book | 0 | any |
| Search keystroke → results (20 books local) | < 120 ms | 300 ms |
| Reader scroll, 10 s fling | 0 dropped frames | > 1% |
| Peak RSS reading the 82 MB book | < 180 MB | 300 MB |
| Peak RSS importing the 82 MB book | < 220 MB | 350 MB |
| Import + index throughput | > 4 MB/s | 1 MB/s |
| FTS index size vs original text | < 55% | 90% |
| Daily sync bandwidth, nothing changed | < 50 KB | 250 KB |
| Package overhead vs raw markdown | < 1.4× | 2× |

---

## 18 · Build and release

Two pipelines in one repository, sharing only the golden vectors. **Adding a readable entry
must never require a Play release.**

```
Content (every main push)          Shared contract         App (android/** and tags)
markdown → validate:content        pnpm app:vectors        unit + golden vectors
  → app:gen:                         → committed             → instrumented, API 26
     framed .athar + .idx            → CI fails on drift     → macrobenchmark vs §17
     + content-addressed catalog                             → signed AAB → Play
     + signed index envelope                                 → signed APK → GH Release
  → upload immutable objects to dedicated R2
  → publish signed index.json last
```

Path filters keep them independent: the Android job ignores content-only commits, the content
job ignores `android/**`. The one deliberate coupling is that both verify the same vectors,
so a normaliser change cannot land on one side only.

GitHub stores and reviews canonical Markdown; it is not a CDN or fallback origin. Android
release configuration contains the R2 custom-domain base URL, while debug builds may use a
separate staging base URL and debug-only trusted signing key. The relative `app/v2` contract
is identical in both environments.

---

## 19 · The content pipeline is written fresh

There is no migration. `app/v1` and its generator were deleted on 17 Aug 2026 — 128,466
objects emitting Markdown plus normalised `text` per chapter, for a client that never
shipped. `package.json` retains `app:vectors` only. The `app/v2` generator is therefore new
code against §4–§7, not an edit to an existing script, and M1 must be sized accordingly.

Retiring v1 also removed the "leave v1 in place for a zero-risk cutover" argument: there is
nothing to cut over from. The `app/v2` prefix is kept for the reason in §4, not this one.

**Carried over from the retired pipeline, deliberately:** the determinism discipline (sorted
iteration, no timestamps) — hard-won, and what makes incremental sync possible; the sorted
hash-manifest discipline, upgraded to full SHA-256 for authenticated v2 artifacts; the
streaming write pattern that keeps `app:gen` inside its heap budget; the thin R2 route and its
edge caching; the `stripMd`/`ar-normalize` sharing, now serving the golden-vector contract
instead of shipping derived text.

---

## 20 · M0 — prototypes

M0 architecture work must report before feature implementation starts. Each prototype is
cheap to run and expensive to discover late.

Results are recorded in [`android/m0-results.md`](android/m0-results.md). The 16 Aug 2026
phone run resolved every architecture choice. It also rejected the full-detail R7 baseline.
API 26 / 2 GB timing and R6 multi-tier backup behavior remain explicit certification gates;
no API 26 image or AVD was available on the development machine.

| ID | Prototype | Runs in | Must test |
|---|---|---|---|
| **R1** | Compose selection over a virtualised list | Android | Selection spanning blocks disposed by scrolling; recovery of `(startBlockId, startOffset, endBlockId, endOffset)` in UTF-16 units; behaviour after fling past disposal; copy fidelity per §9.1 |
| **R2** | Room 3 + `BundledSQLiteDriver` | Android | FTS5 round-trip; APK delta per ABI; `bm25()` and prefix lookup functional; cold-open vs platform SQLite on API 26 |
| **R2b** | FTS A/B — regular vs `contentless_delete=1` | Android | Index size, bm25 latency, delete-by-rowid, per-book update, import speed, search latency, repair semantics |
| **R3** | Framed package + 82 MB import | Android | Blocks per frame, compressed size per frame, resume-after-kill from an exact member boundary, package overhead, throughput, peak RSS |
| **R4** | Block identity survival | **Node / TS** | The five mutations below, on duplicate-heavy hadith text |
| **R5** | FTS query builder | JVM (pure) | Adversarial input; phrase handling; trailing-`*`; ال-expansion; compound names |
| **R6** | Backup rules | Android | API 31+ and API 28–30 behaviour; API 26–27 by static assertion only |
| **R7** | **End-to-end Athar workload** | Android | Combined real-book scenario; the production rerun uses compact raw FTS, the bounded selection surface and a release build |
| **R8** | Navigation 3 spike | Android | ViewModel scoping to back-stack entries; `SavedStateHandle` through process death; two-pane adaptive layout |

**R4's five mutations:** identical paragraph inserted *before* an existing duplicate; edit to
the *previous* paragraph; repeated isnād formulae; heading rename; paragraph moved within a
chapter.

**R7's scenario, on the real 82 MB book:** import → build FTS → open near the middle →
fast-scroll → search a phrase near the end → jump to the result → flash-highlight the
vocalised match → select and copy across multiple blocks → return to the previous reading
position → kill and restart the process, restore position → repeat entirely offline.
Record peak RSS, search latency, reader jank, open latency, position accuracy, selection
behaviour.

### 20.1 Acceptance criteria

| ID | Pass |
|---|---|
| **R1** | Multi-block selection survives ≥200 blocks of scroll; endpoints recoverable as UTF-16 offsets with zero off-by-one against a known fixture; copied Unicode text exactly matches the rendered semantic source text with all tashkeel and Qurʾanic marks preserved, no normalization, separators per §9.1. Compose endpoint failure → bounded native `TextView` fallback, and R7 uses it. |
| **R2** | APK delta ≤ 4 MB per ABI (≤ 1.5 MB with per-ABI splits); `bm25()` and `prefix` functional; cold open ≤ 150 ms on API 26 |
| **R2b** | Winner on: index ≤ 55% of original text size; bm25 query ≤ 40 ms over 20 books; per-book delete ≤ 500 ms; import throughput within 15% of the other. **B additionally requires** a demonstrated offline re-import repair from a retained package. |
| **R3** | Resume after kill ≤ 2 s to first new block; package overhead ≤ 1.4× raw markdown; import ≥ 4 MB/s; peak RSS ≤ 220 MB. Outputs the frame size and part-split threshold. |
| **R4** | ≥ 95% exact survival and ≥ 99% including fuzzy **on every one of the five mutations independently**, not averaged. Any single mutation below 90% exact triggers the stable-ID sidecar (§7.1). |
| **R5** | 100% of the adversarial corpus produces either a well-formed `MATCH` or no query; zero SQL errors; ال and compound-name cases hit expected recall on a 50-book sample |
| **R6** | API 31+: `athar_user.db` absent from cloud backup, present in device-transfer. API 28–30: absent from `bmgr backupnow`, present under D2D. API 26–27: build-time assertion that `res/xml/backup_rules.xml` contains no `<include>` for either database. |
| **R7** | Peak RSS ≤ 180 MB reading; phrase search ≤ 120 ms; jump-to-result ≤ 300 ms; flash-highlight lands exactly on the vocalised match including its diacritics; 0 dropped frames on a 10 s fling; position restored to the same block after process kill; **zero network calls throughout** |
| **R8** | Reader state survives within the back stack and does not leak across books; position restored through process death; no blocker found. Fail → Nav2. |

### 20.2 Execution order

**Wave 1 — parallel, no dependencies.** R4 (Node, no emulator) · R5 (pure JVM) · R1 (start
first — highest risk, longest tail) · R6 · R8.

**Wave 2 — R2.** Room 3 + `BundledSQLiteDriver` skeleton. Short, gates everything below.

**Wave 3 — R2b** on a 20-book subset, and **R3** on the 82 MB book. R3 uses the stable-ID
package schema selected by R4.

**Wave 4 — R7.** The first full-detail Room baseline was recorded and rejected. The production
capstone must use R2b's compact raw FTS layout and run as a release macrobenchmark on the API
26 / 2 GB profile before the performance budgets are claimed.

**Gates:** R4 before M1 (fixes the package schema). R1 before M3 and M6 (fixes the reader's
composition strategy and range capture). R2b may land as late as M5 — it changes only DDL and
the delete path.

**Resolved by M0:** compact contentless `detail=none` FTS · 2,000-block frames and 25 MiB
parts · stable-ID sidecar · bounded native selectable reader surface · Navigation 3.

---

## 21 · Milestones

**Order revised 18 Aug 2026: the UI comes first.** The content model is already specified in
§4–§7, so the screens can be built against fixtures shaped by that contract and tested on a
real phone long before the pipeline exists. Building the pipeline first would mean months
before anything is visible, and every UI question would be answered on a screenshot instead
of a device. The cost of this order is that fixtures can drift from the contract, which is
paid down by deriving every fixture from §4.2 and shipping no field the corpus lacks.

**UI-first is intentional, not accidental coupling.** During M2–M3 the final interaction and
visual states may lead the not-yet-written data implementation. Screens accept immutable UI
state plus callbacks and may use corpus-shaped fixtures; they do not open Room, call HTTP or
construct package models. Before M4 freezes the wire contract, every fixture-only capability
is either represented in that contract, explicitly deferred, or removed. M5 then maps Room
Flows through screen-level ViewModels into the already-tested UI state types. This lets the
backend follow the final UI without letting fixture assumptions silently become storage
schema.

| # | Milestone | Done when |
|---|---|---|
| **M0** | Prototypes R1–R8 | ✅ Architecture questions have measured answers and recorded fallbacks. Frame size, part threshold, FTS mode, block identity, reader strategy and navigation are chosen; API-tier certification gaps are explicit and cannot be reported as passes. |
| **M1** | Site + storage cleanup | ✅ Specialist Qurʾan/Hadith/tafsir products, routes and indexes removed; `app/v1` generation retired; R2 prefix ownership made explicit |
| **M2** | UI vertical slice | Five destinations (الكتب · الشعر · البحث · المقالات · الكناشة) on Navigation 3, using UI states derived from the exact short corpus slice in §16 rather than five books; RTL, dark theme, large-text and tablet states; running on a real device |
| **M3** | Reader UI | Native reading surfaces for its article, book, masʾala and poem; headings, lists, inline styles/links, page markers, verse layout and Arabic typography are final-quality — still on local fixture state |
| **M4** | Contract + packaging | ✅ Build-only TS AST parser emits all four entries as framed `.athar` + hashed `.idx`, the poem's hashed audio/cues, content-addressed catalog/tombstones and the D14 signed envelope; unsupported syntax fails; the dedicated staging R2 origin is published objects-first/root-last; tamper/rotation and cross-language vectors pass |
| **M5** | Data layer | ✅ Both DBs on Room 3, verified read-through frame importer, partial/complete availability, catalog sync, LRU and offline rebuild. Content surfaces use Room Flows behind the same UI state types; the §16 catalog and a verified book frame passed on a real Android 15 phone only through the R2 custom domain. Evidence: [`docs/android/m5-results.md`](android/m5-results.md). |
| **M6** | Downloads + cache | Open-without-Download stops on reader close and remains evictable; explicit Download resumes, verifies, completes and retains; eviction, low-storage behavior and notifications pass |
| **M7** | Search + library organization | FTS5 + rowid join, `normalizeWithMap` snippets on vocalised text, structured filters, tap-to-paragraph; Read later/Reading/Finished shelves and user collections survive a content-DB rebuild |
| **M8** | Annotations + الكناشة | Range model, multi-block selection, central filtered notebook, source-aware copy/share, re-attachment ladder, SAF export/import |
| **M9** | Attached audio | Media3 service proves the §16 poem's existing Opus track and 29 cues end-to-end: background playback, downloads, speed, resume and current-verse highlight; no recitation or specialist Hadith-audio subsystem |
| **M10** | Hardening | §17 budgets on the API 26 profile, a11y pass, backup rules verified, tablet two-pane |

Two ordering rules survive the reshuffle:

- The offline rebuild path lands with the data layer (M5), not in hardening — it is a property
  of the importer, and retrofitting it after eviction exists is harder than building it in.
- **The reader is not built twice.** M3 builds the reader's chrome and typography on fixtures;
  the virtualised block reader and its selection surface land with real data in M5, against
  R1's measured strategy. Before writing that reader, resolve R7's 378 MiB reading RSS — the
  M0 harness ran an unbounded `PagingConfig` with no `maxSize`, so the number is unexplained
  rather than architectural, and compact FTS does not address it.

---

## Appendix · Sources

Corpus counts from `src/content`. Markdown conventions from a 400-book sample; page markers
and HTML tags from a 60-book sample. Payload figures from the last successful CI run
(`gen-app-content: 4,354 catalog entries, 128,466 files, 5,723 MB` — that pipeline was
retired on 17 Aug 2026, see §19). Normalisation from
`src/lib/ar-normalize.ts` and `src/lib/strip-md.ts`. Chunking and page-span logic from
`src/lib/chunk.ts`. Ranking from `src/pages/api/search.ts`. Those inputs were read on
12 Aug 2026; the §16 short-slice byte sizes, publication statuses, audio duration/size and
29-cue timing file were rechecked directly on 19 Aug 2026.
