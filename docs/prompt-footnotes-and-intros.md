# Prompt: fix footnotes across the library + hide مقدمة المحقق

Paste everything below into a new session opened in /home/sinkeat/Projects/athar-archive.

---

Two goals, library-wide, in src/content/book and src/content/book-lg:

1. Every footnote is marked as a footnote. Rule, in my words: "all footnotes to not be part of the text and apear so even if we combined them we should not leave footnotes without marking it as one."
2. Every book opens on the author's words. The editor's front matter (the tahqiq intro, the manuscript description, the editor's own introduction) goes inside the book card, so it shows only under ⋮ → «مقدمة المحقق».

## How the format works (read before you change anything)

- The parser is src/lib/app-v2/parser.ts. The rules are in docs/book-formatting-rules.md. Rule 10 covers the book card.
- **Notes:** each page marker `<hr class="page-sep" data-page=".." data-juz=".." data-notes='[...]' />` carries that page's notes as a JSON array. A ref `(^k)` in the page body points at note k, counting from 1.
- **Run-on notes:** a note that starts with `= ` continues the last note on the page before. The parser joins it there.
- **The card:** front matter goes between `<!-- card -->` and `<!-- /card -->`. The card closes right after a page marker. Today 443 of 1,929 books have a card. About 340 more mention editor-intro words (مقدمة التحقيق, وصف النسخ, عملي في التحقيق, ترجمة المؤلف written by the editor…) but have no card.

## The work list

- **docs/footnotes-todo.tsv** lists the 346 books with problems, worst first. It has columns for notes, refs with no note, notes with no ref, and epub_ids.
  - 276 of these books are matched to the epub library at ~/Projects/books/epub/<id>.epub. All titles are in ~/Projects/books/titles.tsv.
  - Examples: الفصل للوصل = 10842, النبوات = 11817, ميزان الاعتدال = 1692. شرح عمدة الفقه has several editions, so pick the one whose pages match.
- **Library totals:** 21,279 refs with no note and 45,021 notes with no ref.
- **docs/footnote-tools/allnotes.ts** recounts the totals: `./node_modules/.bin/tsx docs/footnote-tools/allnotes.ts`. It prints a `BAD file notes dead unref` line per book. Rerun it after each book. The number must go down and must never go up.

## Method: proven on تفسير مقاتل, which went from 95 problems to 0

The muq* scripts are in docs/footnote-tools. Copy their pattern for each book; don't run them as they are, because each one names its own book.

1. Cache the epub's pages and notes: muqepub.py → a pickle keyed by (juz, page).
2. Align each of our pages with its epub page by matching letters only (strip everything outside the Arabic letter range, written with \u escapes). The epub's `«n»` marks show where each ref goes.
3. **Dead ref** (a `(^k)` with no note k): restore the note from the epub, or fix the ref number if the import renumbered it.
4. **Note with no ref:**
   - If the epub has the mark, place the ref after the same 10 letters.
   - Otherwise, look for a mark the import cut down to a lone `»` that closes nothing. That spot is where the ref goes (muqorphan.py).
   - If neither works, read the page and fix it by hand. muqhand.py shows the kinds of fix: join the pieces of one note, mark a run-on with `= `, turn a leftover "2»" into the ref, or move a summary to its true page.
5. **Notes printed in the body** (the import left them as text at the page's end, e.g. lines starting "(1) …"): move them into data-notes and put refs in. A note must never be shown as plain text. The 193 Shamela books converted on 2026-11-07 are an example of this.

Do the worst books first: مصباح الظلام, النبوات, الفصل للوصل, الانتصارات الإسلامية, الانتصار في الرد على المعتزلة, ميزان الاعتدال, تهذيب سنن أبي داود, and so on down the tsv. If a book has no epub match, fix what the page itself proves and list the rest.

## Intro hiding

- For each of the ~340 candidates, find where the author's text starts. Signs:
  - the author's own خطبة: «بسم الله… الحمد لله…» after the editor's section;
  - a heading such as «مقدمة المؤلف» or «النص المحقق»;
  - the epub's own split.
- Wrap everything before that point in the card, closing right after a page marker (rule 10).
- Our «مقدمة» means the محقق's intro only. The author's own مقدمة stays in the text.
- When you're unsure where the author starts, don't guess. List the book for me.

## Hard rules

- **No character may be lost.**
  - Before and after each book, compare the multiset of Arabic letters, ignoring tags, data-notes JSON order and whitespace.
  - The only allowed change is text moving between body and notes, or into the card.
  - Show the per-book letter diff.
- Run scripts/format-verify.ts and `npm run audit:content` after each batch.
- **Never paste Arabic character classes into code.** Write them with \u escapes (e.g. `[ء-ي]`), because pasted Arabic flips the code's direction.
- Use Athar-Engine for any re-import. Never use scripts/epub-import.ts.
- **No commit, push, R2 publish or Worker deploy.** Only local files. I'll review the diff.
- Ask me before deleting a book.
- Never join text after a page's last note.
- Log each book in docs/overnight-2026-11-07.md, one line each: book, before→after counts, and what you couldn't fix with the reason.
- Run tools with `./node_modules/.bin/tsx`.
