# M0 — prototype results

Specs, acceptance criteria and execution order: [`../main-plan.md`](../main-plan.md) §20.

**Rule: a prototype is not done until its row here has a measured number and a
verdict.** "Seemed fine" is not a result. If a prototype fails, record what it
failed at and which fallback was taken — that record is the reason M0 exists.

The architecture baseline is selected. Timing numbers below come from the connected API 35
phone and are **functional/preliminary**, not substitutes for the required API 26 / 2 GB
macrobenchmark profile. A failed certification gate is evidence to revise the affected
choice; the missing API-tier work is listed at the end rather than being disguised as a pass.

| ID | Prototype | Status | Result | Verdict |
|---|---|---|---|---|
| R1 | Compose selection over a virtualised list | ✅ fallback verified · 2026-08-16 | Compose 1.11 exposes **0 public endpoint callbacks**, so `SelectionContainer` cannot recover stable block IDs + UTF-16 offsets. The native `TextView`-in-Compose fallback selected block 10:3 → 230:17: 17,964 UTF-16 units, exact clipboard text, with two-`LF` block separators. | Use a bounded native selectable text surface inside Compose; do not build annotations on `SelectionContainer` alone |
| R2 | Room 3 + `BundledSQLiteDriver` | ⚠️ functional pass; API 26 pending · 2026-08-16 | API 35: cold create/open 277.3 ms, 20-book import 7,019 ms, phrase `bm25` 28.7 ms, prefix 6.5 ms, 20/20 hits, DB 5,268 KiB. Stripped SQLite is 1.014–1.019 MiB/ABI, under the 1.5 MiB split gate. | Driver/query path works and size passes; the ≤150 ms API 26 cold-open gate is unmeasured and the API 35 number is not a pass |
| R2b | FTS: regular vs contentless variants | ✅ compact contentless selected · 2026-08-16 | 5,428 real blocks / 20 books. Net index size after subtracting an identical relational baseline: regular+prefix 222.4%, contentless+prefix 101.7%, contentless lean 56.4%, **contentless `detail=none` 35.2%**. Compact: import 1,072.4 ms, `bm25` 10.1 ms, prefix query 18.4 ms, delete 42.8 ms, 20/20 hits; offline rebuild 2,176.0 ms, 20/20. | Raw compact DDL, no prefix index. Phrase/compound syntax becomes explicit-AND candidate retrieval followed by exact normalized-body filtering |
| R3 | Framed package + 82 MB import | ⚠️ frame decision ready; RSS soft miss · 2026-08-16 | 81.98 MiB / 75,464 blocks, 38 × 2,000-block frames: full import 15,488.1 ms (5.3 MiB/s), first new block after corrected checkpoint resume 2.9 ms, record/raw 1.060×, package/raw 0.155×. Peak RSS 221.7 MiB, 1.7 MiB over the target. A process-kill run exposed and fixed an `Int.MAX_VALUE` resume overflow. | Select 2,000-block frames and a 25 MiB compressed part threshold; recheck RSS/resume on the API 26 profile |
| R4 | Block identity survival (Node/TS) | ❌ fallback selected · 2026-08-16 | 8,000 real blocks: duplicate insert 99.987% exact/100% recovered; previous edit 99.987%/100%; repeated isnad insertions 99.000%/99.625%; move 99.963%/100%; **heading rename 0%/100%** | Stable-ID sidecar required; derived ID fails the per-mutation exact-survival gate |
| R5 | FTS query builder (pure JVM) | ✅ pass · 2026-08-16 | 1,000 adversarial inputs: 100% well-formed-or-empty, 0 SQLite FTS5 errors; phrases, trailing `*`, and `ال`/compound expansion green; real 50-book sample recall 50/50 (25 article, 25 `عبد الله`/`عبدالله`) | Explicit generated `AND` required between top-level terms; implicit adjacency fails beside an `OR` group |
| R6 | Backup rules across three API tiers | ⚠️ packaged/static pass; runtime tiers pending · 2026-08-16 | API 26–27: zero database `<include>` rules; API 28–30: `athar_user.db` D2D-only; API 31+: user DB cloud-excluded/transfer-included and rebuildable data excluded. Static assertions pass and `aapt2` confirms all three packaged rule resources. The separate shell APK install was rejected twice by Xiaomi, so no `bmgr` result is claimed. | Rules are code-ready; `bmgr` cloud/D2D checks remain required on API 28–30 and 31+ |
| R7 | End-to-end Athar workload | ❌ full-detail baseline fails · 2026-08-16 | Real 75,464-block Room/Paging/full-detail-FTS baseline: import+index 528,093.2 ms, DB 206.9 MiB, import peak 237.7 MiB. Search 15.0 ms and exact vocalised mapping pass; first jump 479.6 ms fails. Ten-second driven scroll: 180 frames, 594 dropped intervals, 194.9 ms slowest, 378.1 MiB peak reading RSS—hard fail. A real background kill restored block 37,795 with exact stable ID; no `INTERNET` permission. The run also found and fixed a Paging restore race and an invalid phrase-fixture crash. | Do not ship Room-managed full-detail FTS. Integrate the R2b compact raw FTS path and profile the production reader in a release macrobenchmark before claiming the R7 budgets |
| R8 | Navigation 3 spike | ✅ pass · 2026-08-16 | Phone back stack retained book 1 VM instance/state, isolated book 2, and restored book 1 after back and real process death. At 617 dp the adaptive layout rendered library + reader panes; density was restored immediately after the test. | Navigation 3 1.1.5 selected; no Nav2 blocker found |

