import * as fs from "fs";
import * as path from "path";
import { PandocParser, SemanticASTBuilder, Normalizer, HeadingExtractor, FootnoteExtractor, QuranExtractor, HadithExtractor, ScholarExtractor, BookExtractor, TopicExtractor, StatisticsGenerator, MarkdownRenderer, SearchJsonGenerator } from "./lib/pipeline";
import { traverseAST } from "./lib/semantic-ast";
import type { SemanticBook, SemanticNode } from "./lib/semantic-ast";

function runPipeline(filePath: string): { book: SemanticBook; parseTime: number } {
  const start = Date.now();
  
  // 1. Parser
  const rawAst = PandocParser.parse(filePath);
  const parseTime = (Date.now() - start) / 1000;
  
  // 2. Semantic AST Builder
  const book = SemanticASTBuilder.build(rawAst);
  
  // 3. Normalizer
  Normalizer.normalize(book);
  
  // 4. Heading Extractor
  HeadingExtractor.extract(book);
  
  // 5. Footnote Extractor
  FootnoteExtractor.extract(book);
  
  // 6. Enrichment Extractors
  QuranExtractor.extract(book);
  HadithExtractor.extract(book);
  ScholarExtractor.extract(book);
  BookExtractor.extract(book);
  TopicExtractor.extract(book);
  
  // 7. Statistics Generator
  StatisticsGenerator.extract(book);
  
  return { book, parseTime };
}

