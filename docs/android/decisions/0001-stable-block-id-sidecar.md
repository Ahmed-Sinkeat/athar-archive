# 0001 — Persist stable block IDs at build time

**Date:** 2026-08-16
**Status:** accepted

## Context

The selected plan provisionally derived each block ID from book ID, chapter anchor, normalized
text, preceding-text context and duplicate index. M0/R4 measured that scheme over 8,000 real
blocks from `al-msnd-al-mwdway-al-jama-llktb-al-ashra` under all five required mutations.

Four mutations retained 99.000–99.987% exact identity and 99.625–100% including fuzzy
recovery. Renaming the chapter heading changed the chapter anchor and therefore changed
**100% of its block IDs**: 0% exact, although fingerprint recovery reached 100%.

The R4 gate requires at least 95% exact on every mutation and mandates the sidecar if any
mutation falls below 90% exact.

## Decision

The TypeScript build pipeline owns a persistent stable-ID sidecar per readable entity
(`book`, `article`, `question`, `poem`). Regeneration
aligns new semantic blocks to the previous generation using exact normalized content first,
then fingerprint, surrounding context and ordinal proximity. Matched blocks retain their
128-bit IDs; genuinely new blocks receive random 128-bit IDs from a cryptographically secure
generator.

The generated `.athar` package carries those IDs. Android stores and compares them but never
derives or rewrites them. The sidecar is build input and is committed or stored durably with
the content pipeline; losing it is treated as a contract-breaking event.

## Alternatives rejected

- **Keep the derived formula and rely on fuzzy recovery:** a harmless heading correction
  would make every annotation in that chapter non-exact and force unnecessary reconciliation.
- **Remove `chapterAnchor` from the formula:** previous-text edits and duplicate insertions
  would still cascade in edge cases; refining the hash only moves the instability.
- **Use ordinals as IDs:** inserting or moving one paragraph renumbers the remainder.

## Consequences

The content build gains a stateful artifact and an alignment step, so package generation is
no longer reproducible from Markdown alone unless the prior sidecars are also present.
In return, ordinary editorial changes preserve user annotations exactly. Fingerprint/context
recovery remains necessary for genuinely rewritten or moved passages and for disaster
recovery, but it is no longer the normal path after a heading edit.
