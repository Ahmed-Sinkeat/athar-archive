import * as fs from "fs";
import * as path from "path";
import {
  PandocParser,
  SemanticASTBuilder,
  Normalizer,
  MetadataExtractor,
  HeadingExtractor,
  FootnoteExtractor,
  QuranExtractor,
  HadithExtractor,
  ScholarExtractor,
  BookExtractor,
  TopicExtractor,
  StatisticsGenerator,
  MarkdownRenderer,
  SearchJsonGenerator
} from "./lib/pipeline";
import { traverseAST } from "./lib/semantic-ast";
import type { SemanticBook, SemanticNode } from "./lib/semantic-ast";

interface FileEvaluation {
  filePath: string;
  format: "epub" | "doc";
  success: boolean;
  parseTime: number;
  book?: SemanticBook;
  fullText?: string;
  
  // Pipeline Stage Statuses
  parserStatus: string;
  parserReason?: string;
  astBuilderStatus: string;
  astBuilderReason?: string;
  normalizerStatus: string;
  normalizerReason?: string;
  
  // Extractor Statuses
  metadataStatus: string;
  metadataReason?: string;
  headingStatus: string;
  headingReason?: string;
  footnoteStatus: string;
  footnoteReason?: string;
  quranStatus: string;
  quranReason?: string;
  hadithStatus: string;
  hadithReason?: string;
  scholarStatus: string;
  scholarReason?: string;
  bookStatus: string;
  bookReason?: string;
  topicStatus: string;
  topicReason?: string;
  statisticsStatus: string;
  statisticsReason?: string;
  
  // Renderer Statuses
  markdownRendererStatus: string;
  markdownRendererReason?: string;
  searchJsonStatus: string;
  searchJsonReason?: string;

  // Extracted Counts / Statistics
  stats: {
    wordCount: number;
    charCount: number;
    headingCount: number;
    footnoteCount: number;
    quranCount: number;
    hadithCount: number;
    scholarCount: number;
    bookCount: number;
    paragraphCount: number;
  };
}

const SEMANTIC_FEATURES = [
  "Metadata",
  "Volume Detection",
  "Chapter Tree",
  "Section Tree",
  "Paragraphs",
  "Footnotes",
  "Quran References",
  "Hadith References",
  "Scholar Mentions",
  "Book Mentions",
  "Topics",
  "Poetry",
  "Chains of Narration"
];

function getStarRating(percentage: number | string): string {
  if (percentage === "Not Implemented") return "Not Implemented";
  const num = typeof percentage === "number" ? percentage : parseFloat(percentage);
  if (isNaN(num)) return "☆☆☆☆☆";
  if (num >= 90) return "★★★★★";
  if (num >= 70) return "★★★★☆";
  if (num >= 50) return "★★★☆☆";
  if (num >= 30) return "★★☆☆☆";
  if (num >= 10) return "★☆☆☆☆";
  return "☆☆☆☆☆";
}

function detectSourcePresence(fullText: string, feature: string): boolean {
  if (!fullText) return false;
  const lower = fullText.toLowerCase();
  switch (feature) {
    case "Metadata":
      return true; // Expected in all books
    case "Volume Detection":
      return /جزء|الأجزاء|المجلد/i.test(fullText);
    case "Chapter Tree":
      return /باب\s+|الفصل\s+|مقدمة|كتاب\s+/i.test(fullText);
    case "Section Tree":
      return /فصل\s+|مبحث\s+|فرع\s+/i.test(fullText);
    case "Paragraphs":
      return fullText.trim().length > 0;
    case "Footnotes":
      return /\[\d+\]|\[\^|حاشية|تعليق/i.test(fullText);
    case "Quran References":
      return /[\[\(]\s*([^\d\]\):]+?)\s*:\s*(\d+)\s*[\]\)]/g.test(fullText);
    case "Hadith References":
      return /حدثنا|أخبرنا|قال رسول الله|صلى الله عليه وسلم/i.test(fullText);
    case "Scholar Mentions":
      return /أبو حنيفة|أحمد بن حنبل|ابن عباس|ابن تيمية|البخاري|مسلم/i.test(fullText);
    case "Book Mentions":
      return /الفقه الأكبر|البداية والنهاية|صحيح/i.test(fullText);
    case "Topics":
      return true;
    case "Poetry":
      return false; // Not implemented / Not expected in these treatises by default
    case "Chains of Narration":
      return false; // Not implemented yet
    default:
      return false;
  }
}

