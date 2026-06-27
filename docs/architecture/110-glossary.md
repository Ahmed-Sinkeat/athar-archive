# Glossary

Canonical term map for the Athar Engine project.
Arabic term → English term → Node type or concept → Notes.

---

## Engine Concepts

| Arabic | English | Concept | Notes |
|--------|---------|---------|-------|
| محرك أثر | Athar Engine | The whole system | Not "Book Engine" — see 010-vision.md |
| الشجرة الدلالية | Semantic AST | Processing representation | Ephemeral — ADR-0002 |
| الاستيراد | Import | The pipeline run | Source → Markdown |
| الاستخراج | Knowledge Extraction | Pipeline stage 5 | Uses Rule Engine |
| الإثراء | Knowledge Enrichment | Pipeline stage 6 | Resolves entities — planned |
| محرك القواعد | Rule Engine | Mechanism inside Extraction | Loads YAML, applies patterns |
| ملف القواعد | Rule file | YAML file in rules/profiles/ | One per category |
| ملف التعريف | Rule profile | Profile directory | generic, epub, shamela |
| الكيان | Entity | A named thing with a canonical ID | Scholar, Book, Topic, Place |
| المعرف الأساسي | Canonical ID | Numeric entity ID | scholar:00001842 — ADR-0006 |
| المُعرِّف | Slug | Human-readable alias | ibn-taymiyyah — display only |

---

## Node Types

| Arabic | English | NodeType | When created |
|--------|---------|----------|-------------|
| الكتاب | Book | `Book` | Root node, always |
| المجلد | Volume | `Volume` | Multi-volume works |
| الباب / الكتاب | Chapter | `Chapter` | Level-1 heading nesting |
| الفصل / المبحث | Section | `Section` | Level-2+ heading nesting |
| العنوان | Heading | `Heading` | Every heading in source |
| الفقرة | Paragraph | `Paragraph` | Every text block |
| الاقتباس | Quote | `Quote` | Block quotes |
| الحاشية / التعليق | Footnote | `Footnote` | Pandoc Note inlines |
| الجدول | Table | `Table` | Tabular content |
| القائمة | List | `List` | Ordered/unordered lists |
| فاصل الصفحة | PageBreak | `PageBreak` | Page boundaries |
| الشعر / المنظومة | Poetry | `Poetry` | Verse — not yet implemented |
| الصورة | Image | `Image` | Embedded images — not yet implemented |
| آية قرآنية | Quran Verse | `QuranVerse` | Verse citation in text |
| حديث | Hadith | `Hadith` | Hadith narration |
| ذكر عالم | Scholar Mention | `ScholarMention` | Named scholar in text |
| ذكر كتاب | Book Reference | `BookReference` | Book title in text |
| ذكر مكان | Place Mention | `PlaceMention` | Geographic name — planned |
| ذكر فرقة | Sect Mention | `SectMention` | Religious group — planned |

---

## Pipeline Stages

| English | Arabic | Code location |
|---------|--------|---------------|
| Importer | المستورد | scripts/epub-import.ts |
| Parser | المحلل | scripts/lib/pipeline.ts — PandocParser |
| Normalizer | المُوحِّد | scripts/lib/pipeline.ts — Normalizer |
| Semantic AST Builder | بناء الشجرة الدلالية | scripts/lib/pipeline.ts — SemanticASTBuilder |
| Knowledge Extraction | استخراج المعرفة | scripts/lib/pipeline.ts — *Extractor classes |
| Knowledge Enrichment | إثراء المعرفة | Planned — not yet implemented |
| Validation | التحقق | Part of benchmark-ast.ts |
| Markdown Renderer | توليد Markdown | scripts/lib/pipeline.ts — MarkdownRenderer |
| Search JSON Generator | توليد بيانات البحث | scripts/lib/pipeline.ts — SearchJsonGenerator |

---

## Metadata Fields

| Arabic label (in source) | Field name | Example |
|--------------------------|-----------|---------|
| الكتاب | title | "العقيدة الطحاوية" |
| المؤلف / تأليف / بقلم | author | "الطحاوي" |
| تحقيق / المحقق | editor | "الألباني" |
| الناشر / دار النشر | publisher | "المكتب الإسلامي" |
| سنة النشر / سنة الطبع | publicationYear | "1408" |
| الطبعة | edition | "الثانية" |
| عدد الأجزاء | volumes | 3 |

---

## Benchmark Terms

| Term | Meaning |
|------|---------|
| PASS | Feature was present in source and correctly extracted |
| FAIL | Feature was present in source but extractor did not find it |
| N/A | Feature is not present in this source document |
| Confidence | 0.0–1.0 score attached to every extracted node |
| Coverage | Percentage of source content represented in the AST |
| Maturity | Production / Beta / Experimental / Prototype / Not Started |
| Golden Book | Reference document with ground-truth expected output |
| Golden Snippet | Short text with known extraction result, used in unit tests |
| Snippet test | A unit test that runs one extractor against one Golden Snippet |

---

## Topic Slugs

| Slug | Arabic topic |
|------|-------------|
| `al-iman` | الإيمان وأصوله |
| `al-asma-was-sifat` | الأسماء والصفات |
| `tahwid-al-ibada` | توحيد العبادة والألوهية |
| `al-qadr` | القضاء والقدر |
| `al-samiyyat` | السمعيات والأمور الغيبية |
| `al-firaq-war-rudud` | الفرق والردود |
| `al-sunnah-wal-bidah` | السنة والبدعة |
| `al-wala-wal-bara` | الولاء والبراء |
| `al-imamah-was-sahabah` | الإمامة والصحابة |
| `al-aqeedah-al-aamah` | عقيدة عامة (default fallback) |
