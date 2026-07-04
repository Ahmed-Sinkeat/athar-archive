import { defineConfig } from "astro/config";
import cloudflare from "@astrojs/cloudflare";
import react from "@astrojs/react";
import keystatic from "@keystatic/astro";

export default defineConfig({
  // static by default; reading routes opt into on-demand via `export const prerender = false`.
  // The Cloudflare adapter emits the Worker that renders + edge-caches those routes.
  output: "static",
  // React is scoped to the /keystatic admin UI only — rest of the app is React-free.
  integrations: [react(), keystatic()],
  // prerenderEnvironment: "node" — prerender static routes in Node at build time so
  // they can still readBody() book/poem/article text from disk (fmLoader stores only
  // filePath). The default "workerd" prerender has no fs. On-demand routes still run
  // in workerd at runtime and read content via the ASSETS binding instead.
  adapter: cloudflare({ prerenderEnvironment: "node" }),
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
  // ponytail: @keystatic/astro's injected /keystatic routes import a virtual
  // module that Vite's esbuild-based dep scanner can't resolve outside its own
  // resolveId hook — exclude them from pre-bundling. Revisit if Keystatic ships
  // a fix upstream.
  vite: {
    optimizeDeps: {
      exclude: [
        "@keystatic/astro/internal/keystatic-api.js",
        "@keystatic/astro/internal/keystatic-page.js",
        "superstruct",
      ],
    },
    ssr: {
      optimizeDeps: {
        exclude: [
          "@keystatic/astro/internal/keystatic-api.js",
          "@keystatic/astro/internal/keystatic-page.js",
          "superstruct",
        ],
      },
    },
  },
  // Content bodies are rendered through src/lib/sanitize.ts (Prose.astro),
  // the canonical sanitized path — see src/lib/sanitize-schema.ts.
});
