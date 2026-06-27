# INV-0006: Every Extraction Decision Is Traceable to a Rule

**Status:** Invariant — cannot be overridden

## Statement

Every semantic node produced by the Knowledge Extraction stage must be traceable to the rule that produced it. The rule ID, the matched text, and the reason must be recorded in the pipeline's decision log (`book.ruleDecisions`).

## What This Prevents

- Hardcoded extraction logic that produces nodes without a traceable source
- An extractor that "infers" a node from context without a named rule
- A node that appears in the output but cannot be explained

## Why This Cannot Change

Traceability is how you debug extraction failures. When a book's Quran verse count drops from 45 to 12 after a rule change, you need to know which rule used to match the 33 missing verses and which rule now fails to match them.

Without rule-level traceability, debugging is grep and guesswork. With it, the benchmark can show exactly which rule fired, what it matched, and why.

## Implementation

The Rule Engine records every match as a `RuleDecision`:
```typescript
interface RuleDecision {
  ruleId: string;      // e.g., "quran_verse"
  category: string;    // e.g., "quran"
  confidence: number;  // e.g., 0.95
  extractedValue: any; // e.g., { surah: "البقرة", ayah: 255 }
  reason: string;      // e.g., "Matched pattern on text [البقرة: 255]"
  origin?: NodeOrigin; // Source location
}
```

Decision logs are written to `book.ruleDecisions` during extraction. They are used by the benchmark and are not persisted to the output Markdown.
