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

// Same enumeration, but frontmatter only — body stays on disk (filePath set,
// body left ""), for readBody() (see read-body.ts) to load per entry. The
// `book` collection alone is 3.2GB+ of text; loadContentFromDisk() reading
// every body into one array up front pinned that whole corpus in memory
// simultaneously with whatever a caller derives from it (the gen-search-index
// CI OOM of 2026-07-30, same "hold two copies of the corpus" shape as the
// Astro getStaticPaths props fix). Callers that only need a handful of small
// collections' bodies (or none at all) can keep using loadContentFromDisk;
// this is for the ones that walk the full corpus one entry at a time.
export function loadContentMetaFromDisk(root = "src/content"): ContentEntry[] {
  const contentRoot = path.resolve(root);
  return COLLECTIONS.flatMap((collection) => {
    const dirs = [collection, ...(EXTRA_DIRS[collection] ?? [])].map((d) => path.join(contentRoot, d));
    return dirs.flatMap((dir) => {
      if (!fs.existsSync(dir)) return [];
      return fs
        .readdirSync(dir)
        .filter((f) => f.endsWith(".md"))
        .map((file) => {
          const filePath = path.join(dir, file);
          const { data } = matter(fs.readFileSync(filePath, "utf-8"));
          return {
            id: file.replace(/\.md$/, ""),
            collection,
            data: data as Record<string, unknown>,
            body: "",
            filePath,
          };
        });
    });
  });
}
