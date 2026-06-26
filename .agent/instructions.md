# Athar Archive — Agent Instructions

> This file tells you how to think when working on this codebase.
> Read it before writing any code. It exists because previous agents
> repeatedly patched symptoms without tracing root causes, wasting
> the maintainer's time across 5+ sessions on the same bugs.

---

## Rule 0: Trace the Data, Not the Symptom

When a bug is reported — especially anything involving Arabic text rendering,
footnotes (حاشية), or broken display — **do not start by editing the template
or CSS.** Start by answering:

1. **Where does this data enter?** (Markdown file in `src/content/`)
2. **What transforms touch it?** (parsers in `src/lib/`, sanitize pipeline,
   Astro frontmatter processing)
3. **How many places consume it?** (templates, breadcrumbs, `<title>`, TOC,
   navigation, slugs)

If a piece of data flows through 6 render surfaces, a fix at surface #3
leaves 5 others broken. Fix at the source.

### Concrete example (heading footnotes)

The content files contain headings like:
```markdown
## أبو عبد الله محمد بن الفخار<sup data-fn="2" data-sep-page="42">2</sup>(ت 754 هـ):
```

This raw `<sup>` tag flows through:
```
Markdown → splitChapters() → finalizeChapter() → chapter.title
  → <title> tag (browser tab)
  → Breadcrumbs component
  → sidebar TOC links
  → mobile TOC links
  → prev/next navigation
  → reader <h1> heading
  → book landing page chapter list
```

**Wrong approach:** fix the `<h1>` rendering only.
**Right approach:** strip HTML at `finalizeChapter()`, store raw in `rawTitle`
for the one place that needs it (the reader `<h1>`).

**Verification grep:** `grep -c '^## .*<sup' src/content/book/*.md` — returns
1,000+ matches. If you didn't run this, you didn't audit the data.

---

## Rule 1: Grep Before You Code

Before claiming anything is "solved" or "the only case," run these:

```bash
# How many headings contain HTML tags?
grep -rc '^## .*<' src/content/book/ | grep -v ':0$' | wc -l

# How many inline footnotes exist across all books?
grep -rc 'data-fn=' src/content/book/ | grep -v ':0$' | wc -l

# How many page separators carry notes?
grep -rc 'data-notes=' src/content/book/ | grep -v ':0$' | wc -l
```

If your fix handles one file but the pattern appears in 600 files,
your fix is incomplete.

---

## Rule 2: The Rendering Pipeline

Content goes through this pipeline. Know which stage you're touching:

```
1. Markdown file (src/content/book/*.md)
   ↓
2. parseBook() / splitChapters()  [src/lib/chapters.ts]
   - Extracts chapters from ## headings
   - chapter.title = clean text (HTML stripped)
   - chapter.rawTitle = original if it had HTML
   - chapter.content = body text
   ↓
3. markdownToSafeHtml()  [src/lib/sanitize.ts]
   - remark-parse → remark-rehype → rehype-raw → rehype-sanitize
   - rehypeSentenceBreaks: inserts <br> between Arabic sentences
   - rehypeArabicTokens: wraps ﴿آيات﴾ in tok-ayah, «quotes» in tok-quote
   - rehypeHeadingIds: stamps slug IDs on headings
   ↓
4. Page-sep processing  [chapter.astro, lines 116-140]
   - Converts <hr class="page-sep"> to <div>
   - Merges DB hashiya annotations into page-sep data-notes
   ↓
5. Footnote stamping  [chapter.astro, lines 142-179]
   - Builds pnMap from page-sep notes
   - Stamps data-note on every <sup data-fn> in the body
   - Also stamps rawTitle footnotes via processedTitleHtml
   ↓
6. Template rendering  [chapter.astro, lines 180-277]
   - <title>: uses chapter.title (clean text)
   - Breadcrumbs: uses chapter.title (clean text)
   - <h1>: uses processedTitleHtml || chapter.title (HTML with working footnotes)
   - TOC sidebar: uses allChapters[].title (clean text)
   - prev/next nav: uses prev.title / next.title (clean text)
```

---

## Rule 3: Arabic Text Tokens

