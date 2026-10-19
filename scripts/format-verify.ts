// Phase 2 formatting gate (docs/book-formatting-rules.md): every changed book must be a markup-only
// edit of its committed version, and its footnote refs must all open a note.
//   tsx scripts/format-verify.ts [--import-fix] src/content/book/<id>.md [...]   (compares to git HEAD)
// --import-fix: page numbers may change and text may move, never change (see verifyFormatting).
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { verifyFormatting } from "../src/lib/format-verify.js";
import { parseReadableDocument } from "../src/lib/app-v2/parser.js";

function footnoteReport(file: string, source: string): string {
  const body = source.replace(/^---\n[\s\S]*?\n---\n/, "");
  const doc = parseReadableDocument({ coll: "book", id: "check", body, sourcePath: file });
  const notes = new Set(doc.footnotes.map((note) => note.id));
  const used = new Set<string>();
  let linked = 0;
  let dead = 0;
  for (const block of doc.blocks) for (const span of block.sp ?? []) {
    if (span.k !== "sup") continue;
    if (span.target && notes.has(span.target)) { linked++; used.add(span.target); } else dead++;
  }
  return `${doc.blocks.length} blocks · ${notes.size} notes · ${linked} linked refs · ${dead} dead sups · ${notes.size - used.size} notes without a ref`;
}

let failed = 0;
const importFix = process.argv.includes("--import-fix");
const pageList = (source: string) => [...source.matchAll(/data-page="([^"]*)"/g)].map((match) => match[1]);
for (const file of process.argv.slice(2).filter((arg) => arg !== "--import-fix")) {
  const before = execFileSync("git", ["show", `HEAD:${file}`], { encoding: "utf8", maxBuffer: 1 << 30 });
  const after = readFileSync(file, "utf8");
  const problem = verifyFormatting(before, after, importFix);
  console.log(`${problem ? "REJECT" : "ok    "} ${file}${problem ? `: ${problem}` : ""}`);
  if (problem) failed++;
  else console.log(`       before: ${footnoteReport(file, before)}\n       after:  ${footnoteReport(file, after)}`);
  if (!problem && importFix) {
    const [old, now] = [pageList(before), pageList(after)];
    if (old.join() !== now.join()) console.log(`       pages before: ${old.join(" ")}\n       pages after:  ${now.join(" ")}`);
  }
}
process.exit(failed ? 1 : 0);
