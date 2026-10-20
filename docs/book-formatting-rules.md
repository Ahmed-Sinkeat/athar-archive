# Book formatting rules (Phase 2)

The standard for the model formatting pass over the books in `src/content/book{,-lg}/`.
Reference books: `al-iman-al-qasim-ibn-sallam` (footnotes, cut notes, numbered athar),
`al-usul-al-thalathah` (stray bullets, book card) and `al-thfa-al-snya-shrh-mnzwma-abn-aby-dawd-al-hayya`
(verses in a شرح, quotes, bold labels, numbered points, import fixes).
Before/after examples: `docs/book-formatting-examples.html`.

## The one rule

**Change markup, never text.** Edit the book file in place. Git keeps the original, and the
gate below proves the words did not move. The website and the app both read the same file, so
both get the improvement.

The gate:

```bash
./node_modules/.bin/tsx scripts/format-verify.ts src/content/book-lg/<id>.md
```

It compares the file with its committed version (`git show HEAD:`). It rejects any change to:
- frontmatter (byte for byte)
- page markers (number, `data-juz`, order)
- letters, digits and punctuation, in order. Diacritics and whitespace are ignored, and so is
  `...`/`…` (the import's gap between two hemistichs).

It also prints the footnote report before and after. A formatted book should show
**0 dead sups** and **0 notes without a ref**, unless a page really has a note that the
printed text never marks.

## Workflow, one book per commit

1. Read the whole book. Survey each page's notes against the anchors in its text.
2. Apply the marks below. Use a throwaway script for the mechanical parts, and handle the
   pages it can't settle by hand.
3. Run the gate. Then read **every** anchor next to the note it opens. Matching counts can
   still hide a shifted note.
4. Commit with the gate's before/after line and anything you found but did not fix (see
   "Import errors").
5. Import fixes (below) go in a **second** commit on top of the markup commit, checked with
   `format-verify --import-fix`.

## What to mark

### 1. Footnote anchors become `(^N)`

`N` is the note's position in the `data-notes` of the page marker **closing** that page
(a marker ends its page). The app links it to the note, and the website renders it as a
superscript.

| Source | Becomes |
|---|---|
| bare digit after text: `وَكَذَا وَكَذَا" 1.` | `وَكَذَا وَكَذَا" (^1).` |
| glued: `"…دمه" 2وَعَلَى` | `"…دمه" (^2) وَعَلَى` |
| EPUB sup: `مُحلّ<sup data-fn="2" data-sep-page="36">2</sup>بْنِ` | `مُحلّ (^2) بْنِ` |

- One note cited twice (`أخرجهما مسلم…`) means both anchors get the same `N`.
- Put a space before `(^N)`, and after it when a word follows.
- A digit with **no note on its page** stays as it is (e.g. `توكلت على الله 1`).
- These are **not** anchors: verse references `[البقرة:83 و110]`, page references `ص 96`,
  item numbers `9- قال`, and dates.

### 2. Cut notes are merged back

The old EPUB import split a note wherever it contained `(N)`, so every later note on that page
shifts by one. A fragment that starts mid-sentence belongs to the note before it. Merge it
inside `data-notes` with a single space, and never reword it.

```
["…فراجع تعليقنا عليه رقم:","و20 و120.","الأصل: \"وذلك\"."]
→ ["…فراجع تعليقنا عليه رقم: و20 و120.","الأصل: \"وذلك\"."]
```

Tells: the fragment starts with `و` plus a digit, with `من "…"`, or with a word that only
finishes the previous note. The number that was cut out is lost. Leave it lost; don't
guess it back.

### 3. Numbered items start their own paragraph

`… أَفَلَا وكلتَ الْأُولَى؟ (^1)". 10- قَالَ أَبُو عُبَيْدٍ: …`: break before `N- قال`,
`N ـ قال`, `N- حدثنا`, whatever comes before it. The numbers should then run in order. A gap
in the source (26 → 28) stays a gap.

### 4. Headings

- Only existing text becomes a heading. Never write a heading that isn't in the book.
- `##` for باب/كتاب/فصل, and `###` for sections within one (`### الأصل الأول`).
- A chapter number on its own line before a heading goes into it: `4-` + `## بَابُ …`
  becomes `## 4- بَابُ …`.
- A heading the import put in the middle of a sentence (`… نتحدث عن` / `## مصدر التلقي عندهم،` /
  `فإن …`) goes back into its sentence. If the book's own numbered line for that section sits just
  before it (`3- مصدر التلقِّي عندهم:`), that line becomes the heading. Only `##` and line breaks move.
