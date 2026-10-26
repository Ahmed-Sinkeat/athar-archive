# Book formatting status

Updated **2026-10-26** (everything now lives on `main`; the overnight branch was merged and all other branches were deleted — see "State on 2026-10-26" below). Previously updated 2026-10-25 for the **whole book-formatting project**, including earlier runs and work committed on the separate overnight branch. The ten-book heading-only review is one subset, not the project total.

## Current totals

Counting unit: **book Markdown files / editions**, not unique printed works or commits. There are **1,219 surviving files** in `src/content/book/` and `src/content/book-lg/`. Repeated passes on one file count once.

| Status | Books | Meaning |
|---|---:|---|
| Formatting pass recorded | **933** | 405 in the earlier reconciled census, 46 later passes in the current branch, and 32 finished passes on the overnight branch. This is a work-history count, not a guarantee that every source/editorial problem is resolved. |
| Structural review; full review incomplete | **10** | Previously heading-only books now have body-formatting passes, but still need complete line-by-line/semantic review. |
| Partially formatted | **137** | Known unresolved source/footnote problems; excluded from the 483 above. |
| Skipped / deferred | **119** | 111 surviving earlier skips plus ten later source-dependent deferrals after the first30 fresh reviews. Includes already-clean books and books needing a source or decision; not all are blockers. |
| Remaining; no recorded formatting pass | **20** | No completed book-formatting pass or explicit skip in the reconciled ledgers. Some may have isolated page-marker repairs; that does not count as a book pass. |
| **Total surviving book files** | **1,219** | Each file appears in exactly one status. |

**1,080 books have formatting work recorded** (933 passes + 10 incomplete reviews + 137 partials). **119 remain skipped/deferred; 20 remain without a recorded pass.** These totals cover all runs, not just the latest session.

Full title, path, status, integration state, evidence and remaining-work note for **every file**: [book-formatting-status.tsv](book-formatting-status.tsv).

## Work completed across the project

- Earlier reconciled census: **405** recorded passes — 275 Sonnet cloud, 67 earlier runs, 59 Codex nights, and four fixes recorded on 2026-10-23. These are historical ledger classifications; their gates have not all been rerun for this documentation update.
- Later current-branch work: **46** further recorded passes, plus the **10** body-review books and **three** partial books (أحاديث في الفتن والحوادث, فتييان تتعلقان بتكفير الجهمية and المنهل المأهول). نور التوحيد now has a finished pass after the parser repair.
- Overnight work on `format/codex-night-2026-10-24`: **32** finished passes, including the newly completed حقوق آل البيت. These 32 books were merged into `main` on 2026-10-26 (merge `0f267366`).
- Typical work includes title/editor cards, chapter hierarchy, interrupted prose, numbered entries, existing body labels, verses/quotes, footnote anchors and separately committed import repairs. Coverage differs by book; a heading-only or structural pass is not treated as a full review.
- Latest ten-book subset: 4,330 additional body labels, 119 split/malformed heading repairs and 141 poetry lines separated/spaced. See the [per-book review and commit table](book-formatting-review-2026-10-25.md). These numbers must not be added as new books.

## Continuation on the 13 flagged books

- Finished نور التوحيد: all **162** notes link after a reader bug fix (`e39a5cae`) and markup restoration (`fdeda136`). The closing-parenthesis labels had been mistaken for cut-note tails; 45 focused tests pass. Readback fixes (`3d72c015`, `5578b16c`) keep outline/compound labels together.
- Finished حقوق آل البيت on the overnight branch (`b6102fff`): all **145** notes link after three existing bare labels were converted in a strict markup pass.
- Advanced أحاديث في الفتن والحوادث (`93b0e1eb`): five verified links added; 12 dead references and 28 unused notes remain. A scanned copy of pp. 50–55 is still needed.
- Advanced eight of the ten structural-review books: **2,406** body/definition labels, **139** poetry lines spaced/separated (138 newly render as verse blocks), **three** numbered-point breaks, and **three** existing source-section titles restored. Source/parser checks passed across all ten; full editorial review remains open for all ten.

