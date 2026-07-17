// Shared mushaf-verification helpers for the tafsir index scripts
// (gen-tafsir-index-zamanin.ts relocates misprinted ranges with these;
// fix-tafsir-index-zamanin.ts audits the final index with the same math —
// they MUST agree, hence one module).
import fs from "node:fs";
import path from "node:path";
import matter from "gray-matter";
import { parseBook } from "../src/lib/chapters.js";

// EXPLICIT escapes, never literal combining chars: a literal class like
// [ؐ-ً…] can silently span U+0610-U+064B and eat every base Arabic letter,
// making all comparisons vacuously pass (that exact bug blessed a broken
// index once). Strips tashkeel + Quranic annotation marks, unifies
// hamza-alef forms, ya/alef-maqsura, ta-marbuta.
export const norm = (s: string): string =>
  s
    .replace(/[\u0610-\u061A\u064B-\u065F\u0670\u06D6-\u06ED]/g, "")
    .replace(/[\u0623\u0625\u0622\u0671]/g, "\u0627")
    .replace(/ى/g, "ي")
    .replace(/ة/g, "ه")
    .replace(/[^ء-ي ]/g, " ")
    .replace(/\s+/g, " ")
    .trim();

/** surah number → (ayah number → normalized ayah text) */
export function loadMushaf(): Map<number, Map<number, string>> {
  const dir = path.resolve("src/content/quran");
  const out = new Map<number, Map<number, string>>();
  for (const file of fs.readdirSync(dir)) {
    if (!file.endsWith(".md")) continue;
    const { data, content } = matter(fs.readFileSync(path.join(dir, file), "utf-8"));
    const ayat = new Map<number, string>();
    for (const p of parseBook(content).paragraphs) {
      const n = Number(p.id);
      if (Number.isFinite(n)) ayat.set(n, norm(p.text.replace(/<[^>]+>/g, " ")));
    }
    out.set(Number(data.number), ayat);
  }
  return out;
}

export const firstQuote = (body: string): string | null => body.match(/﴿([^﴾]{6,})﴾/)?.[1] ?? null;

/** normalized quote → up-to-12 tokens of length ≥3 */
export const quoteTokens = (q: string): string[] =>
  norm(q).split(" ").filter((t) => t.length >= 3).slice(0, 12);

/** fraction of tokens contained in text — per-token substring containment
 *  tolerates Uthmani/imla'i rasm drift */
export function tokenScore(tokens: string[], text: string): number {
  if (!tokens.length) return 1;
  let hit = 0;
  for (const t of tokens) if (text.includes(t)) hit++;
  return hit / tokens.length;
}

/** concatenated normalized text of surah:lo..hi (missing ayat contribute nothing) */
export function rangeText(mushaf: Map<number, Map<number, string>>, surah: number, lo: number, hi: number): string {
  let out = "";
  for (let a = lo; a <= hi; a++) out += " " + (mushaf.get(surah)?.get(a) ?? "");
  return out;
}
