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
    const poem = analyzePoem(poemBody(3, 70)); // 210 verses, 3 chapters
    expect(poem.verseCount).toBe(210);
    expect(poem.chunked).toBe(true);
  });

  it("falls back to single-page when above threshold but lacking chapters", () => {
    const body = Array.from({ length: 250 }, (_, i) => `صدر ${i} --- عجز ${i}`).join("\n");
    const poem = analyzePoem(body);
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
    const book = analyzeBook(body, { words: 10, chapters: 2 }); // force chunked with a tiny fixture
    expect(book.chunked).toBe(true);
    expect(book.chapters.length).toBeGreaterThan(2); // small chapter + 2+ sub-chunks of the giant one
    for (const c of book.chapters) expect(c.content.length).toBeLessThan(giant.length);
  });

  it("tags oversized-chapter slices with parent/parentTitle/firstPage for TOC grouping", () => {
    const giant = Array.from({ length: 80 }, (_, i) => `<hr class="page-sep" data-page="${i + 1}" />\nنص الصفحة ${i + 1}.`).join("\n\n");
    const body = `## باب صغير\n\nنص قصير.\n\n## باب كبير\n\n${giant}\n`;
    const book = analyzeBook(body, { words: 10, chapters: 2 });
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
});
