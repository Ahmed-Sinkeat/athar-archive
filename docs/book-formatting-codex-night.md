# Codex overnight prompt: format the remaining books

Codex works alone in its own worktree and branch, one book per commit, so the morning review can
keep or drop each book separately. Run the setup yourself, then launch Codex and paste everything
below the line.

```bash
cd /home/sinkeat/Projects/athar-archive
git worktree add -b format/codex-night /home/sinkeat/Projects/athar-format HEAD
ln -s /home/sinkeat/Projects/athar-archive/node_modules /home/sinkeat/Projects/athar-format/node_modules
mkdir -p /home/sinkeat/Projects/athar-format-work
codex -C /home/sinkeat/Projects/athar-format -s workspace-write -a never --add-dir /home/sinkeat/Projects/athar-archive/.git --add-dir /home/sinkeat/Projects/athar-format-work
```

Books with uncommitted edits in the main checkout are skipped (54 of the 139 left on
2026-10-22). Commit those edits before the setup to let Codex do them too.

---

You are formatting Arabic books in the Athar archive overnight. Nobody will answer questions
until morning: when something is unclear, skip it and write it down. Work one book at a time,
smallest first, until the list is empty or you run out of time.

## Read first (they are the standard; this prompt only adds how to work tonight)

Read these two from the main checkout, `/home/sinkeat/Projects/athar-archive/docs/` (it has
newer edits than your worktree's copy):

1. `book-formatting-rules.md`: the whole file. The rules for every mark.
2. `book-formatting-agent-brief.md`: the per-book steps, the report format, and
   "Mistakes reviewers caught". Follow it, except where this prompt overrides it (git and paths).
3. Model commits, read a few hundred lines of each:
   ```bash
   git show c6bc50a8 | head -400   # anchors, merged cut notes, numbered athar
   git show d21641c7 | head -400   # verses, quotes, bold labels, numbered points
   git show 15a42301               # book card, stray bullets
   git show 4d44a714 | head -200   # bracket titles into headings, ## + ###
   git show b4b1698a               # import fix: duplicate page markers
   ```

## Where you work

You are in `/home/sinkeat/Projects/athar-format`, a git worktree on branch `format/codex-night`
(`node_modules` is a symlink, already set up). Work only here. The main checkout
(`/home/sinkeat/Projects/athar-archive`) has someone else's uncommitted work: read it, never edit it.
`<WORK>` for a book is `/home/sinkeat/Projects/athar-format-work/<id>/`. Don't use `/tmp`.

## The list

Run this in the worktree before **each** book (it skips books already done, including your own
commits, and books that have uncommitted edits in the main checkout):

```bash
cd /home/sinkeat/Projects/athar-format
done=$(git log --format=%s --grep='(book):' | sed -E 's/^[a-z]+\(book\): ([^ ]+).*/\1/' | sort -u)
dirty=$(git -C /home/sinkeat/Projects/athar-archive status --porcelain src/content | awk '{print $2}')
skipped=$(cat /home/sinkeat/Projects/athar-format-work/SKIPPED.txt 2>/dev/null)
grep -v '^#' docs/book-formatting-queue.tsv | cut -f1 | while read p; do
  id=$(basename "$p" .md)
  echo "$done" | grep -qx "$id" || echo "$dirty" | grep -qx "$p" || echo "$skipped" | grep -qx "$id" || echo "$p"
done | head -1
```

The first line printed is your next `<BOOK>`.

## Per book

1. Follow the brief's steps 1–5 for `<BOOK>` (survey, markup, strict gate, read every anchor/note
   pair, import fixes only if any). Gate command, from the worktree:
   `./node_modules/.bin/tsx scripts/format-verify.ts <BOOK>`
2. **Commit the markup** (strict gate `ok` after your last edit):
   ```bash
   git add <BOOK> && git commit -m "format(book): <id> — <what you did, short>" -m "<full report, brief's format>"
   ```
3. **Import fixes, if any**, go in a second commit. Edit the file, then gate against the commit
   you just made: `./node_modules/.bin/tsx scripts/format-verify.ts --import-fix <BOOK>`, then
   `git commit -am "fix(book): <id> — <page: what>"`. Pure moves/renumbers/duplicate markers only.
4. Append one line to `/home/sinkeat/Projects/athar-format-work/NIGHT.md`:
   `| <id> | committed / skipped | anchors N, headings N, … | import fixes | REPORT ONLY items |`
5. `git status --short` must be empty before the next book.

**Give up on a book** when the strict gate still fails after three honest attempts, or the notes
and anchors don't line up for most of the book, or you have spent more than about 45 minutes on
it: `git restore <BOOK>`, add its id to `/home/sinkeat/Projects/athar-format-work/SKIPPED.txt`,
write why in `NIGHT.md`, go to the next book. A partly formatted book is fine when the parts you
left are named in the report (brief: "When to stop early"); a book with an unchecked guess is not.

## Hard rules for tonight

- Change markup, never text. The gate is the judge; never edit `scripts/format-verify.ts`,
  `src/lib/`, or anything except the one `<BOOK>` file. Never touch frontmatter (including `kind`).
- Import errors matter most. Lost text, a book that stops early, cut hadith numbers, a note with
  no anchor: **report only**, with proof (quote the words on both sides). Never fill a gap,
  never use another edition's text.
- Git: only `add`/`commit` of the one book file, and `git restore <BOOK>` to abandon one, on
  branch `format/codex-night` in your worktree. Never push, merge, rebase, reset, amend, stash,
  switch branches, or touch the main checkout. Never delete a worktree.
- One process at a time: the laptop has 7.6 GB RAM. No parallel books, no `pnpm build`,
  no `pnpm app:gen`, no dev server. Only `./node_modules/.bin/tsx` scratch scripts
  (`.mts`, kept in `<WORK>`).
- No network. Don't kill processes by name or pattern.
- Read big Arabic files in slices; use scripts for mechanical edits, then read what they changed.

## When you stop

Finish the book you are on (commit it or restore it), then write the top of `NIGHT.md`:
books committed, books skipped (and why), and every REPORT ONLY item grouped by book. Leave the
worktree and branch as they are for review.
