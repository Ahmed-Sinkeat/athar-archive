import fs from "node:fs";
import path from "node:path";
import matter from "gray-matter";

const { content } = matter(fs.readFileSync("src/content/book-lg/tafsir-al-quran-al-aziz-ibn-abi-zamanin.md", "utf-8"));
// strip diacritics only, keep everything else (spacing/newlines/punctuation)
// so per-match boundaries stay meaningful — lib-mushaf's norm() flattens too
// aggressively (collapses all whitespace, drops punctuation) for a survey.
const TASHKEEL_RE = /[ؐ-ًؚ-ٰٟۖ-ۭ]/;
const normalize = (s: string) =>
  [...s].filter((c) => !TASHKEEL_RE.test(c)).join("")
    .replace(/[أإآٱ]/g, "ا")
    .replace(/ى/g, "ي")
    .replace(/ة/g, "ه");
const n = normalize(content);

const surahNames: string[] = new Array(115).fill("");
for (const f of fs.readdirSync("src/content/quran")) {
  if (!f.endsWith(".md")) continue;
  const { data } = matter(fs.readFileSync(path.join("src/content/quran", f), "utf-8"));
  surahNames[Number(data.number)] = normalize(String(data.name));
}
const namesByLenDesc = surahNames.filter(Boolean).sort((a, b) => b.length - a.length);

const re = /سور[ةه]\s+[^\n]{2,25}?\s+[^\n]{0,50}/g; // سور[ةه] ...
const matches = [...n.matchAll(re)].map((m) => m[0]);
console.log("total loose matches:", matches.length);

// strip the ACTUAL matched surah name (longest-name-first, so a two-word
// name isn't partially matched by something shorter) instead of guessing
// word count — that silently swallowed "من" into the placeholder before.
const shape = (s: string) => {
  const rest = s.replace(/^سور[ةه]\s+/, "");
  const hit = namesByLenDesc.find((name) => rest.startsWith(name));
  const tail = hit ? rest.slice(hit.length) : rest;
  return "SURAH" + tail.replace(/\d+/g, "#");
};
const shapes = new Map<string, number>();
for (const m of matches) shapes.set(shape(m), (shapes.get(shape(m)) ?? 0) + 1);
const sorted = [...shapes.entries()].sort((a, b) => b[1] - a[1]);
for (const [s, cnt] of sorted) console.log(cnt, JSON.stringify(s));
