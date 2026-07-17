# Quran/Hadith sections + connectivity layer — as-built

All 5 phases complete and on `main`. Self-contained record; assume zero prior context.

## Where
- Repo: `github.com/Ahmed-Sinkeat/athar-archive`. Landed on `main` from `feat/quran-hadith-connectivity`.
- Stack: Astro (no backend), hybrid Cloudflare-Worker rendering. Content = Markdown collections under `src/content/`. Package manager: **pnpm**. Arabic / RTL throughout.

## Verified (lean gate)
`pnpm test` → 80 pass · `pnpm validate:content` → 1025 entries · `pnpm exec tsx scripts/epub-import.ts --selftest` → green · `pnpm build` → exit 0.

## Load-bearing insight (why this was mostly reuse)
- `buildGraph` (`src/lib/graph.ts`) already "replaces a relational DB": forward indexes + `backlinksFor` (reverse edges) + narrator edges.
- Bodies derive stable **anchors** (`extractAnchors` in `src/lib/chapters.ts`: `v{n}` poems, `p{n}`/`{#id}` books). The `annotation` collection layers commentary onto `target_id` + `anchor`, shown via the reader's شرح chooser.
- So: **Quran tafsir-per-آية and Hadith تخريج/grading are just "annotations on an anchor"**, **a narrator is just a `person`** (`tabaqa` already = الصحابة/التابعون/…), and **backlinks are reverse edges over `buildGraph`**.

## Done — all 5 phases
- **Phase 1 — Foundation.** `book.genre` enum (`قرآن|حديث|تراجم`) orthogonal to `kind`; importer `--genre` + `GENRE_FOLDER_MAP` (source رار فن folder → section) in `scripts/epub-import.ts`; `/quran` `/hadith` `/tarajim` genre-filtered section pages (`buildSubjectGroups` gained an optional filter); nav wired in `src/layouts/Base.astro`.
- **Phase 2 — Connectivity core.** `graph.backlinksFor` + `site.backlinksList` + `src/components/Relations.astro` («ما يشير إلى هذا» — collapsible, end-of-page) on book/poem/person/surah pages; `[[type:slug|label]]` wiki-links via `rehypeWikiLinks` in `src/lib/sanitize.ts` + shared `src/lib/wikilink.ts`. Hover "preview" = native `title` tooltip (subtle, JS-free — the lazy version of the planned hover card). Tests in `wikilink.test.ts` + `graph.test.ts`.
- **Phase 3 — Hadith units + grading.** Importer hadith path: footer `الحديث:N` → `{#hN}` anchors, `class="title"` كتاب/باب → `##`, `[التخريج]` → `kind:تخريج` annotation stubs. `annotation.grade` enum (`صحيح|حسن|ضعيف|موضوع`); grading facet on `/hadith` aggregating graded تخريج annotations. `--selftest` covers footer→`hN` + `[التخريج]` split.
- **Phase 4 — Quran spine + tafsir.** `quran` content collection (114 surahs parsed in-house from the mushaf epub: `number`/`name`/`ayah_count`); `/quran` browse + `/quran/[surah]` ayah reader with per-آية tafsir notes (`notesByAnchor`); `annotation` gained `target_type:"quran"` + `kind:"تفسير"`.
- **Phase 5 — Narrator/تراجم graph.** `person.rutba` (جرح/تعديل) + `person.narrates_from` (curated شيوخ edges); تلاميذ derived in-graph (`shuyukhFor`/`talamidhaFor`); person→person edges in `src/pages/graph.astro`; شيوخه/تلاميذه lists + `rutba` pill on `person/[slug].astro`; semi-link mentions («ذُكر في N موضعًا» → search) in the Relations panel only, gated by `also_known_as` match.

## Tafsir sources (as of 2026-07-17)

`src/data/quran-tafsir-index.json` (gitignored, fetched from R2 — see
HANDOFF-perf-size.md M5) now carries 8 sources across 2 kinds, one script
per source's citation format:

