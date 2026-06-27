# 010 — Project Vision

> الهدف ليس بناء موقع.
> الهدف هو بناء محرك لفهم الكتب الإسلامية.
> الموقع مجرد أحد المخرجات.

## The Name

**Athar Engine** — An Islamic Knowledge Processing Engine.

Athar (أثر, plural آثار) means traces, narrations, reports. The traditions passed from scholar to scholar. The name reflects the content — and unlike a descriptive name, it becomes a brand. Like LLVM or Pandoc, it does not describe itself.

The full description, for contexts where it is needed:

> Athar Engine is an Islamic Knowledge Processing Engine that transforms heterogeneous Islamic sources — books, articles, lessons, audio, and other scholarly materials — into a unified semantic knowledge model. This model is then used to generate multiple outputs: websites, APIs, search indexes, mobile applications, and future knowledge services, all from a single processing pipeline.

## What the Engine Processes

The engine is not limited to books. It processes:

- Books (كتب): Classical and contemporary Islamic scholarship
- Hadith collections (أجزاء حديثية): Individual narrations and chains
- Articles (مقالات): Scholarly papers and research
- Lessons (دروس): Lecture transcripts
- Audio (تسجيلات): Transcribed recordings
- Commentaries (شروح): Explanations of primary texts
- Marginal notes (حواشي): Editor and scholar annotations
- Biographies (تراجم): Scholar biographies
- Fatwas (فتاوى): Religious rulings
- Poetry (منظومات): Didactic verse

The common unit is not the book. It is the **knowledge object** — a piece of Islamic content with authorship, transmission, and semantic structure.

## What the Engine Produces

The same semantic model produces multiple outputs:

| Output | Status |
|--------|--------|
| Website (ahlalathar.com) | Operational |
| Search index (Meilisearch) | Operational |
| Markdown corpus | Operational |
| REST API | Planned |
| Android application | Planned |
| Knowledge Graph | Planned |
| Translation pipeline | Planned |
| Desktop application | Future |
| AI-assisted tagging | Future |

No output is privileged. The website is an adapter over the semantic model, not the model itself.

## The Shift in Thinking

Before:
> "How do I display this book?"

After:
> "How do I understand this book?"

Display becomes a consequence, not a goal. When the engine understands a book — its structure, its references, its scholars, its topics, its chains of transmission — any display format follows automatically.

## The LLVM-Style Decoupled Architecture

Similar to the relationship between **LLVM** (the compiler engine) and **Clang** (the compiler frontend), Athar Engine is fully decoupled from its presentation layers:

* **Athar Engine (The Core Engine):** A library/CLI responsible for ingestion, parsing, AST, entity extraction, and semantic mapping. It is presentation-agnostic and does not know about Astro, CSS, or Cloudflare.
* **Ahl al-Athar (The Presentation Frontend):** A collection of client adapters (website, API, mobile app) that depend on Athar Engine as a library and consume its structured outputs.

This decoupling guarantees that the underlying knowledge model remains pure, correct, and completely reusable for any third-party application or research tool. For more details, see [ADR-0010](file:///home/sinkeat/Projects/athar-archive/docs/adr/ADR-0010-decouple-engine-from-presentation.md).

## System Boundaries

Athar Engine is responsible for:

```
✓ Importing knowledge from source documents (DOC, EPUB, PDF, ...)
✓ Parsing source documents into a Semantic AST
✓ Extracting semantic content (headings, Quran refs, Hadith, entities, ...)
✓ Enriching extracted entities with canonical identities
✓ Validating extraction quality against the benchmark
✓ Generating outputs (Markdown, search JSON, metadata JSON)
✓ Providing data to the API layer
```

Athar Engine is **not** responsible for:

```
✗ Editing books (no WYSIWYG editor, no web-based content management)
✗ Managing users or permissions
✗ Performing OCR on scanned documents (pre-processing step, outside pipeline)
✗ Translating scholarly content automatically (translation is an output adapter)
✗ Serving as a general-purpose CMS
✗ Rendering the website (that is Astro's job, consuming the Engine's outputs)
✗ Serving the API (that is a Cloudflare Worker consuming the Engine's outputs)
✗ Storing user annotations or personal data
```

This boundary matters because every system that grows without a stated boundary eventually becomes everything to everyone and excels at nothing. Applications are built *on top of* the Engine. The Engine does not become those applications.

When a proposed feature sits outside this boundary, the correct response is: build it as an application that consumes the Engine's outputs — not as a feature of the Engine itself.
