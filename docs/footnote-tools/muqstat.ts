import { readFileSync } from "node:fs";
import { parseReadableDocument } from "/home/sinkeat/Projects/athar-archive/src/lib/app-v2/parser.ts";
const f = "src/content/book-lg/tfsyr-mqatl-bn-slyman.md";
const st = (src: string) => {
  const d = parseReadableDocument({ coll: "book", id: "c", body: src.replace(/^---\n[\s\S]*?\n---\n/, ""), sourcePath: f });
  const ids = new Set(d.footnotes.map((n: any) => n.id)); const used = new Set<string>();
  let linked = 0, dead = 0, chars = 0;
  for (const b of d.blocks as any[]) { chars += (b.x ?? "").length; for (const s of b.sp ?? []) if (s.k === "sup") { if (s.target && ids.has(s.target)) { linked++; used.add(s.target); } else dead++; } }
  for (const n of d.footnotes as any[]) chars += (n.x ?? "").length;
  return { notes: ids.size, linked, dead, unreferenced: ids.size - used.size, chars };
};
console.log("before", st(readFileSync(process.argv[2], "utf8")));
console.log("after ", st(readFileSync(f, "utf8")));
