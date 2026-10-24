# Before publish

Everything that must be done before the Android app and its content are published. Living doc: each change updates its rows in the same commit. Nothing goes to R2 until the owner says **"publish"**.

Started 2026-10-21 from a code + phone audit (USB phone, benchmark build, local content server).

**Memory leaks come first.** Any sign of growing memory (native heap, Java heap, file handles) is investigated before every other item and blocks release until its cause is found and fixed. A crash or a slow screen is checked for a leak before anything else. The release gate below is run on the phone for every build that goes to the owner.

**State:** ✅ done · 🔧 in progress · ⬜ open · ⏸ deferred by the owner

---

## 1. Owner decisions

| # | Decision |
|---|---|
| D1 | The **only** menu form app-wide is the small anchored `AtharMenu` popup. Filters become a row of small buttons (الموضوع ▾ القرن ▾ النوع ▾ الترتيب ▾), each its own popup, one level deep. |
| D2 | A footnote number opens a small popover **above the number, with a tail pointing at it**. Numbers can be tapped in every mode. |
| D3 | Continuous reading lives in the app as an Aa row «العرض: صفحات / متصل». Content stays as printed, so `joinAcrossPages` leaves the parser. |
| D4 | Poem «النص المتصل» becomes a one-tap Aa toggle, not a new screen. |
| D5 | «مقدمة» becomes **«مقدمة المحقق»**. **Only the editor's / publisher's front matter is cut into the card; the author's own مقدمة stays in the book.** |
| D6 | Delete the Audio tab; recordings stay inside their works. **المقالات** goes into the bottom bar. |
| D7 | Add «الأعلام» (people) to the ⋮ menu next to الأذكار. |
| D8 | Editions: one list entry per work, with the other editions inside. **The default is the best edition** (a named محقق working from manuscripts beats publisher-only; ties go to more linked notes). Broken imports are hidden. Zad's default is **القاسم**. |
| D9 | Soap motion on the reader and poem bars: build **both** variants (travelling pill / press squish). The owner picks on the phone, and the other is deleted. |
| D10 | Importer-assigned `kind: مرجع` or a missing kind is reset to **كتاب**. متن stays where it is set. |
| D11 | The 18 unvocalized poems ship as-is. They are listed as a known gap and do not block publishing. |

---

## 2. Formatted books (59)

List: `git log --grep='(book):'`. Queue: [`book-formatting-queue.tsv`](book-formatting-queue.tsv). Initial formatting gate: all 59 passed before the approved W2/W8/W9 edits. Current gate: 11 pass, 48 deliberately reject (approved frontmatter changes and card-text removals). The 22 W2 removals overlap those frontmatter changes. No blanket gate bypass.

2026-10-23: `format/sonnet-cloud` (298 books) and `format/codex-night` (5 more) merged into main. All 302 changed books pass `format-verify --from <pre-merge main>` (299 strict, 3 import-fix), and their Arabic letter sequence is identical to pre-merge main. Four codex books conflicted with the card cleanup and kept main's version, so they still need formatting: `al-shafya-fy-almy-al-tsryf-walkht`, `nur-al-iman-wa-zulumat-al-nifaq`, `nur-al-tawhid-wa-zulumat-al-shirk`, `rsm-al-ahdaf`. Open import items from the run: [`format-sonnet/REPORT.md`](format-sonnet/REPORT.md).

- "at the start" = card page markers left in the flow (fixed by W1).
- "in a row" = stacked empty pages, mostly from the parser join (fixed by W1; recount after).
- "card checked ✅ (editor only)" = the card holds an intro; confirm it is the editor's, not the author's (D5).

