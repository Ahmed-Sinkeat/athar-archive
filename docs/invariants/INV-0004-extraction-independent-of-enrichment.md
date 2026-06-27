# INV-0004: Knowledge Extraction Never Depends on Enrichment

**Status:** Invariant — cannot be overridden  
**Related ADR:** ADR-0007

## Statement

The Knowledge Extraction stage must be able to run to completion without any access to the Entity Registry or the Enrichment stage. Extraction produces unresolved entity mentions. Resolution is Enrichment's job.

## What This Prevents

- An extractor that queries the entity database to decide whether a name is a scholar
- An extractor that fetches canonical IDs during the extraction run
- A "smart extractor" that uses entity resolution to improve its own recall

## Why This Cannot Change

If Extraction depends on Enrichment, the pipeline becomes circular: Extraction needs the registry, the registry needs Enrichment to be correct, Enrichment needs Extraction to have run first. This circular dependency makes the pipeline impossible to reason about incrementally.

Keeping Extraction independent means:
1. Extraction can run offline with no external dependencies
2. The entity registry can be rebuilt or replaced without re-running Extraction
3. Extraction quality can be benchmarked independently of Enrichment quality
4. A book can always be imported, even when the entity registry is unavailable

The practical consequence: extractors match names by string patterns only. A `ScholarMention` node is created for any text matching a known scholar name pattern. Whether that name resolves to a known entity is Enrichment's concern.
