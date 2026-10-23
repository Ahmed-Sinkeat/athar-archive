# Duplicates and editions — proposal (2026-10-23)

Found by comparing the text of all 1238 books (shared 4-word runs) plus same-title matches,
then reading each card's المحقق / الناشر. **Applied 2026-10-23** as written (owner approved, ⚠ rows
included); deleted books redirect from `public/_redirects`.

**Duplicate** = the same edition (same محقق and publisher) imported twice → keep one, delete the
other, and redirect its old URL to the kept book.
**Edition** = a different تحقيق or printing → keep all, link them as one book. The default opens
first; the others sit in the «الطبعات» menu.

How "best" was picked: the more complete text first (the smaller copy is usually missing pages),
then a known better تحقيق, then the one already formatted or with linked notes.

## A. Duplicates — delete (18)

| # | Keep | Delete | Why |
|---|---|---|---|
| 1 | `bahjat-al-majalis` | `bhjh-al-mjals-wans-al-mjals` | 95% same text, same size |
| 2 | `amdh-al-talb-lnyl-al-marb` | `ktab-amda-al-talb-lnyl-al-marb` | same تحقيق (الجاسر); kept one is fuller |
| 3 | `bidayat-al-abid` | `bdayh-al-aabd-wkfayh-al-zhd-t-al-rkayz` | same تحقيق (اليتامى) |
| 4 | `al-amwal-labn-znjwyh` | `al-amwal-ibn-zanjawayh` | same تحقيق (فياض); deleted one is 950 KB of 1477 |
| 5 | `al-amr-balmarwf-walnhy-an-al-mnkr-llkhlal` | `al-amr-bil-maruf-al-khallal` | same تحقيق (يحيى مراد); 110 KB vs 67 |
| 6 | `al-thwr-llqasm-bn-slam` | `al-tuhur-al-qasim-ibn-sallam` | same publisher; 244 KB vs 150 |
| 7 | `umdat-al-fiqh-ibn-qudamah` | `amdh-al-fqh` | identical (عزوز, العصرية) |
| 8 | `akhsr-al-mkhtsrat-t-al-rkayz` | `ktab-akhsr-al-mkhtsrat-t-rkayz` | same تحقيق (العيدان); 252 KB vs 196 |
| 9 | `ahkam-al-nisa-al-khallal` | `ahkam-al-nsa-llamam-ahmd` | same تحقيق (عمرو سليم); kept one is formatted |
| 10 | `fadl-ilm-al-salaf` | `byan-fdl-alm-al-slf-ala-alm-al-khlf` | same text; 40 KB vs 33 |
| 11 | `dlyl-al-talb-lnyl-al-mtalb` | `ktab-dlyl-al-talb-lnyl-al-mtalb` | 89% same; kept one has the card (الفاريابي) |
| 12 | `mtmma-al-ajrwmya` | `mutammimat-al-ajurrumiyyah` | 86% same; 74 KB vs 69 |
| 13 | `mtn-al-akhdry-fy-al-abadat-ala-mthhb-al-amam-malk` | `ktab-mtn-al-akhdry-fy-al-abadat-ala-mdhhb-al-imam-malk` | the small one is wholly inside the big one |
| 14 | `mtn-al-ashmawyh` | `ktab-mtn-al-ashmawya` | same: small one inside the big one |
| 15 | `jama-byan-al-alm-wfdlh` | `jami-bayan-al-ilm` | same تحقيق (الزهيري); deleted one is 428 KB of 1223 |
| 16 | `adab-al-mashy-ila-al-salah` (+ its primary) | `adab-al-mshy-ala-al-slah-t-al-wzarh` (draft) | same publisher (الوزارة) |
| 17 ⚠ | `tthbyt-al-imama-wtrtyb-al-khlafa-laby-naym-al-asbhany` | `al-imamah-wal-radd-ala-al-rafidah` | same work by أبو نعيم under two titles; the deleted one has no card, so it may be another تحقيق |
| 18 ⚠ | `qata-al-afrah-wmzhq-al-arwah-al-mwt` | `matha-aaddt-llmwt` | same author, 90% same text, two titles |

## B. Editions — link as one book (22)

