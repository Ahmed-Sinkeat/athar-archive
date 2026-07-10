import { describe, it, expect } from "vitest";
import { analyzePoem, analyzeBook } from "./chunk.js";

// --- generators ---

function poemBody(chapters: number, versesPerChapter: number): string {
  let out = "";
  for (let c = 1; c <= chapters; c++) {
    out += `## باب ${c}\n\n`;
    for (let v = 1; v <= versesPerChapter; v++) out += `صدر ${c}-${v} --- عجز ${c}-${v}\n`;
    out += "\n";
  }
  return out;
}

function bookBody(chapters: number, wordsPerChapter: number): string {
  let out = "";
  for (let c = 1; c <= chapters; c++) {
    out += `## باب ${c}\n\n`;
    out += `${Array.from({ length: wordsPerChapter }, () => "كلمة").join(" ")}\n\n`;
  }
  return out;
}

describe("analyzePoem", () => {
  it("stays single-page below the verse threshold", () => {
    const poem = analyzePoem(poemBody(2, 5)); // 10 verses
    expect(poem.verseCount).toBe(10);
    expect(poem.chunked).toBe(false);
  });

  it("chapterizes above the verse threshold with multiple chapters", () => {
    // explicit threshold — independent of ahlalathar.config.ts's tunable default
    const poem = analyzePoem(poemBody(3, 70), 200); // 210 verses, 3 chapters
    expect(poem.verseCount).toBe(210);
    expect(poem.chunked).toBe(true);
  });

  it("falls back to single-page when above threshold but lacking chapters", () => {
    const body = Array.from({ length: 250 }, (_, i) => `صدر ${i} --- عجز ${i}`).join("\n");
    const poem = analyzePoem(body, 200);
    expect(poem.verseCount).toBe(250);
    expect(poem.chapters).toHaveLength(0);
    expect(poem.chunked).toBe(false);
  });

  it("respects a custom threshold (same source, different decision)", () => {
    const body = poemBody(3, 5); // 15 verses, 3 chapters
    expect(analyzePoem(body, 200).chunked).toBe(false);
    expect(analyzePoem(body, 10).chunked).toBe(true);
  });
});

describe("analyzeBook", () => {
  it("stays single-page below both thresholds", () => {
    const book = analyzeBook(bookBody(2, 100)); // 200 words, 2 chapters
    expect(book.chunked).toBe(false);
  });

  it("chapterizes when over the word threshold", () => {
    const book = analyzeBook(bookBody(2, 3100)); // ~6200 words, 2 chapters
    expect(book.wordCount).toBeGreaterThan(6000);
    expect(book.chunked).toBe(true);
  });

  it("chapterizes when over the chapter-count threshold", () => {
    const book = analyzeBook(bookBody(9, 10)); // 9 chapters > 8
    expect(book.chapters).toHaveLength(9);
    expect(book.chunked).toBe(true);
  });

  it("falls back to single-page when long but having one chapter", () => {
    const book = analyzeBook(bookBody(1, 7000)); // 7000 words, single chapter
    expect(book.wordCount).toBeGreaterThan(6000);
    expect(book.chapters).toHaveLength(1);
    expect(book.chunked).toBe(false);
  });

  it("re-splits an oversized chapter (headingless, page-marker-only) so no on-demand render exceeds ~40 pages", () => {
    // one small chapter + one giant, headingless-inside chapter with 80 page
    // markers (mirrors mowatta-malik's كتاب الاستئذان: 0 sub-headings, 694
    // page-seps in a single chapter — the real prod incident this guards).
    const giant = Array.from({ length: 80 }, (_, i) => `<hr class="page-sep" data-page="${i + 1}" />\nنص الصفحة ${i + 1}.`).join("\n\n");
    const body = `## باب صغير\n\nنص قصير.\n\n## باب كبير\n\n${giant}\n`;
    // maxPagesForNoSplit: 0 disables the page-span gate (this fixture's own
    // 80-page span would otherwise stay under it) — this test is specifically
    // about the oversized-single-chapter safeguard, not the small-book policy.
    const book = analyzeBook(body, { words: 10, chapters: 2 }, 0); // force chunked with a tiny fixture
    expect(book.chunked).toBe(true);
    expect(book.chapters.length).toBeGreaterThan(2); // small chapter + 2+ sub-chunks of the giant one
    for (const c of book.chapters) expect(c.content.length).toBeLessThan(giant.length);
  });

  it("tags oversized-chapter slices with parent/parentTitle/firstPage for TOC grouping", () => {
    const giant = Array.from({ length: 80 }, (_, i) => `<hr class="page-sep" data-page="${i + 1}" />\nنص الصفحة ${i + 1}.`).join("\n\n");
    const body = `## باب صغير\n\nنص قصير.\n\n## باب كبير\n\n${giant}\n`;
    const book = analyzeBook(body, { words: 10, chapters: 2 }, 0);
    const small = book.chapters.find((c) => c.title === "باب صغير");
    expect(small?.parent).toBeUndefined();
    const slices = book.chapters.filter((c) => c.parent === "باب-كبير");
    expect(slices.length).toBeGreaterThan(1);
    for (const s of slices) {
      expect(s.parentTitle).toBe("باب كبير");
      expect(s.firstPage).toBeGreaterThan(0);
      expect(s.title).toMatch(/^صفح/); // "صفحة N" / "صفحات S-E", not "باب كبير — صفحات S-E"
    }
    // firstPage strictly increases across slices in source order.
    for (let i = 1; i < slices.length; i++) expect(slices[i].firstPage!).toBeGreaterThan(slices[i - 1].firstPage!);
  });

  it("keeps prose before the first ## heading as its own leading chapter (real bug: it used to vanish from chunked books)", () => {
    const preambleText = "نص افتتاحي قبل أول باب.";
    const body = `${preambleText}\n\n${bookBody(9, 10)}`; // 9 real chapters, forces chunking
    const book = analyzeBook(body);
    expect(book.chunked).toBe(true);
    expect(book.chapters[0].title).toBe("مقدمة الكتاب");
    expect(book.chapters[0].content).toBe(preambleText);
    expect(book.chapters).toHaveLength(10); // preamble + 9 named chapters
    expect(book.chapters[1].title).toBe("باب 1");
  });

  it("has no leading preamble chapter when the body starts directly with a heading", () => {
    const book = analyzeBook(bookBody(9, 10));
    expect(book.chapters[0].title).toBe("باب 1");
  });

  it("stays single-page under the page-span cap even when over the chapter-count threshold (small fiqh matn with many brief chapters)", () => {
    // 9 chapters (> threshold 8) but only spans pages 10-40 (30 pages, well under 100)
    let body = "";
    for (let c = 1; c <= 9; c++) {
      body += `## باب ${c}\n\n<hr class="page-sep" data-page="${10 + c * 3}" />\nنص قصير.\n\n`;
    }
    const book = analyzeBook(body);
    expect(book.chapters).toHaveLength(9);
    expect(book.chunked).toBe(false);
  });

  it("still chapterizes over the page-span cap (genuinely long book)", () => {
    let body = "";
    for (let c = 1; c <= 9; c++) {
      body += `## باب ${c}\n\n<hr class="page-sep" data-page="${c * 20}" />\nنص قصير.\n\n`; // spans ~180 pages
    }
    const book = analyzeBook(body);
    expect(book.chunked).toBe(true);
  });

  it("falls back to word/chapter thresholds when the source has no page markers at all", () => {
    const book = analyzeBook(bookBody(9, 10)); // 9 chapters, no <hr data-page> anywhere
    expect(book.chunked).toBe(true); // unmeasurable page span → threshold rule still applies
  });
});
