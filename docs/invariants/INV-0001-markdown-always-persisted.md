# INV-0001: Markdown Is Always Persisted

**Status:** Invariant — cannot be overridden  
**Related ADR:** ADR-0001

## Statement

Every book processed by the import pipeline must produce a Markdown file persisted to `src/content/book/`. This file is not optional, not an intermediate artifact, and not replaceable by another format.

## What This Prevents

- A pipeline that produces only a database record and no Markdown
- An import mode that writes only to search indexes and skips Markdown
- A future "fast import" that produces a JSON blob instead of Markdown to save disk I/O

## Why This Cannot Change

Markdown is the escape hatch. If every other system fails — the API goes down, the database is corrupted, the search index is lost — a git clone of the repository is sufficient to reconstruct everything. This guarantee is only meaningful if Markdown is always present.

Relaxing this invariant would mean the repository is no longer the source of truth, which changes the fundamental nature of the project.