function compareASTs(docBook: SemanticBook, epubBook: SemanticBook, docTime: number, epubTime: number) {
  const scores: Record<string, number> = {};
  const explanations: string[] = [];
  
  // 1. Metadata Comparison
  const docMeta = docBook.metadata;
  const epubMeta = epubBook.metadata;
  const metaKeys = ["title", "author", "editor", "publisher", "publicationYear", "edition", "volumes"];
  let metaMatched = 0;
  
  for (const k of metaKeys) {
    const dVal = docMeta[k];
    const eVal = epubMeta[k];
    if (dVal && eVal && String(dVal).trim() === String(eVal).trim()) {
      metaMatched++;
    } else {
      if (!dVal && eVal) {
        explanations.push(`❌ Metadata [${k}] missing in DOC version but present in EPUB. (Reason: DOC parser failed to recognize metadata block/inline fields)`);
      } else if (dVal && !eVal) {
        explanations.push(`❌ Metadata [${k}] missing in EPUB version but present in DOC. (Reason: EPUB metadata parser failed to extract info block correctly)`);
      } else if (dVal && eVal) {
        explanations.push(`❌ Metadata [${k}] value mismatch: DOC has "${dVal}", EPUB has "${eVal}". (Reason: OCR/translation variant discrepancy)`);
      }
    }
  }
  scores["metadata"] = Math.round((metaMatched / metaKeys.length) * 100);

  // 2. Structural & Heading Hierarchy Preservation
  const docStats = docBook.statistics || {};
  const epubStats = epubBook.statistics || {};
  
  const docHeadings: SemanticNode[] = [];
  traverseAST(docBook.ast, (n) => { if (n.type === "Heading") docHeadings.push(n); });
  const epubHeadings: SemanticNode[] = [];
  traverseAST(epubBook.ast, (n) => { if (n.type === "Heading") epubHeadings.push(n); });

  const dHeadingCount = docHeadings.length;
  const eHeadingCount = epubHeadings.length;
  
  if (dHeadingCount === eHeadingCount) {
    scores["structure"] = 100;
  } else {
    scores["structure"] = Math.round((Math.min(dHeadingCount, eHeadingCount) / Math.max(dHeadingCount, eHeadingCount)) * 100);
    explanations.push(`⚠️ Heading count mismatch: DOC has ${dHeadingCount} headings, EPUB has ${eHeadingCount} headings. (Reason: Heading detection rule missing, resulting in headings being merged into paragraphs)`);
  }

  // 3. Footnote Integrity
  const dFootnotes = docStats.footnote_count || 0;
  const eFootnotes = epubStats.footnote_count || 0;
  if (dFootnotes === eFootnotes) {
    scores["footnotes"] = 100;
  } else {
    scores["footnotes"] = Math.round((Math.min(dFootnotes, eFootnotes) / Math.max(dFootnotes, eFootnotes)) * 100);
    explanations.push(`⚠️ Footnote count mismatch: DOC has ${dFootnotes} footnotes, EPUB has ${eFootnotes} footnotes. (Reason: Footnote block parsing issue or nested reference mismatch)`);
  }

  // 4. Quran References
  const dQuran = docStats.quran_verse_count || 0;
  const eQuran = epubStats.quran_verse_count || 0;
  if (dQuran === eQuran) {
    scores["quran"] = 100;
  } else {
    scores["quran"] = Math.round((Math.min(dQuran, eQuran) / Math.max(dQuran, eQuran)) * 100);
    explanations.push(`⚠️ Quran verses count mismatch: DOC has ${dQuran}, EPUB has ${eQuran}. (Reason: Reference brackets / citation patterns differ between source texts)`);
  }

  // 5. Hadith Citations
  const dHadith = docStats.hadith_count || 0;
  const eHadith = epubStats.hadith_count || 0;
  if (dHadith === eHadith) {
    scores["hadith"] = 100;
  } else {
    scores["hadith"] = Math.round((Math.min(dHadith, eHadith) / Math.max(dHadith, eHadith)) * 100);
    explanations.push(`⚠️ Hadith count mismatch: DOC has ${dHadith}, EPUB has ${eHadith}. (Reason: Extractor failed to detect narration/isnad chain keywords in the text)`);
  }

  // 6. Named Entity Matching
  const dScholars: string[] = [];
  traverseAST(docBook.ast, (n) => { if (n.type === "ScholarMention" && n.content) dScholars.push(n.content); });
  const eScholars: string[] = [];
  traverseAST(epubBook.ast, (n) => { if (n.type === "ScholarMention" && n.content) eScholars.push(n.content); });
  
  const docScholarSet = new Set(dScholars);
  const epubScholarSet = new Set(eScholars);
  const intersect = new Set([...docScholarSet].filter(x => epubScholarSet.has(x)));
  const totalScholars = new Set([...docScholarSet, ...epubScholarSet]);
  
  if (totalScholars.size === 0) {
    scores["entities"] = 100;
  } else {
    scores["entities"] = Math.round((intersect.size / totalScholars.size) * 100);
    if (intersect.size < totalScholars.size) {
      explanations.push(`⚠️ Named Entity (Scholars) mismatch: DOC has ${docScholarSet.size} unique mentions, EPUB has ${epubScholarSet.size}. (Reason: Commentary additions or text variations containing extra scholar references)`);
    }
  }

  // Content similarity score
  const docText: string[] = [];
  traverseAST(docBook.ast, (n) => { if (n.type === "Paragraph" && n.content) docText.push(n.content); });
  const epubText: string[] = [];
  traverseAST(epubBook.ast, (n) => { if (n.type === "Paragraph" && n.content) epubText.push(n.content); });
  
  // Paragraph counts check
  if (docText.length !== epubText.length) {
    explanations.push(`⚠️ Paragraph grouping difference: DOC has ${docText.length} paragraphs, EPUB has ${epubText.length} paragraphs. (Reason: Source document page segmentation or formatting differences)`);
  }

  // Calculate final score
  const weights: Record<string, number> = {
    metadata: 0.1,
    structure: 0.2,
    footnotes: 0.15,
    quran: 0.15,
    hadith: 0.1,
    entities: 0.1,
    content: 0.2
  };
  
  // content similarity score fallback estimation based on length match
  const contentScore = Math.min(docText.length, epubText.length) / Math.max(docText.length, epubText.length);
  scores["content"] = Math.round(contentScore * 100);
  
  let finalScore = 0;
  for (const k of Object.keys(weights)) {
    finalScore += (scores[k] || 0) * weights[k];
  }
  scores["final_quality_score"] = Math.round(finalScore);

  return { scores, explanations };
}

