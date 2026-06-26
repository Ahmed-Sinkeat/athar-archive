import { execSync } from "child_process";
import * as fs from "fs";
import * as path from "path";
import * as os from "os";
import { createNode, traverseAST } from "./semantic-ast";
import type { SemanticBook, SemanticNode, NodeType } from "./semantic-ast";

// Arabic Surah Names
const SURAH_NAMES = [
  "الفاتحة", "البقرة", "آل عمران", "النساء", "المائدة", "الأنعام", "الأعراف", "الأنفال",
  "التوبة", "يونس", "هود", "يوسف", "الرعد", "إبراهيم", "الحجر", "النحل", "الإسراء",
  "الكهف", "مريم", "طه", "الأنبياء", "الحج", "المؤمنون", "النور", "الفرقان", "الشعراء",
  "النمل", "القصص", "العنكبوت", "الروم", "لقمان", "السجدة", "الأحزاب", "سبأ", "فاطر",
  "يس", "الصافات", "ص", "الزمر", "غافر", "فصلت", "الشورى", "الزخرف", "الدخان",
  "الجاثية", "الأحقاف", "محمد", "الفتح", "الحجرات", "ق", "الذاريات", "الطور", "النجم",
  "القمر", "الرحمن", "الواقعة", "الحديد", "المجادلة", "الحشر", "الممتحنة", "الصف",
  "الجمعة", "المنافقون", "التغابن", "الطلاق", "التحريم", "الملك", "القلم", "الحاقة",
  "المعارج", "نوح", "الجن", "المزمل", "المدثر", "القيامة", "الإنسان", "المرسلات",
  "النبأ", "النازعات", "عبس", "التكوير", "الانفطار", "المطففين", "الانشقاق", "البروج",
  "الطارق", "الأعلى", "الغاشية", "الفجر", "البلد", "الشمس", "الليل", "الضحى",
  "الشرح", "التين", "العلق", "القدر", "البينة", "الزلزلة", "العاديات", "القارعة",
  "التكاثر", "العصر", "الهمزة", "الفيل", "قريش", "الماعون", "الكوثر", "الكافرون",
  "النصر", "المسد", "الإخلاص", "الفلق", "الناس"
];

// Named Entities Lists
const SCHOLAR_ENTITIES = [
  "أبو حنيفة", "النعمان بن ثابت", "محمد بن عبد الرحمن الخميس", "ابن عباس", "عائشة",
  "سيد قطب", "ابن تيمية", "الحسن البصري", "ابن كثير", "خالد بن الوليد", "وهب بن منبه",
  "النووي", "الباقلاني", "الغزالي", "ابن حزم", "أحمد بن حنبل", "أبي بكر الخلال", "البربهاري"
];

const BOOK_ENTITIES = [
  "الفقه الأبسط", "الفقه الأكبر", "البداية والنهاية", "صحيح مسلم", "صحيح البخاري",
  "آكام المرجان", "لقط المرجان", "لوامع الأنوار", "التدمرية", "الحموية", "الواسطية"
];

const PLACE_ENTITIES = [
  "الإمارات", "بغداد", "الكوفة", "البصرة", "مكة", "المدينة", "الشام", "مصر", "لاهور"
];

const SECT_ENTITIES = [
  "الجهمية", "المعتزلة", "الرافضة", "الروافض", "الشيعة", "النصارى", "اليهود", "أهل السنة",
  "الباطنية", "الأشاعرة", "المرجئة", "القدرية", "الخوارج"
];

export function cleanArabicText(text: string): string {
  if (!text) return "";
  // Remove tashkeel (diacritics)
  text = text.replace(/[\u064B-\u0652\u0653\u0670]/g, "");
  // Normalize Alef forms
  text = text.replace(/[إأآ]/g, "ا");
  // Normalize Ta Marbuta and Ha
  text = text.replace(/ة\b/g, "ه");
  // Normalize Ya and Alef Maksura
  text = text.replace(/ى\b/g, "ي");
  // Remove punctuation & brackets
  text = text.replace(/[^\w\s]/g, " ");
  // Standardize spaces
  return text.replace(/\s+/g, " ").trim();
}

