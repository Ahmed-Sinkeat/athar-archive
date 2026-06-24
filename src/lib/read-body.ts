import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import matter from "gray-matter";

export async function readBody(entry: { filePath?: string }): Promise<string> {
  if (!entry.filePath) return "";
  const raw = await readFile(resolve(entry.filePath), "utf-8");
  return matter(raw).content;
}
