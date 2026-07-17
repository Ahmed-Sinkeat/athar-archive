// Per-ayah tafsir extraction for التفسير القيم (تفسير ابن القيم). Unlike
// تيسير الكريم الرحمن, this book's chapter headings are reliable and
// self-contained: each one names both the surah NUMBER and either a single
// ayah ("## سورة البقرة (2) : آية 261") or an explicit range ("## سورة
// الفاتحة (1) : الآيات 1 الى 7") — no name lookup or cross-referencing
// needed, just read the heading and assign the whole chapter's body to it.
// Ibn al-Qayyim wrote thematic essays rather than ayah-by-ayah notes, so
// range chapters get the same content duplicated across every ayah in the
// range (same honesty tradeoff as gen-tafsir-index-muqatil.ts's ranges) —
// this is the one book of the five done this session where that's a
// structural feature of the source, not a parsing compromise.
//
// Output is a PREVIEW file, not a write into the live 97MB
// src/data/quran-tafsir-index.json — merging that in is a separate,
// reviewed step (see gen-tafsir-index-muqatil.ts's own note on this).
import fs from "node:fs";
import path from "node:path";
import matter from "gray-matter";
import { parseBook } from "../src/lib/chapters.js";

// A heading's own stated range can be wrong (source-text typo, not a
// parsing bug — e.g. this print's سورة الفلق heading says "الآيات 1 الى 6"
// but الفلق only has 5 ayat, and the same page's own prose says so: "سورة
// الفلق خمس آيات"). Clip against the real count so no fabricated
// "surah:ayah" key gets written past where the surah actually ends — same
// defensive pattern gen-tafsir-index-muqatil.ts already uses for this.
function loadAyahCounts(): Map<number, number> {
  const dir = path.resolve("src/content/quran");
  const map = new Map<number, number>();
  for (const file of fs.readdirSync(dir)) {
    if (!file.endsWith(".md")) continue;
    const { data } = matter(fs.readFileSync(path.join(dir, file), "utf-8"));
    map.set(Number(data.number), Number(data.ayah_count));
  }
  return map;
}

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

const HEADING_RE = /^سورة\s+.+?\s*\((\d+)\)\s*:\s*(?:آية\s*(\d+)|الآيات\s*(\d+)\s*الى\s*(\d+))\s*$/;

function main() {
  const file = process.argv[2] ?? "src/content/book-lg/al-tafsir-al-qayyim.md";
  const mergeInto = process.argv.includes("--merge") ? "src/data/quran-tafsir-index.json" : null;

  const raw = fs.readFileSync(path.resolve(file), "utf-8");
  const { data: fm, content: body } = matter(raw);
  const bookSlug = path.basename(file, ".md");
  const bookTitle = fm.title ?? bookSlug;
  const kind = "تفسير";
  const label = `${kind} — ${bookTitle}`;

  const ayahCounts = loadAyahCounts();
  const { paragraphs, chapters } = parseBook(body);
  const HR_ONLY_RE = /^<hr[^>]*\/>$/;

  // Ibn al-Qayyim's own "فصل" sub-sections split into separate H2 chapters
  // of their own (parseBook only splits on H2), but they're continuations
  // of whatever "سورة X (N): آية/الآيات ..." heading precedes them, not
  // standalone content — accumulate them into the current range instead of
  // skipping them, or a lot of substantive commentary goes missing.
  type Pending = { surahNum: number; ayahStart: number; ayahEnd: number; anchorId: string; parts: string[] };
  let current: Pending | null = null;
  const flush = (index: Record<string, TafsirNote[]>) => {
    if (!current) return;
    const text = cleanBody(current.parts.join("\n\n"));
    if (text) {
      const href = `/book/${bookSlug}#${current.anchorId}`;
      const note: TafsirNote = { kind, label, sourceSlug: bookSlug, sourceTitle: bookTitle, sourceHref: href, body: text };
      for (let a = current.ayahStart; a <= current.ayahEnd; a++) {
        const key = `${current.surahNum}:${a}`;
        (index[key] ??= []).push(note);
      }
    }
    current = null;
  };

  const index: Record<string, TafsirNote[]> = {};
  let globalIdx = 0;
  let chaptersMatched = 0;
  let chaptersSkipped = 0;

  for (const chapter of chapters) {
    const localCount = parseBook(chapter.content).paragraphs.length;
    const chapterParas = paragraphs.slice(globalIdx, globalIdx + localCount);
    globalIdx += localCount;
    const realParas = chapterParas.filter((p) => !HR_ONLY_RE.test(p.text.trim()));
    const chapterText = cleanBody(realParas.map((p) => stripTags(p.text)).join("\n\n"));

    const m = chapter.title.trim().match(HEADING_RE);
    if (m) {
      flush(index);
      chaptersMatched++;
      const surahNum = Number(m[1]);
      const ayahStart = m[2] ? Number(m[2]) : Number(m[3]);
      const ayahEndRaw = m[2] ? Number(m[2]) : Number(m[4]);
      const ayahEnd = Math.min(ayahEndRaw, ayahCounts.get(surahNum) ?? ayahEndRaw);
      current = { surahNum, ayahStart, ayahEnd, anchorId: realParas[0]?.id ?? "p1", parts: chapterText ? [chapterText] : [] };
    } else if (current && chapterText) {
      current.parts.push(chapterText); // e.g. a "فصل" continuing the current range
    } else {
      chaptersSkipped++; // biography/preamble before the first surah heading
    }
  }
  flush(index);
  const ayatCovered = Object.values(index).reduce((n, notes) => n + notes.length, 0);

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

  console.log(`✓ chapters matched: ${chaptersMatched}, skipped (non-surah, e.g. biography): ${chaptersSkipped}`);
  console.log(`✓ ayat covered: ${ayatCovered}/6236`);
  console.log(`✓ written → ${outPath}`);
}

main();
