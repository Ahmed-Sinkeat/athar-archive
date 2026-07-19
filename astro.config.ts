import fs from "node:fs";
import path from "node:path";
import { defineConfig } from "astro/config";
import cloudflare from "@astrojs/cloudflare";

import sitemap from "@astrojs/sitemap";

// Dev-only: serve dist/r2-upload/tafsir-frag/* over HTTP. The on-demand route
// at src/pages/tafsir-frag/[surah]/[ayah].html.ts tries `node:fs` for this in
// DEV, but on-demand routes run inside the Cloudflare Workers (workerd)
// sandbox even locally — `fs.readFile` isn't implemented there
// ("[unenv] fs.readFile is not implemented yet!"), so every fragment fetch
// silently 404s. This plugin runs in the plain-Node Vite dev server (outside
// workerd), so real fs access works, and it intercepts the request before it
// ever reaches that broken branch — same fix shape as book-asset.ts's
// assetText(), which fetches dist/client/* from the dev server itself instead
// of reading it directly for the same underlying reason.
function serveTafsirFragDev() {
  return {
    name: "serve-tafsir-frag-dev",
    enforce: "pre" as const,
    configureServer(server: { middlewares: { use: (fn: (req: any, res: any, next: () => void) => void) => void } }) {
      server.middlewares.use((req, res, next) => {
        if (!req.url?.startsWith("/tafsir-frag/")) return next();
        const rel = decodeURIComponent(req.url.split("?")[0]!);
        if (!/^\/tafsir-frag\/\d+\/\d+(\.[a-z0-9-]+)?\.html$/.test(rel)) return next();
        const filePath = path.join(process.cwd(), "dist/r2-upload", rel);
        fs.readFile(filePath, "utf-8", (err, html) => {
          if (err) { res.statusCode = 404; res.end("Not found"); return; }
          res.setHeader("content-type", "text/html; charset=utf-8");
          res.end(html);
        });
      });
    },
  };
}

export default defineConfig({
  // static by default; reading routes opt into on-demand via `export const prerender = false`.
  // The Cloudflare adapter emits the Worker that renders + edge-caches those routes.
  output: "static",

  // One constant per BUILD (evaluated once when this config loads), stamped
  // into Base.astro's <meta name="aa-build"> AND compiled into reader.ts.
  // A ClientRouter soft-nav that crosses a deploy runs the NEW page's module
  // scripts in a document where the OLD modules are still alive — two
  // annotation sheets, double listeners (seen live 2026-07-17). reader.ts
  // compares its compiled-in value against the meta and hard-reloads once.
  vite: {
    define: { __AA_BUILD__: JSON.stringify(String(Date.now())) },
    plugins: [serveTafsirFragDev()],
  },

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

  // /book-pages/ is the chapter prerender shadow path (moved to R2 post-build,
  // served at /book/) — the sitemap runs during `astro build`, before that move
  // happens, so it must rewrite (not drop) these: dropping them meant ~13k real
  // chapter pages — the bulk of the site's actual content — were never listed
  // in the sitemap at all.
  integrations: [
    sitemap({
      filter: (page) => !page.includes("/ga-test"),
      serialize: (item) => ({ ...item, url: item.url.replace("/book-pages/", "/book/") }),
    }),
  ]
});