/** 1. Stage: Parser */
export class PandocParser {
  static parse(filePath: string): any {
    const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "pandoc-pipeline-"));
    let targetFile = filePath;

    try {
      // Convert legacy .doc to modern .docx using headless LibreOffice if needed
      if (filePath.endsWith(".doc")) {
        execSync(
          `libreoffice --headless --convert-to docx --outdir "${tempDir}" "${filePath}"`,
          { stdio: "ignore" }
        );
        const baseName = path.parse(filePath).name;
        targetFile = path.join(tempDir, `${baseName}.docx`);
      }

      // Convert to Pandoc JSON AST
      const astJsonPath = path.join(tempDir, "ast.json");
      const format = filePath.endsWith(".epub") ? "epub" : "docx";
      execSync(`pandoc -f ${format} -t json -o "${astJsonPath}" "${targetFile}"`);
      const astRaw = fs.readFileSync(astJsonPath, "utf-8");
      return JSON.parse(astRaw);
    } finally {
      // Cleanup temp dir
      try {
        fs.rmSync(tempDir, { recursive: true, force: true });
      } catch (e) {}
    }
  }
}

/** 2. Stage: Normalizer */
export class Normalizer {
  static normalize(book: SemanticBook): void {
    traverseAST(book.ast, (node) => {
      if (node.content) {
        node.content = node.content.replace(/\s+/g, " ").trim();
      }
    });
  }
}

/** 3. Stage: Semantic AST Builder */
export class SemanticASTBuilder {
  static build(pandocAst: any, source: "doc" | "epub" = "doc", filePath?: string): SemanticBook {
    const metadata: Record<string, any> = {};
    const flatNodes: SemanticNode[] = [];
    const footnotes: { index: number; text: string }[] = [];

    // Parse Pandoc Metadata Block
    const metaBlock = pandocAst.meta || {};
    for (const key of Object.keys(metaBlock)) {
      const val = metaBlock[key];
      if (val && typeof val === "object") {
        const metaType = val.t;
        if (metaType === "MetaInlines") {
          metadata[key] = SemanticASTBuilder.inlineToString(val.c, footnotes);
        } else if (metaType === "MetaString") {
          metadata[key] = val.c;
        }
      }
    }

    let paragraphCounter = 0;
    let accumulatedOffset = 0;

    const getOrigin = (textLength: number): any => {
      paragraphCounter++;
      const currentStart = accumulatedOffset;
      accumulatedOffset += textLength + 1; // simulation of separation
      if (source === "doc") {
        return {
          source: "doc",
          page: 1, // simulated page number
          paragraph: paragraphCounter,
          offsetStart: currentStart,
          offsetEnd: currentStart + textLength
        };
      } else {
        return {
          source: "epub",
          file: filePath ? path.basename(filePath) : "document.epub",
          xpath: `/html/body/div/p[${paragraphCounter}]`,
          offset: currentStart
        };
      }
    };

    // Process blocks list
    const blocks = pandocAst.blocks || [];
    const walkBlocks = (nodeList: any[]) => {
      if (!Array.isArray(nodeList)) return;
      for (const block of nodeList) {
        if (!block || typeof block !== "object") continue;
        const type = block.t;
        const content = block.c;

        if (type === "Header") {
          const level = content[0];
          const text = SemanticASTBuilder.inlineToString(content[2], footnotes);
          flatNodes.push(createNode("Heading", text, { level }, [], 1.0, getOrigin(text.length)));
        } else if (type === "Para" || type === "Plain") {
          const text = SemanticASTBuilder.inlineToString(content, footnotes);
          if (text) {
            flatNodes.push(createNode("Paragraph", text, {}, [], 1.0, getOrigin(text.length)));
          }
        } else if (type === "BlockQuote") {
          const qParser = new PandocASTParser(content, footnotes);
          flatNodes.push(createNode("Quote", undefined, {}, qParser.nodes, 1.0, getOrigin(0)));
        } else if (type === "BulletList" || type === "OrderedList") {
          const listItems = type === "OrderedList" ? content[1] : content;
          const itemsNodes: SemanticNode[] = [];
          for (const item of listItems) {
            const itemParser = new PandocASTParser(item, footnotes);
            itemsNodes.push(...itemParser.nodes);
          }
          flatNodes.push(createNode("List", undefined, { list_type: type }, itemsNodes, 1.0, getOrigin(0)));
        } else if (type === "Table") {
          flatNodes.push(createNode("Table", "[Table Content]", {}, [], 1.0, getOrigin(15)));
        } else {
          // Walk nested dictionaries recursively
          SemanticASTBuilder.walkRecursive(content, flatNodes, footnotes, getOrigin);
        }
      }
    };

    walkBlocks(blocks);

    const root = createNode("Book", undefined, {}, flatNodes);
    
    // Attach footnotes list as metadata attributes initially
    metadata.footnotesRaw = footnotes;

    return {
      metadata,
      ast: root
    };
  }

