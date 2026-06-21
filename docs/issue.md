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
| 1 | 🔴 | **Arabic search recall on hamza/proclitics** — `أسماء`↛`بأسماء`, hamza-form misses (P4 spike). 🔬 | search | studying | Search is core UX. Pagefind does prefix-match + partial normalization, no Arabic morphology. **Study with the real corpus**; if recall is poor, escalate to Meilisearch/Typesense (already the documented trigger). Cheap interim: index a light proclitic/hamza-normalized field. | P8/P9 |
| 3 | 🟠 | **Accessibility — automated WCAG pass done; manual SR/keyboard pass pending.** | a11y | mitigated | `pnpm a11y` (axe-core, headless) runs WCAG 2.0/2.1 A+AA over 22 pages × 3 themes — **all clean**. Fixed: darkened `--ink-faint` + split `--accent`/`--accent-solid` so accent text and white-on-accent buttons both meet AA in every theme. Remaining: a **manual** keyboard/screen-reader walkthrough (axe can't cover focus order / NVDA) — do at P8. | P8 |
| 4 | 🟠 | **CSP `'unsafe-inline'` → removed (strict).** | security | resolved | `script-src` = `'self' 'wasm-unsafe-eval'` + sha256 hashes of the 4 inline scripts (gen-headers.mjs, recomputed each build). `style-src` = `'self' https://fonts.googleapis.com`. `inlineStylesheets: never` keeps CSS external. ⚠️ verify reading-prefs/progress (CSSOM `.style`) under *enforced* CSP after first deploy — expected fine (CSP doesn't govern CSSOM). | done |
| 5 | 🟠 | **Inline styles → classes (done).** | maint | resolved | All 182 inline `style=` (128 unique) extracted to `src/styles/extracted.css` via `scripts/extract-inline-styles.mjs` (identical declarations, zero visual change). Future churn lives in CSS. Class names are hash-based (`uXXXXXXX`) — could be renamed semantically later if desired. | done |
| 6 | 🟠 | **No render tests** → added a post-build smoke test. | testing | mitigated | `pnpm smoke` (`scripts/smoke-test.mjs`, in CI) asserts per-template invariants over `dist/`: verses + stacked annotations, lesson TOC↔heading-id alignment, attachments, audio players, JSON-LD types, canonical/noindex, sitemap/RSS/_redirects/CSP, search-index scoping. 30 assertions green. Could later add Astro Container unit tests for finer cases. | done |
| 7 | 🟡 | **Real content corpus not seeded** — only ~21 fixtures. | content | open | Launch blocker (not a code issue). Seed real متون/منظومات/دروس; will also surface #1 and perf reality. | P8 |
| 12 | 🟠 | **R2 not provisioned; media URLs are placeholders** (`r2.ahlalathar.com/...` don't resolve yet). | infra | open | P6 built the player/links/validation + docs/scripts, but the bucket, real Opus uploads, and a **recovery rehearsal** are pending. Audio/downloads 404 until then. See `docs/media-and-backup.md`. | P8 |
| 13 | 🟠 | **Branch protection not yet applied** — CODEOWNERS + settings documented, not enforced. | governance | open | P7 shipped `docs/governance.md` + `.github/CODEOWNERS`, but `main` branch protection (require Code Owner review + CI status check) must be enabled in the GitHub UI by an admin. Until then team-only-merge is convention, not enforcement. | P8 |
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

---

*Add new items at the right rank; move closed ones to **Resolved** with the resolving phase/decision id. Keep this in sync with `asbuild.md` decisions (D#).*
