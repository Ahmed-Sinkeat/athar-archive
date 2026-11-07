// Before/after of تفسير مقاتل in the app parser: blocks, shown characters, headings, notes, and that the
// hidden surah text no longer appears while the tafsir after it still does.
import { readFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { parseReadableDocument } from "/home/sinkeat/Projects/athar-archive/src/lib/app-v2/parser.ts";
const f = "src/content/book-lg/tfsyr-mqatl-bn-slyman.md";
const run = (src: string) => {
  const d = parseReadableDocument({ coll: "book", id: "c", body: src.replace(/^---\n[\s\S]*?\n---\n/, ""), sourcePath: f });
  const text = (d.blocks as any[]).map((b) => b.x ?? "").join("\n");
  return { blocks: d.blocks.length, chars: text.length, heads: (d.blocks as any[]).filter((b) => /^h/.test(b.t)).length, notes: d.footnotes.length, text };
};
const a = run(execFileSync("git", ["show", `HEAD:${f}`], { encoding: "utf8", maxBuffer: 1 << 30 })), b = run(readFileSync(f, "utf8"));
console.log({ blocks: [a.blocks, b.blocks], chars: [a.chars, b.chars], headings: [a.heads, b.heads], notes: [a.notes, b.notes] });
const left = new Map<string, number>(); for (const t of b.text.split("\n")) left.set(t, (left.get(t) ?? 0) + 1);
const gone = a.text.split("\n").filter((t) => { const n = left.get(t) ?? 0; if (n) { left.set(t, n - 1); return false; } return true; });
console.log("blocks gone:", gone.length, "| new blocks:", [...left.values()].reduce((x, y) => x + y, 0)); for (const t of [gone[0], gone[1], gone[60], gone.at(-1)]) console.log("  gone:", t.slice(0, 110));
