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
});
