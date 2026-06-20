# athar-archive — As-Built Record

This document tracks what has **actually been built**, phase by phase, against the
[`BUILD-PLAN.md`](../../athur/BUILD-PLAN.md) (design corpus). It records deliverables,
Definition-of-Done (DoD) status, decisions, and any deviations from the plan.

> Updated after every phase. Companion: [`structure.md`](./structure.md).

**Project:** أهل الأثر — Arabic Islamic knowledge archive
**Stack:** Astro (static) · Markdown + Zod Content Collections · Pagefind (search, P4) · Cloudflare Pages/R2 (P6/P8)
**Last updated:** P6 complete · audio player + attachments + R2 docs · 66/66 tests

---

## Phase status

| Phase | Title | Status | Commit |
|---|---|---|---|
| **P0** | Foundations & de-risking | ✅ Done (spike now run in P4) | `0c93dc1` |
| **P1** | Content model in code | ✅ Done | `cc3d818` |
| **P2** | Build pipeline & derivations | ✅ Done | `1ef6b93` |
| **P3** | Design system & page templates (RTL) | ✅ Done | `3fba81b` |
| **P4** | Search (Pagefind, Arabic) | ✅ Done | `23e6781` |
| **P5** | SEO, structured data, feeds, permanence | ✅ Done | `51059cd` |
| **P6** | Media pipeline (R2) | ✅ Done (code+docs; R2 not yet provisioned) | `f9b86f7` |
| P7 | Authoring experience & governance | ⬜ Next | — |
| P8 | QA, performance, accessibility, launch | ⬜ Pending | — |
| P9 | Post-launch & deferred roadmap | ⬜ Pending | — |

