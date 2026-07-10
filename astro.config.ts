import { defineConfig } from "astro/config";
import cloudflare from "@astrojs/cloudflare";

import sitemap from "@astrojs/sitemap";

export default defineConfig({
  // static by default; reading routes opt into on-demand via `export const prerender = false`.
  // The Cloudflare adapter emits the Worker that renders + edge-caches those routes.
  output: "static",

  // prerenderEnvironment: "node" — prerender static routes in Node at build time so
  // they can still readBody() book/poem/article text from disk (fmLoader stores only
  // filePath). The default "workerd" prerender has no fs. On-demand routes still run
  // in workerd at runtime and read content via the ASSETS binding instead.
  adapter: cloudflare({ prerenderEnvironment: "node" }),

  // TEMPORARY: placeholder domain, real one (athararchive.com) pending purchase.
  // Mirror in ahlalathar.config.ts.
  site: "https://athar.arthurarchive.com",

  trailingSlash: "never",

  // Prefetch internal links on hover → instant navigation; pairs with the
  // <ClientRouter /> view transitions in Base.astro for an SPA-like feel.
  prefetch: { prefetchAll: true, defaultStrategy: "hover" },

  build: {
    format: "directory",
    // keep all CSS external (no inlined <style>) so the CSP can use style-src 'self'
    inlineStylesheets: "never",
  },

  // Content bodies are rendered through src/lib/sanitize.ts (Prose.astro),
  // the canonical sanitized path — see src/lib/sanitize-schema.ts.
  i18n: {
    defaultLocale: "ar",
    locales: ["ar"],
  },

  integrations: [sitemap()]
});