// Per-ayah tafsir extraction for تيسير الكريم الرحمن (تفسير السعدي). This
// edition marks each block with an inline ﴿N﴾ ayah-number immediately
// followed by the quoted verse text (often several consecutive ayat
// concatenated as one group, starting at ayah N) — good, high-confidence
// per-ayah anchoring. But its own chapter HEADINGS are unreliable: one
// confirmed case has a block titled "## الفاتحة" that is actually سورة
// المائدة content (right after "آخر تفسير سورة النساء", followed by "وهي
// مدنية" then verse 1 of al-Ma'idah) — a title/content mismatch from the
// import, not a parsing bug here. And a naive "ayah number decreased ->
// new surah" sequence tracker undercounts badly, since many surahs here
// are only marked a handful of times each (grouped quotes), so two
// consecutive surahs can both open on "﴿1﴾" with no detectable drop.
//
// So surah identity comes from the quoted VERSE TEXT itself, cross-checked
// against the real quran collection: ﴿N﴾ already gives the starting ayah
// number, so the only ambiguity is which of the ~114 surahs' own ayah N
// this ayah number belongs to — and at most one of them will have a
// verse whose actual text the quote starts with.
//
// Output is a PREVIEW file, not a write into the live 97MB
// src/data/quran-tafsir-index.json — merging that in is a separate,
// reviewed step (see gen-tafsir-index-muqatil.ts's own note on this).
import fs from "node:fs";
import path from "node:path";
import matter from "gray-matter";
import { parseBook } from "../src/lib/chapters.js";
import { stripTashkeel } from "../src/lib/display.js";

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
// Loose enough to survive minor rasm/tashkeel differences between this
// print and the site's own mus'haf text: strip diacritics, drop anything
// that isn't a base letter or digit (punctuation, superscript alef, etc.).
function normText(s: string): string {
  return stripTashkeel(s)
    // Uthmani rasm writes a construct-state تاء مربوطة as plain ت
    // ("رحمتَ الله" not "رحمة الله") — the site's mus'haf text follows that
    // convention, this print doesn't. Word-FINAL ت only (needs the
    // whitespace lookahead run before spaces get stripped below) — a
    // mid-word ت is a real letter ("تلقى"), not this rasm quirk.
    .replace(/ت(?=\s|$)/g, "ه")
    .replace(/ة/g, "ه")
    .replace(/[آأإ]/g, "ا") // hamza-seat variance (شأ/شإ etc.) — another rasm-vs-simplified-print difference
    .replace(/[^ء-ي0-9]/g, "");
}

// verse index: surahNum -> ayahNum -> normalized text, straight from the
// quran collection (the site's own canonical mus'haf text).
function loadVerseIndex(): Map<number, Map<number, string>> {
  const dir = path.resolve("src/content/quran");
  const bySurah = new Map<number, Map<number, string>>();
  for (const file of fs.readdirSync(dir)) {
    if (!file.endsWith(".md")) continue;
    const { data, content } = matter(fs.readFileSync(path.join(dir, file), "utf-8"));
    const surahNum = Number(data.number);
    const { paragraphs } = parseBook(content);
    const ayat = new Map<number, string>();
    for (const p of paragraphs) ayat.set(Number(p.id), normText(stripTags(p.text)));
    bySurah.set(surahNum, ayat);
  }
  return bySurah;
}

const MARKER_RE = /﴿(\d+)﴾\s*﴿([^﴾]+)﴾/gd;

function main() {
  const file = process.argv[2] ?? "src/content/book-lg/taysir-al-karim-al-rahman.md";
  const mergeInto = process.argv.includes("--merge") ? "src/data/quran-tafsir-index.json" : null;

  const raw = fs.readFileSync(path.resolve(file), "utf-8");
  const { data: fm, content: body } = matter(raw);
  const bookSlug = path.basename(file, ".md");
  const bookTitle = fm.title ?? bookSlug;
  const kind = "تفسير";
  const label = `${kind} — ${bookTitle}`;

  const verseIndex = loadVerseIndex();

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

  type Marker = { start: number; end: number; surahNum: number; ayah: number };
  const markers: Marker[] = [];
  let lastSurah = 1; // sequence prior is only a tie-breaker, not a requirement
  let ambiguous = 0;
  let unmatched = 0;

  for (const m of concat.matchAll(MARKER_RE) as IterableIterator<RegExpMatchArray & { indices: Array<[number, number]> }>) {
    const ayah = Number(m[1]);
    const quoteNorm = normText(m[2]);
    if (!quoteNorm) continue;
    const candidates: number[] = [];
    for (const [surahNum, ayat] of verseIndex) {
      const real = ayat.get(ayah);
      if (real && quoteNorm.startsWith(real)) candidates.push(surahNum);
    }
    let surahNum: number | null = null;
    if (candidates.length === 1) surahNum = candidates[0];
    else if (candidates.length > 1) {
      ambiguous++;
      // tie-break toward whichever candidate keeps us moving forward
      // (or staying put) in mus'haf order relative to the last resolved
      // surah — the book proceeds through it in order, ties are rare and
      // only happen on short/formulaic openings shared across surahs.
      surahNum = candidates.reduce((best, c) =>
        Math.abs(c - lastSurah) < Math.abs(best - lastSurah) ? c : best);
    } else {
      unmatched++;
      continue;
    }
    lastSurah = surahNum;
    const [start, end] = m.indices[0];
    markers.push({ start, end, surahNum, ayah });
  }

  const index: Record<string, TafsirNote[]> = {};
  let ayatCovered = 0;
  markers.forEach((mk, i) => {
    const bodyEnd = markers[i + 1] ? markers[i + 1].start : concat.length;
    const text = cleanBody(concat.slice(mk.end, bodyEnd));
    if (!text) return;
    const href = `/book/${bookSlug}#${idAt(mk.start)}`;
    const note: TafsirNote = { kind, label, sourceSlug: bookSlug, sourceTitle: bookTitle, sourceHref: href, body: text };
    const key = `${mk.surahNum}:${mk.ayah}`;
    (index[key] ??= []).push(note);
    ayatCovered++;
  });

  let outPath: string;
  if (mergeInto) {
    outPath = path.resolve(mergeInto);
    const live: Record<string, TafsirNote[]> = fs.existsSync(outPath) ? JSON.parse(fs.readFileSync(outPath, "utf-8")) : {};
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

  console.log(`✓ resolved: ${markers.length}, ambiguous(tie-broken): ${ambiguous}, unmatched: ${unmatched}`);
  console.log(`✓ ayat covered: ${ayatCovered}/6236`);
  console.log(`✓ written → ${outPath}`);
}

main();
