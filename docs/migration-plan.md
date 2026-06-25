# Athar Archive → Hybrid Rendering: Migration Plan

> Status: **Phases 1–5 implemented & verified 2026-06-24; not yet deployed.** Trigger:
> content outgrew static generation (592 huge books → 44k pages → 13 GB `dist`, past
> Cloudflare's 20k-file limit and the GitHub runner's ~14 GB disk). 10k books incoming.
> Result so far: 44k→920 prerendered HTML, 13 GB→88 MB HTML (dist/client ~470 MB incl.
> the book/lesson `.md` shipped as assets), prerender 2m52s→44s, Pagefind removed.
> Deploy with `pnpm build && pnpm deploy`. See **As-built notes** at the bottom.

## Goal
Stop pre-building 44k+ files. Keep small pages static; render heavy reading content
on-demand and edge-cache it. Stay on Astro, stay (nearly) free, no credit card.

## Principle
Static generation is the right default and was the correct original choice. We've
simply outgrown it: at 10k multi-volume books there is no static option. Move the
heavy reading pages to on-demand rendering on a Cloudflare Worker; leave everything
small static.

## Architecture (plain terms)
- **Static pages** (home, lists, book TOCs) are pre-built files — fast, simple, like today.
- **Reading pages** (book chapters, lessons) are rendered by a Cloudflare **Worker** on
  first request, then **edge-cached** — 2nd visitor onward gets it like a static file.
- Book text lives in **Workers Static Assets** (ships with the deploy, no card). The
  Worker reads the one book it needs and renders the one chapter requested.

## Route map

| Route | Mode | Why |
|---|---|---|
| home, `/books` `/poems` `/articles` `/series`, topics/people/subjects, about/contact/search | Static | Small, stable |
| `/graph` (شبكة المعرفة) | Static | Metadata-only; see Graph section |
| `/book/[slug]` (landing) | **Static — light TOC** | Title + metadata + chapter links. No full text. |
| `/book/[slug]/[chapter]` | **On-demand** | The heavy reading content; 99% of the bulk |
| `/series/[slug]/[lesson]` (lessons) | **On-demand** | 2k coming, transcripts grow |
| `/poem/*` | Static | Poems are 16 KB *total* — on-demand buys nothing |
| `/article/[slug]` | Static | Small — on-demand buys nothing |

Decision: poems & articles **stay static** (negligible size). Only books (and later
lessons) force the change.

## Where book text lives — Workers Static Assets (no credit card)
- Book `.md` copied into deployed assets: ~10k files (one per book) < 20k limit;
  biggest book 15 MB < 25 MB/file limit.
- On-demand Worker reads `/content/book/<slug>.md` via the `ASSETS` binding, extracts
  the requested chapter, renders with the **existing** components (Prose, StudyBar,
  annotations — unchanged), sets a cache header → Cloudflare edge-caches it.
- Holds 381 MB now; if ~6 GB gets clunky at full 10k, that's when to add a card and
  move bodies to **R2** (10 GB free). Can go a long way first without a card.

## Book landing = static TOC
Extend the content loader to extract **chapter headings only** (titles + slugs) when it
reads each file, and store that small list in the data store. The landing page reads it
— no body, fully static, low build memory. Full text never enters the data store.

## Search
- **Remove Pagefind** (deletes the indexing step + ~187 MB output).
- **Google Programmable Search** on the existing search page — inline results, keep the
  UI. Submit sitemap so Google crawls on-demand pages.
- Vector / AI ("search with meaning") later — unaffected by this migration.

## Graph view (شبكة المعرفة)
- **Stays static.** It reads only relationships (frontmatter), never book bodies, so it
  has zero connection to the 6 GB-text problem. The book migration doesn't touch it.
- Nodes = entities (books, poems, people, topics, subjects) — **not** chapters. Data is
  a small inline JSON (id/label/type/href per node + links). ~800–1000 nodes now.
- Its limit is **browser rendering, not hosting.** `force-graph` is smooth to ~1–2k
  nodes, janky past ~5k. At ~800 entities you're near the comfortable global-graph
  ceiling already.
- At 10k entities the "show everything" graph becomes unusable (laggy + multi-MB inline
  JSON). Fix then is **not** static-vs-on-demand for the page — it's changing what it
  shows: a focused **neighborhood** (one scholar/book + its links) instead of the whole
  graph, with the neighborhood data fetched **on-demand** (small per-node JSON, via the
  Worker). End state: static shell + on-demand neighborhood data.
