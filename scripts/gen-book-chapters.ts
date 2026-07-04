// Per-chapter book assets (M2 of HANDOFF-perf-size.md). Runs analyzeBook() once
// at build time (moved off the request path) and writes one file per chapter +
// a title/slug manifest, so src/pages/book/[slug]/[chapter].astro reads the one
// chapter instead of re-fetching and re-splitting the whole book per cold request.
// For chunked books this also removes the whole-book .md asset copied by
// copy-content-assets.mjs — the 25 MiB-per-asset risk it created. Runs after
// copy-content-assets.mjs.
//
// Chapter bodies write to dist/r2-upload/ (not dist/client/) — a large book can
// be thousands of chapter files, which pushed deploys toward the Workers Static
// Assets 20k-file ceiling. scripts/upload-r2-assets.mjs pushes that directory to
// the BOOK_ASSETS R2 bucket instead; only the small per-book manifest stays a
// static asset (one file per book, not per chapter).
import fs from "node:fs";
import path from "node:path";
import { loadContentFromDisk } from "../src/lib/load.js";
import { analyzeBook } from "../src/lib/chunk.js";

function main() {
  const books = loadContentFromDisk().filter((e) => e.collection === "book" && e.data.status === "published");
  let chunkedCount = 0;
  let chapterCount = 0;

  for (const book of books) {
    const a = analyzeBook(book.body);
    if (!a.chunked) continue;
    chunkedCount++;

    const dir = path.resolve(`dist/r2-upload/book/${book.id}`);
    fs.mkdirSync(dir, { recursive: true });
    // "اقرأ في موضعه" deep-links (#pN) need to know which chapter a page lives
    // in even for heading-split chapters (no firstPage from page-slicing) — fall
    // back to the first <hr data-page="N"> actually inside the chapter's content.
    const firstPageOf = (c: (typeof a.chapters)[number]) =>
      c.firstPage ?? (c.content.match(/data-page="(\d+)"/)?.[1] ? Number(c.content.match(/data-page="(\d+)"/)![1]) : undefined);
    const manifest = a.chapters.map((c) => ({ title: c.title, rawTitle: c.rawTitle, slug: c.slug, parent: c.parent, parentTitle: c.parentTitle, firstPage: firstPageOf(c) }));
    for (const c of a.chapters) {
      fs.writeFileSync(path.join(dir, `${c.slug}.md`), c.content, "utf-8");
      chapterCount++;
    }
    fs.writeFileSync(
      path.resolve(`dist/client/content/book/${book.id}.chapters.json`),
      JSON.stringify(manifest),
      "utf-8",
    );

    // Whole-book copy (from copy-content-assets.mjs) is no longer read by the
    // chapter route once a manifest exists — drop it, that's the 25MiB risk.
    const wholeBook = path.resolve(`dist/client/content/book/${book.id}.md`);
    if (fs.existsSync(wholeBook)) fs.rmSync(wholeBook);
  }

  console.log(`✓ gen-book-chapters: ${chunkedCount} chunked book(s), ${chapterCount} chapter file(s) → dist/r2-upload/book`);
}

main();
