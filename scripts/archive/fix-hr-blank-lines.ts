// Athar-Engine's compiler sometimes emits a page-sep <hr> right after a
// paragraph's last line with only a single "\n" (no blank line) — most often
// when that paragraph is a multi-line footnote block. remark/rehype-raw's
// paragraph-vs-raw-HTML boundary logic silently drops an <hr> in that
// position instead of erroring, taking the page marker AND (for caret/EPUB
// footnotes anchored to it) that page's whole footer with it. Real, measured
// impact on كتاب أخصر المختصرات: 25 page-seps in the source, only 10 survived
// to rendered HTML before this fix — see caretify-bare-footnotes.ts's commit
// for how this was found.
//
// Fix: ensure a blank line always precedes (and follows) a page-sep <hr> —
// always safe, markdown-wise this only clarifies block boundaries.
//
//   pnpm exec tsx scripts/fix-hr-blank-lines.ts <file.md> [file2.md ...]
//   pnpm exec tsx scripts/fix-hr-blank-lines.ts --scan   # report-only over src/content/book{,-lg}
import fs from "node:fs";
import path from "node:path";

const HR_RE = /<hr class="page-sep"[^>]*\/?>/g;

function fixBlankLines(content: string): { content: string; count: number } {
  let count = 0;
  // insert a blank line before the hr if not already preceded by one
  let out = content.replace(/([^\n])\n(<hr class="page-sep")/g, (_m, prev, hr) => {
    count++;
    return `${prev}\n\n${hr}`;
  });
  // and after, if not already followed by one
  out = out.replace(/(<hr class="page-sep"[^>]*\/?>)\n([^\n])/g, (_m, hr, next) => {
    count++;
    return `${hr}\n\n${next}`;
  });
  return { content: out, count };
}

function processFile(file: string, dry: boolean): number {
  const raw = fs.readFileSync(file, "utf-8");
  const { content, count } = fixBlankLines(raw);
  if (count > 0 && !dry) fs.writeFileSync(file, content);
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
      console.log(`${scan ? "[would fix]" : "[fixed]"} ${f}: ${n} insertion(s)`);
      totalFixed += n;
      filesFixed++;
    }
  }
  console.log(`\n${filesFixed} file(s), ${totalFixed} insertion(s) total`);
}

main();
