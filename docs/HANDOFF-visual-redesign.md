# Athar Archive visual redesign — Phase 1: foundation + home

**Status (2026-07-07): implemented.** The token migration in §1 and the home
reskin in §6 are both live on `main` — verified by direct comparison
against the mockup source this session (`src/styles/global.css`'s color/
type tokens are byte-identical to the mockup's). The rollout continued
past this Phase 1 scope too: `global.css` has "Phase 2/3/4" comments
throughout marking later reskin passes (quote-band verdigris, shadow-lift
cards, narrower ~680px reading measure, etc.) — done incrementally,
directly on `main`, not through the branch below.

**Note on the branch:** `redesign/visual-v2` (last commit 2026-07-03) is a
**separate, abandoned implementation attempt of the same mockup** — it
diverged from `main` before this phased rollout and was never merged
(204 commits only on that branch, 268+ only on `main` as of 2026-07-07).
Don't merge it — the work it contains was superseded by the direct-to-main
approach this doc and its sibling phases used. If reviving anything from
it, diff specific values, don't merge wholesale (main has since touched
the same CSS territory independently for unrelated feature work).

**2026-07-07 follow-up work** (not a rewrite of this doc, just closing gaps
found by comparing the live site to the mockup source directly):
CSS discipline pass (removed ~18 box-shadow rules the spec explicitly
forbids, unified 14 ad-hoc border-radius values down to a 2-token system),
two real layout bugs (a reading-column-width regression from a sidebar
grid nested in the wrong container; the hero calligraphy image swapping on
OS dark-mode instead of the site's own theme), and كُناشتي (`/benefits`,
never covered by the original mockup screens) reskinned to match a newer
mockup iteration the user added directly in the Claude Design project.

## Context

Ahmed produced a full visual redesign spec for the site: an interactive mockup
(`~/Projects/newdesign/Website UI redesign request/Athar Redesign.dc.html`)
covering 12 screens, plus `HANDOFF.md` in that folder specifying the design
system (tokens, type, layout, per-screen notes) for a plain HTML/CSS/Astro
implementation — no React port, the mockup's JS is preview plumbing only.
That doc is the source of visual truth; this doc does not repeat its token
values, it scopes the *rollout*.

The live site (`src/pages/*.astro`, ~30 page types) covers more ground than
the mockup: the mockup mocks الرئيسية / الكتب / قارئ الكتاب / الشعر / قارئ
المنظومة / القرآن / قارئ السورة / الحديث / قارئ الحديث / التراجم / البحث /
شبكة المعرفة. The live site additionally has subjects, topics, articles,
questions, benefits, term, era, about, contact, graph, roadmap, compose,
mujam, tafsir, matn — none of which the mockup touches. Doing all of this in
one pass isn't scoped work, so it's split into phases. This doc covers only
**Phase 1: foundation + home**. Later phases (book reader, quran hub +
surah reader, hadith hub + reader, poetry listing + poem reader, تراجم /
بحث / شبكة المعرفة, then backfilling the untouched page types with tokens
only) get their own HANDOFF docs when they start.

Branch: `redesign/visual-v2`.

## Key finding that shapes this phase

`src/styles/global.css` already runs entirely on CSS custom properties
(`--paper`, `--ink`, `--accent`, `--font-ui`, …), consumed by every page and
component in the site (confirmed via grep — no component hardcodes colors or
font-families). This means the foundation change is a **token remap**, not a
rewrite: redefining what the variables *mean* cascades the new look to every
existing page — including the ~15 page types the mockup never mocked —
without touching those pages' markup.

## 1. Token migration

Add the new spec's tokens as the source of truth, using HANDOFF.md's exact
values:

- Color: `--bg`, `--card`, `--inset`, `--ink`, `--ink2`, `--ink3`, `--line`,
  `--line2`, `--brand`, `--brand2`, `--onbrand`, `--gold`, `--green`,
  `--copper`
- Type: `--f-disp`, `--f-matn`, `--f-quran`, `--f-ui`

Then alias every existing token name to the new system instead of renaming
call sites across ~30 files:

```css
--paper: var(--bg); --paper-2: var(--inset); --surface: var(--card);
--ink-soft: var(--ink2); --ink-faint: var(--ink3);
--rule: var(--line); --rule-strong: var(--line2);
--accent: var(--brand); --accent-solid: var(--brand); --verdigris: var(--green);
--gold-text: var(--gold);
--font-ui: var(--f-ui); --font-serif: var(--f-disp); --font-display: var(--f-disp);
--font-quran: var(--f-quran); --font-mono: var(--f-ui); /* mono kicker aesthetic retired */
```

New screens (starting with Home, this phase) author against the new names
directly (`var(--brand)`, `var(--f-disp)`); everything else keeps working
unchanged through the aliases and just re-themes.

