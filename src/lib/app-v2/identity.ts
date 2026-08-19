import { createHash, randomBytes } from "node:crypto";
import { normalizeArabic } from "../ar-normalize.js";
import type { DraftBlock, PackageBlock } from "./contract.js";

export interface StableIdRecord {
  ord: number;
  blockId: string;
  fp64: string;
  normHash: string;
  kind: DraftBlock["t"];
  chapter: string;
  prev?: string;
  next?: string;
}

export interface StableIdSidecar {
  schema: 1;
  generation: number;
  sourceHash: string;
  ids: StableIdRecord[];
}

interface Descriptor {
  ord: number;
  block: DraftBlock;
  fp64: string;
  normHash: string;
  prev?: string;
  next?: string;
}

const HEX_32_RE = /^[0-9a-f]{32}$/;
const HEX_16_RE = /^[0-9a-f]{16}$/;
const HEX_64_RE = /^[0-9a-f]{64}$/;

export function sha256Hex(value: string | Buffer): string {
  return createHash("sha256").update(value).digest("hex");
}

function identityText(block: DraftBlock): string {
  if (block.x != null) return block.x;
  if (block.s != null) return `${block.s}\u0000${block.j ?? ""}`;
  if (block.t === "page") return `page\u0000${block.p ?? ""}\u0000${block.vol ?? ""}`;
  return block.t;
}

function normalizedIdentity(block: DraftBlock): string {
  return normalizeArabic(identityText(block)).replace(/\s+/g, " ").trim();
}

export function simhash64(text: string): string {
  const tokens = normalizeArabic(text).match(/[\p{L}\p{N}]+/gu) ?? [];
  if (tokens.length === 0) return sha256Hex(text).slice(0, 16);
  const weights = new Int32Array(64);
  for (const token of tokens) {
    const hash = createHash("sha256").update(token, "utf8").digest();
    for (let bit = 0; bit < 64; bit++) {
      const set = (hash[bit >>> 3]! & (1 << (bit & 7))) !== 0;
      weights[bit] += set ? 1 : -1;
    }
  }
  let value = 0n;
  for (let bit = 0; bit < 64; bit++) if (weights[bit]! >= 0) value |= 1n << BigInt(bit);
  return value.toString(16).padStart(16, "0");
}

function hamming64(a: string, b: string): number {
  let value = BigInt(`0x${a}`) ^ BigInt(`0x${b}`);
  let count = 0;
  while (value) {
    value &= value - 1n;
    count++;
  }
  return count;
}

function describe(blocks: DraftBlock[]): Descriptor[] {
  const hashes = blocks.map((block) => sha256Hex(`${block.t}\u0000${normalizedIdentity(block)}`));
  return blocks.map((block, ord) => ({
    ord,
    block,
    normHash: hashes[ord]!,
    fp64: simhash64(identityText(block)),
    ...(ord > 0 ? { prev: hashes[ord - 1] } : {}),
    ...(ord + 1 < hashes.length ? { next: hashes[ord + 1] } : {}),
  }));
}

function validatePrevious(previous: StableIdSidecar | undefined) {
  if (!previous) return;
  if (previous.schema !== 1 || !Number.isSafeInteger(previous.generation) || previous.generation < 1) {
    throw new Error("invalid stable-ID sidecar header");
  }
  if (!HEX_64_RE.test(previous.sourceHash)) throw new Error("invalid stable-ID sidecar sourceHash");
  const ids = new Set<string>();
  for (const record of previous.ids) {
    if (!Number.isSafeInteger(record.ord) || record.ord < 0) throw new Error("invalid stable-ID ordinal");
    if (!HEX_32_RE.test(record.blockId) || ids.has(record.blockId)) throw new Error("invalid or duplicate stable blockId");
    if (!HEX_16_RE.test(record.fp64) || !HEX_64_RE.test(record.normHash)) throw new Error("invalid stable-ID fingerprint");
    ids.add(record.blockId);
  }
}

