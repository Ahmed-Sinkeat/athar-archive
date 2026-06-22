import { defineConfig } from "astro/config";

export default defineConfig({
  output: "static",
  // TEMPORARY: live workers.dev host (sitemap/RSS absolute URLs). Flip to
  // https://ahlalathar.com when the custom domain goes live. Mirror in ahlalathar.config.ts.
  site: "https://athar-archive.ahmedsinkeat2002.workers.dev",
  trailingSlash: "never",
  // Prefetch internal links on hover → instant navigation; pairs with the
  // <ClientRouter /> view transitions in Base.astro for an SPA-like feel.
  prefetch: { prefetchAll: true, defaultStrategy: "hover" },
  build: {
    format: "directory",
    // keep all CSS external (no inlined <style>) so the CSP can use style-src 'self'
    inlineStylesheets: "never",
  },
  i18n: {
    defaultLocale: "ar",
    locales: ["ar"],
  },
  // Content bodies are rendered through src/lib/sanitize.ts (Prose.astro),
  // the canonical sanitized path — see src/lib/sanitize-schema.ts.
});
