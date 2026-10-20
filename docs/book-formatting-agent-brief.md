# Brief: format one book (Phase 2)

The brief each formatting agent gets, with `<BOOK>` (a path from `docs/book-formatting-queue.tsv`)
and `<WORK>` (its own scratch folder). A reviewer checks every book before it is committed, so
report honestly: a skipped page you name is fine, a guess you hide is not.

## Your job

Format **one** book file, `<BOOK>`, by the rules in `docs/book-formatting-rules.md`. Read that whole
file first. It is the standard; this brief only adds how to work.

Model books, already done (read a few hundred lines of each diff to see the style):

```bash
git show c6bc50a8 | head -400   # كتاب الإيمان: anchors, merged cut notes, numbered athar
git show d21641c7 | head -400   # التحفة السنية: verses, quotes, bold labels, numbered points
git show 15a42301               # الأصول الثلاثة: book card, stray bullets
```

## Hard limits

- Edit **only** `<BOOK>`. Scripts and copies go in `<WORK>`, never in the repo.
- Git is read-only for you: `git show`, `git diff`, `git log` only. Never `commit`, `add`,
  `checkout`, `restore`, `stash` or `reset`; other agents are working in the same checkout.
- Never change a letter, digit, punctuation mark or diacritic, the frontmatter, or a page
  marker (except an import fix, below). The gate enforces this; don't argue with it.
- No network. Don't kill processes by name or pattern.
- Run tools with `./node_modules/.bin/tsx` (not pnpm/npx). Scratch scripts need the `.mts` extension.
- Arabic files are large: read with offset/limit, and use scripts for anything mechanical.

## Steps

1. **Survey.** Use a script to list each page's note count (from `data-notes`) next to the anchors
   in its text, and which markup the book already has (`[^x]`, `(^N)`, `<sup>`, headings, bullets).
   If the book's notes are already `[^x]` or `(^N)` and linked, there is less to do. Check them, don't redo them.
2. **Markup.** Apply rules 1–10. Then run the strict gate until it says `ok`:
   ```bash
   ./node_modules/.bin/tsx scripts/format-verify.ts <BOOK>
   ```
   Then `cp <BOOK> <WORK>/stageA.md`.
3. **Check every anchor.** Print every `(^N)` with ~8 words before it and note N of that page
   (a script), and **read every pair**. Matching counts can hide a shifted note. Fix what is
   wrong, rerun the gate, refresh `stageA.md`.
4. **Import fixes, only if there are any** (rules → "Import errors" table: split page, wrong page
   number, swapped verse halves). Edit `<BOOK>`, then:
   ```bash
   ./node_modules/.bin/tsx scripts/format-verify.ts --import-fix --from <WORK>/stageA.md <BOOK>
   ```
   Lost text, a book that stops early, or cut hadith numbers are **report only**, with proof
   (quote the words on both sides of the gap). Never fill a gap.
   A note that is only punctuation (`"."`, `"،"`) is folded into the note before it by the parser,
   so every later `(^N)` on that page must count it as gone. Don't anchor to it; report it as lost.
5. **Required before you report** (a reviewer reruns both and rejects a report that skipped them):
   rerun the gate(s) **after your last edit**, and print and read the anchor/note pairs from step 3.
   Then check each claim in your report against the file (`grep` it): a paragraph you say you kept
   joined must still be joined. A heading line ends its paragraph, so when you rejoin one into its
   sentence, keep a blank line after the joined line; otherwise the next paragraph (the next hadith)
   merges into it. Check the gate's block count: it should drop by about one per heading you rejoined.
6. **Stop** with `<BOOK>` in its final state and `stageA.md` = the markup-only stage. If there
   were no import fixes, the two are identical.

## Mistakes reviewers caught

- Footnote anchors may use Arabic-Indic digits (`(^٣)`): survey for those as well as ASCII.
- A `- …` line ending in `-.` or ` -` is a dash aside (جملة معترضة) in the text, not a bullet: leave it.
- Never move a connective (`و`, `ثم`) into a heading; bold the label after it instead (`و**الفرد النسبي:** …`).
- Two `<!-- card -->` pairs: the second is ignored and stays in the reading flow. Use one.
- The strict gate rejects any reorder of text, even two swapped verse halves: that is an import
  fix (`--import-fix`), or report it.

## When to stop early

Something the rules don't cover: leave it as it is and report it. Don't invent a rule. If a part of
the book can't be settled (e.g. notes and anchors don't line up for a run of pages), leave those
pages unlinked and name them.

## Report (your final message, and also `<WORK>/report.md`)

```
BOOK: <path>
GATE strict: <the ok line + before/after lines>
GATE import-fix: <ok line, or "none">
DONE: anchors N (pages …), cut notes merged N, headings N, verses N, quotes N, bold labels N,
      numbered points N, bullets removed N, card yes/no
IMPORT FIXES: <page: what> or none
REPORT ONLY: <page: what is wrong + proof> or none
UNSURE: <page: decision you were not sure of> or none
SKIPPED: <pages/parts left as they are and why> or none
```

## How the queue was built

`docs/book-formatting-queue.tsv`: every `src/content/book{,-lg}/*.md` of 10–300 KB with at least
10 footnotes in any format (`data-notes`, `[^x]:`, `(^N)`, `<sup>`), smallest first, excluding the
three model books.
