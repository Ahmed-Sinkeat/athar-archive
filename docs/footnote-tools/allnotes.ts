import { readFileSync, readdirSync } from "node:fs";
import { parseReadableDocument } from "/home/sinkeat/Projects/athar-archive/src/lib/app-v2/parser.ts";
const rows: [string, number, number, number][] = [];
for (const dir of ["src/content/book", "src/content/book-lg"]) for (const n of readdirSync(dir)) {
  if (!n.endsWith(".md")) continue; const f = `${dir}/${n}`;
  try {
    const d = parseReadableDocument({ coll: "book", id: "c", body: readFileSync(f, "utf8").replace(/^---\n[\s\S]*?\n---\n/, ""), sourcePath: f });
    const ids = new Set(d.footnotes.map((x: any) => x.id)); const used = new Set<string>(); let dead = 0;
    for (const b of d.blocks as any[]) for (const s of b.sp ?? []) if (s.k === "sup") { if (s.target && ids.has(s.target)) used.add(s.target); else dead++; }
    rows.push([f, ids.size, dead, ids.size - used.size]); if (dead || ids.size - used.size) console.log('BAD', f, ids.size, dead, ids.size - used.size);
  } catch (e) { console.log("ERR", f, String(e).slice(0, 80)); }
}
const bad = rows.filter(r => r[2] || r[3]);
console.log("books", rows.length, "with notes", rows.filter(r => r[1]).length, "with problems", bad.length,
  "dead", bad.reduce((a, r) => a + r[2], 0), "unref", bad.reduce((a, r) => a + r[3], 0));
bad.sort((a, b) => b[2] + b[3] - a[2] - a[3]);
for (const r of bad.slice(0, 15)) console.log(r.join("\t"));