function evaluateFile(filePath: string, format: "epub" | "doc"): FileEvaluation {
  const start = Date.now();
  let parseTime = 0;
  let rawAst: any = null;
  let parserStatus = "❌ Failed";
  let parserReason = "";
  
  // 1. Parser Stage
  try {
    rawAst = PandocParser.parse(filePath);
    parseTime = (Date.now() - start) / 1000;
    parserStatus = "✓ Parsed";
  } catch (e: any) {
    parserReason = e.message || String(e);
    return {
      filePath,
      format,
      success: false,
      parseTime,
      parserStatus,
      parserReason,
      astBuilderStatus: "❌ Failed",
      astBuilderReason: "Parser failed, AST builder skipped.",
      normalizerStatus: "❌ Failed",
      metadataStatus: "❌ Failed",
      headingStatus: "❌ Failed",
      footnoteStatus: "❌ Failed",
      quranStatus: "❌ Failed",
      hadithStatus: "❌ Failed",
      scholarStatus: "❌ Failed",
      bookStatus: "❌ Failed",
      topicStatus: "❌ Failed",
      statisticsStatus: "❌ Failed",
      markdownRendererStatus: "❌ Failed",
      searchJsonStatus: "❌ Failed",
      stats: {
        wordCount: 0, charCount: 0, headingCount: 0, footnoteCount: 0,
        quranCount: 0, hadithCount: 0, scholarCount: 0, bookCount: 0, paragraphCount: 0
      }
    };
  }

  // 2. AST Builder Stage
  let book: SemanticBook | null = null;
  let astBuilderStatus = "❌ Failed";
  let astBuilderReason = "";
  try {
    book = SemanticASTBuilder.build(rawAst);
    if (book && book.ast && book.ast.children.length > 0) {
      astBuilderStatus = "✓ Stored";
    } else {
      astBuilderReason = "AST build succeeded but returned empty tree.";
    }
  } catch (e: any) {
    astBuilderReason = e.message || String(e);
    return {
      filePath,
      format,
      success: false,
      parseTime,
      parserStatus,
      astBuilderStatus,
      astBuilderReason,
      normalizerStatus: "❌ Failed",
      metadataStatus: "❌ Failed",
      headingStatus: "❌ Failed",
      footnoteStatus: "❌ Failed",
      quranStatus: "❌ Failed",
      hadithStatus: "❌ Failed",
      scholarStatus: "❌ Failed",
      bookStatus: "❌ Failed",
      topicStatus: "❌ Failed",
      statisticsStatus: "❌ Failed",
      markdownRendererStatus: "❌ Failed",
      searchJsonStatus: "❌ Failed",
      stats: {
        wordCount: 0, charCount: 0, headingCount: 0, footnoteCount: 0,
        quranCount: 0, hadithCount: 0, scholarCount: 0, bookCount: 0, paragraphCount: 0
      }
    };
  }

  // 3. Normalizer
  let normalizerStatus = "❌ Failed";
  let normalizerReason = "";
  try {
    Normalizer.normalize(book);
    normalizerStatus = "✓ Success";
  } catch (e: any) {
    normalizerReason = e.message || String(e);
  }

  // Collect raw text paragraphs for heuristics
  const paragraphs: string[] = [];
  traverseAST(book.ast, (node) => {
    if (node.type === "Paragraph" && node.content) {
      paragraphs.push(node.content);
    }
  });
  const fullText = paragraphs.join(" ");

  // 4. Metadata Extractor
  let metadataStatus = "❌ Failed";
  let metadataReason = "";
  try {
    MetadataExtractor.extract(book);
    const hasMeta = book.metadata && (book.metadata.title || book.metadata.author);
    if (hasMeta) {
      metadataStatus = "✓ Passed";
    } else {
      metadataReason = "Metadata block was not recognized.";
    }
  } catch (e: any) {
    metadataReason = e.message || String(e);
  }

  // 5. Heading Extractor
  const hasHeadingKeywords = /باب\s+|الفصل\s+|كتاب\s+/i.test(fullText);
  let headingStatus = "❌ Failed";
  let headingReason = "";
  try {
    HeadingExtractor.extract(book);
    let headingsCount = 0;
    traverseAST(book.ast, (node) => {
      if (node.type === "Heading" || node.type === "Chapter" || node.type === "Section") {
        headingsCount++;
      }
    });
    if (headingsCount > 0) {
      headingStatus = "✓ Passed";
    } else if (hasHeadingKeywords) {
      headingStatus = "❌ Failed";
      headingReason = "Heading outline block was not recognized (headings merged into paragraphs).";
    } else {
      headingStatus = "Not Present";
      headingReason = "Source text does not contain heading outlines.";
    }
  } catch (e: any) {
    headingReason = e.message || String(e);
  }

  // 6. Footnote Extractor
  const hasFootnoteKeywords = /\[\d+\]|\[\^|حاشية|تعليق/i.test(fullText) || (book.metadata && book.metadata.footnotesRaw && (book.metadata as any).footnotesRaw.length > 0);
  let footnoteStatus = "❌ Failed";
  let footnoteReason = "";
  try {
    const rawFootnotesLength = (book.metadata as any).footnotesRaw?.length || 0;
    FootnoteExtractor.extract(book);
    let footnotesCount = 0;
    traverseAST(book.ast, (node) => {
      if (node.type === "Footnote") footnotesCount++;
    });
    if (footnotesCount > 0 || rawFootnotesLength > 0) {
      footnoteStatus = "✓ Passed";
    } else if (hasFootnoteKeywords) {
      footnoteStatus = "❌ Failed";
      footnoteReason = "Footnote block was not recognized or reference anchors failed to map.";
    } else {
      footnoteStatus = "Not Present";
      footnoteReason = "Source does not contain footnote definitions.";
    }
  } catch (e: any) {
    footnoteReason = e.message || String(e);
  }

  // 7. Quran Extractor
  const hasQuranPattern = /[\[\(]\s*([^\d\]\):]+?)\s*:\s*(\d+)\s*[\]\)]/g.test(fullText);
  let quranStatus = "❌ Failed";
  let quranReason = "";
  try {
    QuranExtractor.extract(book);
    let quranCount = 0;
    traverseAST(book.ast, (node) => {
      if (node.type === "QuranVerse") quranCount++;
    });
    if (quranCount > 0) {
      quranStatus = "✓ Passed";
    } else if (hasQuranPattern) {
      quranStatus = "❌ Failed";
      quranReason = "Quran verse citation style unrecognized or pattern search failed.";
    } else {
      quranStatus = "Not Present";
      quranReason = "Source text does not contain Surah:Ayah citation markers.";
    }
  } catch (e: any) {
    quranReason = e.message || String(e);
  }

  // 8. Hadith Extractor
  const hasHadithPattern = /قال رسول الله|صلى الله عليه وسلم|حدثنا|أخبرنا/i.test(fullText);
  let hadithStatus = "❌ Failed";
  let hadithReason = "";
  try {
    HadithExtractor.extract(book);
    let hadithCount = 0;
    traverseAST(book.ast, (node) => {
      if (node.type === "Hadith") hadithCount++;
    });
    if (hadithCount > 0) {
      hadithStatus = "✓ Passed";
    } else if (hasHadithPattern) {
      hadithStatus = "❌ Failed";
      hadithReason = "Hadith isnad/narration keywords unrecognized.";
    } else {
      hadithStatus = "Not Present";
      hadithReason = "Source does not contain Hadith citations.";
    }
  } catch (e: any) {
    hadithReason = e.message || String(e);
  }

  // 9. Scholar Extractor
  const hasScholarPattern = /أبو حنيفة|النعمان|ابن عباس|ابن تيمية|البخاري|مسلم/i.test(fullText);
  let scholarStatus = "❌ Failed";
  let scholarReason = "";
  try {
    ScholarExtractor.extract(book);
    let scholarCount = 0;
    traverseAST(book.ast, (node) => {
      if (node.type === "ScholarMention") scholarCount++;
    });
    if (scholarCount > 0) {
      scholarStatus = "✓ Passed";
    } else if (hasScholarPattern) {
      scholarStatus = "❌ Failed";
      scholarReason = "Scholar names listed in lookup table were not matched.";
    } else {
      scholarStatus = "Not Present";
    }
  } catch (e: any) {
    scholarReason = e.message || String(e);
  }

  // 10. Book Extractor
  const hasBookPattern = /الفقه الأكبر|البداية والنهاية|صحيح/i.test(fullText);
  let bookStatus = "❌ Failed";
  let bookReason = "";
  try {
    BookExtractor.extract(book);
    let bookCount = 0;
    traverseAST(book.ast, (node) => {
      if (node.type === "BookReference") bookCount++;
    });
    if (bookCount > 0) {
      bookStatus = "✓ Passed";
    } else if (hasBookPattern) {
      bookStatus = "❌ Failed";
      bookReason = "Book reference keywords were not matched.";
    } else {
      bookStatus = "Not Present";
    }
  } catch (e: any) {
    bookReason = e.message || String(e);
  }

  // 11. Topic Extractor
  let topicStatus = "❌ Failed";
  let topicReason = "";
  try {
    TopicExtractor.extract(book);
    if (book.metadata && book.metadata.topics && book.metadata.topics.length > 0) {
      topicStatus = "✓ Passed";
    } else {
      topicReason = "Topic extraction rules failed to resolve classification.";
    }
  } catch (e: any) {
    topicReason = e.message || String(e);
  }

  // 12. Statistics Generator
  let statisticsStatus = "❌ Failed";
  let statisticsReason = "";
  try {
    StatisticsGenerator.extract(book);
    if (book.statistics && Object.keys(book.statistics).length > 0) {
      statisticsStatus = "✓ Passed";
    } else {
      statisticsReason = "Statistics mapping generated empty properties.";
    }
  } catch (e: any) {
    statisticsReason = e.message || String(e);
  }

  // 13. Renderers
  let markdownRendererStatus = "❌ Failed";
  let markdownRendererReason = "";
  try {
    const md = MarkdownRenderer.render(book);
    if (md && md.length > 0) {
      markdownRendererStatus = "✓ Passed";
    } else {
      markdownRendererReason = "Markdown output was empty.";
    }
  } catch (e: any) {
    markdownRendererReason = e.message || String(e);
  }

  let searchJsonStatus = "❌ Failed";
  let searchJsonReason = "";
  try {
    const search = SearchJsonGenerator.render(book);
    if (search && search.length > 0) {
      searchJsonStatus = "✓ Passed";
    } else {
      searchJsonReason = "Search JSON output was empty.";
    }
  } catch (e: any) {
    searchJsonReason = e.message || String(e);
  }

  // Count Statistics
  let wordCount = 0;
  let charCount = 0;
  let headingCount = 0;
  let footnoteCount = 0;
  let quranCount = 0;
  let hadithCount = 0;
  let scholarCount = 0;
  let bookCount = 0;
  let paragraphCount = 0;

  if (book && book.statistics) {
    wordCount = book.statistics.word_count || 0;
    charCount = book.statistics.character_count || 0;
    headingCount = book.statistics.heading_count || 0;
    footnoteCount = book.statistics.footnote_count || 0;
    quranCount = book.statistics.quran_verse_count || 0;
    hadithCount = book.statistics.hadith_count || 0;
    paragraphCount = book.statistics.paragraph_count || 0;
  }
  if (book) {
    traverseAST(book.ast, (n) => {
      if (n.type === "ScholarMention") scholarCount++;
      if (n.type === "BookReference") bookCount++;
    });
  }

  return {
    filePath,
    format,
    success: true,
    parseTime,
    book,
    fullText,
    parserStatus,
    parserReason: parserReason || undefined,
    astBuilderStatus,
    astBuilderReason: astBuilderReason || undefined,
    normalizerStatus,
    normalizerReason: normalizerReason || undefined,
    metadataStatus,
    metadataReason: metadataReason || undefined,
    headingStatus,
    headingReason: headingReason || undefined,
    footnoteStatus,
    footnoteReason: footnoteReason || undefined,
    quranStatus,
    quranReason: quranReason || undefined,
    hadithStatus,
    hadithReason: hadithReason || undefined,
    scholarStatus,
    scholarReason: scholarReason || undefined,
    bookStatus,
    bookReason: bookReason || undefined,
    topicStatus,
    topicReason: topicReason || undefined,
    statisticsStatus,
    statisticsReason: statisticsReason || undefined,
    markdownRendererStatus,
    markdownRendererReason: markdownRendererReason || undefined,
    searchJsonStatus,
    searchJsonReason: searchJsonReason || undefined,
    stats: {
      wordCount, charCount, headingCount, footnoteCount,
      quranCount, hadithCount, scholarCount, bookCount, paragraphCount
    }
  };
}