  static walkRecursive(node: any, flatNodes: SemanticNode[], footnotes: any[], getOrigin: (len: number) => any) {
    if (Array.isArray(node)) {
      for (const item of node) {
        SemanticASTBuilder.walkRecursive(item, flatNodes, footnotes, getOrigin);
      }
    } else if (node && typeof node === "object") {
      if (node.t === "Para" || node.t === "Plain") {
        const text = SemanticASTBuilder.inlineToString(node.c, footnotes);
        if (text) {
          flatNodes.push(createNode("Paragraph", text, {}, [], 1.0, getOrigin(text.length)));
        }
      } else if (node.c) {
        SemanticASTBuilder.walkRecursive(node.c, flatNodes, footnotes, getOrigin);
      } else {
        for (const k of Object.keys(node)) {
          SemanticASTBuilder.walkRecursive(node[k], flatNodes, footnotes, getOrigin);
        }
      }
    }
  }

  static inlineToString(inlines: any[], footnotes: any[]): string {
    if (!Array.isArray(inlines)) return "";
    const textList: string[] = [];

    for (const node of inlines) {
      if (!node || typeof node !== "object") continue;
      const type = node.t;
      const content = node.c;

      if (type === "Str") {
        textList.append ? (textList as any).append(content) : textList.push(content);
      } else if (type === "Space" || type === "SoftBreak") {
        textList.push(" ");
      } else if (type === "LineBreak") {
        textList.push("\n");
      } else if (type === "Strong" || type === "Emph") {
        textList.push(SemanticASTBuilder.inlineToString(content, footnotes));
      } else if (type === "Note") {
        // Nested block elements representing footnote text
        const noteParser = new PandocASTParser(content, footnotes);
        const noteText = noteParser.nodes.map(n => n.content || "").join(" ").trim();
        footnotes.push({
          index: footnotes.length + 1,
          text: noteText
        });
        textList.push(`[${footnotes.length}]`);
      } else if (type === "Span" || type === "Div") {
        textList.push(SemanticASTBuilder.inlineToString(content[1], footnotes));
      } else if (Array.isArray(content)) {
        textList.push(SemanticASTBuilder.inlineToString(content, footnotes));
      }
    }
    return textList.join("");
  }
}

class PandocASTParser {
  nodes: SemanticNode[] = [];
  constructor(blocks: any[], footnotes: any[]) {
    const walk = (nodeList: any[]) => {
      if (!Array.isArray(nodeList)) return;
      for (const block of nodeList) {
        if (!block || typeof block !== "object") continue;
        if (block.t === "Para" || block.t === "Plain") {
          const text = SemanticASTBuilder.inlineToString(block.c, footnotes);
          if (text) this.nodes.push(createNode("Paragraph", text));
        } else if (block.c) {
          walk(block.c);
        }
      }
    };
    walk(blocks);
  }
}

