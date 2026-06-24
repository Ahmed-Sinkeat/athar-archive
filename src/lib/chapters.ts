// Parse Markdown bodies into derived structure:
//   - chapters from `## …` (h2) markers
//   - Poem verses {n, sadr, ajz?, anchor} with global sequential numbering
//   - Book paragraphs with stable anchors (explicit {#id} or auto p{n})
//   - Lesson heading TOC with slug anchors
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

// --- chapter splitting (h2 = `## …`, exactly two hashes) ---

const H2_RE = /^##\s+(.+?)\s*$/;

export interface RawChapter {
  title: string;
  slug: string;
  order: number;
  content: string; // body lines belonging to this chapter (heading excluded)
}

export interface ChapterSplit {
  preamble: string; // content before the first h2 (may be empty)
  chapters: RawChapter[];
}

export function splitChapters(body: string): ChapterSplit {
  const lines = body.split("\n");
  const preambleLines: string[] = [];
  const chapters: RawChapter[] = [];
  let current: { title: string; lines: string[] } | null = null;

  for (const line of lines) {
    const m = line.match(H2_RE);
    if (m) {
      if (current) chapters.push(finalizeChapter(current, chapters.length));
      current = { title: m[1].trim(), lines: [] };
    } else if (current) {
      current.lines.push(line);
    } else {
      preambleLines.push(line);
    }
  }
  if (current) chapters.push(finalizeChapter(current, chapters.length));

  // dedupe slugs deterministically (-2, -3, …)
  const seen = new Map<string, number>();
  for (const ch of chapters) {
    const base = ch.slug;
    const n = seen.get(base) ?? 0;
    seen.set(base, n + 1);
    if (n > 0) ch.slug = `${base}-${n + 1}`;
  }

  return { preamble: preambleLines.join("\n").trim(), chapters };
}

function finalizeChapter(c: { title: string; lines: string[] }, index: number): RawChapter {
  const slug = slugifyArabic(c.title) || `chapter-${index + 1}`;
  return { title: c.title, slug, order: index + 1, content: c.lines.join("\n").trim() };
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

  return { paragraphs, wordCount, chapters: splitChapters(body).chapters };
}

// --- Lesson (heading TOC) ---

const HEADING_RE = /^(#{2,3})\s+(.+?)\s*$/;

export interface LessonHeading {
  title: string;
  slug: string;
  depth: 2 | 3;
}

export function parseLesson(body: string): { headings: LessonHeading[] } {
  const headings: LessonHeading[] = [];
  const seen = new Map<string, number>();
  for (const line of body.split("\n")) {
    const m = line.match(HEADING_RE);
    if (!m) continue;
    const depth = m[1].length as 2 | 3;
    const title = m[2].trim();
    let slug = slugifyArabic(title) || `section-${headings.length + 1}`;
    const n = seen.get(slug) ?? 0;
    seen.set(slug, n + 1);
    if (n > 0) slug = `${slug}-${n + 1}`;
    headings.push({ title, slug, depth });
  }
  return { headings };
}

// --- anchor enumeration (used by the validator for annotation resolution) ---

export function extractAnchors(collection: string, body: string): Set<string> {
  if (collection === "poem") {
    return new Set(parsePoem(body).verses.map((v) => v.anchor));
  }
  if (collection === "book") {
    return new Set(parseBook(body).paragraphs.map((p) => p.id));
  }
  if (collection === "lesson") {
    return new Set(parseLesson(body).headings.map((h) => h.slug));
  }
  return new Set();
}
