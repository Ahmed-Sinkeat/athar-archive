# athar-archive — As-Built Record

This document tracks what has **actually been built**, phase by phase, against the
[`BUILD-PLAN.md`](../../athur/BUILD-PLAN.md) (design corpus). It records deliverables,
Definition-of-Done (DoD) status, decisions, and any deviations from the plan.

> Updated after every phase. Companion: [`structure.md`](./structure.md).

**Project:** أهل الأثر — Arabic Islamic knowledge archive
**Stack:** Astro (static) · Markdown + Zod Content Collections · Pagefind (search, P4) · Cloudflare Pages/R2 (P6/P8)
**Last updated:** P4 complete · 29 pages built, 15 indexed · 66/66 tests passing

---

## Phase status

| Phase | Title | Status | Commit |
|---|---|---|---|
| **P0** | Foundations & de-risking | ✅ Done (spike now run in P4) | `0c93dc1` |
| **P1** | Content model in code | ✅ Done | `cc3d818` |
| **P2** | Build pipeline & derivations | ✅ Done | `1ef6b93` |
| **P3** | Design system & page templates (RTL) | ✅ Done | `3fba81b` |
| **P4** | Search (Pagefind, Arabic) | ✅ Done | `23e6781` |
| P5 | SEO, structured data, feeds, permanence | ⬜ Next | — |
| P6 | Media pipeline (R2) | ⬜ Pending | — |
| P7 | Authoring experience & governance | ⬜ Pending | — |
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

## Open items

- ✅ ~~Pagefind Arabic spike~~ — **done in P4** (GO; diacritics handled, hamza/proclitic edge cases documented).
- **⏳ Domain extension `.net` vs `.com`.** Still intentionally open (BUILD-PLAN §Decisions). **Must be confirmed before P5** (absolute URLs / canonical / structured data / sitemap). Currently `ahlalathar.net` placeholder in config + `astro.config.ts` + `robots.txt`.
- **Hamza/proclitic search recall** (e.g. `أسماء`↛`بأسماء`). Known Pagefind limitation; revisit with real corpus — escalate to Meilisearch/Typesense only if QA shows it matters.
- **Remote + CI.** No git remote or hosted CI yet; local `pnpm build` is the gate.

## Verification (current)

```
pnpm test            → 66/66 passing (5 files: validate, graph, chapters, chunk, sanitize)
pnpm validate:content → ✓ 20 entries
pnpm build           → ✓ green — 29 pages built, 15 indexed by Pagefind (1 lang, 1 filter)
tsc --noEmit         → ✓ clean
search (dist/)       → ✓ verified via headless Chromium on real fixtures
```

## Next: P5 — SEO, structured data, feeds, permanence

JSON-LD per entity type (Person/Book/Poem/Course/QAPage/Quotation…), Open Graph/Twitter,
canonical URLs, `sitemap.xml`, `rss.xml` (by `published_at`), `robots.txt`, and the
`aliases` → Cloudflare `_redirects` (301) permanence map. **Blocked on confirming the domain
extension (.net/.com)** before emitting absolute URLs.
