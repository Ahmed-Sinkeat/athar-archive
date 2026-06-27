# 050 — Storage Model

## The Two Representations

The project has two representations of a book, serving different purposes:

| | Markdown | Semantic AST |
|--|---------|--------------|
| **Purpose** | Canonical persisted representation | Canonical processing representation |
| **Lives on disk** | Yes | No — ephemeral |
| **Human readable** | Yes | No |
| **Git tracked** | Yes | N/A |
| **Editable** | Yes | No |
| **Rebuilt from** | Source (EPUB/DOC) | Markdown is the output, not the source |

See ADR-0001 and ADR-0002 for the decisions behind this design.

## What Is Persisted

### `src/content/book/*.md`
The canonical Markdown files. Each file is one book. YAML frontmatter carries structured metadata; the body carries the text.

```markdown
---
title: "العقيدة الطحاوية"
author: "الطحاوي"
editor: "الألباني"
publisher: "المكتب الإسلامي"
publication_year: "1408"
edition: "الثانية"
volumes: 1
topics: ["al-iman", "al-aqeedah-al-aamah"]
---

# باب التوحيد

محتوى الباب...
```

### `src/content/` (other collections)
- `article/` — Authored articles (not imported, not pipeline output)
- `annotation/` — Scholar annotations on books
- `audio/` — Audio content metadata

### Search Index (Meilisearch)
Rebuilt from Markdown on each deployment. Not the canonical source.

### `dist/` (build output)
Generated HTML, JavaScript, and assets. Not canonical. Always reproducible from `src/`.

## What Is Not Persisted

- **Semantic AST** — Discarded after each import run. See ADR-0002.
- **Rule decisions** — Recorded during import for benchmark use, not written to disk in production.
- **Intermediate Pandoc JSON** — Written to a temp directory during import, deleted on completion.

## Re-importing

Running the pipeline on a source document overwrites the existing Markdown file. This means:

1. **Human corrections to Markdown are lost** on the next import run for that book.
2. Books that have been manually edited after import should be excluded from automatic re-import.
3. There is no versioning or merge logic — the pipeline output replaces the file.

This is a known limitation. The recommended workflow: import once, then treat the Markdown as the source of truth for that book and do not re-import unless the source has changed significantly.

## Two Content Pipelines

The project contains two types of content with entirely different pipelines:

**Pipeline 1: Imported Content** (books)
```
Source (EPUB/DOC) → Import Pipeline → Markdown → src/content/book/
```
This pipeline is automated and document-driven.

**Pipeline 2: Authored Content** (articles, annotations)
```
Human writes → Commits Markdown → src/content/article/ or annotation/
```
This pipeline is human-driven. There is no import step.

These are not the same pipeline and should not be treated as such. The Athar Engine processes Pipeline 1 content. Pipeline 2 content is managed directly by contributors.

## Future: Entity Registry

When Knowledge Enrichment is implemented (ADR-0007), there will be a third persistent store: an entity registry mapping canonical IDs to entity records.

```
entities/
  scholars/
    00001842-ibn-taymiyyah.yaml
    00000641-al-bukhari.yaml
  books/
    00000712-sahih-bukhari.yaml
```

This is planned but not yet built. The entity registry is the persistent backing store for the Enrichment stage.