- **تفسير** (7): التفسير الميسر, تفسير ابن كثير, تيسير اللطيف المنان
  (السعدي, pre-existing) · تفسير مقاتل بن سليمان (`gen-tafsir-index-muqatil.ts`
  — inline `-N-` markers, range-inferred) · عبد الرزاق الصنعاني (`gen-tafsir-index-tagged.ts`
  — explicit `[سورة:آية]` citation tags) · تفسير القرآن العزيز لابن أبي زمنين
  (`gen-tafsir-index-zamanin.ts` — trailing "من الآية N إلى الآية M" range
  footers) · تيسير الكريم الرحمن (السعدي، `gen-tafsir-index-sadi.ts` — its
  own chapter headings are unreliable, surah resolved by cross-matching the
  quoted verse text against the `quran` collection) · التفسير القيم لابن
  القيم (`gen-tafsir-index-qayyim.ts` — chapter headings + فصل sub-section
  accumulation; thematic essays, so coverage is sparse but per-entry quality
  is high).
- **تعليق** (1, new kind — not lumped under تفسير since it's a scholar's
  commentary gathered from athar, not a primary tafsir text): التعليق على
  التفسير من كتب ابن أبي الدنيا (شرح أبي جعفر الخليفي), same tagged-citation
  script as عبد الرزاق. `src/scripts/reader.ts`'s `KIND_ORDER`/`KIND_LABEL`
  carries the tab label ("تعليق وشرح").

تفسير النسفي (مدارك التنزيل) and its person entry were removed entirely
(`src/content/book-lg/tafsir-al-nasafi.md`, `src/content/person/al-nasafi.md`)
— no other content referenced either slug.

Each `gen-tafsir-index-*.ts` script writes a reviewed PREVIEW file by
default; `--merge` is the explicit, separate step into the live index
(same convention `gen-tafsir-index-muqatil.ts` established).

## Deviations from the plan (intentional)
- Collection named **`quran`**, not `surah`. Ayah anchors are the body's paragraph ids (not a `{surah}:{ayah}` composite).
- Surah pages render the Relations panel **empty** (`<Relations items={[]} />`) — tafsir is shown inline per-آية, so duplicating it in an end-of-page panel would violate the clean-UI gate.
- Hover preview is the native `title` attribute, not a rich JS hover card.

## Content status (infra done, corpus pending)
- **Quran spine is populated** (114 surahs in `src/content/quran/`).
- `/hadith` and `/tarajim` stay empty until books with `genre:حديث`/`genre:تراجم` are imported (currently **0** genre-tagged books). The hadith/tafsir/narrator importer paths exist and are selftested, but emit content only when run against the local رار samples.
- **Real-sample epub imports must run on the owner's machine** — samples under `~/Downloads/Telegram Desktop/` are not in the repo; never commit copyrighted epubs. Local samples: موطأ `06ـ (199) متون الحديث/…موطأ مالك…epub` (8 vols → `--merge-volumes`), mushaf `01ـ (164) التفاسير/…القرآن الكريم…epub`.

## Key files
- Importer: `scripts/epub-import.ts` (red-number detection, `class="title"`→`##`, footnote/تخريج extraction, footer stripping, `GENRE_FOLDER_MAP`, hadith path, `--selftest`).
- Graph: `src/lib/graph.ts` (`buildGraph`, `backlinksFor`, `shuyukhFor`/`talamidhaFor`), `src/lib/site.ts` (`loadGraph`, `backlinksList`, `notesByAnchor`).
- Markdown: `src/lib/sanitize.ts` (`rehypeWikiLinks` + Arabic token colors), `src/lib/wikilink.ts` (shared `[[…]]` parser/regex), `src/lib/chapters.ts` (`parseBook`, `extractAnchors`).
- Schema: `src/content.config.ts`. Section pages: `src/pages/{quran,hadith,tarajim}.astro`, reader `src/pages/quran/[surah].astro`, panel `src/components/Relations.astro`.

## Full original plan
`~/.claude/plans/optimized-waddling-pearl.md` (owner's machine only — this doc is the in-repo equivalent).
