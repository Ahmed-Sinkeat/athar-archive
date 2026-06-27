# 060 — Entity Registry

The Entity Registry is the persistent store that maps canonical entity IDs to entity records. It is the backing store for the Knowledge Enrichment stage of the pipeline.

## Why This Exists

Knowledge Extraction identifies that a text contains "ابن تيمية". But "ابن تيمية" is one of many names for the same person. The registry solves the identity question: *which* person, with certainty, and what do we know about them?

Without the registry, entity mentions are unresolved strings. With it, they become links to a persistent knowledge record that any output — website, API, Android app — can query.

## Scope

The registry covers entities that appear in Islamic scholarly texts:

| Entity type | Namespace | Example |
|-------------|-----------|---------|
| Scholar (عالم) | `scholar:` | `scholar:00001842` |
| Book (كتاب) | `book:` | `book:00000712` |
| Hadith collection (مجموعة حديثية) | `collection:` | `collection:00000001` |
| Place (مكان) | `place:` | `place:00000091` |
| Sect / School (فرقة / مذهب) | `sect:` | `sect:00000017` |
| Topic (موضوع) | `topic:` | `topic:00000003` |

## ID Design

Canonical IDs are numeric within their namespace. See ADR-0006.

```
scholar:00001842
```

The number is assigned once and never changes. It has no inherent meaning — it is a permanent pointer, not a description.

Slugs are display aliases and live alongside the ID:

```yaml
id: scholar:00001842
slug: ibn-taymiyyah
ar: ابن تيمية
```

Multiple slugs can coexist:

```yaml
id: scholar:00001842
slug: ibn-taymiyyah
aliases:
  - taqiy-al-din
  - sheikh-al-islam
  - ahmad-ibn-abd-al-halim
```

## Record Structure

### Scholar Record

```yaml
id: scholar:00001842
slug: ibn-taymiyyah
type: scholar

name:
  ar: أحمد بن عبد الحليم الحراني
  ar_common: ابن تيمية
  en: Ibn Taymiyyah
  en_formal: Aḥmad ibn ʿAbd al-Ḥalīm ibn Taymiyyah

aliases:
  ar: [شيخ الإسلام, تقي الدين, الحراني]
  en: [Sheikh al-Islam, Taqiy al-Din]

born: 661   # AH
died: 728   # AH
madhhab: hanbali
region: sham

works:
  - book:00000091   # مجموع الفتاوى
  - book:00000134   # درء تعارض العقل والنقل

sources:
  - wikipedia: https://ar.wikipedia.org/wiki/...
  - shamela_id: 683
```

### Book Record

```yaml
id: book:00000712
slug: sahih-bukhari
type: book

title:
  ar: الجامع المسند الصحيح المختصر
  ar_common: صحيح البخاري
  en: Sahih al-Bukhari

author: scholar:00000641   # البخاري
died_author: 256

category: hadith
volumes: 9

editions:
  - publisher: دار طوق النجاة
    year: 1422
    editor: محمد زهير الناصر

sources:
  - shamela_id: 1
```

## Aliases and Transliteration

### Arabic Aliases

A single entity may be known by many Arabic names. The registry stores:
- `name.ar`: Full formal name
- `name.ar_common`: Most commonly used name
- `aliases.ar`: All other known Arabic forms

The Enrichment stage tries all aliases when resolving a mention.

### English Transliteration

The project does not enforce a single transliteration standard. The registry stores one canonical English form in `name.en` and accepts variants in `aliases.en`. URLs use the `slug` field, which follows a simplified ASCII transliteration.

## Merge Policy

When two entity records are found to represent the same entity:

1. Assign one ID as the canonical ID (typically the lower-numbered one)
2. Add the other ID to `merged_from` on the canonical record
3. The merged ID is retired — it is never reused or reassigned
4. All references to the merged ID are updated to the canonical ID
5. The merge is recorded with a date and reason

```yaml
id: scholar:00001842
merged_from:
  - scholar:00002107   # Duplicate entry created in 2026-08
  merge_date: 2026-08-15
  merge_reason: Same person, two entries created from different sources
```

## Split Policy

When one entity record is found to represent two distinct entities:

1. Create two new records with new IDs
2. Retire the original ID — mark it as `split_into`
3. Never reuse the original ID
4. Update all references manually (there is no automatic resolution)

Splits are rare and expensive. They are avoided by being conservative during initial record creation.

## Disambiguation

The Enrichment stage must resolve ambiguous mentions. "ابن تيمية" uniquely identifies one person. "أحمد" does not.

Disambiguation strategy (in order):
1. Exact match on `name.ar_common` → high confidence
2. Match on any `aliases.ar` entry → medium confidence
3. Partial match with context (surrounding text mentions known works or students) → low confidence, flagged for review
4. No match → mention remains unresolved (`resolved: null`)

Unresolved mentions are not failures. They are valid states. The benchmark tracks resolution rate separately from extraction rate.

## File Layout

```
entities/
  scholars/
    00001842-ibn-taymiyyah.yaml
    00000641-al-bukhari.yaml
    00000256-ibn-hanbal.yaml
  books/
    00000712-sahih-bukhari.yaml
    00000091-majmu-al-fatawa.yaml
  collections/
    00000001-kutub-al-sitta.yaml
  topics/
    00000001-al-iman.yaml
    00000002-al-asma-was-sifat.yaml
```

## Current Status

The entity registry does not yet exist as a formal file system. Entity knowledge currently lives in rule files (`rules/profiles/generic/scholar.yaml`, `book.yaml`) as flat name lists. These are extraction rules, not registry records.

The registry will be built when the Enrichment stage is implemented (ADR-0007). At that point, the name lists in rule files become lookup tables against the registry, not standalone knowledge.
