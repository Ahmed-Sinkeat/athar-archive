// Tiny side table for search sort-by-death-year + result citation lines.
// Deliberately separate from the docs FTS5 table (scripts/gen-search-index.ts):
// FTS5 virtual tables can't ALTER TABLE ADD COLUMN, so adding death_year there
// would force a full ~22k-doc reindex. This is ~100 rows, always full rebuild,
// joined against docs.person at query time (see src/pages/api/search.ts).
import fs from "node:fs";
import path from "node:path";
import { loadContentFromDisk } from "../src/lib/load.js";

function parseDeathYear(died: unknown): number | null {
  const m = typeof died === "string" ? died.match(/\d+/) : null;
  return m ? parseInt(m[0], 10) : null;
}

function main() {
  const all = loadContentFromDisk();
  const people = all.filter((e) => e.collection === "person" && e.data.status === "published");
  const q = (s: string) => `'${s.replace(/'/g, "''")}'`;
  const rows = people.map((p) => {
    const year = parseDeathYear(p.data.died);
    return `(${q(p.id)},${q(String(p.data.title ?? ""))},${year ?? "NULL"})`;
  });
  // book → topic title, for the search's section filter (the app sends a section's topic titles).
  const topicTitle = new Map(all.filter((e) => e.collection === "topic").map((t) => [t.id, String(t.data.title ?? "")]));
  const workTopics = all
    .filter((e) => e.collection === "book" && e.data.status === "published" && Array.isArray(e.data.topics))
    .flatMap((b) => (b.data.topics as unknown[]).map((t) => topicTitle.get(String(t))).filter((t): t is string => !!t).map((t) => `(${q(b.id)},${q(t)})`));
  const sql =
    "DROP TABLE IF EXISTS person_meta;\n" +
    "CREATE TABLE person_meta (slug TEXT PRIMARY KEY, name TEXT, death_year INTEGER);\n" +
    (rows.length ? `INSERT INTO person_meta (slug,name,death_year) VALUES ${rows.join(",\n")};\n` : "") +
    "DROP TABLE IF EXISTS work_topic;\n" +
    "CREATE TABLE work_topic (book TEXT NOT NULL, topic TEXT NOT NULL, PRIMARY KEY (topic, book));\n" +
    (workTopics.length ? `INSERT OR IGNORE INTO work_topic (book,topic) VALUES ${workTopics.join(",\n")};\n` : "");

  const outDir = path.resolve("dist/search-index");
  fs.mkdirSync(outDir, { recursive: true });
  fs.writeFileSync(path.join(outDir, "person-meta.sql"), sql, "utf-8");
  console.log(`search meta: ${rows.length} person(s), ${workTopics.length} book topic(s) → person-meta.sql`);
}
main();