function exactScore(current: Descriptor, candidate: StableIdRecord): number {
  return (candidate.chapter === current.block.a ? 1_000 : 0)
    + (candidate.prev != null && candidate.prev === current.prev ? 200 : 0)
    + (candidate.next != null && candidate.next === current.next ? 200 : 0)
    - Math.min(Math.abs(candidate.ord - current.ord), 199);
}

function fuzzyScore(current: Descriptor, candidate: StableIdRecord): number {
  return (candidate.chapter === current.block.a ? 100 : 0)
    + (candidate.prev != null && candidate.prev === current.prev ? 30 : 0)
    + (candidate.next != null && candidate.next === current.next ? 30 : 0)
    - hamming64(candidate.fp64, current.fp64) * 10
    - Math.abs(candidate.ord - current.ord);
}

export function assignStableBlockIds(
  blocks: DraftBlock[],
  sourceHash: string,
  previous?: StableIdSidecar,
): { blocks: PackageBlock[]; sidecar: StableIdSidecar; reused: number; created: number } {
  if (!HEX_64_RE.test(sourceHash)) throw new Error("sourceHash must be a full lowercase SHA-256 digest");
  validatePrevious(previous);
  const descriptors = describe(blocks);
  const old = previous?.ids ?? [];
  const unused = new Set(old.map((record) => record.blockId));
  const exact = new Map<string, StableIdRecord[]>();
  for (const record of old) {
    const key = `${record.kind}\u0000${record.normHash}`;
    const bucket = exact.get(key);
    if (bucket) bucket.push(record);
    else exact.set(key, [record]);
  }

  const chosen = new Map<number, StableIdRecord>();
  for (const descriptor of descriptors) {
    const candidates = (exact.get(`${descriptor.block.t}\u0000${descriptor.normHash}`) ?? [])
      .filter((record) => unused.has(record.blockId))
      .sort((a, b) => exactScore(descriptor, b) - exactScore(descriptor, a) || a.ord - b.ord);
    const match = candidates[0];
    if (match) {
      chosen.set(descriptor.ord, match);
      unused.delete(match.blockId);
    }
  }

  for (const descriptor of descriptors) {
    if (chosen.has(descriptor.ord)) continue;
    const from = Math.max(0, descriptor.ord - 50);
    const to = Math.min(old.length - 1, descriptor.ord + 50);
    const candidates: StableIdRecord[] = [];
    for (let index = from; index <= to; index++) {
      const record = old[index];
      if (
        record
        && unused.has(record.blockId)
        && record.kind === descriptor.block.t
        && hamming64(record.fp64, descriptor.fp64) <= 8
      ) candidates.push(record);
    }
    candidates.sort((a, b) => fuzzyScore(descriptor, b) - fuzzyScore(descriptor, a) || a.ord - b.ord);
    const match = candidates[0];
    if (match) {
      chosen.set(descriptor.ord, match);
      unused.delete(match.blockId);
    }
  }

  const allocated = new Set(old.map((record) => record.blockId));
  let created = 0;
  const records = descriptors.map((descriptor): StableIdRecord => {
    let blockId = chosen.get(descriptor.ord)?.blockId;
    if (!blockId) {
      do blockId = randomBytes(16).toString("hex"); while (allocated.has(blockId));
      allocated.add(blockId);
      created++;
    }
    return {
      ord: descriptor.ord,
      blockId,
      fp64: descriptor.fp64,
      normHash: descriptor.normHash,
      kind: descriptor.block.t,
      chapter: descriptor.block.a,
      ...(descriptor.prev ? { prev: descriptor.prev } : {}),
      ...(descriptor.next ? { next: descriptor.next } : {}),
    };
  });

  const generation = previous == null ? 1 : previous.sourceHash === sourceHash ? previous.generation : previous.generation + 1;
  const sidecar: StableIdSidecar = { schema: 1, generation, sourceHash, ids: records };
  return {
    blocks: descriptors.map((descriptor) => {
      const record = records[descriptor.ord]!;
      return { ...descriptor.block, i: descriptor.ord, id: record.blockId, fp: record.fp64 };
    }),
    sidecar,
    reused: records.length - created,
    created,
  };
}
