#!/usr/bin/env node
/**
 * أهل الأثر — Performance Benchmark Runner
 *
 * Measures and reports:
 *   1. Astro build time
 *   2. Per-content-type file counts
 *   3. Dist output stats (page count, total size)
 *
 * Usage:
 *   node scripts/perf-bench.mjs [--count=N] [--skip-gen] [--skip-clean]
 *
 *   --count=N     generate N content items before benchmarking (default: 500)
 *   --skip-gen    skip content generation (use existing content)
 *   --skip-clean  keep generated files after the benchmark
 */

import { execSync, spawnSync } from "node:child_process";
import {
  existsSync, readdirSync, statSync,
  openSync, readSync, closeSync, unlinkSync,
} from "node:fs";
import { join } from "node:path";
import { performance } from "node:perf_hooks";
import { parseArgs } from "node:util";

// ---------------------------------------------------------------------------
// CLI args
// ---------------------------------------------------------------------------

const { values: args } = parseArgs({
  options: {
    count:       { type: "string",  default: "500" },
    "skip-gen":  { type: "boolean", default: false },
    "skip-clean":{ type: "boolean", default: false },
  },
  strict: false,
});

const COUNT      = parseInt(args.count ?? "500", 10);
const SKIP_GEN   = args["skip-gen"]   ?? false;
const SKIP_CLEAN = args["skip-clean"] ?? false;

// ---------------------------------------------------------------------------
// Paths
// ---------------------------------------------------------------------------

const CWD      = process.cwd();
const CONTENT  = join(CWD, "src", "content");
const DIST     = join(CWD, "dist");
const MARKER   = "@perf-generated";

const CONTENT_TYPES = [
  "person", "subject", "topic", "book", "poem",
  "series", "lesson", "article", "benefit", "question",
  "audio", "annotation", "announcement",
];

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Run a shell command and return elapsed ms + stdout. */
function timedExec(label, cmd) {
  console.log(`\n▶  ${label}`);
  console.log(`   $ ${cmd}`);
  const t0  = performance.now();
  const res = spawnSync(cmd, { shell: true, cwd: CWD, stdio: "inherit" });
  const ms  = performance.now() - t0;
  if (res.status !== 0) {
    console.error(`\n✗  "${label}" failed (exit ${res.status})`);
    process.exit(res.status ?? 1);
  }
  console.log(`   ⏱  ${(ms / 1000).toFixed(2)}s`);
  return ms;
}

/** Count .md files in a directory. */
function countMd(dir) {
  if (!existsSync(dir)) return 0;
  return readdirSync(dir, { withFileTypes: true })
    .filter(e => e.isFile() && e.name.endsWith(".md"))
    .length;
}

/** Count generated (marker-tagged) .md files in a directory. */
function countGenerated(dir) {
  if (!existsSync(dir)) return 0;
  let n = 0;
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (!entry.isFile() || !entry.name.endsWith(".md")) continue;
    const fp  = join(dir, entry.name);
    try {
      const fd  = openSync(fp, "r");
      const buf = Buffer.alloc(300);
      const len = readSync(fd, buf, 0, 300, 0);
      closeSync(fd);
      if (buf.subarray(0, len).toString("utf8").includes(MARKER)) n++;
    } catch { /* skip */ }
  }
  return n;
}

/** Walk a directory recursively and return all file paths. */
function walk(dir) {
  if (!existsSync(dir)) return [];
  return readdirSync(dir, { withFileTypes: true }).flatMap(e => {
    const p = join(dir, e.name);
    return e.isDirectory() ? walk(p) : [p];
  });
}

/** Remove marker-tagged files from all content dirs. */
function cleanGenerated() {
  let removed = 0;
  for (const type of CONTENT_TYPES) {
    const dir = join(CONTENT, type);
    if (!existsSync(dir)) continue;
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      if (!entry.isFile() || !entry.name.endsWith(".md")) continue;
      const fp = join(dir, entry.name);
      try {
        const fd  = openSync(fp, "r");
        const buf = Buffer.alloc(300);
        const len = readSync(fd, buf, 0, 300, 0);
        closeSync(fd);
        if (buf.subarray(0, len).toString("utf8").includes(MARKER)) {
          unlinkSync(fp); removed++;
        }
      } catch { /* skip */ }
    }
  }
  return removed;
}

