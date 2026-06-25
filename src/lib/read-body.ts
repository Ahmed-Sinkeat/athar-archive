import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import matter from "gray-matter";

export async function readBody(entry: { filePath?: string; body?: string }): Promise<string> {
  if (entry.body !== undefined && entry.body !== "") return entry.body;
  if (!entry.filePath) return "";
  const raw = await readFile(resolve(entry.filePath), "utf-8");
  return matter(raw).content;
}