## Fresh review of all 151 previously skipped books

The user expanded the scope to **all 151 previously skipped/deferred books plus the ten unfinished reviews**. Every one of these **161 files** has received a fresh parser audit: **zero parse failures; 15 files with dead references and/or unused notes**. After the verified المنهل repairs,14 files still have parser-reported dead or unused notes. These counts identify work; they do not establish a complete source review.

The [161-book review ledger](book-formatting-rereview.tsv) records each source hash, parser counts, review state, commit and source issue. **30 supplied files have been read fully: 28 fixed and two with open source issues. Another 131 full reviews remain queued**, including the ten larger books. The initial “nothing to format” labels are not accepted as fresh review evidence.

| Newly reviewed book | Result | Commit |
|---|---|---|
| منهج أهل السنة والجماعة في السمع والطاعة | Six pages; two bracketed titles repaired, title metadata carded. | `f7b1d9d5` |
| الورقة النحوية | Both pages; 29 labels, nine numbered points, paired definitions and subsection structure formatted. | `b7597f7a` |
| تحقيق الاحتجاج بالقدر | Supplied grading excerpts; 26 verdict labels, metadata card and six duplicate page markers repaired separately. | `fe743ff5`, `b6f84a97` |
| معنى لا إله إلا الله | Four pages; three bracketed titles, 22 paragraph transitions and three labels formatted. | `f9f0af67` |
| تحقيق كلمة الإخلاص | All 29 supplied grading excerpts; labels/card plus seven duplicate page markers repaired separately. | `440ef8ae`, `ea304b08` |
| فتييان تتعلقان بتكفير الجهمية | All seven supplied pages formatted; only the first named question occurs. Original-source coverage must be checked against the two-fatwa title. Kept partial. | `9548b45c` |
| مسألة المحبة | All eight supplied pages; source title, nested knowledge-path sections and23 argument transitions formatted. | `460101e5` |
| فوائت كتاب سيبويه | All33 supplied pages; chapter subtitle/three false headings rejoined,24 lexeme labels and16 poem lines formatted. | `00c09c29` |
| تذكر رقابة الله تعالى | All11 supplied pages; nine scholar-saying blocks, three labels, prose paragraphs and chapter level formatted. | `15f06fa0` |
| هل شكرت الله على نعمه | All ten supplied pages; eight saying blocks, dialogue turns and existing transition label formatted. | `34514c7b` |
| شرح السنة للمزني | All22 supplied pages; opening/closing/prayer-list flow and19 source numbers preserved; missing17 not invented. | `3cb925ee` |
| أصول السنة لأحمد بن حنبل | Complete supplied source;31 numbered points separated, two proposition headings demoted, colophons formatted; corrupt/missing numbers retained. | `49f0f020` |
| رسالة في أن القرآن غير مخلوق | All28 supplied tags/pages;11 reports separated and43 passage boundaries processed; source gaps retained. | `7635cb68` |
| المقدمة الزهرا | All22 supplied pages; argument breaks, five source dialogue labels and one full couplet formatted; no headings invented. | `64b5690a` |
| رسالة لطيفة جامعة في أصول الفقه المهمة | All60 supplied tags; full-body paragraphs/definitions,11 chapter and two subsection headings, title/contents card. | `72d144bd` |
| رسالة في حكم السحر والكهانة | All14 supplied pages; body/report paragraphs, seven existing labels and fatwa headings. | `d753a033` |
| اختصاص القرآن بعوده إلى الرحيم الرحمن | All20 supplied pages;18 numbered items, opening/attribution paragraphs and restored source heading; missing12 retained. | `f7dc5ee1` |
| مسائل الجاهلية | Complete supplied source; enumeration labels, broken sentences/diacritics, closing;102 duplicate first markers merged separately. Source repetitions retained. | `0f50c3aa`, `d3c657ee` |
| التعليق على نصيحة العلثي | Entire supplied transcript;27 body passages, opening label, title/lecturer card; six verse blocks unchanged. Transcript remains under review. | `08ac72e1` |
| اتباع السنن واجتناب البدع | All58 supplied pages;37 reports, body transitions and53 verified poetry lines separated; source wording retained. | `82ad1139` |
| إذا أردت أن يكون دعاؤك مستجابا | Complete supplied text reviewed; body formatting and source-preservation checks pass. See per-book ledger for retained source issues. | `6d07d8b1` |
| من عمل صالحا فلنفسه | Complete supplied text reviewed; body formatting and source-preservation checks pass. See per-book ledger for retained source issues. | `10ffb21f` |
| العقيدة رواية أبي بكر الخلال | Complete supplied text reviewed; body formatting and source-preservation checks pass. See per-book ledger for retained source issues. | `d4383ce3` |
| ماهية العقل ومعناه واختلاف الناس فيه | Complete supplied text reviewed; body formatting and source-preservation checks pass. See per-book ledger for retained source issues. | `4c463ffa` |
| النصيحة في صفات الرب جل وعلا | Complete supplied text reviewed; body formatting and source-preservation checks pass. See per-book ledger for retained source issues. | `b21d7b12` |
| شرح لامية ابن الوردي | Complete supplied text reviewed; body formatting and source-preservation checks pass. See per-book ledger for retained source issues. | `eea93f58` |
| آداب العشرة وذكر الصحبة والأخوة | Complete supplied text read; body formatting and source-preservation checks pass. See ledger for details. | `c87c30ec` |
| التحذير من تعظيم الآثار غير المشروعة | Complete supplied text read; body formatting and source-preservation checks pass. See ledger for details. | `f6b0fe77` |
| أغلو في بعض القرابة وجفاء في الأنبياء والصحابة | Complete supplied text read; body formatting and source-preservation checks pass. See ledger for details. | `a5c34ec7` |
| المنهل المأهول بالبناء للمجهول لأبي الخير محمد بن ظهيرة تحقيق ودراسة | Surviving study and bibliography read/formatted; missing matn and ambiguous apparatus remain partial. Three verified note anchors relocated. | `ea151b03` |

