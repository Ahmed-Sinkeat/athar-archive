# ADR-0003: Rule Engine Over Hardcoded Extractors

**Status:** Accepted  
**Date:** 2026-06-27

## Context

The initial Knowledge Extraction stage had extraction logic hardcoded in TypeScript:

```typescript
if (/الناشر/.test(text)) { ... }
if (/المؤلف|تأليف|بقلم/.test(text)) { ... }
```

This had two problems:
1. Adding a new pattern required a code change and a test run
2. The patterns were embedded in logic, making them hard to audit, compare, or share across extractors

## Decision

All extraction knowledge is expressed in YAML rule files under `rules/profiles/`. The Rule Engine (`scripts/lib/rule-engine.ts`) loads these files at runtime and applies them. TypeScript contains only the matching mechanism; all knowledge lives in YAML.

Example rule (metadata.yaml):
```yaml
rules:
  - id: author
    confidence: 0.95
    patterns:
      - "المؤلف"
      - "تأليف"
      - "بقلم"
```

## Consequences

**Positive:**
- Rule files can be edited without touching TypeScript
- Rules are auditable, diffable, and reviewable by non-engineers
- Different profiles can encode different extraction knowledge for different source formats
- The benchmark can report which rules fired and why (rule decisions are tracked)

**Negative:**
- A custom YAML parser was required (see ADR-0009) to avoid adding a dependency
- The YAML rule format is a custom schema — contributors must learn it
- Complex extraction logic (e.g., multi-step Hadith chain parsing) cannot easily be expressed as simple YAML patterns and may require code-level extensions to the rule schema

**Boundary:** The Rule Engine is a mechanism, not a stage. It is used by the Knowledge Extraction stage. The Knowledge Enrichment stage does not use the Rule Engine — it uses the entity database and disambiguation logic. See ADR-0007.
