# ADR-0005: Benchmark Measures Pipeline Quality, Not DOC-vs-EPUB Similarity

**Status:** Accepted  
**Date:** 2026-06-27

## Context

The initial benchmark compared DOC and EPUB versions of the same book, treating differences in extracted structure as failures. If the DOC version had 15 headings and the EPUB had 12, this was scored as a failure.

This approach was flawed because DOC and EPUB editions of the same book often represent genuinely different editions: different editors, different formatting conventions, different footnote styles, different word counts. The benchmark was measuring edition differences, not extraction quality.

## Decision

Each document is evaluated independently against its own semantic expectations. A document passes a check if its extracted semantic structure is correct for that document, regardless of what another edition produces.

The benchmark now answers the question: *"Can this pipeline correctly reconstruct the semantic structure of THIS document?"* — not *"Does this document look like that other document?"*

Specifically:
- Each feature (Metadata, Headings, Footnotes, Quran references, etc.) is scored as PASS, FAIL, or N/A
- N/A means the source document does not contain this feature — it does not penalize the score
- A feature fails only if the source contains it but the extractor did not find it
- Scores have three states, not two

## Consequences

**Positive:**
- Scores are meaningful — a 100% score means the pipeline correctly extracted everything present in the source
- N/A prevents sparse books (no footnotes, no poetry) from being penalized
- Each extraction stage is scored independently, making regressions easy to locate
- History is preserved — scores across versions are comparable because the scoring methodology is stable

**Negative:**
- Requires defining "expectations" for each feature, which is subjective for some features
- Snippet tests must be written by humans to define ground truth

**Commitment:** The scoring methodology must remain stable across versions. If a new metric is introduced, it is added as a new metric — not a change to existing metric weights. This preserves the meaning of historical scores.
