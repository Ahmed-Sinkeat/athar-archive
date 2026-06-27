# Project Invariants

Invariants are not architectural decisions. They are unconditional laws.

ADRs (in `docs/adr/`) explain *why* a decision was made and can be revisited if the context changes. Invariants cannot be revisited. Violating an invariant means breaking the project's core guarantees — not making a different trade-off.

If a proposed feature or change requires violating an invariant, the invariant wins. The feature is rejected or redesigned until it fits within the invariants.

## Index

| ID | Statement |
|----|-----------|
| INV-0001 | Markdown is always persisted |
| INV-0002 | The Semantic AST is never persisted |
| INV-0003 | Outputs are always reproducible |
| INV-0004 | Knowledge Extraction never depends on Enrichment |
| INV-0005 | Every extracted node carries a confidence score |
| INV-0006 | Every extraction decision is traceable to a rule |
