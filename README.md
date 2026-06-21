# أهل الأثر · athar-archive

أرشيفٌ علميٌّ للمتون والمنظومات والدروس، محقَّقةً ومُشكَّلةً — تجربةُ قراءةٍ هادئةٍ تجعلُ المعرفةَ هي الأصل.

A static, RTL Arabic knowledge archive. **The content is the origin; the technology is a replaceable layer** — the whole site is rebuildable from Git alone.

[![CI](https://github.com/Ahmed-Sinkeat/athar-archive/actions/workflows/ci.yml/badge.svg)](../../actions)

## Stack

- **Astro** (static output) · **Markdown + Zod** content collections (source of truth)
- **Pagefind** — static Arabic search, no server
- **Cloudflare Pages** (hosting) · **Cloudflare R2** (audio in Opus, attachments)
- Content renders **fully without JavaScript**; JS only enhances (search, audio, reading prefs).

## Quick start

```bash
pnpm install
pnpm dev            # local dev server
pnpm new <entity> <slug>   # scaffold a new content file (see CONTRIBUTING.md)
pnpm build          # validate content → astro build → pagefind index → _redirects
pnpm preview        # serve the production build (search works here)
pnpm test           # vitest (validators, graph, chapters, chunking, sanitize)
```

## The 12 entities

Person · Subject · Topic · Book · Poem · Series · Lesson · Questions · Benefit · Article · Audio · Annotation (+ Announcement as homepage chrome).

Polymorphic links (`source_type`/`target_type`) have no DB foreign keys — **Zod + a build-time cross-entity validator** are their only guard. A dangling reference fails the build; it never ships silently.

## Reading & browse

- **One-line top bar** — brand (= home) · slim nav · inline expanding search with an in-bar filter (type / عَلَم / موضوع) · settings gear (font, تشكيل, theme).
- **Browse by فن** — الكتب/المنظومات grouped subject→topic; المسائل as a subject→topic drill-down; `/era/<slug>` pages list an era's poets and منظومات.
- **Inline شرح chooser** — marked phrases open a popover; multiple شروح on one spot show a chooser, then reveal with the phrase highlighted (click / long-press). Build-time data, JS-free `:target` fallback.
- **`/compose`** — maintainer tool (linked as "إضافة محتوى"): pick a type, fill the menus, copy/download a valid `file.md` to commit.

## Docs

| Doc | What |
|---|---|
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | How to add content — per-entity templates, id/slug rules, publish gates (Arabic) |
| [`docs/governance.md`](docs/governance.md) | Roles + branch-protection settings (team-only publish) |
| [`docs/structure.md`](docs/structure.md) | Current repository layout |
| [`docs/asbuild.md`](docs/asbuild.md) | Phase-by-phase as-built record vs the build plan |
| [`docs/issue.md`](docs/issue.md) | Ranked issue / watch register |
| [`docs/media-and-backup.md`](docs/media-and-backup.md) | R2 media + “rebuild from Git” recovery |

## License

To be decided before public launch.