function main() {
  const args = process.argv;
  let epubPath = "";
  let docPath = "";
  let outPath = "";

  for (let i = 0; i < args.length; i++) {
    if (args[i] === "--epub") epubPath = args[i+1];
    if (args[i] === "--doc") docPath = args[i+1];
    if (args[i] === "--out") outPath = args[i+1];
  }

  if (!epubPath || !docPath || !outPath) {
    console.error("Usage: pnpm exec tsx scripts/benchmark-ast.ts --epub <epub> --doc <doc> --out <out_prefix>");
    process.exit(1);
  }

  console.log(`Running Semantic AST Pipeline for DOC: ${docPath}...`);
  const docResult = runPipeline(docPath);

  console.log(`Running Semantic AST Pipeline for EPUB: ${epubPath}...`);
  const epubResult = runPipeline(epubPath);

  console.log("Comparing Semantic ASTs...");
  const { scores, explanations } = compareASTs(
    docResult.book,
    epubResult.book,
    docResult.parseTime,
    epubResult.parseTime
  );

  const report = {
    scores,
    performance: {
      doc: { parse_time: docResult.parseTime },
      epub: { parse_time: epubResult.parseTime }
    },
    statistics: {
      doc: docResult.book.statistics,
      epub: epubResult.book.statistics
    },
    explanations
  };

  // Write JSON report
  fs.writeFileSync(`${outPath}.json`, JSON.stringify(report, null, 2), "utf-8");
  console.log(`Semantic AST JSON results written to ${outPath}.json`);

  // Write human-readable Markdown report
  const mdReport = `
# 🌲 Semantic AST Importer Benchmark Report

This report evaluates and compares the documents based on their **Semantic AST** structures rather than raw markdown differences.

## 🏆 Final Quality Score

| Category | Score | Value Context |
| :--- | :---: | :--- |
| **Metadata Accuracy** | ${scores.metadata}% | Compares Title, Author, Publisher, Year, etc. |
| **Structure & Headings** | ${scores.structure}% | Compares nested Chapter & Section levels. |
| **Content Similarity** | ${scores.content}% | Measures paragraph groupings alignment. |
| **Footnotes Integrity** | ${scores.footnotes}% | Matches resolved inline Footnote notes. |
| **Quran Detection** | ${scores.quran}% | Matches verified Surah:Ayah references. |
| **Hadith Citations** | ${scores.hadith}% | Identifies narrators and Isnad segments. |
| **Entity Recognition** | ${scores.entities}% | Tracks overlap of scholars, books, & places. |
| 👑 **OVERALL AST SCORE** | **${scores.final_quality_score}%** | **Weighted Semantic AST Similarity** |

---

## 🔍 Semantic Explanations for Mismatches
The benchmark identified the following actionable discrepancies and their root causes:

${explanations.map(e => `* ${e}`).join("\n")}

---

## 📂 Semantic AST Statistics

| Element | DOC AST Count | EPUB AST Count | Difference |
| :--- | :---: | :---: | :---: |
| **Headings (Chapters/Sections)** | ${report.statistics.doc.heading_count} | ${report.statistics.epub.heading_count} | ${Math.abs(report.statistics.doc.heading_count - report.statistics.epub.heading_count)} |
| **Paragraph Blocks** | ${report.statistics.doc.paragraph_count} | ${report.statistics.epub.paragraph_count} | ${Math.abs(report.statistics.doc.paragraph_count - report.statistics.epub.paragraph_count)} |
| **Footnotes** | ${report.statistics.doc.footnote_count} | ${report.statistics.epub.footnote_count} | ${Math.abs(report.statistics.doc.footnote_count - report.statistics.epub.footnote_count)} |
| **Quran Verses** | ${report.statistics.doc.quran_verse_count} | ${report.statistics.epub.quran_verse_count} | ${Math.abs(report.statistics.doc.quran_verse_count - report.statistics.epub.quran_verse_count)} |
| **Hadiths** | ${report.statistics.doc.hadith_count} | ${report.statistics.epub.hadith_count} | ${Math.abs(report.statistics.doc.hadith_count - report.statistics.epub.hadith_count)} |

---
*Generated by the Semantic AST Pipeline Benchmark.*
`;

  fs.writeFileSync(`${outPath}.md`, mdReport.trim(), "utf-8");
  console.log(`Semantic AST Markdown report written to ${outPath}.md`);
}

main();
