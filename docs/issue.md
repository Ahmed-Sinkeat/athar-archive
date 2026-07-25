# athar-archive — Issue & Watch Register

Every call-out, known limitation, deviation risk, and "needs-study" item from the
build, **ranked**. Living doc — updated each phase alongside `asbuild.md`.

**Severity:** 🔴 high · 🟠 medium · 🟡 low
**Status:** `open` · `studying` · `mitigated` · `resolved`
**🔬 = needs study/spike** before it can be closed (not just a task).

---

## Ranked — open

| # | Sev | Issue | Area | Status | Why it matters / recommended action | Phase |
|---|----|-------|------|--------|-------------------------------------|-------|
| 1 | 🔴 | **Arabic search recall on hamza/proclitics** — `أسماء`↛`بأسماء`, hamza-form misses (originally studied as a P4 Pagefind spike; search backend is now Cloudflare D1/FTS5, same class of limitation applies). 🔬 | search | studying | Search is core UX. FTS5 does prefix-match + partial normalization, no Arabic morphology. **Study with the real corpus**; if recall is poor, escalate to Meilisearch/Typesense (already the documented trigger). Cheap interim: index a light proclitic/hamza-normalized field. | P8/P9 |
| 2 | 🔴 | **D1 read pressure — search degrades silently at quota.** 540.75M rows read month-to-date (2026-07) against a 5M/day free allowance; one full pass over `docs` is ~188.5k rows. Index itself is healthy (`doc_hash` 132,089 rows, all distinct — no duplication). | search | mitigated | Cause is query *shape*, not a runaway: FTS5 must score every match to `ORDER BY rank`, and prefix-expanding every token ≥3 chars widens the match set. **Fixed so far:** failures now return 503 + log (were 200-with-no-hits, so a quota-dead search was indistinguishable from an empty one); response cache 5min → 30min; OR-retry gated to `offset === 0` and ≤4 tokens; title subquery bounded. **Remaining lever, behaviour-changing so not taken unilaterally:** restrict prefix matching to the *last* token only (normal autocomplete pattern) — cuts rows scanned substantially, costs mid-query typo tolerance. | 2026-07 |
| 2b | 🟠 | **أذكار toolbar actions (نسخ/مشاركة/فهرس/إعادة) missing until a reload.** Reported 2026-07-25. 🔬 | ui | open | **Obvious hypothesis disproved:** the four buttons are static HTML (`adhkar.astro`), not JS-created, and `adhkar.ts` already re-inits on `astro:page-load` *and* at module top level. So it is neither a missing soft-nav hook nor a render failure. **Needs reproduction** — specifically which page the user navigates *from*, and whether the module or the `data-ready` guard is the one short-circuiting. Do not ship a speculative fix. | — |
| 3 | 🟠 | **Accessibility — automated WCAG pass done; manual SR/keyboard pass pending.** | a11y | mitigated | `pnpm a11y` (axe-core, headless) runs WCAG 2.0/2.1 A+AA over 22 pages × 3 themes — **all clean**. Fixed: darkened `--ink-faint` + split `--accent`/`--accent-solid` so accent text and white-on-accent buttons both meet AA in every theme. Remaining: a **manual** keyboard/screen-reader walkthrough (axe can't cover focus order / NVDA) — do at P8. | P8 |
| 4 | 🟠 | **CSP `'unsafe-inline'` → removed (strict).** | security | resolved | `script-src` = `'self' 'wasm-unsafe-eval'` + sha256 hashes of the 4 inline scripts (gen-headers.mjs, recomputed each build). `style-src` = `'self' https://fonts.googleapis.com`. `inlineStylesheets: never` keeps CSS external. ⚠️ verify reading-prefs/progress (CSSOM `.style`) under *enforced* CSP after first deploy — expected fine (CSP doesn't govern CSSOM). | done |
| 5 | 🟠 | **Inline styles → classes (done).** | maint | resolved | All 182 inline `style=` (128 unique) extracted to `src/styles/extracted.css` via `scripts/extract-inline-styles.mjs` (identical declarations, zero visual change). Future churn lives in CSS. Class names are hash-based (`uXXXXXXX`) — could be renamed semantically later if desired. | done |
| 6 | 🟠 | **No render tests** → added a post-build smoke test. | testing | mitigated | `pnpm smoke` (`scripts/smoke-test.mjs`, in CI) asserts per-template invariants over `dist/`: verses + stacked annotations, lesson TOC↔heading-id alignment, attachments, audio players, JSON-LD types, canonical/noindex, sitemap/RSS/_redirects/CSP, search-index scoping. 30 assertions green. Could later add Astro Container unit tests for finer cases. | done |
| 12b | 🟠 | **PWA → Android app storage migration undesigned.** 🔬 | android | open | `localStorage` holds bookmarks, favourites, reading progress, أذكار counters and settings (55 uses in `reader.ts` alone, plus `library.ts`/`marks.ts`/`adhkar.ts`). A WebView app has a **different storage origin from the browser**, so a PWA user who installs the app loses all of it. Must be designed **before** launch, not after the first report. See `docs/android-app.md`. | — |
| 13 | 🟡 | **Branch protection not enabled.** | governance | open | Repo is now **public**, so the earlier "needs GitHub Pro" blocker (2026-07 HTTP 403) no longer applies — just hasn't been turned on. No external contributors yet, so convention suffices for now. Enable via repo → Settings → Branches when the first volunteer joins, or sooner. | — |
| 8 | 🟡 | **QAPage JSON-LD answer is a crude markdown strip** (`[#*_\`>]` removed, 4k cap). | seo | open | Good enough for now; revisit if Q&A pages get structured (per-question/answer) markup. | P9 |
| 9 | 🟡 | **Tashkeel toggle swaps `innerHTML`** of `[data-ar]` (caches full/bare). | frontend | open | Edge cases if content becomes dynamic or deeply nested; fine for static prose/verses today. | — |
| 10 | 🟡 | **Sanitizer allowed-attr policy** (`id`,`className`,`dir`,`lang` global). | security | resolved | P7 documented the allowed subset in CONTRIBUTING (§ما الذي يُسمح): `rehype-sanitize` safe set + those globals; `script`/`style` stripped. Re-review gate noted before accepting richer volunteer HTML. | P7 |
| 11 | 🟡 | **Editorial drift after thousands of materials** (watchlist #14). | governance | open | Long-term top risk. Guarded by Zod + Authoring Guide + PR review, not code. Schedule periodic watchlist review. | P9 |

---

## Resolved

| Sev | Issue | Resolution |
|----|-------|-----------|
| 🟠 | **CSP `'unsafe-inline'`** (#4) | Strict CSP — inline scripts hashed, all CSS external; no `'unsafe-inline'` for script or style. Post-deploy CSSOM check noted. |
| 🟠 | **Inline styles in templates** (#5) | 182 extracted to `extracted.css` (zero visual change). |
| 🟡 | **Sanitizer allowed-attr policy** (#10) | P7 documented the allowed `rehype-sanitize` subset + `id/className/dir/lang` globals (`script`/`style` stripped) in CONTRIBUTING; re-review gate noted for richer HTML. |
| 🔴 | **No CI / no git remote** (was #2) | Pushed to `github.com/Ahmed-Sinkeat/athar-archive`; GitHub Actions CI (install → test → validate:content → build → smoke → tsc) **green** on every push to main. |
| 🔴 | Pagefind Arabic **diacritics** viability (the #1 P0 risk) | P4 spike: Pagefind normalizes diacritics both directions → **GO**, no stripped field (D10). |
| 🟠 | Domain extension `.net` vs `.com` undecided | Ratified **`.com`** in P5 (D11). |
| 🟡 | `verse_count`/`opening_verse` hand-stored vs derived | Made derived-only in P2 (D3, FR-C-06). |
| 🟡 | **Real content corpus not seeded** (was #7) | Long past — corpus is now 6,000+ content entries (`pnpm validate:content`, 2026-07). |
| 🟠 | **R2 media bucket empty** (was #12) | `athar-book-assets` bucket is live and populated (~2.5GB, see `docs/deploy.md`'s usage table); chapter pages + tafsir fragments served from it in production. |

---

*Add new items at the right rank; move closed ones to **Resolved** with the resolving phase/decision id. Keep this in sync with `asbuild.md` decisions (D#).*
