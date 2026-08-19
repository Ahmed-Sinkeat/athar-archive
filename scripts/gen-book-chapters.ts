// Post-build (prerender migration): astro build prerenders every chapter of
// every chunked book at the shadow path /book-pages/<slug>/<chapter> (see
// src/pages/book-pages/[slug]/[chapter].astro). This script then:
//
//   1. Moves those pages out of dist/client into dist/r2-upload/pages-v2/book/ —
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
//
//   5. Gzips each finished page independently and stores it as
//      pages-v2/book/<slug>/<chapter>.html.gz. Deterministically — identical
//      HTML must produce identical BYTES, because the uploader's diff is an md5
//      of exactly these bytes, and any volatile byte would re-upload the whole
//      corpus every deploy (the bug the placeholders in §4 were added to kill).
//      Measured 84% smaller than the uncompressed pages/ prefix it replaces, so
//      this also roughly quarters the r2-staging copy the finish runner has to
//      hold on its very tight disk. The thin route gunzips per cache-miss.
//
//
//   6. `--selftest` (no filesystem) checks the gzip determinism the diff
//      depends on. Run it after touching anything in the compression path.
import assert from "node:assert";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { promisify } from "node:util";
import zlib from "node:zlib";

const SRC = path.resolve("dist/client/book-pages");
const OUT = path.resolve("dist/r2-upload/pages-v2/book");
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

// The asset hashes were not in fact the only volatile bytes: <meta
// name="aa-build"> carries Date.now() from config load, so EVERY page's md5
// changed on every build and all 78k re-uploaded to R2 every deploy (~14 min
// of a 58-min pipeline, measured 2026-07-25) — the placeholder scheme above
// was being defeated one meta tag at a time. Blank it here; the thin route
// restamps it with the live build id per request, exactly as it does the
// asset URLs, so what R2 stores no longer has to be a real build id.
const BUILD_META_RE = /(<meta name="aa-build" content=")[^"]*/;

function toPlaceholders(html: string): string {
  return html
    .replace(ASSET_RE, (_full, name: string, _hash: string, ext: string) => `/_astro-live/${name}.${ext}`)
    .replace(BUILD_META_RE, "$1");
}

const gzipAsync = promisify(zlib.gzip);
// zlib default. Level 9 buys 0.1pp of ratio for 1.7× the CPU; level 1 costs
// 2.9pp for 3× the speed. Measured over 400 real pages: 16.15% of raw at 6.
const GZIP_LEVEL = 6;

/**
 * Deterministic gzip: same bytes in ⇒ same bytes out, on any machine, forever.
 * Node already writes MTIME=0 and no FNAME, so the one platform-dependent byte
 * is the OS field (3 on Linux, 0 on Windows) — pin it to 0xFF ("unknown"), and
 * assert MTIME while we're here. This is load-bearing: the uploader skips a
 * page whose md5 matches R2's ETag, so one volatile byte means re-uploading
 * every chapter on every deploy.
 */
async function gzipStable(html: string): Promise<Buffer> {
  const gz = await gzipAsync(Buffer.from(html, "utf-8"), { level: GZIP_LEVEL });
  gz.writeUInt32LE(0, 4); // MTIME
  gz[9] = 0xff; // OS
  return gz;
}

function selftest() {
  const html = `<!doctype html><meta name="aa-build" content=""><p>${"بابٌ في ذكر الأثر ".repeat(400)}</p>`;
  return Promise.all([gzipStable(html), gzipStable(html)]).then(([a, b]) => {
    assert.ok(a.equals(b), "identical HTML must gzip to identical bytes");
    assert.strictEqual(a.readUInt32LE(4), 0, "gzip MTIME must be zero");
    assert.strictEqual(a[9], 0xff, "gzip OS byte must be pinned to 0xFF");
    assert.strictEqual(zlib.gunzipSync(a).toString("utf-8"), html, "gunzip must round-trip the exact HTML");
    // the route decompresses these, and gen-dl-sizes reads the ISIZE trailer
    assert.strictEqual(a.readUInt32LE(a.length - 4), Buffer.byteLength(html), "ISIZE trailer must be the uncompressed length");
    assert.ok(a.length < Buffer.byteLength(html) / 2, "repetitive Arabic HTML must actually compress");
    console.log(`✓ gen-book-chapters selftest: deterministic gzip, exact round-trip, ISIZE correct (level ${GZIP_LEVEL})`);
  });
}

if (process.argv.includes("--selftest")) {
  await selftest();
  process.exit(0);
}

