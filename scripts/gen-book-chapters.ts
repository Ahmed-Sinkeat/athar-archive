// Post-build (prerender migration): astro build prerenders every chapter of
// every chunked book at the shadow path /book-pages/<slug>/<chapter> (see
// src/pages/book-pages/[slug]/[chapter].astro). This script then:
//
//   1. Moves those pages out of dist/client into dist/r2-upload/pages/book/ —
//      ~10k files would push deploys toward the Workers Static Assets 20k-file
//      ceiling. scripts/upload-r2-assets.mjs pushes them to the BOOK_ASSETS R2
//      bucket and the thin route src/pages/book/[slug]/[chapter].ts serves them
//      with a single R2 read, so the Worker never renders a chapter at request
//      time (the old on-demand render blew the free plan's CPU budget — 1102).
//
//   2. Rewrites /book-pages/ → /book/ in each page (canonical + og:url come
//      from Astro.url, which saw the shadow path at build time).
//
//   3. Drops the whole-book .md copied by copy-content-assets.mjs for chunked
//      books — nothing reads it at runtime once the book is chapter-split, and
//      a single static asset may not exceed 25MiB.
//
//   4. Replaces hashed /_astro/<name>.<hash>.<css|js> asset URLs with stable
//      /_astro-live/<name>.<css|js> placeholders, and injects the real
//      {logical → hashed} map into dist/server/wrangler.json (vars.CHAPTER_ASSETS)
//      so it ships atomically with the Worker deploy. The thin route substitutes
//      the live URLs per request. Why: the hashes were the ONLY volatile bytes
//      in these pages, so a CSS-only change used to re-md5 all ~20k pages and
//      re-upload ~2.4GB to R2 (28-minute deploys); with placeholders those
//      deploys upload nothing. If Astro ever changes its hash format the regex
//      just stops matching and we fall back to today's full re-upload — wrong
//      speed, never wrong pages.
import fs from "node:fs";
import path from "node:path";

const SRC = path.resolve("dist/client/book-pages");
const OUT = path.resolve("dist/r2-upload/pages/book");
const WRANGLER_JSON = path.resolve("dist/server/wrangler.json");
const ASTRO_DIR = path.resolve("dist/client/_astro");
const ASSET_RE = /\/_astro\/([\w.-]+)\.([A-Za-z0-9_-]{8})\.(css|js)\b/g;
const ASSET_FILE_RE = /^([\w.-]+)\.([A-Za-z0-9_-]{8})\.(css|js)$/;

// logical name ("Base.css") → hashed URL ("/_astro/Base.TzsMRw8c.css") — built
// from dist/client/_astro itself (the one set of assets actually deployed),
// NOT from scanning chapter HTML: CI splits the build across shards (see
// BUILD_ROLE), each running its own Vite build, and Vite's content hash for a
// shared chunk isn't stable across separate invocations even when the source
// is byte-identical — chapters from different shards can carry different,
// equally "real" hashes for the same logical asset. The deployed assets are
// all from one build (build-primary), so that's the only source of truth.
const assetMap = new Map<string, string>();
for (const f of fs.existsSync(ASTRO_DIR) ? fs.readdirSync(ASTRO_DIR) : []) {
  const m = f.match(ASSET_FILE_RE);
  if (m) assetMap.set(`${m[1]}.${m[3]}`, `/_astro/${f}`);
}

function toPlaceholders(html: string): string {
  return html.replace(ASSET_RE, (_full, name: string, _hash: string, ext: string) => `/_astro-live/${name}.${ext}`);
}

let moved = 0;
let unbundled = 0;

for (const slug of fs.existsSync(SRC) ? fs.readdirSync(SRC) : []) {
  const bookDir = path.join(SRC, slug);
  if (!fs.statSync(bookDir).isDirectory()) continue;
  let hadChapters = false;
  for (const ch of fs.readdirSync(bookDir)) {
    const page = path.join(bookDir, ch, "index.html");
    if (!fs.existsSync(page)) continue;
    const html = toPlaceholders(fs.readFileSync(page, "utf-8").replaceAll("/book-pages/", "/book/"));
    const dst = path.join(OUT, slug, `${ch}.html`);
    fs.mkdirSync(path.dirname(dst), { recursive: true });
    fs.writeFileSync(dst, html, "utf-8");
    moved++;
    hadChapters = true;
  }
  if (hadChapters) {
    const wholeBook = path.resolve(`dist/client/content/book/${slug}.md`);
    if (fs.existsSync(wholeBook)) {
      fs.rmSync(wholeBook);
      unbundled++;
    }
  }
}

// the shadow pages must not ship as static assets — /book-pages/ is not a real URL
if (fs.existsSync(SRC)) fs.rmSync(SRC, { recursive: true, force: true });

// ship the placeholder → live-URL map with the Worker itself (see header §4)
if (assetMap.size > 0) {
  const wj = JSON.parse(fs.readFileSync(WRANGLER_JSON, "utf-8"));
  wj.vars = { ...wj.vars, CHAPTER_ASSETS: JSON.stringify(Object.fromEntries(assetMap)) };
  fs.writeFileSync(WRANGLER_JSON, JSON.stringify(wj, null, 2), "utf-8");
}

console.log(`✓ gen-book-chapters: ${moved} chapter page(s) → dist/r2-upload/pages/book (${assetMap.size} asset placeholder(s)), ${unbundled} whole-book md dropped from static assets`);
