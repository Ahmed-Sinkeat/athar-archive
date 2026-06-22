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
