// Per-ayah tafsir extraction for sources that self-tag every citation with an
// explicit [السورة: الآية] or [السورة: الآية-الآية] reference — e.g. تفسير
// عبد الرزاق الصنعاني and التعليق على التفسير من كتب ابن أبي الدنيا. Unlike
// gen-tafsir-index-muqatil.ts's marker-INFERRED ranges, these tags are the
// source's OWN explicit citation, so confidence is high — no "expected
// ayah" heuristic needed, just locate each tag and segment the text between
// consecutive ones.
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
  return raw
    .replace(/\\([[\]{}])/g, "$1") // markdown-escaped brackets, e.g. \[ \]
    .replace(/\s+/g, " ")
    .trim();
}

// Canonical surah name -> number, straight from the quran collection —
// diacritic-insensitive since citation tags are sometimes undiacritized even
// when the book body itself is fully vocalized.
function loadSurahNameMap(): Map<string, number> {
  const dir = path.resolve("src/content/quran");
  const map = new Map<string, number>();
  for (const file of fs.readdirSync(dir)) {
    if (!file.endsWith(".md")) continue;
    const { data } = matter(fs.readFileSync(path.join(dir, file), "utf-8"));
    map.set(stripTashkeel(String(data.name)).trim(), Number(data.number));
  }
  return map;
}

// Classical alternate names actually seen in these citation tags that don't
// literally match the collection's canonical `name` field.
const ALT_NAMES: Record<string, string> = {
  "براءة": "التوبة",
  "المؤمن": "غافر",
  "حم السجدة": "فصلت",
  "حم عسق": "الشورى",
  "قاف": "ق", // spelled out in citations, but the collection keys it by the bare letter
};

const TAG_RE = /\\?\[([^\]:]+):\s*(\d+)(?:\s*[-–]\s*(\d+))?\s*\\?\]/g;
// A tag's own paragraph (not a generic backward newline-scan) is the right
// segment boundary — these sources repeat a running "فلان قال:" attribution
// as its OWN short paragraph before every numbered report, and a newline
// scan walks straight past that short paragraph into the wrong one, pulling
// "عبد الرزاق قال:" (belonging to the NEXT report) onto the END of THIS
// report's body. Anchoring on paragraph spans (already tracked for idAt)
// sidesteps that entirely: whichever paragraph contains the tag IS the
// segment start, full stop.
function segmentStart(spans: { start: number; end: number }[], tagStart: number): number {
  return spans.find((s) => tagStart >= s.start && tagStart < s.end)?.start ?? tagStart;
}

function main() {
  const file = process.argv[2];
  const kind = process.argv.find((a) => a.startsWith("--kind="))?.slice(7) ?? "تفسير";
  const mergeInto = process.argv.includes("--merge") ? "src/data/quran-tafsir-index.json" : null;
  if (!file) {
    console.error("usage: tsx scripts/gen-tafsir-index-tagged.ts <book-md-path> [--kind=تفسير] [--merge]");
    process.exit(1);
  }
  const raw = fs.readFileSync(path.resolve(file), "utf-8");
  const { data: fm, content: body } = matter(raw);
  const bookSlug = path.basename(file, ".md");
  const bookTitle = fm.title ?? bookSlug;
  const label = `${kind} — ${bookTitle}`;

  const nameMap = loadSurahNameMap();
  const resolveSurah = (name: string): number | null => {
    let key = stripTashkeel(name).trim();
    key = ALT_NAMES[key] ?? key;
    return nameMap.get(key) ?? null;
  };

  const { paragraphs, chapters } = parseBook(body);

  const index: Record<string, TafsirNote[]> = {};
  let globalIdx = 0;
  let tagsFound = 0;
  let tagsResolved = 0;
  let ayatCovered = 0;
  const unresolvedNames = new Map<string, number>();

  for (const chapter of chapters) {
    const localCount = parseBook(chapter.content).paragraphs.length;
    const chapterParas = paragraphs.slice(globalIdx, globalIdx + localCount);
    globalIdx += localCount;

    const HR_ONLY_RE = /^<hr[^>]*\/>$/;
    // These sources repeat a short "فلان قال:" attribution as its OWN
    // paragraph after every page break (e.g. "عبد الرزاق قال:") — a running
    // header, not content. Left in, it survives as untagged filler between
    // two tagged paragraphs and gets swept onto the END of the body just
    // before it (same class of gap as an untagged report, but pure noise
    // here). A short paragraph that's nothing but "<name> قال:" and has no
    // report number carries no information the numbered report below it
    // doesn't already restate, so drop it.
    const ATTRIBUTION_ONLY_RE = /^(?:[^\d:]{2,30}\s*قَالَ:?|(?:نا|أنا|ثنا|حَدَّثَنَا|أَخْبَرَنَا)\s+[^\d:]{2,30})\s*$/;
    const realParas = chapterParas.filter((p) => {
      const t = p.text.trim();
      return !HR_ONLY_RE.test(t) && !ATTRIBUTION_ONLY_RE.test(stripTags(t));
    });

    let concat = "";
    const spans: { start: number; end: number; id: string }[] = [];
    for (const p of realParas) {
      const start = concat.length;
      concat += stripTags(p.text) + "\n\n";
      spans.push({ start, end: concat.length, id: p.id });
    }
    const idAt = (offset: number) => spans.find((s) => offset >= s.start && offset < s.end)?.id ?? spans[0]?.id;

    const tags: { leadStart: number; tagEnd: number; surahNum: number; ayahStart: number; ayahEnd: number }[] = [];
    for (const m of concat.matchAll(TAG_RE)) {
      tagsFound++;
      const surahNum = resolveSurah(m[1]);
      if (surahNum === null) {
        unresolvedNames.set(m[1], (unresolvedNames.get(m[1]) ?? 0) + 1);
        continue;
      }
      tagsResolved++;
      const ayahStart = Number(m[2]);
      const ayahEnd = m[3] ? Number(m[3]) : ayahStart;
      const tagEnd = m.index! + m[0].length;
      tags.push({ leadStart: segmentStart(spans, m.index!), tagEnd, surahNum, ayahStart, ayahEnd });
    }

    tags.forEach((t, i) => {
      const bodyEnd = tags[i + 1] ? tags[i + 1].leadStart : concat.length;
      const text = cleanBody(concat.slice(t.tagEnd, bodyEnd));
      if (!text) return;
      const href = `/book/${bookSlug}#${idAt(t.tagEnd)}`;
      const note: TafsirNote = { kind, label, sourceSlug: bookSlug, sourceTitle: bookTitle, sourceHref: href, body: text };
      for (let a = t.ayahStart; a <= t.ayahEnd; a++) {
        const key = `${t.surahNum}:${a}`;
        (index[key] ??= []).push(note);
        ayatCovered++;
      }
    });
  }

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

  console.log(`✓ tags found: ${tagsFound}, resolved: ${tagsResolved}, unresolved: ${tagsFound - tagsResolved}`);
  if (unresolvedNames.size) {
    console.log(`✗ unresolved surah names:`, [...unresolvedNames.entries()].sort((a, b) => b[1] - a[1]));
  }
  console.log(`✓ ayat covered: ${ayatCovered}/6236`);
  console.log(`✓ written → ${outPath}`);
}

main();
