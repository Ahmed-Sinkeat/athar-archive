# Book formatting status

Updated **2026-10-25** for the **whole book-formatting project**, including earlier runs and work committed on the separate overnight branch. The ten-book heading-only review is one subset, not the project total.

## Current totals

Counting unit: **book Markdown files / editions**, not unique printed works or commits. There are **1,219 surviving files** in `src/content/book/` and `src/content/book-lg/`. Repeated passes on one file count once.

| Status | Books | Meaning |
|---|---:|---|
| Formatting pass recorded | **471** | 405 in the earlier reconciled census, 34 later passes in the current branch, and 32 finished passes on the overnight branch. This is a work-history count, not a guarantee that every source/editorial problem is resolved. |
| Structural review; full review incomplete | **10** | Previously heading-only books now have body-formatting passes, but still need complete line-by-line/semantic review. |
| Partially formatted | **2** | Known unresolved source/footnote problems; excluded from the 471 above. |
| Skipped / deferred | **134** | 123 surviving earlier skips plus 11 later source-dependent deferrals after the first seventeen fresh reviews. Includes already-clean books and books needing a source or decision; not all are blockers. |
| Remaining; no recorded formatting pass | **602** | No completed book-formatting pass or explicit skip in the reconciled ledgers. Some may have isolated page-marker repairs; that does not count as a book pass. |
| **Total surviving book files** | **1,219** | Each file appears in exactly one status. |

**483 books have formatting work recorded** (471 passes + 10 incomplete reviews + 2 partials). **134 remain skipped/deferred; 602 remain without a recorded pass.** These totals cover all runs, not just the latest session.

Full title, path, status, integration state, evidence and remaining-work note for **every file**: [book-formatting-status.tsv](book-formatting-status.tsv).

## Work completed across the project

- Earlier reconciled census: **405** recorded passes — 275 Sonnet cloud, 67 earlier runs, 59 Codex nights, and four fixes recorded on 2026-10-23. These are historical ledger classifications; their gates have not all been rerun for this documentation update.
- Later current-branch work: **34** further recorded passes, plus the **10** body-review books and **two** partial books (أحاديث في الفتن والحوادث and فتييان تتعلقان بتكفير الجهمية). نور التوحيد now has a finished pass after the parser repair.
- Overnight work on `format/codex-night-2026-10-24`: **32** finished passes, including the newly completed حقوق آل البيت. These **32 books are not integrated into the current branch**; they are included in project progress and explicitly flagged in the TSV.
- Typical work includes title/editor cards, chapter hierarchy, interrupted prose, numbered entries, existing body labels, verses/quotes, footnote anchors and separately committed import repairs. Coverage differs by book; a heading-only or structural pass is not treated as a full review.
- Latest ten-book subset: 4,330 additional body labels, 119 split/malformed heading repairs and 141 poetry lines separated/spaced. See the [per-book review and commit table](book-formatting-review-2026-10-25.md). These numbers must not be added as new books.

## Continuation on the 13 flagged books

- Finished نور التوحيد: all **162** notes link after a reader bug fix (`e39a5cae`) and markup restoration (`fdeda136`). The closing-parenthesis labels had been mistaken for cut-note tails; 45 focused tests pass. Readback fixes (`3d72c015`, `5578b16c`) keep outline/compound labels together.
- Finished حقوق آل البيت on the overnight branch (`b6102fff`): all **145** notes link after three existing bare labels were converted in a strict markup pass.
- Advanced أحاديث في الفتن والحوادث (`93b0e1eb`): five verified links added; 12 dead references and 28 unused notes remain. A scanned copy of pp. 50–55 is still needed.
- Advanced eight of the ten structural-review books: **2,406** body/definition labels, **139** poetry lines spaced/separated (138 newly render as verse blocks), **three** numbered-point breaks, and **three** existing source-section titles restored. Source/parser checks passed across all ten; full editorial review remains open for all ten.

## Fresh review of all 151 previously skipped books

