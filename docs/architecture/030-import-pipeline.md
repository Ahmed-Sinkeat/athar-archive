# 030 — Import Pipeline

The import pipeline transforms a source document (DOC, EPUB, PDF, or other format) into all persistent outputs in a single pass. The pipeline runs from the command line and has no interactive steps.

## Stages

```
Source Document
    ↓
Importer
    ↓
Parser (Pandoc)
    ↓
Normalizer
    ↓
Semantic AST Builder
    ↓
Knowledge Extraction
    ↓
Knowledge Enrichment     ← (planned, not yet operational)
    ↓
Validation
    ↓
Outputs
    ├── Markdown  → src/content/book/
    ├── Metadata JSON
    └── Search JSON
```

The Semantic AST is ephemeral. It exists during the pipeline run and is discarded afterward. See ADR-0002.

## Stage Descriptions

### 1. Importer

Selects the appropriate importer for the source format and invokes it. The importer is responsible for handling format-specific pre-processing (e.g., `.doc` → `.docx` via LibreOffice before Pandoc can read it).

All importers produce a Pandoc JSON AST as output. If a source format cannot be handled by Pandoc, a custom importer must produce an equivalent JSON structure. See ADR-0008.

### 2. Parser (Pandoc)

Converts the source document to Pandoc's internal JSON AST format:
```
pandoc -f epub -t json -o ast.json source.epub
pandoc -f docx -t json -o ast.json source.docx
```

The Pandoc JSON AST is a typed, structured representation of the document's block structure (headers, paragraphs, lists, tables, quotes) and inline content (text, emphasis, footnotes, links).

### 3. Normalizer

Applies text-level normalization to all paragraph content:
- Collapses multiple spaces into one
- Trims leading and trailing whitespace
- Does not modify Arabic text structure or remove diacritics (that is the extractor's responsibility if needed)

### 4. Semantic AST Builder

Walks the Pandoc JSON AST and produces a `SemanticBook` containing:
- A `metadata` record populated from the Pandoc metadata block
- A flat list of `SemanticNode` objects (Heading, Paragraph, Quote, List, Table, etc.)
- Origin tracking: each node records its source (doc/epub), page, paragraph index, and character offset

The output is a typed `SemanticBook` defined in `scripts/lib/semantic-ast.ts`.

### 5. Knowledge Extraction

Runs a series of extractors against the Semantic AST. Each extractor is focused on one semantic type:

| Extractor | What it finds | Maturity |
|-----------|--------------|----------|
| MetadataExtractor | Title, author, editor, publisher, year, edition | Production |
| HeadingExtractor | Nests flat headings into Chapter/Section hierarchy | Production |
| FootnoteExtractor | Attaches footnote text nodes to AST | Production |
| QuranExtractor | Identifies Quran verse references | Beta |
| HadithExtractor | Identifies Hadith narrations | Beta |
| ScholarExtractor | Identifies scholar name mentions | Beta |
| BookExtractor | Identifies book title references | Beta |
| TopicExtractor | Assigns subject topics from title | Beta |
| StatisticsGenerator | Counts words, paragraphs, headings, etc. | Production |

Extractors use the Rule Engine (ADR-0003) to match patterns defined in YAML rule files under `rules/profiles/`.

Each matched node is tagged with:
- `confidence`: 0.0–1.0 score from the matching rule
- `origin`: source location in the original document
- `ruleId`: which rule produced this node (for debugging)

### 6. Knowledge Enrichment *(planned)*

Resolves extracted entity mentions to canonical identities:
```
ScholarMention { raw: "ابن تيمية" }
    ↓
ScholarMention { id: "scholar:00001842", died: 728, slug: "ibn-taymiyyah" }
```

Enrichment is a separate stage from Extraction (ADR-0007). It does not modify what was found — it adds identity. The disambiguation problem (multiple names for the same entity) is solved here.

### 7. Validation

Runs semantic validation rules against the completed AST before output. Checks that:
- Required metadata fields are present
- Heading hierarchy is consistent
- Confidence scores are within expected ranges
- No N/A features are incorrectly marked as FAIL

Validation failures are reported but do not halt output unless configured to do so. The benchmark (see `docs/architecture/`) uses validation results to score the pipeline.

### 8. Outputs

From the validated Semantic AST, the pipeline generates:

**Markdown** (`MarkdownRenderer`): A YAML-frontmatter Markdown file written to `src/content/book/`. This is the canonical persisted representation (ADR-0001).

**Search JSON** (`SearchJsonGenerator`): A structured JSON document per paragraph, used to populate Meilisearch.

**Metadata JSON**: A structured record of all extracted metadata, statistics, and entity mentions.

## Error Handling

Extractors fail gracefully. If one extractor throws, the pipeline logs the error, marks that extractor as FAIL in the benchmark, and continues to the next stage. A book is never abandoned because one extractor failed. The output Markdown may have fewer semantic nodes, but it will be produced.

## Idempotency

Running the pipeline twice on the same source produces the same Markdown. The output file is overwritten on each run. Human corrections to an output Markdown file will be overwritten on the next import run. If a book has been manually corrected, it should be removed from the automatic import corpus and maintained manually.

## Rule Profiles

Each import run specifies a rule profile (default: `generic`). The profile selects which rule files to load for each extractor. See ADR-0004 for the fallback model.

Available profiles:
- `generic`: Baseline rules for well-formatted Arabic scholarly text
- `epub`: Adjustments for EPUB-specific structure (chapter files, spine order)

Future profiles: `shamela`, `golden-shamela`, publisher-specific.