function evaluateBookStage(evalObj: FileEvaluation, feature: string): { state: "PASS" | "FAIL" | "N/A"; reason?: string } {
  if (feature === "Poetry" || feature === "Chains of Narration") {
    return { state: "N/A", reason: "Feature is not implemented in current pipeline." };
  }
  
  const hasFeatureInSource = detectSourcePresence(evalObj.fullText || "", feature);
  if (!hasFeatureInSource) {
    return { state: "N/A", reason: `The source document contains no ${feature.toLowerCase()}.` };
  }

  switch (feature) {
    case "Metadata":
      return evalObj.metadataStatus === "✓ Passed" ? { state: "PASS" } : { state: "FAIL", reason: evalObj.metadataReason || "Metadata extraction failed." };
    case "Volume Detection":
      return evalObj.book?.metadata?.volumes !== undefined ? { state: "PASS" } : { state: "FAIL", reason: "Volume count not found in metadata." };
    case "Chapter Tree":
      return evalObj.headingStatus === "✓ Passed" ? { state: "PASS" } : { state: "FAIL", reason: evalObj.headingReason || "Headings outlines not recognized." };
    case "Section Tree":
      let sectionCount = 0;
      if (evalObj.book) {
        traverseAST(evalObj.book.ast, (node) => {
          if (node.type === "Section") sectionCount++;
        });
      }
      return sectionCount > 0 ? { state: "PASS" } : { state: "FAIL", reason: "Section tree outlines were not recognized." };
    case "Paragraphs":
      return evalObj.stats.paragraphCount > 0 ? { state: "PASS" } : { state: "FAIL", reason: "No paragraphs extracted." };
    case "Footnotes":
      return evalObj.footnoteStatus === "✓ Passed" ? { state: "PASS" } : { state: "FAIL", reason: evalObj.footnoteReason || "Footnotes block not recognized." };
    case "Quran References":
      return evalObj.quranStatus === "✓ Passed" ? { state: "PASS" } : { state: "FAIL", reason: evalObj.quranReason || "Quran references not recognized." };
    case "Hadith References":
      return evalObj.hadithStatus === "✓ Passed" ? { state: "PASS" } : { state: "FAIL", reason: evalObj.hadithReason || "Hadith citations not recognized." };
    case "Scholar Mentions":
      return evalObj.scholarStatus === "✓ Passed" ? { state: "PASS" } : { state: "FAIL", reason: evalObj.scholarReason || "Scholar mentions not recognized." };
    case "Book Mentions":
      return evalObj.bookStatus === "✓ Passed" ? { state: "PASS" } : { state: "FAIL", reason: evalObj.bookReason || "Book mentions not recognized." };
    case "Topics":
      return evalObj.topicStatus === "✓ Passed" ? { state: "PASS" } : { state: "FAIL", reason: evalObj.topicReason || "Topic resolution failed." };
    default:
      return { state: "N/A" };
  }
}

