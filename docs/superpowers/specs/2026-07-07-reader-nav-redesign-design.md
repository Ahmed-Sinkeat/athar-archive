# Reader navigation redesign — unified sidebar, deduped search, topbar icons

**Status:** Approved 2026-07-07 · brainstormed with the user after a round of mobile bug reports.

## Context

Three related complaints, investigated down to root causes before this spec:

1. **Two "search this page" UIs on book/chapter reading pages.** `StudyBar.astro` renders a visible "بحث في الصفحة" text input wired to the deprecated, browser-inconsistent `window.find()` API (`src/scripts/reader.ts` `search:next`/`search:prev` actions). Separately, `src/scripts/marks.ts` implements a proper custom finder (`aa-find` bar + `aa-findbtn` floating button) using the CSS Custom Highlight API, with match count and next/prev. Same job, done twice — the floating one is the better implementation.

2. **The floating find button visually blocks the floating chapter-jump button on mobile.** `.aa-findbtn` (`inset-inline-end: 18px`, bottom ~22px, z-index 74) and `.fab-toc` (`inset-inline-end: 18px`, bottom 20px, z-index 45) are pinned to the identical fixed screen position. On mobile, where `.fab-toc` is shown, the higher-z-index find button sits on top of it.

3. **Inconsistent TOC placement across content types.** Chunked book chapters (`src/pages/book/[slug]/[chapter].astro`) already have a real sidebar: a two-column grid (`.chap-grid`), a sticky `.toc-side.chap-toc-aside` on desktop, and a `<details class="chap-mobile-toc">` popup on mobile. Poems, articles, and non-chunked (small) books instead render their heading outline as a `<nav class="toc-box">` block stacked *above* the reading content (`src/pages/poem/[slug].astro`, `src/pages/article/[slug].astro`, `src/pages/book/[slug].astro` for `!chunked`). The user wants one consistent pattern everywhere: sidebar on desktop, popup menu on mobile, never a block above the content — regardless of book size.

Masail (`src/pages/questions/[slug].astro`) is explicitly out of scope: it has no in-page heading structure to navigate (a single Q&A block), so there is nothing for a sidebar to show there.

## Goals

- One shared component renders the sidebar/popup-TOC pattern for: chunked book chapters, non-chunked books, poems, and articles.
- Sidebar state (open/closed) is a per-device preference that persists across navigation, mirroring how theme/width already persist.
- On mobile, the topbar (already auto-hides on scroll-down, reappears on scroll-up — `updateTopbarVisibility()` in `reader.ts`) gains two icons: "find on page" and "chapter/heading list" — both next to the existing gear icon. The floating `aa-findbtn` and `.fab-toc` buttons are removed.
- `StudyBar`'s in-page search bar is removed; the modern floating finder becomes the only in-page search, just triggered from the topbar instead of a floating button.

## Non-goals