The user expanded the scope to **all 151 previously skipped/deferred books plus the ten unfinished reviews**. Every one of these **161 files** has received a fresh parser audit: **zero parse failures; 15 files with dead references and/or unused notes**. These counts identify work; they do not establish a complete source review.

The [161-book review ledger](book-formatting-rereview.tsv) records each source hash, parser counts, review state, commit and source issue. **Seventeen supplied files have been read fully: sixteen fixed and one with an open coverage issue. Another 144 full reviews remain queued**, including the ten larger books. The initial “nothing to format” labels are not accepted as fresh review evidence.

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

All seventeen passed the strict markup gate, exact letters/digits/punctuation/diacritics/order, frontmatter/marker and rendered reading-text checks. The two page-marker repairs passed their separate import gates and exact expected marker-list checks. None of the seventeen has imported notes. Sparse source references and incomplete source quotations were retained. No GUI/build checks, source reconstruction or new push in this continuation.

## Remaining work and known partials

| Book / group | Remaining work |
|---|---|
| [أحاديث في الفتن والحوادث ط القاسم](../src/content/book-lg/ahadith-fil-fitan-wal-hawadith.md) | Pages 50–55 need source/apparatus review: 12 dead original refs and 28 unreferenced notes remain; 463 references now link. Five clear page-50 citations were linked; the mismatched labels still need printed-source review. Partial pass. |
| [فتييان تتعلقان بتكفير الجهمية](../src/content/book/ftyyan-ttalqan-btkfyr-al-jhmya.md) | The supplied pages155–161 contain only the first named question despite the two-fatwa title. Formatting passes; verify original-source coverage before calling the book complete. |
| Ten structural-review books | Finish line-by-line review, semantic anchor-to-note matching and remaining inline labels/poetry. Preserved source fragments and unsupported verse lines are listed in the linked review. |
| Current authorized review | The ten unfinished reviews plus all 151 originally skipped/deferred books; see the fresh 161-target ledger below. |
| 602 remaining files | Outside the current review queue; no new formatting pass has been started on them. |
| Earlier formatted books | Follow up on known source gaps and unresolved notes. A recorded pass does not clear the issues in the [Sonnet report](format-sonnet/REPORT.md), [earlier Codex stop report](book-formatting-codex-night.md), or individual commit messages. |
| Separate overnight branch | Review/integrate 32 committed books separately when authorized. This documentation update did not merge, cherry-pick or copy their content into the current branch. |

## Skipped and deliberately deferred

The current **134-book** skipped/deferred list is in the main TSV; the original 151 targets remain in the fresh review ledger, including seventeen now reviewed. The following **11 later deferrals** need original-source or apparatus decisions; their wording is preserved in the TSV notes.

