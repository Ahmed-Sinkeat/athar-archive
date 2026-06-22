# athar-archive — Repository Structure

**As of:** P7 complete (authoring docs, intake governance, content scaffold) + UX-R (reading/browse/search redesign, admin v2, مختارات الأسبوع — see `asbuild.md`)
**Companion docs:** [`asbuild.md`](./asbuild.md) · [`issue.md`](./issue.md) · [`governance.md`](./governance.md) · [`media-and-backup.md`](./media-and-backup.md)

This document describes the *actual* repository layout. It is updated after every phase. Directories that exist but are not yet populated are marked **(pending P7+)**.

---

**Repo:** [github.com/Ahmed-Sinkeat/athar-archive](https://github.com/Ahmed-Sinkeat/athar-archive) (private) · CI green on every push to `main`.

## Top level

```
athar-archive/
├─ .github/
│  ├─ workflows/ci.yml       # CI: install → test → validate:content → build → smoke → tsc
│  ├─ CODEOWNERS             # team review on src/content/**, schemas, governance (P7)
│  ├─ PULL_REQUEST_TEMPLATE.md   # pre-publish checklist (P7)
│  └─ ISSUE_TEMPLATE/        # propose-content / report-correction forms + config (P7)
├─ CONTRIBUTING.md           # Arabic authoring guide: per-entity templates, rules, gates (P7)
├─ README.md
├─ ahlalathar.config.ts      # tunable values: domain, chunk thresholds, mailto, topicsMax
├─ astro.config.ts           # Astro static config + markdown sanitize wiring + RTL i18n
├─ tsconfig.json             # extends astro/strict; @/@lib/@components path aliases
├─ vitest.config.ts          # test runner config (src/**/*.test.ts)
├─ package.json              # scripts: dev, new, build, preview, validate, validate:content, test
├─ .npmrc                    # pnpm onlyBuiltDependencies allowlist (esbuild, sharp)
├─ .editorconfig · .gitignore · pnpm-workspace.yaml
├─ docs/                     # ← this folder
├─ public/                   # static passthrough
├─ scripts/                  # build-time tooling
└─ src/                      # application source
```

## `src/`

```
src/
├─ content.config.ts         # all 14 Astro Content Collections + Zod schemas (P1)
├─ content/                  # Markdown source of truth — one folder per entity
│  ├─ person/                #   الشخص        — 2 fixtures
│  ├─ subject/               #   التصنيف      — 2 fixtures
│  ├─ topic/                 #   الموضوع      — 2 fixtures (→ Subject)
│  ├─ book/                  #   الكتاب       — 1 fixture (→ Person)
│  ├─ poem/                  #   المنظومة     — 2 fixtures (long + short)
│  ├─ series/                #   السلسلة      — 1 fixture (polymorphic → book)
│  ├─ lesson/                #   الدرس        — 2 fixtures (published + draft/transcript-gate)
│  ├─ question/              #   المسائل      — 1 fixture (QAPage)
│  ├─ benefit/               #   الفائدة      — 2 fixtures (with + without source)
│  ├─ article/               #   المقالة      — 1 fixture
│  ├─ audio/                 #   الصوتية      — 1 fixture (embedded, polymorphic)
│  ├─ annotation/            #   الشرح/الحاشية — 4 fixtures (target+anchor; optional phrase mark + source link)
│  ├─ announcement/          #   الإعلان      — 1 fixture (homepage chrome)
│  └─ highlight/             #   مختار الأسبوع — آية/حديث/بيت + reference; homepage chrome, no page — 3 fixtures
│
├─ lib/                      # build pipeline, derivations & page helpers (P1–P3)
│  ├─ types.ts               #   ContentEntry, COLLECTIONS, MATERIAL_COLLECTIONS, isPublished
│  ├─ load.ts                #   loadContentFromDisk() — gray-matter loader (scripts/tests)
│  ├─ validate.ts            #   cross-entity build-time validator (+ tests)
│  ├─ graph.ts               #   in-memory knowledge graph + derived series stats (+ tests)
│  ├─ chapters.ts            #   chapter/verse/paragraph/heading parser + Arabic slugify (+ tests)
│  ├─ chunk.ts               #   threshold-driven single-page vs chapterized (+ tests)
│  ├─ sanitize.ts            #   Markdown→safe HTML + Arabic heading-ids (+ tests)
│  ├─ sanitize-schema.ts     #   shared rehype-sanitize schema
│  ├─ display.ts             #   Arabic-Indic numerals, route map (hrefFor), entity labels, stripTashkeel, era slugs (eraHref)
│  ├─ site.ts                #   page-runtime: loadGraph(), personNameMap(), publishedSorted(), notesByAnchor(), subjectTitlesFor()
│  ├─ browse.ts              #   subject→topic grouping for الكتب/المنظومات/المسائل (buildSubjectGroups)
│  ├─ content-forms.ts       #   /compose field specs per entity (mirrors the Zod schema; ref/refs name-picker fields)
│  └─ structured-data.ts     #   JSON-LD builders per entity type (+ WebSite/SearchAction)
│
├─ styles/
│  └─ global.css             #   design system: tokens, 3 themes, reading-scale, verses, inline شرح chooser, browse grouping, search/settings popovers
├─ scripts/
│  ├─ reader.ts              #   client enhancement: reading prefs/theme/drawer/progress, expanding search + filter/settings popovers, inline شرح chooser (click/long-press), اختبار reveal
│  └─ compose.ts             #   /compose client: add|edit modes; featured types + «أنواع أخرى»; guided collapsible sections; searchable name pickers (datalist/checklist) + edit prefill; .txt/.md upload → live-built file.md
│
├─ layouts/
│  └─ Base.astro             #   RTL shell: single-row header (brand · nav · search + settings popovers), drawer, progress bar, footer, pre-paint script
├─ components/
│  ├─ Breadcrumbs.astro · EntityCard.astro · Prose.astro · StudyBar.astro
│  ├─ Verse.astro          # numbered بيت + inline شرح mark (.ann-mark) + hidden chooser pack
│  ├─ AudioPlayer.astro    # native <audio> (sources[]); multi-recitation <select> switcher on متون/منظومات
│  └─ BrowseGroups.astro   # collapsible تصنيف→موضوع accordion (books/poems/articles/series)
└─ pages/                    # every route (BUILD-PLAN 0.4) — all render from fixtures
   ├─ index.astro · search.astro · compose.astro · roadmap.astro · about.astro · contact.astro · 404.astro
   ├─ books.astro · poems.astro · subjects.astro · topics.astro · people.astro    # browse: grouped by subject→topic / era
   ├─ articles.astro · benefits.astro · series/index.astro · questions/index.astro  # questions: subject→topic drill-down
   ├─ book/[slug].astro · book/[slug]/[chapter].astro
   ├─ poem/[slug].astro · poem/[slug]/[chapter].astro
   ├─ series/[slug].astro · series/[slug]/[lesson].astro
   ├─ person/[slug].astro · subject/[slug].astro · topic/[slug].astro · era/[slug].astro
   ├─ benefit/[slug].astro · article/[slug].astro · questions/[slug].astro
   └─ sitemap.xml.ts · rss.xml.ts            # endpoints (XML feeds)
```

**Permanence / SEO outputs** (generated into `dist/`): `sitemap.xml`, `rss.xml`,
`_redirects` (via `scripts/gen-redirects.ts`, aliases→301), `_headers` (from
`public/_headers`, CSP + security). Canonical/OG/JSON-LD are emitted by `Base.astro`.

## `scripts/`

```
scripts/
├─ validate-content.ts       # CLI: loads content, runs validate.ts, exits 1 on any breach
│                            # wired into `pnpm build` before astro build
├─ new-content.mjs           # scaffold a new entity stub: pnpm new <entity> <slug> (P7)
├─ gen-redirects.ts          # post-build: aliases → dist/_redirects (301)
├─ smoke-test.mjs            # post-build per-template invariant checks (pnpm smoke, in CI)
├─ check-links.mjs           # post-build internal-link + redirect integrity (pnpm check:links, P8)
├─ perf-budget.mjs           # post-build page-weight + JS-free + RTL budget (pnpm perf:budget, P8)
├─ a11y-audit.mjs            # axe-core WCAG A/AA over dist/ in headless Chromium (pnpm a11y)
├─ upload-media.sh           # rclone mirror of local media → R2 bucket
└─ pagefind-spike/           # P0 Arabic search spike (run in P4) + real-index verification
   ├─ site/                  #   controlled corpus: diacritized.html, stripped.html
   ├─ spike-search.mjs       #   headless-chromium search test over the corpus
   └─ verify-real.mjs        #   headless-chromium search over the built dist/
```

Search index (`dist/pagefind/`, generated by `pagefind --site dist` at the end of `pnpm build`) is gitignored. `/search` consumes it client-side via Pagefind's JS API.

## `public/`

```
public/
└─ robots.txt                # allow-all + sitemap pointer (sitemap generated in P5)
```

## `docs/`

```
docs/
├─ structure.md              # this file — current repo layout
├─ asbuild.md                # phase-by-phase as-built record vs BUILD-PLAN
├─ issue.md                  # ranked issue / watch register
├─ governance.md             # roles + branch-protection settings (P7)
├─ deploy.md                 # Cloudflare Pages deploy runbook (P8)
└─ media-and-backup.md       # R2 media + rebuild-from-Git recovery
```

---

## Conventions

- **Source of truth:** Markdown + YAML frontmatter under `src/content/`. The whole site is rebuildable from Git alone (NFR-04).
- **IDs = filenames = slugs.** Pattern `^[a-z0-9]+(--?[a-z0-9]+)*$`; `--` separates parent from child (e.g. `sharh-al-wasitiyyah--lesson-1`, `alfiyyah-ibn-malik--v1--sharh`).
- **Derived, never hand-stored:** verse counts, opening verse, chapter splits, series stats — all computed in `src/lib/` from the body (FR-C-06).
- **Two-tier validation:** per-entity rules in `content.config.ts` (Zod); cross-entity rules in `src/lib/validate.ts` (run by `scripts/validate-content.ts`).
- **Tests** live beside their module as `*.test.ts` and run under Vitest.