let moved = 0;
let unbundled = 0;

const pages: { page: string; dst: string }[] = [];
for (const slug of fs.existsSync(SRC) ? fs.readdirSync(SRC) : []) {
  const bookDir = path.join(SRC, slug);
  if (!fs.statSync(bookDir).isDirectory()) continue;
  let hadChapters = false;
  for (const ch of fs.readdirSync(bookDir)) {
    const page = path.join(bookDir, ch, "index.html");
    if (!fs.existsSync(page)) continue;
    pages.push({ page, dst: path.join(OUT, slug, `${ch}.html.gz`) });
    hadChapters = true;
  }
  if (hadChapters) {
    fs.mkdirSync(path.join(OUT, slug), { recursive: true });
    const wholeBook = path.resolve(`dist/client/content/book/${slug}.md`);
    if (fs.existsSync(wholeBook)) {
      fs.rmSync(wholeBook);
      unbundled++;
    }
  }
}

// zlib.gzip is async ON THE LIBUV THREADPOOL, so a small pool of in-flight
// compressions is real parallelism, not just interleaving — measured 9.8 MB/s
// single-threaded vs 22+ MB/s here, which is the difference between ~40 and
// ~18 minutes over the whole corpus. build:finish raises UV_THREADPOOL_SIZE to
// match; without that this silently caps at libuv's default of 4.
// ponytail: a fixed pool over a flat list, not a work-stealing scheduler —
// pages vary ~50× in size, so the tail is ragged; only worth fixing if the
// last few percent of the run ever shows up as real wall-clock time.
const CONCURRENCY = Math.max(4, os.availableParallelism?.() ?? 4);
let next = 0;
await Promise.all(
  Array.from({ length: Math.min(CONCURRENCY, pages.length) }, async () => {
    while (next < pages.length) {
      const { page, dst } = pages[next++];
      const html = toPlaceholders(fs.readFileSync(page, "utf-8").replaceAll("/book-pages/", "/book/"));
      fs.writeFileSync(dst, await gzipStable(html));
      // drop the source page immediately — writing all ~75k transformed copies
      // before the single rmSync at the end meant 2× the chapter corpus (~48GB)
      // on disk at once, which filled the CI finish runner (2026-07-30). The
      // final rmSync below still sweeps the emptied dirs.
      fs.rmSync(page);
      moved++;
    }
  }),
);

// the shadow pages must not ship as static assets — /book-pages/ is not a real URL
if (fs.existsSync(SRC)) fs.rmSync(SRC, { recursive: true, force: true });

// ship the placeholder → live-URL map with the Worker itself (see header §4), and
// force Cloudflare's static-asset serving to match trailingSlash: "never" (astro.config.ts).
// The default html_handling ("auto-trailing-slash") 307s /foo -> /foo/, whose canonical
// tag then points back to /foo — the URL that just redirected away from it. GSC flagged
// ~3.4k pages over this ("Alternate page with proper canonical tag", found 2026-07-30).
const wj = JSON.parse(fs.readFileSync(WRANGLER_JSON, "utf-8"));
wj.assets = { ...wj.assets, html_handling: "drop-trailing-slash" };
if (assetMap.size > 0) {
  wj.vars = { ...wj.vars, CHAPTER_ASSETS: JSON.stringify(Object.fromEntries(assetMap)) };
}
// Rollout flag for the pages/ → pages-v2/ migration, read by the thin route:
// "" (default) serves the old uncompressed pages/, "<slug>,<slug>" serves those
// books from pages-v2/, "*" serves everything from it. The route always falls
// back to pages/ when a v2 object is missing or unreadable, so this is a
// canary/kill switch, not a correctness dependency. Set as a CI variable so
// flipping it needs no code change. Delete once pages/ is gone.
wj.vars = { ...wj.vars, CHAPTERS_V2: process.env.CHAPTERS_V2 ?? "" };
fs.writeFileSync(WRANGLER_JSON, JSON.stringify(wj, null, 2), "utf-8");

const gzBytes = pages.reduce((n, { dst }) => n + (fs.existsSync(dst) ? fs.statSync(dst).size : 0), 0);
console.log(
  `✓ gen-book-chapters: ${moved} chapter page(s) → dist/r2-upload/pages-v2/book as .html.gz ` +
  `(${(gzBytes / 1024 ** 3).toFixed(2)} GB compressed, ${assetMap.size} asset placeholder(s), ` +
  `CHAPTERS_V2="${process.env.CHAPTERS_V2 ?? ""}"), ${unbundled} whole-book md dropped from static assets`,
);