The sanitize pipeline wraps text patterns in colored spans:

| Pattern | Class | Color | Purpose |
|---|---|---|---|
| `﴿…﴾` | `tok-ayah` | Red | Quranic verses |
| `«…»` `"…"` `"…"` | `tok-quote` | **Disabled** | Was blue, looked like links. Currently commented out in CSS. |
| `(…)` | `tok-paren` | Green | Parenthetical notes |

**Decision made:** `tok-quote` color was disabled because blue citations
in Arabic scholarly text look like hyperlinks and confuse readers. If you
want to re-enable quote distinction, use something subtle (opacity, weight)
— never saturated blue.

**If you want to fully remove tok-quote:** delete the wrapping from
`splitTokens()` in `sanitize.ts` and remove the CSS variables. Don't leave
dead code.

---

## Rule 4: Ponytail Principles

This project follows ponytail principles. Before adding code, ask:

- **delete:** Is this dead code or a speculative feature? Remove it.
- **stdlib:** Am I reinventing something the platform already does? Use the platform.
- **yagni:** Does this abstraction have more than one consumer? If not, inline it.
- **shrink:** Can the same logic be expressed in fewer lines? Do it.

Specific patterns to watch:
- Don't add `as any` casts when the type already has the property.
- Don't duplicate regex logic — extract a helper if used in 2+ places.
- Don't comment out CSS rules — delete them or keep them.
- Don't leave scratch/debug files in the repo root.

---

## Rule 5: Verification Checklist

Before saying "done," run all of these:

```bash
pnpm test                # 84 unit tests
pnpm validate:content    # 1082 content entries cross-linked
pnpm build               # full Astro + Cloudflare build
```

All three must pass. The build will surface Astro template errors that
tests won't catch. The content validator will catch broken anchors and
references that the build won't.

---

## Rule 6: What NOT to Do

These are actual mistakes from previous sessions. Don't repeat them.

1. **Don't claim "solved" without testing the exact file the user showed.**
   If the user showed الاعتصام للشاطبي, test that specific book's chapters.

2. **Don't write a methodology document congratulating yourself.** Write
   instructions that help the next person avoid your mistakes.

3. **Don't add dependencies without explaining why.** Check `git diff package.json`
   before committing — accidental dependency additions inflate the lock file.

4. **Don't modify `scratch.ts` or leave debug files.** If you need scratch
   work, put it in a temp file and delete it when done.

5. **Don't patch one render surface when data flows through six.** Count the
   consumers first.

---

## Architecture Quick Reference

| Path | Purpose |
|---|---|
| `src/content/book/*.md` | Book content (Markdown with embedded HTML footnotes) |
| `src/lib/chapters.ts` | Splits books into chapters, extracts TOC, generates slugs |
| `src/lib/sanitize.ts` | Markdown → safe HTML pipeline (remark/rehype) |
| `src/lib/chunk.ts` | Decides if a book needs chunking (word/chapter thresholds) |
| `src/lib/site.ts` | Knowledge graph, annotation loading, person name map |
| `src/pages/book/[slug].astro` | Book landing page (static, prerendered) |
| `src/pages/book/[slug]/[chapter].astro` | Chapter reader (SSR, edge-cached) |
| `src/scripts/reader.ts` | Client-side: footnote popovers, annotation sheets, search |
| `src/styles/global.css` | All styles including Arabic token colors, prose layout |
| `ahlalathar.config.ts` | Thresholds for chunking, configurable without rebuild |

---

## The Content Format

Books contain inline footnotes as raw HTML inside Markdown:
```html
<sup data-fn="1" data-sep-page="42">1</sup>
```

Page boundaries are marked with:
```html
<hr class="page-sep" data-page="42" data-notes='["note 1","note 2"]' />
```

The `data-notes` array on page-seps carries the actual footnote text.
The `data-sep-page` on each `<sup>` points to which page-sep holds its note.
The server stamps `data-note="..."` on each `<sup>` so the client can render
popovers without DOM traversal.

Headings (`##`) may contain `<sup>` tags. The parser strips them from
`chapter.title` and preserves the original in `chapter.rawTitle`.
