# Editing the site's text (beginner guide)

*النسخة العربية: [editing-text.ar.md](./editing-text.ar.md)*

This guide is for changing the **visible text** of the site: menu names, page
titles, buttons, and hints. The **content itself** (books, poems, questions) is
added and edited from the CMS at `/admin` — see `docs/adding-content.en.md`.

> Golden rule: only change the Arabic text you can read. Never touch anything
> between `< >` or `{ }`, any English words, or style names like `uf5db21c`.

---

## Easiest method (works for any text)

1. Note the exact text you see on the site, e.g. «المنظومات».
2. Open the repo on GitHub, press `/` (or the search box at the top), and type
   the text you want to change — it points you to the file and line.
3. Open the file, then click the pencil ✏️ (Edit) at the top-right.
4. Change only the Arabic text; leave everything around it as-is.
5. Scroll down, click **Commit changes**. The site rebuilds automatically in a
   few minutes.

---

## Worked example: rename «المنظومات» → «الشعر» in the menu

This label appears in three places. Open each file, find «المنظومات», and
replace it with «الشعر»:

| Where | File |
| --- | --- |
| Top menu + footer links | `src/layouts/Base.astro` |
| Home-page card | `src/pages/index.astro` |
| The poems page title itself | `src/pages/poems.astro` |

Note: the small «منظومة» badge shown on each poem is separate — it lives in
`src/lib/display.ts` (the `labelFor` function).

---

## Where each piece of text lives (reference)

| Visible text | File to edit |
| --- | --- |
| Top menu (الكتب/الشعر/الدروس/المسائل/المقالات) | `src/layouts/Base.astro` — the `nav` array |
| Side-drawer extra links (الموضوعات/الفوائد/الأعلام) | `src/layouts/Base.astro` — `navAll` |
| Footer links | `src/layouts/Base.astro` — the `<footer>` section |
| Search box placeholder | `src/layouts/Base.astro` (+ `index.astro`, `search.astro`) |
| Filter buttons (type/person/subject) + type names | `src/layouts/Base.astro` — `searchTypes` |
| Reading settings (font/tashkeel/numbers/theme) | `src/layouts/Base.astro` — `settings-pop` |
| Home title, buttons, stats, section labels, cards | `src/pages/index.astro` |
| Browse page titles and intros | `src/pages/books/[...page].astro` · `poems.astro` · `questions/index.astro` · `articles/[...page].astro` · `people.astro` · `subjects.astro` · `benefits.astro` · `matn.astro` |
| Content-type badge (منظومة / متن / مرجع / مجموع) | `src/lib/display.ts` — `labelFor` |
| Era names (الأموي/العباسي/الأندلسي…) | `src/lib/display.ts` — `ERA_VALUES`, `eraLabel` |
| Reader-page chrome (منظومة، نظمُ، متن، بيت، باب) | `src/pages/poem/[slug].astro`, `src/pages/book/[slug].astro` |
| CMS collection labels/fields | `public/admin/config.yml` |
| Sort buttons (alphabetical/by date) + «عرض الكل» | `src/components/SortableList.astro`, `BrowseGroups.astro` |

---

## Safety rules (so nothing breaks)

- Change only text between `>` and `<` — the part you can read.
- Don't change English words, style names like `u5fbcbc5`, or anything inside `{ }`.
- One change at a time, then save, then check the site.
- If something breaks: GitHub keeps full history — Revert the commit, no harm done.

Tip: every edit triggers an automatic rebuild. To preview on your own machine
first, run `pnpm dev` and open the local URL.