| Book | Why deferred |
|---|---|
| [الآثار المروية في صفة المعية](../src/content/book-lg/al-athar-al-mrwya-fy-sfa-al-maya.md) | Skipped unchanged: page 52 interleaves the body poem and next scholar entry inside data-notes, splits المع / ايب across body/note, and mixes the next entry with citation 1; uncertain repair boundaries |
| [المنهل المأهول بالبناء للمجهول لأبي الخير محمد بن ظهيرة تحقيق ودراسة](../src/content/book/al-mnhl-al-mahwl-balbna-llmjhwl-laby-al-khyr-mhmd-bn-zhyra-thqyq-wdrasa.md) | missing matn and displaced references |
| [المعجزات والغيبيات بين بصائر التنزيل ودياجير الإنكار والتأويل](../src/content/book-lg/al-mujizat-wal-ghaybiyyat.md) | Malformed duplicate definitions fn36/page176 and fn52/page182 precede entire main-text paragraphs, swallowing prose into apparatus; the later real definitions reuse those IDs |
| [التمسك بالسنن والتحذير من البدع](../src/content/book-lg/al-tamassuk-bil-sunan.md) | Extensive body/apparatus interleaving: page125 starts continuation of note2, followed by literal notes3–7 in body;126 interrupts matn at ومثبتة الصفات with notes1–7, while127 resumes القرآنية لا الخبرية |
| [بحث في صيغة أفعل بين النحويين واللغويين واستعمالاتها في العربية](../src/content/book-lg/bhth-fy-sygha-afal-byn-al-nhwyyn-wallghwyyn-wastamalatha-fy-al-arbya.md) | Third instalment mixes plain page markers, missing apparatus and note prose into matn: page279 has literal 1حكاية عن الكسائي انظر، إصلاح المنطق.. |
| [حقيقة شهادة أن محمدا رسول الله صلى الله عليه وسلم](../src/content/book-lg/hqyqa-shhada-an-mhmda-rswl-al-lh-sla-al-lh-alyh-wslm.md) | Page50 main quotation loses its Quran text after قول الله عز وجل and أما تقرأ; those two verse texts are interleaved with citations in data-notes entries4–7, while the body has only superscript5 |
| [التوحيد لابن عبد الوهاب](../src/content/book-lg/kitab-al-tawhid.md) | Surveyed opening, chapter structure, apparatus samples and ending |
| [مسألة في الكنائس](../src/content/book-lg/masalah-fil-kanais.md) | Missing page105/107 apparatus; unresolved note3–6 series on106 and abandoned continuations104→106→108 |
| [مكارم الأخلاق لمن أراد الخلاق](../src/content/book/mkarm-al-akhlaq-lmn-arad-al-khlaq.md) | mixed and widely displaced notes |
| [مناظرات ابن تيمية لأهل الملل والنحل](../src/content/book-lg/mnazrat-abn-tymya-lahl-al-mll-walnhl.md) | Page29 superscript1 after قائلاً precedes an apparently missing citation; first surviving note explains the الحديث في العقل marked2 |
| [نحو مير = مبادئ قواعد اللغة العربية](../src/content/book-lg/nhw-myr-mbady-qwaad-al-lgha-al-arbya.md) | Widespread shifted Markdown refs and split/interleaved fn103/104 apparatus; whole source read, unchanged. |

- The earlier 141 skipped files had **140 surviving files** before this fresh review (123 still skipped, sixteen now formatted and one partial): `al-amda-fy-iarab-al-brda-qsyda-al-bwsyry.md` was explicitly deleted (`086e7f73`). It is excluded from all current totals, not counted as a remaining book.
- No guessed missing text, punctuation, hadith numbers, note boundaries or speculative anchor reconstruction.
- Whole-archive validation checks ledger totals, current paths, commit existence and branch ancestry. Formatting gates were run for the targeted books; they were not rerun across all 1,219 files.
- No app build, GUI/screenshot tests, bundle/cache regeneration, merge, push or deployment. Generated bundles may still contain stale content, including the deleted book.

## How these totals were reconciled

Baseline: the 2026-10-23 status at `9efb034f` (405 recorded passes, 141 skips, 674 remaining; 1,220 files). Reconciled against current book paths, subsequent per-book commits on the current branch, the 2026-10-24/25 completion/skip ledgers and commit ancestry on `format/codex-night-2026-10-24`. The older Sonnet/Codex branches have no book commits absent from the current branch; the newer overnight branch does. Subsequent continuation finished نور التوحيد (`fdeda136`) and حقوق آل البيت (`b6102fff`), moving two partials into recorded passes without changing the total file count.

The old overall figure of 405 is therefore superseded by this project-wide reconciliation. The current source tree still excludes the 32 overnight book passes until integration; the TSV preserves that distinction. Local reports under `/home/sinkeat/Projects/athar-format-work/` supplied details, but the current inventory and outstanding-work notes are now committed here so the overview does not depend on temporary files.

Checks: exactly 1,219 unique existing paths; status counts sum to 1,219; no duplicate slugs; deleted book excluded; every referenced commit exists; all 32 unintegrated book commits verified on the named overnight branch.
