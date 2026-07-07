# Importing EPUBs: athar-archive's importer vs. Athar-Engine

Correction to an earlier version of this doc: `athar-archive/scripts/epub-import.ts` and `/home/sinkeat/Projects/Athar-Engine` are **two different, separate systems**, not one thing named twice. I originally missed the `Athar-Engine` repo entirely.

## The two systems

| | `athar-archive/scripts/epub-import.ts` | `Athar-Engine` (separate repo) |
|---|---|---|
| What it is | One standalone script, ~regex/xhtml parsing | A whole repo: a "deterministic Foundation Compiler" |
| Pipeline | EPUB → hand-rolled xhtml/regex parse → Markdown, written straight into `src/content/` | EPUB/DOCX/HTML → **Pandoc** → structural AST → semantic AST (entity/knowledge extraction via `EntityRegistry`) → Markdown renderer |
| Scope | Only Shamela-style EPUB exports (one xhtml per page) | Multiple source formats; has a `compare-importers.ts` to diff epub-vs-doc output for the *same* book |
| Output destination | Writes directly into athar-archive's `src/content/book|person/*.md` | Writes into its own `canonical-corpus/*/output/`; a separate script (`sync-website-bodies.mjs`) later copies compiled bodies into athar-archive's `src/content/book/`, **preserving** athar-archive's curated frontmatter |
| Quality control | None beyond `pnpm validate:content` (Zod schema) | A 15-book "canonical corpus" regression suite, benchmarks, quality dashboards, per-domain rules (headings/footnotes/poetry/tables/…) |
| Status | Production, used ad hoc per book | Active R&D — "Phase 1" frozen scope (structure/typography only; entity/semantic extraction is built but frozen until Phase 2) |
| Maturity signal | Simple enough to read top to bottom in one sitting | Has its own docs/, ADRs, rules/, quality/ — a project in its own right |

## What this means practically
- **Quick one-off import** (a metn/poem you want live today) → use `athar-archive`'s `pnpm import:epub`. It's simpler, direct, good enough for the Shamela format it targets.
- **Higher-fidelity compile with regression protection** (headings, footnotes, poetry, tables verified against a golden corpus, or a book Athar-Engine has already been tuned against) → compile it in `Athar-Engine` (`compile-canonical.ts` / `compile-all-epubs.ts`), then bring it into athar-archive with **`ingest-new-book.mjs`** (new book) or **`sync-website-bodies.mjs`** (existing book, body-only refresh).
- They are **not interchangeable inputs to the same pipeline** — Athar-Engine's output only overwrites the *body*, never the frontmatter fields (person/topics/etc.) that athar-archive's own importer, `/compose`, or `ingest-new-book.mjs` set.

## Guide: importing an EPUB

**Path A — fast, direct (athar-archive importer):**
```
cd /home/sinkeat/Projects/athar-archive
pnpm import:epub "/home/sinkeat/Projects/books/starterbooks/<file>.epub" --dry-run   # inspect first
pnpm import:epub /home/sinkeat/Projects/books/starterbooks/                          # whole folder, once happy
pnpm validate:content
```

**Path B — via Athar-Engine's compiler, book already has a content file on the site (higher fidelity, regression-checked):**
```
cd /home/sinkeat/Projects/Athar-Engine
# add the book to compile-all-epubs.ts's list, or run compile-canonical.ts directly:
pnpm exec tsx scripts/compile-canonical.ts "<epub path>" canonical-corpus/<folder> <slug>
node scripts/sync-website-bodies.mjs --dry     # preview what would sync into athar-archive
node scripts/sync-website-bodies.mjs           # write it (only touches files that already exist on the site)
```

**Path C — via Athar-Engine's compiler, book is brand new (2026-07-07, `ingest-new-book.mjs`):**
```
cd /home/sinkeat/Projects/Athar-Engine
pnpm exec tsx scripts/compile-canonical.ts "<epub path>" canonical-corpus/<folder> <slug>
node scripts/ingest-new-book.mjs <folder> --slug <slug> --title "<title>" --person <person-slug> \
  --kind مرجع --topics <topic-slug> --authored-year <year> --status published \
  --description "<one-line description>"
```
This is the gap Path B always had (it "only updates files that already exist on the site" —
refuses to touch anything without a content file, by design, to avoid clobbering curated
frontmatter). `ingest-new-book.mjs` writes a brand-new content file with frontmatter built
from the CLI flags, and refuses to run if the slug already exists (use Path B for that case
instead). Proven on 13 books so far (سيرة ابن هشام, then a batch of early-salaf works).

## What I actually did (and got wrong first)
1. First pass, I only looked inside `athar-archive/` and concluded "Athar Engine" was just a marketing name for that repo's own stack — wrong. I hadn't checked for a sibling directory.
2. On correction, found `/home/sinkeat/Projects/Athar-Engine` — a real, separate, actively-developed repo with its own docs/ADRs/quality regression suite and a Pandoc-based compiler pipeline, distinct from athar-archive's lightweight `epub-import.ts`.
3. Verified the wiring between them by reading `sync-website-bodies.mjs` and `integrate-to-website.ts`: Athar-Engine compiles into its own `canonical-corpus/`, then a separate sync step pushes bodies (not frontmatter) into athar-archive.
4. Ran a real dry-run of athar-archive's own importer against one `starterbooks` epub to confirm Path A still works end-to-end (correct slug, author stub, topic guess, valid frontmatter) — did not run Path B since it requires adding the book to `compile-all-epubs.ts`'s hardcoded list or building a canonical-corpus folder first, which is a bigger step than a docs task warranted.

Lesson: don't infer "these must be the same thing" from a shared name — check the filesystem for the literal path the user names before writing docs about it.
