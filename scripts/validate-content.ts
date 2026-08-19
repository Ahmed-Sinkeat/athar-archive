// Build-time cross-entity validation. Run via: pnpm validate:content
// Called automatically before astro build by the "build" script.

import fs from "node:fs";
import path from "node:path";
import { loadContentMetaFromDisk } from "../src/lib/load.js";
import { readBody } from "../src/lib/read-body.js";
import { validate, formatErrors } from "../src/lib/validate.js";

// Originally: src/content/book/ was the folder the Sveltia CMS listed, and it
// bulk-downloaded every file there through GitHub's API on /admin load, so big
// files 502/504'd the panel for everyone. The CMS was deleted 2026-08-11, so
// that reason is gone — but the split itself is still load-bearing (src/lib/load.ts
// reads both dirs as one collection) and unwinding it would rewrite the history of
// 955 files for no gain. Keep the limit: it costs nothing and keeps the two folders
// meaning what every doc says they mean.
const BOOK_CMS_LIMIT = 100 * 1024;
function oversizedCmsBooks(): string[] {
  const dir = path.resolve("src/content/book");
  return fs.readdirSync(dir)
    .filter((f) => f.endsWith(".md") && fs.statSync(path.join(dir, f)).size >= BOOK_CMS_LIMIT)
    .map((f) => `src/content/book/${f} is ≥100KB — move it to src/content/book-lg/ (CMS must not load it)`);
}

async function main() {
  // Cross-entity validation is frontmatter-only except for annotation anchors.
  // Loading every book body held the 3.2 GB large-book corpus in one array and
  // made this mandatory pre-build step OOM. Load metadata for the corpus, then
  // hydrate only the distinct annotation targets whose anchors are inspected.
  const entries = loadContentMetaFromDisk();
  const byKey = new Map(entries.map((entry) => [`${entry.collection}/${entry.id}`, entry]));
  const annotationTargets = new Set(
    entries
      .filter((entry) => entry.collection === "annotation")
      .map((entry) => `${String(entry.data.target_type ?? "")}/${String(entry.data.target_id ?? "")}`),
  );
  await Promise.all([...annotationTargets].map(async (key) => {
    const target = byKey.get(key);
    if (target) target.body = await readBody(target);
  }));
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

await main();
