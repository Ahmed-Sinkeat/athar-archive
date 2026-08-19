# 0002 — Use compact contentless FTS with exact phrase post-filtering

**Date:** 2026-08-16
**Status:** accepted

## Context

The plan originally expected a Room `@Fts5` external-content table with `prefix='2 3'`.
M0/R2b measured four FTS5 layouts over 5,428 real blocks from 20 books. Index size was
calculated after subtracting an identical relational-only database, so it measures the index
rather than charging the FTS design for the retained source text.

| Layout | Net index/raw | Import | Search | Prefix | Delete |
|---|---:|---:|---:|---:|---:|
| regular + prefix 2/3 | 222.4% | 1,694.3 ms | 11.9 ms | 13.2 ms | 66.1 ms |
| contentless + prefix 2/3 | 101.7% | 1,457.6 ms | 11.5 ms | 13.2 ms | 259.2 ms |
| contentless, no prefix | 56.4% | 1,209.1 ms | 11.7 ms | 15.7 ms | 31.7 ms |
| contentless, `detail=none` | **35.2%** | **1,072.4 ms** | **10.1 ms** | 18.4 ms | 42.8 ms |

All variants returned 20/20 expected hits. The compact layout rebuilt offline from retained
packages in 2,176.0 ms and again returned 20/20. R7 separately showed that the Room-managed
full-detail path produced a 206.9 MiB database for one 81.98 MiB book and took 528 seconds to
import and index it on the connected phone.

## Decision

Room continues to own relational schema, migrations and transactions. Block full-text search
uses raw migration/callback DDL because Room's `@Fts5` annotation cannot express this layout:

```sql
CREATE VIRTUAL TABLE block_fts USING fts5(
  norm,
  tokenize='unicode61',
  content='',
  contentless_delete=1,
  detail=none
);
```

There is no prefix index. A trailing `*` remains functional against the ordinary term index
and was only 2.7–8.3 ms slower in the measured variants.

`detail=none` stores no token positions, so quoted FTS phrase syntax is unavailable. The
query builder emits explicit-`AND` candidate terms for phrases and split compound names.
Candidates join by `rowid` to retained original block text; the app normalizes that text and
requires an exact contiguous phrase before displaying or ranking a phrase hit. Original
vocalised ranges still come from `normalizeWithMap`.

Candidate retrieval is paged, not capped. The repository scans deterministic 80-row batches
ordered by `bm25(), rowid` until it has enough verified hits, exhausts the candidates, or the
search is cancelled. A fixed first-batch limit would create false negatives whenever the
first exact occurrence ranked after many co-occurring-term blocks.

Per-book removal deletes FTS rowids before relational block rows. Repair recreates the index
offline from retained `.athar` packages; the FTS table is never treated as a source of truth.

## Alternatives rejected

- **Regular external-content FTS:** simple Room integration, but the index alone was 222.4%
  of raw text and missed the 55% gate by more than 4×.
- **Contentless with prefix indexes:** smaller than regular, but still 101.7% of raw text.
- **Contentless without `detail=none`:** nearly met the gate at 56.4%, but remained 60% larger
  than the compact winner without a useful latency advantage.
- **Keep prefix 2/3 indexes:** saved only a few milliseconds while adding substantial disk
  cost; ordinary FTS prefix lookup already met the 40 ms gate.

## Consequences

Phrase search has a two-stage contract and must never trust candidate retrieval alone. The
query builder therefore has a compact-candidate mode; paged candidate scanning and exact
normalized-body filtering are mandatory in repository tests, including a fixture whose
first exact hit appears after candidate 80. Room cannot model the virtual table as an
`@Fts5` entity, so DDL, paging, delete and rebuild paths require focused SQL tests. In return,
index size, import speed, delete latency, ordinary search and offline repair all meet the
measured M0 gates.
