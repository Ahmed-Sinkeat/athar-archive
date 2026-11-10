import { readFileSync, existsSync, unlinkSync, readdirSync } from "node:fs";
import { join } from "node:path";

const CONTENT_ROOT = "src/content";
const COLLECTIONS = ["book", "poem"];

function main() {
  let deletedCount = 0;
  let skippedCount = 0;

  for (const collection of COLLECTIONS) {
    const dir = join(CONTENT_ROOT, collection);
    if (!existsSync(dir)) continue;

    const files = readdirSync(dir);
    for (const file of files) {
      if (!file.includes("--v")) continue;

      const filePath = join(dir, file);
      // Resolve base filename by removing --v[0-9]+
      const baseFile = file.replace(/--v\d+\.md$/, ".md");
      const baseFilePath = join(dir, baseFile);

      if (existsSync(baseFilePath)) {
        const content = readFileSync(filePath, "utf-8");
        const baseContent = readFileSync(baseFilePath, "utf-8");

        if (content === baseContent) {
          unlinkSync(filePath);
          console.log(`Deleted identical duplicate: ${collection}/${file}`);
          deletedCount++;
        } else {
          console.log(`Kept different version: ${collection}/${file}`);
          skippedCount++;
        }
      } else {
        console.log(`No base file found for: ${collection}/${file}`);
        skippedCount++;
      }
    }
  }

  console.log(`\nCleanup complete: Deleted ${deletedCount} identical files, skipped ${skippedCount} files.`);
}

main();
