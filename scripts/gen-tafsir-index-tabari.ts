// Per-ayah tafsir extraction for تفسير الطبري = جامع البيان (ط هجر). This
// edition's compile pass (Athar-Engine) auto-annotates literally every
// recognized Quran quote with a trailing "[السورة: الآية]" tag — including
// quotes deep inside a chapter's body that are mere cross-references (Tabari
// habitually explains one ayah by quoting an unrelated one elsewhere, or
// backs up a point with an ayah from a wholly different surah).
//
// First cut only trusted a heading's OWN leading tag and assigned the whole
// chapter's body to it — safe, but capped coverage at ~38% (2399/6236).
// A "monotonic forward progress only" walk (same idea as
// gen-tafsir-index-muqatil.ts's strict "-N-" marker discipline) did no
// better (1761/6236): checking the actual tag stream directly showed tags
// across the whole book touch 5861/6236 distinct ayat — the ceiling was
// never the tagging, it was how much of it a cautious segmenter refused to
// trust.
//
// This version segments on every tag within a chapter (collapsing only an
// EXACT repeat of the still-open ayah — Tabari re-quotes the same ayah
// across many consecutive hadith chains, and that shouldn't fragment one
// ayah's discussion into dozens of redundant same-key entries). A stray
// cross-reference tag can mis-attribute the text up to the NEXT tag, but
// with ~63k tags across ~59.7k paragraphs (more than one per paragraph),
// that window is typically a sentence, not a paragraph — and it's still
// chapter-scoped, so it can never bleed past a `##` heading boundary into
// an unrelated topic. Headings with no quote at all (سورة dividers, purely
// thematic essays) get zero tags and are skipped entirely.
//
// Output is a PREVIEW file, not a write into the live 97MB
// src/data/quran-tafsir-index.json — merging that in is a separate,
// reviewed step (see gen-tafsir-index-muqatil.ts's own note on this).
import fs from "node:fs";
import path from "node:path";
import matter from "gray-matter";
import { parseBook } from "../src/lib/chapters.js";
import { stripTashkeel } from "../src/lib/display.js";
import { loadMushaf } from "./lib-mushaf.js";

interface TafsirNote {
  kind: string;
  label: string;
  sourceSlug: string;
  sourceTitle: string;
  sourceHref: string;
  body: string;
}

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
const ALT_NAMES: Record<string, string> = {
  "براءة": "التوبة",
  "المؤمن": "غافر",
  "حم السجدة": "فصلت",
  "حم عسق": "الشورى",
  "قاف": "ق",
};

const TAG_RE = /\\?\[([^\]:]+):\s*(\d+)(?:\s*[-–]\s*(\d+))?\s*\\?\]/g;
const HR_ONLY_RE = /^<hr[^>]*\/>$/;
// Mirrors chapters.ts's internal (unexported) H2_RE exactly — needed here to
// re-walk the same heading boundaries chapters.ts finds, but with character
// offsets attached, which its own return value doesn't carry.
const H2_RE = /^##\s+(.+?)\s*$/;

// Exact character offset, in `body`, of every paragraph parseBook returned —
// computed by a single forward-only scan (each search starts right after
// the previous match), so a recurring phrase earlier or later in the book
// can never be mistaken for the current paragraph's real position.
function computeParagraphOffsets(body: string, paragraphs: { text: string }[]): number[] {
  const offsets: number[] = [];
  let cursor = 0;
  for (const p of paragraphs) {
    const needle = p.text.slice(0, 40) || p.text; // anchors are stripped from the END, so a prefix survives intact
    const start = body.indexOf(needle, cursor);
    if (start === -1) throw new Error(`paragraph offset not found forward of cursor ${cursor}: ${JSON.stringify(needle)}`);
    offsets.push(start);
    cursor = start + needle.length;
  }
  return offsets;
}

// Exact [start, end) character span, in `body`, of each chapter's CONTENT
// (heading line excluded) — replicates splitChapters' own line-by-line H2
// walk and empty-chapter filter so the spans line up 1:1, in order, with
// the `chapters` array parseBook returned.
function computeChapterSpans(body: string): { start: number; end: number }[] {
  const lines = body.split("\n");
  const raw: { start: number; end: number; hasContent: boolean }[] = [];
  let offset = 0;
  let current: (typeof raw)[number] | null = null;
  for (const line of lines) {
    const lineStart = offset;
    offset += line.length + 1; // +1 for the '\n' split consumed
    if (H2_RE.test(line)) {
      if (current) raw.push(current);
      current = { start: offset, end: offset, hasContent: false };
    } else if (current) {
      current.end = offset;
      if (line.trim() && !line.trim().startsWith("#")) current.hasContent = true;
    }
  }
  if (current) raw.push(current);
  return raw.filter((c) => c.hasContent).map((c) => ({ start: c.start, end: c.end }));
}