/** 4. Stage: Metadata Extractor */
export class MetadataExtractor {
  static extract(book: SemanticBook): void {
    // Attempt to extract editor, publisher, publicationYear, edition, and volumes from the text body
    const meta = book.metadata;
    book.metadata.confidence = book.metadata.confidence || {};

    const cleanFind = (pattern: RegExp, paragraphs: string[]): string | undefined => {
      for (const p of paragraphs.slice(0, 15)) {
        const m = p.match(pattern);
        if (m && m[1]) return m[1].trim();
      }
      return undefined;
    };

    const paragraphs: string[] = [];
    traverseAST(book.ast, (node) => {
      if (node.type === "Paragraph" && node.content) {
        paragraphs.push(node.content);
      }
    });

    if (meta.title) {
      book.metadata.confidence.title = 1.0;
    } else {
      const foundTitle = cleanFind(/الكتاب\s*:\s*([^\n]+)/i, paragraphs);
      if (foundTitle) {
        meta.title = foundTitle;
        book.metadata.confidence.title = 0.85;
      }
    }

    if (meta.author) {
      book.metadata.confidence.author = 1.0;
    } else {
      const foundAuthor = cleanFind(/المؤلف\s*:\s*([^\n]+)/i, paragraphs);
      if (foundAuthor) {
        meta.author = foundAuthor;
        book.metadata.confidence.author = 0.85;
      }
    }

    const editor = cleanFind(/تحقيق\s*:\s*([^\n]+)|المحقق\s*:\s*([^\n]+)/i, paragraphs);
    if (editor) {
      meta.editor = editor;
      book.metadata.confidence.editor = 0.90;
    }

    const publisher = cleanFind(/الناشر\s*:\s*([^\n]+)/i, paragraphs);
    if (publisher) {
      meta.publisher = publisher;
      book.metadata.confidence.publisher = 0.90;
    }

    const publicationYear = cleanFind(/سنة النشر\s*:\s*([^\n]+)|سنة الطبع\s*:\s*([^\n]+)/i, paragraphs);
    if (publicationYear) {
      meta.publicationYear = publicationYear;
      book.metadata.confidence.publicationYear = 0.85;
    }

    const edition = cleanFind(/الطبعة\s*:\s*([^\n]+)/i, paragraphs);
    if (edition) {
      meta.edition = edition;
      book.metadata.confidence.edition = 0.90;
    }
    
    const vols = cleanFind(/عدد الأجزاء\s*:\s*(\d+)/i, paragraphs);
    if (vols) {
      meta.volumes = parseInt(vols);
      book.metadata.confidence.volumes = 0.95;
    }
  }
}

/** 5. Stage: Heading Extractor (Nesting builder) */
export class HeadingExtractor {
  static extract(book: SemanticBook): void {
    // Transform flat book ast.children of Heading and Paragraphs into structural nested Chapters/Sections
    const flatNodes = book.ast.children;
    const root = createNode("Book", undefined, {}, []);
    
    interface StackFrame {
      level: number;
      node: SemanticNode;
    }
    
    const stack: StackFrame[] = [{ level: 0, node: root }];
    
    for (const node of flatNodes) {
      if (node.type === "Heading") {
        const level = node.attributes?.level || 1;
        
        while (stack.length > 1 && stack[stack.length - 1].level >= level) {
          stack.pop();
        }
        
        const type = level === 1 ? "Chapter" : "Section";
        const container = createNode(type, undefined, { level }, [node]);
        
        stack[stack.length - 1].node.children.push(container);
        stack.push({ level, node: container });
      } else {
        stack[stack.length - 1].node.children.push(node);
      }
    }
    
    book.ast = root;
  }
}

/** 6. Stage: Footnote Extractor */
export class FootnoteExtractor {
  static extract(book: SemanticBook): void {
    // Resolve Footnote references inside text and place footnote text elements as actual child nodes
    const rawNotes = book.metadata.footnotesRaw as { index: number; text: string }[] || [];
    if (!rawNotes.length) return;

    // Attach footnotes to the end of the Book AST
    const footnoteNodes = rawNotes.map(n => 
      createNode("Footnote", n.text, { index: n.index })
    );
    
    // Create an Appendix / Footnotes wrapper at the end
    const footnotesContainer = createNode("Section", "الحواشي والتعليقات", { type: "footnotes" }, footnoteNodes);
    book.ast.children.push(footnotesContainer);
    
    // Cleanup temporary metadata field
    delete book.metadata.footnotesRaw;
  }
}

/** 7. Stage: Quran Extractor */
export class QuranExtractor {
  static extract(book: SemanticBook): void {
    // Detect Quran verses, e.g. [الأعراف: 27] or [البقرة: 12] or (الرحمن: 15)
    const pattern = /[\[\(]\s*([^\d\]\):]+?)\s*:\s*(\d+)\s*[\]\)]/g;
    
    traverseAST(book.ast, (node) => {
      if (node.type === "Paragraph" && node.content) {
        // If paragraph contains a Quran verse reference, enrich it
        const matches = [...node.content.matchAll(pattern)];
        for (const m of matches) {
          const surahCandidate = m[1].replace(/^سورة\s+/, "").trim();
          const ayah = parseInt(m[2]);
          const matchedSurah = SURAH_NAMES.find(sn => 
            cleanArabicText(sn) === cleanArabicText(surahCandidate) || 
            cleanArabicText(sn).includes(cleanArabicText(surahCandidate))
          );
          
          if (matchedSurah) {
            // High confidence for exact matches, slightly lower for fuzzy search inclusions
            const isExact = cleanArabicText(matchedSurah) === cleanArabicText(surahCandidate);
            const confidence = isExact ? 1.0 : 0.85;
            const startIdx = m.index || 0;
            const endIdx = startIdx + m[0].length;
            const nodeOrigin = node.origin ? {
              ...node.origin,
              offsetStart: (node.origin.offsetStart || 0) + startIdx,
              offsetEnd: (node.origin.offsetStart || 0) + endIdx
            } : undefined;

            node.children.push(createNode("QuranVerse", m[0], {
              surah: matchedSurah,
              ayah: ayah,
              raw_match: m[0]
            }, [], confidence, nodeOrigin));
          }
        }
      }
    });
  }
}

