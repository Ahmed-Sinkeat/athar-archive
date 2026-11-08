# Prompt: format the books added on 2026-11-07

This is the full Phase 2 format (card, headings, verses, quotes, footnote links, import fixes).
The footnotes-only job is a different prompt: `docs/prompt-footnotes-and-intros.md`.
Run it on Sonnet 5.5, medium effort. Paste everything below the line.

---

You are formatting Arabic books in the Athar archive. You work alone, one book at a time, smallest first, until the list is empty or you run out of time. Nobody answers questions while you work. When something is unclear, skip it and write it down.

## Setup

```bash
git fetch origin
git switch -c format/new-2026-11-07 origin/main   # resuming: git switch format/new-2026-11-07
corepack enable && pnpm install --frozen-lockfile  # only tsx is needed; no build
mkdir -p /tmp/work
```

`<WORK>` for a book is `/tmp/work/<id>/`. Reports go in `docs/format-new-2026-11-07/`:
- `REPORT.md`: one entry per book, in the brief's report format.
- `SKIPPED.txt`: each skipped book, with the reason.

## The list

These are the books added in commit `cc751abb`, smallest first. Skip any book that already has a `format(book): <id>` or `fix(book): <id>` commit:

```bash
git diff --name-only --diff-filter=A 989d698c cc751abb -- src/content/book src/content/book-lg | xargs ls -S -r
git log --oneline --grep='(book):' origin/main
```

## Read first

The standard is in these files. This prompt only adds how to work here.

1. `docs/book-formatting-rules.md`: read the whole file.
2. `docs/book-formatting-agent-brief.md`: per-book steps, report format, and "Mistakes reviewers caught". Follow it, except that you commit, as described below.
3. Model commits, a few hundred lines each: `git show c6bc50a8 | head -400` · `git show d21641c7 | head -400` · `git show 15a42301` · `git show 4d44a714 | head -200` · `git show b4b1698a` · `git show 2b25c065`

**These rules are newer than the repo copy, and they win:**
- The card page is called «مقدمة المحقق». Put **only the editor's** front matter in `<!-- card -->…<!-- /card -->`. That covers the title page, edition lines, tahqiq intro and manuscript description. The author's own مقدمة, khutbah and «أما بعد» stay in the flow. Page markers inside the card leave the flow with it.
- Never join paragraphs across a page break. Paragraphs stay as printed. Leave page markers where they are.
- Never invent a heading. A bare title line or a bold-only line that really is a section title becomes `##`. Many books from this import are UNDIVIDED (0–1 headings) or have BOLD-ONLY-HEADINGS, and `./node_modules/.bin/tsx scripts/audit-content.ts` lists them.
- Never write Arabic characters into a regex character class. Use `\u` escapes.
- About 350 of these books skip one page number, usually right after the title page (for example 511 → 513). Do not renumber. Write it in REPORT.md only if text is cut there: quote the words on both sides of the break.

## Per book

1. Follow the brief's steps 1–6. The gate must print `ok`:
   `./node_modules/.bin/tsx scripts/format-verify.ts <BOOK>`
   For an import fix, use `--import-fix --from <WORK>/stageA.md`.
2. Read every anchor and its note (step 3) before you commit. No note may show as plain text.
3. Commit that one file:
   - `git commit -m "format(book): <id>" -- <BOOK>` for markup;
   - `fix(book): <id>` when it contains import fixes.

   Add the report entry.
4. Push the branch every 10 books: `git push -u origin format/new-2026-11-07`.

## Never

- Push to `main`, merge, deploy, or touch R2, D1 or Cloudflare.
- Delete a book, change frontmatter, or change a letter, diacritic, digit or punctuation mark.
- Edit any file other than the book and your report files.

## Done

The branch is pushed with one commit per book. REPORT.md and SKIPPED.txt cover every book on the list.
