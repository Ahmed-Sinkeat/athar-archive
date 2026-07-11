# Ahl al-Athar — Technology Stack Decisions

> **Status:** Accepted 2026-06-27 · corrected 2026-07-02 (HTMX was never adopted; hosting is Workers static assets, not Pages). This document records the architectural reasoning behind every major technology choice for the Ahl al-Athar presentation frontend.

---

## Astro

### Purpose
Astro serves as the static site generator (SSG) and server-side rendering (SSR) framework. It generates the HTML layouts, handles routing, and orchestrates content building.

### Why we chose it
Astro is built around the "Islands Architecture" and defaults to shipping zero JavaScript to the client. Because RTL Arabic text readability, speed, and SEO are the primary goals, Astro allows us to server-render 100% of our content. It keeps pages incredibly lightweight (budgets under 150 KB) while supporting dynamic progressive enhancement when needed.

### Alternatives considered
* **Next.js (React):** A highly popular React meta-framework.
* **Eleventy (11ty):** A lightweight, pure-static site generator.

### Why they were rejected
* **Next.js:** Highly coupled to React and client-side hydration. Next.js compiles large JavaScript runtimes that must execute in the browser. For a content-rich Islamic text archive, shipping megabytes of JS runtimes degrades performance on mobile devices and creates unnecessary build-time complexity.
* **Eleventy:** Extremely fast for pure static sites, but lacks native component frameworks (like Astro components) and doesn't provide a clean, out-of-the-box story for hybrid edge rendering (Cloudflare Workers adapters) which we need to scale past the 20,000 file build limit.

### Future replacement policy
Stable. Astro is the core layout framework. It will not be replaced unless the rendering framework space shifts fundamentally.

---

## Progressive interactivity — vanilla TypeScript (HTMX never adopted)

### Purpose
Client-side enhancement: annotation bottom sheets, reading preferences, search UI, the graph view.

### What actually shipped
An earlier revision of this document selected **HTMX**, but it was never installed — `package.json` has no htmx dependency and no template references it. Everything HTMX was slated for is covered by small vanilla TypeScript modules (`src/scripts/reader.ts`, `library.ts`, `marks.ts`, `graph.ts`) attached to server-rendered HTML. Astro's `<ClientRouter />` + hover prefetch provide the SPA-feel navigation.

### Why vanilla won
The interactivity surface turned out to be small and mostly DOM-local (open a sheet from a hidden `.ann-pack`, toggle prefs). No fragment-swapping over the network was needed, so a library — even a small one — would have been dead weight. Content still renders fully without JavaScript.

### Future replacement policy
Add a library only when a concrete feature needs network-driven partial updates that the vanilla modules can't express cleanly. Re-evaluated 2026-07-07 (Astro 7 upgrade + athar features plan): still not adopted — the one network feature (`/api/search`) returns JSON to a tiny vanilla renderer, and ClientRouter already owns navigation (htmx would compete with it for DOM/nav ownership, plus its `hx-on` attributes are blocked by the strict CSP). **Adoption trigger:** the deferred D1-backed comments system (form → Worker → HTML fragment) — adopt htmx 2.x then, loaded only on those pages.

---

## Cloudflare Workers + Static Assets

### Purpose
Hosting: static pages served from Workers Static Assets (CDN); book chapter pages prerendered at build time into the `BOOK_ASSETS` R2 bucket and served by a thin Worker route (one R2 read — see `docs/deploy.md`), deployed with `wrangler deploy` (`pnpm deploy`). *(An earlier revision of this doc said Cloudflare Pages; the project actually deploys as a Worker — Pages was never set up.)*

### Why we chose it
Global CDN coverage, free static-asset bandwidth, and one deploy artifact for the hybrid split: stable pages (TOCs, home, surahs) as prebuilt assets, heavy reading pages (`/book/<slug>/<chapter>`) rendered on demand at the edge and cached via the Cache API (`src/middleware.ts`). Content markdown is shipped as assets and read by the Worker through the `ASSETS` binding.

### Hard limits that shape the architecture
* **25 MiB per static asset** — `tafsir-ibn-kathir.md` sits at ~99% of this; per-chapter content assets are the planned fix (see `HANDOFF-perf-size.md`).
* **20,000 files per deploy** — why the ~10k prerendered chapter pages live in R2 instead of shipping as static assets.
* **Worker bundle ≤ a few MB gzipped** — large JSON indexes must be assets, not imports.

### Alternatives considered
* **Netlify / Vercel:** Premium hosting ecosystems.
* **VPS hosting (DigitalOcean/Hetzner):** Self-managed server infrastructure.

### Why they were rejected
* **Netlify / Vercel:** Introduce strict bandwidth and serverless execution quotas. At scale, they become expensive and couple us to proprietary hosting add-ons.
* **VPS Hosting:** Requires ongoing system administration, security hardening, load balancing, and fails to provide global CDN replication out-of-the-box.

### Future replacement policy
Stable. Cloudflare Workers matches our global scaling demands with minimal maintenance.

---

## Cloudflare R2

### Purpose
Object storage for large media files (Opus-encoded recitations, PDF downloads, cover images), and — since 2026-07-03 — book chapter bodies and Quran tafsir fragments, moved off Workers Static Assets to keep deploys under the 20,000-file limit as more books are added (see `docs/HANDOFF-perf-size.md`).

