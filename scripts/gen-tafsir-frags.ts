// Per-verse tafsir fragments (M1 of HANDOFF-perf-size.md). Replaces inlining the
// 93MB quran-tafsir-index.json into every surah page (surah 2 was 7.8MB of HTML)
// with one small static fragment per verse, fetched on demand when the reader
// opens a verse's tafsir sheet. Runs after `astro build`.
import fs from "node:fs";
import path from "node:path";
import { markdownToSafeHtml } from "../src/lib/sanitize.js";
import tafsirIndex from "../src/data/quran-tafsir-index.json" with { type: "json" };

type TafsirNote = { kind: string; label: string; sourceHref?: string; sourceTitle?: string; body: string };

const esc = (s: string) => s.replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");

function main() {
  const index = tafsirIndex as Record<string, TafsirNote[]>;
  const outRoot = path.resolve("dist/client/tafsir-frag");
  let written = 0;

  for (const [verseKey, notes] of Object.entries(index)) {
    if (!notes.length) continue;
    const [surah, ayah] = verseKey.split(":");
    const packId = `ann-quran-${surah}-${ayah}`;
    const entries = notes.map((nt) => {
      const bodyHtml = `<div class="ann-entry-body" data-ar>${markdownToSafeHtml(nt.body)}</div>`;
      const sourceLink = nt.sourceHref
        ? `<a class="ann-source-link" href="${esc(nt.sourceHref)}">اقرأ في موضعه${nt.sourceTitle ? `: ${esc(nt.sourceTitle)}` : ""} ←</a>`
        : "";
      return `<div class="ann-entry k-tafsir" data-kind="${esc(nt.kind)}" data-label="${esc(nt.label)}">${bodyHtml}${sourceLink}</div>`;
    });
    const html = `<div class="ann-pack" id="${packId}" data-ann-pack hidden>${entries.join("")}</div>`;

    const dir = path.join(outRoot, surah);
    fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(path.join(dir, `${ayah}.html`), html, "utf-8");
    written++;
  }

  console.log(`✓ gen-tafsir-frags: ${written} verse fragment(s) → dist/client/tafsir-frag`);
}

main();
