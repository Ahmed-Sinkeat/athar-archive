# INV-0002: The Semantic AST Is Never Persisted

**Status:** Invariant — cannot be overridden  
**Related ADR:** ADR-0002

## Statement

The Semantic AST must not be written to disk, a database, or any persistent store. It exists only in memory during a single pipeline run and is discarded when the run completes.

## What This Prevents

- An "AST cache" that stores the AST to speed up re-processing
- A "differential import" that reads the AST from a previous run and applies only changed extractors
- An API that queries the AST directly instead of querying the Outputs

## Why This Cannot Change

Persisting the AST creates a second source of truth. The moment an AST cache exists, questions arise: is the cache current? Does it match the Markdown? Which one wins on conflict? These questions have no good answers because the AST and the Markdown represent different stages of the same data.

The compiler model avoids this entirely: source in, outputs out, no intermediate state on disk.

If faster re-processing is needed, the correct solution is to make the pipeline faster — not to cache intermediate state.