- Separate workstream from the book migration; do it when the global graph degrades.

## Build pipeline changes
| Step | Change |
|---|---|
| Astro config | add `@astrojs/cloudflare` adapter; reading routes `prerender = false` |
| content loader | also store chapter TOC (headings only) |
| book bodies | copy `.md` into deployed assets |
| Pagefind | removed |
| `gen-headers` (CSP) | on-demand pages get the same CSP hashes (same layout) |
| `gen-redirects`, sitemap | keep; sitemap lists on-demand URLs so Google finds them |

## Deploy
Adapter outputs a Worker + static assets; `wrangler` deploys both. Cloudflare Workers
free plan — no card. CI build drops from ~15 min to near-instant.

## Rollout — phased, each step shippable & verified
1. ✅ **Books on-demand**: adapter + `/book/[slug]/[chapter]` reads from assets, render +
   cache. Smallest blast radius, solves the crisis.
2. ✅ **Book landing → light TOC**: chunked landing renders a `.toc-box` of links to the
   on-demand chapters (no body). (Kept `readBody` in getStaticPaths — transient.)
3. ✅ **Lessons on-demand** (before adding the 2k).
4. ✅ **Search swap**: removed Pagefind; search → Google site-search (not PSE — see notes).
5. ✅ **Cleanup**: removed inert `data-pagefind-*` attrs, `pagefind` dep, dead accordion CSS.

After Phase 1–2 the 10k books can be added.

## Risks / unknowns
- ~6 GB static assets at full scale — fine now, verify later; fallback R2 (needs card).
- Edge-cache invalidation on content edits — wire cache-versioning / purge.
- CSP for on-demand pages — handle hashes at runtime, not just over `dist`.
- First uncached hit slightly slower (then cached).
- Astro 6.4.8 + Cloudflare adapter version compatibility — pin & test in Phase 1.

## What does NOT change
Components, layouts, content files, reading experience, marks/find/reader scripts, the
content model, and most pages (home, lists, TOCs, graph stay static). To a visitor the
site looks and feels identical — without the build/scale wall.

## As-built notes (what differed from the plan)
- **Adapter:** `@astrojs/cloudflare@^13.7` (14.x needs Astro 7). Needs `wrangler@^4.104`.
  Adapter supplies `main` + the `ASSETS` binding itself — wrangler.toml only carries
  `compatibility_flags=["nodejs_compat"]` + observability. Output is `dist/client`
  (assets) + `dist/server` (worker + generated `wrangler.json`); deploy = `wrangler
  deploy -c dist/server/wrangler.json`. All post-build scripts target `dist/client`.
- **`prerenderEnvironment: "node"`** is required — the adapter's default `workerd`
  prerender has no `node:fs`, which breaks `readBody()` on every static page.
- **Astro 6 runtime API:** `Astro.locals.runtime` is gone → `import { env } from
  "cloudflare:workers"` (dynamic, prod-only), exec ctx = `Astro.locals.cfContext`,
  global `caches`. `Astro.rewrite("/404")` 500s from the worker → serve `404.html`
  via ASSETS with status 404 (`notFound()`). Cache API responses have immutable
  headers → return `new Response(hit.body, hit)` to avoid i18n-finalize throwing.
- **Edge cache:** Workers Cache API in `src/middleware.ts` (Cache-Control alone won't
  cache a Worker response). TTL = 1 day (not a year) until edit→deploy purge is wired.
- **CSP for on-demand pages:** `_headers` only covers asset responses, so the build
  also emits `_headers.json`; the middleware reads it via ASSETS and applies the same
  CSP to worker responses.
- **Search:** shipped **Google site-search** (`window.open` to `google.com/search?q=
  site:<host> …`), not inline PSE — PSE needs a `cx` id + a CSP relaxation. Sitemap
  already lists the on-demand chapter/lesson URLs so Google can crawl them.
  → Upgrade path: create a PSE, wire it with a CSP scoped to `/search`.
- **Known follow-ups:** in-bar search filter (type/person/subject chips) still builds
  `/search?…` params the Google search ignores; `searchMeta` prop still threaded
  through pages but unused; move the book TOC into the loader if 10k build time drags.
