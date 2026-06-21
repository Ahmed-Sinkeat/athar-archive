# Performance Test — أهل الأثر

**Date:** 2026-06-21
**Phase:** P8 (QA, performance, accessibility, launch)
**Tools:** `scripts/gen-perf-content.mjs` + `scripts/perf-bench.mjs`

---

## What was tested

A stress benchmark was run against 500 generated realistic Arabic Islamic content items to validate that the project stays within its performance budgets at realistic content scale (real corpus is ~21 fixtures; 500 is ~24× that).

**Pipeline tested:**
1. Generate 500 synthetic Arabic content files (`gen-perf-content.mjs`)
2. Full `pnpm build` (validate → Astro static build → Pagefind index)
3. Render-quality analysis: page weight, JS-free content, RTL/lang attributes (`perf-budget.mjs`)

---

## Results — 500 items, 529 pages built

| Metric | Result | Budget / Floor |
|---|---|---|
| Content generation | 0.04s | — |
| Astro build | 5.90s | — |
| Pagefind indexing | 0.353s | — |
| Heaviest page | 61.1 KB | ≤ 150 KB |
| Min JS-free text | 578 chars | ≥ 100 chars |
| RTL/lang check | ✅ All 529 pages pass | 100% |

---

## Verdict

**All budgets pass with large headroom.**

- **Page weight:** 61.1 KB peak is 59% under the 150 KB ceiling. Even at 24× the real corpus, pages are light.
- **JS-free content:** Every page renders readable Arabic text without JavaScript (578 chars minimum, well above the 100-char floor). The `FR-P-05` requirement holds at scale.
- **RTL/Arabic:** `lang="ar"` and `dir="rtl"` present on every one of the 529 pages — no template drift.
- **Build speed:** Astro builds 529 pages in under 6 seconds. Pagefind indexes 515 Arabic pages in 353ms. Both are linear and comfortable.

**Conclusion:** the project is production-ready on the performance dimension. No page-weight, JS, or RTL regression is expected when the real corpus is seeded.

---

## Scripts added

| Script | Purpose |
|---|---|
| `scripts/gen-perf-content.mjs` | Generates N realistic Arabic content files. Flags: `--count` (default 500), `--clean` (remove generated files), `--dry-run`. Files are marker-tagged so clean is surgical. |
| `scripts/perf-bench.mjs` | Full benchmark runner: generate → build → analyze → budget check → print table. |

**npm scripts:**

```
pnpm perf:gen    # generate 500 test items
pnpm perf:clean  # remove generated files (marker-based, safe)
pnpm perf:bench  # run the full benchmark
```

To stress test at larger scale:

```
node scripts/perf-bench.mjs --count=2000
```

---

## Render-quality CI step

`perf-budget.mjs` (`pnpm perf:budget`) is also wired into CI as a post-build check. It runs on every push and enforces:

- Heaviest page ≤ 150 KB
- Every page has JS-free Arabic text (≥ 100 chars in `<main>` without `<script>`)
- Every page carries `lang="ar"` and `dir="rtl"`

This step was added in P8 alongside the link-integrity checker (`pnpm check:links`, 867 links verified).