| # | Default (opens first) | Other editions | Why this default |
|---|---|---|---|
| 1 | `al-snn-al-kbra-llbyhqy-t-al-trky` (التركي) | `al-snn-al-kbra-llbyhqy` (عطا) | newer, fuller تحقيق |
| 2 | `al-shrh-al-kbyr-ala-al-mqna-t-al-trky` (التركي) | `al-shrh-al-kbyr-ala-mtn-al-mqna` (دار الكتاب العربي) | محقق, 16 MB vs 8.7 |
| 3 | `al-ansaf-fy-marfh-al-rajh-mn-al-khlaf-t-al-trky` (التركي) | `al-ansaf-…-llmrdawy` (إحياء التراث) | محقق |
| 4 | `sunan-al-tirmidhi` (بشار) | `snn-al-trmdhy-t-shakr` (شاكر) | complete تحقيق |
| 5 | `shyh-abn-khzyma-t` (ط ٣) | `shyh-abn-khzyma` | later, fuller printing |
| 6 | `al-aqtrah-fy-aswl-al-nhw-t-al-qlm` (القلم) | `…-t-al-byrwty` (البيروتي) | fuller |
| 7 | `bidayat-al-abid` (اليتامى) | `bdayh-al-aabd-wkfayh-al-zahd-…` (العجمي) | fuller |
| 8 | `akhsr-al-mkhtsrat-t-al-rkayz` (العيدان) | `akhsr-al-mkhtsrat` (العجمي) | fuller; the العجمي import lost its first 84 pages |
| 9 | `zad-al-mustaqni` (القاسم) | `zad-al-mstqna-fy-akhtsar-al-mqna` (العسكر) | already linked |
| 10 | `al-iman-al-qasim-ibn-sallam` | `al-iyman-llqasm-bn-slam-mkhrja` (الألباني) | formatted, 71 pages of linked notes |
| 11 | `al-bath-walnshwr-llbyhqy-t-al-shwamy` (الشوامي) | `al-bath-walnshwr-llbyhqy` (حيدر) | fuller |
| 12 | `al-jama-labn-whb-t-mstfa-abw-al-khyr` (أبو الخير) | `…-t-rfat-fwzy-abd-al-mtlb` (رفعت فوزي) | fuller |
| 13 | `al-mntkhb-…-t-mstfa-al-adwy` (العدوي) | `…-t-sbhy-al-samrayy` (السامرائي) | fuller |
| 14 | `hlyh-al-alma-…-t-al-rsalh-al-hdythh` | `hlyh-al-alma-fy-marfh-mthahb-al-fqha` (درادكة) | fuller (4.4 MB vs 0.7) |
| 15 | `mowatta-malik` (الأعظمي) | `mwta-malk-t-abd-al-baqy` (عبد الباقي) | fuller |
| 16 | `musnad-ahmad` (الرسالة) | `msnd-ahmd-t-shakr` (شاكر) | شاكر's تحقيق never finished |
| 17 | `mstkhrj-aby-awana-t-al-jamaa-al-islamya` | `mstkhrj-aby-awana` (الدمشقي) | fuller |
| 18 | `mward-al-zman-…-t-hsyn-asd` (حسين أسد) | `mward-al-zman-ila-zwayd-abn-hban` (حمزة) | fuller |
| 19 | `rsala-al-mlayka-t-al-jndy` (الجندي) | `rsala-al-mlayka` (الميمني) | fuller |
| 20 | `sunan-ibn-majah` (الأرنؤوط) | `snn-abn-majh` (عبد الباقي) | fuller |
| 21 | `sunan-abi-dawud` (الأرنؤوط) | `snn-aby-dawd` (عبد الحميد) | fuller |
| 22 | `al-ajurrumiyyah` | `al-ajrwmya` (الصميعي) | already linked |

Left apart on purpose: `shyh-abn-hban-mkhrja` and `al-talyqat-al-hsan-ala-shyh-abn-hban` (الألباني's
work on the صحيح, with his own grades). That is a different book, not a printing. There are also 92
pairs where a short text sits inside a bigger book (a متن inside its شرح, a رسالة inside a مجموع);
those are not duplicates.

## Counts

The public counts (home page, lists, author pages, topics, search) count a book with several editions
as **one**. `pnpm audit:content` prints both numbers: works and edition files.