`--gold-soft`, `--accent-border`, `--accent-wash`, `--verdigris-wash`, etc.
(the `color-mix()` derived tones) stay as-is, recomputed from the new base
values — no spec equivalent needed, they're implementation detail.

## 2. Themes: 3 → 2

Delete the `sepia` theme block. `:root` (no `data-theme` attribute) becomes
the merged **paper** theme using HANDOFF.md's paper hex values (replaces the
old bright-white default). `[data-theme="dark"]` gets re-colored to
HANDOFF.md's dark hex values. No third state.

`src/scripts/reader.ts`: `THEMES` shrinks to `["paper", "dark"]`;
`setTheme()` simplifies — anything other than `"dark"` clears the attribute
(falls back to default paper), so no explicit migration is needed for users
with a stale `"sepia"` or `"light"` value in `localStorage`.

## 3. Fonts

Google Fonts import in `Base.astro` swaps to: Amiri (400/700 + italic),
Amiri Quran, Noto Naskh Arabic (400–700), IBM Plex Sans Arabic (400–700).
Drops Reem Kufi, Cairo, Scheherazade New, IBM Plex Mono.

## 4. Header chrome (`Base.astro`)

- New standalone theme toggle: a single 34px icon button in the header row
  (sun/moon), replacing the 3-way `فاتح/ورقي/داكن` switch. Toggles
  `paper ⇄ dark` directly — no popover needed for this control.
- The existing gear-icon settings popover **stays**, unchanged functionally
  (font stepper, تشكيل / ترقيم الأبيات / عرض الصفحات / الحواشي toggles) —
  these are reader-specific controls whose real home (a sticky per-reader
  toolbar, per HANDOFF.md §5) is Phase 2+ work. Just restyle it with the new
  tokens/fonts; don't relocate its contents yet.
- Nav link set, search-filter popover, and drawer stay functionally as-is,
  restyled only.

## 5. Footer

Restyle with new tokens per HANDOFF.md §3 (brand blurb + link columns +
bottom strip with ۞). Keep the existing link list — the mockup's footer spec
is generic chrome, not content-specific.

## 6. Home page (`src/pages/index.astro`)

Current structure already matches the mockup's screen closely — this is a
reskin, not new data plumbing:

| Mockup section | Current section | Change |
|---|---|---|
| Manuscript-plate hero + search + 3 pills | `.hero` + search form + 3 `.btn` links | Restyle: double-border plate, pill-style quick links |
| تصفّح الأرشيف — 4 cards (كتب/قرآن/حديث/شعر), tradition-color top borders, + secondary pill row (تراجم/موضوعات/مسائل/مقالات) | 3 cards (books/poems/people), no accent coloring | **Real change**: swap to the 4 cards the mockup specifies (books/quran/hadith/poems — all 4 target pages already exist), add top-border accent per tradition, add the secondary pill row |
| مختارات الأسبوع — ayah (gold)/hadith (green)/verse (copper), each in its own dress | Already picks one آية/حديث/بيت per week (`pickWeekly`) | Restyle only — data logic already correct |
| أحدث الإضافات — list rows, type badge chip · title · author · meta | `.latest-row` list, already same shape | Restyle only |
| stats footer line | `.stats-strip` | Restyle only |
| — (not in mockup) | إعلانات, منظومة مختارة sections | **Keep**, restyle generically (card surface + brand accent) — real content the mockup didn't happen to mock, not something to delete |

## 7. Explicitly out of scope this phase

- Any reader page (book/poem/surah/hadith) — inherits new colors/fonts via
  the alias layer automatically, but keeps its current layout/markup until
  its own phase.
- Any listing page other than home (الكتب, الشعر, الحديث, القرآن, التراجم,
  البحث, شبكة المعرفة) — same: auto-reskinned via aliases, not restructured.
- The ~15 page types absent from the mockup (subjects, topics, articles,
  questions, benefits, term, era, about, contact, graph, roadmap, compose,
  mujam, tafsir, matn) — auto-reskinned via aliases only.
- Relocating reader-prefs toggles out of the header popover into a
  per-reader sticky toolbar — Phase 2+.

## Verification

- `pnpm validate` (astro check) and `pnpm build` must stay green.
- `pnpm test` (vitest) — no logic under test here changes, should stay green.
- Manual: load home + one page from each currently-unstyled family (e.g.
  `/book/zad-al-mustaqni`, `/quran`, `/subjects`) in both themes, confirm no
  broken contrast/missing var fallback (would show as `unset`/transparent).
- `pnpm a11y` if touching contrast-sensitive tokens (new gold/green/copper
  need AA-on-paper and AA-on-dark contrast, same bar the old tokens already
  cleared — verify with the existing script rather than eyeballing).
