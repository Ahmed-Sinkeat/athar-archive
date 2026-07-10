// Shared content shape used across validate / graph / chapters / chunk.
// Mirrors what gray-matter (build scripts, tests) and Astro's getCollection
// (page runtime) both produce, normalized to one structure.

export interface ContentEntry {
  id: string;
  collection: string;
  data: Record<string, unknown>;
  body: string;
  filePath?: string;
}

export const COLLECTIONS = [
  "person",
  "subject",
  "topic",
  "book",
  "poem",
  "question",
  "article",
  "audio",
  "annotation",
  "announcement",
  "highlight",
  "term",
  "quran",
] as const;

export type CollectionName = (typeof COLLECTIONS)[number];

// Collections whose entries are "materials": standalone, topic-bearing,
// and surfaced on topic/subject/person listing pages.
export const MATERIAL_COLLECTIONS = [
  "book",
  "poem",
  "article",
  "question",
] as const;

export function isPublished(entry: { data: Record<string, unknown> }): boolean {
  return String(entry.data.status ?? "draft") === "published";
}
