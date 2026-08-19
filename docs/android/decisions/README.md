# Architecture decision records — Android

The selected baseline lives in [`../../main-plan.md`](../../main-plan.md). It is the
plan of record; these ADRs capture measured changes and security decisions whose trade-offs
would otherwise be easy to lose.

## Accepted records

- [`0001-stable-block-id-sidecar.md`](0001-stable-block-id-sidecar.md)
- [`0002-compact-contentless-fts.md`](0002-compact-contentless-fts.md)
- [`0003-signed-content-root.md`](0003-signed-content-root.md)
- [`0004-r2-read-through-content-delivery.md`](0004-r2-read-through-content-delivery.md)

## Write an ADR only when all three are true

1. **Hard to reverse** — it shapes schemas, the content contract, or the module
   graph, and undoing it means a migration rather than an edit.
2. **Surprising without context** — a competent reader would otherwise ask "why
   on earth is it done this way?"
3. **A real trade-off existed** — an alternative was seriously considered and
   rejected for a stated reason.

Everything else belongs in a code comment or a commit message. Most decisions do
not need an ADR, and a directory full of ceremonial ones makes the real ones
harder to find.

## Already recorded in main-plan.md — do not re-litigate as ADRs

Native Kotlin over TWA and hybrid · two databases · framed gzip packaging ·
dropping the shipped `text` field · bundled SQLite · query-side `ال` expansion
instead of a normaliser change · cloud backup disabled for private data.

## When an M0 prototype overturns an assumption

That **is** ADR-worthy — it satisfies all three tests by construction. Record the
measurement that forced the change, not just the new decision.

## Format

`NNNN-short-title.md`, numbered in order, never renumbered:

```markdown
# 0001 — Short title

**Date:** 2026-··-··
**Status:** accepted | superseded by 0007

## Context
What forced a decision. Include the measurement if there was one.

## Decision
What we do now.

## Alternatives rejected
What else was considered, and the specific reason it lost.

## Consequences
What this costs, and what it makes harder later.
```