/** 8. Stage: Hadith Extractor */
export class HadithExtractor {
  static extract(book: SemanticBook): void {
    const indicators = [
      /قال رسول الله صلى الله عليه وسلم/u,
      /عن\s+([أبإ]\w+\s+){1,3}قال/u,
      /حدثنا\s+\w+/u,
      /أخبرنا\s+\w+/u
    ];

    traverseAST(book.ast, (node) => {
      if (node.type === "Paragraph" && node.content) {
        let isHadith = false;
        let confidence = 0.72;
        let matchIndex = 0;
        let matchLength = node.content.length;

        for (const ind of indicators) {
          const match = node.content.match(ind);
          if (match) {
            isHadith = true;
            matchIndex = match.index || 0;
            matchLength = match[0].length;
            if (ind.source.includes("رسول الله")) {
              confidence = 0.95;
            }
            break;
          }
        }
        if (isHadith) {
          const nodeOrigin = node.origin ? {
            ...node.origin,
            offsetStart: (node.origin.offsetStart || 0) + matchIndex,
            offsetEnd: (node.origin.offsetStart || 0) + matchIndex + matchLength
          } : undefined;

          node.children.push(createNode("Hadith", node.content, { type: "hadith_quotation" }, [], confidence, nodeOrigin));
        }
      }
    });
  }
}

/** 9. Stage: Scholar Extractor */
export class ScholarExtractor {
  static extract(book: SemanticBook): void {
    traverseAST(book.ast, (node) => {
      if (node.type === "Paragraph" && node.content) {
        for (const scholar of SCHOLAR_ENTITIES) {
          const idx = node.content.indexOf(scholar);
          if (idx !== -1) {
            const nodeOrigin = node.origin ? {
              ...node.origin,
              offsetStart: (node.origin.offsetStart || 0) + idx,
              offsetEnd: (node.origin.offsetStart || 0) + idx + scholar.length
            } : undefined;
            node.children.push(createNode("ScholarMention", scholar, {}, [], 0.90, nodeOrigin));
          }
        }
      }
    });
  }
}

/** 10. Stage: Book Extractor */
export class BookExtractor {
  static extract(book: SemanticBook): void {
    traverseAST(book.ast, (node) => {
      if (node.type === "Paragraph" && node.content) {
        for (const bEnt of BOOK_ENTITIES) {
          const idx = node.content.indexOf(bEnt);
          if (idx !== -1) {
            const nodeOrigin = node.origin ? {
              ...node.origin,
              offsetStart: (node.origin.offsetStart || 0) + idx,
              offsetEnd: (node.origin.offsetStart || 0) + idx + bEnt.length
            } : undefined;
            node.children.push(createNode("BookReference", bEnt, {}, [], 0.85, nodeOrigin));
          }
        }
      }
    });
  }
}