All30 passed the strict markup gate, exact letters/digits/punctuation/diacritics/order, frontmatter/marker and rendered reading-text checks. The three page-marker repairs passed their separate import gates and exact expected marker-list checks. All62 note texts in the two note-bearing files were read: four references remain linked in من عمل صالحا فلنفسه, and المنهل now has58 linked references after three verified relocations. Its remaining source/anchor corruption is unresolved despite zero dead or unused references; the other28 files have no imported notes. The relocation check preserves reader prose and note texts while allowing only the verified superscript moves/renumbering. Sparse source references and incomplete source quotations were retained. No GUI/build checks, source reconstruction or new push in this continuation.

## Remaining work and known partials

| Book / group | Remaining work |
|---|---|
| [أحاديث في الفتن والحوادث ط القاسم](../src/content/book-lg/ahadith-fil-fitan-wal-hawadith.md) | Pages 50–55 need source/apparatus review: 12 dead original refs and 28 unreferenced notes remain; 463 references now link. Five clear page-50 citations were linked; the mismatched labels still need printed-source review. Partial pass. |
| [فتييان تتعلقان بتكفير الجهمية](../src/content/book/ftyyan-ttalqan-btkfyr-al-jhmya.md) | The supplied pages155–161 contain only the first named question despite the two-fatwa title. Formatting passes; verify original-source coverage before calling the book complete. |
| [المنهل المأهول](../src/content/book/al-mnhl-al-mahwl-balbna-llmjhwl-laby-al-khyr-mhmd-bn-zhyra-thqyq-wdrasa.md) | Surviving text formatted; source stops mid-study408, then empty matn headings/No pages placeholders, resumes bibliography501. Missing409–500 and ambiguous fn43–46 apparatus need original. Three verified anchors fixed;58 references link. Repeated400 markers retained after merge changed printed-note labels. |
| Ten structural-review books | Finish line-by-line review, semantic anchor-to-note matching and remaining inline labels/poetry. Preserved source fragments and unsupported verse lines are listed in the linked review. |
| Current authorized review | The ten unfinished reviews plus all 151 originally skipped/deferred books; see the fresh 161-target ledger below. |
| 602 remaining files | Outside the current review queue; no new formatting pass has been started on them. |
| Earlier formatted books | Follow up on known source gaps and unresolved notes. A recorded pass does not clear the issues in the [Sonnet report](format-sonnet/REPORT.md), [earlier Codex stop report](book-formatting-codex-night.md), or individual commit messages. |
| Separate overnight branch | Done 2026-10-26: merged into `main` (`0f267366`); all 32 pass `format-verify`. |

