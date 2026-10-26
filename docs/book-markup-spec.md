# Structure markers in book Markdown

Invisible labels that say **what a passage is**, so a later style change reads the label instead of
guessing. They are HTML comments on their own line: the website and the app show nothing, so the
reading view stays clean. The formatting gate ignores them (it strips every `<…>`), so they never
count as text. `scripts/format-verify.ts` rejects a malformed marker.

```
<!-- verse -->

تمسك بحبل الله واتبع الهدى ... ولا تك بدعيًا لعلك تفلح

ولا تكفرن أهل الصلاة وإن عصوا ... فكلهم يعصي وذو العرش يصفح

<!-- /verse -->
```

| Marker | Wraps | Use |
|---|---|---|
| `verse` | consecutive paragraphs that are lines of poetry (`صدر ... عجز`) | every verse paragraph, always. `scripts/wrap-verses.ts <book>` does it with the parser's own verse test |
| `hadith` | one whole narration attributed to the Prophet ﷺ | a numbered entry or standalone paragraph that **is** the narration |
| `athar` | one whole narration attributed to a Companion, Successor or later scholar | same |
| `riwayah` | a narration whose attribution is unclear | when unsure: never guess hadith vs athar |
| `ayah` | a Qur'an verse standing as its own paragraph | only a paragraph that is just the verse (`﴿…﴾ [سورة: N]`) |

## Rules

- Marker lines stand alone, with a blank line before and after. Spelling: `<!-- kind -->` … `<!-- /kind -->`, lower case.
- No nesting, never empty, always closed. A page marker (`<hr class="page-sep">`) may sit inside a block.
- Mark **whole entries only**. A hadith or ayah quoted inside a sentence or a commentary paragraph is not marked.
- Never add visible text for a marker (no titles, labels or captions). They exist only for the machine.
- Markers do not replace the other rules: headings, `(^N)` anchors, bold labels and the `<!-- card -->` pair stay as in `book-formatting-rules.md`.
- Unknown kinds are rejected. A new kind is added in `src/lib/markers.ts` and here first.

Nothing reads the markers yet except the checker. When the style changes, the renderer reads them and no book is edited.
