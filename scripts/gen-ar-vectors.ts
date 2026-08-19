// Golden test vectors for the Kotlin port of normalizeArabic (docs/android-app.md:
// app-side offline search must normalize queries EXACTLY like the site or the
// same query returns different results in the app — so the port is verified
// against vectors generated from the TS implementation, not hand-ported hopes).
//
// Output: android/core/athar-text/src/test/resources/ar-normalize-vectors.tsv
//   one "input<TAB>expected" pair per line, \t \n \\ escaped.
// Regen: pnpm app:vectors (after any change to src/lib/ar-normalize.ts —
// ArNormalizeTest.kt then proves the Kotlin side still agrees).
//
// Inputs = hand-picked edge cases (escaped codepoints, never pasted Arabic
// char classes — bidi display reorders them silently) + a deterministic sample
// of real corpus lines (every 30th entry's first non-empty line).
import fs from "node:fs";
import path from "node:path";
import assert from "node:assert";
import { normalizeArabic } from "../src/lib/ar-normalize.js";
import { loadContentFromDisk } from "../src/lib/load.js";

const OUT = path.resolve("android/core/athar-text/src/test/resources/ar-normalize-vectors.tsv");

const edge: string[] = [
  "", // empty
  "abc 123", // latin passthrough
  "١٢٣٤٥٦٧٨٩٠", // arabic-indic digits pass through
  "وَالْعَادِيَاتِ ضَبْحًا", // the vitest cases
  "أإآٱ ى ة",
  "الصَّلَاة",
  "الصلاه",
  // every boundary of the deleted ranges (U+0610-061A, 0640, 064B-065F, 0670,
  // 06D6-06ED) plus the codepoints just outside them (must NOT be deleted) —
  // built from codepoints, not literals: bidi display reorders pasted marks.
  ...[0x0610, 0x061a, 0x0640, 0x064b, 0x065f, 0x0670, 0x06d6, 0x06ed].map((cp) => `x${String.fromCodePoint(cp)}x`),
  ...[0x060f, 0x061b, 0x063f, 0x0641, 0x064a, 0x0660, 0x0671, 0x06d5, 0x06ee].map((cp) => `x${String.fromCodePoint(cp)}x`),
  // mapped codepoints
  "آأإٱ ة ى",
  // NFC matters: alef + combining hamza above/below compose to أ/إ first, then map to ا
  "أ", "إ", "ؤ", "ئ",
  // tatweel inside a word, dagger alif (الرحمٰن), quranic sajda mark
  "الرحـــمن", "الرحمٰن", "۩ سجدة",
  // ta marbuta mid-word vs final, alef maqsura
  "مدرسة كبيرة", "على المصطفى",
  // mixed direction + punctuation
  "قال (الشيخ): \"نعم\" — ثم سكت.",
];

// deterministic corpus sample — first non-empty line of every 30th entry,
// truncated to 200 chars. Sorted walk, no randomness, no timestamps: the tsv
// must be byte-stable across regens on unchanged content.
const entries = loadContentFromDisk().sort((a, b) =>
  a.collection === b.collection ? a.id.localeCompare(b.id) : a.collection.localeCompare(b.collection),
);
const corpus: string[] = [];
for (let i = 0; i < entries.length; i += 30) {
  const line = entries[i].body.split("\n").find((l) => l.trim().length > 0);
  if (line) corpus.push(line.trim().slice(0, 200));
}

const esc = (s: string) => s.replace(/\\/g, "\\\\").replace(/\t/g, "\\t").replace(/\n/g, "\\n");
const inputs = [...edge, ...corpus];
const lines = inputs.map((s) => `${esc(s)}\t${esc(normalizeArabic(s))}`);

// selftest: the vitest-known outputs, and normalization must be idempotent
assert.strictEqual(normalizeArabic("وَالْعَادِيَاتِ ضَبْحًا"), "والعاديات ضبحا");
assert.strictEqual(normalizeArabic("أإآٱ ى ة"), "اااا ي ه");
assert.strictEqual(normalizeArabic("الصَّلَاة"), normalizeArabic("الصلاه"));
for (const s of inputs) assert.strictEqual(normalizeArabic(normalizeArabic(s)), normalizeArabic(s), `not idempotent: ${s}`);

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, lines.join("\n") + "\n", "utf-8");
console.log(`✓ gen-ar-vectors: ${edge.length} edge + ${corpus.length} corpus vectors → ${OUT}`);
