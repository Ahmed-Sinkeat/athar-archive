# Overnight work — 2026-11-07

The user is asleep. The work is resumed every 15 min by a cron job. Mark each item `[x]` when it's done and verified (compile/test). Record findings and anything skipped.
Rules: no commit/push, no R2 publish, no Worker deploy. Release build + phone install only at the very end (feedback_test-install-per-batch).

## 0. Hunt for problems on the phone first
- [x] adb: logcat crashes/ANR, cold-start time, meminfo, the downloaded books on reopen

## 1. Remove what isn't finished
- [x] remove تسميع from the UI (the code stays inactive or is deleted) + any other unfinished stubs/sample data

## 2. Library
- [x] one list engine for books/المتون/poetry/articles/issues/scholars (fast scroller, the same search as the main search tab)
- [x] list search = the same matching as the main search tab (fix «تائية الإلبيري»)
- [x] filter: remove «النوع»; century as a compact scroll picker; direction = an arrow button next to the sort; topic merged into الأقسام

## 3. Launch
- [x] downloaded books re-download / show up slowly after reopening the app

## 4. Downloads
- [x] the MB size in «تنزيل الكل», speed, «تنزيل كل المتون»

## 5. Menus
- [x] AtharMenu like WhatsApp: compact, at the button, with an arrow

## 6. Content
- [x] footnotes in the bad books (نقض الدارمي first) — Athar-Engine
- [x] link the Hamdan editions/duplicates (الرد على الزنادقة)

## 7. Small items
- [x] المتون icon
- [x] old-paper theme: clear cream (not washed-out/dull)
- [x] overlays → tabs (find which screens)

