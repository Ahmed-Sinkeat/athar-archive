import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import { normalizeArabic } from "../src/lib/ar-normalize.js";
import { slugifyArabic, splitChapters } from "../src/lib/chapters.js";

type Block = {
  origin: string;
  chapterAnchor: string;
  text: string;
  ordinal: number;
  blockId: string;
  fp64: bigint;
};

type Seed = Omit<Block, "ordinal" | "blockId" | "fp64">;

const BOOK_ID = "al-msnd-al-mwdway-al-jama-llktb-al-ashra";
const BOOK_PATH = new URL(`../src/content/book-lg/${BOOK_ID}.md`, import.meta.url);
const SAMPLE_BLOCKS = 8_000;

function sha(bytes: string): Buffer {
  return createHash("sha256").update(bytes, "utf8").digest();
}

function simhash64(text: string): bigint {
  const weights = new Int32Array(64);
  const tokens = normalizeArabic(text).match(/[\p{L}\p{N}]+/gu) ?? [];
  for (const token of tokens) {
    const hash = sha(token);
    for (let bit = 0; bit < 64; bit++) {
      const set = (hash[bit >>> 3] & (1 << (bit & 7))) !== 0;
      weights[bit] += set ? 1 : -1;
    }
  }
  let value = 0n;
  for (let bit = 0; bit < 64; bit++) if (weights[bit] >= 0) value |= 1n << BigInt(bit);
  return value;
}

function identify(seeds: Seed[]): Block[] {
  const duplicates = new Map<string, number>();
  let previous = "";
  return seeds.map((seed, ordinal) => {
    const norm = normalizeArabic(seed.text).trim();
    const duplicateKey = `${seed.chapterAnchor}\u0000${norm}`;
    const duplicateIndex = duplicates.get(duplicateKey) ?? 0;
    duplicates.set(duplicateKey, duplicateIndex + 1);
    const material = `${BOOK_ID}\u0000${seed.chapterAnchor}\u0000${norm}\u0000${previous.slice(0, 64)}\u0000${duplicateIndex}`;
    const blockId = sha(material).subarray(0, 16).toString("hex");
    if (norm) previous = norm;
    return { ...seed, ordinal, blockId, fp64: simhash64(norm) };
  });
}

function hamming(a: bigint, b: bigint): number {
  let value = a ^ b;
  let count = 0;
  while (value) {
    value &= value - 1n;
    count++;
  }
  return count;
}

function score(original: Block[], mutatedSeeds: Seed[]) {
  const mutated = identify(mutatedSeeds);
  const byOrigin = new Map(mutated.map((block) => [block.origin, block]));
  let exact = 0;
  let recovered = 0;
  for (const old of original) {
    const current = byOrigin.get(old.origin);
    if (!current) continue;
    if (old.blockId === current.blockId) {
      exact++;
      recovered++;
    } else if (Math.abs(old.ordinal - current.ordinal) <= 50 && hamming(old.fp64, current.fp64) <= 8) {
      recovered++;
    }
  }
  return {
    exactPct: (exact * 100) / original.length,
    recoveredPct: (recovered * 100) / original.length,
  };
}

function clone(seeds: Seed[]): Seed[] {
  return seeds.map((seed) => ({ ...seed }));
}

const markdown = await readFile(BOOK_PATH, "utf8");
const body = markdown.replace(/^---\n[\s\S]*?\n---\n/, "");
const chapters = splitChapters(body);
const largest = chapters.chapters.reduce((a, b) => a.content.length >= b.content.length ? a : b);
const chapterAnchor = largest.slug;
const paragraphs = largest.content
  .split(/\n\s*\n+/)
  .map((text) => text.trim())
  .filter((text) => text && !/^<hr\b/i.test(text))
  .slice(0, SAMPLE_BLOCKS);

const seeds: Seed[] = paragraphs.map((text, index) => ({
  origin: `b${index}`,
  chapterAnchor,
  text,
}));
const original = identify(seeds);

const scenarios = new Map<string, Seed[]>();

const inserted = clone(seeds);
const insertAt = Math.floor(inserted.length / 3);
inserted.splice(insertAt, 0, { ...inserted[insertAt], origin: "inserted-duplicate" });
scenarios.set("identical paragraph inserted before duplicate", inserted);

const previousEdited = clone(seeds);
const editAt = Math.floor(previousEdited.length / 2);
previousEdited[editAt]!.text += " [تصحيح اختباري]";
scenarios.set("previous paragraph edited", previousEdited);

const repeated = clone(seeds);
const formula = repeated.find((block) => normalizeArabic(block.text).includes("حدثنا")) ?? repeated[10]!;
for (let i = 100; i < repeated.length; i += 100) {
  repeated.splice(i, 0, { ...formula, origin: `formula-${i}` });
}
scenarios.set("repeated isnad formulae inserted", repeated);

const renamed = clone(seeds).map((seed) => ({
  ...seed,
  chapterAnchor: slugifyArabic(`${largest.title} مصحح`),
}));
scenarios.set("heading renamed", renamed);

const moved = clone(seeds);
const [movedBlock] = moved.splice(Math.floor(moved.length / 2), 1);
moved.splice(Math.floor(moved.length / 2) + 20, 0, movedBlock!);
scenarios.set("paragraph moved within chapter", moved);

console.log(`R4 corpus: ${BOOK_ID}, largest chapter «${largest.title}», ${original.length} blocks`);
console.log("mutation\texact\texact+fuzzy\tverdict");
let sidecar = false;
for (const [name, mutation] of scenarios) {
  const result = score(original, mutation);
  const pass = result.exactPct >= 95 && result.recoveredPct >= 99;
  if (result.exactPct < 90) sidecar = true;
  console.log(`${name}\t${result.exactPct.toFixed(3)}%\t${result.recoveredPct.toFixed(3)}%\t${pass ? "PASS" : "FAIL"}`);
}
console.log(`decision\t${sidecar ? "stable-ID sidecar required" : "derived identity retained"}`);
