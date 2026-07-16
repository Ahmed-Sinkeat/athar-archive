// Per-ayah tafsir extraction for تفسير مقاتل بن سليمان (and, in principle, any
// classical تفسير printed the same way: the whole surah dumped as one block,
// then continuous commentary with occasional inline "-N-" verse-end markers
// instead of per-ayah headings/paragraphs).
//
// Unlike tafsir-muyassar/tafsir-ibn-kathir (paragraph- or heading-per-ayah,
// so quran-tafsir-index.json can key a note to a single "surah:ayah" with
// confidence), this edition offers no such guarantee — see
// docs/HANDOFF-quran-hadith.md's TAFSIR_AYAH_SOURCE note and the investigation
// that produced this script. So instead of claiming single-ayah precision,
// each commentary chunk between two *confirmed* markers is emitted as a
// RANGE and duplicated across every ayah key in that range — honest about
// how finely this 8th-century text actually demarcates itself.
//
// A marker "-N-" only confirms a boundary when N is EXACTLY the next
// expected ayah number for the CURRENT surah (strict, no off-by-one
// tolerance — a lenient +1 skip risks silently absorbing a systematic
// misnumbering for an entire surah). Any other "-N-" (a cross-reference to
// a different surah, a citation, noise) is ignored and folds into whichever
// range is still open.
//
// Output is a PREVIEW file, not a write into the live 97MB
// src/data/quran-tafsir-index.json — merging that in is a separate,
// reviewed step (see docs task list / conversation).
import fs from "node:fs";
import path from "node:path";
import matter from "gray-matter";
import { parseBook } from "../src/lib/chapters.js";

interface TafsirNote {
  kind: string;
  label: string;
  sourceSlug: string;
  sourceTitle: string;
  sourceHref: string;
  body: string;
}

// This edition's own printed heading is wrong for these two surahs (Ar-Ra'd
// says "1 الى 113", real count 43; Al-Qamar says "1 الى 62", real count 55) —
// a source-text typo, not a parsing bug. Clip to the real count so no
// "surah:ayah" key gets fabricated past where the surah actually ends.
const REAL_AYAH_COUNT_OVERRIDE: Record<number, number> = { 13: 43, 54: 55 };

const SURAH_HEADING_RE = /^\[سورة\s+.+?\s*\((\d+)\)\s*:\s*الآيات\s*(\d+)\s*الى\s*(\d+)\]$/;
const MARKER_RE = /-\s*(\d+)\s*-/g;
// Phrases Muqatil uses right before citing a DIFFERENT surah's ayah for
// comparison ("نظيرها في الأنبياء...-17-") — a "-N-" preceded by one of these
// within a short lookback is a citation, not this surah's next verse.
const CROSS_REF_CUE_RE = /(نظيرها في|وفي سورة|في سورة|قال في|كقوله في|قوله في|انظر|راجع|سبق في|مثلها في)[^-]{0,60}$/;

// Diacritic-insensitive "بسم الله الرحمن الرحيم" — the classification note's
// own prefix text is often plain/undiacritized while the Quranic Basmala
// quoted right after it is fully diacritized (or vice versa book-wide), so
// an exact string match only catches one form.
const DIACRITIC = "[\\u064B-\\u065F\\u0670]*";
const BASMALA_RE = new RegExp([...`بسم الله الرحمن الرحيم`].map((ch) => (ch === " " ? "\\s+" : ch + DIACRITIC)).join(""));

// Strip footnote sup markers and any other tags up front, before the blurb
// regex runs against it — leaving them in lets a footnote ref land mid-blurb
// (real case: "سورة الرعد<sup ...>1</sup>مكية...") and blow past a char-count
// cap sized for plain text.
function stripTags(raw: string): string {
  return raw.replace(/<sup[^>]*>.*?<\/sup>/g, "").replace(/<[^>]+>/g, " ");
}

function cleanBody(raw: string): string {
  return raw
    .replace(/\[\d+\s*[أب]\]/g, "") // manuscript folio markers like [3 ب]
    .replace(/\s+/g, " ")
    .trim();
}

