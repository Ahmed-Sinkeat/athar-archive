// Local release gate: authenticate the root and every text/frame/audio artifact.
import assert from "node:assert/strict";
import { createPublicKey, createHash } from "node:crypto";
import { createReadStream, readFileSync } from "node:fs";
import path from "node:path";
import { gunzipSync } from "node:zlib";
import type { CatalogDocument, PackageIndex, PackageBlock } from "../src/lib/app-v2/contract.js";
import { sha256Hex, verifyRootEnvelope } from "../src/lib/app-v2/package.js";

const root = path.resolve(process.argv[2] ?? "dist/app-content/app/v2");
const keyFile = process.env.ATHAR_CONTENT_SIGNING_KEY_FILE;
const keyId = process.env.ATHAR_CONTENT_SIGNING_KEY_ID;
assert(keyFile && keyId, "source publish.env to verify the signed root");
const payload = verifyRootEnvelope(readFileSync(path.join(root, "index.json")), new Map([[keyId, createPublicKey(readFileSync(keyFile))]]));
function artifact(ref: { path: string; hash: string; size: number }) {
  const file = path.resolve(root, ref.path);
  assert(file.startsWith(root + path.sep), "unsafe artifact path");
  const bytes = readFileSync(file);
  assert.equal(bytes.length, ref.size, ref.path);
  assert.equal(sha256Hex(bytes), ref.hash, ref.path);
  return bytes;
}
const catalog = JSON.parse(artifact(payload.catalog).toString()) as CatalogDocument;
artifact(payload.tombstones);
const identities = new Set(catalog.entries.map(entry => entry.id));
assert.equal(identities.size, catalog.entries.length);
let frames = 0, blocks = 0, recordings = 0;
let adjacentDividers = 0, leadingDividers = 0;
const audioSeen = new Set<string>();
for (const entry of catalog.entries) {
  const bytes = artifact(entry.pkg);
  const index = JSON.parse(artifact({ path: entry.pkg.idxPath, hash: entry.pkg.idxHash, size: entry.pkg.idxSize }).toString()) as PackageIndex;
  assert.equal(index.entityId, entry.id);
  assert.equal(index.coll, entry.coll);
  assert.equal(index.v, entry.v);
  let nextOffset = 0, nextOrdinal = 0, uncompressed = 0;
  let previousWasPage = false, sawMainText = false;
  const blockIds = new Set<string>();
  const noteIds = new Set<string>();
  let expectedNotes = 0;
  for (const frame of index.frames) {
    assert.equal(frame.off, nextOffset, entry.id);
    assert.equal(frame.ord, nextOrdinal, entry.id);
    const compressed = bytes.subarray(frame.off, frame.off + frame.len);
    assert.equal(sha256Hex(compressed), frame.sha256, entry.id);
    const raw = gunzipSync(compressed);
    uncompressed += raw.length;
    const lines = raw.toString().trim().split("\n").map(line => JSON.parse(line));
    if (frame.ord === 0) {
      const header = lines.shift();
      assert.equal(header.t, "header");
      assert.equal(header.id, entry.id);
      assert.equal(header.blocks, entry.pkg.blocks);
      expectedNotes = header.footnotes;
    }
    const footnotes = lines.filter(line => line.t === "fn");
    const frameNoteIds = new Set(footnotes.map(note => note.id));
    assert.equal(frameNoteIds.size, footnotes.length, entry.id);
    for (const id of frameNoteIds) noteIds.add(id);
    const content = lines.filter(line => line.t !== "fn") as PackageBlock[];
    assert.equal(content.length, frame.n, entry.id);
    for (const block of content) {
      if (block.t === "page") {
        if (previousWasPage) adjacentDividers++;
        if (!sawMainText) leadingDividers++;
      } else if (block.t !== "notes") sawMainText = true;
      previousWasPage = block.t === "page";
      assert.equal(block.i, nextOrdinal++, entry.id);
      assert(!blockIds.has(block.id), `${entry.id}: duplicate block ID`);
      blockIds.add(block.id);
      for (const id of block.f ?? []) assert(frameNoteIds.has(id), `${entry.id}: missing note ${id}`);
      const text = block.x ?? [block.s, block.j].filter(Boolean).join("\n");
      for (const span of block.sp ?? []) {
        assert(span.s >= 0 && span.e > span.s && span.e <= text.length, `${entry.id}: invalid span`);
        if (span.k === "sup" && span.target != null) assert(frameNoteIds.has(span.target), `${entry.id}: dead note link`);
      }
    }
    nextOffset += frame.len;
    frames++;
  }
  assert.equal(nextOffset, bytes.length, entry.id);
  assert.equal(nextOrdinal, entry.pkg.blocks, entry.id);
  assert.equal(uncompressed, entry.pkg.uncompressed, entry.id);
  assert.equal(noteIds.size, expectedNotes, `${entry.id}: dropped footnotes`);
  blocks += nextOrdinal;
  for (const audio of entry.audio ?? []) {
    if (audioSeen.has(audio.path)) continue;
    audioSeen.add(audio.path);
    const hash = createHash("sha256");
    let size = 0;
    const file = path.resolve(root, audio.path);
    assert(file.startsWith(root + path.sep));
    for await (const chunk of createReadStream(file)) { hash.update(chunk); size += chunk.length; }
    assert.equal(size, audio.size, audio.id);
    assert.equal(hash.digest("hex"), audio.hash, audio.id);
    recordings++;
  }
}
for (const person of catalog.persons ?? []) assert(person.works.every(id => identities.has(id)), person.id);
console.log(`verified ${catalog.entries.length} texts, ${frames} frames, ${blocks} blocks, ${recordings} recordings, ${catalog.persons?.length ?? 0} people · ${payload.generationId}`);
console.log(`page census: ${adjacentDividers} adjacent dividers, ${leadingDividers} leading dividers`);
