# Handoff — Quran/Hadith sections + connectivity layer

Self-contained brief for continuing this feature. Assume zero prior context.

## Where
- Repo: `github.com/Ahmed-Sinkeat/athar-archive`. Branch: **`feat/quran-hadith-connectivity`** (off `main`; leave `main` alone).
- Stack: Astro (no backend), hybrid Cloudflare-Worker rendering. Content = Markdown collections under `src/content/`. Package manager: **pnpm**. Arabic / RTL throughout.

## How to work (non-negotiable)
- **Ponytail (lazy/minimal).** Laziest solution that actually works: reuse existing machinery, no new dependencies or speculative abstractions, shortest diff. This whole feature is deliberately *additive + reuse*, not a parallel system.
- **Clean UI is a hard gate.** New connectivity/UI must not clutter the reading flow — reverse-references live in an end-of-page collapsible panel, links render subtly. If something can't be surfaced cleanly, tuck it away or drop it.
- **Lean testing only.** Functional checks: `pnpm test` (vitest), `pnpm validate:content`, `pnpm exec astro build`, and the importer's `pnpm tsx scripts/epub-import.ts --selftest`. **No visual/screenshot/e2e tests** — the owner eyeballs the look himself. Always do a byte-sanity check on imports (a real bug earlier silently shrank a poem 583KB→6.7KB).
- **Hybrid auto-link posture.** Auto-create high-confidence links/edges; render uncertain matches softly; **flag ambiguous narrator names for review rather than guessing**.
- **Commit per phase**, footer `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`, push to the branch.

## Load-bearing insight (why this is mostly reuse)
- `buildGraph` (`src/lib/graph.ts`) already "replaces a relational DB": forward indexes + (new) `backlinksFor`.
- Bodies derive stable **anchors** (`extractAnchors` in `src/lib/chapters.ts`: `v{n}` poems, `p{n}`/`{#id}` books). The `annotation` collection layers commentary onto `target_id` + `anchor`, shown via the reader's شرح chooser.
- Therefore: **Quran tafsir-per-آية and Hadith تخريج/grading are just "annotations on an anchor."** A **narrator is just a `person`** (`tabaqa` field already = الصحابة/التابعون/أتباع التابعين/…). Backlinks = reverse edges over `buildGraph`.

## Done (committed on the branch)
- **Phase 1** — `book.genre` enum (`قرآن|حديث|تراجم`) orthogonal to `kind`; importer `--genre` + `GENRE_FOLDER_MAP` (maps the source رار فن folder → section) in `scripts/epub-import.ts`; `/quran` `/hadith` `/tarajim` genre-filtered section pages (`buildSubjectGroups` gained an optional filter); nav wired in `src/layouts/Base.astro`.
- **Phase 2** — `graph.backlinksFor` + `site.backlinksList` + `src/components/Relations.astro` («ما يشير إلى هذا», collapsible, end-of-page) on book/poem/person pages; `[[type:slug|label]]` wiki-links via `rehypeWikiLinks` in `src/lib/sanitize.ts` + shared `src/lib/wikilink.ts`. Tests in `wikilink.test.ts` + `graph.test.ts` (80 pass, build 0).

## Remaining

### Phase 3 — Hadith units + grading
Import hadith books (`genre:حديث`) as numbered, anchored, citable units.
- New importer path in `scripts/epub-import.ts` (e.g. `buildHadithBody`, gated on `genre==="حديث"` or a `--hadith` flag):
  - Each hadith starts at `<span class="red">N - </span>` (or `N/ M - `). **Canonical number N is also in the page footer** `<div class="center">الحديث: N ¦ الجزء ¦ الصفحة</div>`.
  - Emit one paragraph per hadith with an explicit `{#hN}` anchor (`parseBook` already supports `{#id}` anchors).
  - كتاب/باب = `<span class="title">` → `## ` headings.
  - Per-hadith `<span class="footnote">` carries `[التَّخْرِيجُ]` (cross-refs w/ numbers), `[مَعَانِي الْكَلِمَات]`, and a كتاب:باب ref. Extract `[التخريج]` → an `annotation` stub (`kind:تخريج`, `target_type:book`, `target_id:<book>`, `anchor:hN`).