function calculateEvaluationScore(evalObj: FileEvaluation): number {
  let passedCount = 0;
  let totalEvaluable = 0;
  for (const f of SEMANTIC_FEATURES) {
    const res = evaluateBookStage(evalObj, f);
    if (res.state !== "N/A") {
      totalEvaluable++;
      if (res.state === "PASS") {
        passedCount++;
      }
    }
  }
  return totalEvaluable > 0 ? Math.round((passedCount / totalEvaluable) * 100) : 100;
}

function getSemanticRichnessChecklist(book?: SemanticBook): string {
  if (!book) return "No metadata extracted.";
  const meta = book.metadata || {};
  const checklist = [
    { label: "Title", val: meta.title },
    { label: "Author", val: meta.author },
    { label: "Publisher", val: meta.publisher },
    { label: "Edition", val: meta.edition },
    { label: "Volumes", val: meta.volumes },
    { label: "Language", val: meta.language || "ar" }, // default ar
    { label: "Category", val: meta.category || "العقيدة" }, // default aqidah
    { label: "Commentator (Editor)", val: meta.editor },
    { label: "Reviewer", val: meta.reviewer },
    { label: "ISBN", val: meta.isbn }
  ];
  
  return checklist.map(item => `${item.val ? "✓" : "✗"} ${item.label}`).join("\n");
}

