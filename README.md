# Ahl al-Athar (أهل الأثر)

A static digital library powered by Athar Engine.

موقعٌ يُعنى بجمع التراث الإسلامي على مرِّ العصور، مع سهولة البحث والقراءة.

A static, RTL Arabic knowledge archive. **The content is the origin; the technology is a replaceable layer** — the whole site is rebuildable from Git alone.

[![CI](https://github.com/Ahmed-Sinkeat/athar-archive/actions/workflows/ci.yml/badge.svg)](https://github.com/Ahmed-Sinkeat/athar-archive/actions/workflows/ci.yml)

> ### 📖 Want to add or edit a book?
> **No programming needed.** Read **[«كيف تُضيف كتابًا أو تُعدّله» — دليل المبتدئين](docs/adding-content.ar.md)** ([English](docs/adding-content.en.md)).
> Everything below this box is for developers.

## Stack

- **Astro** (static output) · **Markdown + Zod** content collections (source of truth)
- **Cloudflare D1 (FTS5)** search via a Worker API route (`/api/search`) — not Pagefind; the index refreshes incrementally on every `main` deploy (CI), capped by `SEARCH_ROW_BUDGET` to stay inside D1's daily write quota, so new content can take several deploys to become searchable
- **Cloudflare Workers + Static Assets** (hosting, `pnpm deploy`) · **Cloudflare R2** (book/tafsir chapter bodies, audio, attachments — pushed on every CI deploy via `pnpm r2:upload`)
- Content renders **fully without JavaScript** for the reading path; JS enhances (search, audio, reading prefs).

## Quick start

```bash
pnpm install
pnpm dev            # local dev server
pnpm new <entity> <slug>   # scaffold a new content file (see CONTRIBUTING.md)
pnpm build          # validate content → astro build → chapter/tafsir assets → _redirects → _headers
pnpm preview        # serve the production build
pnpm test           # vitest (validators, graph, chapters, chunking, sanitize)
```

## The entities

Person · Subject · Topic · Book · Poem · Question · Article · Audio · Annotation · Term (المعجم) (+ Announcement and Highlight — مختار الأسبوع: آية/حديث/بيت — as homepage chrome). The old Series/Lesson split was retired — a lesson is now just a book with audio attached. **الفوائد (كُناشة)** are device-local reader bookmarks (`localStorage`), not a CMS/Git-authored collection. **القرآن** is a separate collection — 114 surahs as a mushaf spine with a `/quran/<surah>` ayah reader.

Books carry an optional **genre** (`قرآن|حديث|تراجم`) routing them to dedicated `/quran` `/hadith` `/tarajim` sections (still under `/books`); `/hadith` adds a صحيح/حسن/ضعيف/موضوع grading facet.

Polymorphic links (`source_type`/`target_type`) have no DB foreign keys — **Zod + a build-time cross-entity validator** are their only guard. A dangling reference fails the build; it never ships silently.

## Reading & browse

- **One-line top bar** — brand (= home) · slim nav · inline expanding search with an in-bar filter (type · searchable multi-select عَلَم/موضوع, OR) · settings gear (font, تشكيل, theme). The home hero search carries the same filter icon.
- **Browse by فن** — الكتب/المنظومات/المقالات/الدروس as collapsible تصنيف→موضوع accordions (native `<details>`, sorted by سنة التصنيف); المسائل as a subject→topic drill-down; `/era/<slug>` pages list an era's poets and منظومات.
- **Inline شرح chooser** — marked phrases open a popover; multiple شروح on one spot show a chooser, then reveal with the phrase highlighted (click / long-press). Build-time data, JS-free `:target` fallback. Book bottom حواشٍ collapse under a `<details>`.
- **مختارات الأسبوع** — the home shows a weekly-rotating آية/حديث/بيت (the `highlight` collection). متون/منظومات with more than one recitation get a small native dropdown to switch recordings.
- **Connections** — a collapsible «ما يشير إلى هذا» relations panel at page end (backlinks: شروح، فوائد، سلاسل، authored works, unlinked mentions), subtle inline `[[type:slug]]` wiki-links, and a person→شيوخ narrator graph (شيوخه/تلاميذه on each عَلَم). Connectivity stays out of the reading flow — clean-UI is a hard gate.
- **`/roadmap`** — طريق طلب العلم page, content from `src/data/roadmap.md` (edit to fill it out); linked from the home hero.
- **`/admin`** — a Sveltia CMS panel is still deployed here, but **it is not the working path and is effectively retired**: no content has come through it (every commit is plain Git), it only ever exposed الكتب + الأشخاص, and it can only see the 284 books under `src/content/book/` — not the 955 in `src/content/book-lg/`. **Content is authored and edited through GitHub** — see [`docs/adding-content.ar.md`](docs/adding-content.ar.md).

## Docs

| Doc | What |
|---|---|
| [`docs/adding-content.ar.md`](docs/adding-content.ar.md) / [`.en.md`](docs/adding-content.en.md) | **Start here for content.** Adding/editing a book through the GitHub web UI, no coding — frontmatter template, the `book`/`book-lg` split, undoing mistakes |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | The same job via Git + Pull Request — per-entity templates, id/slug rules, publish gates (Arabic) |
| [`docs/editing-text.ar.md`](docs/editing-text.ar.md) / [`.en.md`](docs/editing-text.en.md) | Changing site interface text (menus, labels) |
| [`docs/deploy.md`](docs/deploy.md) | Cloudflare Workers deploy + domain + `/admin` access control |
| [`docs/structure.md`](docs/structure.md) | Current repository layout |
| [`docs/technology-stack.md`](docs/technology-stack.md) | Why each piece of the stack was chosen |
| [`docs/android-app.md`](docs/android-app.md) | Native Android app + the `app/v1` JSON pipeline |
| [`docs/import-epub-guide.md`](docs/import-epub-guide.md) | Bulk-importing books from EPUB |
| [`docs/asbuild.md`](docs/asbuild.md) | Phase-by-phase as-built record vs the build plan |
| [`docs/issue.md`](docs/issue.md) | Ranked issue / watch register |

## License

To be decided before public launch.
