# Athar Archive redesign — typed works, honest chips, book-like reading

## Context

Ahmed's screenshot review surfaced structural problems, not just bugs: the hadith popup shows تخريج/حكم from the *wrong* hadith (20-char text-prefix matching collides), فوائد is a redundant tab, الأربعون النووية exists twice with no explanation, card chips are misleading topic labels (حديث عام, عام, منظومة+متن doubled), poem titles are scraped junk (عَلَيْهِ), the Muwatta TOC repeats "مقدمة المؤسسة … صفحات X-Y" twenty times, and footnotes live in a confusing popup. He approved a phased model redesign: typed works (متن/شرح/تفسير/ديوان…), شرح↔أصل links, inline expandable شرح, printed-book-style footnotes. Priority: (A) book model, (B) reading experience, then (C) hadith accuracy, (D) poems.

Repos: site = `/home/sinkeat/Projects/athar-archive` (Astro + Cloudflare), engine = `/home/sinkeat/Projects/Athar-Engine` (exporters).

**User decisions (fixed):** drop muharrar/mowatta auto-matched تخريج/حكم entirely; merge فوائد into شرح; merge the two أربعين into one (clean slug, redirect); one card chip = work type only; شرح renders as inline `<details>` under each hadith/bayt; footnotes render under each page separator like a printed footer (no popup); poem titles = real occasion when clean, else first hemistich.

**Facts found during design (correct earlier assumptions):**
- "اقرأ في موضعه" hrefs are **paragraph** ordinals (`#pN` from `paragraphNumberFor`, `export-hadith-index.ts:179,275`), not pages — fix emits page anchors instead.
- `/book/<slug>` already lands on an overview; the "land on overview" item is an inbound-link audit.
- `validate.ts:98-106` requires `sharh_of` → book; must accept poem targets for sharh-lamiyyat.

## Phase A — Book model + شرح↔أصل

**A1. Single work-type chip.** Source of truth: `work_type` frontmatter.
- `src/content.config.ts`: extend `workTypeField` enum with `تفسير، سيرة، ديوان، فتاوى، قصيدة`.
- `src/lib/display.ts` `labelFor()`: book → `work_type || kind || "كتاب"`; poem → `work_type || "منظومة"`.
- `src/lib/site.ts`: delete `topicLabelFor()`; replace its call sites with `labelFor` (books/matn/tafsir/hadith/index/person/book pages).
- `src/components/EntityCard.astro`: remove `matn` prop + `badge-matn` span (one chip only); fix callers (`poems.astro:24-25` hardcoded `kind:"منظومة", matn:true`, `person/[slug].astro`, `matn.astro`).
- Frontmatter pass on `src/content/book/*.md`: متن = arbaeen (merged), wasitiyyah, kitab-al-tawhid, zad, umdah; شرح = the 5 sharh-* books; تفسير = ibn-kathir (change from مصدر أصلي), muyassar, taysir-latif-mannan; ديوان = diwan-ilbiri; فتاوى = fatawa-shanqiti; سيرة = sirah-ibn-hisham; مصدر أصلي = mowatta, muharrar; فهرس = siraj. `kind:` stays (drives isMatn/chunking, not the chip).

**A2. شرح↔أصل links.**
- `sharh-lamiyyat-ibn-taymiyyah.md`: add `sharh_of: "lamiyyat-ibn-taymiyyah"` (poem).
- `src/lib/validate.ts`: resolve `sharh_of` against book **or** poem.
- `src/pages/book/[slug].astro`: when `sharh_of`/`mukhtasar_of`/`hashiyah_on` set, render `شرحٌ لـ: <a>الأصل</a>` under the byline (one small helper).
- `src/pages/poem/[slug].astro`: add «شروحه» block via `graph.commentariesOf` (copy the book-page pattern).

**A3. أربعين merge.** Survivor slug `al-arbaeen-al-nawawiyyah`, content = the hub.
- Delete plain `al-arbaeen-al-nawawiyyah.md`; rename `-epub.md` onto the clean slug; title → "الأربعون النووية"; `aliases: ["al-arbaeen-al-nawawiyyah-epub"]`; `work_type: متن`.
- Both sharh-arbaeen-* books: `sharh_of: "al-arbaeen-al-nawawiyyah"`.
- Engine `export-hadith-index.ts`: hub slug + `attachesTo` → clean slug (regenerate once, together with Phase C).
- `scripts/gen-redirects.ts` (site): aliases already emit 301s; add a `/:splat` line for book aliases so old *chapter* URLs redirect too (book is chunked).

## Phase B — Reading experience

**B1. TOC collapse.**
- `src/lib/chapters.ts` `RawChapter` + `src/lib/book-asset.ts` `ChapterMeta`: optional `parent`, `parentTitle`.
- `src/lib/chunk.ts` `splitOversizedChapters()`: slices carry `parent: c.slug, parentTitle: c.title`; slice title = just `صفحات S-E`.
- `scripts/gen-book-chapters.ts`: manifest includes `parent`, `parentTitle`, `firstPage` (first `data-page` in slice — needed by B5).
- `book/[slug].astro` TOC + `[chapter].astro` sidebar: one entry per `parent ?? slug` group (link → first slice); current parent's sibling slices as nested sub-links; h1 shows `parentTitle` with the range as subtitle; breadcrumb uses `parentTitle ?? title`. Prev/next already walks the flat manifest.

