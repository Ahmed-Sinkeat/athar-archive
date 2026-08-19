import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { gzipSync } from "node:zlib";

const bookId = "al-msnd-al-mwdway-al-jama-llktb-al-ashra";
const source = new URL(`../src/content/book-lg/${bookId}.md`, import.meta.url);
const outputDir = new URL("../android/m0/src/main/assets/m0/", import.meta.url);
const packagePath = new URL("r3-big.athar", outputDir);
const indexPath = new URL("r3-big.athar.idx", outputDir);
const chosenFrameSize = 2_000;
const candidates = [1_000, 2_000, 4_000];

const markdown = await readFile(source, "utf8");
const body = markdown.replace(/^---\n[\s\S]*?\n---\n/, "");
const blocks = body
  .split(/\n\s*\n+/)
  .map((text) => text.trim())
  .filter(Boolean);

const blockLines = blocks.map((text, ordinal) => {
  const type = /^#{2,4}\s/.test(text) ? "h" : "p";
  const id = createHash("sha256").update(`${bookId}\0${ordinal}`).digest("hex").slice(0, 32);
  return Buffer.from(`${JSON.stringify({ t: type, i: ordinal, x: text, id })}\n`, "utf8");
});
const header = Buffer.from(`${JSON.stringify({
  t: "header", schema: 2, id: bookId, v: 1, blocks: blocks.length,
})}\n`, "utf8");

function encode(frameSize: number, keepFrames: boolean) {
  let compressedBytes = 0;
  let uncompressedBytes = header.byteLength;
  const frames: Buffer[] = [];
  const index: { off: number; len: number; ord: number; n: number }[] = [];
  for (let ordinal = 0; ordinal < blockLines.length; ordinal += frameSize) {
    const lines = blockLines.slice(ordinal, ordinal + frameSize);
    const raw = Buffer.concat(ordinal === 0 ? [header, ...lines] : lines);
    const compressed = gzipSync(raw, { level: 6 });
    index.push({ off: compressedBytes, len: compressed.byteLength, ord: ordinal, n: lines.length });
    compressedBytes += compressed.byteLength;
    uncompressedBytes += lines.reduce((sum, line) => sum + line.byteLength, 0);
    if (keepFrames) frames.push(compressed);
  }
  return { compressedBytes, uncompressedBytes, frames, index };
}

for (const frameSize of candidates) {
  const result = encode(frameSize, false);
  console.log(`frame=${frameSize}\tframes=${result.index.length}\tcompressed=${result.compressedBytes}`);
}

const chosen = encode(chosenFrameSize, true);
const packageBuffer = Buffer.concat(chosen.frames);
const index = {
  schema: 2,
  bookId,
  v: 1,
  rawMarkdown: Buffer.byteLength(markdown),
  uncompressedRecords: chosen.uncompressedBytes,
  packageBytes: packageBuffer.byteLength,
  blocks: blocks.length,
  frameSize: chosenFrameSize,
  frames: chosen.index,
};

await mkdir(outputDir, { recursive: true });
await writeFile(packagePath, packageBuffer);
await writeFile(indexPath, `${JSON.stringify(index)}\n`, "utf8");

console.log(`chosen=${chosenFrameSize}\tblocks=${blocks.length}\tframes=${chosen.index.length}`);
console.log(`raw-markdown=${index.rawMarkdown}\trecord-stream=${index.uncompressedRecords}\tpackage=${index.packageBytes}`);
console.log(`record-overhead=${(index.uncompressedRecords / index.rawMarkdown).toFixed(3)}x`);
console.log(`package-vs-raw=${(index.packageBytes / index.rawMarkdown).toFixed(3)}x`);
