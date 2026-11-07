import { readFileSync } from "node:fs";
import { parseReadableDocument } from "/home/sinkeat/Projects/athar-archive/src/lib/app-v2/parser.ts";
const f = "src/content/book-lg/tfsyr-mqatl-bn-slyman.md";
const d = parseReadableDocument({ coll: "book", id: "c", body: readFileSync(f, "utf8").replace(/^---\n[\s\S]*?\n---\n/, ""), sourcePath: f });
const used = new Set<string>(); for (const b of d.blocks as any[]) for (const s of b.sp ?? []) if (s.k === "sup" && s.target) used.add(s.target);
const un = (d.footnotes as any[]).filter((n) => !used.has(n.id));
for (const n of un) console.log(n.id, "|", n.x.slice(0, 70));
