// Generate dist/client/dl-sizes.json — exact "download for offline" size per
// book/poem/article, so list rows can show the size BEFORE downloading (like
// the Turath app) without a HEAD-probe storm. Runs after astro build +
// gen-book-chapters, when every page this sums actually exists on disk.
//
// bytes(entity) = its dist/client pages (landing + any small-book chapters)
//               + its prerendered R2 chapter pages (dist/r2-upload/pages/…)
//               + its linked audio size_bytes (from src/content/audio/*.md)

import fs from "node:fs";
import path from "node:path";

const CLIENT = path.resolve("dist/client");
const R2PAGES = path.resolve("dist/r2-upload/pages");
const AUDIO_DIR = path.resolve("src/content/audio");

function dirBytes(dir) {
  if (!fs.existsSync(dir)) return 0;
  return fs.readdirSync(dir).reduce((sum, f) => {
    const p = path.join(dir, f);
    const st = fs.statSync(p);
    return sum + (st.isDirectory() ? dirBytes(p) : st.size);
  }, 0);
}

const sizes = {};
for (const kind of ["book", "poem", "article"]) {
  for (const root of [path.join(CLIENT, kind), path.join(R2PAGES, kind)]) {
    if (!fs.existsSync(root)) continue;
    for (const slug of fs.readdirSync(root)) {
      const p = path.join(root, slug);
      if (!fs.statSync(p).isDirectory()) continue;
      sizes[`${kind}:${slug}`] = (sizes[`${kind}:${slug}`] || 0) + dirBytes(p);
    }
  }
}

// audio dominates any book that has it (a 15 MB opus vs ~100 KB of pages) —
// without this the shown size would be a lie for exactly the heaviest items
if (fs.existsSync(AUDIO_DIR)) {
  for (const f of fs.readdirSync(AUDIO_DIR)) {
    if (!f.endsWith(".md")) continue;
    const fm = fs.readFileSync(path.join(AUDIO_DIR, f), "utf-8");
    const type = /^source_type:\s*(\S+)/m.exec(fm)?.[1];
    const id = /^source_id:\s*(\S+)/m.exec(fm)?.[1];
    const bytes = Number(/^size_bytes:\s*(\d+)/m.exec(fm)?.[1] || 0);
    const key = `${type}:${id}`;
    if (type && id && bytes && sizes[key] != null) sizes[key] += bytes;
  }
}

fs.writeFileSync(path.join(CLIENT, "dl-sizes.json"), JSON.stringify(sizes));
const n = Object.keys(sizes).length;
if (n < 100) { console.error(`gen-dl-sizes: only ${n} entries — dist looks incomplete`); process.exit(1); }
console.log(`gen-dl-sizes: ${n} entries → dl-sizes.json (${(fs.statSync(path.join(CLIENT, "dl-sizes.json")).size / 1024).toFixed(1)} KB)`);
