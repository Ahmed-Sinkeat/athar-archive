# Design audit — "one body" — 14 October 2026

Every reachable screen, menu, sheet and dialog was captured on the Redmi in dark and light themes; the
hidden screens were read in code. The Search tab is out of scope by owner decision.

## A. The app speaks several dialects

| # | Dialect clash | Where |
|---|---|---|
| A1 | **Three vocabularies for one idea.** Saving an item: «قائمتي» tab, bookmark icon, «مكتبتي» page, «مجموعة جديدة», «إضافة إلى قائمة». Marking a place: «إضافة إشارة» button, «المواضع» panel tab. Highlights: «تظليل» → toast «حُفظ في الكناشة» → panel tab «فوائدك». | every tab, reader, player |
| A2 | **Four control styles for choices.** Underline tabs (Books/Poetry/Audio); pill segment (Kannashah «الكل/مجمّع»); filter chips with checks (search sheet); segmented boxes (Aa popover); radio list with far-left checks (Appearance) — the last two set the *same* settings. | tabs, Kannashah, search, reader, settings |
| A3 | **Every ⋮ means something else.** Poem reader: menu (fixed). Player: opens the source text — a poem opens as prose. Reader: an «المصدر» dialog. Kannashah: a comment sheet. Downloads: a one-item menu. | 5 screens |
| A4 | **Three surface styles for floating things.** New `AtharMenu` card; Material `AlertDialog` (28dp radius, new collection, footnotes, source); Material `ModalBottomSheet` (excerpt note, search filters); a raw `Popup` for Aa. | |
| A5 | **No shape or type scale.** 15 distinct corner radii (1–99dp) and 16 font sizes (9–34sp) chosen per call site. | code |
| A6 | **Digits and units mixed.** «3.1 KB», «2.8 KB», «B 879», «6.5 ك.ب من ٢ غيغابايت», font size «21», two byte formatters (`storageSize`, `byteSize`). | rows, Downloads, Aa, Appearance |
| A7 | **Two progress bars.** Player uses Material's expressive slider (vertical-line thumb at one end, stop-dot at the other) — reads as broken; the mini player and reader use thin bars. | player |

## B. Structure

| # | Problem |
|---|---|
| B1 | Four layers of chrome above one item on every tab: header icons, search, tab row, five filter menus. Search already has the cleaner «تصفية» + sort pattern. |
| B2 | **Half the library is hidden.** Articles (1,984) and fatwas (953) are reachable only through the unmarked feather logo → «الأقسام». The bottom bar keeps «الكتب» lit while there. |
| B3 | Saved things live in three places (see A1). |
| B4 | Rows disagree: Books = download, save; Articles = save, download; Fatwas = download, save; Poetry shows ✓ for downloaded; Audio has four actions. |
| B5 | مكتبتي: «واصل القراءة» is both a filter and a heading; stray «١»; «الحالة» filter floats at the bottom. |
| B6 | Player: three time readouts, a filter inside the queue, generic artwork, ⋮ as above. Poem audio row «فتح النص» also opens poems as prose. |
| B7 | Reader panel: «الجزء» for single-volume books; «انتقل» looks disabled. Aa: A−/A+ reversed in RTL, size in Latin digits. |
| B8 | Selection page drops list bullets and page markers, so the text reflows the moment selection starts; the selection colour is nearly invisible on the dark page; «تظليل» uses the bookmark icon; the toolbar sits at the top of the screen, far from the words. |

## C. Hidden screens

| Screen | Lines | Data source | Verdict |
|---|---|---|---|
| `feature/home` | 711 | none — only `@Preview` fixtures; overlaps مكتبتي's «واصل القراءة» | delete |
| `feature/adhkar` | 897 | app: preview fixtures only. **Correction:** the site has a real 45-item morning/evening list in `src/lib/adhkar-data.ts` | kept, not in the menu until wired to that list |
| `feature/sections` + Articles + Fatwas | live | real | promote out of the logo (B2) |

## D. Already fixed today

Poem ⋮ → menu; Sada-style `AtharMenu` replaces all Material dropdowns; selection fling; reader progress bar.

## E. Done in the "one body" pass (same day)

| Item | Change |
|---|---|
| B2/B3/A1 | Root screens: ⋮ opens one app menu — محفوظاتي, المقالات, المسائل, التنزيلات, الإعدادات. The logo is branding only. مكتبتي → **محفوظاتي** (shelves as a tab row); every «قائمتي» tab removed. Vocabulary: محفوظاتي · علامة · الكناشة. |
| C | `feature/home` and the الأقسام page deleted (`AtharSection` enum kept). |
| B1 | Books' four filter menus → one «تصفية» menu (group → options, «إزالة التصفية») + «الترتيب». Other tabs already had filter + sort only. |
| A2 | Kannashah's pill toggle → standard tab row; filter moved onto the sort row. Appearance uses the Aa segment controls. |
| A3 | Every ⋮ is a menu: reader (عن المصدر, فتح في الموقع), Kannashah (تعليق, فتح المصدر), player (فتح النص/القصيدة), audio rows. |
| B4/B6 | Audio row = play + ⋮ (download, save, open text/poem). Poems opened from audio go to the poem reader. Row sizes hidden on browse tabs. Player: plain seek bar, one elapsed/remaining pair, no queue filter. |
| A4 | Dialogs and sheets use 14dp corners (the menu card's). |
| A6 | One byte formatter, Arabic digits and units («٣٫١ ك.ب»). |
| B7 | «الجزء» only for multi-volume books; the page jump is hidden when a book has no page markers; «انتقل» in accent; Aa and Appearance: A− left / A+ right, Arabic digits. |
| B8 | Selection page draws list bullets and page dividers in reserved space (text offsets unchanged); stronger selection colour; the toolbar sits just above the selected lines (below when there is no room). |

Not done: A5 shape/type scale (a refactor across ~every screen; worth its own pass).

See also [`2026-10-14-backlog.md`](2026-10-14-backlog.md) for the owner's follow-up requests.
