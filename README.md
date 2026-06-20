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
pnpm build          # validate content → astro build → pagefind index → _redirects
pnpm preview        # serve the production build (search works here)
pnpm test           # vitest (validators, graph, chapters, chunking, sanitize)
```

## The 12 entities

Person · Subject · Topic · Book · Poem · Series · Lesson · Questions · Benefit · Article · Audio · Annotation (+ Announcement as homepage chrome).

Polymorphic links (`source_type`/`target_type`) have no DB foreign keys — **Zod + a build-time cross-entity validator** are their only guard. A dangling reference fails the build; it never ships silently.

## Docs

| Doc | What |
|---|---|
| [`docs/structure.md`](docs/structure.md) | Current repository layout |
| [`docs/asbuild.md`](docs/asbuild.md) | Phase-by-phase as-built record vs the build plan |
| [`docs/issue.md`](docs/issue.md) | Ranked issue / watch register |
| [`docs/media-and-backup.md`](docs/media-and-backup.md) | R2 media + “rebuild from Git” recovery |

## License

To be decided before public launch.
