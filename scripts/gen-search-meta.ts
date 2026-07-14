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
  const people = loadContentFromDisk().filter((e) => e.collection === "person" && e.data.status === "published");
  const q = (s: string) => `'${s.replace(/'/g, "''")}'`;
  const rows = people.map((p) => {
    const year = parseDeathYear(p.data.died);
    return `(${q(p.id)},${q(String(p.data.title ?? ""))},${year ?? "NULL"})`;
  });
  const sql =
    "DROP TABLE IF EXISTS person_meta;\n" +
    "CREATE TABLE person_meta (slug TEXT PRIMARY KEY, name TEXT, death_year INTEGER);\n" +
    (rows.length ? `INSERT INTO person_meta (slug,name,death_year) VALUES ${rows.join(",\n")};\n` : "");

  const outDir = path.resolve("dist/search-index");
  fs.mkdirSync(outDir, { recursive: true });
  fs.writeFileSync(path.join(outDir, "person-meta.sql"), sql, "utf-8");
  console.log(`search meta: ${rows.length} person(s) → person-meta.sql`);
}
main();
