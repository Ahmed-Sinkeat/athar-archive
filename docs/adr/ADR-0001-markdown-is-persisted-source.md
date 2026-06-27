# ADR-0001: Markdown is the Canonical Persisted Representation

**Status:** Accepted  
**Date:** 2026-06-27

## Context

When designing the import pipeline, a question arose: after a book is imported from DOC or EPUB, what is the source of truth on disk? Three options were considered:
1. A database (SQLite or similar)
2. The Semantic AST serialized to JSON
3. Markdown files in src/content/book/

The project already used Markdown for all existing content. The question was whether to keep it or replace it.

## Decision

Markdown files in `src/content/book/` are the canonical persisted representation. All other formats are either inputs (EPUB, DOC, PDF) or derived outputs (search JSON, metadata JSON, HTML). The Semantic AST is ephemeral and exists only during processing — see ADR-0002.

This means:
- A book's authoritative text lives in its `.md` file
- Human edits are made to the `.md` file, never to a database
- All downstream systems (website, search, API) are rebuilt from Markdown
- If Markdown and a downstream output ever conflict, Markdown wins

## Consequences

**Positive:**
- Human readable without tooling
- Git history is the full audit trail
- Offline editing with any text editor
- Longevity — Markdown files will be readable in 50 years
- Backup is a git clone
- Merge conflicts are resolvable by a human

**Negative:**
- Markdown has limited semantic expressibility — complex metadata must live in YAML frontmatter
- Re-importing from a new EPUB version risks overwriting human corrections to the Markdown
- Search and API data must be rebuilt whenever Markdown changes (not live from a database)

**Boundary:** Markdown is the canonical persisted representation. It is NOT the canonical *processing* representation. During import, the Markdown produced by the pipeline is always secondary to what the Semantic AST contains. See ADR-0002 for the processing side of this boundary.
