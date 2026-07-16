// Disk loader for content — used by build scripts and tests.
// (Astro page runtime uses getCollection instead; see toContentEntries adapter.)

import fs from "node:fs";
import path from "node:path";
import matter from "gray-matter";
import { COLLECTIONS, type ContentEntry } from "./types.js";

// book spans two folders: book/ (small, hand-editable, listed in the CMS) and
// book-lg/ (≥100KB imported texts the CMS must never load) — same collection,
// same ids, split only so /admin doesn't pull 143MB through GitHub's API
const EXTRA_DIRS: Record<string, string[]> = { book: ["book-lg"] };

export function loadContentFromDisk(root = "src/content"): ContentEntry[] {
  const contentRoot = path.resolve(root);
  return COLLECTIONS.flatMap((collection) => {
    const dirs = [collection, ...(EXTRA_DIRS[collection] ?? [])].map((d) => path.join(contentRoot, d));
    return dirs.flatMap((dir) => {
      if (!fs.existsSync(dir)) return [];
      return fs
        .readdirSync(dir)
        .filter((f) => f.endsWith(".md"))
        .map((file) => {
          const raw = fs.readFileSync(path.join(dir, file), "utf-8");
          const { data, content } = matter(raw);
          return {
            id: file.replace(/\.md$/, ""),
            collection,
            data: data as Record<string, unknown>,
            body: content,
          };
        });
    });
  });
}
