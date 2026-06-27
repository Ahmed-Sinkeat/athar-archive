# ADR-0007: Knowledge Extraction and Knowledge Enrichment Are Separate Pipeline Stages

**Status:** Accepted  
**Date:** 2026-06-27

## Context

In earlier pipeline designs, "extraction" covered everything from identifying a scholar name in text to resolving it to a specific canonical entity with a death date and known works. This created tight coupling: the extractor had to know about the entity database, and improving the entity database required re-running extractors.

## Decision

Extraction and Enrichment are distinct pipeline stages with different responsibilities:

**Knowledge Extraction:** Identifies semantic content in the raw text and creates unresolved nodes.
```
قال ابن تيمية
↓
ScholarMention { raw: "ابن تيمية", resolved: null }
```

**Knowledge Enrichment:** Resolves unresolved nodes to canonical entities and adds structured data.
```
ScholarMention { raw: "ابن تيمية", resolved: null }
↓
ScholarMention { raw: "ابن تيمية", id: "scholar:00001842", died: 728, slug: "ibn-taymiyyah" }
```

Extraction uses the Rule Engine (ADR-0003). Enrichment uses the entity registry (ADR-0006) and disambiguation logic.

## Consequences

**Positive:**
- Extractors can run without an entity database
- Enrichment can be improved (or rebuilt entirely) without touching extraction
- A book can be successfully imported even when entity resolution fails — it gets unresolved mentions
- Extraction quality and enrichment quality can be benchmarked independently

**Negative:**
- The pipeline has more stages — more code, more test surface
- Unresolved mentions must be handled gracefully throughout the system
- Disambiguation (the same scholar referred to by many names) is the hard problem of Enrichment and is not solved by this separation — it just localizes the problem to the Enrichment stage

**Note:** Knowledge Enrichment is a planned stage. It does not fully exist in the current codebase. Extraction exists and is operational. This ADR records the decision so that when Enrichment is built, it is built as a separate stage.