## Skipped and deliberately deferred

The current **121-book** skipped/deferred list is in the main TSV; the original 151 targets remain in the fresh review ledger, including30 now reviewed. The following **ten remaining later deferrals** need original-source or apparatus decisions; their wording is preserved in the TSV notes.

| Book | Why deferred |
|---|---|
| [الآثار المروية في صفة المعية](../src/content/book-lg/al-athar-al-mrwya-fy-sfa-al-maya.md) | Skipped unchanged: page 52 interleaves the body poem and next scholar entry inside data-notes, splits المع / ايب across body/note, and mixes the next entry with citation 1; uncertain repair boundaries |
| [المعجزات والغيبيات بين بصائر التنزيل ودياجير الإنكار والتأويل](../src/content/book-lg/al-mujizat-wal-ghaybiyyat.md) | Malformed duplicate definitions fn36/page176 and fn52/page182 precede entire main-text paragraphs, swallowing prose into apparatus; the later real definitions reuse those IDs |
| [التمسك بالسنن والتحذير من البدع](../src/content/book-lg/al-tamassuk-bil-sunan.md) | Extensive body/apparatus interleaving: page125 starts continuation of note2, followed by literal notes3–7 in body;126 interrupts matn at ومثبتة الصفات with notes1–7, while127 resumes القرآنية لا الخبرية |
| [بحث في صيغة أفعل بين النحويين واللغويين واستعمالاتها في العربية](../src/content/book-lg/bhth-fy-sygha-afal-byn-al-nhwyyn-wallghwyyn-wastamalatha-fy-al-arbya.md) | Third instalment mixes plain page markers, missing apparatus and note prose into matn: page279 has literal 1حكاية عن الكسائي انظر، إصلاح المنطق.. |
| [حقيقة شهادة أن محمدا رسول الله صلى الله عليه وسلم](../src/content/book-lg/hqyqa-shhada-an-mhmda-rswl-al-lh-sla-al-lh-alyh-wslm.md) | Page50 main quotation loses its Quran text after قول الله عز وجل and أما تقرأ; those two verse texts are interleaved with citations in data-notes entries4–7, while the body has only superscript5 |
| [التوحيد لابن عبد الوهاب](../src/content/book-lg/kitab-al-tawhid.md) | Surveyed opening, chapter structure, apparatus samples and ending |
| [مسألة في الكنائس](../src/content/book-lg/masalah-fil-kanais.md) | Missing page105/107 apparatus; unresolved note3–6 series on106 and abandoned continuations104→106→108 |
| [مكارم الأخلاق لمن أراد الخلاق](../src/content/book/mkarm-al-akhlaq-lmn-arad-al-khlaq.md) | mixed and widely displaced notes |
| [مناظرات ابن تيمية لأهل الملل والنحل](../src/content/book-lg/mnazrat-abn-tymya-lahl-al-mll-walnhl.md) | Page29 superscript1 after قائلاً precedes an apparently missing citation; first surviving note explains the الحديث في العقل marked2 |
| [نحو مير = مبادئ قواعد اللغة العربية](../src/content/book-lg/nhw-myr-mbady-qwaad-al-lgha-al-arbya.md) | Widespread shifted Markdown refs and split/interleaved fn103/104 apparatus; whole source read, unchanged. |