**B2. Affordances + landing.**
- Audit inbound links that deep-link to a first chapter → point at `hrefFor("book", id)` overview.
- Visible «فصول الكتاب» toggle/icon in `.reader-head`; style the mobile TOC summary as a clear button.
- Page separator becomes a static `ص N` marker (no popup after B4) — remove click affordance ambiguity. No popup opens unprompted (structural: `injectPageNotes` deleted in B4).

**B3. Inline expandable شرح (native `<details>`, zero JS).**
- `book/[slug]/[chapter].astro`: for hub chapters, drop the `ann-mark`/`data-ann` h1 wiring and hidden hadith `.ann-pack`; render after the prose one `<details class="inline-note k-sharh">` per note: `<summary>شرح — المصدر</summary>` + sanitized body + «اقرأ في موضعه» link. Order شرح → تخريج → حكم.
- Poem pages: same `<details>` under annotated verses for whole-verse شرح.
- **Ann-sheet stays** for Quran tafsir verse taps and phrase-level marks (verse-dense pages would drown in details blocks). `reader.ts` untouched here beyond dead bindings.
- `global.css`: one `.inline-note` block reusing `k-sharh`/`k-takhrij` accents.

**B4. Page-footer footnotes.**
- `[chapter].astro`: replace the `data-notes` JSON packing with `footerHtmlByPage` (DB حواشي + `extractFootnotesByPage` bodies via `markdownToSafeHtml`, items `<li id="fn-{page}-{i}">`); render `<aside class="page-footnotes"><ol>…</ol></aside>` before each next page-sep + after last content. Strip `[^id]:` defs pre-render (`stripFootnoteDefs` next to `extractFootnotesByPage` in `chapters.ts`); rewrite inline `[^id]` refs to `#fn-{page}-{i}` anchors.
- `reader.ts`: delete `injectPageNotes()` + the page-sep/footnote-ref click handlers. Marker click = native anchor; `:target` highlight in CSS.
- `global.css`: `.page-footnotes` — hairline rule, smaller font, printed-footer look.

**B5. "اقرأ في موضعه" anchors.**
- `id="p{page}"` on page-sep divs (done in B4 markup).
- Engine `export-hadith-index.ts`: `sourceHref` uses nearest preceding `data-page` (like `export-poems.ts` `nearestPage()`) instead of `paragraphNumberFor` (which silently falls back to 1).
- Chunked books: hash never reaches the server — overview `book/[slug].astro` gets a tiny inline script + JSON page-map (`firstPage` from manifest): on `#p(\d+)`, `location.replace` to the right chapter slice. (Same paragraph-anchor bug exists in `export-quran-tafsir-index.ts` — follow-up, out of scope.)

## Phase C — Hadith index accuracy (engine, regenerate once with A3)

`scripts/export-hadith-index.ts` + test:
1. Remove `muharrar-fi-al-hadith` and `mowatta-malik` from `NOTE_SOURCES`; delete `processMatnPrefixNoteSource`. Keep umdah hub + `crossReferenceHubs` (flag: same 20-char matching — one-line removal if wrong cross-refs observed).
2. فوائد → شرح: `labelToKind` collapses; narrow `HadithNote["kind"]` to `شرح|تخريج|حكم`.
3. Dedupe: merge notes sharing `(anchor, sourceSlug, kind)` (join bodies), drop exact-duplicate bodies.
4. Rerun → commit `athar-archive/src/data/hadith-index.json`. Site: drop `فوائد` from `HADITH_KIND_CLASS` in `[chapter].astro`.
5. Docs: check off fixed items in `docs/TODO-website-issues.md`; ADR-0013 amendment note (sources dropped, hub re-slugged, page-anchor hrefs).

## Phase D — Poems (engine)

`scripts/export-poems.ts` + test:
1. Replace `FORMULA_RE` with token-vocabulary stripping (normalize via `normalizeArabic`; vocabulary: وقال، وله، رحمه، رضي، عنه، عليه، تعالى، ونضر، وجهه، الالبيري variants، ابواسحاق، الاستاذ، الزاهد، الغرناطي…). Keep occasion title only if a non-vocabulary token remains; else first-hemistich fallback (existing path). Kills عَلَيْهِ / وَرَضي عَنهُ / the 3 الألبيري spellings.
2. Emit `work_type: "قصيدة"` in poem frontmatter (pairs with A1 enum).
3. Rerun exporter (idempotent, owns `diwan-ilbiri--*`).

## Verification

- Engine: `pnpm test`; rerun both exporters; check `hadith-index.json` has no muharrar/mowatta sourceSlug, no فوائد kind, keys `al-arbaeen-al-nawawiyyah:N`, hrefs point at real pages.
- Site: `pnpm test` (update chunk/chapters/book-asset tests for `parent`/`firstPage`), `pnpm validate:content`, `pnpm build`, `pnpm smoke`, `pnpm check:links`.
- Manual: one chip per card everywhere; `-epub` URLs 301 (incl. a chapter URL); arbaeen chapter shows `<details>` شرح (عثيمين/مناوي only) and «اقرأ في موضعه» lands on the right page/slice; Muwatta TOC shows «مقدمة المؤسسة» once; a tafsir-ibn-kathir chapter shows footnotes under each page with no popup; sharh-lamiyyat page shows «شرحٌ لـ»; poem page shows «شروحه»; Quran verse tap still opens the sheet.

**Sequencing:** A1→A2 independent; A3 with C (one regeneration); B1 before B5; D independent.
