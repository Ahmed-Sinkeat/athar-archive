// One-off/idempotent: wrap each run of consecutive verse paragraphs in <!-- verse --> … <!-- /verse -->
// (docs/book-markup-spec.md). Uses the parser's own verse test, so what the app shows as verse is
// what gets marked. Markup only: scripts/format-verify.ts proves no text changed.
//   tsx scripts/wrap-verses.ts src/content/book/<id>.md [...]
import { readFileSync, writeFileSync } from "node:fs";
import { verseHalves } from "../src/lib/app-v2/parser.js";

const isVerse = (paragraph: string) => {
  const lines = paragraph.split("\n");
  return lines.every((line) => line.trim() && !/^\s*(#|>|-\s|\*\s|<)/.test(line) && verseHalves(line));
};

let total = 0;
for (const file of process.argv.slice(2)) {
  const source = readFileSync(file, "utf8");
  const front = source.match(/^---\n[\s\S]*?\n---\n/)?.[0] ?? "";
  const paragraphs = source.slice(front.length).split(/\n{2,}/);
  const out: string[] = [];
  let inMarker = false, inCard = false, run: string[] = [], runs = 0;
  const flush = () => {
    if (!run.length) return;
    out.push("<!-- verse -->", ...run, "<!-- /verse -->");
    runs++; run = [];
  };
  for (const paragraph of paragraphs) {
    const trimmed = paragraph.trim();
    if (/^<!--\s*card\s*-->/.test(trimmed)) inCard = true;
    if (/^<!--\s*(verse|hadith|athar|riwayah|ayah)\s*-->$/.test(trimmed)) inMarker = true;
    const verse = !inMarker && !inCard && trimmed === paragraph && isVerse(paragraph);
    if (verse) run.push(paragraph); else { flush(); out.push(paragraph); }
    if (/<!--\s*\/card\s*-->\s*$/.test(trimmed)) inCard = false;
    if (/^<!--\s*\/(verse|hadith|athar|riwayah|ayah)\s*-->$/.test(trimmed)) inMarker = false;
  }
  flush();
  if (runs) writeFileSync(file, front + out.join("\n\n"));
  total += runs;
  console.log(`${runs ? "wrapped" : "none   "} ${runs} ${file}`);
}
console.log(`${total} verse blocks`);
