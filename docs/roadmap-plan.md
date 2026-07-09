# Athar Archive — problems.txt roadmap

**Status (2026-07-07): all 6 phases shipped.** Phases 1-4: commit `3ede7ae`.
Phases 5-6: see their sections below for what actually shipped (differs in
places from what was originally scoped — R2 diffing and Astro 7 turned out
to already/already-plan-to be done, Phase 6 kept the old importer as
fallback rather than retiring it). `proplems.txt` itself was deleted once
this doc fully superseded it.

## Context

`proplems.txt` lists ~20 issues. Confirmed priorities with the user, in order:
**1)** user-friendly admin content editing, **2)** reader UX fixes, **3)** content/import bugs,
**4)** cleanup, **5)** CI/CD + storage optimization, plus **6)** moving the importer to Athar Engine.
Too big for one pass — execute phase by phase, each phase is one working session, verified before the next.

Confirmed details: the zoom problem = text column stays narrow when zooming out (wants editor-style re-wrap);
CMS direction = prototype candidates and pick with evidence; comments (giscus) = low priority.

## Opinion answers (no code, recorded here)

- **giscus**: technically ideal for a static site (free, no server, spam-proof) but every commenter needs a GitHub account — wrong fit for a general Arabic audience. When comments get scheduled, a lightweight self-hosted option on the existing Cloudflare D1 is the better path. Deferred per user.
- **Astro 7** (released 2026-06-22): ~~don't upgrade yet~~ — **done**. Upgraded after Phase 1 removed Keystatic as planned (`astro: ^7.0.6` in `package.json`); the content pipeline keeps its own remark/rehype sanitize chain (`src/lib/sanitize.ts`) independent of Astro's Sätteri default, so that risk never materialized.

---

## Phase 1 — Admin content editing (top priority)

Goal: admin adds/edits/removes content from a browser; every save is a GitHub commit; CI deploys.

1. **Spike both candidates against the real repo** (branch each):
   - **Pages CMS** — one `.pages.yml` describing the collections; GitHub-app login; modern UI.
   - **Sveltia CMS** — single static `admin/index.html` + `config.yml`; strong i18n; very active.
   - Model the real collections from `keystatic.config.ts` / `src/content.config.ts` (book, poem, person, benefit, article, question, topic, term, audio, annotation, highlight, announcement).
   - Test with real content: Arabic/RTL editing quality, a large book body, relationship fields (person/topics), slug validation, add + delete flows.
2. **Pick the winner, wire it as `/admin`, remove Keystatic**: `keystatic.config.ts`, `@keystatic/*` deps, `patches/@keystatic__astro.patch`, the `PUBLIC_KEYSTATIC_*` CI env, and react/react-dom if nothing else uses them (check `@astrojs/react` usage first — force-graph pages may).
3. Known ceiling to state in the report: no git-backed CMS edits multi-MB book bodies comfortably — whole-book ingestion stays with the importer (Phase 6); the CMS is for metadata, normal-sized entries, and deletions.

Fallback if both fail on Arabic/RTL: keep Keystatic but hide technical fields and improve labels.

## Phase 2 — Reader UX

1. **One search per list page**: on `books.astro` / `poems.astro` / `matn.astro` etc., replace `SectionSearch` (full-content API dropdown) with a client-side **title-only filter** over the already-rendered cards. Header global search (in `Base.astro`) stays the single deep search.
2. **In-book search**: `Base` already scopes header search with `searchScope={in: entry.id}`; add a visible "ابحث في هذا الكتاب" control in `StudyBar.astro` hitting `/api/search?in=<id>`.
3. **Sharh side panel** (books + poems): replace the current linked-bottom-sheet pattern — wide screens get a docked, resizable panel beside the text (opposite the TOC side) with a shorooh switcher; small screens get a **floating, movable, resizable popup** with the same switcher. Files: `StudyBar.astro`, annotation packs in `src/pages/book/[slug].astro` + `poem/[slug].astro` (+ chapter pages), the bottom-sheet JS/CSS in `Base.astro`/global styles.
4. **Quran/tafsir menu**: same pattern as sharh — tafsir sidebar on desktop, clean popup on shrink; kill the bottom bar. File: `src/pages/quran/[surah].astro`.
5. **Article TOC sidebar**: headers tree for articles, reusing `parseToc` from `src/lib/chapters.ts`.
6. **Zoom/measure fix**: reading-width control (presets + "ملء الشاشة" that lifts the rem cap, persisted in localStorage) so zooming out re-wraps lines into the freed space.
7. **Kunasha (benefits) reorganization**: `benefits.astro` groups by source book by default, switchable to topic / person (reuse `buildSubjectGroups` pattern in `src/lib/browse.ts`).
8. **Rename** articles section to **مقالات ومحاضرات** (nav, titles, breadcrumbs).

