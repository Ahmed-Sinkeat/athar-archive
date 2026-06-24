# Lighthouse Report Analysis — athar-archive
**Date:** 2026-06-24 | **Page tested:** Homepage (mobile simulation)

---

## Scores

| Category | Score | Status |
|---|---|---|
| Performance | 51/100 | 🔴 Poor |
| Accessibility | 98/100 | 🟢 Excellent |
| Best Practices | 96/100 | 🟢 Good |
| SEO | 100/100 | 🟢 Perfect |

---

## Performance — What's Hurting

### Core metrics

| Metric | Value | Target |
|---|---|---|
| FCP (First Contentful Paint) | 3.1s | < 1.8s |
| LCP (Largest Contentful Paint) | 3.6s | < 2.5s |
| TBT (Total Blocking Time) | 1,310ms | < 200ms |
| Speed Index | 10.7s | < 3.4s |
| CLS (Layout Shift) | 0.014 | ✅ < 0.1 |
| TTI (Time to Interactive) | 4.9s | < 3.8s |

### Root causes (ranked by impact)

**1. Google Fonts CSS is render-blocking (+1,257ms to FCP/LCP)**
The `fonts.googleapis.com` CSS link in `<head>` blocks the browser from painting anything until it downloads. This alone pushes FCP to 3s+.

**2. Fonts load too late and are too large (219KB total, 7 files)**
- Amiri (2 variants): 108KB + 19KB = **127KB** just for one font
- IBM Plex Sans Arabic: 33KB + 14KB
- Reem Kufi: 20KB + 12KB
- IBM Plex Mono: 10KB

Font files don't even start downloading until 4.3s into page load, and Amiri finishes at 8.5s. This explains the terrible Speed Index (page looks blank until fonts arrive).

**3. Main thread blocked for 6.8s — Style & Layout taking 4.7s**
Two long tasks: 1,179ms and 932ms. Both attributed to the main HTML document. This is likely RTL text shaping + Tailwind/CSS layout calculations for Arabic content. TBT of 1,310ms means the page feels unresponsive for over a second after it appears.

**4. Forced reflow by ClientRouter (326ms)**
Astro's ClientRouter (`ClientRouter.astro_astro_type_script_index_0_lang.js`) reads `offsetWidth` after DOM mutations, causing browser to recalculate layout. Base.astro script adds another 62ms forced reflow.

**5. LCP element render delay: 6,714ms**
The `<h1 class="hero-title">أهل الأثر</h1>` is the LCP element. It renders immediately in HTML, but the browser delays painting it until the Amiri font loads. Most of the 3.6s LCP is waiting for fonts, not network.

---

## What CAN Be Fixed

### 🟢 Easy / High impact

**1. ✅ Add a favicon (fixes console error)**
`public/favicon.svg` added (dark green + gold "أ"), declared in `<head>`. No more 404 on every page load.

**2. ✅ Fix heading order (accessibility)**
`div.ann-card > h3` changed to `h2` in `src/pages/index.astro` and `src/styles/global.css`. Heading order is now h1→h2 throughout the homepage.

**3. ✅ Load Google Fonts asynchronously (saves ~1,257ms FCP)**
Changed from blocking `rel="stylesheet"` to `rel="preload" as="style"` with the `onload` swap in `src/layouts/Base.astro`. Added `<noscript>` fallback. Page now paints in fallback font immediately; fonts load in background.

**4. Preload the critical fonts**
Tell the browser to start downloading fonts immediately instead of waiting for CSS to parse:
```html
<link rel="preload" as="font" type="font/woff2" crossorigin
      href="https://fonts.gstatic.com/s/amiri/v30/J7aRnpd8CGxBHpUrtLMS7JNKIjk.woff2">
<link rel="preload" as="font" type="font/woff2" crossorigin
      href="https://fonts.gstatic.com/s/ibmplexsansarabic/v15/Qw3CZRtWPQCuHme67tEYUIx3Kh0PHR9N6Ys43PW5fslBEg0.woff2">
```
Font URLs can change with Google Fonts updates — self-hosting is more reliable for this.

### 🟡 Medium effort / High impact

