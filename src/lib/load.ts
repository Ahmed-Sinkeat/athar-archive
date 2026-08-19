// Disk loader for content — used by build scripts and tests.
// (Astro page runtime uses getCollection instead; see toContentEntries adapter.)

import fs from "node:fs";
import path from "node:path";
import { StringDecoder } from "node:string_decoder";
import matter from "gray-matter";
import { COLLECTIONS, type ContentEntry } from "./types.js";

// book spans two folders: book/ (small, hand-editable, listed in the CMS) and
// book-lg/ (≥100KB imported texts the CMS must never load) — same collection,
// same ids. The split originally kept /admin (deleted 2026-08-11) from pulling
// 143MB through GitHub's API; it is now vestigial but harmless — leave it, both
// dirs are one collection and moving 955 files would churn every book's history
const EXTRA_DIRS: Record<string, string[]> = { book: ["book-lg"] };
const FRONTMATTER_CHUNK_BYTES = 16 * 1024;
const MAX_FRONTMATTER_BYTES = 1024 * 1024;

// Metadata-only callers must not read a multi-megabyte book merely to reach
// frontmatter that always ends near the beginning of the file. Reading whole
// bodies here previously made validate:content retain/scan 3.2 GB before every
// build. StringDecoder keeps a UTF-8 character split across chunks intact.
export function readFrontmatterData(filePath: string): Record<string, unknown> {
  const descriptor = fs.openSync(filePath, "r");
  const decoder = new StringDecoder("utf8");
  const buffer = Buffer.allocUnsafe(FRONTMATTER_CHUNK_BYTES);
  let text = "";
  let total = 0;
  try {
    while (total < MAX_FRONTMATTER_BYTES) {
      const read = fs.readSync(descriptor, buffer, 0, buffer.byteLength, null);
      if (read === 0) {
        text += decoder.end();
        break;
      }
      total += read;
      text += decoder.write(buffer.subarray(0, read));
      if (total === read && !text.startsWith("---\n") && !text.startsWith("---\r\n")) return {};
      const frontmatter = text.match(/^---\r?\n[\s\S]*?\r?\n---(?:\r?\n|$)/)?.[0];
      if (frontmatter) return matter(frontmatter).data as Record<string, unknown>;
    }
  } finally {
    fs.closeSync(descriptor);
  }
  if (total >= MAX_FRONTMATTER_BYTES) throw new Error(`${filePath}: frontmatter exceeds 1 MiB or has no closing delimiter`);
  return matter(text).data as Record<string, unknown>;
}

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
          const data = readFrontmatterData(filePath);
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
