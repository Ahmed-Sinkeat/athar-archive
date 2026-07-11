// Parse Markdown bodies into derived structure:
//   - chapters from `## …` (h2) markers (empty chapters filtered out)
//   - Poem verses {n, sadr, ajz?, anchor} with global sequential numbering
//   - Book paragraphs with stable anchors (explicit {#id} or auto p{n})
//   - Nested heading TOC (h1-h6) for in-page outline / jump-links
// All derivations come from the source body — never hand-stored (FR-C-06).

// --- Arabic-aware slugify ---

// Tashkeel / diacritics and tatweel to strip before slugifying.
const ARABIC_DIACRITICS = /[ؐ-ًؚ-ٰٟۖ-ۭـ]/g;

export function slugifyArabic(input: string): string {
  const slug = input
    .normalize("NFC")
    .replace(ARABIC_DIACRITICS, "")
    .toLowerCase()
    .trim()
    // keep Arabic letters, Arabic-Indic digits, latin alphanumerics; everything else → hyphen
    .replace(/[^ء-ي٠-٩a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
  // Cap length: a dir name must stay under the 255-byte filesystem limit, and
  // Arabic is 2 bytes/char. 80 chars ≈ 160 bytes leaves room for the -N dedup
  // suffix the callers append. Paragraph-long headings (a whole hadith) hit this.
  return slug.slice(0, 80).replace(/-+$/g, "");
}

// Append -2, -3, … until `slug` is unique within `seen`, then record it. Keys on
// the FINAL slug so a natural heading like "الغلط 2" (→ الغلط-2) can't collide
// with a generated "الغلط-2" — the old counter-by-base map let that route clash.
export function uniqueSlug(slug: string, seen: Set<string>): string {
  let s = slug;
  let n = 1;
  while (seen.has(s)) s = `${slug}-${++n}`;
  seen.add(s);
  return s;
}

// --- chapter splitting (h2 = `## …`, exactly two hashes) ---

const H2_RE = /^##\s+(.+?)\s*$/;

export interface RawChapter {
  title: string;
  rawTitle?: string;
  slug: string;
  order: number;
  content: string; // body lines belonging to this chapter (heading excluded)
  parent?: string;      // oversized-chapter slices: the un-split chapter's slug
  parentTitle?: string; // …and its title, for TOC grouping
  firstPage?: number;   // page-marker slices: the first data-page in the slice
}

export interface ChapterSplit {
  preamble: string; // content before the first h2 (may be empty)
  chapters: RawChapter[];
}

export function splitChapters(body: string): ChapterSplit {
  const lines = body.split("\n");
  const preambleLines: string[] = [];
  const rawChapters: { title: string; lines: string[] }[] = [];
  let current: { title: string; lines: string[] } | null = null;

  for (const line of lines) {
    const m = line.match(H2_RE);
    if (m) {
      if (current) rawChapters.push(current);
      current = { title: m[1].trim(), lines: [] };
    } else if (current) {
      current.lines.push(line);
    } else {
      preambleLines.push(line);
    }
  }
  if (current) rawChapters.push(current);

  // ponytail: drop chapters whose content has 0 real paragraphs/verses
  // fixes القحطاني "empty ## القصيدة النونية" → 2 junk chapters bug
  const nonEmpty = rawChapters.filter((c) =>
    c.lines.some((l) => l.trim() && !l.trim().startsWith("#")),
  );

  const chapters = nonEmpty.map((c, i) => finalizeChapter(c, i));

  // dedupe slugs deterministically (-2, -3, …)
  const seen = new Set<string>();
  for (const ch of chapters) ch.slug = uniqueSlug(ch.slug, seen);

  return { preamble: preambleLines.join("\n").trim(), chapters };
}

function finalizeChapter(c: { title: string; lines: string[] }, index: number): RawChapter {
  const cleanTitle = c.title.replace(/<[^>]*>/g, "").replace(/\s+/g, " ").trim();
  const slug = slugifyArabic(cleanTitle) || `chapter-${index + 1}`;
  return {
    title: cleanTitle,
    rawTitle: c.title !== cleanTitle ? c.title : undefined,
    slug,
    order: index + 1,
    content: c.lines.join("\n").trim()
  };
}

// --- Poem ---

export interface Verse {
  n: number;
  sadr: string;
  ajz?: string;
  anchor: string; // v{n}
}

export interface PoemChapter {
  title: string; // empty for the synthetic opening chapter — renderers fall back to MATLA_TITLE where a label is required (cards/TOC) and skip the band where it isn't
  slug: string;
  order: number;
  verses: Verse[];
  prose?: string; // prose-only front-matter chapters on the no-basmala path (see parsePoem)
}

// Recurring editorial front-matter heading (manuscript copies used in the
// tahqiq) — verbatim across most imported poems, always prose, never verses.
// Not a general heuristic: matched by exact known title text only, to avoid
// false-positives on real chapter headings.
const PROSE_CHAPTER_TITLES = new Set(["النسخ المعتمدة في تحقيق هذا المتن"]);
function isProseChapterTitle(title: string): boolean {
  return PROSE_CHAPTER_TITLES.has(title.trim().replace(/:$/, "").trim());
}

// Collapse the recurring «النسخ المعتمدة في تحقيق هذا المتن» bullet block
// (editorial tahqiq front-matter, not author text) into a <details> box so it
// stops opening the reading page. Matched by the exact known bullet text only.
// Page-sep <hr>s inside the block are hoisted after it to keep page markers in
// the reading flow.
const MANUSCRIPT_MARKER = /^-\s*النسخ المعتمدة في تحقيق هذا المتن:?\s*$/;
export function collapseManuscriptNote(body: string): string {
  const lines = body.split("\n");
  const start = lines.findIndex((l) => MANUSCRIPT_MARKER.test(l.trim()));
  if (start === -1) return body;
  const inner: string[] = [];
  const hoisted: string[] = [];
  let end = start + 1;
  for (; end < lines.length; end++) {
    const t = lines[end].trim();
    if (t === "" || t.startsWith("- ")) { inner.push(lines[end]); continue; }
    if (t.startsWith('<hr class="page-sep"')) { hoisted.push(t); continue; }
    break;
  }
  const block = [
    '<details class="book-catalog">',
    "<summary>عن هذه الطبعة والنسخ المعتمدة</summary>",
    "",
    ...inner,
    "",
    "</details>",
    ...hoisted,
  ];
  return [...lines.slice(0, start), ...block, ...lines.slice(end)].join("\n");
}

// Label renderers use for the synthetic untitled opening chapter (chunked
// poems need a card/TOC name; single-page poems just skip the band).
export const MATLA_TITLE = "مطلع المنظومة";
export const MATLA_SLUG = "matla";

export interface ParsedPoem {
  verses: Verse[];
  verseCount: number;
  openingVerse?: string;
  chapters: PoemChapter[];
  // Poem-level front matter (poet bio, manuscript list, khutbah prose before
  // the first verse) — markdown, rendered as a collapsed panel by the poem
  // pages. Only set on the basmala path below.
  frontMatter?: string;
}

// A verse line uses ` --- ` (or ` ... `) to separate the two hemistichs.
const HEMISTICH_SEP = /\s+(?:---|\.\.\.|‏…|…)\s+/;

// spelled out or as the single ligature codepoint (﷽, U+FDFD)
function isBasmalaLine(t: string): boolean {
  return t === "بسم الله الرحمن الرحيم" || t === "﷽";
}

function isContentLine(line: string): boolean {
  const t = line.trim();
  if (!t) return false;
  if (t.startsWith("#")) return false; // sub-headings inside a chapter
  if (/^-{3,}$/.test(t)) return false; // horizontal rule (dash form)
  if (/^<hr\b/i.test(t)) return false; // horizontal rule / page-sep marker (HTML form)
  if (t === "*" || /^\*\s*\*\s*\*$/.test(t)) return false; // "*" / "* * *" ornament dividers
  if (/^\*\*.+\*\*$/.test(t)) return false; // bold-wrapped sub-heading line, e.g. "**[في الوقف]**"
  if (/^\[.+\]$/.test(t)) return false; // bracket-wrapped metadata/labels, e.g. "[عدد الأبيات: ١٠٠]", "[خاتمة]"
  if (isBasmalaLine(t)) return false; // never a verse, wherever it appears
  if (t === "تم بحمد الله") return false; // colophon closing line, not a verse
  return true;
}

// Verses imported with the print edition's own numbering ("١ - يقول…",
// "12- …") carry it into the text and double up with the rendered vnum
// badge — strip the leading number + separator, the parser renumbers anyway.
const LEADING_VERSE_NUM = /^[0-9٠-٩۰-۹]+\s*[-–—.)ـ]\s*/;

function lineToVerse(line: string, n: number): Verse {
  const parts = line.trim().replace(LEADING_VERSE_NUM, "").split(HEMISTICH_SEP);
  const sadr = parts[0].trim();
  const ajz = parts.length > 1 ? parts.slice(1).join(" ").trim() : undefined;
  return { n, sadr, ajz, anchor: `v${n}` };
}

// Many poems have no ## chapters at all — their entire body IS the preamble
// (e.g. diwan-ilbiri--*), so preamble can't be unconditionally excluded from
// verse-counting. But every front-matter preamble in this corpus (the "بطاقة
// الكتاب" book-info card, or a title-only heading) starts with a markdown
// heading as its very first content line — real verses never do. That one
// signal reliably tells the two apart.
function isFrontMatterPreamble(preamble: string): boolean {
  const firstLine = preamble.split("\n").find((l) => l.trim())?.trim();
  return !!firstLine && firstLine.startsWith("#");
}

export function parsePoem(body: string): ParsedPoem {
  const { preamble, chapters: rawChapters } = splitChapters(body);
  const verses: Verse[] = [];
  let counter = 0;

  const collect = (content: string): Verse[] => {
    const out: Verse[] = [];
    for (const line of content.split("\n")) {
      if (!isContentLine(line)) continue;
      counter += 1;
      const v = lineToVerse(line, counter);
      out.push(v);
      verses.push(v);
    }
    return out;
  };

  // Tahqiq-layout front matter is a POEM-level region, not a per-chapter one:
  // bio lines, the manuscript list, the basmala, and sometimes the author's
  // own khutbah prose after it — possibly spanning several ## headings —
  // all precede the poem's first real verse (first line with a hemistich
  // separator). A basmala in that region is the signal this file uses that
  // layout at all; verse-only diwans (no basmala, verses from line one) and
  // heading-only fronts take the plain path below. Whatever precedes the
  // first verse becomes ParsedPoem.frontMatter (the بطاقة الكتاب preamble
  // stays excluded as before), and the verses between the split point and the
  // first real heading become a synthetic untitled opening chapter — so
  // chunked poems keep them reachable and cards stop claiming the manuscript
  // list is "N بيت".
  const excludedPreamble = isFrontMatterPreamble(preamble);
  const segments: { title?: string; slug?: string; excluded: boolean; lines: string[] }[] = [
    { excluded: excludedPreamble, lines: preamble.split("\n") },
    ...rawChapters.map((c) => ({ title: c.title, slug: c.slug, excluded: false, lines: c.content.split("\n") })),
  ];
  let split: { seg: number; line: number } | null = null;
  let sawBasmala = false;
  outer: for (let s = 0; s < segments.length; s++) {
    for (let l = 0; l < segments[s].lines.length; l++) {
      const t = segments[s].lines[l].trim();
      if (isBasmalaLine(t)) sawBasmala = true;
      if (HEMISTICH_SEP.test(segments[s].lines[l])) { split = { seg: s, line: l }; break outer; }
    }
  }

  if (split && sawBasmala) {
    const fmParts: string[] = [];
    const pushFm = (seg: (typeof segments)[number], lines: string[]) => {
      if (seg.excluded) return;
      const text = lines.join("\n").trim();
      if (!text) return;
      fmParts.push((seg.title ? `### ${seg.title}\n\n` : "") + text);
    };
    for (let s = 0; s < split.seg; s++) pushFm(segments[s], segments[s].lines);
    pushFm(segments[split.seg], segments[split.seg].lines.slice(0, split.line));
    // a basmala dangling at the very end belongs to the verses, not the panel
    const frontMatter = fmParts.join("\n\n").replace(/(?:بسم الله الرحمن الرحيم|﷽)\s*$/, "").trim() || undefined;

    const chapters: PoemChapter[] = [];
    const matlaVerses = collect(segments[split.seg].lines.slice(split.line).join("\n"));
    if (matlaVerses.length) chapters.push({ title: "", slug: MATLA_SLUG, order: 0, verses: matlaVerses });
    for (let s = split.seg + 1; s < segments.length; s++) {
      const seg = segments[s];
      chapters.push(
        isProseChapterTitle(seg.title ?? "")
          ? { title: seg.title!, slug: seg.slug!, order: s, verses: [], prose: seg.lines.join("\n") }
          : { title: seg.title!, slug: seg.slug!, order: s, verses: collect(seg.lines.join("\n")) },
      );
    }
    return { verses, verseCount: verses.length, openingVerse: verses[0]?.sadr, chapters, frontMatter };
  }

  // No tahqiq front matter detected — plain layout (diwans, heading-only fronts).
  if (!excludedPreamble) collect(preamble);
  const chapters: PoemChapter[] = rawChapters.map((c) =>
    isProseChapterTitle(c.title)
      ? { title: c.title, slug: c.slug, order: c.order, verses: [], prose: c.content }
      : { title: c.title, slug: c.slug, order: c.order, verses: collect(c.content) },
  );

  return {
    verses,
    verseCount: verses.length,
    openingVerse: verses[0]?.sadr,
    chapters,
  };
}

// --- Book ---

const PARA_ANCHOR_RE = /\{#([a-z0-9][a-z0-9-]*)\}\s*$/i;

export interface BookParagraph {
  id: string; // explicit {#id} or auto p{n}
  text: string;
}

export interface ParsedBook {
  paragraphs: BookParagraph[];
  wordCount: number;
  chapters: RawChapter[];
}

export function parseBook(body: string): ParsedBook {
  const paragraphs: BookParagraph[] = [];
  let auto = 0;

  // paragraphs = blank-line-separated blocks that are not headings/hr
  for (const block of body.split(/\n\s*\n/)) {
    const trimmed = block.trim();
    if (!trimmed) continue;
    if (/^#{1,6}\s/.test(trimmed)) continue; // heading block
    if (/^-{3,}$/.test(trimmed)) continue;
    auto += 1;
    const m = trimmed.match(PARA_ANCHOR_RE);
    const id = m ? m[1] : `p${auto}`;
    const text = m ? trimmed.replace(PARA_ANCHOR_RE, "").trim() : trimmed;
    paragraphs.push({ id, text });
  }

  const wordCount = body
    .replace(/^#{1,6}\s+.*$/gm, "") // drop heading lines from the count
    .replace(PARA_ANCHOR_RE, "")
    .split(/\s+/)
    .filter(Boolean).length;

  // Real prose before the first ## heading (splitChapters' `preamble`) used to
  // just be dropped here — chunked books rendered only `.chapters`, so any
  // text before the first heading (e.g. a book that opens with narrations
  // before its first named باب) silently vanished from the chunked reader.
  // Surface it as its own leading chapter instead of losing it.
  const { preamble, chapters } = splitChapters(body);
  let allChapters = chapters;
  if (preamble) {
    const seen = new Set(chapters.map((c) => c.slug));
    const slug = uniqueSlug(slugifyArabic("مقدمة الكتاب") || "muqaddima", seen);
    allChapters = [
      { title: "مقدمة الكتاب", slug, order: 0, content: preamble },
      ...chapters.map((c) => ({ ...c, order: c.order + 1 })),
    ];
  }

  return { paragraphs, wordCount, chapters: allChapters };
}

// --- Nested TOC (h1-h6 in-page outline for books / audio-books) ---
// h2 = chapter boundary (handled by splitChapters); deeper levels are sub-nodes.
// h1 is intentionally excluded (= book title).

const TOC_HEADING_RE = /^(#{2,6})\s+(.+?)\s*$/;

export interface TocHeading {
  title: string;
  slug: string;
  depth: 2 | 3 | 4 | 5 | 6;
}

/** Extract all h2-h6 headings as flat ordered list; callers nest by depth. */
export function parseToc(body: string): TocHeading[] {
  const headings: TocHeading[] = [];
  const seen = new Set<string>();
  for (const line of body.split("\n")) {
    const m = line.match(TOC_HEADING_RE);
    if (!m) continue;
    const depth = m[1].length as 2 | 3 | 4 | 5 | 6;
    const rawTitle = m[2].trim();
    const cleanTitle = rawTitle.replace(/<[^>]*>/g, "").replace(/\s+/g, " ").trim();
    const slug = uniqueSlug(slugifyArabic(cleanTitle) || `section-${headings.length + 1}`, seen);
    headings.push({ title: cleanTitle, slug, depth });
  }
  return headings;
}

// --- footnote page-grouping ---
// GFM footnote defs ([^fnN]: body) bundle at wherever the source's own page
// ends, not next to whichever inline citation marker they annotate — Ibn
// Kathir routinely piles 5 defs onto the last few words before a page break.
// Rendering them as scattered inline superscripts is misleading (the number
// has no visible connection to anything nearby), so group them by the
// printed page they belong to instead, matching how page-level حاشية/تخريج
// annotations already open in one navigable sheet.

const PAGE_SEP_SPLIT_RE = /(<hr class="page-sep" data-page="\d+"[^>]*\/>)/;
const PAGE_SEP_NUM_RE = /data-page="(\d+)"/;
const FN_DEF_RE = /^\[\^([a-zA-Z0-9_-]+)\]:\s*([\s\S]*)$/;

export interface FootnotesByPage {
  /** Ordered footnote defs for each printed page, in source order. */
  itemsByPage: Map<number, Array<{ id: string; body: string }>>;
  /** Where a given footnote id (e.g. "fn1216") landed: its page + index within it. */
  pageIndexById: Map<string, { page: number; index: number }>;
}

/** Groups a chapter's [^id]: body footnote definitions by printed page. */
export function extractFootnotesByPage(content: string): FootnotesByPage {
  const parts = content.split(PAGE_SEP_SPLIT_RE);

  // Seed the starting page the same way callers already compute it elsewhere:
  // the first page-sep marks the page AFTER this chapter's start, so back up one.
  let pageNum = 1;
  for (const part of parts) {
    const m = part.match(PAGE_SEP_NUM_RE);
    if (m) { pageNum = parseInt(m[1], 10) - 1; break; }
  }

  const itemsByPage = new Map<number, Array<{ id: string; body: string }>>();
  const pageIndexById = new Map<string, { page: number; index: number }>();

  for (const part of parts) {
    const m = part.match(PAGE_SEP_NUM_RE);
    if (m) { pageNum = parseInt(m[1], 10); continue; }
    for (const block of part.split(/\n\s*\n/)) {
      const dm = block.trim().match(FN_DEF_RE);
      if (!dm) continue;
      const [, id, body] = dm;
      if (!itemsByPage.has(pageNum)) itemsByPage.set(pageNum, []);
      const arr = itemsByPage.get(pageNum)!;
      pageIndexById.set(id, { page: pageNum, index: arr.length });
      arr.push({ id, body: body.trim() });
    }
  }

  return { itemsByPage, pageIndexById };
}

// --- anchor enumeration (used by the validator for annotation resolution) ---

export function extractAnchors(collection: string, body: string): Set<string> {
  if (collection === "poem") {
    return new Set(parsePoem(body).verses.map((v) => v.anchor));
  }
  // book + quran: paragraph anchors; also expose heading slugs for TOC links
  if (collection === "book" || collection === "quran") {
    const { paragraphs } = parseBook(body);
    const toc = parseToc(body);
    return new Set([...paragraphs.map((p) => p.id), ...toc.map((h) => h.slug)]);
  }
  return new Set();
}
