// Build-time cross-entity validation. Run via: pnpm validate:content
// Called automatically before astro build by the "build" script.

import fs from "node:fs";
import path from "node:path";
import matter from "gray-matter";
import { validate, formatErrors, type ContentEntry } from "../src/lib/validate.js";

const CONTENT_ROOT = path.resolve("src/content");

const COLLECTIONS = [
  "person", "subject", "topic",
  "book", "poem", "series", "lesson",
  "question", "benefit", "article",
  "audio", "annotation", "announcement",
];

function readCollection(collection: string): ContentEntry[] {
  const dir = path.join(CONTENT_ROOT, collection);
  if (!fs.existsSync(dir)) return [];

  return fs
    .readdirSync(dir)
    .filter((f) => f.endsWith(".md"))
    .map((file) => {
      const raw = fs.readFileSync(path.join(dir, file), "utf-8");
      const { data, content } = matter(raw);
      const id = file.replace(/\.md$/, "");
      return { id, collection, data: data as Record<string, unknown>, body: content };
    });
}

function main() {
  const entries: ContentEntry[] = COLLECTIONS.flatMap(readCollection);
  const errors = validate(entries);

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
