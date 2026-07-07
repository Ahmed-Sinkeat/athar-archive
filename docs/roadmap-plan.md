# Athar Archive — problems.txt roadmap

## Context

`proplems.txt` lists ~20 issues. Confirmed priorities with the user, in order:
**1)** user-friendly admin content editing, **2)** reader UX fixes, **3)** content/import bugs,
**4)** cleanup, **5)** CI/CD + storage optimization, plus **6)** moving the importer to Athar Engine.
Too big for one pass — execute phase by phase, each phase is one working session, verified before the next.

Confirmed details: the zoom problem = text column stays narrow when zooming out (wants editor-style re-wrap);
CMS direction = prototype candidates and pick with evidence; comments (giscus) = low priority.

## Opinion answers (no code, recorded here)

- **giscus**: technically ideal for a static site (free, no server, spam-proof) but every commenter needs a GitHub account — wrong fit for a general Arabic audience. When comments get scheduled, a lightweight self-hosted option on the existing Cloudflare D1 is the better path. Deferred per user.
- **Astro 7** (released 2026-06-22): don't upgrade yet. What it offers this site: Rust compiler + Vite 8/Rolldown → much faster builds (real win for a content-heavy site); Sätteri as the new default Markdown processor; streaming rendering by default; stricter HTML validation (breaking). The risks here: the custom remark/rehype sanitize pipeline (`src/lib/sanitize.ts`, rehype-raw/sanitize deps) vs the new Sätteri default, inline HTML in content (`<hr class="page-sep">`) vs stricter validation, the pinned `vite: 7.3.5` override, and patched `@keystatic/astro`. Revisit **after Phase 1 removes Keystatic** (the main blocker) — then upgrade for the build-speed win, keeping the legacy markdown pipeline if Sätteri breaks the sanitize chain.

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
- Delete `proplems.txt` once everything is tracked here.

## Phase 5 — CI/CD + storage optimization

- **R2 upload diffing**: `upload-r2-assets.mjs` currently re-puts ~15k objects every deploy (unproven 40min+ step). Keep a hash manifest, upload only changed keys.
- **Re-enable search indexing** (`if: false` steps in `ci.yml`): make it incremental/diff-based so it fits D1's 100k writes/day instead of a 30k-row full reindex per deploy.
- **LFS/storage audit**: ~290MB tracked in LFS against a 1GiB/month quota; move audio/media to R2-only and out of git where possible (new `audio/` dir at repo root is a candidate).

## Phase 6 — Importer → Athar Engine

- Retire `scripts/epub-import.ts` (1420 lines) as the import path; `~/Projects/Athar-Engine` is already the deterministic EPUB→Markdown compiler. Archive keeps a thin "ingest compiled markdown + frontmatter" step. Poetry paren/numbering rules from Phase 3 land in the Engine.

## Deferred (per user)

Comments system (giscus verdict recorded above) · شجرة الرواة people-tree · linking books to usul al-hadith · article/kunasha visual redesign (waiting on user's new design) · Astro 7 upgrade (after Phase 1).

## Verification (every phase)

`pnpm test` · `pnpm validate:content` · `pnpm build` · `pnpm smoke` · `pnpm check:links` · `pnpm perf:budget` — plus `astro dev` eyeball for UX phases (user reviews look themselves).