## Decisions this unblocks

Each stays **provisional** until the prototype that decides it reports.

| Provisional decision | Decided by | Current assumption |
|---|---|---|
| FTS mode | R2b | **contentless + `contentless_delete=1` + `detail=none`, no prefix index**; exact phrase post-filter |
| Frame size (blocks per gzip member) | R3 | **2,000 selected** — 4,000 saves only 0.16% but doubles resume work |
| Part-split threshold | R3 | **25 MiB compressed**; the largest real fixture is 12.73 MiB |
| Block identity: derived vs stable-ID sidecar | R4 | **stable-ID sidecar selected** — heading rename invalidated every derived ID in the chapter |
| Reader composition strategy | R1 | **bounded selectable native text surface inside Compose** |
| Navigation | R8 | **Navigation 3 1.1.5** |
| End-to-end baseline | R7 | full-detail Room FTS rejected; compact raw FTS + reader performance work required |

## Recording a result

Keep it short and quantitative:

```
R3 · ✅ pass · 2026-08-·· · Pixel 4a emulator, API 26, 2 GB
resume after kill 1.4 s · overhead 1.31× · throughput 5.2 MB/s · peak RSS 198 MB
frame size 2,000 blocks chosen (4,000 gave +8% ratio, +0.9 s resume)
```

A failure gets the same treatment plus the fallback taken:

```
R1 · ❌ fail · selection lost after ~40 blocks of scroll
fallback: windowed non-lazy Column, ±60 blocks. R7 re-run against it.
```

## Environment

Record the device/emulator profile with every timing. The budgets in
`main-plan.md` §17 are set against a **low-memory API 26 profile (2 GB RAM
emulator)**; a number from a flagship is not comparable and should not be
entered without saying so.

- JVM R5 host: OpenJDK 21, SQLite 3.53.3 with `ENABLE_FTS5`, Arch Linux.
- Connected functional-test device: Redmi 23053RN02Y, Android 15 / API 35,
  1080×2460 at 440 dpi, 7.87 GB RAM. This is **not** the API 26 / 2 GB
  performance baseline; its timings will be labelled functional/preliminary.
- The SDK has no API 26 system image and no AVD. API 26 / 2 GB numbers therefore remain
  unmeasured; do not compare the Redmi numbers directly with §17 budgets.
- Xiaomi accepted the M0 harness updates but rejected the separate production-shell install
  twice, so R6 runtime backup inspection could not proceed.

## Remaining certification gates

These do not reopen the architecture choices above, but they must be green before release:

1. Run R2, R3 and the compact/release form of R7 on a 2 GB API 26 profile.
2. Run R6 `bmgr` cloud and device-transfer checks on API 28–30 and API 31+ devices.
3. Re-run R7 after the raw compact index is integrated; meet jump, jank and reading-memory
   budgets rather than carrying forward the failed full-detail numbers.