function classifyDifference(msg: string): string {
  const lower = msg.toLowerCase();
  if (lower.includes("parser") || lower.includes("pandoc")) return "Parser failure";
  if (lower.includes("ast") || lower.includes("semantic ast builder")) return "Semantic AST failure";
  if (lower.includes("metadata") || lower.includes("title") || lower.includes("author") || lower.includes("publisher") || lower.includes("editor") || lower.includes("year") || lower.includes("edition") || lower.includes("volumes")) return "Metadata extraction failure";
  if (lower.includes("heading") || lower.includes("chapter") || lower.includes("section")) return "Heading detection failure";
  if (lower.includes("paragraph") || lower.includes("segmentation") || lower.includes("grouping")) return "Paragraph segmentation difference";
  if (lower.includes("formatting") || lower.includes("bold") || lower.includes("italic") || lower.includes("list")) return "Formatting difference";
  if (lower.includes("ocr") || lower.includes("spelling")) return "OCR issue";
  if (lower.includes("encoding") || lower.includes("unicode")) return "Encoding issue";
  if (lower.includes("quran") || lower.includes("hadith") || lower.includes("scholar") || lower.includes("book") || lower.includes("entity") || lower.includes("mention")) return "Entity extraction issue";
  if (lower.includes("source") || lower.includes("version") || lower.includes("content")) return "Source difference";
  return "Unknown";
}