### Why we chose it
Cloudflare R2 provides S3-compatible storage with **zero egress fees**. In a digital library, users download large files (PDF/Audio) repeatedly. Traditional storage providers (like AWS S3) charge per GB of downloaded traffic, which would make the archive financially unsustainable at scale. R2 eliminates egress bills entirely.

### Alternatives considered
* **Amazon S3:** The industry-standard object store.
* **Backblaze B2:** A low-cost alternative object storage.

### Why they were rejected
* **Amazon S3:** Prohibitively expensive egress rates. A single viral audio recitation could cost hundreds of dollars in data transfer fees.
* **Backblaze B2:** Very cheap storage, but still incurs minor egress fees and requires separate CDN routing configurations.

### Future replacement policy
Stable. R2 is core to our cost-control strategies.

---

## Meilisearch

### Purpose
A potential upgrade search engine for advanced full-text Arabic searches.

### Why we chose it
Meilisearch is currently documented as our **escalation pathway**. Production search is now Cloudflare D1 (SQLite FTS5) via a Worker API route (`/api/search`) — the earlier static-indexing approach (Pagefind, then Google Site Search) is retired; see `docs/asbuild.md` for that history. FTS5 handles diacritics and basic matches but lacks root-word Arabic morphology. If real-world corpus testing reveals poor search recall (e.g. failing to match hamza-variants or proclitics), Meilisearch will be deployed. It is open-source, supports advanced Arabic tokenizer mapping, and is highly performant.

### Alternatives considered
* **Elasticsearch / OpenSearch:** Enterprise search platforms.
* **Algolia:** Hosted search service.

### Why they were rejected
* **Elasticsearch:** Extremely resource-heavy. Requires running Java virtual machines on dedicated, high-cost server clusters. Over-engineered for our scale.
* **Algolia:** Closed-source, proprietary, and becomes exceptionally expensive as search queries scale.

### Future replacement policy
Experimental / Escalation. Meilisearch is not yet active, but stands as the designated upgrade path if the current D1/FTS5 search hits its limits.

---

## TypeScript

### Purpose
Type-safe script logic for Astro pages, backend middlewares, loading schemas, and browser scripts.

### Why we chose it
TypeScript prevents class and data mapping errors before they can reach production. By sharing the type definitions compiled by the Athar Engine, the website templates can guarantee they are matching valid properties, reducing layout crashes to zero.

### Alternatives considered
* **Plain JavaScript:** Native script language.

### Why they were rejected
* **Plain JavaScript:** Lacks compiler safety. Typing mistakes, missing Zod model properties, or changes in schemas would only be caught during live staging checks or runtime page loads.

### Future replacement policy
Stable. Essential for system-wide validation checks.

---

## Markdown

### Purpose
The markup representation used to construct static content collections.

### Why we chose it
Astro content collections naturally ingest Markdown. It is human-readable, maintains strict file boundaries, maps beautifully to HTML elements, and guarantees that our content is not locked inside database binary files.

### Alternatives considered
* **MDX:** Markdown with embedded JSX components.
* **JSON / YAML content blocks:** Structuring book pages as database objects.

### Why they were rejected
* **MDX:** Adds compilation overhead and introduces security risks if raw user content includes executable Javascript.
* **JSON Blocks:** Destroys readability. Human editors cannot easily correct typos or read raw text files without specialized tools.

### Future replacement policy
Stable.

---

## Sveltia CMS

### Purpose
A Git-CMS editor interface at `/admin`, replacing both Keystatic and the copy/paste `/compose` tool.

### Why we chose it
Keystatic required its own GitHub App + patched dependency (`patches/@keystatic__astro.patch`) and the `/compose` tool never committed anything itself — it only generated a markdown stub the admin had to paste into GitHub's web editor by hand. Sveltia CMS is a single self-hosted bundle (`public/admin/sveltia-cms.js`, ~2MB, vendored so CSP `script-src 'self'` still holds) driven by one `public/admin/config.yml` mirroring the `src/content.config.ts` schemas — real GUI editing, save = a real GitHub commit, includes delete. No React, no build-time integration, no patch file.

### Alternatives considered
* **Keystatic:** required `@astrojs/react` + `@keystatic/astro` + `@keystatic/core` + a patched dependency just for the admin route.
* **Decap CMS (formerly Netlify CMS):** aging, slower maintenance, more complex OAuth setup than Sveltia's.
* **Pages CMS:** newer, smaller community; not chosen over Sveltia's simpler single-file self-hosting.

### Setup still required
GitHub OAuth needs a small Cloudflare Worker (`sveltia-cms-auth`) deployed and registered as a GitHub OAuth App — see the TODO comment at the top of `public/admin/config.yml`. Until that's done, open `/admin` locally in a Chromium browser and use "Work with Local Repository" (no server needed).

### Future replacement policy
Replaceable. Git-based editing is modular; any interface can write to the underlying Markdown files.

---

## CSS

### Purpose
Styles the visual design system, layout grid, typography, and themes.

### Why we chose it
We use **Vanilla CSS** with CSS Custom Properties (variables) for maximum flexibility. The Arabic layout constraints (RTL grids, precise line heights for fonts like Amiri, sepia/dark themes, and scaling text) are best managed with clean, standard CSS variables rather than utility wrappers.

### Alternatives considered
* **Tailwind CSS:** A utility-first CSS framework.

### Why they were rejected
* **Tailwind CSS:** Utility-first classes clutter the markup. In complex Islamic reading designs where we toggle font scaling (`--reading-scale`) and diacritic visibility dynamically, Tailwind classes become unreadable and complicate custom responsive styling.

### Future replacement policy
Stable.