- The earlier 141 skipped files had **140 surviving files** before this fresh review (111 still skipped,28 now formatted and one partial): `al-amda-fy-iarab-al-brda-qsyda-al-bwsyry.md` was explicitly deleted (`086e7f73`). It is excluded from all current totals, not counted as a remaining book.
- No guessed missing text, punctuation, hadith numbers, note boundaries or speculative anchor reconstruction.
- Whole-archive validation checks ledger totals, current paths, commit existence and branch ancestry. Formatting gates were run for the targeted books; they were not rerun across all 1,219 files.
- No app build, GUI/screenshot tests, bundle/cache regeneration, merge, push or deployment. Generated bundles may still contain stale content, including the deleted book.

## How these totals were reconciled

Baseline: the 2026-10-23 status at `9efb034f` (405 recorded passes, 141 skips, 674 remaining; 1,220 files). Reconciled against current book paths, subsequent per-book commits on the current branch, the 2026-10-24/25 completion/skip ledgers and commit ancestry on `format/codex-night-2026-10-24`. The older Sonnet/Codex branches have no book commits absent from the current branch; the newer overnight branch does. Subsequent continuation finished نور التوحيد (`fdeda136`) and حقوق آل البيت (`b6102fff`), moving two partials into recorded passes without changing the total file count.

The old overall figure of 405 is therefore superseded by this project-wide reconciliation. The 32 overnight book passes were integrated on 2026-10-26; every TSV row is now at location `main`. Local reports under `/home/sinkeat/Projects/athar-format-work/` supplied details, but the current inventory and outstanding-work notes are now committed here so the overview does not depend on temporary files.

Checks: exactly 1,219 unique existing paths; status counts sum to 1,219; no duplicate slugs; deleted book excluded; every referenced commit exists; all 32 unintegrated book commits verified on the named overnight branch.

## State on 2026-10-26

- **One branch.** All formatting work (earlier Sonnet/Codex nights, the `format/bold-headings` line and the 32-book `format/codex-night-2026-10-24` branch) is on `main`. The old feature/redesign branches are preserved as `archive/*` tags, not branches.
- **Since the 2026-10-25 ledger:** تميز الصدق finished (`4dc0dd05`) and فتيا وجوابها formatted but kept partial (`befe00fc`) — the TSV was updated for both. `git log --grep '^format(book)'` touches 498 distinct book files, matching 484 + 10 + 4.
- **Android content on R2** (`app/v2/`, generation `20fceaeb`) was generated from this state, so it includes all of the above.
- **Not covered by these numbers:** 602 books have no pass, 119 are skipped/deferred, and a recorded pass is not a guarantee the source text is complete.

## Session of 2026-10-26 (markers + 149 mechanical passes)

- **Invisible structure markers** (`<!-- verse -->`, `hadith`, `athar`, `riwayah`, `ayah`): spec in [`book-markup-spec.md`](book-markup-spec.md), checked by `src/lib/markers.ts` inside `scripts/format-verify.ts`. They are HTML comments, so nothing new shows on the site or in the app, and a later style change can read them. `scripts/wrap-verses.ts` wrapped **3,043 verse blocks in 180 already-formatted books**.
- **149 of the 151 small/medium books (under 1 MB) got a mechanical pass** (one commit each): paragraph breaks between source lines, heading levels (a lone `#` becomes `##`; import headings that were really sentences became paragraphs), hemistich spacing, verse blocks, standalone-ayah blocks, and `<sup data-fn>` anchors converted to `(^N)`. Every file passes `format-verify` against its pre-session version, and linked-ref/dead-ref counts never got worse (`audit` over all 149).
- **This is not the line-by-line review of the earlier passes.** Not done in these 149: reading every anchor/note pair, bare-digit anchors (37 books keep notes without a ref; these are `partial` in the ledger), cut-note merges, bold labels, numbered-entry breaks, book cards, and any `hadith`/`athar`/`riwayah` marking (attribution is a judgment call and nothing was guessed).
- **Two books left unformatted** because the strict gate cannot prove them safe: `malmh-al-fqh-al-malky` (literal `<<…>>` in the text) and `masra-al-tasawwuf` (a `##` glued mid-line). They need a manual pass.
- `zahra-al-tqard-fy-al-nhw-al-arby` moved to `src/content/book-lg/` (the markers pushed it over the 100 KB CMS limit); its slug and URL are unchanged.
- **Not covered:** the app/site were not rebuilt and no content was regenerated or published; the Android app was not built (no SDK in this environment).

