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
  title: string;
  slug: string;
  order: number;
  verses: Verse[];
}

export interface ParsedPoem {
  verses: Verse[];
  verseCount: number;
  openingVerse?: string;
  chapters: PoemChapter[];
}

// A verse line uses ` --- ` (or ` ... `) to separate the two hemistichs.
const HEMISTICH_SEP = /\s+(?:---|\.\.\.|‏…|…)\s+/;

function isContentLine(line: string): boolean {
  const t = line.trim();
  if (!t) return false;
  if (t.startsWith("#")) return false; // sub-headings inside a chapter
  if (/^-{3,}$/.test(t)) return false; // horizontal rule
  return true;
}

function lineToVerse(line: string, n: number): Verse {
  const parts = line.trim().split(HEMISTICH_SEP);
  const sadr = parts[0].trim();
  const ajz = parts.length > 1 ? parts.slice(1).join(" ").trim() : undefined;
  return { n, sadr, ajz, anchor: `v${n}` };
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

  // verses in the preamble are numbered first, then chapter verses
  collect(preamble);
  const chapters: PoemChapter[] = rawChapters.map((c) => ({
    title: c.title,
    slug: c.slug,
    order: c.order,
    verses: collect(c.content),
  }));

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
