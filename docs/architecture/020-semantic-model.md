# 020 — Semantic Model

The Semantic AST is a typed tree of `SemanticNode` objects. Every piece of content extracted from a source document becomes one of these node types. This document defines every node type, when it is created, and what it contains.

## Node Structure

Every node has the same shape:

```typescript
interface SemanticNode {
  type: NodeType;
  content?: string;                   // Raw text of this node
  attributes?: Record<string, any>;   // Node-specific metadata
  children: SemanticNode[];           // Child nodes
  confidence?: number;                // 0.0–1.0 extraction confidence
  origin?: NodeOrigin;                // Source location
}
```

`NodeOrigin` records where in the source document this node came from:
```typescript
interface NodeOrigin {
  source: "doc" | "epub" | "markdown" | "other";
  page?: number;
  paragraph?: number;
  offsetStart?: number;  // Character offset in source
  offsetEnd?: number;
  file?: string;         // EPUB chapter file
  xpath?: string;        // EPUB XPath
}
```

## Node Hierarchy

```
Book
├── Volume (optional)
│   └── Chapter
│       ├── Heading
│       ├── Paragraph
│       │   ├── QuranVerse
│       │   ├── Hadith
│       │   ├── ScholarMention
│       │   └── BookReference
│       ├── Section
│       │   ├── Heading
│       │   └── Paragraph
│       ├── Quote
│       │   └── Paragraph
│       ├── List
│       │   └── Paragraph
│       └── Table
└── Section [type="footnotes"]
    └── Footnote
```

## Structural Nodes

**Book** — Root of every semantic tree. Created by SemanticASTBuilder.

**Volume** — A single volume in a multi-volume work. `attributes: { number: 2 }`. *Maturity: Experimental.*

**Chapter** — Top-level section, created when HeadingExtractor nests a Level-1 heading. `attributes: { level: 1 }`. *Maturity: Production.*

**Section** — Sub-section within a Chapter. `attributes: { level: 2 }` or `{ type: "footnotes" }`. *Maturity: Production.*

**Heading** — Heading text node; always a child of Chapter or Section. `attributes: { level: 1|2|3 }`. *Maturity: Production.*

## Content Nodes

**Paragraph** — Block of prose text. Most common node type. *Maturity: Production.*

**Quote** — Block quote. Children are Paragraph nodes. Created from Pandoc BlockQuote. *Maturity: Production.*

**Footnote** — Single footnote entry. Child of a Section with `type: "footnotes"`. `attributes: { index: 1 }`. *Maturity: Production.*

**Table** — Tabular block. Content not yet semantically parsed. *Maturity: Experimental.*

**List** — Ordered or unordered list. `attributes: { list_type: "BulletList"|"OrderedList" }`. Children are Paragraph nodes. *Maturity: Production.*

**PageBreak** — Page boundary. `attributes: { page: 42 }`. *Maturity: Experimental.*

**Image** — Embedded image. Not yet implemented. *Maturity: Not Started.*

**Poetry** — Structured verse with optional meter and rhyme. *Maturity: Not Started.*

## Semantic Nodes

**QuranVerse** — Reference to a specific verse. Always child of a Paragraph.  
`content`: citation text. `attributes: { surah: "البقرة", ayah: 255, raw_match: "[البقرة: 255]" }`.  
Confidence: 0.95. *Maturity: Beta.*

**Hadith** — A hadith narration. Always child of a Paragraph.  
`content`: full narration text. `attributes: { type: "hadith_quotation" }`.  
Confidence: 0.90. *Maturity: Beta.*  
Future: separate Isnad and Matn as child nodes.

## Entity Reference Nodes

These nodes are always children of Paragraph nodes. They mark a mention of a named entity in the text.

**ScholarMention** — Named scholar.  
`content`: name as it appears. `attributes: { resolved?: "scholar:00001842" }` after Enrichment.  
Confidence: 0.90. *Maturity: Beta.*

**BookReference** — Book title mention.  
`content`: title as it appears. `attributes: { resolved?: "book:00000712" }` after Enrichment.  
Confidence: 0.85. *Maturity: Beta.*

**PlaceMention** — Geographic place name. *Maturity: Not Started.*

**SectMention** — Religious group or school of thought (e.g., الجهمية, المعتزلة). *Maturity: Not Started.*

## Metadata

`BookMetadata` lives on `SemanticBook.metadata`, not in the AST tree:

```typescript
interface BookMetadata {
  title?: string;
  author?: string;
  editor?: string;         // Muhaqqiq
  publisher?: string;
  publicationYear?: string;
  edition?: string;
  volumes?: number;
  topics?: string[];       // Topic slugs from TopicExtractor
}
```

## Node Creation Rules

A node is created when:
1. The source document explicitly contains the corresponding structure (structural nodes), OR
2. An extractor identifies a semantic pattern in existing text (Islamic/entity nodes)

A node is **not** created when:
- The source contains no evidence of the corresponding content
- A match confidence is below the extractor's threshold
- The feature is marked N/A for the document type

## Planned Nodes

| Node | Description |
|------|-------------|
| `Isnad` | Hadith chain of narrators, parsed from Hadith |
| `Matn` | Hadith text body, parsed from Hadith |
| `TermMention` | Technical Islamic term (الإيمان, البدعة) |
| `CrossReference` | Explicit "انظر" / "see also" links between books |
| `ChapterSummary` | Auto-generated chapter summary |
