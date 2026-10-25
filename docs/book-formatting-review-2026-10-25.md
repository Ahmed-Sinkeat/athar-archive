# Review of previously heading-only books — 2026-10-25

This report covers only the ten-book review subset. For all runs, totals and remaining/skipped books, see [project-wide formatting status](format-status.md).

Branch: `format/bold-headings`. Scope follows the user’s instruction to review books already touched and not start new ones.

History identifies **11 original heading-only books: ten surviving books reviewed, one explicitly deleted**. This latest pass changed nine books in separate commits; أبنية الأسماء والأفعال والمصادر needed no further changes in this pass. All ten already have body-formatting changes from earlier review passes.

**Status: structural review verified; full editorial review remains incomplete.** Passing the formatting gate proves preservation, not that every paragraph, verse or footnote has been reviewed semantically.

## Work already completed before the latest pass

| Book | Body work already committed | Commit |
|---|---|---|
| [أدوات الإعراب](../src/content/book-lg/adwat-al-iarab.md) | Rejoined 41 false-heading interruptions and nine split entries; restored lemma/subsection structure, 39 labels and 226 numbered body items; title-only card. | `3e059d3e` |
| [أبنية الأسماء والأفعال والمصادر](../src/content/book-lg/abnya-al-asma-walafaal-walmsadr.md) | Formatted 1,559 pattern labels plus other definitions, 28 verses, repaired interrupted prose and separated title material. | `edc27750` |
| [الفواكه الدواني على رسالة ابن أبي زيد القيرواني](../src/content/book-lg/al-fwakh-al-dwany-ala-rsalh-abn-aby-zyd-al-qyrwany.md) | Formatted 1,790 body labels and 296 first points; title card. | `066ff87f` |
| [البيان والتحصيل](../src/content/book-lg/al-byan-walthsyl.md) | Formatted 8,367 commentary labels, 408 enumerated points and 36 clear commentary starts; repaired hierarchy and title card. | `fc8f3495` |
| [البيان في مذهب الإمام الشافعي](../src/content/book-lg/al-byan-fy-mthhb-al-amam-al-shafay.md) | Formatted 6,539 enumeration labels; repaired two split chapter titles, card and page-marker spacing. | `fcdb05cd` |
| [العزيز شرح الوجيز المعروف بالشرح الكبير ط العلمية](../src/content/book-lg/al-azyz-shrh-al-wjyz-al-marwf-balshrh-al-kbyr-t-al-almyh.md) | Formatted 22,569 body labels and retained seven adjacent heading references in reader output; author introduction kept in flow. | `af704bea` |
| [الإشراف على نكت مسائل الخلاف](../src/content/book-lg/al-ashraf-ala-nkt-msayl-al-khlaf.md) | Formatted 2,110 issue/section labels, ten joined entries, 21 list points and five verse paragraphs; four recognized as verses; card and hierarchy. | `505d141b` |
| [الإنصاف في معرفة الراجح من الخلاف للمرداوي](../src/content/book-lg/al-ansaf-fy-marfh-al-rajh-mn-al-khlaf-llmrdawy.md) | Formatted 9,301 labels and 685 first points; title card. | `01f527be` |
| [الأم للشافعي](../src/content/book-lg/al-am-llshafay.md) | Formatted 6,077 parenthetical attributions and 318 other labels; title card. | `099822cb` |
| [العدة شرح العمدة](../src/content/book-lg/al-adh-shrh-al-amdh.md) | Formatted 2,107 body labels, repaired 11 joined issues and title card. | `d60438a2` |

These are earlier-pass counts, not additions from the latest pass. Do not add paragraph counts across passes: later repairs can split or rejoin the same material.

## Latest verified pass

**4,330 additional body labels; 119 split/malformed heading repairs; 141 poetry lines separated/spaced.** Of those poetry lines, 139 now render as verse blocks and two remain prose under the current detector.

| Book | Body labels | Heading repairs | Poetry lines | New commit |
|---|---:|---:|---:|---|
| أدوات الإعراب | 4 | 0 | 0 | `1c1ec452` |
| أبنية الأسماء والأفعال والمصادر | 0 | 0 | 0 | Unchanged; prior commit above |
| الفواكه الدواني على رسالة ابن أبي زيد القيرواني | 19 | 7 | 0 | `47390d4c` |
| البيان والتحصيل | 13 | 0 | 47 | `6a3ad6ca` |
| البيان في مذهب الإمام الشافعي | 2,956 | 0 | 0 | `98296307` |
| العزيز شرح الوجيز المعروف بالشرح الكبير ط العلمية | 1,240 | 106 | 0 | `94566d83` |
| الإشراف على نكت مسائل الخلاف | 10 | 0 | 0 | `10ee200d` |
| الإنصاف في معرفة الراجح من الخلاف للمرداوي | 37 | 0 | 94 | `e1fd61a2` |
| الأم للشافعي | 0 | 6 | 0 | `b440e94a` |
| العدة شرح العمدة | 51 | 0 | 0 | `d06e0fbf` |

