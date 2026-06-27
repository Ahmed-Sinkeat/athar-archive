# INV-0005: Every Extracted Node Carries a Confidence Score

**Status:** Invariant — cannot be overridden

## Statement

Every node produced by Knowledge Extraction (QuranVerse, Hadith, ScholarMention, BookReference, etc.) must carry a `confidence` value between 0.0 and 1.0. A confidence value of 0.0 is valid. The absence of a confidence value is not.

Structural nodes produced by the Semantic AST Builder (Heading, Paragraph, List, etc.) carry confidence 1.0 by default — they are not extracted, they are parsed.

## What This Prevents

- An extractor that returns nodes without confidence scores
- A "quick extractor" that produces nodes with `confidence: undefined`
- Silently treating all extractions as equally reliable

## Why This Cannot Change

Confidence scores are the mechanism by which downstream consumers decide what to trust. The API can filter by confidence. The benchmark uses confidence to understand extraction quality. The Android app can show or hide nodes below a threshold.

Without confidence scores, all extracted data is indistinguishable — a high-precision QuranVerse match looks the same as a speculative ScholarMention. This collapses the quality signal the engine is designed to provide.

## Implementation

Confidence is set by the matching rule in the rule file:
```yaml
- id: quran_verse
  confidence: 0.95
```

If a rule does not specify confidence, the Rule Engine defaults to 0.90. This default must never be 1.0, since 1.0 is reserved for structurally parsed nodes.