**5. Self-host fonts (biggest single win)**
Self-hosting removes the Google Fonts DNS lookup, connection, and CSS download from the critical path. Use [google-webfonts-helper](https://gwfh.mranftl.com/fonts) to download woff2 files and generate CSS. Place in `public/fonts/`. Saves ~1s+ on first load, eliminates CLS from font swap.

**6. Subset Amiri font**
Amiri at 108KB is large because it includes all Unicode. For this site, only Arabic + basic Latin is needed. Tool: `pyftsubset` (fonttools) or [Fontsquirrel subsetter](https://www.fontsquirrel.com/tools/webfont-generator). Can cut it to ~40-60KB. This is the single heaviest resource on the page.

---

## What Is NOT Easily Fixable

**1. Style & Layout taking 4.7s of main thread**
Arabic RTL text shaping is computationally expensive. The browser must calculate bidirectional text, kashida, ligatures, and complex script rendering for every character. This is a browser/OS limitation — not something we can optimize in code.

**2. ClientRouter forced reflow (326ms)**
This is Astro's internal view transition code reading layout properties (`scrollY`, `offsetWidth`) after navigation. Fixing it would require changes to Astro core. Not something we control.

**3. Unused CSS 81%**
The global CSS bundle has 81% unused rules on the homepage. This is expected for a multi-page site — the homepage doesn't use book reader styles, compose styles, etc. Code-splitting CSS per-page would require significant architectural changes to Astro output. The savings (11KB) don't justify the effort.

**4. Speed Index 10.7s**
Driven almost entirely by the font loading waterfall. Without self-hosting + subsetting, fonts will always arrive late and the "visually complete" time stays high. Fixing items #5 and #6 above would drop this significantly.

**5. Two 1,000ms+ long tasks**
These are attributed to the HTML document itself (not JS). They appear to be the browser parsing + laying out the page content — Arabic text with complex CSS. Not controllable without simplifying the page structure.

---

## Security / Headers (Informational)

These are flagged but don't affect scores heavily:

| Issue | Impact | Fix |
|---|---|---|
| No HSTS header | Low (Cloudflare handles HTTPS) | Add `Strict-Transport-Security` header in Workers |
| No COOP header | Low | Add `Cross-Origin-Opener-Policy: same-origin` |
| CSP missing `unsafe-inline` fallback | Medium | Add to existing CSP if one is set |

Cloudflare Workers lets you add headers in the worker script or via a `_headers` file in `/public/`. These are not blocking issues for a content site.

---

## Priority Order

1. ✅ **Add favicon** — done (`public/favicon.svg`)
2. ✅ **Fix h1→h3 heading skip** — done (ann-card h3→h2)
3. ✅ **Async load Google Fonts CSS** — done (`Base.astro` preload+onload swap)
4. **Preload critical woff2 files** — 30 minutes, shaves time-to-text
5. **Self-host fonts** — 2-3 hours, biggest overall win (~2-3s off Speed Index)
6. **Subset Amiri** — half day, cuts 60-70KB from heaviest resource

Items 1-3 done. Items 1-4 together should push performance from ~51 to ~65-70. Items 5-6 could push it to ~75-80. Beyond that, the Arabic layout cost is the ceiling.

---

## AI-SEO Improvements (separate report, score 76/100)

Three fixes applied to improve how AI search engines understand and answer questions about the site:

- **`public/llms.txt` created** — AI crawlers read this first. Answers: what the site is, it's free, no newsletter, no fixed publishing schedule, contact email for suggestions.
- **`src/pages/about.astro` expanded** — Added four Arabic Q&A sections (who runs it, publication cadence, free/paid, how to contribute). Improves completeness of AI-indexable content.
- **Schema.org already well-covered** — books, poems, articles, people, series all have JSON-LD. No action needed.

---

## Design Bugs Fixed (frontend review)

Four CSS/routing bugs identified and fixed:

- **Duplicate `.page-sep` block removed** — Lines 716–720 in `global.css` were dead code with a `var(--bg)` reference to a non-existent token. The block was superseded by a newer definition at line 869.
- **Footer `/topics` → `/subjects`** — Footer link for الموضوعات was pointing to a different URL than the top nav. Both pages exist but users saw URL change inconsistently.
- **`.section-label` font-family fixed** — Was `var(--font-mono)` (IBM Plex Mono, no Arabic glyphs). All section labels are Arabic text; changed to `var(--font-ui)`.
- **`--gold` token removed** — Defined in all three themes, used nowhere. Removed from `:root`, `[data-theme="sepia"]`, and `[data-theme="dark"]`.
