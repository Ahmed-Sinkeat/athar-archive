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

- [x] 3.1 (2026-11-10, commit 327fc145: downloads moved to `DownloadsModel` (built on the ViewModel's scope; the library lists still lay its state over their rows), 1,960 → 1,650 lines; commit 5cf67864: search moved to `SearchModel` (it never used the reader's frame walk after all), 1,650 → 1,160 lines; commit 6385f336: reader moved to `ReaderModel` (state, window, open/close, position, lookups, editions, its saves), 1,160 → 710 lines; commit ec82c61a: audio moved to `AudioModel` (recordings, now playing, play), 710 → 640 lines; what stays is the wiring plus the library lists, الكناشة, sync, cache and backup; no behaviour change, compiled and unit-tested, not run on the phone) One ViewModel per feature, split out of `AtharContentViewModel` one feature at a time. (#2)
- [x] 3.2 (2026-11-11, commit 5fa4519e: the 13 route entries moved to `ReaderEntries`, `AudioEntries`, `LibraryEntries`, `AdhkarEntries`, `SettingsEntries` (settings screen with it); routes and the `AppNav` holder in `Routes.kt`; state reaches the entries as `State` read through delegates, so reads stay where they are drawn; `AtharApp.kt` 1,647 → 930 lines, keeping the shell, bars and the shared popovers; compiled, unit and androidTest compile pass, not run on the phone) `AtharApp.kt` split by feature; each feature owns its route entry. (#3)
- [ ] 3.3 Feature Gradle modules; drop `:m0` if nothing uses it. (#16, #17)
- [x] 3.4 (2026-11-10, commit 151df9a0: `KeyedLock` drops a book's lock once nobody holds or waits; transfer and open locks use it; test) Keyed mutex that releases unused locks. (#19)
- [x] 3.5 (2026-11-10, nothing to change: every family is in `AtharTheme.kt` and no file ships twice (checked by hash); assets/fonts stays because the Elgharib terms require the original file names, which res/font cannot hold) One font registry; one font source (res or assets, not both). (#13b)
- [x] 3.6 (2026-11-10, commit da5826cd: the tables, delete trigger and catalog insert are written once in `ContentSearch.kt`; `bundle-app-catalog.mjs` keeps its own copy, noted beside the list) Search-table SQL in one place. (#12b)
- [x] 3.7 (2026-11-10, not done on purpose: the audio service's `runBlocking` runs on ExoPlayer's loading thread, which the Media3 resolver requires to be synchronous, so it cannot freeze the app; a comment says so. Moving adhkar and the audio queue to Room would migrate the owner's saved adhkar for no user gain; reopen if those stores need queries) Remaining SharedPreferences state (adhkar, audio queue) moved to Room; audio service resolves
      URIs without `runBlocking`. (#6b, #7b)

## Phase 4 — correctness and safety

- [ ] 4.1 Adhkar reminders scheduled one at a time at a fixed clock time (no drift). (#28)
- [ ] 4.2 `FallbackDns`: DNS over HTTPS with the zone IPs as bootstrap; remember last good addresses. (#31)
- [x] 4.3 (2026-11-10, `BuildConfig.BUILD_TYPE == "benchmark"`) `testTagsAsResourceId` only in the benchmark build. (#34)
- [x] 4.4 (2026-11-10, local: the window's items are keyed by index, so a chunk that lands fills its placeholders in place; not checked on the phone) Reader list keyed by ordinal so loaded chunks update in place. (#35)
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

---

# Continuation — 2026-11-09/10 review (phone app)

Owner's report plus a code read. Nothing here is built yet: this is a structure phase, fixes follow
the order below. New findings are numbered from #45. Decided: no Rust (no measured problem is a
language problem; revisit only for an on-device vector index, by prototype). Smart search is parked
(Phase 11). Copy keeps its current plain text.

Order: 6 (downloads) → 7 (measure, reader) → 3.1/3.2 (the ViewModel split, with 7.2 as its first
slice) → 8 (search) → 9 (works, editions, pages) → 10 (UI batch) → 11 (later).

## New findings

| # | Finding | Evidence |
|---|---|---|
| 45 | Reader state is rebuilt on every scroll stop: saving the position re-emits `readerState`, which maps every footnote and TOC row of the book again, re-queries a ±24 block window the reader no longer draws from (`ReaderWindow` does), and runs `editionsFor` | `AtharContentViewModel.readerState` / `toReaderUi`; الشرح الكبير has 14,433 notes, مجموع الفتاوى 1,361 headings |
| 46 | `books` (2,465 rows) is rebuilt on every position save: it combines `positions` | `AtharContentViewModel.books` |
| 47 | `onReaderPosition` loads all frames of the book on every save | same file |
| 48 | No book-open benchmark, so «فتح الكتب صار ثقيلا» (1.2.3) passed unseen. Benchmarks cover start, tabs, list fling, reader fling, typing | `AtharBenchmarks.kt` |
| 49 | Idle home screen: 153 MB PSS, 62 MB Java heap (Redmi, 1.2.3). Whole-catalog `SELECT *` feeds five flows, three of them `Eagerly` | `dumpsys meminfo`; `observeAllEntities`/`observeCatalog` callers |
| 50 | The page number at the foot is one page behind and has no volume: `folio` shows the marker at or before the top line, but a marker ENDS its page. To confirm on the phone: الواسطية is 3/129 in print, its text sits between markers 128 and 129 | `ReaderScreen.kt` `folio`; `parser.ts` "A marker ENDS its page" |
| 51 | `mjmwa-al-ftawa` (2026-11-03 copy) has no page markers at all; 513 of 2,549 books have none. `majmu-al-fatawa` repeats marker 88 of vol 3 fifteen times | corpus scan 2026-11-10 |
| 52 | A search hit has no app position. `docs` rows carry the site url only, so the app searches the chapter again frame by frame (≤ 40 frames, each a download + local search) and ignores the url's «--pages-N-M» part → long «جارٍ فتح الموضع», wrong landing. In-book search goes through the same path | `gen-search-index.ts` schema; `locateOnline` |
| 53 | Live index rows are still v1 chunks (~9,000 chars): an AND query matches words pages apart, no proximity tier. «من أرتد ثم تاب حبط»: the wanted passage («اتفاق الأمة على أن من كان مؤمنا ثم ارتد…») ranked 13th and 20th | live API 2026-11-09 |
| 54 | The same passage fills four result places (two مجموع الفتاوى copies, دقائق التفسير, مجموعة الرسائل): results are not folded per work, and duplicate editions are separate works | same query |
| 55 | Snippets show folded text (علي، الائمه) for rows loaded with `SEARCH_NO_RAW` | same query |
| 56 | No work identity: slugs come from the importer's transliteration (`majmu-al-fatawa` / `mjmwa-al-ftawa`), so a duplicate can only be guessed from title + author | `docs/editions-and-duplicates-2026-11-07.md` |
| 57 | No Android job in CI: the 50 test files run only on the owner's machine | `.github/workflows/ci.yml` |
| 58 | No crash or error capture in the app | grep: no handler, no reporter |
| 59 | 972 `src/content/audio/*.md` files have an uncommitted `url:` change (r2 → app-content) | `git status` 2026-11-10 |
| 60 | `ReaderPanel` page jump filters `state.blocks` for page breaks under a note that says all blocks are in memory; they are a ±24 window | `ReaderPanel.kt:150` |

## Phase 6 — downloads (owner: still very bad, 2026-11-10)

Report: slow, slow to start, the whole phone drags during «تنزيل الكل» / all poems. Phase 1 fixed
the lock and the 20-at-once transfers; the cost that is left is per item, not per byte.

Measured 2026-11-10 on the Redmi (23053RN02Y), benchmark build 1.2.2, content from the local server
over `adb reverse` (so no network wait at all), downloads screen open:

| Run | Size | Time | CPU used | Frames |
|---|---|---|---|---|
| 105 poems | 2.7 MB | 30 s (3.5 items/s), first start 1.2 s after the tap | 121 core-seconds (≈ 3.7 cores busy) | 164 of 180 janky, p90 150 ms, p99 850 ms |
| 953 مسائل | 649 KB | first start about 9 s after the tap, then 188 s (5 items/s) | 231 core-seconds in the first 63 s (≈ 3.7 cores busy) | 1,115 of 1,199 janky, p90 150 ms, p99 2.3 s |
| 2 books (27.6 MB of text) + 1 poem, after a restart | 6.1 MB packages | 37 s, i.e. 0.75 MB of text per second | 77 core-seconds, including a catalog apply | 70 of 527 janky |

Where the CPU went during the مسائل run (`top -H`, 8 s): coroutines on `Dispatchers.Default` 67%,
GC 12%, WorkManager's executor 9%, main thread 8% (most of one core), OkHttp under 2%. So the
network and the database import are not the cost; the app's own bookkeeping per item is.

| # | Finding | Evidence |
|---|---|---|
| 61 | About one core-second per downloaded item, however small (a مسألة is 0.6 KB). «المكتبة كاملة · نصوص» is 5,511 items: about an hour and a half of all cores busy before a single large book is counted | runs above |
| 62 | Two thirds of that CPU is on `Dispatchers.Default`, where the list states are built. Each finished item changes `localStates`, `retainedEntityIds` and the job list, and each of those rebuilds whole-catalog lists (books 2,465, articles 1,984, مسائل 953, the downloads rows over 5,550 entries; three of them are `Eagerly`, so they rebuild with no screen showing them). To confirm with a method trace before changing anything | `top -H`; `AtharContentViewModel` list flows |
| 63 | One WorkManager job per item. In the foreground WorkManager starts up to 200 at once; all but 3 then wait on `transferSlots`, but each has already run its start-up: two DB reads, a foreground promotion and a notification. `workInfos` re-reads every unfinished job on every job change | log: "Starting work" reached 200, 400, 600, 800 while 24–275 were done; `ContentDownloadWorker.transfer` calls `setForeground` before `transferSlots` |
| 64 | Slow to start: `downloadAll` writes the pins, then enqueues one unique work per item, each its own WorkManager transaction, before anything is scheduled (> 8 s for 953). After a restart with a new catalog, downloads resumed 33 s after launch: 23 s to fetch, parse and apply the 10 MB catalog (heap 130–159 MB, four GCs in 1.5 s), then the reconcile | log 00:35:06 → 00:35:39 |
| 65 | Small kinds are thousands of requests for a few MB: poems 109 items / 3.0 MB, articles 1,984 / 11.1 MB, مسائل 953 / 0.7 MB (median 0.6 KB), two requests each (index + package). On mobile data each request is a round trip of 130–340 ms | catalog `41b0884a…`; curl from Sudan via NBO |
| 66 | Packages are not cached at Cloudflare's edge: `cf-cache-status: DYNAMIC` on an `immutable` content-addressed `.athar` (the extension is not in the default cacheable list), so every request goes to R2. Closes the question in 5.4 | `curl -I` |
| 67 | Books: the phone builds the database. 0.75 MB of text per second with no network wait, so all books (7.46 GB of text) is about 2.8 hours of import at best, against about 8 minutes to fetch their 1.89 GB at the 4 MB/s measured here | runs above; catalog sizes |
| 68 | The bulk row shows the compressed size («الكتب · نصوص … ١٫٧ غ.ب»); on the phone books take about 5.7× that, ≈10.7 GB. Measured by rebuilding the phone's schema from 50 books (every 50th by size): 36.7 MB downloaded → 208.5 MB of database. Of that: text 41% (Arabic is 2 bytes a letter once unzipped), the rest of each block row 25% (book slug, 25 chars, and chapter anchor, 21 chars, stored in each of 13.9M rows), block indexes 23% (`entityId, chapterAnchor` alone 11%), search index 7%, chapters and footnotes 3%. The space check uses the real figure, the label does not | `simdb.py` run 2026-11-10 (scratch); `diskBytes` in `ContentTransferRunner` |
| 69 | A single download re-promotes the worker to the foreground on every percent (75 times for one book) | log: "Moving WorkSpec to the foreground" |
| 70a | Measured after 6.3: WorkManager's own threads (`WM.task`) were 60–70% of the CPU of a 1,984-item run, not the list states; with the queue worker they are gone. What remains is the import itself: 1,984 articles (33 MB of text) took 170 s and 637 core-seconds (0.32 per item), 91% janky frames; مسائل (0.65 KB each) 0.13 per item. Per-item phases on a مسألة: fetch 15 ms, preImport 20 ms, importFrames 12 ms, finishImport 27 ms, prepare 7 ms, serialised by `mutationMutex`. Next: a profile of an article import | `threads.sh`, ADL timings (removed) |
| 70 | Two of the block indexes match no query: `(entityId, chapterAnchor)` and `fp64` (grep finds neither in a WHERE). `(blockIdHi, blockIdLo)` is only read together with `entityId`. Together ≈18% of book space, ≈1.9 GB | `ContentDatabase.kt:125-130`; `ContentDatabase.kt:456` |
| 71 | The text could stay where it already is: the downloaded `.athar` is gzip frames with offsets in `.idx`. Same 50 books with text left in the package, integer book/chapter keys and only lookup + search rows in SQLite: 48 MB of database + 36.7 MB of packages = 85 MB instead of 208 MB (2.5× less, all books ≈4.3 GB). It also removes most of the row-by-row import (#67). Reader, selection and snippets would read frames instead of rows | same run, `lean.db` |

Steps, in order. 6.1 and 6.2 are the bulk of the win and need no format change.

- [ ] 6.1 Method trace of the مسائل run on a profileable build; name the flows behind #62. Add
      `bulkDownload` to the benchmarks: N small items from the local server, limits on core-seconds
      per item and janky frames. (#61, #62)
- [~] 6.2 (2026-11-10, quick form: `localStates` and `retainedPins` throttled to 1/s in `AtharContentViewModel`; not measured on the phone yet, the real fix below still open) Lists do not rebuild per item: completed/retained ids applied to the rows already built
      (the way `withTransfers` lays transfers over them), no `Eagerly` list flows, downloads rows
      counted in SQL. Part of 3.1 (downloads get their own ViewModel). (#62)
- [x] 6.3 (2026-11-10: `ContentDownloadWorker.bulk()` + `ContentDownloadScheduler.enqueueBulk`; «تنزيل الكل», resume and launch recovery (>8 pending) use it; audio and single downloads keep one job each. Phone, same local-server method, no UI polling: 953 مسائل 188 s → 34.6 s, first item 9 s → 2.2 s, 127 core-s, janky 93% → 29%; 109 poems 30 s → 10.4 s, 28 core-s; pause stops within the 3 in flight, resume completes exactly 953 of 953) One worker runs a queue instead of one job per item: bulk download is a single unique
      work that takes items from the pins table, three at a time, one foreground notification. A
      single book stays one job. Removes the enqueue wait and the 200-worker start-up. (#63, #64, #69)
- [x] 6.4 (2026-11-10, commit 1cb999bf: ≤256 KB items in 8 MiB parts, same rule in TS and Kotlin; phone, local server: مسائل 10.6 s, articles 42.5 s, poems 5.4 s; needs a publish to reach R2) Small kinds ship as one bundle per kind (poems, articles, مسائل): one request, one import
      transaction per commit budget. Per-item packages stay for single downloads and updates. (#65)
- [x] 6.5 (2026-11-10: already in place since 11-03, rule «book files»; live files answer HIT with `max-age=31536000, immutable`, age 37 h) (owner, in the Cloudflare dashboard) Cache Rule on `app-content.arthurarchive.com`: cache everything under `app/v2/content/`
      (immutable, content-addressed). Owner applies it or approves it; it is account configuration. (#66)
- [~] 6.6 (launch already recovers pinned downloads before it syncs. 2026-11-10: of the 23 s only the apply (2.3 s) was logged; desktop JVM over 5,550 entries: decode 1.0 s cold / 0.3 s warm, entity mapping 0.3 s, diff 15 ms; the protect step decoded every entry twice, now reads audio rows only; sync logs fetch+decode / wait / apply+protect for the next real generation. Shipping the catalog as a prebuilt DB file is open, decide after those logs) Catalog sync does not hold downloads: resume pinned work from the stored catalog at
      launch, apply a new catalog after. Catalog parsed as a stream. (#64)
- [ ] 6.7 Book import cost: prototype a per-book prebuilt SQLite file (text and its search rows built
      in the pipeline, attached on the phone) against today's row-by-row import, on one large book.
      Decide from the numbers; this is the main-plan guardrail case. (#67)
- [x] 6.8 (2026-11-10: diskBytes = package + 1.5× text; row and menu say «تأخذ N على الهاتف») Bulk rows show the space the download will take on the phone. (#68)
- [~] 6.9 (2026-11-10, (a) done plus per-block deflate of the text, content DB v12, commit 19b06b40: simulated 50 books 201 → 116 MB (1.73×), 1.56× with packages; checked on the phone: migration, a new download renders, in-book search 39 hits. Books already downloaded stay unpacked until re-downloaded; (b), (c) not started) Smaller books on the phone, cheapest first: (a) drop the indexes no query uses, a
      migration and no format change (≈18%); (b) integer book and chapter keys instead of slugs in
      every row; (c) text stays in the downloaded frames, SQLite keeps lookup and search rows only
      (≈2.5× less). (c) is decided together with 6.7, from the same one-book prototype. (#70, #71)

## Phase 7 — measure, then the reader

- [~] 7.1 (2026-11-10, commit 31c069e5: `AtharBookOpen` async trace section, `BookOpenBenchmark`; on the phone (1.2.3) مجموع الفتاوى warm open median 245 ms; small book, cold runs and 1.2.2 not measured: the bench package install is refused by the phone, `INSTALL_FAILED_USER_RESTRICTED`) `bookOpen` macrobenchmark (tap a row → first page drawn) for a small book and مجموع الفتاوى,
      warm and cold cache; a trace section around open. Record 1.2.2 against 1.2.3 to name the regression. (#48)
- [x] 7.2 (2026-11-10, local: the book (entity, contents, editions) is built once per change of those, a saved position or annotation only copies it; the ±24 block window and the whole-book footnote list are gone, footnotes are read by id for the loaded chunk and for the marker callout; the page box shows on `pageTo`; heap and open time not measured, a footnote without a marker is numbered within its chunk now) Reader state in two parts: the book (title, card, TOC, footnotes, editions; loaded once) and
      the place (position, annotations). Footnotes read per page, not per book. Drop the ±24 window
      once nothing reads `state.blocks`. First slice of 3.1. (#45, #47, #60)
- [x] 7.3 (2026-11-10, commit def7d1cf: the books list follows positions every 3 s instead of every change; then: it follows only whole-percent progress and recent order (distinct), the unused «الموضع N» label is gone, «آخر ما قرأت» is a one-row query; heap not measured) Library lists do not depend on every position: «آخر ما قرأت» from its own small query. (#46)
- [x] 7.4 (2026-11-10, commit def7d1cf: book, article and question lists read lean rows; heap not measured) Catalog lists select only the columns a row draws; check heap before and after. (#49)

## Phase 8 — search (owner: «البحث ثم البحث ثم البحث»)

Acceptance case for the whole phase: «من أرتد ثم تاب حبط» shows the ابن تيمية passage in the first
three places, once, and a tap lands on that exact passage within a second, on a downloaded book and on
one read online (owner, 2026-11-10).

Search test, 2026-11-10: 20 queries against the live Worker with a cache-busting parameter, plus
deep pages and in-book search (`searchtest.py`, scratch). Times are from the PC, not from Sudan.

| # | Finding | Evidence |
|---|---|---|
| 72 | One heavy query takes search down for everyone. A 9-word query took 6.9–8.4 s; right after, every query (even «الله», «الصبر») answered 503 «search unavailable» for about a minute. Deep pages of common phrases also fail now and then (offset 60 of «وقال رسول الله صلى الله عليه وسلم»). Likely the single D1 database's queue; the Worker log holds the exact error | runs 00:55–00:57 |
| 73 | Ranking does not know which source matters: «إنما الأعمال بالنيات» has neither صحيح البخاري nor الأربعون النووية in the first 100 (first: عقيدة المسلم). `prior` is not live yet, so every source weighs 1, and bm25 over 9k-char chunks favours short chunks | `search-handler.ts` `schema.prior`; 5 pages read |
| 74 | Names: «ابن تيمية» shows articles about him first and none of his books; «البربهاري» shows his card, then a nisba entry in الأنساب للسمعاني, before شرح السنة | run 1 |
| 75 | 21 of 97 long snippets (22%) are folded text («الا تري انه لو ادي زكاه»): the rows loaded with `SEARCH_NO_RAW` (#55, measured) | run 1 |
| 76 | One work fills many places: up to 6 of 20 («شرح السنة», «الصبر»); `WORK_CAP` = 2 per tier, but a work comes back in each later tier, and editions count as different works (#54) | run 1, `sameWorkRepeats` |
| 77 | Each page is 0.4–1.9 s from here (common words slowest); every later page reruns the tiers before it. In-book search asks for 100 hits, i.e. up to five pages one after another | `OnlineSearch.hits`; `AtharContentViewModel.kt:1178` |
| 78 | Big books with almost no chapters, so search and the TOC can only say «صفحات N-M»: 15 books over 3 MB of text have ≤3 chapters (البداية والنهاية 42 MB in 2, المغني 3, كشاف القناع 1). In صحيح البخاري the first hadiths sit under «مقدمة د مصطفى البغا» | catalog `41b0884a…` |
| 79 | Offline search cannot match a phrase in SQL: the phone index is `detail=none` («phrase queries are not supported»), so phrases are checked in Kotlin over candidates, and bm25 ranks only the first `RankCap` matches in index order (the server does the same with `RANK_CAP` = 10,000). For a common word the order is a sample of the books imported first | `ContentSearch.kt:111`, `:386`; `search-handler.ts:45` |
| 80 | Smaller: Latin «tawhid» finds nothing; an ayah («قل هو الله أحد») finds hadith books, as the index has no Quran (types: book, poem, article, question, term, person) | run 1 |

- [~] 8.1 (2026-11-10, commit 4c150e1b: v2 rows carry `pg` = the app block range, found by aligning each passage's letters with the app parser's blocks; 97.6% placed on every 10th book, 0.4% wrong on every 40th (both fall back to the walk); the Worker returns it, the app opens only those frames (≤4), online or downloaded; test `src/lib/gen-search-index.test.ts`; live only after the 8.2 reindex and the held Worker deploy, not checked on the phone; incremental runs must keep SEARCH_FORMAT=v2) Each index row carries the app's place (first block ordinal, or the stable block id from
      `content-ids`); the app opens that frame directly. `locateOnline`'s walk goes. (#52)
- [ ] 8.2 Full v2 reindex (2,500-char passages, `raw`, `prior`) now that the account is Workers Paid:
      check `size_after` against the 10 GB cap first, raise `SEARCH_ROW_BUDGET`, swap. (#53, #55)
- [~] 8.3 (2026-11-10, phone side commit 6687bb29: with positions the site's tiers (phrase, NEAR 12, every word, prefix; long queries drop particles) run in SQL over the whole library, test in FtsQueryBuilderSqliteM0Test (run with PATH=/usr/bin first: platform-tools' sqlite3 has no FTS5); not checked on the phone. Earlier: NEAR tier in `search-rank.ts` + test, committed, NOT deployed: wrangler deploy was blocked by the permission system; the Kotlin side has no NEAR, the phone index is detail=none) A proximity tier between «exact phrase» and «every word» (FTS5 `NEAR`), same tier in the
      Kotlin search; golden cases shared by both (4.9). (#53)
- [~] 8.4 (2026-11-10, commit 18b9c5c8: `work_edition` table from `edition_of` in person-meta.sql; the Worker folds editions into their work for the per-work cap and shows one passage once across copies; test; needs the person-meta.sql load and the held deploy) One result per work per passage: editions folded under their default (needs 9.1). (#54)
- [x] 8.5 (2026-11-10: flat rows, «title — author» on one line, thin rule between results; compiled, not checked on the phone) Result card redesign: book and author, the passage with the words marked, volume and page.
      Cleaner, fewer elements. Mock first, owner picks.
- [~] 8.6 (2026-11-10, commit 1055c856: the Worker sends `Server-Timing: d1, worker` (cache hits marked), the app logs dns/tcp/tls/send/wait/body and parse per request, `adb logcat -s AtharSearch`; needs the held deploy for the server half, then one run on Sudani data by the owner) Time each stage of one search (DNS, Worker, D1, draw) on Sudani mobile data before
      touching speed; the Worker already logs D1 time.
- [~] 8.7 (2026-11-10: 503 cause from the Worker log: every one is D1 «DB is overloaded. Too many resources consumed within a 10 second window»; one heavy query (3–8 s wall: «الله», 10-word phrases) uses the DB up and the searches in the next ~10 s are refused; the schema probe threw 500 outside the try, now 503. The app retries once on a 503, commit 6a08dc50; Worker side local only, not deployed: long queries drop particles from the every-word tiers, `requiredWords` in `search-rank.ts` + test; the 503 cause and retry still open; then: each tier after the first starts only within a 1.5 s budget, a spent budget ends the page early and `next` carries on; test with a stepped clock; deploy held to the end) First in this phase: a heavy query must not fail the rest. Read the Worker log for the
      503 reason; then a long query runs as phrase/NEAR only (not every-word over 9+ words), and
      one retry on overload. (#72)
- [x] 8.8 (2026-11-10: `pnpm search:golden [base] [--fresh]`, `scripts/search-golden.mjs`; against the live (old) Worker 6 of 20 miss: «من أرتد ثم تاب حبط» at 6, «إنما الأعمال بالنيات» in all three spellings without Bukhari or النووية, «الأصول الثلاثة» absent, «البربهاري» at 4; that is ranking work for 8.2–8.4) The 20 queries above become the golden list with the expected top three (Bukhari for
      «إنما الأعمال بالنيات», his books for «ابن تيمية», …); 8.2–8.4 are judged against it. (#73, #74, #76)
- [x] 8.9 (2026-11-10, local: a page answers `next` = tier.skip and `after=` resumes there, rebuilding only the title-tier exclusions; the site and the app send it and fall back to `offset` on an old Worker; a test walks 100 rows both ways and gets the same rows once each; the in-book panel shows the site's first page as it lands; Worker and site deploy held) Page 2 and later read on from where page 1 stopped instead of rerunning the tiers; in-book
      search shows the first page at once. (#77)
- [ ] 8.10 The 15 large books get real chapters (part of 9.4). (#78)
- [x] 8.11 (2026-11-10, owner chose word positions; commit 6687bb29: new installs and the bundled catalog get detail=full, old installs copy their index in the background in 2k-block chunks and swap; measured: every 50th book by size, 50 books, 181k blocks, 27.1M letters; the phone's `block_fts` as built today (detail=none) 9.4 MB, with word positions (detail=full, needed for phrase and NEAR offline) 19.2 MB: 2.0× the index, +9.8 MB ≈ +8% on the 116 MB that 6.9 leaves for the same sample; switching is the owner's call, then 8.3's Kotlin tier) Offline ranking after 6.9 decides the phone index format; a `detail=full` index costs
      space, so measure it on the same 50 books first. (#79)

## Phase 9 — works, editions, pages

- [ ] 9.1 A `work` id in frontmatter, independent of the slug; `edition_of` resolved through it; the
      audit refuses a new book whose work already exists without a link. (#56)
- [~] 9.2 (2026-11-10: مجموع الفتاوى done, `mjmwa-al-ftawa` is `edition_of: majmu-al-fatawa`; the other groups wait for a per-book look at which copy is better) Round-2 duplicate decisions (`docs/editions-and-duplicates-2026-11-07.md`), مجموع الفتاوى first. (#54, #56)
- [x] 9.3 (2026-11-10, checked on the phone: first page of فضائل مكة now shows ١١, the marker that ends it; volume shown when the book has volumes; no unit test, it lives in the composable) Folio shows the page being read, with its volume; one unit test on a two-page fixture. (#50)
- [ ] 9.4 Audit lists books with no page markers and repeated markers; decide per book: re-import
      through Athar-Engine, or hide page UI for it. (#51)
- [ ] 9.5 «طبعة أثر» rule written into `docs/book-formatting-rules.md`: a typo fix keeps the edition;
      any other change to the text makes it «طبعة أثر» with a change log (place, before, after,
      proof). No code until the first real case.
- [ ] 9.6 New books and articles through Athar-Engine, after 9.1 (owner: later).

## Phase 10 — UI batch (owner's list)

- [x] 10.1 (2026-11-10, checked on the phone: the menu opens right above the share button) Share: the نص/صورة choice is a small AtharMenu at the share button.
- [x] 10.2 (2026-11-10, checked on the phone: «نُسخ ✓» for 1.2 s, then the selection clears) Copy: the icon turns into ✓ for a moment.
- [x] 10.3 (2026-11-10, commit 0db0c5eb: the card follows the reader theme; then: verses drawn staggered, صدر at the start and عجز at the far end, cut whole at the limit; poem selections and book selections of verses only (a mix with prose stays running text); not checked on the phone) Share image follows the theme; better layout; verse as صدر then عجز on two lines, staggered.
- [~] 10.4 (2026-11-10: the action bar label is «بمصدر» so a long selection has the bar width of one word; not checked on the phone) Selection box keeps the one-word size however long the selection.
- [~] 10.5 (2026-11-10, commit 3886ea64: صدر at the start, عجز at the far end, in `ReaderBlockView`; not checked on the phone) Verse inside books drawn like the poem page.
- [~] 10.6 (2026-11-10, owner: a tap on the شرح rule beside a verse opens its شرح in a bottom sheet, «كل الشروح» opens the side panel; the side panel stays for now. Not checked on the phone) Poem commentary redesigned from scratch for the phone (no side column). Mock first.
- [ ] 10.7 (2026-11-10: a neutral splash and an icon intro were tried and reverted at the owner's request; the system default splash is back. Do not touch again unless asked) Coloured start screen: verse takes a colour that reads on each background, not white.
- [x] 10.8 (2026-11-10: the open page is ticked in the ⋮ menu; compiled, not checked on the phone) Pages opened from ⋮ show where you are (title, and the entry marked in the menu).
- [x] 10.9 (2026-11-10: moved from the ⋮ menu to the selection bar (icon only, book and poem readers), mail to admin@arthurarchive.com with book, place and the selected text; compiled, not checked on the phone; check the bar still fits a 360dp phone with six actions) «أبلغ عن خطأ» in the selection menu: opens the mail app with book, place and the selected
      text filled in. No server.

## Phase 12 — content fixes (owner, 2026-11-10)

- [ ] 12.1 Poems set inside books (منظومات, e.g. ملحة الإعراب) drawn as verse everywhere: find every
      book whose text holds a poem as prose lines and mark its verses (صدر/عجز) in the source.
- [ ] 12.2 Books with stray formatting, first case: English «quote» markers showing as plain text in
      the reader. Audit for leftovers like it and fix in the source.
- [ ] 12.3 Poem titles: every معلقة shows and its title carries «معلقة».
- [ ] 12.4 Poetry by era (جاهلي، صدر الإسلام، أموي، عباسي…) instead of centuries: a filter that shows
      only the poets of one era and their poems.

## Phase 11 — later, by decision

- Smart search: owner prefers a self-built index on R2 over Vectorize (whole library on Vectorize is
  ~$12/month from stored dimensions alone). Hard rule: it turns itself off before it can cost money.
  Pilot on the 52 mutoon and their شروح first.
- Android build + unit tests in CI (#57); crash capture (#58) — decide with 10.9.
- Commit or revert the 972 audio url edits (#59): owner to say which.

## Done when

Every box is ticked, each phase's tests pass, and on the Redmi 12 «تنزيل الكل» runs while a book
opens and a list scrolls without a stall (measured with a trace, before and after).
