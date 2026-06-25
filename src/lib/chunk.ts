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

import type { RawChapter } from "./chapters.js";

function splitByPages(body: string, pagesPerChunk: number = 40): RawChapter[] {
  const SEP_RE = /(<hr[^>]*class=["']page-sep["'][^>]*data-page=["']\d+["'][^>]*\/?>)/gi;
  const segments = body.split(SEP_RE);
  if (segments.length < 3) return [];
  
  const chapters: RawChapter[] = [];
  let currentContent = "";
  let firstPageInChunk: string | null = null;
  let lastPageInChunk: string | null = null;
  let pageCount = 0;
  
  const extractPage = (marker: string) => marker.match(/data-page=["'](\d+)["']/)?.[1] || "";

  for (let i = 0; i < segments.length; i++) {
    const isMarker = i % 2 !== 0;
    const text = segments[i];
    
    currentContent += text;
    
    if (isMarker) {
      const p = extractPage(text);
      if (!firstPageInChunk) firstPageInChunk = p;
      lastPageInChunk = p;
      pageCount++;
      
      if (pageCount >= pagesPerChunk) {
         const s = firstPageInChunk || "1";
         const e = lastPageInChunk || s;
         chapters.push({
           title: s === e ? `صفحة ${s}` : `صفحات ${s}-${e}`,
           slug: `pages-${s}-${e}`,
           order: chapters.length + 1,
           content: currentContent.trim()
         });
         currentContent = "";
         pageCount = 0;
         firstPageInChunk = null;
      }
    } else if (i === segments.length - 1 && currentContent.trim()) {
       const s = firstPageInChunk || lastPageInChunk || "1";
       const e = lastPageInChunk || s;
       chapters.push({
         title: s === e ? `صفحة ${s}` : `صفحات ${s}-${e}`,
         slug: `pages-${s}-${e}`,
         order: chapters.length + 1,
         content: currentContent.trim()
       });
    }
  }
  return chapters;
}

// A book chapterizes when it exceeds EITHER the word or chapter-count
// threshold AND has more than one chapter to split into.
export function analyzeBook(
  body: string,
  threshold: { words: number; chapters: number } = config.bookChapterThreshold,
): AnalyzedBook {
  const parsed = parseBook(body);
  let aboveThreshold =
    parsed.wordCount > threshold.words || parsed.chapters.length > threshold.chapters;
  
  let chunked = aboveThreshold && parsed.chapters.length > 1;
  
  // Phase 3: Giant chunking. If over threshold but no/one chapter, split by page markers.
  if (aboveThreshold && parsed.chapters.length <= 1) {
    const pageChapters = splitByPages(body, 40);
    if (pageChapters.length > 1) {
      parsed.chapters = pageChapters;
      chunked = true;
    }
  }
  
  return { ...parsed, chunked };
}