- Schema: add `grade: z.enum(["صحيح","حسن","ضعيف","موضوع"]).optional()` to `annotation` in `src/content.config.ts`.
- `/hadith` page: add a grading facet (aggregate annotation grades).
- Verify: extend importer `--selftest` with a Muwatta-page fixture (footer→`hN`, `[التخريج]` split); import a real sample locally; build.
- **Local sample**: `/home/sinkeat/Downloads/Telegram Desktop/06ـ (199) متون الحديث/0179هـ موطأ مالك ت الأعظمي --- مالك بن أنس.epub` (8 vols → use `--merge-volumes`; vol 1 is a long محقّق intro, real hadith start ~page 575+).

### Phase 4 — Quran spine + per-آية tafsir
- New **`surah`** data collection (114 entries) parsed from the mushaf epub (in-house, **not** an external JSON):
  - Local sample: `/home/sinkeat/Downloads/Telegram Desktop/01ـ (164) التفاسير/0011هـ القرآن الكريم --- تنزيل من حكيم حميد.epub`.
  - **Non-Shamela layout**: `OEBPS/Text/page_N.xhtml`, plain `<p>` text, each ayah closed by ` (N)`, surah header `N - سورة X` (an `<h1>` for al-Fatiha, inline for al-Baqarah — watch a stray `Z` artifact before the first ayah), footer `الصفحة: P - الجزء: J`. Hafs / رسم إملائي, full tashkeel.
  - Parser: surah boundaries `/(\d+)\s*-\s*سورة/`, ayat split on ` (N)`, juz from footer. **Ayah anchor = `"{surah}:{ayah}"`** (e.g. `2:255`).
- `/quran`: browse-by-surah + an ayah reader.
- `annotation`: `target_type` += `"quran"`, `kind` += `"تفسير"`; tafsir importer parses «قوله تعالى …» segments → ayah-anchored annotations; reuse the شرح chooser as a per-آية tafsir chooser.

### Phase 5 — Narrator / تراجم graph
- `person`: add optional `rutba` (جرح/تعديل verdict) + `narrates_from: z.array(slug)` (curated edges; students derived in-graph).
- Build an isnād→person index — **hybrid**: high-confidence from narrator-organized takhrij/su'ālāt books + Muwatta's structured isnads; **flag ambiguous names, don't auto-resolve every chain** (e.g. "مالك" = many people).
- شيوخ/تلاميذ on `src/pages/person/[slug].astro`; person→person edges in `src/pages/graph.astro` (extend its `links` builder).
- Semi-link mentions («ذُكر في N موضعًا»): surface unlinked name/alias (`also_known_as`) occurrences **in the Relations panel only**, never inline.

## Key files
- Importer: `scripts/epub-import.ts` (red-number detection, `class="title"`→`##`, footnote extraction, footer stripping, `GENRE_FOLDER_MAP`, `--selftest` all present).
- Graph: `src/lib/graph.ts` (`buildGraph`, `backlinksFor`), `src/lib/site.ts` (`loadGraph`, `backlinksList`, `relatedTo`, `notesByAnchor`).
- Markdown: `src/lib/sanitize.ts` (`markdownToSafeHtml`, rehype plugins incl. `rehypeWikiLinks`), `src/lib/chapters.ts` (`parseBook`, `extractAnchors`, `slugifyArabic`, `uniqueSlug`).
- Schema: `src/content.config.ts`. Browse: `src/lib/browse.ts` (`buildSubjectGroups`), section pages `src/pages/{quran,hadith,tarajim}.astro`, `src/components/Relations.astro`.
- URLs/labels: `src/lib/display.ts` (`hrefFor`, `labelFor`).

## Cloud-agent caveats
- The local epub samples (under `~/Downloads/Telegram Desktop/`) are **not** in the repo — a cloud agent can write importer logic + `--selftest` fixtures, but **real-sample imports must run on the owner's machine**. Do not commit copyrighted epub content.
- The full رار collection isn't extracted yet (only `~/book/extracted/aqeeda/` exists locally). Section pages stay empty until hadith/quran/tarajim books are imported.

## One full plan exists locally
`~/.claude/plans/optimized-waddling-pearl.md` (owner's machine only — this doc is the in-repo equivalent).
