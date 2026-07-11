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
import fs from "node:fs";
import path from "node:path";

const SRC = path.resolve("dist/client/book-pages");
const OUT = path.resolve("dist/r2-upload/pages/book");

let moved = 0;
let unbundled = 0;

for (const slug of fs.existsSync(SRC) ? fs.readdirSync(SRC) : []) {
  const bookDir = path.join(SRC, slug);
  if (!fs.statSync(bookDir).isDirectory()) continue;
  let hadChapters = false;
  for (const ch of fs.readdirSync(bookDir)) {
    const page = path.join(bookDir, ch, "index.html");
    if (!fs.existsSync(page)) continue;
    const html = fs.readFileSync(page, "utf-8").replaceAll("/book-pages/", "/book/");
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

console.log(`✓ gen-book-chapters: ${moved} chapter page(s) → dist/r2-upload/pages/book, ${unbundled} whole-book md dropped from static assets`);
