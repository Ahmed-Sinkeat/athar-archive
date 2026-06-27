# Ahl al-Athar — Technology Stack Decisions

> **Status:** Accepted 2026-06-27. This document records the architectural reasoning behind every major technology choice for the Ahl al-Athar presentation frontend.

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

## HTMX

### Purpose
HTMX provides progressive interactivity (live-reloading tabs, dynamic overlays, inline search filtering) without client-side framework overhead.

### Why we chose it
HTMX allows us to keep the frontend completely HTML-driven. Instead of building a complex client-side Single Page Application (SPA) using React or Vue, we send standard HTML snippets directly from the Cloudflare Worker. This satisfies our constraint of keeping the technology layer minimal and easily replaceable.

### Alternatives considered
* **React / Vue.js:** Traditional SPA framework ecosystems.
* **Alpine.js:** A lightweight inline utility script.

### Why they were rejected
* **React / Vue.js:** Over-engineered for a digital library. They move the rendering logic to the client, creating slower initial loads, complex hydration bugs, and making the site unreadable when JavaScript is disabled.
* **Alpine.js:** While lightweight, it still shifts state logic to client memory. HTMX's approach of swapping pure HTML fragments is cleaner and aligns better with our static/hybrid rendering pipeline.

### Future replacement policy
Replaceable. If another progressive enhancement tool becomes simpler, HTMX can be replaced without rewriting layout templates.

---

## Cloudflare Pages

### Purpose
Hosting provider for static assets and edge execution via Cloudflare Workers.

### Why we chose it
Cloudflare Pages provides global CDN coverage, free bandwidth, and integrates natively with Cloudflare Workers. It handles the hybrid routing split: serving stable pages (like TOCs, home) instantly from the CDN, and routing heavy reading pages to the edge Worker. It matches our requirement of being highly scalable without requiring a credit card or complex devops setup.

### Alternatives considered
* **Netlify / Vercel:** Premium hosting ecosystems.
* **VPS hosting (DigitalOcean/Hetzner):** Self-managed server infrastructure.

### Why they were rejected
* **Netlify / Vercel:** Introduce strict bandwidth and serverless execution quotas. At scale, they become expensive and couple us to proprietary hosting add-ons.
* **VPS Hosting:** Requires ongoing system administration, security hardening, load balancing, and fails to provide global CDN replication out-of-the-box.

### Future replacement policy
Stable. Cloudflare Pages matches our global scaling demands with minimal maintenance.

---

## Cloudflare R2

### Purpose
Object storage for large media files (Opus-encoded recitations, PDF downloads, cover images).

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
Meilisearch is currently documented as our **escalation pathway**. Our current production search utilizes Pagefind (static indexing) and Google Site Search. Pagefind handles diacritics and basic matches but lacks root-word Arabic morphology. If real-world corpus testing reveals poor search recall (e.g. failing to match hamza-variants or proclitics), Meilisearch will be deployed. It is open-source, supports advanced Arabic tokenizer mapping, and is highly performant.

### Alternatives considered
* **Elasticsearch / OpenSearch:** Enterprise search platforms.
* **Algolia:** Hosted search service.

### Why they were rejected
* **Elasticsearch:** Extremely resource-heavy. Requires running Java virtual machines on dedicated, high-cost server clusters. Over-engineered for our scale.
* **Algolia:** Closed-source, proprietary, and becomes exceptionally expensive as search queries scale.

### Future replacement policy
Experimental / Escalation. Meilisearch is not yet active, but stands as the designated upgrade path if Pagefind limits are hit.

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

## Keystatic

### Purpose
A Git-CMS editor interface.

### Why we chose it
Keystatic is currently **deferred** in favor of the lightweight `/compose` tool. Keystatic is an excellent Markdown editing interface, but it requires setup configuration and adds dependencies. The lightweight `/compose` tool generates identical valid frontmatter stubs locally without any package overhead.

### Alternatives considered
* **Decap CMS (formerly Netlify CMS):** Traditional static site Git CMS.

### Why they were rejected
* **Decap CMS:** An aging library with slow active maintenance, relying on complex external configurations and client-side authenticators.

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
