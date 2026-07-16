// Build-time cross-entity validation. Run via: pnpm validate:content
// Called automatically before astro build by the "build" script.

import fs from "node:fs";
import path from "node:path";
import { loadContentFromDisk } from "../src/lib/load.js";
import { validate, formatErrors } from "../src/lib/validate.js";

// src/content/book/ is listed in the CMS, which bulk-downloads every file in
// it through GitHub's API on /admin load — big files there broke /admin with
// 502/504 for everyone. Whole-book imports belong in src/content/book-lg/
// (same collection, same ids, not CMS-loaded). Hard limit so it can't regress.
const BOOK_CMS_LIMIT = 100 * 1024;
function oversizedCmsBooks(): string[] {
  const dir = path.resolve("src/content/book");
  return fs.readdirSync(dir)
    .filter((f) => f.endsWith(".md") && fs.statSync(path.join(dir, f)).size >= BOOK_CMS_LIMIT)
    .map((f) => `src/content/book/${f} is ≥100KB — move it to src/content/book-lg/ (CMS must not load it)`);
}

function main() {
  const entries = loadContentFromDisk();
  const errors = validate(entries);
  const oversized = oversizedCmsBooks();
  if (oversized.length) {
    console.error(`✗ ${oversized.length} oversized file(s) in the CMS book folder:\n${oversized.join("\n")}`);
    process.exit(1);
  }

  if (errors.length === 0) {
    console.log(`✓ content validation passed (${entries.length} entries)`);
    process.exit(0);
  } else {
    console.error(`✗ content validation failed — ${errors.length} error(s):\n`);
    console.error(formatErrors(errors));
    process.exit(1);
  }
}

main();