/** Format bytes as KB or MB. */
function fmtBytes(b) {
  return b >= 1_048_576
    ? `${(b / 1_048_576).toFixed(1)} MB`
    : `${(b / 1024).toFixed(0)} KB`;
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

const benchStart = performance.now();

console.log(`
╔══════════════════════════════════════════════════════╗
║    أهل الأثر — Build & Search Performance Bench    ║
╚══════════════════════════════════════════════════════╝
`);

// ── Step 1: Generate content ─────────────────────────────────────────────────

let genMs = 0;
if (!SKIP_GEN) {
  genMs = timedExec(
    `Generate ${COUNT} content items`,
    `node scripts/gen-perf-content.mjs --count=${COUNT}`,
  );
}

// ── Step 2: Count content ────────────────────────────────────────────────────

console.log("\n📁 Content inventory:");
let totalContent = 0, totalGenerated = 0;
for (const type of CONTENT_TYPES) {
  const dir  = join(CONTENT, type);
  const all  = countMd(dir);
  const gen  = countGenerated(dir);
  const orig = all - gen;
  totalContent   += all;
  totalGenerated += gen;
  if (all > 0) {
    console.log(`   ${type.padEnd(14)} ${String(all).padStart(4)} total  (${gen} generated + ${orig} original)`);
  }
}
console.log(`   ${"TOTAL".padEnd(14)} ${String(totalContent).padStart(4)}`);

// ── Step 3: Build ────────────────────────────────────────────────────────────

const buildMs = timedExec("Astro build", "pnpm build");

// ── Step 4: Analyse dist ─────────────────────────────────────────────────────

if (existsSync(DIST)) {
  const allFiles  = walk(DIST);
  const htmlFiles = allFiles.filter(f => f.endsWith(".html"));
  const totalSize = allFiles.reduce((sum, f) => {
    try { return sum + statSync(f).size; } catch { return sum; }
  }, 0);
  const htmlSize  = htmlFiles.reduce((sum, f) => {
    try { return sum + statSync(f).size; } catch { return sum; }
  }, 0);

  console.log(`\n📦 Dist output:`);
  console.log(`   HTML pages     : ${htmlFiles.length}`);
  console.log(`   HTML size      : ${fmtBytes(htmlSize)}`);
  console.log(`   Total dist     : ${fmtBytes(totalSize)}`);
}

// ── Step 5: Perf budget check ────────────────────────────────────────────────

console.log("\n🔍 Running perf budget checks…");
const budgetRes = spawnSync("node scripts/perf-budget.mjs", {
  shell: true, cwd: CWD, stdio: "inherit",
});

// ── Step 6: Clean up ─────────────────────────────────────────────────────────

if (!SKIP_CLEAN) {
  process.stdout.write("\n🧹 Removing generated files… ");
  const removed = cleanGenerated();
  console.log(`removed ${removed} file(s).`);
}

// ── Summary ───────────────────────────────────────────────────────────────────

const totalMs = performance.now() - benchStart;

console.log(`
╔══════════════════════════════════════════════════════╗
║                   Benchmark Results                  ║
╚══════════════════════════════════════════════════════╝

  Content items   : ${totalContent} (${totalGenerated} generated)
  Generation time : ${genMs > 0 ? (genMs / 1000).toFixed(2) + "s" : "skipped"}
  Build time      : ${(buildMs / 1000).toFixed(2)}s
  Total wall time : ${(totalMs / 1000).toFixed(2)}s
  Perf budget     : ${budgetRes.status === 0 ? "✅ PASS" : "❌ FAIL"}

  Tip: Re-run with larger --count values to stress-test build scaling.
       Suggested: 100, 500, 1000, 2000
`);

process.exit(budgetRes.status ?? 0);
