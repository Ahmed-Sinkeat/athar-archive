# Android v1 work — 12 October 2026

This is development progress, not release certification. The Redmi 12 runs the updated
debug app; its signed staging origin still serves one article, book, question and poem.
The latest page-notes UI debug build was installed with an update that preserves data
on 12 October at 18:17 local time. A cold launch returned Status: ok; the app process was running.

| Area | Implemented in this working tree | Verification / remaining work |
|---|---|---|
| Catalog | Published article/book/book-lg/question/poem discovery; Arabic topic names; author metadata; all attached published recordings; durable tombstones; separate slice output | Discovered 4,240 published sources: 1,984 articles, 1,195 books, 953 questions, 108 poems. Local unsigned generation completed: 4,240 texts and 117 recordings. Final full-bundle verification has not run; no new root has been published. Publication needs the owner's signing/R2 credentials. |
| Reader | Bounded ordinal paging and frame-by-frame whole-book operations within cache budgets; saved position; TOC/page/volume jumps; whole-book text search; inline links/styles; complete page notes despite malformed import numbering; font/spacing/margins/palette/tashkeel preferences | Build and parser checks; visual, accessibility and device-tier performance certification remain unrun. |
| Annotations | Multi-block native selection, exact character overlays, editable notes, bookmarks, filtered/grouped notebook, attributed sharing and source reopening | JVM checks cover range capture and reattachment. Partial content does not falsely orphan notes; approximate recovery preserves original anchors. |
| Library | Durable reading shelves and user collections; catalog and local-text search | Existing Room/Flow paths retained; no fixture data is passed by the navigation host. |
| Downloads | WorkManager progress, pause/resume/cancel/retry; interrupted file recovery; text retention and audio verification; foreground notifications | Updated app installs. New in-memory Room tests compile but Xiaomi rejects installation of the separate test APK. |
| Audio | Media3 service, Android session/notification controls, real queues and playlists, seek/speed/repeat/sleep/resume, authenticated downloads and fractional poem cues | Real Opus streaming reached PLAYING then PAUSED at 32,502 ms; force-stop/relaunch restored 32,502 ms. Original playback preferences restored byte for byte. Remaining controls have build verification only. |
| Backup | Transactional bounded JSONL import/export; validation, duplicate/newer-local merge; appearance backup and device-only playback transfer | JVM archive checks pass. Malformed-import rollback and malformed-package replacement tests compile; device execution is blocked by test-APK installation. Media/playback preferences are outside the SAF archive. |
| Release | Production origin/key and keystore requirements enforced by a configuration-cache-compatible Gradle check | Guard correctly refuses missing production configuration. No release APK or signed full generation has been published. |

## Checks

- `:core:data:testDebugUnitTest`, `:core:data:assembleDebugAndroidTest`,
  `:app:assembleDebug`, `:app:lintDebug`: passed after final reader hardening: 32 core JVM tests, no failures.
- `pnpm exec vitest run src/lib/app-v2`: 27 tests passed.
- Targeted TypeScript compilation: passed after parser fixes.
- Publisher `--selftest`: passed signature/hash-chain validation and tamper rejection.
- `:app:verifyReleaseConfiguration`: expected failure naming missing production content
  configuration; configuration cache stored successfully.
- Phone: update install, cold launch, Media3 play/pause and process-restart position
  restoration passed. No user database was cleared or uninstalled.

The small-document audit passed all 3,927 sources (804,705 blocks). Regression checks
cover imported HTML/position markers, nested footnotes/lists/quotes, page markers, styled
verses, literal backticks and repeated page numbers across volumes. The audit of the
remaining 249 large sources found three additional forms; all three now pass targeted
rechecks. A deeper artifact check found malformed footnote numbering in 15 books.
After repair, all 29,314 notes across those books are retained, with zero missing
references or dropped page notes. The reader exposes page notes directly. Full unsigned
packaging completed locally. Earlier partial artifacts and stable-ID staging remain under
ignored `dist/app-content/`; the full catalog is generated; final full-bundle verification remains pending.

GUI/screenshot/e2e tests and performance benchmarks were not run, following the requested
lean-check scope. Android 26/low-memory and tablet certification are still outstanding.

The generator now stages stable IDs on the output disk, after the first full attempt
hit the RAM-backed temporary filesystem quota. All 27 content tests and targeted
TypeScript checks passed after this correction. All 117 published recording URLs
passed HTTP availability and declared-size checks. Production signing/publication,
Room device checks and full-bundle validation remain incomplete.

The full-run metadata audit found two book links to nonexistent recordings; those stale
links were removed. Published text now omits draft recordings (853 existing references)
without publishing their audio. All 117 published recordings have valid parent metadata.
One poem had two cues beyond its actual 512.607-second duration (verified with ffprobe);
those invalid cues were removed, retaining all poem text. All three published timing
files now pass the cue preflight. The draft-recording regression passes in the 27-test suite.

