# ADR-0008: Pandoc Is the Universal Document Parser

**Status:** Accepted  
**Date:** 2026-06-27

## Context

The importer must handle multiple document formats: DOC, DOCX, EPUB, and eventually PDF. Each format has its own internal structure. Two approaches were considered:

1. **Format-specific parsers:** A DOC parser, an EPUB parser, a PDF parser, each producing the Semantic AST directly
2. **Universal intermediate:** All formats are converted to a common intermediate representation first; then one builder produces the Semantic AST

## Decision

Pandoc is used as the universal intermediate layer. All input formats are converted to Pandoc's JSON AST. The `SemanticASTBuilder` operates on Pandoc JSON, not on raw format bytes.

For legacy `.doc` files: LibreOffice converts `.doc` → `.docx` first, then Pandoc processes the DOCX.

Pipeline:
```
.doc → (LibreOffice) → .docx → (Pandoc) → Pandoc JSON AST → SemanticASTBuilder → SemanticAST
.epub →                          (Pandoc) → Pandoc JSON AST → SemanticASTBuilder → SemanticAST
```

## Consequences

**Positive:**
- One builder handles all formats — no format-specific AST builders
- Pandoc handles complex format internals (EPUB chapter splitting, DOCX style resolution)
- Adding a new format means adding a Pandoc conversion step, not a new parser
- Pandoc is battle-tested on millions of documents

**Negative:**
- Pandoc is a system dependency — it must be installed on every machine running imports
- Formats Pandoc does not support (Shamela database exports, audio transcripts, scanned PDFs) require custom importers that produce a Pandoc-compatible or AST-compatible intermediate representation
- Pandoc's JSON AST schema can change across versions — the SemanticASTBuilder must handle version differences
- Some format-specific metadata (EPUB chapter IDs, DOCX paragraph styles) is lost or normalized during Pandoc conversion
