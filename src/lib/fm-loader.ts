// ponytail: stores frontmatter + filePath only — body is NOT in the data store.
// Cuts the store from ~3 GB to ~10 MB for large content collections.
// Pages call readBody(entry) to load body from disk on demand.
import type { Loader } from "astro/loaders";
import { readdir, readFile } from "node:fs/promises";
import { join, resolve, relative } from "node:path";
import matter from "gray-matter";

export function fmLoader(base: string): Loader {
  const absBase = resolve(base);
  return {
    name: "fm-loader",
    load: async ({ store, parseData, generateDigest, logger }) => {
      let files: string[];
      try {
        files = (await readdir(absBase, { recursive: true }) as string[])
          .filter((f) => f.endsWith(".md"))
          .map((f) => f.replace(/\\/g, "/"));
      } catch {
        logger.warn(`fm-loader: directory not found — ${absBase}`);
        files = [];
      }

      const fileSet = new Set(files.map((f) => f.replace(/\.md$/, "")));
      for (const [id] of store.entries()) {
        if (!fileSet.has(id)) store.delete(id);
      }

      for (const file of files) {
        const filePath = relative(process.cwd(), join(absBase, file));
        const raw = await readFile(filePath, "utf-8");
        const { data } = matter(raw);
        const id = file.replace(/\.md$/, "");
        const digest = generateDigest(JSON.stringify(data));
        if (store.has(id) && store.get(id)?.digest === digest) continue;
        store.set({ id, data: await parseData({ id, data }), filePath, digest });
      }
    },
  };
}