## Phase 3 — Content/import bugs

1. **Pre-heading text breaks layout** (e.g. `al-sunnah-abdullah-ibn-ahmad.md` — 74 lines of body before the first `##`): fix `analyzeBook` in `src/lib/chunk.ts` to emit the preface as its own leading chapter/section instead of mangling it.
2. **Poem artifacts**: one-time normalization script over `src/content/poem/` — strip the `(...)` wrappers / unbalanced parens (السفارينية has `... (` line endings), and remove embedded original verse numbers where the site numbers verses itself (the "two numbers" bug). Same rules go into the importer so future imports are clean.
3. **Poems inside books/matn look wrong**: fix verse rendering when poetry appears inside book `Prose`, and stop treating poem front-matter prose (info/intro) as verses in `analyzePoem`.
4. **Heading policy for small books**: books under ~100 pages don't get h1/h2 chapter-chunking — render whole with an h3/h4 inline TOC (threshold change in `analyzeBook`; user cites الواسطية and the fiqh books as the model).

## Phase 4 — Cleanup

- Delete `scripts/pagefind-spike/` and purge stale Pagefind references (`docs/asbuild.md`, `deploy.md`, `migration-plan.md`, `media-and-backup.md`, comment in `book/[slug].astro`); refresh docs to current reality.
- **Footer gap**: `min-height:100svh` flex column on `Base.astro` so the footer hugs the bottom with no dead band.
- **Smoothness pass**: button `:active`/transition states, tap feedback, scroll behavior audit.
- ~~Delete `proplems.txt` once everything is tracked here.~~ Done — deleted 2026-07-07, fully captured in this doc.

**Status: shipped** (commit `3ede7ae`, "Phases 1-4").

## Phase 5 — CI/CD + storage optimization ✅ done (2026-07-07)

- **R2 upload diffing**: turned out `upload-r2-assets.mjs` already diffs against a remote md5 index and only PUTs changed keys — the "re-puts everything" comment in `ci.yml` was stale, not the actual behavior. Fixed the comment only.
- **Search indexing**: `gen-search-index.ts` is now incremental — diffs against a remote `doc_hash(url, hash)` snapshot (`pnpm search:hashes`) and only emits SQL for docs that actually changed; falls back to a full rebuild when no snapshot exists yet. Re-enabled the `ci.yml` steps that were `if: false`. Also fixed a real O(n²) bug in the SQL-writer loop found while testing this (`Buffer.byteLength` was rescanning the whole output buffer per doc — took a 22k-doc reindex from 15+ minutes, never finishing, to ~9 seconds).
- **LFS/storage audit**: `audio/` (local rclone staging dir for `scripts/upload-media.sh` → the `athar-media` R2 bucket) was already correctly untracked/never committed — just added it to `.gitignore` so it can't be committed by accident. Media was never meant to live in git/LFS, only its `r2.arthurarchive.com` URL in frontmatter (see `media-and-backup.md`).

## Phase 6 — Importer → Athar Engine ✅ done (2026-07-07)

`epub-import.ts` is **not** retired — Athar-Engine's compiler is mature (all 10 quality domains "Locked"), but that's only proven against the 12-14 books it was locked against, not the current 18-book corpus or arbitrary new books, so keeping it as a fallback was the safer call. What shipped instead: `scripts/ingest-new-book.mjs` in the **Athar-Engine repo** — takes a compiled book from `canonical-corpus/*/output/` and writes a new athar-archive content file with frontmatter (companion to the existing `sync-website-bodies.mjs`, which only updates books that already have a content file). Proven on 13 real books so far: سيرة ابن هشام, then 12 early-salaf works (Abu Ubaid al-Qasim ibn Sallam ×9, Al-Khallal ×2, Ibn Taymiyyah ×1, Ibn Zanjawayh ×1).

## Deferred (per user)

Comments system (giscus verdict recorded above) · شجرة الرواة people-tree · linking books to usul al-hadith · Astro 7 upgrade (after Phase 1— **done**, see `docs/HANDOFF-visual-redesign.md`'s branch note).

Article/kunasha visual redesign: **partially done** (2026-07-07) — كُناشتي (`/benefits`) now matches the Claude Design mockup (color-coded tabs, grouped box with dividers, centered quotes, count badge). Articles page itself not yet touched.

## Verification (every phase)

`pnpm test` · `pnpm validate:content` · `pnpm build` · `pnpm smoke` · `pnpm check:links` · `pnpm perf:budget` — plus `astro dev` eyeball for UX phases (user reviews look themselves).
