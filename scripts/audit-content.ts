// Content-quality audit (run: pnpm audit:content) — catches what schema
// validation can't: duplicate works imported twice under different slugs, and
// import damage like undivided books or headings that lost their markdown.
// Informational: prints findings, exits 1 only if any finding exists so it
// can gate imports later if wanted.
import fs from "node:fs";
import path from "node:path";
import { normalizeArabic } from "../src/lib/ar-normalize.js";

const ROOT = path.resolve("src/content");
const findings: string[] = [];

function fm(file: string, field: string): string | undefined {
  const head = fs.readFileSync(file, "utf-8").slice(0, 2000);
  return new RegExp(`^${field}:\\s*"?([^"\\n]+)"?\\s*$`, "m").exec(head)?.[1]?.trim();
}
function body(file: string): string {
  const raw = fs.readFileSync(file, "utf-8");
  const end = raw.indexOf("\n---", 3);
  return end === -1 ? "" : raw.slice(end + 4);
}

// --- 1. duplicate title+person within a collection ---
// book spans two folders (book/ + book-lg/) but is ONE collection — scan them
// together so a duplicate split across the folders is still caught
for (const [coll, dirs] of [["book", ["book", "book-lg"]], ["poem", ["poem"]], ["article", ["article"]], ["question", ["question"]]] as [string, string[]][]) {
  const byKey = new Map<string, string[]>();
  for (const dir of dirs.map((d) => path.join(ROOT, d)).filter(fs.existsSync)) {
    for (const f of fs.readdirSync(dir)) {
      if (!f.endsWith(".md")) continue;
      const p = path.join(dir, f);
      if (fm(p, "status") !== "published") continue;
      const title = fm(p, "title");
      if (!title) continue;
      // صوتيات share generic titles («تتمة الجواب») across DIFFERENT recordings —
      // distinct audio ids means distinct content, not a duplicate
      const key = `${normalizeArabic(title)}|${fm(p, "person") ?? ""}`;
      byKey.set(key, [...(byKey.get(key) ?? []), path.join(dir, f)]);
    }
  }
  for (const [key, files] of byKey) {
    if (files.length < 2) continue;
    const audios = new Set(files.map((p) => fm(p, "audio") ?? ""));
    if (audios.size === files.length && ![...audios].includes("")) continue; // all have their own recording
    findings.push(`DUPLICATE ${coll}: «${key.split("|")[0]}» → ${files.map((p) => path.basename(p)).join(", ")}`);
  }
}

// --- 2..5 book body damage ---
for (const bookDir of [path.join(ROOT, "book"), path.join(ROOT, "book-lg")].filter(fs.existsSync))
for (const f of fs.readdirSync(bookDir)) {
  if (!f.endsWith(".md")) continue;
  const p = path.join(bookDir, f);
  if (fm(p, "status") !== "published") continue;
  const lines = body(p).split("\n");
  const content = lines.filter((l) => l.trim() !== "");
  const h2 = lines.filter((l) => /^##\s+\S/.test(l)).length;
  // NOTE: a plain `# heading` in the body is a legit convention (بطاقة الكتاب,
  // الجزء الأول, …) — only the import-damage signatures below are flagged:
  // `# **…**` (h1-wrapped-bold, the «شرح أصول السنة» bug) and orphan bare `#`
  const h1Bold = lines.filter((l) => /^#\s+\*\*/.test(l)).length;
  const orphanHash = lines.filter((l) => /^#{1,6}\s*$/.test(l.trim()) && l.trim() !== "").length;
  // bold-only lines that look like headings (short, no sentence punctuation)
  const boldHead = lines.filter((l) => /^\*\*[^*]{3,80}\*\*\s*$/.test(l.trim()) && !/[.،؛:]/.test(l)).length;
  // epub-import.ts strips the Shamela `<div class="center">الجزء: N ¦ الصفحة:
  // M</div>` footer before it reaches markdown — but some sources wrap it
  // differently (no class="center", "-" separator, word juz like "المقدمة")
  // and the stripping silently no-ops, leaving the footer as a literal body
  // line. (Literal "(^N) note text" footnotes are NOT flagged here — that's
  // now a supported convention, see extractCaretNotesByPage in chapters.ts.)
  const leakedFooter = lines.filter((l) => /^الجزء:\s*\S.*[-¦]\s*الصفحة:\s*[٠-٩0-9]+\s*$/.test(l.trim())).length;
  if (content.length < 10) findings.push(`EMPTY book: ${f} (${content.length} lines)`);
  if (h2 <= 1 && content.length > 300) findings.push(`UNDIVIDED book: ${f} (${content.length} lines, ${h2} chapter headings)`);
  if (h1Bold > 0) findings.push(`H1-BOLD-HEADINGS book: ${f} (${h1Bold} «# **…**» lines — broken chapter split)`);
  if (orphanHash > 0) findings.push(`ORPHAN-HASH book: ${f} (${orphanHash} bare # lines)`);
  if (boldHead >= 5) findings.push(`BOLD-ONLY-HEADINGS book: ${f} (${boldHead} bold-only lines)`);
  if (leakedFooter > 0) findings.push(`LEAKED-PAGE-FOOTER book: ${f} (${leakedFooter} literal «الجزء: … الصفحة: N» lines — import didn't strip the source footer)`);
}

if (findings.length) {
  console.log(`${findings.length} finding(s):\n`);
  for (const x of findings.sort()) console.log("  " + x);
  process.exit(1);
}
console.log("✓ audit clean — no duplicates or damaged books");