- An import `##` left in the middle of a paragraph (`… الفتن (^1) ## أولاً: مفهوم الاعتصام: لا شك …`)
  is a real heading: put it on its own line with a blank line before and after, ending where the
  heading's own text ends (`## أولاً: مفهوم الاعتصام:`).

### 5. Bullets that aren't lists

Remove a leading `- ` from paragraphs that only look like a list
(`- اعلم رحمك الله…`, `- المرتبة الثانية…`). Keep real lists, such as the manuscripts list.

### 6. Page markers

Never add, remove, move or renumber a `<hr class="page-sep">` (except as an import fix, below). Each one sits on its own line
with a blank line before it. The only edit allowed inside one is rule 2.

### 7. Verses inside prose

A شرح quotes its matn as `(صدر... عجز)` or `N (صدر)... (عجز)` inside a paragraph. Put each verse
in its own paragraph with a space on both sides of the `...`:

```
(تمسك بحبل الله واتبع الهدى ... ولا تك بدعيًا لعلك تفلح ُ)

26 (ولا تكفرن أهل الصلاة وإن عصوا) ... (فكلهم يعصي وذو العرش يصفح)
```

The parser makes it a verse block and drops the wrapping brackets for display; a verse number
stays. A half-verse quoted in the commentary (`قوله: (وقل إنما الإيمان... )`) is not a verse.

### 8. Quotes

`> ` only where the book lists sayings one after another (`ـ قال فلان: "…"`, one per scholar).
A quote inside a sentence stays inline.

### 9. Bold labels and numbered points

- A label the book already has (`ولادته:`, `الأول:`, `الأمر الثاني:`) starts its own paragraph,
  in bold: `**ولادته:** ولد…`. Never bold for emphasis you chose.
- A numbered point run into a sentence (`على أمرين: 1- … 2- …`) starts its own paragraph.
  Not a range like `(النجم: 3-4)`.
- A heading glued to its first paragraph (`ترجمة موجزة…<sup>1</sup>اسمه…`) becomes `##`.

### 10. Book card

The title page and the editor's front matter (author line, edition, manuscripts used) go between
`<!-- card -->` and `<!-- /card -->`. The app moves those lines into ⋮ → «بطاقة الكتاب»; page
markers inside stay in the flow. The website still shows the lines (the comments are invisible).

## Import errors

Mistakes made by the import, not the book. Fix them in a separate commit after the markup
commit, and run `format-verify --import-fix`: page numbers may change, of two consecutive markers
with one number the first may go, and text may move, but no character may be added or lost
(anchor digits excepted, since merging pages renumbers them). The gate prints the page list
before and after.

| Error | Fix |
|---|---|
| One printed page with two markers (the import split it at a new باب) | drop the first marker, move its notes to the second, shift that page's later `(^N)` |
| A marker with a wrong number (`7, 11, 9, 10, 11`) | renumber it (`8`) |
| Verse halves swapped across columns | move the halves back |

**Report, don't fix** (needs the original source): text the import lost (كتاب الإيمان p57→58
lost ~3 lines, so p58 opens with `74] ,`), a book that stops early (كتاب الإيمان ends at p99; the
مخرجا edition goes on to `ذِكْرُ الْأَصْنَافِ الْخَمْسَةِ`), hadith numbers the old import cut out of
notes (`أخرجه مسلم برقم ⟨…⟩`), and a note with no number in the text (التحفة p43). Never fill
a gap from a different edition without asking.

## Never

- Change a letter, digit, punctuation mark or diacritic, even an obvious typo. Those are
  تحقيق decisions; the editor's notes record them (`الأصل: …`).
- Add, remove or renumber a page marker outside an import fix.
- Touch frontmatter. That includes `kind`, which is literal and never auto-classified.
- Rewrite or reorder notes.

## Not supported yet (needs code first)

- **Joining a sentence split by a page break** is done by the app parser, not by editing the book:
  a paragraph that doesn't end a sentence carries on into the next page's first paragraph
  (`joinAcrossPages` in `src/lib/app-v2/parser.ts`). Leave page markers where they are. The website
  still breaks there.
- **Removing printed page-number lines** (`-46`). That removes text, so the gate rejects it
  until it has an explicit allowlist.
- **Verse styling on the website.** The site shows each verse as its own paragraph, brackets
  and `...` included.
- Search aids, links between books, and chapter summaries: not designed yet.

## Effect on saved highlights

Block IDs are kept when only the block kind changes (`li`→`p`, `p`→`h2`). Splitting a
paragraph gives the new pieces new IDs. A highlight on a split paragraph is re-attached by
fingerprint and quote. If that fails it is kept as **Orphaned**, never deleted.
