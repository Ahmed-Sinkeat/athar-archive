// Per-ayah tafsir extraction for تفسير القرآن العزيز لابن أبي زمنين. Unlike
// gen-tafsir-index-tagged.ts's LEADING tags ("قوله: {verse} [ref]", content
// after), this edition closes each commentary block with a TRAILING,
// self-declared range footer: "سورة <name> من الآية (N) إلى الآية (M)." or
// "... من الآية (N) فقط." — both start AND end are spelled out explicitly,
// so no "expected ayah" inference is needed, just segment on the footers.
// Chapter headings in this file are corrupted by an OCR/import artifact
// (e.g. "## ِ الأَنْفَالِ"), so surah identity comes from the footer's own
// name, not the heading — processed as one flat sequence of paragraphs
// rather than per-chapter.
//
// Output is a PREVIEW file, not a write into the live 97MB
// src/data/quran-tafsir-index.json — merging that in is a separate,
// reviewed step (see gen-tafsir-index-muqatil.ts's own note on this).
import fs from "node:fs";
import path from "node:path";
import matter from "gray-matter";
import { parseBook } from "../src/lib/chapters.js";
import { stripTashkeel } from "../src/lib/display.js";
import { loadMushaf, firstQuote, quoteTokens, tokenScore, rangeText } from "./lib-mushaf.js";

interface TafsirNote {
  kind: string;
  label: string;
  sourceSlug: string;
  sourceTitle: string;
  sourceHref: string;
  body: string;
}

function stripTags(raw: string): string {
  return raw.replace(/<sup[^>]*>.*?<\/sup>/g, "").replace(/<[^>]+>/g, " ");
}
function cleanBody(raw: string): string {
  return raw.replace(/\s+/g, " ").trim();
}

function loadSurahNameMap(): { names: Map<string, number>; counts: Map<number, number> } {
  const dir = path.resolve("src/content/quran");
  const names = new Map<string, number>();
  const counts = new Map<number, number>();
  for (const file of fs.readdirSync(dir)) {
    if (!file.endsWith(".md")) continue;
    const { data } = matter(fs.readFileSync(path.join(dir, file), "utf-8"));
    names.set(stripTashkeel(String(data.name)).trim(), Number(data.number));
    counts.set(Number(data.number), Number(data.ayah_count));
  }
  return { names, counts };
}
const ALT_NAMES: Record<string, string> = {
  "براءة": "التوبة",
  "المؤمن": "غافر",
  "حم السجدة": "فصلت",
  "حم عسق": "الشورى",
  "قاف": "ق",
};

// "سورة <name> من الآية (N) إلى الآية (M)." — but this print run is
// inconsistent about it: missing parens, "حتى" instead of "إلى", a bare
// ayah with no closing clause at all, plural الآيات in the lead phrase, even
// an OCR-split digit ("7 8" for 78) in a couple of spots. Matching against a
// diacritic/letter-variant-normalized copy of the text covers every variant
// actually seen (see the survey that produced this pattern) — but this
// print is so heavily vocalized that nearly every letter carries a mark, so
// a length-preserving replace (mark -> space) shatters words into
// letter-by-letter spacing ("مِنْ" -> "م ن"). Deleting marks instead means
// match positions in the normalized text no longer line up with the
// original, so this also returns a position map back to it.
const TASHKEEL_RE = /[ً-ٰٟۖ-ۭ]/; // NOT chapters.ts's ARABIC_DIACRITICS: that range starts at U+0610, which overlaps the entire base Arabic letter block (U+0621-U+064A) and eats real letters too
function normalizeWithMap(s: string): { norm: string; map: number[] } {
  let norm = "";
  const map: number[] = [];
  for (let i = 0; i < s.length; i++) {
    let ch = s[i];
    if (TASHKEEL_RE.test(ch)) continue; // dropped entirely, not mapped
    if (ch === "آ" || ch === "أ" || ch === "إ") ch = "ا"; // آ أ إ -> ا
    else if (ch === "ى") ch = "ي"; // ى -> ي
    else if (ch === "ة") ch = "ه"; // ة -> ه
    norm += ch;
    map.push(i);
  }
  return { norm, map };
}
const RANGE_RE = /سور[ةه]\s+([^\n.]{2,25}?)\s+(?:الايات?\s+)?من\s*الاي[ةه]\s*\(?\s*(\d+(?:\s\d)?)\s*\)?\s*(?:(?:الي|حتي)\s*(?:(?:ال)?ايه?\s*)?\(?\s*(\d+(?:\s\d)?)\s*\)?|فقط)?\s*\./gd;

