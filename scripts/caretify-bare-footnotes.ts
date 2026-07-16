// Some imports (see docs/import-epub-guide.md) left footnotes as literal
// "(N) note text." lines instead of the "(^N)" caret convention that
// chapters.ts's extractCaretNotesByPage()/page-footnotes.ts already knows how
// to pull into a real per-page footer box. Symptom: the marker renders as
// plain (colored) text mid-sentence, and the note body sits as an ordinary
// paragraph right before the page-sep — never counted as a footnote at all.
//
// Heuristic: a page-sep-terminated chunk whose LAST paragraph(s) contain a
// contiguous run of "(N) text" lines with N starting at 1 (or 2) and
// increasing by exactly 1 is almost certainly that page's footnote block
// (the printed convention: notes stacked at the page bottom, renumbered from
// 1 on every page — sometimes each on its own paragraph, sometimes several
// packed into one paragraph with plain single newlines between them, both
// handled here). Caret-ify each definition line and the last matching bare
// "(N)" occurring earlier in that same chunk (same-page reference — the
// overwhelmingly common case).
//
//   pnpm exec tsx scripts/caretify-bare-footnotes.ts <file.md> [file2.md ...]
//   pnpm exec tsx scripts/caretify-bare-footnotes.ts --scan   # report-only over src/content/book{,-lg}
import fs from "node:fs";
import path from "node:path";

const PAGE_SEP_SPLIT_RE = /(<hr class="page-sep" data-page="\d+"[^>]*\/>)/;
const DIGIT_MAP: Record<string, string> = { "٠": "0", "١": "1", "٢": "2", "٣": "3", "٤": "4", "٥": "5", "٦": "6", "٧": "7", "٨": "8", "٩": "9" };
const toInt = (s: string) => parseInt([...s].map((c) => DIGIT_MAP[c] ?? c).join(""), 10);
const DEF_RE = /^\(([٠-٩0-9]{1,2})\)\s+\S/;

function caretifyBody(body: string): { body: string; count: number } {
  const parts = body.split(PAGE_SEP_SPLIT_RE);
  let count = 0;
  for (let i = 0; i < parts.length; i++) {
    if (parts[i].startsWith("<hr")) continue;
    const paras = parts[i].split(/\n\n+/);

    // Candidate trailing paragraphs: consecutive from the end whose first line looks like a def.
    let runStart = paras.length;
    for (let j = paras.length - 1; j >= 0; j--) {
      if (!DEF_RE.test(paras[j].split("\n")[0].trim())) break;
      runStart = j;
    }
    if (runStart === paras.length) continue;
    const runParas = paras.slice(runStart);

    // Flatten into individual defs — a paragraph may itself contain several
    // "(N) …" lines back-to-back with no blank line between them.
    const defs: { id: string }[] = [];
    for (const par of runParas) {
      for (const line of par.split("\n")) {
        const m = line.trim().match(DEF_RE);
        if (m) defs.push({ id: m[1] });
      }
    }
    if (defs.length < 2) continue;
    const nums = defs.map((d) => toInt(d.id));
    const sequential = nums.every((n, idx) => (idx === 0 ? n === 1 || n === 2 : n === nums[idx - 1] + 1));
    if (!sequential) continue;

    const newRunParas = runParas.map((par) =>
      par
        .split("\n")
        .map((line) => {
          const m = line.match(/^(\(([٠-٩0-9]{1,2})\))\s+\S/);
          return m ? line.replace(`(${m[2]})`, `(^${m[2]})`) : line;
        })
        .join("\n")
    );

    let bodyBefore = paras.slice(0, runStart).join("\n\n");
    for (const { id } of defs) {
      const re = new RegExp(`\\(${id}\\)`, "g");
      let lastIndex = -1, m: RegExpExecArray | null;
      while ((m = re.exec(bodyBefore))) lastIndex = m.index;
      if (lastIndex >= 0) bodyBefore = bodyBefore.slice(0, lastIndex) + `(^${id})` + bodyBefore.slice(lastIndex + id.length + 2);
    }

    count += defs.length;
    parts[i] = (bodyBefore ? bodyBefore + "\n\n" : "") + newRunParas.join("\n\n");
  }
  return { body: parts.join(""), count };
}

function splitFrontmatter(s: string) {
  const m = s.match(/^---\n[\s\S]*?\n---\n?/);
  return m ? { fm: m[0], body: s.slice(m[0].length) } : { fm: "", body: s };
}

function processFile(file: string, dry: boolean): number {
  const raw = fs.readFileSync(file, "utf-8");
  const { fm, body } = splitFrontmatter(raw);
  const { body: newBody, count } = caretifyBody(body);
  if (count > 0 && !dry) fs.writeFileSync(file, fm + newBody);
  return count;
}

function main() {
  const args = process.argv.slice(2);
  const scan = args.includes("--scan");
  const files = scan
    ? ["src/content/book", "src/content/book-lg"].flatMap((dir) =>
        fs.readdirSync(dir).filter((f) => f.endsWith(".md")).map((f) => path.join(dir, f))
      )
    : args.filter((a) => a.endsWith(".md"));

  let totalFixed = 0, filesFixed = 0;
  for (const f of files) {
    const n = processFile(f, scan);
    if (n > 0) {
      console.log(`${scan ? "[would fix]" : "[fixed]"} ${f}: ${n} footnote(s)`);
      totalFixed += n;
      filesFixed++;
    }
  }
  console.log(`\n${filesFixed} file(s), ${totalFixed} footnote(s) total`);
}

main();
