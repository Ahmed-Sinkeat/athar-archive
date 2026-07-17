// Mushaf-verification AUDIT for ابن أبي زمنين's notes in quran-tafsir-index.json.
// (Repair itself lives in gen-tafsir-index-zamanin.ts: leading-header semantics
// + content-driven misprint relocation + --replace. This script only reports.)
//
// Each zamanin note body is duplicated across every key of its claimed range,
// so verification is per RANGE, not per key: group placements by body, take
// the group's keys as the claimed range, and require the note's first ﴿quote﴾
// to appear (token-overlap ≥ 0.4 over normalized text, per-token substring
// containment to tolerate Uthmani/imla'i rasm drift) somewhere in that range
// ±2 ayat. Reports failures; changes nothing.
//
// Usage: tsx scripts/fix-tafsir-index-zamanin.ts
import fs from "node:fs";
import path from "node:path";
import { loadMushaf, firstQuote, quoteTokens, tokenScore, rangeText } from "./lib-mushaf.js";

const ZAM = "tafsir-al-quran-al-aziz-ibn-abi-zamanin";
const PASS_MIN = 0.4;

function main() {
  const index: Record<string, { sourceSlug: string; body: string }[]> = JSON.parse(
    fs.readFileSync(path.resolve("src/data/quran-tafsir-index.json"), "utf-8"),
  );
  const mushaf = loadMushaf();

  // body → keys it is placed on (= the claimed range)
  const groups = new Map<string, string[]>();
  for (const [key, notes] of Object.entries(index)) {
    for (const n of notes) if (n.sourceSlug === ZAM) (groups.get(n.body) ?? groups.set(n.body, []).get(n.body)!).push(key);
  }

  let pass = 0, fail = 0, noQuote = 0;
  const failures: string[] = [];
  for (const [body, keys] of groups) {
    const q = firstQuote(body);
    if (!q) { noQuote++; pass++; continue; }
    const tokens = quoteTokens(q);
    const parsed = keys.map((k) => k.split(":").map(Number) as [number, number]);
    const surah = parsed[0][0];
    const lo = Math.min(...parsed.map(([, a]) => a)) - 2;
    const hi = Math.max(...parsed.map(([, a]) => a)) + 2;
    if (tokenScore(tokens, rangeText(mushaf, surah, lo, hi)) >= PASS_MIN) pass++;
    else {
      fail++;
      if (failures.length < 20) failures.push(`${surah}:${lo + 2}-${hi - 2}  ﴿${q.slice(0, 50)}…﴾`);
    }
  }

  console.log(`zamanin ranges: ${groups.size} · pass: ${pass} (${noQuote} no-quote) · fail: ${fail}`);
  if (failures.length) console.log(`failing ranges:\n  ${failures.join("\n  ")}`);
  process.exit(fail > 0 ? 1 : 0);
}

main();
