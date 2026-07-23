// Copy PUBLISHED on-demand content bodies into dist/client so the Worker can read
// them via the ASSETS binding (/content/<collection>/<id>.md). Drafts are skipped —
// they must not be publicly fetchable. Runs after `astro build`. (migration P1/P3)
import fs from "node:fs";
import path from "node:path";
import matter from "gray-matter";

// collections whose reading routes are on-demand; book-lg is the second half
// of the book collection (big imported texts) and shares book's public URL
// space (/content/book/<id>.md), so it copies into the same output folder
const COLLECTIONS = [["book", "book"], ["book-lg", "book"], ["lesson", "lesson"]];

let copied = 0;
let skipped = 0;
for (const [col, outCol] of COLLECTIONS) {
  const src = path.resolve(`src/content/${col}`);
  const dst = path.resolve(`dist/client/content/${outCol}`);
  if (!fs.existsSync(src)) continue;
  // recursive: ids from fmLoader keep their subdir path, so preserve it
  for (const rel of fs.readdirSync(src, { recursive: true })) {
    if (typeof rel !== "string" || !rel.endsWith(".md")) continue;
    const abs = path.join(src, rel);
    if (!fs.statSync(abs).isFile()) continue;
    if (matter(fs.readFileSync(abs, "utf-8")).data.status !== "published") {
      skipped++;
      continue;
    }
    const out = path.join(dst, rel);
    fs.mkdirSync(path.dirname(out), { recursive: true });
    fs.copyFileSync(abs, out);
    copied++;
  }
}
// astro build's own public/ passthrough copies public/content (a symlink to
// src/content) verbatim — draft status and all — into dist/client/content/
// BEFORE this script runs, including a raw dist/client/content/book-lg/ that
// duplicates what the loop above already wrote (correctly, published-only)
// into dist/client/content/book/. public/.assetsignore lists content/book-lg/**
// specifically to keep that raw copy out of the Cloudflare deploy (oversized
// imports blow the 25 MiB per-asset limit), but that's an unverified ignore
// rule sitting between here and the actual upload — remove the directory
// outright instead of trusting it: nothing ever reads from /content/book-lg/*
// at runtime (only /content/book/* is a real route, via the ASSETS binding).
const rawBookLg = path.resolve("dist/client/content/book-lg");
if (fs.existsSync(rawBookLg)) fs.rmSync(rawBookLg, { recursive: true, force: true });

console.log(`✓ copy-content-assets: ${copied} published copied → dist/client/content, ${skipped} draft(s) skipped`);