- No change to the Quran reader (separate, larger piece of feedback — its own future spec).
- No change to site-wide search (`/search`, the drawer's "البحث" link) — only in-page find and content-outline navigation are in scope here.
- Masail pages are not touched.

## Design

### 1. `ReaderSidebar.astro` (new shared component)

Replaces the ad hoc TOC markup duplicated across four page types. Props:

```ts
interface Props {
  // Either a flat/grouped chapter list (books) or a heading outline (poems/articles/small books) —
  // both already produced today by existing helpers (groupToc() in [chapter].astro / [slug].astro,
  // parseToc() in chapters.ts) — this component only needs a single normalized shape:
  items: { href: string; title: string; current?: boolean; slices?: { href: string; title: string; current?: boolean }[] }[];
  label: string; // "فصول الكتاب" / "فهرس العناوين" / etc.
}
```

Renders the existing `.toc-box`/`.toc-group` markup (unchanged CSS classes — they're already generic, not chunked-book-specific) wrapped in:
- a sticky `<aside class="toc-side">` for desktop (same as today's `.chap-toc-aside`, generalized — drop the `chap-` prefix since it's no longer chapter-specific)
- a `<details class="mobile-toc">` for the mobile popup (renamed from `.chap-mobile-toc`, same behavior)

Each call site keeps building its own `items`/`label` (they already compute equivalent data today via `groupToc()`/`parseToc()`); only the rendering markup is shared.

### 2. Desktop sidebar toggle + persistence

- New topbar icon button (desktop-only, next to the gear icon) toggles a `data-sidebar="hidden"` attribute on `<html>`, mirroring exactly how `data-width` works today.
- New localStorage key `aa-sidebar` (added to the existing `LS` object in `reader.ts`), default unset = open.
- Restored in the same three places `data-width` now is, for consistency: `Base.astro`'s pre-paint inline script (first load), `applyStoredPrefs()` (after `astro:after-swap`), and the initial button-state sync block.
- CSS: `.toc-side { display: none }` when `html[data-sidebar="hidden"] .toc-side`; content column expands to fill the freed space (same `.chap-grid` single-column fallback already used for mobile, reused here for the "hidden" desktop state).

### 3. Mobile: topbar icons replace floating buttons

- Two new icon buttons added to `.topbar-tools` in `Base.astro`, next to the existing settings gear: a "find on page" icon and a "chapter/heading list" icon. Both `display: none` on desktop (existing sidebar there makes them redundant) — mirrors how `.fab-toc` is already desktop-hidden today.
- "Find on page" click: opens the existing `aa-find` bar (`marks.ts`) — the bar's own open/close logic is unchanged, only its trigger moves. `aa-findbtn` and its CSS are deleted.
- "Chapter/heading list" click: opens the `<details class="mobile-toc">` from `ReaderSidebar.astro` for the current page. On mobile, `ReaderSidebar`'s own inline `<summary>` trigger ("فصول الكتاب · N فصل") is visually hidden — the topbar icon becomes the *only* way to open it, avoiding the exact "two triggers for one thing" problem this spec fixes for search. The `<details>` element itself stays in the DOM (still driving open/closed state); only its inline summary is hidden via CSS on mobile. `.fab-toc` and its CSS are deleted.

### 4. Remove `StudyBar`'s in-page search

- Delete the `.inpage-search-box` markup from `StudyBar.astro` and the `search:next`/`search:prev` actions + `[data-inpage-search]` wiring from `reader.ts`.
- `StudyBar`'s other job (download button) is untouched.

## Migration scope (files touched)

- `src/components/ReaderSidebar.astro` — new
- `src/components/StudyBar.astro` — remove in-page search box
- `src/layouts/Base.astro` — add two topbar icons; pre-paint script gains `data-sidebar` restore
- `src/pages/book/[slug]/[chapter].astro` — adopt `ReaderSidebar`, drop local sidebar/mobile-toc markup
- `src/pages/book/[slug].astro` — adopt `ReaderSidebar` for the non-chunked path (currently a `toc-box` above content)
- `src/pages/poem/[slug].astro` — adopt `ReaderSidebar`
- `src/pages/article/[slug].astro` — adopt `ReaderSidebar`
- `src/scripts/reader.ts` — `aa-sidebar` LS key + restore logic; topbar find/chapter-list button wiring; remove `search:next`/`search:prev`/`inpageSearch`
- `src/scripts/marks.ts` — `aa-findbtn` removed (trigger now external), `aa-find` bar open function exported/callable from the topbar action
- `src/styles/global.css` — generalize `.chap-toc-aside`→`.toc-side`-only, `.chap-mobile-toc`→`.mobile-toc`, add `data-sidebar` hidden state, remove `.aa-findbtn`/`.fab-toc` rules, add topbar icon styles

## Testing

- Existing `vitest` suite covers `parseToc`/`groupToc`-equivalent logic already — no new pure-function logic introduced here beyond prop-shaping, which is straightforward enough not to need its own unit test (per the project's lean-testing convention: this is layout/DOM wiring, not business logic).
- Manual verification in `astro dev`/browser (this repo has no automated browser testing): sidebar toggle + persistence across a navigation, mobile popup opens from the topbar icon, find-on-page opens from the topbar icon and matches the old floating behavior, no leftover references to removed classes (`.fab-toc`, `.aa-findbtn`, `.inpage-search-box`).
- `pnpm check:links`, `pnpm smoke`, `pnpm build`, `astro check` as usual.

## Open questions resolved during brainstorming

- Sidebar default: **open**, remembered per-device (not per-page-load-reset). ✓
- Sidebar content for non-chapter pages: **the existing heading outline**, just relocated — no new content added. ✓
- Masail: **out of scope** — no heading structure to navigate.
