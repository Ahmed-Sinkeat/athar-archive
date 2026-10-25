# Book formatting status

Updated **2026-10-25** for the **whole book-formatting project**, including earlier runs and work committed on the separate overnight branch. The ten-book heading-only review is one subset, not the project total.

## Current totals

Counting unit: **book Markdown files / editions**, not unique printed works or commits. There are **1,219 surviving files** in `src/content/book/` and `src/content/book-lg/`. Repeated passes on one file count once.

| Status | Books | Meaning |
|---|---:|---|
| Formatting pass recorded | **453** | 405 in the earlier reconciled census, 17 later passes in the current branch, and 31 finished passes on the overnight branch. This is a work-history count, not a guarantee that every source/editorial problem is resolved. |
| Structural review; full review incomplete | **10** | Previously heading-only books now have body-formatting passes, but still need complete line-by-line/semantic review. |
| Partially formatted | **3** | Known unresolved footnote/parser problems; excluded from the 453 above. |
| Skipped / deferred | **151** | 140 surviving earlier skips plus 11 later source-dependent deferrals. Includes already-clean books and books needing a source or decision; not all are blockers. |
| Remaining; no recorded formatting pass | **602** | No completed book-formatting pass or explicit skip in the reconciled ledgers. Some may have isolated page-marker repairs; that does not count as a book pass. |
| **Total surviving book files** | **1,219** | Each file appears in exactly one status. |

**466 books have formatting work recorded** (453 passes + 10 incomplete reviews + 3 partials). **151 were skipped/deferred; 602 remain without a recorded pass.** These totals cover all runs, not just the latest session.

Full title, path, status, integration state, evidence and remaining-work note for **every file**: [book-formatting-status.tsv](book-formatting-status.tsv).

## Work completed across the project

- Earlier reconciled census: **405** recorded passes — 275 Sonnet cloud, 67 earlier runs, 59 Codex nights, and four fixes recorded on 2026-10-23. These are historical ledger classifications; their gates have not all been rerun for this documentation update.
- Later current-branch work: **17** further recorded passes, plus the **10** body-review books and **one** additional partial book (أحاديث في الفتن والحوادث). The earlier partial نور التوحيد is tracked separately.
- Overnight work on `format/codex-night-2026-10-24`: **31** finished passes and **one** partial (حقوق آل البيت). These **32 books are not integrated into the current branch**; they are included in project progress and explicitly flagged in the TSV.
- Typical work includes title/editor cards, chapter hierarchy, interrupted prose, numbered entries, existing body labels, verses/quotes, footnote anchors and separately committed import repairs. Coverage differs by book; a heading-only or structural pass is not treated as a full review.
- Latest ten-book subset: 4,330 additional body labels, 119 split/malformed heading repairs and 141 poetry lines separated/spaced. See the [per-book review and commit table](book-formatting-review-2026-10-25.md). These numbers must not be added as new books.

## Remaining work and known partials

| Book / group | Remaining work |
|---|---|
| [نور التوحيد وظلمات الشرك في ضوء الكتاب والسنة](../src/content/book-lg/nur-al-tawhid-wa-zulumat-al-shirk.md) | 157 unresolved references on 40 pages; parser folds notes beginning with a closing parenthesis. Partial pass, not finished. |
| [حقوق آل البيت](../src/content/book-lg/huquq-aal-al-bayt.md) | Three bare anchors on pp. 30–31 remain unlinked after import repair; partial pass. Missing manuscript images and source citation gaps also retained. |
| [أحاديث في الفتن والحوادث ط القاسم](../src/content/book-lg/ahadith-fil-fitan-wal-hawadith.md) | Pages 50–55 need source/apparatus review: 12 dead original refs and 33 unreferenced notes remain; other 458 references checked. Partial pass. |
| Ten structural-review books | Finish line-by-line review, semantic anchor-to-note matching and remaining inline labels/poetry. Preserved source fragments and unsupported verse lines are listed in the linked review. |
| 602 remaining files | Work through the unformatted queue when authorized; the current instruction is to review already-worked books, not start new ones. |
| Earlier formatted books | Follow up on known source gaps and unresolved notes. A recorded pass does not clear the issues in the [Sonnet report](format-sonnet/REPORT.md), [earlier Codex stop report](book-formatting-codex-night.md), or individual commit messages. |
| Separate overnight branch | Review/integrate 32 committed books separately when authorized. This documentation update did not merge, cherry-pick or copy their content into the current branch. |

## Skipped and deliberately deferred

The full **151-book** skipped/deferred list is in the TSV. The following **11 later deferrals** need original-source or apparatus decisions; their wording is preserved in the TSV notes.

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

- The earlier 141 skipped files now contribute **140 surviving files**: `al-amda-fy-iarab-al-brda-qsyda-al-bwsyry.md` was explicitly deleted (`086e7f73`). It is excluded from all current totals, not counted as a remaining book.
- No guessed missing text, punctuation, hadith numbers, note boundaries or speculative anchor reconstruction.
- This documentation update did not format books or rerun gates across all 1,219 files. It checked ledger totals, current paths, commit existence and branch ancestry.
- No app build, GUI/screenshot tests, bundle/cache regeneration, merge, push or deployment. Generated bundles may still contain stale content, including the deleted book.

## How these totals were reconciled

Baseline: the 2026-10-23 status at `9efb034f` (405 recorded passes, 141 skips, 674 remaining; 1,220 files). Reconciled against current book paths, subsequent per-book commits on the current branch, the 2026-10-24/25 completion/skip ledgers and commit ancestry on `format/codex-night-2026-10-24`. The older Sonnet/Codex branches have no book commits absent from the current branch; the newer overnight branch does.

The old overall figure of 405 is therefore superseded by this project-wide reconciliation. The current source tree still excludes the 32 overnight book passes until integration; the TSV preserves that distinction. Local reports under `/home/sinkeat/Projects/athar-format-work/` supplied details, but the current inventory and outstanding-work notes are now committed here so the overview does not depend on temporary files.

Checks: exactly 1,219 unique existing paths; status counts sum to 1,219; no duplicate slugs; deleted book excluded; every referenced commit exists; all 32 unintegrated book commits verified on the named overnight branch.