function stripTags(raw: string): string {
  return raw.replace(/<sup[^>]*>.*?<\/sup>/g, "").replace(/<[^>]+>/g, " ");
}
function cleanBody(raw: string): string {
  return raw
    .replace(/\\([[\]{}])/g, "$1")
    .replace(TAG_RE, "") // drop the auto-injected citation noise from rendered prose
    .replace(/\s+/g, " ")
    .trim();
}

interface Boundary { pos: number; tagEnd: number; surahNum: number; ayahLo: number; ayahHi: number }

function main() {
  const file = process.argv[2] ?? "src/content/book-lg/tafsir-tabari.md";
  const mergeInto = process.argv.includes("--merge") ? "src/data/quran-tafsir-index.json" : null;

  const raw = fs.readFileSync(path.resolve(file), "utf-8");
  const { data: fm, content: body } = matter(raw);
  const bookSlug = path.basename(file, ".md");
  const bookTitle = fm.title ?? bookSlug;
  const kind = "تفسير";
  const label = `${kind} — ${bookTitle}`;

  const nameMap = loadSurahNameMap();
  const resolveSurah = (name: string): number | null => {
    let key = stripTashkeel(name).trim();
    key = ALT_NAMES[key] ?? key;
    return nameMap.get(key) ?? null;
  };

  const { paragraphs, chapters } = parseBook(body);
  const paragraphOffsets = computeParagraphOffsets(body, paragraphs);
  const chapterSpans = computeChapterSpans(body);
  if (chapterSpans.length !== chapters.length) {
    throw new Error(`chapter/span count mismatch: ${chapters.length} chapters vs ${chapterSpans.length} spans — computeChapterSpans's empty-chapter filter no longer matches splitChapters'`);
  }

  const index: Record<string, TafsirNote[]> = {};
  let pIdx = 0; // pointer into paragraphs/paragraphOffsets, monotonically advancing
  let chaptersWithTag = 0, chaptersSkipped = 0, segments = 0, ayatCovered = 0, tagsSeen = 0, tagsAccepted = 0;

  chapters.forEach((chapter, ci) => {
    const span = chapterSpans[ci];
    const chapterParas: typeof paragraphs = [];
    while (pIdx < paragraphs.length && paragraphOffsets[pIdx] >= span.start && paragraphOffsets[pIdx] < span.end) {
      chapterParas.push(paragraphs[pIdx]);
      pIdx++;
    }

    // NOT an early return on zero real paragraphs: this print sometimes
    // glues a whole "﴿quote﴾ [tag] يقول: ..." sentence onto the `##` line
    // itself (splitChapters' H2 capture is greedy to end-of-line, no
    // required break after the heading text) — that chapter's actual
    // content span is then empty even though its OWN title carries a real
    // tag and real commentary. Skipping those wholesale silently dropped
    // ayat that only ever appeared this way (confirmed: 37:85, 37:119,
    // 80:9, 80:14, 114:3 — every one of the 5 ayat this book tags
    // SOMEWHERE but this script wasn't reaching). concat always starts
    // from the title regardless, so those tags get scanned either way.
    const realParas = chapterParas.filter((p) => !HR_ONLY_RE.test(p.text.trim()));

    // One concatenated text for the whole chapter (title + every real
    // paragraph), with a span index so a match offset maps back to the
    // paragraph id it fell in (for the href anchor) — falling back to the
    // chapter's own slug when a boundary lands inside the title, before
    // any real paragraph exists yet to anchor to.
    let concat = chapter.title + "\n";
    const spans: { start: number; end: number; id: string }[] = [{ start: 0, end: concat.length, id: chapter.slug }];
    for (const p of realParas) {
      const start = concat.length;
      concat += stripTags(p.text) + "\n\n";
      spans.push({ start, end: concat.length, id: p.id });
    }
    const idAt = (offset: number) => spans.find((s) => offset >= s.start && offset < s.end)?.id ?? spans[0]?.id;

    const boundaries: Boundary[] = [];
    let current: Boundary | null = null;
    for (const m of concat.matchAll(TAG_RE)) {
      tagsSeen++;
      const surahNum = resolveSurah(m[1]);
      if (surahNum === null) continue;
      const ayahLo = Number(m[2]);
      const ayahHi = Number(m[3] ?? m[2]);
      const pos = m.index!;
      const tagEnd = pos + m[0].length;
      // Only reject an EXACT repeat of the still-open segment (Tabari
      // re-quotes fragments of the same ayah across many consecutive hadith
      // chains — that shouldn't fragment one ayah's discussion into dozens
      // of redundant same-key segments). Anything else — forward, backward,
      // even a different surah — starts a new segment: chapter-scoping
      // already bounds the blast radius of a stray cross-reference tag to
      // "until the next tag" (typically a sentence or two, since tags run
      // roughly one per paragraph here), and eating that small risk is what
      // recovers the ayat this book only ever mentions via a cross-reference
      // from a DIFFERENT ayah's heading.
      const repeat = current !== null && surahNum === current.surahNum && ayahLo === current.ayahLo && ayahHi === current.ayahHi;
      const forward = !repeat;
      if (current === null || forward) {
        current = { pos, tagEnd, surahNum, ayahLo, ayahHi };
        boundaries.push(current);
        tagsAccepted++;
      }
      // else: cross-reference (backward / distant / different surah) — folds
      // silently into whichever segment is still open, no new boundary.
    }
    if (!boundaries.length) { chaptersSkipped++; return; }
    chaptersWithTag++;

    boundaries.forEach((b, i) => {
      const textEnd = boundaries[i + 1] ? boundaries[i + 1].pos : concat.length;
      const text = cleanBody(concat.slice(b.tagEnd, textEnd));
      if (!text) return;
      const href = `/book/${bookSlug}#${idAt(b.tagEnd)}`;
      const note: TafsirNote = { kind, label, sourceSlug: bookSlug, sourceTitle: bookTitle, sourceHref: href, body: text };
      segments++;
      for (let a = b.ayahLo; a <= b.ayahHi; a++) {
        const key = `${b.surahNum}:${a}`;
        (index[key] ??= []).push(note);
        ayatCovered++;
      }
    });
  });

  // Refrain reuse: several surahs repeat one ayah's exact wording many
  // times (الرحمن's «فَبِأَيِّ آلَاءِ رَبِّكُمَا تُكَذِّبَانِ» ×31, الشعراء's
  // recurring warner refrains, القمر, المرسلات...). Tabari explains the
  // wording in full on its first occurrence and then, on every repeat,
  // simply notes it's the same as before WITHOUT re-quoting it — so the
  // repeat ayah never gets its own tag and stays outside the tag-based
  // ceiling entirely (measured: 5861/6236). Since the wording is
  // byte-identical, the first occurrence's commentary is straightforwardly
  // still correct for the repeat — reuse it, but say so explicitly rather
  // than silently duplicating (the sourceHref still points at the
  // ORIGINAL occurrence's location in the book, on purpose).
  const mushaf = loadMushaf();
  let refrainsReused = 0;
  for (const [surahNum, ayat] of mushaf) {
    for (const [ayah, text] of ayat) {
      const key = `${surahNum}:${ayah}`;
      if (index[key]) continue; // already covered directly
      const source = [...ayat.entries()].find(([a2, t2]) => a2 !== ayah && t2 === text && index[`${surahNum}:${a2}`]);
      if (!source) continue;
      const [origAyah] = source;
      const origNotes = index[`${surahNum}:${origAyah}`];
      index[key] = origNotes.map((n) => ({
        ...n,
        body: `(نفس نص الآية ${origAyah}، انظر تفسيرها هناك) ${n.body}`,
      }));
      refrainsReused++;
    }
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

  console.log(`✓ chapters: ${chapters.length}, tagged: ${chaptersWithTag}, skipped (no tag at all): ${chaptersSkipped}`);
  console.log(`✓ tags seen: ${tagsSeen}, accepted as boundaries: ${tagsAccepted}, segments written: ${segments}`);
  console.log(`✓ ayat covered: ${ayatCovered} placements / 6236 mushaf ayat (${new Set(Object.keys(index)).size} unique keys, ${refrainsReused} via refrain reuse)`);
  console.log(`✓ written → ${outPath}`);
}

main();
