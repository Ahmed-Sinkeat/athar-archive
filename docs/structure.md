# athar-archive — Repository Structure

**As of:** P3 complete (RTL design system & all page templates)
**Companion doc:** [`asbuild.md`](./asbuild.md) — phase-by-phase as-built record.

This document describes the *actual* repository layout. It is updated after every phase. Directories that exist but are not yet populated are marked **(pending P4+)**.

---

## Top level

```
athar-archive/
├─ ahlalathar.config.ts      # tunable values: domain, chunk thresholds, mailto, topicsMax
├─ astro.config.ts           # Astro static config + markdown sanitize wiring + RTL i18n
├─ tsconfig.json             # extends astro/strict; @/@lib/@components path aliases
├─ vitest.config.ts          # test runner config (src/**/*.test.ts)
├─ package.json              # scripts: dev, build, preview, validate, validate:content, test
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
├─ content.config.ts         # all 13 Astro Content Collections + Zod schemas (P1)
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
│  ├─ annotation/            #   الشرح/الحاشية — 1 fixture (embedded, target+anchor)
│  └─ announcement/          #   الإعلان      — 1 fixture (homepage chrome)
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
│  ├─ display.ts             #   Arabic-Indic numerals, route map (hrefFor), entity labels, stripTashkeel
│  └─ site.ts                #   page-runtime: loadGraph(), personNameMap(), publishedSorted()
│
├─ styles/
│  └─ global.css             #   design system: tokens, 3 themes, reading-scale, verses, annotations
├─ scripts/
│  └─ reader.ts              #   reading-prefs/theme/drawer/progress enhancement (bundled client script)
│
├─ layouts/
│  └─ Base.astro             #   RTL shell: 3-row header, drawer, progress bar, footer, pre-paint script
├─ components/
│  ├─ Breadcrumbs.astro · EntityCard.astro · Prose.astro · Verse.astro
└─ pages/                    # every route (BUILD-PLAN 0.4) — all render from fixtures
   ├─ index.astro · search.astro · about.astro · contact.astro · 404.astro
   ├─ books.astro · poems.astro · subjects.astro · topics.astro · people.astro
   ├─ articles.astro · benefits.astro · series/index.astro · questions/index.astro
   ├─ book/[slug].astro · book/[slug]/[chapter].astro
   ├─ poem/[slug].astro · poem/[slug]/[chapter].astro
   ├─ series/[slug].astro · series/[slug]/[lesson].astro
   ├─ person/[slug].astro · subject/[slug].astro · topic/[slug].astro
   └─ benefit/[slug].astro · article/[slug].astro · questions/[slug].astro
```

## `scripts/`

```
scripts/
├─ validate-content.ts       # CLI: loads content, runs validate.ts, exits 1 on any breach
│                            # wired into `pnpm build` before astro build
└─ pagefind-spike/           # (pending) P0 throwaway Arabic search spike — not yet run
```

## `public/`

```
public/
└─ robots.txt                # allow-all + sitemap pointer (sitemap generated in P5)
```

## `docs/`

```
docs/
├─ structure.md              # this file — current repo layout
└─ asbuild.md                # phase-by-phase as-built record vs BUILD-PLAN
```

---

## Conventions

- **Source of truth:** Markdown + YAML frontmatter under `src/content/`. The whole site is rebuildable from Git alone (NFR-04).
- **IDs = filenames = slugs.** Pattern `^[a-z0-9]+(--?[a-z0-9]+)*$`; `--` separates parent from child (e.g. `sharh-al-wasitiyyah--lesson-1`, `alfiyyah-ibn-malik--v1--sharh`).
- **Derived, never hand-stored:** verse counts, opening verse, chapter splits, series stats — all computed in `src/lib/` from the body (FR-C-06).
- **Two-tier validation:** per-entity rules in `content.config.ts` (Zod); cross-entity rules in `src/lib/validate.ts` (run by `scripts/validate-content.ts`).
- **Tests** live beside their module as `*.test.ts` and run under Vitest.
