# ADR-0002: The Semantic AST Is Ephemeral (Compiler Model)

**Status:** Accepted  
**Date:** 2026-06-27

## Context

The import pipeline builds a Semantic AST — a rich, typed representation of a book's content. The question arose whether to persist this AST to disk (as JSON or in a database) for later reuse, or to discard it after each import run.

Persisting the AST would allow:
- Incremental extraction (re-run only changed extractors)
- Querying the AST without re-importing the source
- Using the AST as a database for the API

Discarding the AST (compiler model) would mean:
- Every import is a full rebuild from source
- The AST is a processing artifact, not a data store
- Outputs (Markdown, search JSON, metadata JSON) are the only persistent artifacts

## Decision

The Semantic AST is never persisted to disk. It is built during import, used to generate all outputs, then discarded. This is the compiler model — analogous to how a compiler builds an AST from source, generates machine code, then exits.

This decision is stated explicitly in the project vision:
> AST لا يخزن على القرص. يبنى أثناء الاستيراد. يستخدم لاستخراج المعرفة. يحذف بعد الانتهاء.

## Consequences

**Positive:**
- No AST database schema to maintain or migrate
- Outputs are always deterministic — the same source always produces the same AST
- No stale AST problem — the AST is always fresh from the source
- Simpler architecture — there is no "AST store" service

**Negative:**
- Re-importing is always a full rebuild — no incremental extraction
- The API cannot query the AST directly; it queries the Outputs (Markdown, metadata JSON)
- If a source file is lost (EPUB deleted), the only recovery is from the Markdown output

**Implication for Android and API:** Neither Android nor the API accesses the AST directly. They access the Outputs via the API layer. The API reconstructs structured responses from stored Markdown + metadata JSON. See ADR-0007 and docs/architecture/070-api.md.
