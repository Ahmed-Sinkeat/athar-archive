# ADR-0004: Rule Profiles Use Fallback, Not Inheritance

**Status:** Accepted  
**Date:** 2026-06-27

## Context

The Rule Engine supports multiple rule profiles (generic, epub, shamela, etc.) to handle different source formats. Two models were considered for how profiles relate to each other:

**Model 1: Inheritance**  
A specific profile inherits all rules from Generic and can override individual rules within a category. Like CSS inheritance: the specific wins, but the generic is the default.

**Model 2: Fallback**  
The engine looks for a category's rule file in the specific profile directory. If not found, it falls back to the Generic profile's file. The entire file is replaced, not individual rules.

## Decision

Fallback model. Each profile directory contains complete rule files only for the categories it overrides. If a rule file for a category is absent, the Generic profile file is used wholesale.

Implementation in `rule-engine.ts`:
```typescript
let filePath = path.join(rulesDir, profileName, `${category}.yaml`);
if (!fs.existsSync(filePath)) {
  filePath = path.join(rulesDir, 'generic', `${category}.yaml`);
}
```

## Consequences

**Positive:**
- Simple to implement and reason about
- No complex inheritance resolution needed
- Adding a new profile means only creating files for the categories that differ

**Negative:**
- If you want to add one rule to a specific profile's category, you must copy the entire Generic rule file and add to it — there is no delta/patch mechanism
- If Generic rules improve, existing profile overrides don't inherit the improvement automatically

**Future option:** Add an `extends: generic` key inside rule files to enable selective inheritance at the rule level. This would allow a profile to say "use all Generic rules for this category, plus these additional ones." This has not been implemented.