### Second half of the session: the other 433 books

The same mechanical pass (plus `<sup data-fn>` to `(^N)` where the book has them) was run over the 448 files that exist among the 453 remaining books, and **433 passed** (one commit each, all against their pre-session version, with linked/dead-ref counts never worse). 96 of them are `partial` because notes without a ref remain. Fixes made on the way: `format-verify` now ignores comment markers and a `##` glued after an anchor, so a stray `<` in a book's text cannot swallow words; multi-volume books keep their level-1 `# الجزء …` headings (notes depend on them).

**20 books still without a pass.** 15 failed because the pass made the footnote result slightly worse (a few extra dead refs) or the text cannot be proved unchanged, and 5 listed paths are not in the repo:

- `book-lg/al-bidayah-wal-nihayah.md`
- `book-lg/al-jama-al-shyh-llsnn-walmsanyd.md`
- `book-lg/al-majm-al-kbyr-lltbrany-j.md`
- `book-lg/al-mjalsa-wjwahr-al-alm.md`
- `book-lg/al-mkhlsyat.md`
- `book-lg/al-msnd-al-mwdway-al-jama-llktb-al-ashra.md`
- `book-lg/al-rawd-al-basim.md`
- `book-lg/al-tfsyr-mn-snn-sayd-bn-mnswr-mhqqa.md`
- `book-lg/dayf-aby-dawd-al-am.md`
- `book-lg/iqtida-al-sirat-al-mustaqim.md`
- `book-lg/majmu-al-fatawa.md`
- `book-lg/mkhtsr-shyh-al-imam-al-bkhary.md`
- `book-lg/msbah-al-zlam-fy-al-rd-ala-mn-kdhb-al-shykh-al-imam-wnsbh-ila-tkfyr-ahl-al-iyman-walislam.md`
- `book-lg/mward-al-zman-ila-zwayd-abn-hban-t-hsyn-asd.md`
- `book-lg/tafsir-ibn-kathir.md`
- `book-lg/tafsir-muyassar.md`
- `book-lg/tafsir-tabari.md`
- `book-lg/taliq-ala-al-tafsir-min-kutub-ibn-abi-al-dunya.md`
- `book-lg/tariq-al-hijratayn.md`
- `book-lg/tfsyr-mqatl-bn-slyman.md`

### Review pass over 100 books (2026-10-26)

A real review of the first 100 books of the review list (smallest to largest by size):

- Books 1–37 (small, `src/content/book/`) were read paragraph by paragraph. Fixes: sentence fragments that had become `##` headings, address headings, lone ordinals above headings, mis-paired or page-split hemistichs in verse, narrations cut by page markers, run-together `[N]` points; hadith/athar/riwayah markers added only around whole standalone narrations (athar = Companions/Successors/early imams).
- Books 38–100 (large, `src/content/book-lg/`) were reviewed from their outline and structure only, not read in full: heading hierarchy (باب/فصل/مبحث/مطلب), joined heading fragments, demoted sentence-style and numbered-point headings.
- Every change passed `format-verify` (letters, digits and punctuation unchanged).

