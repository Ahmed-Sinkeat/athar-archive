// Build-time chapter computation for chunked books. Used by the prerendered
// chapter route's getStaticPaths (src/pages/book/[slug]/[chapter].astro) —
// extracted from scripts/gen-book-chapters.ts when chapter pages moved from
// on-demand Worker rendering to full prerender (the on-demand render rebuilt
// the knowledge graph + ran markdown→HTML per request, which blew the free
// plan's ~10ms CPU budget and 1102'd under load).
import { analyzeBook, deriveVolumes } from "./chunk";
import { readBody } from "./read-body";

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
export function buildBookChapters(body: string): BuiltBook | null {
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
  // per volume in source order, feeds the reader's page/volume jump control.
  // Books without explicit tags fall back to a synthetic volume derived from
  // page-number resets (see deriveVolumes) — see book/[slug].astro for the
  // matching client-side redirect logic that consumes `juz`.
  const rawJuzOf = (c: (typeof a.chapters)[number]) => c.content.match(/data-juz="([^"]+)"/)?.[1];
  const { volumes, juzAt } = deriveVolumes(body, a.chapters.map((c) => ({ firstPage: firstPageOf(c) })));

  const chapters = a.chapters.map((c, i) => {
    return {
      title: c.title,
      rawTitle: c.rawTitle,
      slug: c.slug,
      parent: c.parent,
      parentTitle: c.parentTitle,
      firstPage: firstPageOf(c),
      lastPage: lastPageOf(c),
      juz: rawJuzOf(c) ?? juzAt(i),
      content: c.content,
    };
  });

  return { chapters, catalog: extractCatalog(a.chapters), volumes };
}

// One-book memo for the prerendered chapter route. Chapter text is
// deliberately NOT carried in getStaticPaths props: Astro retains every
// path's props until the whole build ends, so props holding content meant
// the entire chunked corpus (~3GB+ of Arabic text) live in one heap at once
// — the CI OOM of 2026-07-29. Each page render re-derives its book through
// this memo instead. Renders are sequential (build.concurrency defaults to
// 1) and paths stay grouped by book, so each book is rebuilt once per shard
// — the same work getStaticPaths already does to enumerate chapters. Lives
// here (a real module) rather than in the .astro file because Astro hoists
// getStaticPaths out of frontmatter scope AND re-runs frontmatter per page,
// so state declared there neither reaches getStaticPaths nor persists.
let builtMemo: { id: string; built: BuiltBook | null } | null = null;
export async function builtForCached(
  entry: { id: string; filePath?: string; body?: string },
): Promise<BuiltBook | null> {
  if (!builtMemo || builtMemo.id !== entry.id) {
    builtMemo = { id: entry.id, built: buildBookChapters(await readBody(entry)) };
  }
  return builtMemo.built;
}
