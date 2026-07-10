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

  // "hover" never fires on a touchscreen — most traffic here is mobile, so
  // every tap was a cold, unprefetched navigation (the loader you keep seeing
  // even on "static" pages). "tap" starts the fetch on touchstart/mousedown,
  // ahead of the click that actually triggers navigation — same gesture,
  // just gets the network request a head start.
  prefetch: { prefetchAll: true, defaultStrategy: "tap" },

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