| # | name | id | state | open issues |
|---|---|---|---|---|
| 1 | آداب المشي إلى الصلاة | `adab-al-mashy-ila-al-salah` | formatted · conservation ✅ · strict expected REJECT | page census ✅; alternate edition ✅ |
| 2 | آداب المشي إلى الصلاة (مطبوع ضمن مؤلفات الشيخ محمد بن عبد الوهاب، الجزء الثالث) | `adab-al-mshy-ala-al-slah-mtbwa-dmn-mwlfat-al-shykh-mhmd-bn-a` | formatted · conservation ✅ · strict expected REJECT | page census ✅; default edition ✅ |
| 3 | آداب المشي إلى الصلاة ط الوزارة | `adab-al-mshy-ala-al-slah-t-al-wzarh` | formatted · conservation ✅ · strict expected REJECT | page census ✅; draft ✅ |
| 4 | إغلاق الأبواب لمنع الحب والإعجاب | `aghlaq-al-abwab-lmna-al-hb-walaajab` | formatted · conservation ✅ · strict expected REJECT | page census ✅ |
| 5 | الآجرومية | `al-ajrwmya` | formatted · conservation ✅ · strict expected REJECT | page census ✅; alternate edition ✅ |
| 6 | المقدمة الآجرومية | `al-ajurrumiyyah` | formatted · conservation ✅ · strict expected REJECT | page census ✅; default edition ✅ |
| 7 | العقيدة الطحاوية | `al-aqeedah-al-tahawiyyah` | formatted · conservation ✅ · strict PASS | page census ✅ |
| 8 | العقيدة الواسطية | `al-aqeedah-al-wasitiyyah` | formatted · conservation ✅ · strict PASS | page census ✅ |
| 9 | الأربعون النووية | `al-arbaeen-al-nawawiyyah` | formatted · conservation ✅ · strict PASS | page census ✅ |
| 10 | الأربعون المدنية | `al-aryawn-al-mdnyh` | formatted · conservation ✅ · strict expected REJECT | page census ✅ |
| 11 | الفتوى الحموية الكبرى | `al-fatwa-al-hamawiyyah-al-kubra` | formatted · conservation ✅ · strict expected REJECT | page census ✅; 1 note with no ref |
| 12 | الغيبة | `al-ghybh` | formatted · conservation ✅ · strict expected REJECT | page census ✅; no card |
| 13 | الإباضية وهل هم خوارج | `al-ibadya-whl-hm-khwarj` | formatted · conservation ✅ · strict expected REJECT | page census ✅ |
| 14 | الإبداع في بيان كمال الشرع وخطر الابتداع | `al-ibda-fi-bayan-kamal-al-shar` | formatted · conservation ✅ · strict expected REJECT | page census ✅; no card |
| 15 | الإيمان للقاسم بن سلام | `al-iman-al-qasim-ibn-sallam` | formatted · conservation ✅ · strict expected REJECT | page census ✅; no card; TOC numbering |
| 16 | الاعتصام بالكتاب والسنة… | `al-itisam-bil-kitab-wal-sunnah` | formatted · conservation ✅ · strict expected REJECT | page census ✅ |
| 17 | الكذب | `al-kthb` | formatted · conservation ✅ · strict expected REJECT | page census ✅; no card |
| 18 | المغني في تصريف الأفعال | `al-mghny-fy-tsryf-al-afaal` | formatted · conservation ✅ · strict expected REJECT | page census ✅ |
| 19 | المنتخب من صحيح السنة النبوية | `al-mntkhb-mn-shyh-al-snh-al-nbwyh` | formatted · conservation ✅ · strict expected REJECT | page census ✅; no card |
| 20 | المحرر في أحاديث الأحكام | `al-muharrar` | formatted · conservation ✅ · strict expected REJECT | source dividers: 1 stacked / 0 leading |
| 21 | القواعد الأربع | `al-qawaid-al-arba` | formatted · conservation ✅ · strict PASS | page census ✅ |
| 22 | التعقبات المفيدة… لمخلوف | `al-taaqqubat-al-mufidah-makhluf` | formatted · conservation ✅ · strict expected REJECT | page census ✅ |
| 23 | التدمرية | `al-tadmuriyyah` | formatted · conservation ✅ · strict expected REJECT | page census ✅; 1 note with no ref |
| 24 | ثلاثة الأصول وأدلتها | `al-usul-al-thalathah` | formatted · conservation ✅ · strict PASS | page census ✅ |
| 25 | الورقات | `al-waraqat` | formatted · conservation ✅ · strict PASS | page census ✅ |
| 26 | أنوار الهلالين في التعقبات على الجلالين | `anwar-al-hilalayn` | formatted · conservation ✅ · strict expected REJECT | page census ✅ |
| 27 | عشر من الخصال للباحثات عن الجمال | `ashr-mn-al-khsal-llbahthat-an-al-jmal` | formatted · conservation ✅ · strict expected REJECT | page census ✅ |
| 28 | بهجة النظر في آداب السفر | `bhjh-al-nzr-fy-adab-al-sfr` | formatted · conservation ✅ · strict expected REJECT | page census ✅; no card |
| 29 | بلوغ المرام من أدلة الأحكام | `bulugh-al-maram` | formatted · conservation ✅ · strict expected REJECT | source dividers: 3 stacked / 0 leading; card checked ✅ (editor only) |
| 30 | غيث القلوب ذكر الله تعالى | `ghyth-al-qlwb-thkr-al-lh-taala` | formatted · conservation ✅ · strict expected REJECT | page census ✅; no card |
| 31 | حتى لا نخسر رمضان | `hta-la-nkhsr-rmdan` | formatted · conservation ✅ · strict expected REJECT | page census ✅ |
| 32 | اعتقاد أئمة الحديث | `itiqad-aimmat-al-hadith` | formatted · conservation ✅ · strict expected REJECT | page census ✅; no card |
| 33 | جواب في الحلف بغير الله… | `jawab-fi-al-half-bighayrillah` | formatted · conservation ✅ · strict expected REJECT | page census ✅ |
| 34 | كشف الشبهات | `kashf-al-shubuhat` | formatted · conservation ✅ · strict PASS | page census ✅; no card |
| 35 | كتاب العلم | `kitab-al-ilm-abi-khaythama` | formatted · conservation ✅ · strict expected REJECT | page census ✅; card checked ✅ (editor only) |
| 36 | السلاح | `kitab-al-silah-al-qasim-ibn-sallam` | formatted · conservation ✅ · strict expected REJECT | page census ✅ |
| 37 | كتاب التوحيد | `kitab-at-tawhid` | formatted · conservation ✅ · strict PASS | source dividers: 1 stacked / 0 leading |
| 38 | كتاب مختصر الأذكار والآداب | `ktab-mkhtsr-al-adhkar-waladab--v2` | formatted · conservation ✅ · strict expected REJECT | page census ✅ |
| 39 | كتاب متون طالب العلم - المستوى التمهيدي | `ktab-mtwn-talb-al-alm-al-mstwa-al-tmhydy--v2` | formatted · conservation ✅ · strict expected REJECT | page census ✅ |
| 40 | لمعة الاعتقاد | `lumat-al-itiqad` | formatted · conservation ✅ · strict expected REJECT | page census ✅; no card |
| 41 | ملحة الإعراب | `mlha-al-iarab` | formatted · conservation ✅ · strict expected REJECT | page census ✅ |
| 42 | متن أبي شجاع | `mtn-aby-shjaa-al-msma-al-ghayh-waltqryb` | formatted · conservation ✅ · strict expected REJECT | page census ✅ |
| 43 | مقدمة في أصول التفسير | `muqaddimah-fi-usul-al-tafsir` | formatted · conservation ✅ · strict expected REJECT | page census ✅ |
| 44 | نواقض الإسلام | `nawaqid-al-islam` | formatted · conservation ✅ · strict PASS | page census ✅ |
| 45 | نخبة الفكر | `nukhbat-al-fikar` | formatted · conservation ✅ · strict expected REJECT | page census ✅; card checked ✅ (editor only) |
| 46 | رسالة في القضاء والقدر | `risalah-fil-qada-wal-qadar-uthaymeen` | formatted · conservation ✅ · strict expected REJECT | page census ✅; no card |
| 47 | رسالة إلى أخي في الله | `rsalh-ala-akhy-fy-al-lh` | formatted · conservation ✅ · strict expected REJECT | page census ✅ |
| 48 | صبرا آل ياسر | `sbra-al-yasr` | formatted · conservation ✅ · strict expected REJECT | page census ✅ |
| 49 | شروط الصلاة وأركانها وواجباتها | `shurut-al-salah` | formatted · conservation ✅ · strict PASS | page census ✅ |
| 50 | تأملات وأحكام في قوله تعالى ولا تباشروهن… | `taammulat-wa-ahkam-la-tubashiruhunna` | formatted · conservation ✅ · strict expected REJECT | page census ✅ |
| 51 | تحقيق القول في مسألة: عيسى كلمة الله… | `tahqiq-al-qawl-fi-isa-kalimatullah` | formatted · conservation ✅ · strict expected REJECT | page census ✅; no card; 10 notes with no ref |
| 52 | ثلاثة الأصول وشروط الصلاة والقواعد الأربع | `thalathat-al-usul-wa-shurut-al-salah` | formatted · conservation ✅ · strict PASS | page census ✅; 1 note with no ref; card checked ✅ («الأصول الثلاثة» is the publisher's title line) |
| 53 | طريقك إلى الدعاء المستجاب | `tryqk-ala-al-daa-al-mstjab` | formatted · conservation ✅ · strict expected REJECT | page census ✅; no card |
| 54 | تسلية نفوس النساء والرجال عند فقد الأطفال | `tslyh-nfws-al-nsa-walrjal-and-fqd-al-atfal` | formatted · conservation ✅ · strict expected REJECT | page census ✅ |
| 55 | العمدة في الأحكام | `umdat-al-ahkam` | formatted · conservation ✅ · strict expected REJECT | source dividers: 3 stacked / 0 leading; card checked ✅ (editor only) |
| 56 | وأحسن كما أحسن الله إليك | `wahsn-kma-ahsn-al-lh-alyk` | formatted · conservation ✅ · strict expected REJECT | source dividers: 1 stacked / 0 leading; no card |
| 57 | زاد المستقنع (العسكر) | `zad-al-mstqna-fy-akhtsar-al-mqna` | formatted · conservation ✅ · strict expected REJECT | page census ✅; alternate edition ✅ |
| 58 | زاد المستقنع (القاسم) | `zad-al-mustaqni` | formatted · conservation ✅ · strict expected REJECT | page census ✅; card checked ✅ (editor only); default edition ✅ |
| 59 | ظاهرة التقاص في النحو العربي | `zahra-al-tqas-fy-al-nhw-al-arby` | formatted · conservation ✅ · strict expected REJECT | page census ✅ |

---

## 3. Issues

| id | area | issue | cause | state |
|---|---|---|---|---|
| R1 | reader | A tapped footnote shows an empty white dialog in every book (e.g. الإيمان لأبي عبيد). | The `ReaderScreen.kt:327` AlertDialog draws `FootnoteBody`, but `ReaderBlockView.kt:303` renders that only in Footer mode. | ✅ phone: الإيمان note visible above Arabic digit in footer and continuous modes; JVM tests pass |
| R2 | reader | Footnote numbers can't be tapped in «أسفل الصفحة» mode. | `markedText` links notes only when `tapNotes` is on. | ✅ phone: الإيمان note visible above Arabic digit in footer and continuous modes; JVM tests pass |
| R3 | reader | A footnote opens a dialog instead of an anchored popover. | D2 | ✅ phone: الإيمان note visible above Arabic digit in footer and continuous modes; JVM tests pass |
| R4 | content | Books open on runs of empty pages (Zad: 14). | Card page markers stay in the flow (rule 10). Bulugh 14, Nukhba 10, Umda 7. | ✅ all published packages have zero leading dividers; Zad/الإيمان phone check passed; final card refresh phone check pending |
| R5 | content | Library-wide, about 106k stacked empty page dividers (al-istiqamah: 813 pages become 36 paragraphs). **Content must not be published from the current parser.** | `joinAcrossPages` chain-joins unpunctuated books (`parser.ts:378`). | ✅ paragraph joining removed; source block/verse guards pass; final census: 6,388 consecutive-number source blanks. Raw adjacent count 17,898 includes source-order anomalies (R10); earlier scratch counts are superseded |
| R9 | content | Auto-card extraction swallowed the author’s opening merged onto «تأليف:» in `al-iman-libn-taymiyyah`; metadata title-page markers also remained in flow. | Imported merged paragraph / heuristic card boundary. | ✅ 10-book artifact check: author opening restored; original body retained; three front-matter notes stay in cards; final phone check pending |
| R10 | content | Final packages contain 11,378 backward adjacent page-marker pairs in 146 books (e.g. مسند أحمد 198→197, الطبري 15→14); also 131 forward jumps and one volume boundary. | Imported marker order. | 🔧 2026-10-24: the 11,378 figure predates the two-series fix (census then: 293 steps / 123 books). 247 marker numbers renumbered in 81 books, each gated by `format-verify --import-fix` (no text or marker lost): lone typos whose neighbours leave one value (310·3110·312 → 311) and stray markers between two consecutive pages (235·79·236 → 236). Left: **82 steps / 62 books**: numbering restarts without a volume tag (new part at 1), books with skipping source numbers, and **7 books with a page's text duplicated** (Tafsir Ibn Kathir v4 pp.126–129 twice, verbatim; also شرح الشافية للرضي, صحيح ابن حبان, الفروع, الشرح الكبير على المقنع, من تاريخ النحو العربي, نهاية المطلب) — deleting a copy needs the owner |
| R6 | reader | «أرقام الصفحات: مخفية» still shows page-end notes, so there is no true continuous view. | D3 | ✅ phone: متصل hides dividers and page-end notes; number still opens note |
| R7 | content | 22 cards still carry Shamela lines («بطاقة الكتاب وفهرس الموضوعات», an empty «صفحة المؤلف:», «[ترقيم الكتاب موافق للمطبوع]»). | Import leftovers. | ✅ 22 cards cleaned (51 lines + 13 tags; line-diff verified) |
| R8 | reader | The label says «مقدمة»; it must say «مقدمة المحقق». The `thalathat-al-usul-wa-shurut-al-salah` card holds a «## الأصول الثلاثة» heading and must be checked so no author text is cut. | D5 | ✅ label renamed; 7 large cards checked |
| P1 | poem | Every باب heading is dropped (Alfiyya 83, Kafiya 97, Nahj 67). | `toPoemReaderUi` keeps only verse blocks (`AtharContentViewModel.kt:1042`). | ✅ phone: headings, verse numbers, poem Aa, panel tabs, save verse and bookmark checked; editor-card refresh retest pending |
| P2 | poem | Aa shows book-only rows (الحواشي, أرقام الصفحات). Verse numbers are never shown. | Book Aa reused (seen on the phone). | ✅ phone: headings, verse numbers, poem Aa, panel tabs, save verse and bookmark checked; editor-card refresh retest pending |
| P3 | poem | The panel has only الشرح/البحث; it opens on an empty «لا شرح» tab; there are no contents, الكناشة or bookmarks; search appears twice (panel and top icon). | `PoemReaderPanel.kt` | ✅ phone: headings, verse numbers, poem Aa, panel tabs, save verse and bookmark checked; editor-card refresh retest pending |
| P4 | poem | ⋮ «النص المتصل» opens a new screen. | D4 | ✅ phone: headings, verse numbers, poem Aa, panel tabs, save verse and bookmark checked; editor-card refresh retest pending |
| P5 | poem | `alfya-abn-malk` is broken: 83 half verses, a biography in the flow, and a biography verse used as its list preview. Split-verse defects in الزبد 159, الكوكب الزهري 138, اللؤلؤ 25, الدرة السنية 23, الجواهر الكنزية 16. No caesura in `raghif-khubz-yabis` or `lamiyyat-ibn-taymiyyah`. | Import | 🔧 alfya-abn-malk drafted; other source repairs carried to Athar-Engine import work |
| P9 | poem | Unpinned poems loaded only one 250-block frame, silently omitting later verses and headings. | Poem route called the single-frame opener once. | 🔧 every poem frame now loads; partial content offers load-rest; stable reading/bookmark IDs survive moved front matter; three restore tests pass; final phone check pending |
| P8 | poem | Alfiyya source has 991 numbered verse lines against a front-matter count of 1,002; all 991 parse. Printed source numbering has gaps while app verse indices are sequential for audio/annotations. | Source discrepancy; not caused by W5/card extraction. Needs original-source comparison, no reconstruction from another edition. | ⬜ reported |
| P6 | poem | 18 poems are effectively unvocalized (11 with no marks at all): Alfiyya, al-Iraqi, al-Suyuti, Jazariyya, Tuhfat al-Atfal, Rahabiyya, Bayquniyya and more; see §6. | Source. **Shipped as-is (D11).** | ⏸ accepted as-is under D11 |
| P7 | poem | Some list titles are a first verse plus «…»; poetry topics are incomplete (النحو shows 3 poems). | Metadata | ⬜ |
| N1 | nav | The Audio tab duplicates audio that already lives in its works (971 recordings, all attached). | D6 | ✅ bottom bar and menu walked; mini-player retained |
| N2 | nav | There is no people page (593 person files: names and death years, 5 bios). | D7 | 🔧 generator, database and people screens built; regenerated-data phone walk pending |
| N3 | nav | Secondary pages (المقالات, الإعدادات…) keep the bottom bar with the *previous* tab highlighted. | Seen on the phone | 🔧 selection is null on secondary routes; final phone check pending |
| M1 | menus | Bottom sheets and dialogs: `AtharFilterButton` (`AtharSectionUi.kt:397`), `SearchFilterSheet.kt`, the remove-download dialog (`AtharApp.kt:270`), the delete-excerpt dialog (`:808`), the note editor sheet (`:744`). | D1 | 🔧 filters, confirms and editor walked; grep gate clear; Arabic selection menu verified; delete/cancel draft edge pending |
| L1 | lists | Duplicate editions are listed separately: Zad ×2, Ajurrumiyya ×2, Alfiyya ×2, آداب المشي ×3, بهجة المجالس ×2, جامع بيان العلم ×2, بداية العابد ×2. | D8 | 🔧 three approved edition groups linked, two broken imports drafted; other defaults await owner; regenerated-data phone walk pending |
| L2 | lists | Row icon order differs: المقالات shows star then download; الشعر and المسائل show download then star. | Seen on the phone | ✅ row order corrected; phone books row checked |
| L3 | lists | Every article preview is the opening formula «الحمد لله والصلاة والسلام…». | Preview takes the first line. | 🔧 opening formula skipped; generator regression passes; regenerated preview phone check pending |
| L4 | lists | Question rows repeat the bracket title as their subtitle; sizes show a bare «ب» unit («٨٨١ ب»). | Seen on the phone | 🔧 subtitle dedupe and بايت implemented; final phone check pending |
| L5 | lists | Audit found another title/author duplicate candidate: `amdh-al-fqh` / `umdat-al-fiqh-ibn-qudamah` (عمدة الفقه), outside the approved edition table. | Import / edition review | ⬜ compare sources before grouping or hiding either |
| U1 | reader | The loading screen uses a «رجوع» text button; everywhere else uses a back arrow. | Seen on the phone | 🔧 back arrows implemented; final phone check pending |
| U2 | reader | The reader ⋮ menu ignores the reader palette (white menu on a warm page). | Seen on the phone | 🔧 reader menu uses reader palette; final phone check pending |
| U3 | reader | The Aa label «عند الضغط على الرقم» wraps to 2 lines. Footnote numbers use Latin digits. | Seen on the phone | ✅ phone: single-line «عند الضغط»; Arabic digits in all note modes |
| U4 | reader | TOC numbering is inconsistent in الإيمان. | Content | ⬜ |
| U5 | perf | Zad first open takes about 35 s on the phone. | Not yet profiled | ⬜ |
| U8 | lists | Reader side panel showed the author; lists opened newest-first. | Owner request | 🔧 author removed from the panel; A–Z is the default order for books, poems, articles and questions (titles compared normalized; الكناشة stays newest-first); phone check pending |
| U9 | reader | Side panel had an empty header row with a ✕ (after the author was removed). | Owner request | ✅ header and ✕ removed from the book and poem panels; the scrim or back closes them |
| U10 | content | Book info (الكتاب، المؤلف، الناشر…) still showed in the text of آداب العشرة and 109 other books. | The parser's auto card stopped at labels it did not know («عني بتحقيقه», «رواية», «خرجه وعلق عليه»…), a leading «الجزء: 1», the «جامع الحديث» description (56 books) and the printed title page. | ✅ parser learns those (credits by first word); old vs new over the library: 110 books changed, 0 with flow text lost beyond the card; dictionary/«**[باب]**» lines stay text (test). Label is now «مقدمة المحقق ومعلومات عن الكتاب»; phone: آداب العشرة opens on «بسم الله الرحمن الرحيم», its info on the renamed page |
| U11 | lists | The library header shot off ~3× faster than the finger and faded (200dp header hidden by 64dp of scroll); letting go near the top hid it and left a blank band above the first row. | Fixed 64dp travel; the settle ignored how far the list had scrolled (and `firstVisibleItemIndex` moves on once a row slides under the header padding) | ✅ travels 1:1 with the list, no fade, never hides further than row 0 has scrolled (settles open otherwise); all 6 list screens; checked on the phone by screen recording |
| U7 | perf | The app crash-looped and a small downloaded book took 24 s to open. | A book update was imported in **one** transaction; the bundled SQLite keeps temp files in RAM (`TEMP_STORE=3`), so native heap grew ~30 MB/s to 1.9 GB and the app was killed, rolled back and retried on every launch. Every open and download tap waited behind it. | ✅ updates staged beside the old copy (a commit per frame, short rename swap, old copy purged in slices); whole-book deletes sliced; downloaded books open from their own copy until the swap. Phone: native heap flat 25–40 MB for 90 s after launch (was 1.9 GB at 65 s); same book opens in ≈1.5 s |
| U12 | perf / **memory** | Applying a new content generation (catalog sync) took **42–48 s** and native heap climbed 5 → ~200 MB (one sample 295 MB), then dropped to ~40 MB. Released, not leaked, but a low-RAM phone could be killed mid-apply, the U7 failure mode. | 2026-10-23 phase logs on the phone: `replaceEntities` (4,240 REPLACE rows in the one apply transaction) **42–47 s**, slowing with every row (500-row chunks: 0.6 → 1.7 → 2.8 → … → 11 s); stale-page delete 0.8–0.9 s; catalog FTS 0.9–1.1 s. The stale-page suspicion was wrong. Fix: `applyCatalogSlices` marks stale read-through books absent and purges them in sliced autocommits first (old package hash kept, so an interrupted apply finds them again), writes catalog rows 250 per transaction re-reading each slice's rows, then one small transaction writes tombstones, `catalog_fts` and the generation row last (an unfinished apply re-runs on the next sync). | ✅ fixed. Phone, benchmark APK, generation switch confirmed by request log (index + catalog + tombstones fetched at launch): native **34–38 MB flat** for 75 s, same pid, no crash. No timing logs left in the code |
| U6 | motion | The soap motion exists only in the bottom bar. | D9 | 🔧 owner defined soap = the highlight travelling between tabs/buttons with a jelly squeeze, in every selector. Variant A kept (B deleted); one shared `SoapLayer` now drives section tabs, Aa/Appearance segments, reader and poem panel tabs. Phone check pending |
| K1 | content | 21 formatted books had `kind: مرجع` and 14 had no kind (source recount: 35, correcting the initial 37); library-wide 489 مرجع and 319 none. | Importer (D10) | ✅ 35 formatted books corrected; 773 library-wide entries await owner exception list |
| K2 | content | Title typos and import junk: «الأريعون المدنية» (الأربعون), «الإيمان للقاسم بن سلام - محققا», «آداب المشي إلى الصلاة ط الوزارة». | Import | ✅ two titles corrected; redundant الوزارة import drafted |
| K3 | content | Notes with no ref: تحقيق القول 10, الحموية 1, التدمرية 1, ثلاثة الأصول وشروط الصلاة 1. | Gate report | 🔧 2 of 4 were Shamela bookkeeping notes, dropped with the card markers (التدمرية, ثلاثة الأصول وشروط الصلاة); تحقيق القول 10 and الحموية 1 remain |
| B* | backlog | Still open from `docs/android/2026-10-14-backlog.md`: A2 prefetch, B1 first-open jump, C downloads, D2 opening tab. They are carried into the doc and **not** in this plan's scope unless the owner pulls them in. | — | ⬜ |

### Duplicate editions — defaults (D8)

| work | default | other editions | state |
|---|---|---|---|
| زاد المستقنع | `zad-al-mustaqni` (القاسم, 230 manuscripts) | `zad-al-mstqna-fy-akhtsar-al-mqna` (العسكر) | ✅ linked; phone data check pending |
| الآجرومية | `al-ajurrumiyyah` (edited on manuscripts) | `al-ajrwmya` (دار الصميعي) | ✅ linked; phone data check pending |
| آداب المشي إلى الصلاة | `adab-al-mshy-ala-al-slah-mtbwa-dmn-mwlfat-al-shykh-mhmd-bn-a` (جامعة الإمام, محقق) | `adab-al-mashy-ila-al-salah` (الوزارة); `adab-al-mshy-ala-al-slah-t-al-wzarh` is the same print → hide | ✅ linked / redundant import drafted; phone data check pending |
| ألفية ابن مالك (poem) | `alfiyyah-ibn-malik` | `alfya-abn-malk` broken → hide | ✅ draft; retained poem card refresh phone check pending |
| جامع بيان العلم وفضله | **proposed** `jama-byan-al-alm-wfdlh` (2.2 MB) | `jami-bayan-al-ilm` (737 KB): same print (الزهيري, ابن الجوزي); find out why it is ⅓ the size before hiding | ⬜ owner to confirm |
| بداية العابد وكفاية الزاهد | **proposed** `bidayat-al-abid` | `bdayh-al-aabd-wkfayh-al-zhd-t-al-rkayz` is the same print (132 parsed main-text notes each) → hide; `bdayh-al-aabd-wkfayh-al-zahd-fy-al-fqh-ala-mthhb-al-amam-ahm` (دار البشائر) is a real 2nd edition | ⬜ owner to confirm |
| بهجة المجالس وأنس المجالس | **proposed** `bhjh-al-mjals-wans-al-mjals` | `bahjat-al-majalis`: no editor line in either; compare before choosing | ⬜ owner to confirm |

`mnyh-al-sajd-bshrh-bdayh-al-aabd-wkfayh-al-zahd` is a شرح, not an edition, and stays its own work.

---

## 4. Features to build

| W | feature | issues | state |
|---|---|---|---|
| W1 | Parser: remove `joinAcrossPages`; drop page markers inside the card | R4, R5 | ✅ parser + tests + full regeneration; zero leading dividers; 6,388 consecutive-number source blanks; R10 source-order gate remains open |
| W2 | Strip Shamela lines from cards; check the 7 large cards; rename to «مقدمة المحقق» | R7, R8 | ✅ |
| W3 | Footnote popover above the number with a tail; numbers tappable in every mode; Arabic digits | R1–R3, U3 | ✅ phone: footer, tap-only, and continuous modes; note above Arabic digit |
| W4 | Aa «العرض: صفحات / متصل» (replaces «أرقام الصفحات») | R6 | ✅ phone: متصل hides pages + page notes; paragraphs unchanged |
| W5 | Poem reader: headings, verse numbers, poem Aa, panel = الفهرس/الكناشة/العلامات (+الشرح), «النص المتصل» in Aa | P1–P4 | 🔧 headings/Aa/panel/save/bookmark walked on preliminary APK; editor cards + full-frame loading + stable-position/source-reopening fixes verified in artifacts/JVM tests; final phone walk pending |
| W6 | Bottom bar الكتب · الشعر · البحث · المقالات · الكناشة; ⋮ المسائل · الأذكار · الأعلام · الإعدادات; «الأعلام» page | N1–N3 | 🔧 bottom bar/menu walked; 593 real people bundled, headers corrected; people/secondary-selection phone walk pending |
| W7 | Popups only: filter row of small menus, confirms, note editor | M1 | 🔧 popup grep gate clear; filters/confirms/note save/Arabic selection menus walked; final empty-filter and delete/cancel draft checks pending; latest APKs built |
| W8 | Editions: `edition_of`, one entry per work, ⋮ «الطبعات»; hide broken imports | L1, P5 | 🔧 three approved groups + two hidden imports verified in final catalog; editions phone walk and three owner defaults pending |
| W9 | `kind` مرجع/none → كتاب (formatted books first, library-wide after the owner names exceptions); title fixes | K1, K2 | ✅ 35 actual affected books corrected; two titles fixed. Library-wide reset remains pending owner exceptions |
| W10 | Row icon order, article previews, question subtitles, size units, back arrow, reader menu palette | L2–L4, U1, U2 | 🔧 all changes built; row order walked; generated previews verified; remaining phone checks pending |
| W11 | Soap motion on every selector (owner: "every place that needs it") | U6 | 🔧 reader/poem bars (A), section tabs, segments, panel tabs; phone check pending |

Plan: `~/.claude/plans/we-should-make-the-effervescent-moth.md`.

---

## 5. Release gates

| gate | state |
|---|---|
| Full regeneration after W1–W9 (`scripts/gen-app-content.ts`) | ✅ 4,238 texts + 117 recordings; final ten-book refresh; signed generation `4a453251810def09`; bundled into both preview APKs |
| Empty-page census / source page order | 🔧 zero leading dividers, 6,388 consecutive-number blanks, no unexplained source block/verse loss. **R10 remains blocking:** 11,378 backward pairs across 146 imports. 2026-10-21 two-series fix census: backward page steps 104,395 → 292, books with any 279 → 123; text identical in 1,300/1,301 books (صحيح ابن حبان: two duplicate-note id suffixes renumbered, same text); no notes lost. Leftovers to review: `mstkhrj-aby-awana-t-al-jamaa-al-islamya`, `lamiyyat--v1`. [Detailed census](beforepublish-page-census.json) |
| `format-verify` on the 59 formatted books | ✅ conservation checked for all 59: only 22 allowlisted card cleanups, one card-boundary repair + approved metadata fields differ. Strict gate reports 11 pass / 48 expected reject after edition labels |
| `audit:content` | 🔧 2026-10-24: 300 → **90**. «Leaked footers» (202) were a false alarm: all 257,258 lines become page markers in the site and app; the check now normalizes first. 31 empty `#` headings removed (8 books, gated). Left: 70 books whose sub-headings («**[فصل …]**») are bold text, not TOC headings; 20 books with no chapters (mostly short ابن أبي الدنيا collections). Quality, not damage |
| No `ModalBottomSheet` / `AlertDialog` left (grep gate, W7) | ✅ no matches, including `Dialog(`; intro uses the existing reader content view |
| Room device checks, full-bundle validation, release certification ([`android/v1-progress.md`](android/v1-progress.md)) | 🔧 full signed bundle **verified** (22,413 frames / 4,751,870 blocks / 117 recordings); Room test APK still blocked by Xiaomi; Android 26/tablet certification unrun |
| **Memory:** launch, idle 90 s, open/close books, download and update a big book; native heap (`dumpsys meminfo`) stays flat; any growth is a blocker | ❌ open: U12 (48 s generation apply peaks ~200 MB). Earlier ✅ 2026-10-21 after U7 fix (25–40 MB flat); rerun on every release build |
| Owner walk on the phone | ⬜ |
| Owner says "publish" | ⬜ |

---

## 6. Known gaps shipped as-is (D11)

18 poems are effectively unvocalized: 11 with no tashkeel at all, 7 with a handful of marks.

| id | title | tashkeel marks |
|---|---|---|
| `al-drh-al-snyh-mnzwmh-fy-alm-al-frayd` | الدرة السنية منظومة في علم الفرائض | 2 |
| `alfiyyah-al-iraqi` | ألفية العراقي في علوم الحديث | 0 |
| `alfiyyah-al-suyuti` | ألفية السيوطي في علم الحديث | 0 |
| `alfiyyah-ibn-malik` | الخلاصة في النحو (ألفية ابن مالك) | 0 |
| `al-jazariyyah` | المقدمة الجزرية في التجويد | 0 |
| `al-jwahr-al-knzyh-lnzm-ma-jma-fy-al-azyh` | الجواهر الكنزية لنظم ما جمع في العزية | 2 |
| `al-kafiyah-al-shafiyah` | نونية ابن القيم = الكافية الشافية | 100 |
| `al-kwkb-al-zhry-nzm-mkhtsr-al-akhdry` | الكوكب الزهري نظم مختصر الأخضري | 2 |
| `al-lwlw-al-mnzwm-fy-nzm-mnthwr-abn-ajrwm` | اللؤلؤ المنظوم في نظم منثور ابن آجروم | 2 |
| `al-manzumah-al-bayquniyyah` | المنظومة البيقونية | 0 |
| `al-manzumah-al-tabriziyyah` | المنظومة التبريزية في العقيدة الصحيحة السنية | 1 |
| `al-rahabiyyah` | الرحبية (بغية الباحث عن جمل الموارث) | 0 |
| `hadya-awly-al-fhm-fy-nzm-hlya-talb-al-alm` | حادية أولي الفهم في نظم حلية طالب العلم | 0 |
| `lamiyyat-al-afal` | لامية الأفعال في التصريف | 0 |
| `manzumat-al-ilbiri` | تائية الإلبيري | 0 |
| `nuniyyat-abi-firas-al-hamdani` | نونية أبي فراس الحمداني (أراك عصي الدمع) | 1 |
| `tuhfat-al-atfal` | تحفة الأطفال والغلمان في تجويد القرآن | 0 |
| `unwan-al-hikam` | عنوان الحكم | 0 |

---

## 7. Not walked on the phone yet

USB phone disconnected before the final walk. Still check: people list/detail/century/work links; secondary tabs unselected; edition switching; refreshed book/poem cards and complete poem loading and saved-verse reopening; reader selection and top-bar hide; downloads and settings pages; final preview/title/subtitle/size labels; audio; note-editor unsaved draft after delete/cancel. Arabic search and root/popup selection menus were walked earlier. The test verse and bookmark remain on the phone; its temporary comment was removed, and the pre-existing excerpt was preserved.

## 8. Out of scope here (carried)

- Zad first open takes ~35 s (U5): profile after W1.
- Re-import the split-verse poems (P5) through Athar-Engine with `--import-fix`, one at a time.
- Backlog A2, B1, C and D2 from [`android/2026-10-14-backlog.md`](android/2026-10-14-backlog.md).

## 9. Verification log (continuation, 2026-10-21)

- App and core JVM tests plus debug Kotlin compile passed after W6–W11 implementation. TypeScript app-v2: 44/44 pass.
- W3/W4 phone: الإيمان footnote in footer + continuous modes shows real note above Arabic digit with a tail. Continuous hides notes/dividers. Zad opens directly on author text; «مقدمة المحقق» exists.
- Audio network error appeared with the previous local bundle; retest after regeneration.
- W9 recount against HEAD: 21 مرجع + 14 missing = 35; retained every literal متن. No library-wide automatic classification.
- Pending owner choices remain: three proposed edition defaults, library-wide kind exceptions, and soap A/B.

### Edition comparison before the pending owner decisions

Main-text characters below exclude whitespace and diacritics and omit parsed footnotes. These are completeness signals, not a declaration that two editions are interchangeable.

| source | bytes | main characters | page dividers | parsed notes / linked references |
|---|---:|---:|---:|---:|
| `jama-byan-al-alm-wfdlh` | 2,227,932 | 559,076 | 1,836 | 0 / 0 |
| `jami-bayan-al-ilm` | 736,910 | 303,307 | 605 | 0 / 0 |
| `bidayat-al-abid` | 222,931 | 48,068 | 172 | 132 / 128 |
| `bdayh-al-aabd-wkfayh-al-zhd-t-al-rkayz` | 219,643 | 48,068 | 172 | 132 / 128 |
| `bdayh-al-aabd-wkfayh-al-zahd-fy-al-fqh-ala-mthhb-al-amam-ahm` | 125,377 | 40,717 | 67 | 10 / 10 |
| `bhjh-al-mjals-wans-al-mjals` | 1,351,774 | 572,333 | 256 | 0 / 0 |
| `bahjat-al-majalis` | 1,320,867 | 575,428 | 256 | 0 / 0 |

The smaller جامع بيان العلم contains only 54% of the main-text characters of the larger import: the difference is not merely tashkeel. Do not hide either before the owner confirms the default. بداية العابد's two الركائز imports have identical counts after excluding the card; the old 139-note estimate included front-matter notes. بهجة still requires a source comparison before choosing.

Remaining library-wide kind review: [773 entries, title + current kind + status](beforepublish-kind-review.tsv). None reset automatically.

- Full TypeScript suite: first run 179/180 (large-parser test timed out at 5s under load); rerun with one worker and a 20s timeout: **180/180 pass**. `tsc --noEmit`: pass.
- Phone regression found and fixed: nested horizontal scrolling in the books filter row caused a launch crash. Removed the outer scroller; repeat installation and books/filter walk passed.
- Room instrumentation initially failed to compile because an existing test used retired collection APIs. It now seeds equivalent legacy DAO rows; test APK compiles. Device install was rejected, so **no Room device assertions ran**.

- W3 additional phone check: paged «عند الضغط» hides footer notes and opens the same real note at its Arabic digit; all three combinations checked (footer / tap / continuous).
- W5 additional phone checks: first verse saved into both poem-panel and main notebook; bookmark appears under العلامات; no empty الشرح tab on Alfiyya. Verse numbers and prose toggle apply in place.
- Phone found Alfiyya's unmarked editor front matter displayed as a false first heading. Added an explicit editor card through the page-11 marker, preserving the author’s opening and every verse; parser now supports explicit poem cards. Shared the book's intro reading page with poems. Card regression test: pass. The original generation loaded the earlier parser; its replacement full run now includes all eight poem cards and the final edition labels, so no separate targeted refresh is required.
- W7 note editor: saved a temporary `phone-check` comment to the newly created test excerpt. Remove that temporary comment in the final walk. No pre-existing excerpt altered.

- Seven more poems had the same unmarked editor/manuscript front matter as Alfiyya: `lamiyyat-al-afal`, `manzumat-al-ilbiri`, `alfiyyah-al-suyuti`, `alfiyyah-al-iraqi`, `al-jazariyyah`, `unwan-al-hikam`, `al-manzumah-al-bayquniyyah`. Read the complete front-matter ranges and marked only those through their last manuscript-list page. All eight source/verse conservation assertions pass; author openings stay outside cards. They share «مقدمة المحقق» with the book reader.
- Release verifier regression test: a signed generated fixture passes; appending corrupt bytes to its package fails. Generator tests now 11/11 pass. Full-corpus verifier awaits regeneration.
- Motion A and B preliminary APKs installed and retained locally in `dist/phone-previews/`; owner choice still open. Temporary notebook comment removed after successful save; existing user excerpts were preserved.

- Final cheap checks: **183/183 TypeScript tests**, type-check, content validation (**8,342 entries**), Android app **23/23** and core **43/43** JVM tests pass. Grep overlay gate and `git diff --check` pass.
- Formatted-book census (59): stacked dividers **642 → 9**, leading dividers **73 → 0**; remaining stacked dividers exist in source. Two expected parsed-footnote differences are Shamela bookkeeping (التدمرية / ثلاثة الأصول وشروط الصلاة), not author text. Source + metadata conservation **59/59**, poem conservation **8/8**.
- First full generation failed at Node’s default 2 GB heap limit. Restarted locally with `NODE_OPTIONS=--max-old-space-size=5120`; no root was published. First census attempt hit an unsupported table in an article; restarted for books/poems with per-100-source checkpoints and frontmatter caching disabled. Neither full-corpus gate is marked passed yet.
- USB phone disconnected during continuation. Remaining phone checks (including regenerated people/editions/card data and delete/cancel draft retention) remain pending. Corrected the people list/detail headers to use the shared back-arrow header; compile pending for that final two-line change.

- The standalone verifier also passed the **previous** signed generation (`b004389bb86e5743`): 4,240 texts / 22,420 frames / 4,752,915 blocks / 117 referenced recordings. This is a baseline only. Superscripts without targets are valid typography; linked superscripts still require a real note. Regression fixture covers both. People-header back-arrow fix compiles.

- Shared filter search now shows «لا توجد نتائج» when no option matches (previously a blank popup). Final APK rebuild and phone check pending; device still disconnected.
- Saved the pre-regeneration verse-ID baseline for all eight card-marked poems, to check bookmarks/excerpts can keep their stable anchors after the front matter moves.

- Full rebuild target census: **4,238 published texts** (1,984 articles / 1,194 books / 953 questions / 107 poems). The audio source inventory is 117 published + 854 draft; the baseline verifier checked every published recording referenced by the old catalog. Draft recordings remain hidden.

- Final inventory check caught an unapplied draft edit: `alfya-abn-malk` still had quoted `status: "published"`. Corrected that exact field and restarted the full generation, rather than editing signed artifacts. Both required imports now have `status: draft`; final catalog must contain neither ID.

- Census scopes are explicit: the signed **previous published-package** baseline has 1,303 books/poems, 18,297 stacked dividers and 43 leading dividers. The separate before/after parser audit covers all 1,346 book/poem source files, including drafts; its raw totals must not be compared directly with published-only counts. The original scratch-census estimate is not treated as final release evidence.

- Reviewed الاعتصام’s final leading marker: it ends its title page but sat immediately outside `<!-- /card -->`. Moved only that closing delimiter past the marker; the author’s basmalah and مقدمة remain in the body. Conservation **59/59** still passes. All 59 formatted books now have **zero leading dividers** (stacked total remains 9). The full run had already packaged this book, so a final one-book refresh is required. Full-source census totals need the separately measured **−1 leading-divider** correction for this post-checkpoint edit.

- Regression review found poem read-through truncation: the route opened only one frame. `openReader` now uses the existing bounded `visitTextFrames` helper for poems; partial content is marked incomplete and offers «تحميل بقية القصيدة». Saved ordinals are bounded to the current package. Annotation recovery already runs afterward and its existing shifted-ID/ordinal JVM regression passes. Final build and phone verification pending.

- Poem completeness and position fixes compile; app JVM tests **26/26 pass**, including three new partial/moved/out-of-range restore cases. Poem UI resolves saved verse/bookmark positions by block ID while annotations reattach; the shared bookmark toggle now matches stable IDs, preserving the existing approximate-location fallback. This final toggle adjustment is included in the final APK build check.

- Full signed generation completed locally: **4,238 texts**, generation `d3e011023412040a`. Final card regression review identified nine affected automatic-card prefixes (read the complete metadata-to-body boundaries); together with the earlier الاعتصام boundary fix, ten books need a small final refresh. The author’s الإيمان opening is restored to the body. Metadata-only title-page markers leave the flow, while all three source front-matter notes from العرشية / الصفدية are retained as card text. Full TypeScript suite **185/185**, type-check pass after two new card tests.

### Final local results

- Signed generation **`4a453251810def09`**: **4,238 texts, 593 people, 22,413 frames, 4,751,870 blocks, 117 published recordings**. Full signature/hash/frame/span/note/audio verification passes; catalog checks confirm three edition groups, both hidden imports, literal kinds/titles, and a substantive article preview. All eight edited poems retain **every verse and stable block ID**.
- Source census covers **1,346 books/poems**, including 45 drafts. Published-source main-text-free divider pairs (including footnote-only pages) fall **407,361 → 17,904**. This metric differs from the original scratch estimate. Final direct-adjacency count is **17,898**: **6,388** next-number source blanks, **131** forward jumps, **11,378** backward pairs, **1** volume boundary. **Zero leading dividers** in every published package; all 59 formatted books have no backward adjacent pairs. The nine remaining formatted-book blanks are literal source boundaries (e.g. بلوغ المرام 271→272); no text was invented to fill them.
- Card corrections: the original body of all ten refreshed books is retained; الإيمان لابن تيمية’s author opening is restored from the erroneous card; all three source front-matter notes remain readable in their cards. No automatic printed-page renumbering or source re-import was attempted.
- **185/185 TypeScript tests**, type-check, content validation (**8,342 entries**), **26/26 app + 43/43 core JVM tests**, popup grep gate and diff whitespace check pass. `format-verify`: **11 pass / 48 expected reject** for approved metadata/card edits; separate exact conservation **59/59** plus **8/8 poems** passes.
- Both final benchmark previews build and bundle the exact verified root/catalog/tombstones: `dist/phone-previews/athar-soap-a.apk` and `athar-soap-b.apk`. Final source flag stays B until the owner chooses; neither final APK was installed after USB disconnected. No commit, R2 upload or publication.
- Release remains open: **290 source-audit findings**, **R10 page-order anomalies**, final phone walk, Room/device certification, three edition defaults, 773 kind-review exceptions, soap choice, and explicit publication authorization. The new source anomalies need original-source comparison through Athar-Engine, rather than blind changes to printed page numbers.

- Saved-verse source integration corrected: الكناشة now carries the real source collection and opens saved verses in the poem reader. It loads the poem and reattaches the annotation before using its ordinal, so card extraction does not send «فتح المصدر» to the old verse number. Compile/JVM checks pass; final preview rebuild includes this change; disconnected-phone source-open walk remains pending.

- Explicit poem-source targets and panel jumps now pause audio following; the existing return-to-listening control resumes it. Final A/B rebuild includes this interaction fix; phone verification is still pending.

- Final preview recheck after source-opening/audio-follow fixes: A benchmark bytecode calls the travelling indicator; B does not. Both APKs have distinct dex files, pass standalone APK signature verification, and contain the exact signed `4a453251810def09` snapshot. App JVM tests remain **26/26 pass**. Temporary corpus-check scripts and the old parser copy were removed; the local content server remains available.