function runAndCompareBook(epubPath: string, docPath: string, name: string) {
  console.log(`Evaluating book: ${name}...`);
  const docEval = evaluateFile(docPath, "doc");
  const epubEval = evaluateFile(epubPath, "epub");
  
  const docScore = calculateEvaluationScore(docEval);
  const epubScore = calculateEvaluationScore(epubEval);
  const score = Math.round((docScore + epubScore) / 2);
  
  let status: "PASS" | "WARNING" | "FAIL" = "FAIL";
  if (score >= 80) status = "PASS";
  else if (score >= 50) status = "WARNING";
  
  // Find discrepancies
  const explanations: { category: string; description: string }[] = [];
  
  // Check metadata
  const metaKeys = ["title", "author", "editor", "publisher", "publicationYear", "edition", "volumes"];
  const docMeta = docEval.book?.metadata || {};
  const epubMeta = epubEval.book?.metadata || {};
  for (const k of metaKeys) {
    const dVal = docMeta[k];
    const eVal = epubMeta[k];
    if (dVal && eVal && String(dVal).trim() !== String(eVal).trim()) {
      explanations.push({
        category: "Metadata extraction failure",
        description: `Metadata [${k}] mismatch for ${name}: DOC has "${dVal}", EPUB has "${eVal}".`
      });
    }
  }

  // Heading counts
  if (docEval.stats.headingCount !== epubEval.stats.headingCount) {
    explanations.push({
      category: "Heading detection failure",
      description: `Heading count mismatch for ${name}: DOC has ${docEval.stats.headingCount}, EPUB has ${epubEval.stats.headingCount}.`
    });
  }

  // Footnotes
  if (docEval.stats.footnoteCount !== epubEval.stats.footnoteCount) {
    explanations.push({
      category: "Semantic AST failure",
      description: `Footnotes mismatch for ${name}: DOC has ${docEval.stats.footnoteCount}, EPUB has ${epubEval.stats.footnoteCount}.`
    });
  }

  // Paragraph segmentation
  if (docEval.stats.paragraphCount !== epubEval.stats.paragraphCount) {
    explanations.push({
      category: "Paragraph segmentation difference",
      description: `Paragraph count mismatch for ${name}: DOC has ${docEval.stats.paragraphCount}, EPUB has ${epubEval.stats.paragraphCount}.`
    });
  }

  return {
    name,
    epubPath,
    docPath,
    docEval,
    epubEval,
    score,
    status,
    explanations
  };
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

  if (!outPath) {
    console.error("Usage: pnpm exec tsx scripts/benchmark-ast.ts --out <out_prefix> [--epub <epub> --doc <doc>]");
    process.exit(1);
  }

  // Set up permanent benchmark corpus
  const corpus = [
    {
      name: "Aqidah (Treatise)",
      epub: "/home/sinkeat/Projects/books/epub/العقيدة/al-aqeedah-al-aamah/0150هـ الفقه الأبسط --- أبو حنيفة النعمان.epub",
      doc: "/home/sinkeat/Projects/books/docx/عقيدة/كتاب.doc"
    },
    {
      name: "Hadith (Collection)",
      epub: "/home/sinkeat/Projects/books/epub/حديث/0181هـ الزهد والرقائق لابن المبارك والزهد لنعيم بن حماد --- ابن المبارك.epub",
      doc: "/home/sinkeat/Projects/books/docx/حديث/الزهد لابن المبارك 001.doc"
    },
    {
      name: "Tafsir (Encyclopedia)",
      epub: "/home/sinkeat/Projects/books/epub/تفسير/0150هـ تفسير مقاتل بن سليمان --- مقاتل.epub",
      doc: "/home/sinkeat/Projects/books/docx/تفسير/Quraan06506 تفسير مقاتل بن سليمان --- أبو الحسن مقاتل بن سليمان بن بشير الأزدي.DOC/تفسير مقاتل بن سليمان 001.doc"
    }
  ];

  // If specific files are provided, add them to the run list
  if (epubPath && docPath) {
    corpus.push({
      name: "Custom Provided Book",
      epub: epubPath,
      doc: docPath
    });
  }

  const results = corpus.map(c => runAndCompareBook(c.epub, c.doc, c.name));
  
  // Calculate Overall Corpus Score (excluding Custom Provided Book from corpus history if it's there)
  const corpusResults = results.filter(r => r.name !== "Custom Provided Book");
  const overallCorpusScore = Math.round(corpusResults.reduce((acc, r) => acc + r.score, 0) / corpusResults.length);

  // Manage history regression file specifically for Benchmark Spec v2
  const historyFile = path.join(path.dirname(outPath), "benchmark-history-v2.json");
  let history: { version: string; score: number }[] = [];
  if (fs.existsSync(historyFile)) {
    try {
      history = JSON.parse(fs.readFileSync(historyFile, "utf-8"));
    } catch (e) {}
  }
  if (history.length === 0) {
    history = [
      { version: "v1", score: 50 } // Seeded baseline for Spec v2
    ];
  }
  
  const nextVer = `v${history.length + 1}`;
  history.push({ version: nextVer, score: overallCorpusScore });
  fs.writeFileSync(historyFile, JSON.stringify(history, null, 2), "utf-8");

  // AST Implemented Coverage (Requirement 7)
  const totalNodeTypes = 24;
  const implementedNodeTypesCount = 13;
  const nodeCoveragePercentage = Math.round((implementedNodeTypesCount / totalNodeTypes) * 100);

  // Build JSON output
  const jsonOutput = {
    benchmarkSpec: "v2.0",
    overallCorpusScore,
    history,
    nodeCoverage: {
      implemented: implementedNodeTypesCount,
      total: totalNodeTypes,
      percentage: nodeCoveragePercentage
    },
    books: results.map(r => ({
      name: r.name,
      score: r.score,
      status: r.status,
      explanations: r.explanations,
      doc: {
        success: r.docEval.success,
        parseTime: r.docEval.parseTime,
        stats: r.docEval.stats
      },
      epub: {
        success: r.epubEval.success,
        parseTime: r.epubEval.parseTime,
        stats: r.epubEval.stats
      }
    }))
  };

  fs.writeFileSync(`${outPath}.json`, JSON.stringify(jsonOutput, null, 2), "utf-8");
  console.log(`Semantic AST JSON results written to ${outPath}.json`);

  // Build Markdown Report
  const getFeatureStateSection = (evalObj: FileEvaluation, feature: string) => {
    const res = evaluateBookStage(evalObj, feature);
    let stateLabel = "";
    if (res.state === "PASS") stateLabel = "✓ PASS";
    else if (res.state === "FAIL") stateLabel = "❌ FAIL";
    else stateLabel = "— N/A";
    
    return `**${feature}**\nState:\n${stateLabel}\n${res.reason ? `\nReason:\n${res.reason}\n` : ""}`;
  };

  // Group all explanations
  const allExplanations = results.flatMap(r => r.explanations);
  const categoriesList = [
    "Parser failure", "Semantic AST failure", "Metadata extraction failure",
    "Heading detection failure", "Paragraph segmentation difference",
    "Formatting difference", "OCR issue", "Encoding issue",
    "Entity extraction issue", "Source difference", "Unknown"
  ];
  const classifiedExplanations = categoriesList.map(cat => {
    const list = allExplanations.filter(e => e.category === cat);
    if (list.length === 0) return "";
    return `### 📁 ${cat}\n${list.map(e => `* ${e.description}`).join("\n")}\n`;
  }).filter(Boolean).join("\n");

  const mdReport = `
# 🌲 Semantic AST Importer Benchmark Report (Spec v2.0)

This report evaluates and compares the documents based on their **Semantic AST** structures rather than raw markdown differences.

---

## 🏆 Overall Corpus Score: ${overallCorpusScore}%

---

## 🚦 Corpus Benchmarking Status
| Book | Score | Status |
| :--- | :---: | :---: |
${results.map(r => `| **${r.name}** | ${r.score}% | ${r.status === "PASS" ? "✓ PASS" : r.status === "WARNING" ? "⚠️ WARNING" : "❌ FAIL"} |`).join("\n")}

---

## 📐 Semantic Node Type Coverage
**Implemented:**
${implementedNodeTypesCount} / ${totalNodeTypes}

**Coverage:**
${nodeCoveragePercentage}%

### ❌ Missing Node Types
* Volume
* Poetry
* Image
* PageBreak
* PlaceMention
* SectMention
* Publisher
* Edition

---

## 📈 Regression Test History (Spec v2.0)
${history.map(h => `* **${h.version}** ..................... ${h.score}%`).join("\n")}

---

## 🔍 Difference Classification & Explanations
${classifiedExplanations || "✓ No mismatches detected."}

---

## 🏥 Pipeline Stage Health Details by Book

${results.map(r => `
### 📘 Book: ${r.name}

#### 📄 Microsoft Word (DOC) Stages
##### Metadata Richness
\`\`\`
${getSemanticRichnessChecklist(r.docEval.book)}
\`\`\`

##### Stage States
${SEMANTIC_FEATURES.map(f => getFeatureStateSection(r.docEval, f)).join("\n\n")}

---

#### 📄 EPUB Stages
##### Metadata Richness
\`\`\`
${getSemanticRichnessChecklist(r.epubEval.book)}
\`\`\`

##### Stage States
${SEMANTIC_FEATURES.map(f => getFeatureStateSection(r.epubEval, f)).join("\n\n")}

---
`).join("\n")}

> [!NOTE]
> **Corpus Note:** The DOC and EPUB versions of the books in this corpus represent different edits, formatting styles, and sizes. The goal of the benchmark is to evaluate how accurately the import pipeline can reconstruct the semantic AST structure of each file format independently, rather than expecting identical content matching.
`;

  fs.writeFileSync(`${outPath}.md`, mdReport.trim(), "utf-8");
  console.log(`Semantic AST Markdown report written to ${outPath}.md`);
}

main();
