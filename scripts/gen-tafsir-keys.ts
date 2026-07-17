// Small sidecar to src/data/quran-tafsir-index.json: just the verse keys
// that actually have notes, nothing else. [surah].astro only ever needed
// Object.keys(tafsirIndex) — a boolean "does this verse have tafsir" check
// to decide whether to render a clickable ayah-num button — but importing
// the full index (336MB as of the 2026-07-17 tafsir batch: عبد الرزاق,
// التعليق على التفسير من كتب ابن أبي الدنيا, ابن أبي زمنين, السعدي, ابن
// القيم) to get that meant Vite had to parse/bundle the whole thing as a
// page module dependency — OOMs `astro check` and would OOM `astro build`
// too (gen-tafsir-frags.ts already learned this lesson for note BODIES,
// see its own header comment; this is the same fix for the boolean check).
// Runs as a plain Node script (no Vite/Astro involved), so the 336MB read
// itself is cheap — must run BEFORE `astro build` in package.json's build
// script, not after like gen-tafsir-frags.ts.
import fs from "node:fs";
import path from "node:path";

function main() {
  const src = path.resolve("src/data/quran-tafsir-index.json");
  const out = path.resolve("src/data/quran-tafsir-keys.json");
  const index: Record<string, unknown[]> = JSON.parse(fs.readFileSync(src, "utf-8"));
  const keys = Object.entries(index)
    .filter(([, notes]) => notes.length > 0)
    .map(([key]) => key);
  fs.writeFileSync(out, JSON.stringify(keys), "utf-8");
  console.log(`✓ gen-tafsir-keys: ${keys.length} verse key(s) → ${out}`);
}

main();