Known leftovers:

- Unformatted because the formatted output does not parse or fails the gate: `taliq-ala-al-tafsir…`, `al-mnhaj-al-mkhtsr-fy-almy-al-nhw-walsrf`, `al-ftwhat-al-rbanyh-bshrh-al-drh-al-mdyh-fy-alm-al-qwaad-al` (autofix reverted), plus the other stuck large books listed above.
- Poem pairing not repaired in `al-hll-fy-shrh-abyat-al-jml` and `shrh-al-mnzwmh-al-mymyh-fy-al-adab-al-shrayh` (several adjacent lone hemistichs).
- About 147 partial books still have unlinked footnotes (bare-digit anchors, empty-definition notes); these need source-level repair, not a mechanical pass.
- Nothing was rebuilt or published (`pnpm app:gen` / `app:publish` not run); the Android changes are uncompiled.

### Books under 1 MB: review state (2026-10-26)

About 750 books are under 1 MB. After this session every one of the 112 that had only the mechanical pass has been looked at: 36 small books (`src/content/book/`) were read paragraph by paragraph, and the large ones (100 KB to 1 MB) were reviewed from their outline (headings, hierarchy, fragments, Q/A labels) only. The ledger notes now say which kind of review each of the 107 reviewed books received. The other ~640 small books keep their earlier hand or mechanical pass and have not been read line by line.

### Sweep of the other small books (2026-10-26)

All 241 small books (`src/content/book/`) not yet reviewed were swept with an automatic check for the usual Shamela import defects (fragment headings, lone closing quotes, lone opening brackets before a bracketed section title, sentence-style headings, unmarked verse). 95 books were changed (88 fragment/quote fixes, 7 bracketed section titles turned into headings, plus `nqd-mratb-al-ajmaa` sentence headings demoted); `bayan-aqeedat-ahl-al-sunnah` was reverted because the fix broke the gate. The other ~146 raised no flags. This is a defect sweep, not a line-by-line read: poems, hadith and athar markers in these books were not individually checked.

### Ten small books read line by line (2026-10-26)

`mlha-al-iarab`, `al-arbaeen-al-nawawiyyah`, `ktab-mkhtsr-al-adhkar-waladab--v2`, `al-hilm-ibn-abi-al-dunya`, `al-umr-wal-shayb`, `adab-al-ishrah`, `dhamm-al-muskir`, `aghlaq-al-abwab-lmna-al-hb-walaajab`, `atha-ardt-an-ykwn-daawk-mstjaba`, `kalam-al-layali-wal-ayyam`. Main findings: hadith blocks covered only the first paragraph of multi-paragraph narrations (now whole), most numbered hadith/athar in the collections were unmarked (about 280 narrations now wrapped, classified hadith / athar / riwayah by the narrator), split or stray bayt lines (one corrupted bayt in `mlha-al-iarab` noted, not changed), and one missing chapter heading. Narrations that contain a verse block were left unmarked, since markers cannot nest.

### The remaining 231 small books (2026-10-26)

Not read line by line. Two automatic passes, both gated by `format-verify`:

- **Narration markers:** isnad narrations (numbered «N - حدثنا…» items and «أخبرنا أحمد» units) in 46 books were wrapped whole and classified by heuristic: hadith when the Prophet is named in the matn, riwayah for «كان يقال / قال لقمان / مكتوب في الحكمة / بلغني», athar when a named Companion or Successor is the speaker. Checked against the 4 books classified by hand, the heuristic agreed on about 75 of 119 narrations; the rest differ on the hadith/athar/riwayah boundary or were left unmarked by hand. Treat these markers as provisional until a scholar reviews them. Narrations containing a verse block are left unmarked, and unclear speakers are left unmarked.
- **Chapter hierarchy:** باب / فصل / مبحث / مطلب headings were nested in 14 books.

185 books had no isnad narrations and received no marker change. None of the 231 had its poems, hadith or footnotes individually checked.
