import { gzipSync } from "node:zlib";
import { mkdir, readdir, readFile, writeFile } from "node:fs/promises";
import { basename } from "node:path";

const contentDir = new URL("../src/content/book/", import.meta.url);
const output = new URL("../android/m0/src/main/assets/m0/fts-20.ndjson.gzipdata", import.meta.url);
const booksWanted = 20;
const blocksPerBook = 300;

const files = (await readdir(contentDir, { withFileTypes: true }))
  .filter((entry) => entry.isFile() && entry.name.endsWith(".md"))
  .map((entry) => entry.name)
  .sort();

const lines: string[] = [];
const selected: { id: string; blocks: number }[] = [];
for (const file of files) {
  const markdown = await readFile(new URL(file, contentDir), "utf8");
  const body = markdown.replace(/^---\n[\s\S]*?\n---\n/, "");
  const blocks = body
    .split(/\n\s*\n+/)
    .map((value) => value.trim())
    .filter((value) => value.length >= 20 && !/^<hr\b/i.test(value));
  if (blocks.length < 200) continue;

  const id = basename(file, ".md");
  const sample = blocks.slice(0, blocksPerBook);
  sample.forEach((text, ordinal) => lines.push(JSON.stringify({ book: id, ordinal, text })));
  selected.push({ id, blocks: sample.length });
  if (selected.length === booksWanted) break;
}

if (selected.length !== booksWanted) {
  throw new Error(`wanted ${booksWanted} books, found ${selected.length}`);
}

const raw = `${lines.join("\n")}\n`;
await mkdir(new URL(".", output), { recursive: true });
await writeFile(output, gzipSync(raw, { level: 6 }));

console.log(`R2b sample: ${selected.length} real books, ${lines.length} blocks`);
console.log(`raw NDJSON: ${Buffer.byteLength(raw)} bytes`);
console.log(`gzip: ${gzipSync(raw, { level: 6 }).byteLength} bytes`);
for (const book of selected) console.log(`${book.id}\t${book.blocks}`);
