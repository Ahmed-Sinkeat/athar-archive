# Adding content (poems, books, annotations)

*النسخة العربية: [adding-content.ar.md](./adding-content.ar.md)*

All material is added from the admin page `/compose`: pick the type, fill the
fields, then press **«نشر إلى GitHub»** (the text is copied and the save page
opens at the right path — paste and Commit). Changing the *interface* text
(menus/titles) has a [separate guide](./editing-text.en.md).

---

## Adding a poem — how صدر / عجز is decided

Write **one بيت (verse) per line**, separating the صدر (first hemistich) from the
عجز (second) with ` --- ` (three dashes with a space on each side), or ` ... `:

```
أَبْدَأُ بِالْحَمْدِ مُصَلِّيًا عَلَى --- مُحَمَّدٍ خَيْرِ نَبِيٍّ أُرْسِلَا
وَذِي مِنَ الأَقْسَامِ تَقْسِيمُ السَّنَدْ --- مِنْهَا الصَّحِيحُ وَهْوَ مَا قَدِ اتَّصَلْ
```

- Text **before** `---` is the **صدر**; text **after** is the **عجز**.
- A line with no separator = a single hemistich (صدر only).
- **Diacritics:** type verses **fully vocalized (مع التشكيل)** — the site has a
  «التشكيل» toggle that strips it on demand, and search matches undiacritized
  automatically.
- Numbering is automatic (١، ٢، ٣…) — don't type numbers.
- New باب (section): a blank line, then `## Section title`, then its verses.
- You can upload a whole text file instead of pasting (the upload button under
  the verses field).

---

## Adding a شرح / إعراب / حاشية / تخريج (annotation)

In `/compose` choose «شرح / حاشية», then:

1. **المتن (target):** pick the poem or book by name.
2. **الموضع (anchor):** `v1` = first verse, `v2` = second… and `p1` = first
   paragraph in books.
3. **الجملة (phrase, optional):** paste the exact words from the متن so the
   annotation marks them and opens from there; leave empty to attach to the
   whole verse/paragraph.
4. **Kind:** شرح / إعراب / حاشية / تخريج.
5. **Annotator** and **source** (optional).

**How readers see it:** tapping a verse (or the highlighted words) opens a
**bottom sheet** with a tab per kind (شرح/إعراب…). If several annotators cover
the same spot they appear as **chips** to switch between, and «previous/next» at
the bottom moves between annotated spots.

---

## Other types

كتاب · درس · سلسلة · عَلَم · فائدة · مقالة · مسألة · صوتية · مختار الأسبوع — all
added the same way in `/compose`, each field with a short hint. Start with status
«مسودة» (draft) and switch to «منشور» (published) when ready.

---

## Book Page Separators & Footnotes

In `book` content, the text flow can be split into pages using HTML tags that define page boundaries and associate page-level footnotes.

1. **Page Separators:** To mark the start of a page, insert a `page-sep` tag. If the page contains footnotes, pass them as a JSON string array inside the `data-notes` attribute (ensure inner double quotes are escaped and the attribute itself uses single quotes):
   ```html
   <hr class="page-sep" data-page="56" data-notes='["في بعض النسخ: \"عنان\".", "إن من قواعد وأصول أهل السنة والجماعة..."]' />
   ```
2. **Inline Footnote References:** To link inline text to a footnote on a specific page, use a `sup` tag referencing the page and the 1-indexed footnote number:
   ```html
   عقال<sup data-fn="1" data-sep-page="56">1</sup>الفتنة
   ```
   *Note: When a reader clicks this inline `<sup>` reference, the system automatically opens the page's bottom sheet and pre-focuses/selects the corresponding footnote entry chip.*

---

## Importing books from EPUB (Bulk Import CLI)

To import multiple books or a large library from EPUB files, you can use the command-line EPUB importer script:

```bash
pnpm import:epub <path-to-folder-or-epub> [options]
```

### Folder Organization & Auto-Taxonomy
To have the importer automatically resolve the correct **Subject** (major category) and **Topic** (sub-category/genre), organize your EPUB files in subdirectories under the `books/` folder (which is ignored by Git).

#### 1. Main Category (Subject) Matching
The importer walks up the directory tree of the imported file and matches folder names (case-insensitive) against [FOLDER_SUBJECT_MAP](file:///home/sinkeat/Projects/athar-archive/scripts/epub-import.ts#L572-L581) to assign the main subject:
- **Aqeedah (العقيدة)**: Place in a folder containing `aqeeda`, `عقيدة`, or `توحيد`.
- **Hadith (الحديث)**: Place in a folder containing `hadith`, `حديث`, `سنن`, or `تخريج`.
- **Fiqh (الفقه)**: Place in a folder containing `fiqh` or `فقه`.
- **Lughah (اللغة العربية)**: Place in a folder containing `lughah`, `language`, `لغة`, or `نحو`.
- **Quran (القرآن الكريم)**: Place in a folder containing `quran`, `قرآن`, or `تفسير`.
- **Tarajim (التراجم والسير)**: Place in a folder containing `tarajim`, `biography`, or `تراجم`.
- **Tarikh (التاريخ)**: Place in a folder containing `tarikh`, `history`, or `تاريخ`.
- **Raqaq (الرقائق والآداب)**: Place in a folder containing `raqaq`, `ethics`, or `رقائق`.

#### 2. Sub-Category (Topic) Matching
Subfolders under your main category (or terms in the EPUB's internal metadata/title) are matched against [SECTION_TOPIC_MAP](file:///home/sinkeat/Projects/athar-archive/scripts/epub-import.ts#L130-L180) to assign specific topics:
- **Fiqh Hanbali**: Folder containing `hanbali`, `فقه حنبلي`, or `حنابلة` (e.g. `books/fiqh/hanbali/`)
- **Fiqh Shafi'i**: Folder containing `shafii`, `shafey`, or `فقه شافعي`
- **Fiqh Maliki**: Folder containing `maliki` or `فقه مالكي`
- **Fiqh Hanafi**: Folder containing `hanafi` or `فقه حنفي`
- **Fiqh Muqaran**: Folder containing `muqaran` or `فقه مقارن`
- **Usul al-Fiqh / General Fiqh**: Folder containing `usul` or `أصول الفقه`
- **Hadith Mustalah**: Folder containing `mustalah` or `مصطلح`
- **Quran Tafsir**: Folder containing `tafsir` or `تفسير`
- **Lughah / Grammar**: Folder containing `nahw` or `نحو`
- **Biography**: Folder containing `biography` or `تراجم`

### Command-line Options
- `--out <dir>`: Output path for markdown files (defaults to `src/content/`).
- `--genre <genre>`: Explicitly set the genre (`قرآن|حديث|تراجم`).
- `--kind <kind>`: Explicitly set the kind (`متن|شرح|مستخلص|معجم`).
- `--merge-volumes`: Merge multiple EPUB files inside the directory into a single multi-volume book.
- `--dry-run`: Run the parser and display the output metadata without writing files to disk.
- `--selftest`: Run internal unit tests on footnote/hadith parsing.