Heading repairs include rejoining diacritics to their existing words, restoring the separation between attribution and section title in العزيز, and using intact existing titles beside five ambiguous duplicate fragments in الأم. No source letters, punctuation or diacritics were removed.

## Verification completed

- All ten books passed `scripts/format-verify.ts` before the latest commits.
- Independent comparison preserved source letters, digits, punctuation, diacritics and order, ignoring only permitted markup and whitespace; frontmatter, page markers and footnote-definition lines stayed unchanged.
- Parser checks preserved rendered reading text after normalizing whitespace and verse display separators/parentheses. All rendered note objects and reference-target order stayed unchanged.
- العزيز retains all **14,430 notes and 14,430 linked references**, with zero dead references and zero unreferenced notes. The other nine books report zero imported footnote objects; this does **not** prove their printed/editorial notes are absent or complete.
- No inline `##`/`###` interruptions or headings beginning with a detached vowel remain in the scanned cohort. A restricted scan found no consecutive `N-`/`Nـ` items run together in one paragraph; other numbering styles are not covered by that assertion.
- `git diff --check` passed; the nine content commits left a clean working tree.

## Remaining work

| Area | What remains |
|---|---|
| All ten books | Complete line-by-line review against all formatting rules, including remaining inline labels, quoted half-verses and other poetry candidates. The structural scans do not establish completion. |
| Footnotes | Semantic anchor-to-note verification, including all 14,430 notes in العزيز. Existing targets were preserved and mechanically checked, not all reread individually. |
| الأم | Five leading-vowel duplicate-title fragments and another orphan kasra remain verbatim; intact titles now supply navigation. Source comparison is needed before changing their text. |
| الإنصاف | Two separated poetry lines still render as prose under the current detector. Source text and punctuation are intact. No parser code was changed. |
| الإشراف | Editor apparatus on pp. 659–669 remains embedded in prose without usable anchors; p. 317 has an orphan printed (1); p. 194 is empty. A four-dot verse separator remains unsupported. Existing numbering gaps/duplicates need source review. |
| البيان في مذهب الإمام الشافعي | Malformed source prefixes, including 2,176 lines beginning `]:`, remain; detached `7 -` before volume 7 p. 502 is ambiguous. More inline labels and poetry remain outside the completed label pass. |
| البيان والتحصيل | Seven detached number/dash lines and ambiguous commentary/quotation boundaries remain unchanged. |
| أدوات الإعراب / أبنية الأسماء | Source spelling, truncated wording and missing list numbers were not reconstructed. |
| Archive-wide status | Superseded by the project-wide reconciliation in [format-status.md](format-status.md) and its full TSV inventory. Counts distinguish committed work on a separate branch from current-branch content. |

## Deleted and deliberately skipped

- `al-amda-fy-iarab-al-brda-qsyda-al-bwsyry.md` was deleted at the user’s explicit request (`086e7f73`), together with its associated unused source records. It remains absent and must not be restored as a review task.
- New books were excluded from this review. Five books formatted earlier, before the no-new-books instruction, are separate work: `al-risalah-al-mufidah` (`7ad3d2bd`, import fix `fa2683b1`), `akhls-al-aml-fan-al-naqd-bsyr` (`853b081a`), `abnawna-walslah` (`6dbf42e1`), `hl-jahdt-nfsk` (`93d88565`), and `hl-ant-mn-al-mraqbyn-llh-taala` (`699b6c5f`). They are not part of the ten-book cohort or its counts.
- No guessed source reconstruction, supplied missing numbers, speculative footnote anchors, or punctuation/spelling corrections.
- No app build, GUI/screenshot checks, full end-to-end tests, bundle/cache regeneration, merge, push or deployment. The ignored generated bundle was not refreshed after the deletion, so generated/distributed content must not be assumed current.

## Evidence and next continuation

Committed book diffs and commit messages are the durable evidence. Detailed latest checks and backups currently live at `/tmp/athar-heading-review/` and can disappear when temporary files are cleared. Earlier per-book reports live under `/home/sinkeat/Projects/athar-format-work/`; these local paths are not portable repository artifacts.

Continue with the remaining review of these ten existing books. Keep source-dependent repairs separate from markup-only commits, and preserve ambiguous text until the original source can resolve it. The earlier overnight-run report is historical and is not an instruction to start new books.
