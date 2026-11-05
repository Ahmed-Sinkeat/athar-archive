# Performance and debt plan (from the 2026-11-05 audit)

The audit started from one report: «تنزيل الكل» makes the app unusable. It found 44 problems across the
app, build, website and pipeline. This is the ordered plan for all of them. Each phase is one or more
commits; each step names the findings it closes (#n). Builds per step are compile + tests only; phone
install, release build, Worker deploy and publish happen once at the end (see beforepublish.md §5).

Rule for every step: fix the cause, not the symptom. No shortcut comments, no "for later" scaffolding.

## Owner decisions needed (blocking only the steps that name them)

| ID | Decision | Default I'd take |
|---|---|---|
| P1 | Bulk download on mobile data | Show total size; Wi-Fi only by default, with «تنزيل عبر بيانات الجوال» override |
| P2 | Analytics on by default? | Keep on, batched, one shared HTTP client |
| P3 | Highlights/notes cloud backup | Remind to export monthly; no cloud copy |
| P4 | Launcher icon aliases | Keep if the launcher test passes |
| P5 | Move content out of the git repo | Yes, own repo / R2 as source of truth |
| P6 | Stop prerendering 75k chapter pages in Astro | Yes, generate chapters in the content pipeline |

## Phase 1 — the freeze (downloads, catalog, launch)

- [x] 1.1 Split live transfer state out of `entity` into `entityState` (availability, transfer state,
      local package); write-only columns dropped; migration 10→11 copies in SQL, no catalog re-apply;
      `ContentMigrationTest` added (compiles; run on a device). Catalog lists now reload only when the
      catalog changes, so slimming their columns moves to 2.1. (#12, #4, #8, #18, #36)
- [x] 1.2 At most 3 transfers at once (`transferSlots` in `ContentDownloadWorker.kt`); the rest show
      «في الانتظار». Imports were already one writer at a time. (#1, #3)
- [x] 1.3 `ContentWriteLock`: the open path (space check, page import, trim) goes ahead of download
      batches waiting for the lock; pin checked once per batch, outside the write transaction.
      Eviction stays under the lock (it races imports otherwise). Catalog apply still holds it for the
      whole apply — fixed by 2.1. (#2, #9, #11, #14)
- [x] 1.4 Live bytes kept in memory (`ContentDownloadScheduler.progress`), not WorkManager progress;
      the job query returns unfinished and failed jobs only; lists built without transfers, which are
      laid over the downloading rows (`withTransfers`); download lists built on `Default`. (#4, #5, #36)
- [x] 1.5 Audio, pause and «تنزيل الكل» bookkeeping in user-DB rows (migration 1→2, `UserMigrationTest`),
      written in order behind in-memory sets; old SharedPreferences sets imported once. (#6, #7)
- [x] 1.6 Launch trusts the retained manifest (written only after verification) plus a size check;
      the rebuild still hashes in full. A finished download is renamed into the store
      (`retainDownloaded`), not hashed and copied again under the write lock. Frames already commit
      one transaction (one sync) per batch. (#10, #13, #15)
- [x] 1.7 «تنزيل الكل» menu: size, then «عبر Wi-Fi» (unmetered, the default) or «تنزيل عبر بيانات
      الجوال»; queued items say «في انتظار Wi-Fi». A row that cannot fit is refused up front; each
      running download reserves its disk (package + text) so parallel checks count each other.
      Android 15+ data-sync timeout: WorkManager re-queues, the reader gets «توقّف التنزيل مؤقتًا». (#27, #39, #40)
- [x] 1.8 `ContentWriteStressTest` (passes on the Redmi 12): three 120-frame books import through the
      real runner while a page is read every 15 ms. It found the freeze was still there: decode ran
      inside the lock and eight-frame commits held it 250-420 ms (page reads: median 750 ms, worst
      917 ms). Now frames are decoded before the lock (`prepareFrame`), commits stop after ~50 ms of
      writing, and a batch that took the lock while a page waits steps aside: median ~55 ms,
      worst ~200 ms. Bulk import is ~1.7x slower in exchange. Limits: median < 100, worst < 400. (#20)

## Phase 2 — first launch and app updates

- [x] 2.1 Catalog applied as a delta: only rows that differ from the stored row are written (whole-row
      compare, safer than the entry hash). New columns are filled in SQL (10→11 does); 8→9 and 9→10
      keep their forced re-apply for old installs, which now writes only what differs.
      `catalogApplyWritesOnlyChangedEntriesAndKeepsTheirSearchRows` (compiles; run on a device). (#9)
- [x] 2.2 The catalog ships as a database: `pnpm app:bundle-catalog` builds `catalog.db` from
      Room's exported schema (rows and search tables as the app writes them) and Room copies it
      in on a fresh install (`createFromAsset`). The JSON seed (`seedCatalog`) is gone; the release
      build refuses to run without `catalog.db`. Redmi 12: first launch spent 4.6-4.9 s decoding
      the 9 MB catalog JSON and 4.2-6.7 s applying it (~6.5 s in all when precompiled); opening
      the prebuilt database takes 130-140 ms. `PrebuiltCatalogTest` proves every row, search row
      and table definition equals the app's own apply (and fails on one changed row). (#10b)
- [x] 2.3 `ContentSearchSchema.updateCatalog`: deletes and re-inserts search rows for changed and
      tombstoned entries only. Rebuild now also leaves editions out, like the apply. (#11b)
- [x] 2.4 Content DB: destructive fallback only on downgrade; migrations in `ContentMigrations`;
      `everyOldVersionMigratesToTheCurrentSchema` runs 1..10 → 11. User DB has no fallback and its
      1→2 test (compile; run on a device). (#4b, #29)
- [x] 2.5 The app had no baseline profile of its own (only its libraries'). `BaselineProfileGenerator`
      records start → book list → reader → search → downloads on the phone; the result is
      `app/src/main/baseline-prof.txt` (1,468 of the app's own rules after R8, against 4 before).
      The benchmark build now has its own id (`com.atharchive.bench`, «أثر قياس») so it installs
      beside the real app, and keeps names unobfuscated so the profile maps onto release. (#1, corrected)

## Phase 3 — structure

- [ ] 3.1 One ViewModel per feature, split out of `AtharContentViewModel` one feature at a time. (#2)
- [ ] 3.2 `AtharApp.kt` split by feature; each feature owns its route entry. (#3)
- [ ] 3.3 Feature Gradle modules; drop `:m0` if nothing uses it. (#16, #17)
- [ ] 3.4 Keyed mutex that releases unused locks. (#19)
- [ ] 3.5 One font registry; one font source (res or assets, not both). (#13b)
- [ ] 3.6 Search-table SQL in one place. (#12b)
- [ ] 3.7 Remaining SharedPreferences state (adhkar, audio queue) moved to Room; audio service resolves
      URIs without `runBlocking`. (#6b, #7b)

## Phase 4 — correctness and safety

- [ ] 4.1 Adhkar reminders scheduled one at a time at a fixed clock time (no drift). (#28)
- [ ] 4.2 `FallbackDns`: DNS over HTTPS with the zone IPs as bootstrap; remember last good addresses. (#31)
- [ ] 4.3 `testTagsAsResourceId` only in the benchmark build. (#34)
- [ ] 4.4 Reader list keyed by ordinal so loaded chunks update in place. (#35)
- [ ] 4.5 Large screens: cap content width; check one tablet/foldable emulator. (#33)
- [ ] 4.6 Analytics on the shared client, batched (P2). (#14b)
- [ ] 4.7 R8 keep rules made explicit; one minified-build check. (#15b)
- [ ] 4.8 Licenses: add IBM Plex Sans Arabic OFL text and a credits screen generated from dependencies. (#44)
- [ ] 4.9 Shared ranking golden cases for the TS and Kotlin search. (#21)
- [x] 4.10 Text selection: kept; why-comment in `PageSelection.kt`; `JustifiedLineHitTest` added
      (compiles; run on a device). TalkBack check on the phone still open. (#38)
- [ ] 4.11 TalkBack and touch-target pass on the main screens. (#37)
- [ ] 4.12 Launcher-icon alias test on common launchers (P4). (#32)
- [ ] 4.13 Backup reminder for notes (P3). (#30)

## Phase 5 — website, pipeline, repo

- [ ] 5.1 Chapters generated in the content pipeline; Astro builds ~5.4k pages; build heap back to a sane size (P6). (#22)
- [ ] 5.2 Per-book hashing so `app:gen` and uploads only touch changed books. (#25)
- [ ] 5.3 One publish step writing and verifying both the site's R2 and the app's R2. (#26)
- [ ] 5.4 Check how app downloads are served; if through a Worker, serve from R2 directly. (#41)
- [ ] 5.5 One-off scripts moved to `scripts/archive/`; `import:epub` removed. (#24)
- [ ] 5.6 Content out of the git repo (P5); `git gc` the leftover temp packs; build outputs cleaned after publish. (#23, #43)
- [ ] 5.7 Encrypted offline backup of `~/.config/athar` keys and a written key-rotation procedure. (#42)

## Done when

Every box is ticked, each phase's tests pass, and on the Redmi 12 «تنزيل الكل» runs while a book
opens and a list scrolls without a stall (measured with a trace, before and after).
