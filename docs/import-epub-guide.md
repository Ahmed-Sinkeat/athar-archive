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

## What this means practically — Athar-Engine is the default (2026-07-16)

**Default to Path B/C (Athar-Engine) for every new book, not just the ones already in its corpus.** This reverses the old guidance below, after a concrete incident: importing ~80 books in one session via `epub-import.ts` produced real defects — missing headings on a whole second EPUB template (`<h2>`/`<p>` tags instead of Shamela's `<span class="title">`), fused paragraphs, leaked page-footer text, and a duplicate of **فتح المجيد شرح كتاب التوحيد** that Athar-Engine had *already compiled* (`canonical-corpus/16-fath-al-majid`) — its version had richer metadata (full author lineage/dates), correctly separated embedded poetry into verse lines, and real linked footnotes; the quick importer's version flattened all of that. Same story every time this has come up before.

Athar-Engine's `compile-canonical.ts` **does work on books outside its original corpus** — tested live on two fresh books, not just the existing 30 — but had two silent-failure traps that likely explain why it kept getting skipped: it required `<golden_dir>/output/` and `.../benchmarks/` to already exist (bare `ENOENT` otherwise), and silently defaulted an *omitted* slug argument to a hardcoded leftover test value (`al-arbaeen-al-nawawiyyah-epub`) instead of erroring. Both fixed 2026-07-16 (`compile-canonical.ts` now auto-creates those two dirs and requires the slug explicitly) — if you hit either failure again, the fix didn't make it into this checkout, not a reason to fall back to Path A.

**Only reach for `pnpm import:epub` (Path A) when Athar-Engine genuinely can't be used** — e.g. a format Pandoc can't parse at all — not merely because it's fewer steps. Path A is still fine for quick drafts you plan to re-compile properly later, but don't let that become the default it's kept becoming.

- Before compiling anything, `ls canonical-corpus/` in Athar-Engine — the book may already be done.
- They are **not interchangeable inputs to the same pipeline** — Athar-Engine's output only overwrites the *body*, never the frontmatter fields (person/topics/etc.) that athar-archive's own importer, `/compose`, or `ingest-new-book.mjs` set.

## Guide: importing an EPUB

**Path C — Athar-Engine, book is brand new (default — start here):**
```
cd /home/sinkeat/Projects/Athar-Engine
ls canonical-corpus/                                                    # check it isn't already compiled
pnpm exec tsx scripts/compile-canonical.ts "<epub path>" canonical-corpus/<n>-<name> <slug>
# <golden_dir> and its output/+benchmarks/ subdirs are auto-created — nothing to mkdir first
node scripts/ingest-new-book.mjs <n>-<name> --slug <slug> --title "<title>" --person <person-slug> \
  --kind مرجع --topics <topic-slug> --authored-year <year> --status published \
  --description "<one-line description>"
cd /home/sinkeat/Projects/athar-archive && pnpm validate:content
```
`ingest-new-book.mjs` writes a brand-new content file with frontmatter built from the CLI
flags, and refuses to run if the slug already exists. It always writes into `src/content/book/`
— move to `book-lg/` yourself afterward if the compiled `.md` is ≥100KB (CMS folder-size rule).
Proven on 13+ books (سيرة ابن هشام, a batch of early-salaf works, plus the size/format-agnostic
test run that produced the auto-mkdir fix above).

**Path B — Athar-Engine, book already has a content file on the site (refresh only):**
```
cd /home/sinkeat/Projects/Athar-Engine
pnpm exec tsx scripts/compile-canonical.ts "<epub path>" canonical-corpus/<n>-<name> <slug>
node scripts/sync-website-bodies.mjs --dry     # preview what would sync into athar-archive
node scripts/sync-website-bodies.mjs           # write it (only touches files that already exist on the site)
```

**Path A — athar-archive's own importer (fallback only, not the default):**
```
cd /home/sinkeat/Projects/athar-archive
pnpm import:epub "/home/sinkeat/Projects/books/starterbooks/<file>.epub" --dry-run   # inspect first
pnpm import:epub /home/sinkeat/Projects/books/starterbooks/                          # whole folder, once happy
pnpm validate:content
```
Reach for this only when Athar-Engine genuinely can't handle the source (e.g. a format Pandoc
can't parse) — not just because it's fewer steps. It's regex-based against one specific Shamela
HTML template; anything else silently drops headings/paragraph boundaries/footers rather than
erroring, which is exactly what happened across ~80 books in one sitting before this note
existed. If you use it anyway, budget time to manually verify headings survived, paragraphs
didn't fuse, and no page-footer text leaked into the body — don't assume it "just worked."

## What I actually did (and got wrong first)
1. First pass, I only looked inside `athar-archive/` and concluded "Athar Engine" was just a marketing name for that repo's own stack — wrong. I hadn't checked for a sibling directory.
2. On correction, found `/home/sinkeat/Projects/Athar-Engine` — a real, separate, actively-developed repo with its own docs/ADRs/quality regression suite and a Pandoc-based compiler pipeline, distinct from athar-archive's lightweight `epub-import.ts`.
3. Verified the wiring between them by reading `sync-website-bodies.mjs` and `integrate-to-website.ts`: Athar-Engine compiles into its own `canonical-corpus/`, then a separate sync step pushes bodies (not frontmatter) into athar-archive.
4. Ran a real dry-run of athar-archive's own importer against one `starterbooks` epub to confirm Path A still works end-to-end (correct slug, author stub, topic guess, valid frontmatter) — did not run Path B since it requires adding the book to `compile-all-epubs.ts`'s hardcoded list or building a canonical-corpus folder first, which is a bigger step than a docs task warranted.

Lesson: don't infer "these must be the same thing" from a shared name — check the filesystem for the literal path the user names before writing docs about it.
