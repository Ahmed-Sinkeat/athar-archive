import { readFileSync } from "node:fs";
import { parseReadableDocument } from "/home/sinkeat/Projects/athar-archive/src/lib/app-v2/parser.ts";
const f = "src/content/book-lg/tfsyr-mqatl-bn-slyman.md";
const d = parseReadableDocument({ coll: "book", id: "c", body: readFileSync(process.argv[2] ?? f, "utf8").replace(/^---\n[\s\S]*?\n---\n/, ""), sourcePath: f });
const ids = new Set(d.footnotes.map((n: any) => n.id));
for (const b of d.blocks as any[]) for (const s of b.sp ?? []) if (s.k === "sup" && !(s.target && ids.has(s.target))) console.log(JSON.stringify(s).slice(0, 80), "|", (b.x ?? "").slice(Math.max(0, s.s - 50), s.s + 10));