¹ The Pagefind Arabic spike (P0's gate for P4) is **not yet run** — see Open Items. It does not block P1–P3, so the build proceeded; it must be done before P4.

---

## P0 — Foundations & de-risking ✅

**Built**
- Repo init: Node LTS + pnpm + Astro 6 + TypeScript (strict); `.editorconfig`, `.gitignore`, `.npmrc` (build-script allowlist), `pnpm-workspace.yaml`.
- `ahlalathar.config.ts` — tunable config (domain, `poemChapterThreshold: 200`, `bookChapterThreshold: {words:6000,chapters:8}`, mailto, `topicsMax: 5`).
- `astro.config.ts` — static output, `site: ahlalathar.net`, `trailingSlash: never`, RTL `i18n` (ar).
- `public/robots.txt`; placeholder `src/pages/index.astro` (RTL).
- Astro telemetry disabled.

**DoD**
- ✅ `pnpm build` passes in CI-equivalent local run.
- ⏳ Pagefind spike — **deferred** (tracked in Open Items; gates P4 only).
- ⬜ Branch protection — N/A locally (no remote yet).

**Deviations from plan**
- **Pagefind spike not run yet.** Plan lists it as P0's first task. Deferred because it gates P4, not P1–P3, and we wanted the model/pipeline spine first. Must run before P4.
- **No remote / CI service yet.** Git is local-only; "green CI" is satisfied by the local `pnpm build`. Add remote + CI when hosting is set up (P8 territory).

---

## P1 — Content model in code ✅

**Built**
- `src/content.config.ts` — all **13 collections** with typed Zod schemas: person, subject, topic, book, poem, series, lesson, question, benefit, article, audio, annotation, announcement.
  - Shared fields (`status`, `published_at`, `updated_at?`, `aliases[]?`), slug regex, topic cap (1–5), polymorphic `source_type/source_id` with `.refine()` both-or-neither guards.
- `src/lib/validate.ts` — cross-entity build-time validator. Rules: id slug format, mandatory relations (Topic→Subject, Lesson→Series, Person on book/poem/series/benefit/article), ref resolution, draft-ref guard, transcript gate, SourceType enforcement, topic-ref resolution.
- `scripts/validate-content.ts` — CLI runner; wired into `pnpm build` (runs **before** `astro build`).
- **19 seed fixtures** across every entity + edge cases (long/short poem, published + draft lesson, benefit with/without source, valid annotation).
- Vitest suite for the validator.

**DoD**
- ✅ Valid fixtures build green; intentionally-broken inputs fail with precise messages (proven via unit tests, not committed broken fixtures — see decision D4).
- ✅ CI-equivalent runs the validator suite.

**Deviations from plan**
- **D1 — Config file location:** Astro 6 requires `src/content.config.ts` (not the plan's `src/content/config.ts`). Moved; legacy location errors out in Astro 6.
- **D2 — Slug pattern:** widened to `^[a-z0-9]+(--?[a-z0-9]+)*$` to allow `--` child separators (`series--lesson-n`, `target--anchor--kind`), which the plan's own id scheme requires.
- **D4 — Broken fixtures:** the plan suggested committing intentionally-broken fixtures (e.g. a dangling annotation) to prove failure. Instead, failure cases live in **unit tests** so `main` stays buildable. Same guarantee, green tree.

---

## P2 — Build pipeline & derivations ✅

**Built** (all in `src/lib/`, each with a colocated `*.test.ts`)
- `graph.ts` — in-memory knowledge graph (replaces a DB at Phase-1 scale, FR-B-05): materials-by-topic / -subject / -person, ordered lessons-by-series, reverse polymorphic lookups (annotations/audio/benefits/series by source), and **derived series stats** (lesson count, published count, summed duration). `toContentEntries()` adapter for Astro `getCollection` at page runtime.
- `chapters.ts` — `## …` chapter splitting; Poem verses `{n, sadr, ajz?, anchor}` with global numbering; Book paragraph anchors (explicit `{#id}` or auto `p{n}`); Lesson heading TOC; **Arabic-aware slugify** (strips tashkeel/tatweel); `extractAnchors()`.
- `chunk.ts` — threshold-driven single-page vs chapterized from the **same source** (poem >200 verses; book >6000 words or >8 chapters), with single-page fallback when chapters can't be formed.
- `sanitize.ts` + `sanitize-schema.ts` — Markdown→safe HTML via unified (remark → rehype-raw → rehype-sanitize). Neutralizes `<script>`, inline event handlers, `javascript:` URLs. Shared schema also wired into Astro's markdown config.
- `types.ts` / `load.ts` — shared `ContentEntry` + disk loader (DRY across validator, graph, tests).

**Validator enhancement**
- Annotation **anchor resolution**: `anchor` must resolve to a real position (verse/paragraph) in the target body; out-of-range anchors fail the build (`anchor-resolution` rule).

**Model enhancement**
- Removed hand-stored `verse_count` / `opening_verse` from the poem schema + fixtures — now **derived only** (FR-C-06).

**DoD**
- ✅ Same poem source renders one-page below / chapterized above threshold (chunk tests).
- ✅ Anchors resolve (validator + `extractAnchors`).
- ✅ Raw `<script>` neutralized in output (sanitize tests).
- ✅ Derived stats match fixtures (`sharh-al-wasitiyyah` → 2 lessons, 1 published, `2:10:54` total).

**Deviations from plan**
- **D3 — verse_count/opening_verse:** P1 had carried these as optional frontmatter; P2 made them strictly derived per FR-C-06 and dropped them from schema + fixtures.
- **D5 — sanitize approach:** plan named `rehype-sanitize`; we use it inside a standalone unified pipeline (`sanitize.ts`) **and** wire the same schema into Astro's markdown config — the standalone path is the controllable, unit-tested one likely used for content bodies in P3.
- **Config → TypeScript:** `astro.config.mjs` → `astro.config.ts` so it can import the shared sanitize schema. `tsconfig.json` dropped deprecated `baseUrl` (TS 6).

---

## P3 — Design system & page templates (RTL) ✅

**Source of truth:** the final mockup at `athur/Book and Poetry Design/` (the user's last iteration) — *not* BUILD-PLAN §0.6. Ported its exact tokens, fonts, header, reading controls, verse/annotation styling, and screen layouts.

**Key translation:** the mockup is a single-page app with JS-toggled `data-screen` sections; the production site is a **static multi-page** Astro build where content renders fully without JS (FR-P-05). Reading prefs / theme / annotations became progressive enhancement.

**Built**
- `src/styles/global.css` — design system: exact tokens, three themes (فاتح/ورقي/داكن via `[data-theme]`), `--reading-scale`, and component classes (header, drawer, cards, verses, notes, prose, lesson reader, home).
- `src/layouts/Base.astro` — RTL shell: 3-row header (brand / nav / reading settings), mobile drawer, scroll-progress bar, footer; **pre-paint inline script** applies saved theme/scale/prefs before first paint (no flash).
- `src/scripts/reader.ts` — enhancement: font size, tashkeel toggle (caches full/bare HTML per `[data-ar]`), verse-numbering toggle, theme switch/cycle, drawer, progress; persisted to `localStorage`. Bundled by Astro (inlined as a module script).
- Components: `Breadcrumbs`, `EntityCard`, `Prose` (sanitized body), `Verse` (verse + **JS-free `:target` annotation reveal**, stacked شرح/حاشية/تخريج/إعراب notes).
- All routes: home; indexes (books/poems/series/subjects/topics/people/articles/benefits/questions); readers (poem, book/matn, series, lesson, person, subject, topic, benefit, article, questions); about/contact/404; search stub.
- `src/lib/display.ts` (Arabic-Indic numerals, `hrefFor` route map, entity labels, `stripTashkeel`) and `src/lib/site.ts` (`loadGraph`, name maps).

**Model / pipeline enhancements**
- Annotation gains a `kind` enum (شرح/حاشية/تخريج/إعراب) driving the note label/accent.
- `sanitize.ts` adds an Arabic heading-id rehype step so lesson-TOC anchors match rendered heading ids (verified: `#المقدمة` ↔ `<h2 id="المقدمة">`).

**DoD**
- ✅ All routes render from fixtures (29 pages built).
- ✅ JS-disabled pass: verses, prose, breadcrumbs, internal links all server-rendered; annotations reachable via `:target`; defaults (light theme, scale 1, tashkeel + verse-numbers shown) are fully readable.
- ✅ Breadcrumbs + internal links present everywhere.
- ⏳ a11y: baseline in place (RTL, `lang=ar`, focus-visible, aria-current/pressed/labels). Full axe/keyboard/contrast audit is P8.

**Deviations from plan**
- **D6 — Tokens from the final mockup, not BUILD-PLAN §0.6.** The user directed us to `Book and Poetry Design` as the real design; its palette (`--paper:#FAF7F0`, `--accent:#9A2F24`, …) and the ورقي/داكن themes supersede §0.6's values.
- **D7 — SPA mockup → static MPA.** Screens became routes; the mockup's client router/`data-screen` toggling is replaced by real pages.
- **D8 — Annotations revealed JS-free via `:target`** (anchor link → note block), enhanced by `reader.ts`, instead of the mockup's right-click/long-press (which would gate šarḥ behind JS).
- **D9 — `/books` is the combined "المكتبة"** (books + poems, filter chips), matching the mockup's library + the nav; `/poems` remains a poems-only index (footer).

---

## P4 — Search (Pagefind, Arabic) ✅

**Spike first (the deferred P0 gate, the #1 technical risk).** Ran *real* Pagefind searches in headless Chromium (playwright-core + system chromium) over a controlled 2-page corpus (`scripts/pagefind-spike/`), one diacritized, one stripped.

**Findings**
- **Diacritics fully handled.** `كلام` and the fully-diacritized `كَلَامُنَا` both match diacritized text — normalization happens on index *and* query side. The planned diacritic-stripped match field is **unnecessary**; we index visible diacritized content directly (real excerpts).
- Most bare-root queries work across the definite article and clitics (`إيمان`→`الإيمان`, `صفات`→`وصفاته`).
- **Edge case:** some hamzated/proclitic words miss (`أسماء`↛`بأسماء`) — prefix/normalization rough edge. Acceptable for Phase 1.
- **Verdict: GO with Pagefind.** Escalation to Meilisearch/Typesense stays documented if real-content QA degrades.

**Integration**
- `Base.astro`: `searchMeta` prop marks `<main>` as `data-pagefind-body` + `data-pagefind-meta(title)` + `data-pagefind-filter(type)`; chrome (header/drawer/footer) and `Breadcrumbs` are `data-pagefind-ignore` → **only content detail pages are indexed** (15 pages). Added `noindex` prop.
- `searchMeta` on all 12 detail templates (+ chapter pages) with per-type values.
- `/search`: custom RTL UI on Pagefind's JS API — reads `?q`, live (debounced) search, type facet chips, diacritized titles + `<mark>` excerpts, graceful no-JS (`<noscript>`) and no-index (dev) fallbacks. No server, no keys (SEC-01).
- `pnpm build` runs `pagefind --site dist`; added `search:index` script.

**DoD**
- ✅ Queries with/without تشكيل return correct results across متون/منظومات/دروس/مقالات/فوائد/مسائل (verified on real fixtures via `verify-real.mjs`: «التوحيد» → article+benefit+subject+topic+person; «كلامنا» → الألفية; «توقيفية» → المسائل).
- ✅ No key/secret client-side; index regenerates every build (15 pages, 1 filter).
- ✅ `/search` results are `noindex`; graceful degradation present.

**Deviations from plan**
- **D10 — No diacritic-stripped index field.** The spike proved Pagefind normalizes Arabic diacritics, so the BUILD-PLAN's stripped-field mitigation is dropped as redundant; we index the visible diacritized text (nicer excerpts).
- **Custom search UI** (Pagefind JS API) instead of the default Pagefind UI component, to match the manuscript design.

---

## P5 — SEO, structured data, feeds, permanence ✅

**Domain ratified: `ahlalathar.com`** (the last open decision). Single source of truth in `ahlalathar.config.ts` (`siteUrl`); all `.net` references updated (config, astro.config, robots, audio fixture).

**Built**
- `src/lib/structured-data.ts` — per-type JSON-LD builders, each with a `BreadcrumbList`: `ProfilePage`+`Person`, `Book`, `CreativeWork`/`Poem`, `Course` (series), `LearningResource` (lesson), `Quotation` (benefit), `Article`, `QAPage` (questions), `CollectionPage` (subject/topic), and `WebSite`+`SearchAction` on home.
- `Base.astro` — canonical URLs, Open Graph + Twitter per page, `og:type` per entity, `jsonLd` slot, `noindex`↔canonical switch.
- `src/pages/sitemap.xml.ts` — 27 indexable URLs with `lastmod` (from `updated_at`/`published_at`); excludes `/search` (noindex) and 404; includes chapter routes when chunked.
- `src/pages/rss.xml.ts` — latest 30 materials by `published_at`, XML-escaped.
- `scripts/gen-redirects.ts` → `dist/_redirects` — `aliases` → 301 (`/poem/bayquniyyah` → `/poem/al-bayquniyyah`); `archived` keeps its URL. Wired into `pnpm build`.
- `public/_headers` — CSP (allowances for Google Fonts, Pagefind wasm, R2 media), `nosniff`, `frame-ancestors none`, referrer policy; `robots.txt` disallows `/search` + sitemap pointer.

**DoD**
- ✅ Structured data validates per type (15/15 JSON-LD blocks parse; types match URL Map §06).
- ✅ Sitemap/RSS correct (27 URLs / 30-item cap, `.com`).
- ✅ Renamed fixture's old URL 301s to the new one (verified in `dist/_redirects`).
- ✅ CSP present (`dist/_headers`).

**Deviations**
- **`_redirects` via post-build script, not a page.** Astro treats `src/pages/_redirects.ts` as private (leading underscore), so a `tsx` script writes `dist/_redirects` after `astro build` (same pattern as Pagefind).
- Hand-rolled sitemap/RSS (no `@astrojs/*` deps) for control over `lastmod`, noindex exclusion, and entity-aware routes.

---

## P6 — Media pipeline (R2) ✅ (code + docs; bucket not yet provisioned)

**Built (code)**
- `AudioPlayer.astro` — native `<audio controls preload="metadata">` in a styled shell (duration + size + download). Embedded on **lesson, article, book, poem** (audio resolved via `graph.audioForSource`). Audio captions are `data-pagefind-ignore`.
- `Attachments.astro` — download links (PDF/EPUB/…) with format + size.
- Model: `book` gains `cover` + `attachments[]`; `poem`/`article` gain `attachments[]`; `attachment` schema (`label`/`url`/`format`/`size_bytes`) Zod-validated. `Audio` already validates `url`/`format`/`duration`/`size_bytes`; the **lesson transcript gate** (P1) stands (`FR-W-05`).
- Fixtures: `al-wasitiyyah` cover + PDF/EPUB; `al-bayquniyyah` recitation Audio.

**Built (ops docs/scripts)** — R2 can't be provisioned from here, so:
- `docs/media-and-backup.md` — R2 bucket + public host (`r2.ahlalathar.com`, already in CSP), Opus encoding (`ffmpeg`), key convention, upload, weekly media backup + `rclone check`, and the **NFR-04 "rebuild from Git" recovery** (site from `pnpm build`; media links in Git, bytes in the R2 backup).
- `scripts/upload-media.sh` — `rclone` mirror to R2 with immutable cache headers.

**DoD**
- ✅ A lesson/poem/book/article plays from its (R2) URL via an accessible native player; attachments download.
- ✅ No lesson without a transcript can reach `published` (build-time gate).
- ✅ Recovery procedure documented (rehearsal is a P8 checklist item).
- ⏳ Real R2 bucket + actual media uploads — **infra, pending launch** (issue #12). Audio/attachment URLs currently point at the future `r2.ahlalathar.com`.

**Deviations**
- **D13 — Native `<audio controls>` instead of the mockup's custom JS audio bar.** Native is keyboard- and screen-reader-accessible with zero JS (helps issue #3) and still styled to fit; the custom seek/speed bar was JS-only and less accessible.

---

## Hardening / infra (post-P6, pre-P7) — fixing the issue register

Not a numbered phase — a pass at `docs/issue.md`:

- **#2 CI/remote → resolved.** Repo pushed to `github.com/Ahmed-Sinkeat/athar-archive` (private). GitHub Actions CI (`.github/workflows/ci.yml`): install → vitest → validate:content → build → **smoke** → tsc. **Green** on every push. Fixed a pnpm-11 `allowBuilds` (esbuild/sharp) install failure surfaced only in CI's frozen install.
- **#3 a11y → mitigated.** `scripts/a11y-audit.mjs` (`pnpm a11y`) runs axe-core WCAG 2.0/2.1 A+AA over 22 pages × 3 themes in headless Chromium — **all clean**. Fixes: darkened `--ink-faint` (light/sepia) for small-text contrast; **split `--accent` (brand/text) vs `--accent-solid` (white-on-accent fills)** because in dark one color can't satisfy both directions. Manual keyboard/SR pass still pending (P8).
- **#6 render tests → mitigated.** `scripts/smoke-test.mjs` (`pnpm smoke`, in CI): 30 per-template invariant assertions over `dist/` (verses+annotations, lesson TOC↔heading-id, attachments, audio, JSON-LD, canonical/noindex, feeds, redirects, CSP, search scoping).
- Added `README.md`.

Still open (need data / your input / big refactor): **#1** Arabic search recall (real corpus), **#4+#5** CSP `unsafe-inline` ↔ inline-style refactor, **#7** real content, **#12** R2 provisioning. See `docs/issue.md`.

---

## Decisions log

| # | Decision | Rationale |
|---|---|---|
| D1 | Content config at `src/content.config.ts` | Required by Astro 6 (legacy path errors) |
| D2 | Slug pattern allows `--` child separators | The id scheme (`series--lesson-n`) needs it |
| D3 | `verse_count`/`opening_verse` derived only | FR-C-06 — never hand-stored |
| D4 | Failure cases in unit tests, not committed broken fixtures | Keep `main` buildable while proving build-fail rules |
| D5 | Sanitize via standalone unified pipeline (Prose.astro) | Controllable, testable rendering path; dropped deprecated Astro markdown.rehypePlugins |
| D6 | Design tokens from final mockup, not BUILD-PLAN §0.6 | User designated `Book and Poetry Design` as the real design |
| D7 | SPA mockup ported to static multi-page routes | Content must render without JS (FR-P-05) |
| D8 | Annotations revealed JS-free via `:target` | Keep šarḥ reachable without JS; enhance with reader.ts |
| D9 | `/books` = combined library (books+poems); `/poems` also kept | Matches mockup library + nav "المكتبة" |
| D10 | No diacritic-stripped search field | Spike proved Pagefind normalizes Arabic diacritics — mitigation redundant |
| D11 | Domain = `ahlalathar.com` | User ratified .com over .net (2026-06); resolves the last open decision |
| D12 | `_redirects` written by post-build script | Astro treats `_redirects.ts` as private (underscore); script writes `dist/_redirects` |
| D13 | Native `<audio controls>` over the mockup's custom JS bar | Keyboard/SR-accessible, JS-free; styled to fit |

## Open items

- ✅ ~~Pagefind Arabic spike~~ — **done in P4** (GO; diacritics handled, hamza/proclitic edge cases documented).
- ✅ ~~Domain extension~~ — **ratified `.com` in P5**.
- **Hamza/proclitic search recall** (e.g. `أسماء`↛`بأسماء`). Known Pagefind limitation; revisit with real corpus — escalate to Meilisearch/Typesense only if QA shows it matters.
- **Remote + CI.** No git remote or hosted CI yet; local `pnpm build` is the gate.
- **CSP `'unsafe-inline'`.** Design uses inline styles + scripts heavily, so `style-src`/`script-src` allow `'unsafe-inline'`. Tighten with hashes/nonces only if a build step is added (post-launch).

## Verification (current)

```
pnpm test            → 66/66 passing (5 files: validate, graph, chapters, chunk, sanitize)
pnpm validate:content → ✓ 21 entries
pnpm build           → ✓ green — 29 pages + sitemap + rss + _redirects + _headers
tsc --noEmit         → ✓ clean
JSON-LD              → ✓ 15/15 valid; types per URL Map §06
search (dist/)       → ✓ verified via headless Chromium; audio captions excluded
media                → players on lesson/poem/book/article; book PDF/EPUB downloads
```

## Next: P7 — Authoring experience & governance

`CONTRIBUTING.md` mirroring the Authoring Guide (per-entity frontmatter templates incl.
the new `attachments`/`cover`; id/slug rules; chapter markers; annotation anchors; status
+ publish gates; pre-publish checklist). Volunteer intake (untrusted Markdown → sanitize →
team curates PR → sets status → merges). Optional scaffolding scripts for new content.
CMS (Decap) stays deferred.
