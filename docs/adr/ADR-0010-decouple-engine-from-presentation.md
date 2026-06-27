# ADR-0010: Decoupling processing engine from presentation layer

**Status:** Accepted  
**Date:** 2026-06-27

## Context

Athar Engine has grown inside a single repository alongside the Ahl al-Athar website (Astro pages, CSS styles, Cloudflare configurations, UI components). Having both codebase types in one repository couples the logic of understanding and parsing Islamic text to the rendering technology. 

As the project scales to support multiple presentation surfaces (Website, REST APIs, Android App, Desktop Reader), the engine must be treated as a pure, decoupled processing library (similar to LLVM or Pandoc) rather than an Astro utility.

## Decision

We will architecturally and physically split the project into two distinct entities:

1. **Athar Engine (`athar-engine`):** A compiler-like core library and CLI. It is responsible for parsing heterogenous documents into a Semantic AST, extracting named entities, resolving canonical IDs, and outputting structured Markdown/JSON data. It has **zero dependencies** on presentation frameworks (Astro, HTMX, Tailwind) or hosting environments (Cloudflare Workers).
2. **Ahl al-Athar (`ahl-al-athar`):** The product and user interface layers. It consumes the structured outputs of Athar Engine and depends on it as a package/library.

To avoid premature physical splitting, we will decouple the **architecture documentation, vision, and ADRs first**. The actual codebase separation (into separate repositories or a monorepo workspace) will occur once the internal engine APIs stabilize.

## Proposed Repository Layout

### Athar Engine Repository
```
athar-engine/
├── docs/                      # Ingress, Taxonomy, Semantic and Engine specs
├── packages/
│   ├── parser/                # Ephemeral AST parser
│   ├── ast/                   # Type definitions & AST node operations
│   ├── extractor/             # Rule Engine & pattern extractors (Regex, NER)
│   ├── enrichment/            # Entity Registry matcher & disambiguation
│   ├── importer-epub/         # EPUB ingest parser
│   ├── importer-docx/         # DOCX ingest parser
│   ├── importer-html/         # HTML ingest parser
│   ├── renderer-markdown/     # Standard Markdown renderer output
│   └── cli/                   # Ingestion & validation CLI tool
├── corpus/                    # Staged testing corpus
├── tests/                     # Validation & parser unit tests
└── examples/                  # Client usage examples
```

### Ahl al-Athar Repository
```
ahl-al-athar/
├── website/                   # Astro static website frontend
├── api/                       # Cloudflare Worker REST API
└── android/                   # Native Android application
```

## Consequences

**Positive:**
- **Separation of Concerns:** The engine developers focus entirely on the correctness of Islamic science models, while UI developers focus entirely on SEO, Core Web Vitals, accessibility, and user experience.
- **Reusability:** Multiple downstream projects (such as university tools or desktop readers) can consume `athar-engine` as a standard dependency.
- **Open Source Ready:** The processing engine can be open-sourced independently from the proprietary elements or configuration of Ahl al-Athar.
- **Fast Tests & Builds:** Astro static site builders do not have to parse unit tests and compiler specs, making compilation cleaner.

**Negative:**
- **Monorepo / Multi-repo overhead:** Managing dependencies, package versioning, and developer linking becomes slightly more complex.
- **Dual-step development:** Changes to the parser require modifying the engine package, publishing or linking it, and then deploying the updated website dependency.
