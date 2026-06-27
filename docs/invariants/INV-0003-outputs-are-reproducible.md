# INV-0003: Outputs Are Always Reproducible

**Status:** Invariant — cannot be overridden  
**Related ADR:** ADR-0002, ADR-0008

## Statement

Given the same source document and the same rule files, the import pipeline must always produce the same outputs. No randomness, no timestamps embedded in output content, no side effects that vary between runs.

## What This Prevents

- Embedding `imported_at: 2026-06-27T03:00:00Z` inside the Markdown content (timestamps in frontmatter for display are acceptable; pipeline-generated content must be stable)
- Using random IDs for nodes during a pipeline run
- Extractors that produce different results on the same input due to non-deterministic ordering

## Why This Cannot Change

Reproducibility is what makes git diffs meaningful. If the pipeline is non-deterministic, every re-import generates a noisy diff full of timestamp changes and random ID shuffles. Reviewers cannot tell what actually changed.

Reproducibility also means that if a bug is found in an extractor, fixing the extractor and re-running the pipeline is sufficient to fix all affected books. There is no "AST state" from the previous run that must be migrated.

## Clarification

Output files (Markdown) may be overwritten on each re-import. INV-0003 does not prevent overwriting — it requires that the *content* of the overwritten file is identical to the previous run, assuming the source and rules have not changed.
