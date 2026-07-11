// Build-time chapter computation for chunked books. Used by the prerendered
// chapter route's getStaticPaths (src/pages/book/[slug]/[chapter].astro) —
// extracted from scripts/gen-book-chapters.ts when chapter pages moved from
// on-demand Worker rendering to full prerender (the on-demand render rebuilt
// the knowledge graph + ran markdown→HTML per request, which blew the free
// plan's ~10ms CPU budget and 1102'd under load).
import fs from "node:fs";
import path from "node:path";
import { analyzeBook } from "./chunk";
import { parseBook } from "./chapters";
import { isAtharNumberedBook, injectAtharAnchors, type TakhrijLink } from "./hadith";

export interface ChapterMeta {
  title: string;
  rawTitle?: string;
  slug: string;
  parent?: string;
  parentTitle?: string;
  firstPage?: number;
  lastPage?: number;
  juz?: string;
}
export interface CatalogEntry { label: string; value: string }
export interface BuiltBook {
  chapters: (ChapterMeta & { content: string })[];
  catalog: CatalogEntry[];
  volumes: string[];
}

// تفسير الميسر only: this edition's paragraphs are commentary-only (no ﴿ayah﴾
// quoted inline), unlike ibn Kathir / Taysir al-Latif which already quote the
// ayah as part of their own prose. quran-tafsir-index.json (93MB — build-time
// only, never bundled) already has the verified surah:ayah for each paragraph's
// tafsir-muyassar body; matching each chapter paragraph's TEXT against that
// (not its page number or position) sidesteps a real trap: a printed page
// routinely still holds the previous surah's tail commentary, so "paragraph 1
// of this chapter = ayah 1" is wrong at almost every surah boundary.
export const TAFSIR_AYAH_SOURCE = "tafsir-muyassar";
const norm = (s: string) => s.replace(/\s+/g, " ").trim();

export function buildAyahInjector(
  quranSurahs: { number: number; body: string }[],
): (content: string) => string {
  const tafsirIndex = JSON.parse(
    fs.readFileSync(path.resolve("src/data/quran-tafsir-index.json"), "utf-8"),
  ) as Record<string, { sourceSlug: string; body: string }[]>;

  const verseKeyByBody = new Map<string, string>(); // normalized tafsir body -> "surah:ayah"
  for (const [verseKey, notes] of Object.entries(tafsirIndex)) {
    for (const nt of notes) {
      if (nt.sourceSlug === TAFSIR_AYAH_SOURCE) verseKeyByBody.set(norm(nt.body), verseKey);
    }
  }

  const ayahTextByVerseKey = new Map<string, string>(); // "surah:ayah" -> ayah text
  for (const surah of quranSurahs) {
    const { paragraphs } = parseBook(surah.body);
    paragraphs.forEach((p, i) => {
      const cleanText = p.text.replace(/<hr[^>]*>/g, "").trim();
      ayahTextByVerseKey.set(`${surah.number}:${i + 1}`, cleanText);
    });
  }

  const qualifiesAsParagraph = (block: string) => {
    const t = block.trim();
    return !!t && !/^#{1,6}\s/.test(t) && !/^-{3,}$/.test(t);
  };

  return (content: string): string =>
    content
      .split(/(<hr class="page-sep" data-page="\d+"[^>]*\/>)/)
      .map((part) => {
        if (/data-page="\d+"/.test(part)) return part;
        return part
          .split(/\n\s*\n/)
          .map((block) => {
            if (!qualifiesAsParagraph(block)) return block;
            const verseKey = verseKeyByBody.get(norm(block));
            const ayahText = verseKey && ayahTextByVerseKey.get(verseKey);
            // blank line, not \n: keeps the ayah as its own <p> (auto-styled
            // gold via the ﴿…﴾ tok-ayah tokenizer) instead of merging into
            // the commentary paragraph's own text.
            return ayahText ? `﴿ ${ayahText} ﴾\n\n${block}` : block;
          })
          .join("\n\n");
      })
      .join("");
}

// The "المقدمة" chapter some books ship is a publisher/editor catalog block
// (عَلَم / الكتاب / المؤلف / المحقق / الناشر / الطبعة / ...) rather than real
// reading content — pull it out into a `catalog` field so the chapter route
// can show it as a panel above the TOC instead of a fake chapter.
const CATALOG_BULLET_RE = /^-\s+\*\*([^*:]+):\*\*\s*(.+)$/gm;
function extractCatalog(chapters: { title: string; content: string }[]): CatalogEntry[] {
  const muqaddima = chapters.find((c) => c.title.trim() === "المقدمة");
  if (!muqaddima) return [];
  const out: CatalogEntry[] = [];
  for (const m of muqaddima.content.matchAll(CATALOG_BULLET_RE)) {
    out.push({ label: m[1].trim(), value: m[2].trim() });
  }
  return out;
}

// Analyze one book body and return its chapters (with transformed content) +
// manifest metadata, or null when the book is small enough to stay one page.
export function buildBookChapters(
  bookId: string,
  body: string,
  opts: { takhrij: Record<string, TakhrijLink[]>; injectAyat?: (content: string) => string },
): BuiltBook | null {
  const a = analyzeBook(body);
  if (!a.chunked) return null;

  // "اقرأ في موضعه" deep-links (#pN) need to know which chapter a page lives
  // in even for heading-split chapters (no firstPage from page-slicing) — fall
  // back to the first <hr data-page="N"> actually inside the chapter's content.
  const firstPageOf = (c: (typeof a.chapters)[number]) =>
    c.firstPage ?? (c.content.match(/data-page="(\d+)"/)?.[1] ? Number(c.content.match(/data-page="(\d+)"/)![1]) : undefined);
  // last page seen in a chapter's own content — used to derive the book's
  // total page count (max across chapters) for the sidebar header
  const lastPageOf = (c: (typeof a.chapters)[number]) => {
    const pages = [...c.content.matchAll(/data-page="(\d+)"/g)].map((m) => Number(m[1]));
    return pages.length > 0 ? Math.max(...pages) : undefined;
  };
  // multi-volume (مجلد) books carry data-juz on each page-sep — one entry
  // per volume in source order, feeds the reader's page/volume jump control
  const firstJuzOf = (c: (typeof a.chapters)[number]) => c.content.match(/data-juz="([^"]+)"/)?.[1];
  const volumes = [...new Set([...body.matchAll(/data-juz="([^"]+)"/g)].map((m) => m[1]))];

  const atharNumbered = isAtharNumberedBook(parseBook(body).paragraphs);
  const takhrijFor = (n: number) => opts.takhrij[`${bookId}:${n}`];

  const chapters = a.chapters.map((c) => {
    let content = bookId === TAFSIR_AYAH_SOURCE && opts.injectAyat ? opts.injectAyat(c.content) : c.content;
    if (atharNumbered) content = injectAtharAnchors(content, takhrijFor);
    return {
      title: c.title,
      rawTitle: c.rawTitle,
      slug: c.slug,
      parent: c.parent,
      parentTitle: c.parentTitle,
      firstPage: firstPageOf(c),
      lastPage: lastPageOf(c),
      juz: firstJuzOf(c),
      content,
    };
  });

  return { chapters, catalog: extractCatalog(a.chapters), volumes };
}
