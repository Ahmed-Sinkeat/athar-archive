# ADR-0006: Canonical Entity IDs Are Numeric; Slugs Are Display Aliases

**Status:** Accepted  
**Date:** 2026-06-27

## Context

Entities (scholars, books, topics, hadith collections) need stable identifiers that survive renaming, transliteration changes, and format migrations. Two approaches were considered:

**Slug-based IDs:** `scholar:ibn-taymiyyah`, `book:kitab-al-tawhid`  
**Numeric IDs:** `scholar:00001842`, `book:00000712`

Slug-based IDs are human-readable but brittle — transliteration standards differ (ibn-taymiyya vs ibn-taymiyyah), Arabic names have multiple romanizations, and slugs may need to change when canonical names are corrected.

## Decision

Canonical entity IDs are numeric within their namespace. Slugs are display aliases stored alongside the ID.

```
scholar:00001842
  slug: ibn-taymiyyah
  ar: ابن تيمية
  aliases: [taqiy-al-din, sheikh-al-islam]
```

All internal references (AST nodes, enrichment links, search indexes) use the numeric ID. Slugs are used only in URLs, human-readable output, and rule files where humans need to write entity names.

## Consequences

**Positive:**
- IDs are permanent — they never need to change regardless of naming decisions
- Multiple slugs can coexist (Arabic slug, English transliteration, historical name)
- Merging duplicates is a metadata change, not a reference update
- Compatible with any future database or knowledge graph

**Negative:**
- IDs are not self-documenting — `scholar:00001842` tells you nothing without a lookup
- Requires an entity registry to assign and maintain IDs
- The entity registry does not yet exist as a formal system — this ADR anticipates it

**Current state:** This ID scheme has been decided but the entity registry has not been built. Rule files currently reference entities by Arabic name. When the entity registry is built, Enrichment will use it to resolve names to IDs.
