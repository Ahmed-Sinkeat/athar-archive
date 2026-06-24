import { readFile } from "node:fs/promises";
import matter from "gray-matter";

export async function readBody(entry: { filePath?: string }): Promise<string> {
  if (!entry.filePath) return "";
  const raw = await readFile(entry.filePath, "utf-8");
  return matter(raw).content;
}
