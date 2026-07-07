// Per-chapter book assets (M2 of HANDOFF-perf-size.md). Runs analyzeBook() once
// at build time (moved off the request path) and writes one file per chapter +
// a title/slug manifest, so src/pages/book/[slug]/[chapter].astro reads the one
// chapter instead of re-fetching and re-splitting the whole book per cold request.
// For chunked books this also removes the whole-book .md asset copied by
// copy-content-assets.mjs — the 25 MiB-per-asset risk it created. Runs after
// copy-content-assets.mjs.
//
// Chapter bodies write to dist/r2-upload/ (not dist/client/) — a large book can
// be thousands of chapter files, which pushed deploys toward the Workers Static
// Assets 20k-file ceiling. scripts/upload-r2-assets.mjs pushes that directory to
// the BOOK_ASSETS R2 bucket instead; only the small per-book manifest stays a
// static asset (one file per book, not per chapter).
import fs from "node:fs";
import path from "node:path";
import { loadContentFromDisk } from "../src/lib/load.js";
import { analyzeBook } from "../src/lib/chunk.js";
import { parseBook } from "../src/lib/chapters.js";
import { isAtharNumberedBook, injectAtharAnchors, type TakhrijLink } from "../src/lib/hadith.js";
import takhrijData from "../src/data/takhrij.json" with { type: "json" };

// تفسير الميسر only: this edition's paragraphs are commentary-only (no ﴿ayah﴾
// quoted inline), unlike ibn Kathir / Taysir al-Latif which already quote the
// ayah as part of their own prose. quran-tafsir-index.json (93MB — build-time
// only, never imported at request time) already has the verified surah:ayah
// for each paragraph's tafsir-muyassar body; matching each chapter paragraph's
// TEXT against that (not its page number or position) sidesteps a real trap:
// a printed page routinely still holds the previous surah's tail commentary,
// so "paragraph 1 of this chapter = ayah 1" is wrong at almost every surah
// boundary. Text-matching finds the true ayah regardless of where it sits.
const TAFSIR_AYAH_SOURCE = "tafsir-muyassar";
const norm = (s: string) => s.replace(/\s+/g, " ").trim();

function buildAyahInjector(allEntries: ReturnType<typeof loadContentFromDisk>) {
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
  const quranEntries = allEntries.filter((e) => e.collection === "quran");
  for (const surah of quranEntries) {
    const { paragraphs } = parseBook(surah.body);
    paragraphs.forEach((p, i) => {
      const cleanText = p.text.replace(/<hr[^>]*>/g, "").trim();
      ayahTextByVerseKey.set(`${surah.data.number}:${i + 1}`, cleanText);
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
// reading content — as its own chapter it competed for a slot in the TOC
// (and a reader had to click into it just to see who edited the book). Pull
// it out at build time into a small `catalog` field on the manifest instead,
// so the chapter route can show it as a panel above the TOC on every chapter
// of that book. Most books' "المقدمة" is genuine intro prose with none of
// these bullets — catalog stays empty and nothing changes for them.
const CATALOG_BULLET_RE = /^-\s+\*\*([^*:]+):\*\*\s*(.+)$/gm;
function extractCatalog(chapters: { title: string; content: string }[]): { label: string; value: string }[] {
  const muqaddima = chapters.find((c) => c.title.trim() === "المقدمة");
  if (!muqaddima) return [];
  const out: { label: string; value: string }[] = [];
  for (const m of muqaddima.content.matchAll(CATALOG_BULLET_RE)) {
    out.push({ label: m[1].trim(), value: m[2].trim() });
  }
  return out;
}

function main() {
  const allEntries = loadContentFromDisk();
  const books = allEntries.filter((e) => e.collection === "book" && e.data.status === "published");
  const injectAyat = buildAyahInjector(allEntries);
  let chunkedCount = 0;
  let chapterCount = 0;

  for (const book of books) {
    const a = analyzeBook(book.body);
    if (!a.chunked) continue;
    chunkedCount++;

    const dir = path.resolve(`dist/r2-upload/book/${book.id}`);
    fs.mkdirSync(dir, { recursive: true });
    // "اقرأ في موضعه" deep-links (#pN) need to know which chapter a page lives
    // in even for heading-split chapters (no firstPage from page-slicing) — fall
    // back to the first <hr data-page="N"> actually inside the chapter's content.
    const firstPageOf = (c: (typeof a.chapters)[number]) =>
      c.firstPage ?? (c.content.match(/data-page="(\d+)"/)?.[1] ? Number(c.content.match(/data-page="(\d+)"/)![1]) : undefined);
    const manifest = a.chapters.map((c) => ({ title: c.title, rawTitle: c.rawTitle, slug: c.slug, parent: c.parent, parentTitle: c.parentTitle, firstPage: firstPageOf(c) }));
    const atharNumbered = isAtharNumberedBook(parseBook(book.body).paragraphs);
    const takhrijFor = (n: number) => (takhrijData as Record<string, TakhrijLink[]>)[`${book.id}:${n}`];
    for (const c of a.chapters) {
      let content = book.id === TAFSIR_AYAH_SOURCE ? injectAyat(c.content) : c.content;
      if (atharNumbered) content = injectAtharAnchors(content, takhrijFor);
      fs.writeFileSync(path.join(dir, `${c.slug}.md`), content, "utf-8");
      chapterCount++;
    }
    const catalog = extractCatalog(a.chapters);
    fs.writeFileSync(
      path.resolve(`dist/client/content/book/${book.id}.chapters.json`),
      JSON.stringify({ chapters: manifest, catalog }),
      "utf-8",
    );

    // Whole-book copy (from copy-content-assets.mjs) is no longer read by the
    // chapter route once a manifest exists — drop it, that's the 25MiB risk.
    const wholeBook = path.resolve(`dist/client/content/book/${book.id}.md`);
    if (fs.existsSync(wholeBook)) fs.rmSync(wholeBook);
  }

  console.log(`✓ gen-book-chapters: ${chunkedCount} chunked book(s), ${chapterCount} chapter file(s) → dist/r2-upload/book`);
}

main();