A later full run reached the poems after packaging every book, but a recording response
ended with ECONNRESET. The audio loader now retries the complete transfer up to five
times, including interrupted response bodies; its regression passes with all 27 content
tests and targeted TypeScript checks. The retry regression passes; later local generation used verified downloaded recordings.

The whole-package footnote-count audit found 88 books with repeated page identifiers,
reused Markdown labels or unreferenced definitions. Page occurrences now receive distinct
note IDs; ambiguous reused Markdown labels retain both bodies and styling; unreferenced
notes remain accessible beside their source definitions. All 88 books passed a source
recheck covering 593,504 retained, uniquely identified notes. Both remaining recordings
finished resumable downloads and passed size/ffprobe checks. All 117 recordings are now
available locally; generation completed using SHA-256-verified local artifacts,
independent of the unstable network. Final full-bundle checks remain pending.

Work stopped at the user's explicit request. The full generation had completed before
the stop: generation `2ef13f29be3a0979`, 4,240 texts and 117 recordings. No build is running.
The final full-bundle checker has not run, and this unsigned generation is not published.
The latest APK update and cold launch now pass. Room device checks, full-bundle validation
and release certification remain outstanding.

Owner instruction: do not publish the app or upload new app content until explicitly authorized.

## 2026-10-14 — overnight fix pass (uncommitted, unpublished)

- Amiri re-subset from Amiri 1.003 (adds ASCII/Latin-1/punctuation; fixes reversed parentheses). No codepoints dropped.
- Reader: page tap toggles the top bar (it used to stay hidden after the position restore); fixed the blank long-press selection; a tap dismisses the selection.
- Arabic count grammar (`arabicCount`) across library, search, player, kannashah, and poem panel; ArabicCountTest.
- Duplicate title/source lines hidden in the mini-player, audio cards, and player; tap targets raised to 48dp.
- Search no longer shows recents while searching, and has an empty-state hint. Settings storage label made clearer.
- Pipeline: parser `prose` guard (alfiyyah ibn malik 1111→1089 verses); alfiyyah-al-iraqi merged verses split. Needs regeneration plus an authorized publish before it reaches the app.
- Not done: the reader's الجزء field for single-volume books; dead feature/home and feature/adhkar screens (a product decision); audit items 30/35/36/38.
- Follow-up, same day: the poem ⋮ is now a menu (copy, share, continuous text) instead of jumping into the book reader. A shared `AtharMenu` (ported from Sada's AnchoredMenu) replaces all six Material dropdowns. The selection page flings. The reader progress bar is fractional and reaches 100% at the end.

## 2026-10-14 — one body, real content on the phone, open speed (uncommitted, unpublished)

- The phone now runs the benchmark build against the **full** signed generation served from a local
  HTTPS server (`adb reverse`); the catalog of 4,240 items loads and every collection opens.
- First open of a new book measured at about 9 s (2,000-block pieces, doubled import, extra
  transfer-state writes). Frames are now 250 blocks, the duplicate open is removed and the
  read-through path no longer writes `entity` rows: about 0.5 s of app work.
- Loading indicators in the reader and poem reader; the app's own text-selection toolbar replaces
  the platform bar.
- Owner feedback from the same session (catalog in the app, prefetch policy, download problems,
  selection, colours, settings) is recorded in
  [`2026-10-14-backlog.md`](2026-10-14-backlog.md). None of it is implemented yet.
- Caveat: `android/app/build.gradle.kts` was reconstructed after an accidental revert — see the
  backlog §G.


## 2026-10-21 — before-publish continuation (uncommitted, unpublished)

Approved W1–W11 implementation is in the working tree; detailed states and remaining owner decisions are in [beforepublish.md](../beforepublish.md). The final signed local generation is `4a453251810def09`: 4,238 texts, 593 people and all 117 published recordings. Full artifact verification passes over 22,413 frames and 4,751,870 blocks. Both motion-preview benchmark APKs build and contain the exact verified catalog snapshot. TypeScript: 185 tests + type-check; Android: 26 app + 43 core JVM tests pass.

The phone was disconnected before the final walk. Preliminary footnote/continuous/poem/menu flows were walked; refreshed people, editions, cards and full poem loading still need device checks. Room instrumentation remains blocked by Xiaomi’s test-APK installation restriction. Android 26/low-memory and tablet certification remain unrun.

Release is **not certified**: the source audit reports 290 findings, and the explicit page-order census records 11,378 backward marker pairs in 146 imports. No printed pages were automatically renumbered. Three edition defaults, library-wide kind exceptions, the motion choice and publication itself still need the owner. Nothing was committed or uploaded.
