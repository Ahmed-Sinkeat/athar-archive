// Threshold-driven chunking. The SAME parsed source decides single-page vs
// chapterized output; the page layer renders one or the other off `chunked`.
// Thresholds live in ahlalathar.config.ts (tunable without reprocessing).

import { config } from "../../ahlalathar.config.js";
import { parseBook, parsePoem, type ParsedBook, type ParsedPoem } from "./chapters.js";

export interface AnalyzedPoem extends ParsedPoem {
  chunked: boolean;
}

export interface AnalyzedBook extends ParsedBook {
  chunked: boolean;
}

// A poem chapterizes when it exceeds the verse threshold AND actually has
// (more than one) chapter to split into; otherwise it stays single-page.
export function analyzePoem(body: string, threshold: number = config.poemChapterThreshold): AnalyzedPoem {
  const parsed = parsePoem(body);
  const aboveThreshold = parsed.verseCount > threshold;
  const chunked = aboveThreshold && parsed.chapters.length > 1;
  return { ...parsed, chunked };
}

// A book chapterizes when it exceeds EITHER the word or chapter-count
// threshold AND has more than one chapter to split into.
export function analyzeBook(
  body: string,
  threshold: { words: number; chapters: number } = config.bookChapterThreshold,
): AnalyzedBook {
  const parsed = parseBook(body);
  const aboveThreshold =
    parsed.wordCount > threshold.words || parsed.chapters.length > threshold.chapters;
  const chunked = aboveThreshold && parsed.chapters.length > 1;
  return { ...parsed, chunked };
}
