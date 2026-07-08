#!/usr/bin/env tsx
// De-duplicate src/content/article/*.md by normalized BODY content (not
// title — many posts share a generic short title like "تنبيه" with
// otherwise-unrelated content, so title alone isn't a safe dedup key).
// Source: the shaykh sometimes re-posts the same text as a fresh message
// instead of using Telegram's native forward — that carries no "Forwarded
// from" marker, so the importer's structural dedup can't catch it.
// Keeps the earliest-published copy per duplicate group, deletes the rest.
//
// Usage: pnpm dedupe:articles [--dir <articleDir>] [--dry-run]

import { unlinkSync, existsSync } from "node:fs";
import { join } from "node:path";
import { loadArticleIndex, normText, type ArticleIndexEntry } from "./lib/dedup.ts";

export function findDuplicateGroups(dir: string): ArticleIndexEntry[][] {
  const groups = new Map<string, ArticleIndexEntry[]>();
  for (const e of loadArticleIndex(dir)) {
    if (e.bodyNorm.length < 30) continue; // too short to trust as a dedup signal
    const arr = groups.get(e.bodyNorm) ?? [];
    arr.push(e);
    groups.set(e.bodyNorm, arr);
  }
  return [...groups.values()].filter((arr) => arr.length > 1);
}

function selftest() {
  const a = (cond: boolean, msg: string) => { if (!cond) throw new Error("selftest: " + msg); };
  a(normText("نص  فيه   مسافات") === normText("نص فيه مسافات"), "whitespace normalized");
  a(normText("نص <!-- telegram: x --> فيه تعليق") === normText("نص  فيه تعليق"), "provenance comment stripped");
  console.log("✓ selftest passed (all assertions)");
}

function main() {
  const argv = process.argv.slice(2);
  if (argv.includes("--selftest")) return selftest();

  const flag = (name: string) => { const i = argv.indexOf(name); return i >= 0 ? argv[i + 1] : undefined; };
  const dir = flag("--dir") ?? "src/content/article";
  const dryRun = argv.includes("--dry-run");

  if (!existsSync(dir)) { console.error(`✗ not found: ${dir}`); process.exit(1); }

  const groups = findDuplicateGroups(dir);
  let deleted = 0;
  for (const arr of groups) {
    arr.sort((a, b) => a.publishedAt.localeCompare(b.publishedAt));
    const [keep, ...dupes] = arr;
    console.log(`kept: ${keep.file}  (${dupes.length} duplicate${dupes.length > 1 ? "s" : ""} removed)`);
    for (const d of dupes) {
      if (!dryRun) unlinkSync(join(dir, d.file));
      deleted++;
    }
  }
  console.log(`\n${groups.length} duplicate group(s), ${deleted} file(s) ${dryRun ? "would be " : ""}deleted.`);
}

main();
