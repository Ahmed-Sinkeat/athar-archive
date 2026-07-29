// Sunan Ibn Majah (ت الأرنؤوط edition) lost all 37 كتاب-level headings during
// compilation — every "باب" ended up flattened to h2 with no parent. The
// plain "سنن ابن ماجه" edition (already in this corpus) has the correct
// structure intact; hadith numbering is verified identical between the two
// editions (spot-checked #267, #667, #4100 — same isnad opening both places).
// This inserts the real كتاب headings at the verified position (found via
// matching hadith numbers, not guessed) and demotes existing "## باب" lines
// that now fall under a كتاب to "### باب" — text of every line is untouched,
// only heading levels/insertions change.
import * as fs from "fs";

const strip = (s) => s.replace(/\p{Mn}/gu, "");
const REF = "src/content/book-lg/snn-abn-majh.md";
const TARGET = "src/content/book-lg/sunan-ibn-majah.md";

// 1. extract {title, firstHadithNum} for each of the 37 كتب from the reference
const refLines = fs.readFileSync(REF, "utf-8").split("\n");
const kitabs = [];
let cur = null;
for (const line of refLines) {
  if (/^## كتاب/.test(strip(line).trim())) {
    cur = { title: line.replace(/^##\s+/, "").trim(), firstNum: null };
    kitabs.push(cur);
    continue;
  }
  if (cur && cur.firstNum === null) {
    const m = strip(line).match(/^(\d+)\s*-\s*(حدثنا|اخبرنا)/);
    if (m) cur.firstNum = Number(m[1]);
  }
}
if (kitabs.some((k) => k.firstNum === null)) {
  console.error("some kitabs had no hadith number found:", kitabs.filter((k) => !k.firstNum));
  process.exit(1);
}
console.log(`reference: ${kitabs.length} كتب extracted`);

// 2. locate each firstNum's line in the target edition
const targetLines = fs.readFileSync(TARGET, "utf-8").split("\n");
const numToLine = new Map();
for (let i = 0; i < targetLines.length; i++) {
  const m = strip(targetLines[i]).match(/^(\d+)\s*-\s*(حدثنا|اخبرنا)/);
  if (m) {
    const n = Number(m[1]);
    if (!numToLine.has(n)) numToLine.set(n, i); // first occurrence only
  }
}

const insertions = []; // { atLine, title }
for (const k of kitabs) {
  const hadithLine = numToLine.get(k.firstNum);
  if (hadithLine === undefined) {
    console.error(`✗ could not find hadith #${k.firstNum} for "${k.title}" in target — skipping`);
    continue;
  }
  // walk backward (bounded — a real باب opener is always close by; further
  // than that risks grabbing one that belongs to the previous كتاب instead)
  // to the nearest "## باب" heading at/before the hadith line, so it becomes
  // the كتاب's first sub-section. No match in range → insert right at the
  // hadith itself (degrades gracefully, still adds the كتاب heading).
  const SEARCH_WINDOW = 30;
  let atLine = hadithLine;
  for (let i = hadithLine; i >= Math.max(0, hadithLine - SEARCH_WINDOW); i--) {
    if (/^#{2,3}\s+باب|^#{2,3}\s+بَاب/.test(strip(targetLines[i]))) { atLine = i; break; }
  }
  insertions.push({ atLine, title: k.title, firstNum: k.firstNum });
}
console.log(`resolved ${insertions.length}/${kitabs.length} insertion points`);
insertions.sort((a, b) => a.atLine - b.atLine);

// sanity: insertion points must be strictly increasing (kitabs appear in order)
for (let i = 1; i < insertions.length; i++) {
  if (insertions[i].atLine <= insertions[i - 1].atLine) {
    console.error(`✗ non-increasing insertion order: "${insertions[i - 1].title}" (${insertions[i - 1].atLine}) then "${insertions[i].title}" (${insertions[i].atLine})`);
    process.exit(1);
  }
}

if (process.argv.includes("--dry")) {
  for (const ins of insertions) {
    console.log(`\n[#${ins.firstNum}] insert "## ${ins.title}" before line ${ins.atLine + 1}:`);
    console.log("  " + targetLines[ins.atLine]);
    console.log("  " + (targetLines[ins.atLine + 1] ?? "").slice(0, 90));
  }
  process.exit(0);
}

// 3. demote every existing "## باب"/"## بَاب" to "### ..." (they now all fall
// either inside a كتاب's range, or — before the first insertion — as part of
// المقدمة, matching the reference edition's own structure there too).
const out = [];
for (let i = 0; i < targetLines.length; i++) {
  const ins = insertions.find((x) => x.atLine === i);
  if (ins) out.push(`## ${ins.title}`);
  const line = targetLines[i];
  if (/^##\s+(باب|بَاب)/.test(strip(line).trim())) {
    out.push(line.replace(/^##/, "###"));
    continue;
  }
  out.push(line);
}

fs.writeFileSync(TARGET, out.join("\n"));
console.log(`✓ inserted ${insertions.length} كتاب headings, wrote ${TARGET}`);
