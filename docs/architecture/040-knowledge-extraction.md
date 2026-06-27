# 040 — Knowledge Extraction Engine

The Knowledge Extraction stage is responsible for identifying semantic content in the Semantic AST and annotating nodes with typed, confidence-scored semantic information.

## Architecture

```
Semantic AST
    ↓
Rule Engine (loads YAML rules)
    ↓
Extractors (use Rule Engine)
    ↓
Annotated Semantic AST
```

The Rule Engine is the mechanism. Knowledge Extraction is the stage. These are different levels.

## The Rule Engine

The Rule Engine (`scripts/lib/rule-engine.ts`) provides a generic matching interface:

1. Loads rule files from `rules/profiles/<profileName>/<category>.yaml`
2. Falls back to `rules/profiles/generic/<category>.yaml` if a profile-specific file is absent (ADR-0004)
3. Applies patterns to text and returns structured match results
4. Records every rule decision with: rule ID, category, confidence, extracted value, reason, and origin

### Rule File Structure

Each rule file contains a list of rules. A rule has:
- `id`: unique identifier within the category
- `enabled`: true/false kill switch
- `confidence`: default confidence score for matches (0.0–1.0)
- `priority`: ordering when multiple rules could match (higher wins)
- `language`: `ar` or `en`
- Category-specific fields: `patterns`, `keywords`, `entities`, `pattern`, `surah_patterns`, etc.

Example (metadata.yaml):
```yaml
rules:
  - id: author
    priority: 100
    enabled: true
    confidence: 0.95
    language: ar
    category: metadata
    patterns:
      - "المؤلف"
      - "تأليف"
      - "بقلم"
```

### Rule Profiles

Profiles live at `rules/profiles/<name>/`. The current profiles:

- `generic/`: Baseline rules for standard Arabic scholarly text
  - `metadata.yaml`, `heading.yaml`, `quran.yaml`, `hadith.yaml`, `scholar.yaml`, `book.yaml`, `topic.yaml`
- `epub/`: *(planned)* EPUB-specific overrides

## Extractors

Each extractor is a static class with an `extract(book, profile)` method. Extractors are called in order after the Semantic AST Builder completes.

### MetadataExtractor

Searches the first 15 paragraphs for metadata patterns (title, author, editor, publisher, year, edition, volumes). Matches using `matchMetadata()` from the Rule Engine, which applies regex patterns like:
```regex
المؤلف\s*:\s*([^\n]+)
```

**Maturity:** Production. High confidence on well-formatted title pages.

### HeadingExtractor

Takes the flat list of Heading nodes from the AST Builder and nests them into a Chapter/Section hierarchy based on heading level. Applies keyword rules to refine detected levels:

- Level 1 keywords: كتاب, باب, القسم
- Level 2 keywords: فصل, مبحث, المطلب
- Level 3 keywords: فرع, مسألة

**Maturity:** Production.

### FootnoteExtractor

Resolves footnote references embedded during parsing (Pandoc Note inline elements) into explicit Footnote nodes attached to the AST.

**Maturity:** Production.

### QuranExtractor

Matches Quran verse references using configurable patterns. The default pattern matches the format `[سورة: آية]`. Validates the surah name against the complete list of 114 surah names.

**Maturity:** Beta. The pattern covers bracket-format citations; other citation styles require additional rules.

### HadithExtractor

Matches Hadith narration chains using isnad patterns: `حدثنا`, `أخبرنا`, `روى`, combined with narrator name sequences.

**Maturity:** Beta.

### ScholarExtractor

Matches scholar names from an entity list in `scholar.yaml`. String search (not regex) for performance on large texts.

**Maturity:** Beta. Recall limited by entity list size.

### BookExtractor

Matches book title references from an entity list in `book.yaml`.

**Maturity:** Beta.

### TopicExtractor

Matches topic keywords against the book's title field using regex patterns in `topic.yaml`. Assigns topic slugs (e.g., `al-iman`, `al-asma-was-sifat`).

**Maturity:** Beta.

### StatisticsGenerator

Counts semantic nodes: words, characters, paragraphs, headings, footnotes, Quran verses, Hadith nodes.

**Maturity:** Production.

## Rule Decisions

Every match is recorded as a `RuleDecision` on the book object:
```typescript
interface RuleDecision {
  ruleId: string;
  category: string;
  confidence: number;
  extractedValue: any;
  reason: string;
  origin?: NodeOrigin;
}
```

These decisions are used by the benchmark to explain why a node was or was not extracted. They are not persisted to the output Markdown.

## Confidence Scores

Every extracted node carries a confidence score (0.0–1.0). Confidence is assigned by the matching rule and represents the engine's certainty that the extraction is correct.

| Score range | Meaning |
|-------------|----------|
| 0.95–1.0 | High confidence — reliable for display |
| 0.80–0.94 | Medium confidence — reliable with review |
| 0.60–0.79 | Low confidence — needs human verification |
| < 0.60 | Speculative — do not use without review |

## Extractor Maturity Levels

| Level | Meaning |
|-------|---------|
| **Production** | Stable, tested, high recall. Used in pipeline without review. |
| **Beta** | Functional, tested, but recall varies by source format. |
| **Experimental** | Works on known cases. May fail on new inputs. |
| **Prototype** | Proof of concept. Not in main pipeline. |
| **Not Started** | Planned but not implemented. |
