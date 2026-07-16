// ponytail: stores frontmatter + filePath only — body is NOT in the data store.
// Cuts the store from ~3 GB to ~10 MB for large content collections.
// Pages call readBody(entry) to load body from disk on demand.
import type { Loader } from "astro/loaders";
import { readdir, readFile } from "node:fs/promises";
import { join, resolve, relative } from "node:path";
import matter from "gray-matter";

// Accepts several base dirs so one collection can span folders (book/ +
// book-lg/): ids stay the bare filename either way, so splitting a folder
// never changes an entry's id or URL.
export function fmLoader(base: string | string[]): Loader {
  const absBases = (Array.isArray(base) ? base : [base]).map((b) => resolve(b));
  return {
    name: "fm-loader",
    load: async ({ store, parseData, generateDigest, logger }) => {
      const found: { absBase: string; file: string }[] = [];
      for (const absBase of absBases) {
        try {
          for (const f of (await readdir(absBase, { recursive: true })) as string[]) {
            if (f.endsWith(".md")) found.push({ absBase, file: f.replace(/\\/g, "/") });
          }
        } catch {
          logger.warn(`fm-loader: directory not found — ${absBase}`);
        }
      }

      const fileSet = new Set(found.map(({ file }) => file.replace(/\.md$/, "")));
      for (const [id] of store.entries()) {
        if (!fileSet.has(id)) store.delete(id);
      }

      for (const { absBase, file } of found) {
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