## Log
- 01:10 phone: cold start 0.6 s, no crashes in the crash log. 3 article download workers stuck on RETRY (krahya-al-msaraa…, mn-aazm-asbab…, qlb-al-astdlal…) — to check.
- 01:20 ✅ reopen: `_retainedEntityIds` started empty on every launch → every downloaded book showed «downloading» until reconcilePinnedDownloads finished. Now saved in prefs and restored at once (ContentDataModule.setRetained). Compiles.
- 01:40 ✅ list search: books/المتون/poetry/articles/issues now use the main search engine (ContentSearchField.Catalog + rememberCatalogRanks); poetry keeps matching on the opening verses, issues on the answer text. Test hamzaAboveFindsATitleSpelledWithHamzaBelow passes. Compiles.
- 02:00 ✅ تسميع removed from the app (code moved to android/parked/tasmee, uncommitted so it's not lost) + onnxruntime + RECORD_AUDIO. Removed AdhkarBabsScreen (dead, no caller). Compiles.
- 02:30 ✅ filter: removed «النوع» from books; direction = an arrow at the end of the «الترتيب» row that turns over when tapped (no longer counts as a filter); centuries = one line that scrolls sideways; «الموضوع» moved into the «الأقسام» popup as chips (books/poetry/articles/issues). Compiles.
- 02:45 ✅ fast scroller added to poetry/articles/issues/scholars/الكناشة (it was only in books/المتون). Compiles.
- 03:05 ✅ المتون icon: a scroll drawn in strokes (AtharIcons.Mutoon) instead of Archive. Compiles.
- 03:10 ✅ downloads: «تنزيل الكل» now says «في انتظار Wi-Fi» when the queue is waiting for Wi-Fi (the real reason it «doesn't progress» on mobile data) + a menu item «تنزيل الآن عبر بيانات الجوال». It already showed «X من Y · MB من MB». A «كل المتون» row at the top of the المتون page. Compiles.
- 03:25 ✅ menus: AtharMenu now has a small arrow pointing at its button (on whichever side it opened); still anchored at the button's end edge. Kept «above when it fits» (AtharMenuTest pins it). Compiles.
- 03:35 ✅ theme «عتيق» (aged): yellowed, slightly faded page #EFE4CC, brown-black ink, Quran red (the default). Read «ليس كريمي ولكن باهت وقديم» as «what I sent isn't cream, it's aged» → a separate theme next to «دافئ» (which is the cream). In the theme list in settings and in the reader. Compiles.
- 03:40 ✅ stacking: the places opened from the app menu (المتون، الأذكار، الأعلام…) were pushed on top of each other → back walked through all of them. Now they replace each other like the bottom bar's tabs (navigateTo). Compiles.
- 03:45 ✅ الكناشة: the search didn't normalise at all (hamza/tashkeel/ة) → normalised once per list. The facets already switch to a typed search above 12 options, so they scale to 1000. Compiles.
- 04:30 ✅ footnotes — cause found: Athar-Engine/scripts/ocr-json-import.py only recognised a note block that *starts* with «(n)». Mistral often merges the previous page's note running on + this page's notes into one text block, or opens them with «= ←» → the whole notes area landed in the body as text, the «(n)» stayed unlinked. Fix: `notes_start()` (a «references» block, a «=» mark under body text, or a «(n) …» line whose «(n)» is in a body block above opens the notes; everything after it is notes) + a selftest.
  - نقض الدارمي (Hamdan): 906 → 940 notes, 889 → 923 linked refs, 0 dead sups (app parser). Letters identical (530169, same multiset). The only things dropped are Hamdan's continuation arrows «⇐» ×104 «←» ×18 (meaningless once a note is joined). The 2062-line «join» pass from 14e1226e was replayed exactly (learned from HEAD, reproduces HEAD byte for byte on the old version). format-verify REJECTs: (1) text moves (expected → `--import-fix`), (2) `--import-fix` sees the dropped arrows + the stray OCR «<» marks: its `<[^>]+>` eats from a «<» up to the next page-sep, so it already reads these pages wrong in HEAD. To decide: keep the arrows (then the gate passes) or accept dropping them.
- 04:40 ✅ editions: linked 3 copies to the Hamdan editions (الرد على الجهمية والزنادقة → Hamdan, كشف الكربة, a third copy of الإبانة الكبرى). Found **86 more unlinked groups** (mostly from the 2026-11-03 batch: مجموع الفتاوى twice ≈49 MB each, البداية والنهاية ×6, المغني ×3…) → proposal in docs/editions-and-duplicates-2026-11-07.md, for you to approve (deletions need your OK, like round 1).
- 04:50 ✅ unit tests: :app:testDebugUnitTest + :core:athar-text:test pass (the تسميع test had stayed behind → moved to android/parked/tasmee-test). Running: the importer before/after over all 43 Hamdan books, then the same gates per book (scratchpad apply_book.py).
- 05:30 ✅ footnotes for all Hamdan books: the importer was run before/after the fix over all 43 (both runs lose 0 letters; the corpus outputs were restored byte for byte). Per book: a page the fix changed takes the new version only if its hand-fixed copy is still identical to the old import (otherwise the hand fix stays); then the «join» pass is replayed (must reproduce HEAD exactly); then a strict check: same characters (only the «⇐ ←» arrows dropped), same page markers.
  - **34 books changed**, 0 dead sups in all of them (app parser). Biggest gains in linked notes: نقض الدارمي +34, الرد على الجهمية +19, اللالكائي +14, أربع قصائد +13, المختار +7, الإبانة الصغرى +5, الجامع للآجري +5. Three books lose 1–2 «linked refs» with the same number of notes and notes without a ref: a second ref to an already-linked note that sat in a note continuation and went back into the note (checked: 4 refs, all a «(١)» inside note text the OCR had put in the body). Separate, old OCR defect seen on the way: some pages repeat a note sentence twice (e.g. الأربعون p. 582) — already in HEAD, not touched.
  - **7 skipped** (left as they are): الاحتجاج بالآثار، آداب المعلمين (a heading would have gone into the notes — possible false positive), الشريعة (a $^{}$ formula), صبيان، عقائد، كتب الإيمان، إثبات الحد (the site file was edited after the join pass in a way the replay doesn't reproduce — needs a manual look).
  - Not changed: the corpus outputs in Athar-Engine (untracked, they contain the old import + hand fixes). The importer fix (notes_start + selftest) is in Athar-Engine/scripts/ocr-json-import.py — also untracked, as before.
- 05:50 checked the «linked refs» drop: 4 refs in 3 books, each a «(١)» inside note text that the OCR had put in the body → back inside its note. Benign.
- 06:00 ⚠️ release build compiles; **phone install refused**: INSTALL_FAILED_UPDATE_INCOMPATIBLE (the com.atharchive on the phone has a different signature — probably the Play build). Installing needs an uninstall = wipes the app's data/downloads on the phone → waiting for your OK. Nothing was uninstalled.
- 07:xx (user awake) old com.atharchive uninstalled (OK given); new install refused by the phone: INSTALL_FAILED_USER_RESTRICTED — Xiaomi needs «Install via USB» allowed / the on-screen prompt tapped. The phone has no Athar app until then.
- Duplicates re-checked: of 86 groups, 57 = different تحقيق/publisher (→ link as editions, never delete), 17 = parts/volumes or a small extract (→ leave), 12 = same محقق, possible true duplicates (→ check the text before deleting anything).
- Hamdan's two footnote layers (تحقيق / manuscripts): not in the imported text — the manuscript notes («في نسخة (ج)…») share one numbering with the rest; the only pages with two «(١)» series are double OCR scans. The overnight fix neither merges nor separates them. Still deferred; need one printed page as an example.
- 09:xx footnotes shown as text (all books, not only Hamdan): 193 Shamela-style books wrote notes as plain «(١) …» at the page end, which the app showed as body text (فتاوى نور على الدرب: 10,210 notes as text, 0 notes). Marked them as notes (adds only «^», joins a note's lines; letters unchanged). Per page only when the notes are a clean 1..k run, each number is referenced above, and the page had no notes yet. Plus «* …» star notes → «(*) …» and «- …» note lines joined (markdown read them as lists and swallowed the notes after them).
  - app parser over the 193 books: notes 69,794 → 278,473, linked refs 68,986 → 281,305, broken refs 4,856 → 4,812, «(n) …» paragraphs shown as body 114,668 → 47,883.
  - Left alone on purpose: paragraphs *after* a page's last note (often real body text: the next dictionary entry, «الطريقة الخامسة»…); حاشية الآجرومية and الحلل الإبريزية (there the «(n)» paragraphs are the main text). 4 books gained 4–10 broken refs (تذكرة الحفاظ، قاعدة حسنة، وبل الغمامة، الصحاح) — to check.
  - Backups of the 17 books that had uncommitted changes: scratchpad pn-backup/; the rest are in git HEAD.
- Phone: the MIUI USB-install prompt closes itself as «cancelled» (not Play Protect). APK copied to the phone: Download/athar-release.apk — install it from the file manager. The phone has no Athar app until then.

## تفسير مقاتل — full surah texts hidden, footnotes realigned

- 114 "whole surah" paragraphs under `## [سورة X : الآيات a الى b]` are now in HTML comments. The text stays in the file, and the app skips it.
- Footnotes (ref ^k opens the k-th note on its page) were out of step in three ways:
  - A shared note «(3، 4، 5) …» was glued to the note before it. It is split out, and the label is kept.
  - One note was cut into lines, e.g. «سورة آل عمران:» | «4.». The pieces are joined.
  - The editor's study at the start of juz 5 numbers refs across pages (31..34). They are recounted per page.
  - Only the ref numbers and whitespace changed (checked by a gate).
- Parser before → after: dead refs 689 → 52, notes no ref opens 1955 → 806, notes 8827 → 8315 (joined pieces).
- Left as is: 12 pages (juz 5 study 46/47 and some body pages) where the counts still don't match. Most of the 806 unreferenced notes are notes whose ref mark the import dropped from the body. These can't be placed without the print.
- Lost text in the import: in the juz 5 study, numbers at line ends are gone, e.g. «انظر تفسيره للآية [n] من سورة يوسف» and «ولد سنة [88] ببعلبك». They need the PDF.
- Update, from the epub (books/epub/23614.epub, another print of the same edition). The md text is kept; only note boundaries, ref marks and numbers come from the epub. Gate: the old text is fully kept, and the 2,991 added chars are only digits and `( ) - .`.
  - 604 pages: numbers the import dropped inside notes are back («للآية (40) من سورة هود», «ولد سنة 88», «57- 58»).
  - 640 pages: notes split and joined where the epub starts each one. 50 refs the import dropped were added at the epub's «n».
  - 41 «n» marks that showed as body text are now refs.
  - 4 shared «(2)، (3) …» notes had become a lone «،»; they are rejoined.
  - 6 page-top `<sup data-sep-page>` refs that opened nothing are now (^1).
  - 16 run-on notes are marked «= » so the app joins them to the previous page's last note.
  - «آية الاسراء (^79)» is back to (79).
  - Parser now: dead refs 0 (was 689), notes without a ref 95 (was 1955). For those 95 the epub has no mark either (e.g. 1/108 has marks 1–4 and 6, no 5). They show as notes in the page's notes list, never as text.
- Update, the last 95 notes (read one by one against the epub). Now 0 dead refs, 0 notes without a ref (parser).
  - 62 refs placed at the word the note is about. The best clue: a lone «»» left from the mark's lost number, which the epub has too (e.g. «ويأخذون»» ⇐ «فى أ: يأخذون»). In the juz 5 study three leftover digits went with it («ثَمانِيَةٌ3»).
  - Long notes the import cut into separate notes, rejoined: 1/545, 2/513, 2/641, 4/409.
  - Surah-info tails rejoined with the editor's surah number: 2/396 (14), 3/151 (23), 3/223 (25), 4/175 (54). Two lost numbers restored: «4/ 29- 30», «آل عمران: 100- 105».
  - 3/135, 3/641: the first notes continue the page before → «= ». Every ref on 3/135 had opened the note two places early.
  - al-Baqara's «مقصود السورة» summary (epub p.82, no mark) moved to page 41, with its ref on «(2) سورة البقرة (^1) مدنية…». The book does the same for المؤمنون.
  - Whole-book gate vs the start of today: no Arabic letter removed. Removed: only mark leftovers («n», lone »), plus 6 `<sup>` tags. Added: only digits and `- ( ) . =`.

## Evening batch (2026-11-07)

**Authors split over two records (root fix).**
- 19 authors had two person records, so each one's works sat on two pages. Each is now merged into one record, keeping the better-filled one:
  - الخليفي (958 works + 1), الذهبي (46 + 1), السعدي (3 + 3), ابن عثيمين (9 + 6);
  - الآجري، ضياء المقدسي، الدارمي، السيوطي، ابن عبد البر، مالك، الحجاوي، عبد الرحمن بن حسن، عبد الملك بن قاسم، الزركشي، السبكي، السمعاني، الجويني، البعلي، الحطاب.
- The removed record's names became `also_known_as`. Each old /person/ link 301s to the kept record (public/_redirects).
- Cause: scripts/epub-import.ts made a new slug from each epub's creator. It now reuses an existing person whose title or alias matches, and reads «الذهبي، شمس الدين» as «شمس الدين الذهبي».
- Guard: `audit:content` now reports `SPLIT AUTHOR` (one name on two records) and `NO SUCH PERSON` (a work naming a missing record). Both are 0 now.

**App (compiled, not yet run on the phone).**
- Places opened from the bar or ⋮ menu show the app icon, not a back arrow; they used to stack on الكتب.
- The arrow inside the الأقسام button is removed.
- An author's page tabs by kind of work (الكتب / الشعر / المقالات / المسائل), showing only the kinds they have, in place of الكل / المفضّلة / المحمّلة. Issues were missing from author pages and now show.
- Same frame on every list page (Search, الكنّاشة, الأذكار).
- Pinned default filters; the direction button sits outside the filter popup.
- Launch screen takes the icon colour.
- «إلى الأذكار» is hidden for a one-word selection.
- Settings grouped.

**Content.**
- Hanafi fiqh books removed, in session «تحديث كتب الحنفية».
- العنقري (17) and مصعب بن صلاح added.
- Names list: docs/scholars-to-add.md.

**Footnotes.** 346 books still have note problems. The list is in docs/footnotes-todo.tsv and the handoff prompt in docs/prompt-footnotes-and-intros.md.

**Open.**
- The phone gets heavy after «تنزيل الكل»: not measured yet, as the phone was disconnected.
- The app catalog isn't regenerated, so the author merges reach the app only after `app:gen`.