/** 11. Stage: Topic Extractor */
export class TopicExtractor {
  static extract(book: SemanticBook): void {
    // Deduce Aqeedah topic categories based on metadata title or content text matches
    const title = book.metadata.title || "";
    const cleanTitle = cleanArabicText(title);
    const topics: string[] = [];

    const topicRules = [
      { pattern: /أسماء|صفات|أصفهانية|حموية|تدمرية|إلهيات/i, topic: "al-asma-was-sifat" },
      { pattern: /توحيد|عبادة|ألوهية|شرك/i, topic: "tahwid-al-ibada" },
      { pattern: /فرق|ملل|رافضة|شيعة|أشاعرة|معتزلة|رد على|نقض/i, topic: "al-firaq-war-rudud" },
      { pattern: /إيمان|أصول الإيمان/i, topic: "al-iman" },
      { pattern: /قدر|قضاء/i, topic: "al-qadr" },
      { pattern: /آخرة|قبور|حشر|جنة|نار|أشراط الساعة/i, topic: "al-samiyyat" },
      { pattern: /ولا|براء/i, topic: "al-wala-wal-bara" },
      { pattern: /صحابة|آل البيت/i, topic: "al-imamah-was-sahabah" },
      { pattern: /سنة|بدع|بدعة/i, topic: "al-sunnah-wal-bidah" }
    ];

    for (const rule of topicRules) {
      if (rule.pattern.test(cleanTitle)) {
        topics.push(rule.topic);
      }
    }

    if (topics.length === 0) {
      topics.push("al-aqeedah-al-aamah"); // fallback general category
    }

    book.metadata.topics = topics;
  }
}

/** 12. Stage: Statistics Generator */
export class StatisticsGenerator {
  static extract(book: SemanticBook): void {
    let wordCount = 0;
    let charCount = 0;
    let headingCount = 0;
    let paraCount = 0;
    let footnoteCount = 0;
    let quranVerseCount = 0;
    let hadithCount = 0;

    traverseAST(book.ast, (node) => {
      if (node.type === "Heading") {
        headingCount++;
      } else if (node.type === "Paragraph") {
        paraCount++;
        if (node.content) {
          charCount += node.content.length;
          wordCount += node.content.split(/\s+/).length;
        }
      } else if (node.type === "Footnote") {
        footnoteCount++;
      } else if (node.type === "QuranVerse") {
        quranVerseCount++;
      } else if (node.type === "Hadith") {
        hadithCount++;
      }
    });

    book.statistics = {
      word_count: wordCount,
      character_count: charCount,
      heading_count: headingCount,
      paragraph_count: paraCount,
      footnote_count: footnoteCount,
      quran_verse_count: quranVerseCount,
      hadith_count: hadithCount
    };
  }
}

/** 13. Stage: Markdown Renderer */
export class MarkdownRenderer {
  static render(book: SemanticBook): string {
    const yamlHeader = [
      "---",
      `title: "${book.metadata.title || ''}"`,
      `author: "${book.metadata.author || ''}"`,
      `editor: "${book.metadata.editor || ''}"`,
      `publisher: "${book.metadata.publisher || ''}"`,
      `publication_year: "${book.metadata.publicationYear || ''}"`,
      `edition: "${book.metadata.edition || ''}"`,
      `volumes: ${book.metadata.volumes || 1}`,
      `topics: [${(book.metadata.topics || []).map(t => `"${t}"`).join(", ")}]`,
      "---",
      ""
    ].join("\n");

    const bodyLines: string[] = [];

    const renderNode = (node: SemanticNode) => {
      if (node.type === "Heading") {
        const lvl = node.attributes?.level || 1;
        const hashes = "#".repeat(lvl);
        bodyLines.push(`\n${hashes} ${node.content}\n`);
      } else if (node.type === "Paragraph") {
        bodyLines.push(node.content || "");
      } else if (node.type === "Footnote") {
        const idx = node.attributes?.index || 1;
        bodyLines.push(`\n[^${idx}]: ${node.content}\n`);
      } else if (node.type === "QuranVerse") {
        bodyLines.push(`\n> **${node.content}**\n`);
      } else if (node.type === "Quote") {
        bodyLines.push(`\n> ${node.content || ''}\n`);
      }
      
      // Render children recursively
      for (const child of node.children) {
        renderNode(child);
      }
    };

    // Skip root Book wrapper node and render children
    for (const child of book.ast.children) {
      renderNode(child);
    }

    return yamlHeader + bodyLines.join("\n\n");
  }
}

/** 14. Stage: Search JSON Generator */
export class SearchJsonGenerator {
  static render(book: SemanticBook): string {
    const searchDocs: any[] = [];
    let currentChapter = "";

    traverseAST(book.ast, (node) => {
      if (node.type === "Heading") {
        currentChapter = node.content || "";
      } else if (node.type === "Paragraph" && node.content) {
        searchDocs.push({
          book_title: book.metadata.title,
          author: book.metadata.author,
          chapter: currentChapter,
          text: node.content,
          topics: book.metadata.topics
        });
      }
    });

    return JSON.stringify(searchDocs, null, 2);
  }
}