function main() {
  const file = process.argv[2] ?? "src/content/book-lg/tafsir-al-quran-al-aziz-ibn-abi-zamanin.md";
  // --replace: strip this book's existing notes from the live index first,
  // then merge fresh — the recovery path for the trailing→leading semantics
  // fix (plain --merge only ever APPENDS, it can't heal wrong placements)
  const replace = process.argv.includes("--replace");
  const mergeInto = replace || process.argv.includes("--merge") ? "src/data/quran-tafsir-index.json" : null;

  const raw = fs.readFileSync(path.resolve(file), "utf-8");
  const { data: fm, content: body } = matter(raw);
  const bookSlug = path.basename(file, ".md");
  const bookTitle = fm.title ?? bookSlug;
  const kind = "تفسير";
  const label = `${kind} — ${bookTitle}`;

  const { names: nameMap, counts: ayahCounts } = loadSurahNameMap();
  const resolveSurah = (name: string): number | null => {
    let key = stripTashkeel(name).trim();
    key = ALT_NAMES[key] ?? key;
    return nameMap.get(key) ?? null;
  };

  const { paragraphs } = parseBook(body);
  const HR_ONLY_RE = /^<hr[^>]*\/>$/;
  const realParas = paragraphs.filter((p) => !HR_ONLY_RE.test(p.text.trim()));

  let concat = "";
  const spans: { start: number; end: number; id: string }[] = [];
  for (const p of realParas) {
    const start = concat.length;
    concat += stripTags(p.text) + "\n\n";
    spans.push({ start, end: concat.length, id: p.id });
  }
  const idAt = (offset: number) => spans.find((s) => offset >= s.start && offset < s.end)?.id ?? spans[0]?.id;

  const { norm: normConcat, map: posMap } = normalizeWithMap(concat);

  const index: Record<string, TafsirNote[]> = {};
  const markers: { start: number; end: number; surahNum: number; ayahStart: number; ayahEnd: number }[] = [];
  let unresolved = 0;
  const unresolvedNames = new Map<string, number>();

  const numFrom = (s?: string) => (s === undefined ? undefined : Number(s.replace(/\s+/g, ""))); // "7 8" -> 78
  for (const m of normConcat.matchAll(RANGE_RE) as IterableIterator<RegExpMatchArray & { indices: Array<[number, number] | undefined> }>) {
    const nameSpan = m.indices[1]!;
    const name = concat.slice(posMap[nameSpan[0]], posMap[nameSpan[1] - 1] + 1); // original (diacritized) name
    const surahNum = resolveSurah(name);
    if (surahNum === null) {
      unresolved++;
      unresolvedNames.set(name, (unresolvedNames.get(name) ?? 0) + 1);
      continue;
    }
    const ayahStart = numFrom(m[2])!;
    const ayahEnd = numFrom(m[3]) ?? ayahStart; // no closing clause, or "فقط" = single ayah
    const origStart = posMap[m.indices[0]![0]];
    const matchEndNorm = m.indices[0]![1];
    const origEnd = matchEndNorm >= posMap.length ? concat.length : posMap[matchEndNorm];
    markers.push({ start: origStart, end: origEnd, surahNum, ayahStart, ayahEnd });
  }

  // Misprint repair, content-driven: the print misnumbers a handful of
  // headers (a literal repeat of «سورة الحجر من الآية (1) إلى (8)» where
  // النحل starts, «الأنعام (11)-(18)» in the middle of الأعراف). Order-based
  // repair cascades (one overshoot makes every following CORRECT header look
  // wrong), so instead each block's own first ﴿quote﴾ is verified against the
  // mushaf: a block that clearly fails its claimed range is relocated to the
  // best-matching ayah (keeping the claimed span), or dropped if nothing
  // matches confidently — absent beats wrong.
  const mushaf = loadMushaf();
  let relocatedN = 0, droppedN = 0;
  markers.forEach((mk, i) => {
    const block = concat.slice(mk.end, markers[i + 1]?.start ?? concat.length);
    const q = firstQuote(block);
    if (!q) return;
    const tokens = quoteTokens(q);
    if (tokenScore(tokens, rangeText(mushaf, mk.surahNum, mk.ayahStart - 2, mk.ayahEnd + 2)) >= 0.4) return;
    let best = 0, bestSurah = 0, bestAyah = 0;
    for (const [surah, ayat] of mushaf) {
      for (const [ayah, text] of ayat) {
        const sc = tokenScore(tokens, text);
        if (sc > best) { best = sc; bestSurah = surah; bestAyah = ayah; }
      }
    }
    const span = mk.ayahEnd - mk.ayahStart;
    if (best >= 0.7) {
      const end = Math.min(bestAyah + span, ayahCounts.get(bestSurah) ?? bestAyah);
      console.log(`  ↺ ${mk.surahNum}:${mk.ayahStart}-${mk.ayahEnd} → ${bestSurah}:${bestAyah}-${end}  ﴿${q.slice(0, 40)}…﴾`);
      mk.surahNum = bestSurah; mk.ayahStart = bestAyah; mk.ayahEnd = end;
      relocatedN++;
    } else {
      console.log(`  ✗ dropped unlocatable block ${mk.surahNum}:${mk.ayahStart}-${mk.ayahEnd}  ﴿${q.slice(0, 40)}…﴾ (best ${best.toFixed(2)})`);
      mk.ayahStart = 1; mk.ayahEnd = 0; // empty range → contributes nothing below
      droppedN++;
    }
  });
  if (relocatedN || droppedN) console.log(`✓ misprint repair: ${relocatedN} relocated, ${droppedN} dropped`);

  let ayatCovered = 0;
  markers.forEach((mk, i) => {
    // LEADING headers, not trailing footers: the range line precedes its
    // commentary («سورة الحجر من الآية (1) إلى الآية (8).» then قوله: ﴿الر…﴾).
    // The first extraction assumed trailing and shifted every block one range
    // forward — mushaf-verification (fix-tafsir-index-zamanin.ts) caught it:
    // only 685/2622 placements matched their claimed ayat, with mismatches
    // consistently pointing a few ayat BACK. Block = this marker's end to the
    // next marker's start.
    const text = cleanBody(concat.slice(mk.end, markers[i + 1]?.start ?? concat.length));
    if (!text) return;
    const href = `/book/${bookSlug}#${idAt(mk.end)}`;
    const note: TafsirNote = { kind, label, sourceSlug: bookSlug, sourceTitle: bookTitle, sourceHref: href, body: text };
    for (let a = mk.ayahStart; a <= mk.ayahEnd; a++) {
      const key = `${mk.surahNum}:${a}`;
      (index[key] ??= []).push(note);
      ayatCovered++;
    }
  });

  let outPath: string;
  if (mergeInto) {
    outPath = path.resolve(mergeInto);
    const live: Record<string, TafsirNote[]> = fs.existsSync(outPath) ? JSON.parse(fs.readFileSync(outPath, "utf-8")) : {};
    if (replace) {
      let stripped = 0;
      for (const [key, notes] of Object.entries(live)) {
        const keep = notes.filter((n) => n.sourceSlug !== bookSlug);
        stripped += notes.length - keep.length;
        if (keep.length) live[key] = keep;
        else delete live[key];
      }
      console.log(`✓ stripped ${stripped} existing ${bookSlug} note placement(s)`);
    }
    let merged = 0;
    for (const [key, notes] of Object.entries(index)) {
      const existing = (live[key] ??= []);
      for (const note of notes) {
        if (existing.some((n) => n.sourceSlug === note.sourceSlug && n.body === note.body)) continue;
        existing.push(note);
        merged++;
      }
    }
    fs.writeFileSync(outPath, JSON.stringify(live), "utf-8");
    console.log(`✓ merged ${merged} new note(s) into ${outPath} (${Object.keys(live).length} verse keys total)`);
  } else {
    outPath = path.resolve(`src/data/${bookSlug}-tafsir-index.preview.json`);
    fs.writeFileSync(outPath, JSON.stringify(index, null, 1), "utf-8");
  }

  console.log(`✓ markers found: ${markers.length + unresolved}, resolved: ${markers.length}, unresolved: ${unresolved}`);
  if (unresolvedNames.size) console.log(`✗ unresolved surah names:`, [...unresolvedNames.entries()].sort((a, b) => b[1] - a[1]));
  console.log(`✓ ayat covered: ${ayatCovered}/6236`);
  console.log(`✓ written → ${outPath}`);
}

main();
