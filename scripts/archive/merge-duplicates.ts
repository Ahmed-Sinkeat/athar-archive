import { readFileSync, writeFileSync, existsSync, unlinkSync, readdirSync } from "node:fs";
import { join } from "node:path";

const CONTENT_ROOT = "src/content";
const COLLECTIONS = ["book", "poem"];

function splitFmAndBody(content: string) {
  const match = content.match(/^---\r?\n([\s\S]*?)\r?\n---([\s\S]*)$/);
  if (!match) return { fm: "", body: content };
  return { fm: match[1], body: match[2] };
}

function main() {
  let mergedCount = 0;
  let skippedCount = 0;

  for (const collection of COLLECTIONS) {
    const dir = join(CONTENT_ROOT, collection);
    if (!existsSync(dir)) continue;

    const files = readdirSync(dir);
    // Sort files numerically so we process base first, then v2, v3, v4, etc.
    files.sort((a, b) => a.localeCompare(b, undefined, { numeric: true, sensitivity: 'base' }));

    for (const file of files) {
      if (!file.includes("--v")) continue;

      const filePath = join(dir, file);
      if (!existsSync(filePath)) continue; // might have been deleted in a prior iteration

      // Resolve base prefix (e.g. "shrh-al-qwaad-al-arba")
      const basePrefix = file.replace(/--v\d+\.md$/, "");
      
      // Find all potential target files of the same book/poem that already exist
      // e.g. basePrefix.md, basePrefix--v2.md, basePrefix--v3.md, etc.
      // We only compare against files that are "earlier" in version (or base)
      const content = readFileSync(filePath, "utf-8");
      const parsed = splitFmAndBody(content);

      let merged = false;

      // Find candidates to compare with
      const candidates = readdirSync(dir)
        .filter((f) => {
          if (f === file) return false;
          // Must start with basePrefix
          if (!f.startsWith(basePrefix)) return false;
          // Must match the base filename or an earlier version filename
          const fBase = f.replace(/--v\d+\.md$/, "");
          if (fBase !== basePrefix) return false;
          
          // Verify it's an earlier candidate
          // base file is always earlier. Otherwise compare version numbers.
          if (!f.includes("--v")) return true;
          
          const matchVThis = file.match(/--v(\d+)\.md$/);
          const matchVCand = f.match(/--v(\d+)\.md$/);
          if (matchVThis && matchVCand) {
            return parseInt(matchVCand[1], 10) < parseInt(matchVThis[1], 10);
          }
          return false;
        })
        // Sort candidates so we prefer base first, then v2, etc.
        .sort((a, b) => a.localeCompare(b, undefined, { numeric: true, sensitivity: 'base' }));

      for (const cand of candidates) {
        const candPath = join(dir, cand);
        if (!existsSync(candPath)) continue;

        const candContent = readFileSync(candPath, "utf-8");
        const candParsed = splitFmAndBody(candContent);

        if (parsed.body.trim() === candParsed.body.trim()) {
          // Overwrite the candidate with the latest metadata of the duplicate
          writeFileSync(candPath, content, "utf-8");
          unlinkSync(filePath);
          console.log(`Merged and overwritten: ${collection}/${cand} with latest metadata from ${file}`);
          merged = true;
          mergedCount++;
          break;
        }
      }

      if (!merged) {
        console.log(`No match found (kept separate edition): ${collection}/${file}`);
        skippedCount++;
      }
    }
  }

  console.log(`\nMerge complete: Merged and deleted ${mergedCount} versioned files, skipped ${skippedCount} files.`);
}

main();