function main() {
  const file = process.argv[2];
  if (!file) {
    console.error("usage: tsx scripts/gen-tafsir-index-muqatil.ts <book-md-path>");
    process.exit(1);
  }
  const raw = fs.readFileSync(path.resolve(file), "utf-8");
  const { data: fm, content: body } = matter(raw);
  const bookSlug = path.basename(file, ".md");
  const bookTitle = fm.title ?? bookSlug;

  const { paragraphs, chapters } = parseBook(body);

  const index: Record<string, TafsirNote[]> = {};
  let globalIdx = 0;
  let surahsProcessed = 0;
  let ayatCovered = 0;
  let rangesEmitted = 0;
  let widestRange = 0;

  for (const chapter of chapters) {
    const localCount = parseBook(chapter.content).paragraphs.length;
    const chapterParas = paragraphs.slice(globalIdx, globalIdx + localCount);
    globalIdx += localCount;

    const m = chapter.title.trim().match(SURAH_HEADING_RE);
    if (!m || chapterParas.length <= 1) continue;
    const [, surahNumStr, , ayahEndStr] = m;
    const surahNum = Number(surahNumStr);
    const ayahEnd = Math.min(Number(ayahEndStr), REAL_AYAH_COUNT_OVERRIDE[surahNum] ?? Infinity);
    surahsProcessed++;

    // parseBook treats each bare <hr class="page-sep" .../> as its own
    // "paragraph" (no heading, no --- to exclude it) — drop those before
    // anything else, or paragraph-position assumptions below (which
    // paragraph is the ayah dump, which is a stray after-dump blurb) shift
    // by however many page breaks happen to fall in between.
    const HR_ONLY_RE = /^<hr[^>]*\/>$/;
    const realParas = chapterParas.filter((p) => !HR_ONLY_RE.test(p.text.trim()));
    // Skip the first paragraph: the whole-surah ayah dump, not commentary.
    let commentary = realParas.slice(1);

    // Some surahs put a مكية/مدنية classification note (name, Meccan/Medinan
    // status, sometimes exception clauses quoting other ayat, ayah count —
    // wording varies too much to regex-match reliably) either as its own
    // paragraph right after the ayah dump, or glued onto the SAME paragraph
    // as the real commentary's opening. The real commentary always restarts
    // with the Basmala, so use that as the anchor: drop any leading
    // paragraph(s) that don't contain it, then trim whatever precedes it in
    // the paragraph that does. Diacritic-insensitive — the note's own prefix
    // text is often undiacritized while the Quranic quote right after it is
    // fully diacritized, so an exact string match misses half of these.
    const firstBasmalaIdx = commentary.findIndex((p) => BASMALA_RE.test(stripTags(p.text)));
    if (firstBasmalaIdx > 0) commentary = commentary.slice(firstBasmalaIdx);

    // Concatenate with offset tracking so a marker's position maps back to
    // whichever paragraph (== printed page, in this edition) it landed in.
    let concat = "";
    const spans: { start: number; end: number; id: string }[] = [];
    for (const p of commentary) {
      const start = concat.length;
      let text = stripTags(p.text);
      if (start === 0) {
        const basmalaMatch = text.match(BASMALA_RE);
        if (basmalaMatch && basmalaMatch.index! > 0) text = text.slice(basmalaMatch.index!);
      }
      concat += text + "\n\n";
      spans.push({ start, end: concat.length, id: p.id });
    }

    const idAt = (offset: number) => spans.find((s) => offset >= s.start && offset < s.end)?.id ?? spans[0]?.id;

    let expected = 1;
    let segStart = 0;
    const pushSegment = (ayahStart: number, ayahEndSeg: number, text: string, startOffset: number) => {
      const clean = cleanBody(text);
      if (!clean) return;
      const href = `/book/${bookSlug}#${idAt(startOffset)}`;
      const note: TafsirNote = {
        kind: "تفسير",
        label: `تفسير — ${bookTitle}`,
        sourceSlug: bookSlug,
        sourceTitle: bookTitle,
        sourceHref: href,
        body: clean,
      };
      rangesEmitted++;
      widestRange = Math.max(widestRange, ayahEndSeg - ayahStart + 1);
      for (let a = ayahStart; a <= ayahEndSeg; a++) {
        const key = `${surahNum}:${a}`;
        (index[key] ??= []).push(note);
        ayatCovered++;
      }
    };

    for (const match of concat.matchAll(MARKER_RE)) {
      const n = Number(match[1]);
      const matchStart = match.index!;
      // Forward progress only. n > expected resyncs past a skipped/undermarked
      // digression — required, or a single miss desyncs "expected" for the
      // rest of the surah with no way back — but only when it's not a cited
      // ayah from another surah (cross-ref cue immediately before it).
      if (n < expected || n > ayahEnd) continue;
      if (n > expected && CROSS_REF_CUE_RE.test(concat.slice(Math.max(0, matchStart - 80), matchStart))) continue;
      const matchEnd = matchStart + match[0].length;
      pushSegment(expected, n, concat.slice(segStart, matchStart), segStart); // exclude the "-N-" marker itself from displayed text
      segStart = matchEnd;
      expected = n + 1;
    }
    // Trailing text after the last confirmed marker, if it still owes ayat.
    if (expected <= ayahEnd && segStart < concat.length) {
      pushSegment(expected, ayahEnd, concat.slice(segStart), segStart);
    }
  }

  const outPath = path.resolve("src/data/muqatil-tafsir-index.preview.json");
  fs.writeFileSync(outPath, JSON.stringify(index, null, 1), "utf-8");

  const totalAyat = 6236;
  console.log(`✓ surahs processed: ${surahsProcessed}/114`);
  console.log(`✓ ayat covered: ${ayatCovered}/${totalAyat} (${((ayatCovered / totalAyat) * 100).toFixed(1)}%)`);
  console.log(`✓ ranges emitted: ${rangesEmitted} (avg ${(ayatCovered / rangesEmitted).toFixed(2)} ayat/range, widest ${widestRange})`);
  console.log(`✓ written → ${outPath}`);
}

main();
