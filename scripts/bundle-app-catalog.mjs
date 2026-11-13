// Builds the Android app's catalog database from a generated app-content build, so a fresh
// install opens with the whole library and no catalog to decode or apply (that took ~6.5 s on
// a Redmi 12). Run after `npm run app:gen`, before building a release:
//   pnpm app:bundle-catalog [dist/app-content/app/v2]
// Writes android/app/src/main/assets/app-content/{index.json,catalog.db}. The app copies
// catalog.db in only when it has no content database yet (Room's createFromAsset); index.json
// is what the release build checks against the published generation.
//
// The tables come from Room's exported schema, so they are exactly what the app expects. The
// rows mirror `CatalogEntry.toEntity()` in ContentImporter.kt and the search tables mirror
// `ContentSearchSchema` in ContentSearch.kt (the normalizer is the site's, which ArNormalize.kt
// is tested against). PrebuiltCatalogTest checks rows and table definitions against the app's
// own apply on a device: run it after changing either side.
import { createHash } from "node:crypto";
import { copyFileSync, mkdirSync, readdirSync, readFileSync, rmSync, statSync } from "node:fs";
import { DatabaseSync } from "node:sqlite";
import path from "node:path";
import { normalizeArabic } from "../src/lib/ar-normalize.js";
import { readLightCatalog } from "../src/lib/app-v2/package.js";

const source = path.resolve(process.argv[2] ?? "dist/app-content/app/v2");
const target = path.resolve("android/app/src/main/assets/app-content");
const schemaDir = path.resolve("android/core/data/schemas/com.atharchive.core.data.db.content.AtharContentDatabase");

const envelope = JSON.parse(readFileSync(path.join(source, "index.json"), "utf8"));
const root = typeof envelope.payload === "string"
  ? JSON.parse(Buffer.from(envelope.payload, "base64url").toString("utf8"))
  : envelope.payload;
// The light catalog is what this app syncs (the old one leaves out the ديوان entries).
const catalog = root.light
  ? readLightCatalog(readFileSync(path.join(source, root.light.path)))
  : JSON.parse(readFileSync(path.join(source, root.catalog.path), "utf8"));

// The newest schema Room exported is the one the app builds against.
const version = Math.max(...readdirSync(schemaDir).filter((f) => /^\d+\.json$/.test(f)).map((f) => Number.parseInt(f)));
const schema = JSON.parse(readFileSync(path.join(schemaDir, `${version}.json`), "utf8")).database;

rmSync(target, { recursive: true, force: true });
mkdirSync(target, { recursive: true });
copyFileSync(path.join(source, "index.json"), path.join(target, "index.json"));
const dbPath = path.join(target, "catalog.db");
const db = new DatabaseSync(dbPath);
db.exec("PRAGMA journal_mode = DELETE");
for (const entity of schema.entities) {
  db.exec(entity.createSql.replaceAll("${TABLE_NAME}", entity.tableName));
  for (const index of entity.indices ?? []) db.exec(index.createSql.replaceAll("${TABLE_NAME}", entity.tableName));
}
for (const query of schema.setupQueries) db.exec(query);
db.exec(`PRAGMA user_version = ${schema.version}`);

// kotlinx.serialization as AppContentJson writes it: declaration order, nulls and defaults left out.
const fields = (value, keys) => Object.fromEntries(keys
  .filter(([key, empty]) => value[key] != null && !(empty && Array.isArray(value[key]) && value[key].length === 0))
  .map(([key]) => [key, value[key]]));
// Kotlin writes a Double with a fraction (1.0), JSON.stringify does not (1).
const kotlinDouble = (n) => `\u0000${Number.isInteger(n) ? n.toFixed(1) : String(n)}`;
const withDoubles = (json) => json.replace(/"\\u0000([^"]*)"/g, "$1");
const audioJson = (audio) => withDoubles(JSON.stringify(audio.map((a) => {
  const out = fields(a, [["id"], ["title"], ["path"], ["hash"], ["format"], ["seconds"], ["size"], ["reciters", true], ["cues", true]]);
  if (out.cues) out.cues = out.cues.map((c) => ({ v: c.v, t: kotlinDouble(c.t) }));
  return out;
})));
const f0Json = (f) => JSON.stringify(fields(f, [["off"], ["len"], ["ord"], ["n"], ["sha256"]]));
const cardJson = (card) => JSON.stringify(card.map((c) => fields(c, [["l"], ["v"], ["k"]])));
const personsJson = (persons) => JSON.stringify(persons.map((p) => fields(p, [["id"], ["name"], ["died"], ["bio"], ["works", true]])));

const insertEntity = db.prepare(`INSERT INTO entity (id, coll, v, hash, title, titleNorm, person, personName, died, kind,
  authoredYear, publishedAt, description, excerpt, openingVersesJson, topicsCsv, pkgPath, pkgHash, pkgSize, pkgUncompressed,
  idxPath, idxHash, idxSize, pkgBlocks, pkgChapters, audioJson, editionOf, edition, editionsJson, verseCount, pageFrom,
  pageTo, volumes, volumeUnit, f0Json, cardJson, poemsJson, catalogPresent)
  VALUES (${Array(38).fill("?").join(", ")})`);
const insertState = db.prepare("INSERT INTO entityState (id, availability, transferState, localPackageHash) VALUES (?, 'absent', 'idle', NULL)");
db.exec("BEGIN");
for (const e of catalog.entries) {
  const pkg = e.pkg;
  const pages = pkg.pages;
  insertEntity.run(
    e.id, e.coll, e.v, e.hash, e.title, normalizeArabic(e.title), e.person ?? null, e.personName ?? null, e.died ?? null,
    e.kind ?? null, e.authoredYear ?? null, e.publishedAt ?? null, e.description ?? null, e.excerpt ?? null,
    JSON.stringify(e.openingVerses ?? []), (e.topics ?? []).join(","), pkg.path, pkg.hash, pkg.size, pkg.uncompressed,
    pkg.idxPath, pkg.idxHash, pkg.idxSize, pkg.blocks, pkg.chapters, audioJson(e.audio ?? []), e.editionOf ?? null,
    e.edition ?? null, JSON.stringify(e.editions ?? []), e.verseCount ?? null, pages?.from ?? null, pages?.to ?? null,
    pages?.vols ?? null, pages?.unit ?? null, pkg.f0 ? f0Json(pkg.f0) : null, e.card?.length ? cardJson(e.card) : null,
    e.poems?.length ? JSON.stringify(e.poems.map((p) => fields(p, [["t"], ["o"]]))) : null, 1,
  );
  insertState.run(e.id);
}
// Tombstones need nothing: they only mark rows that a fresh database does not have.
db.prepare("INSERT INTO contentGeneration (singleton, generationId, catalogHash, tombstonesHash, appliedAt, personsJson) VALUES (0, ?, ?, ?, ?, ?)")
  .run(root.generationId, root.catalog.hash, root.tombstones.hash, Date.now(), personsJson(catalog.persons ?? []));
// Search tables as ContentSearchSchema creates them; the schema row tells the app not to rebuild.
db.exec("CREATE VIRTUAL TABLE IF NOT EXISTS block_fts USING fts5(norm, tokenize='unicode61', content='', contentless_delete=1, detail=full)");
db.exec("CREATE VIRTUAL TABLE IF NOT EXISTS catalog_fts USING fts5(titleNorm, personNorm, topicsNorm, id UNINDEXED, tokenize='unicode61')");
db.exec("CREATE TRIGGER IF NOT EXISTS block_fts_before_delete BEFORE DELETE ON block BEGIN DELETE FROM block_fts WHERE rowid = OLD.rowid; END");
db.exec("CREATE TABLE IF NOT EXISTS athar_search_meta(key TEXT PRIMARY KEY NOT NULL, value INTEGER NOT NULL)");
db.exec("INSERT OR REPLACE INTO athar_search_meta(key, value) VALUES ('schema', 1), ('positions', 1)");
const insertSearch = db.prepare("INSERT INTO catalog_fts(titleNorm, personNorm, topicsNorm, id) VALUES (?, ?, ?, ?)");
for (const e of catalog.entries) {
  if (e.editionOf != null) continue;
  insertSearch.run(normalizeArabic(e.title), normalizeArabic(e.personName ?? ""), normalizeArabic((e.topics ?? []).join(" ")), e.id);
}
// الشعر's lines as PoemRows.kt makes them (same DDL and rows), so a fresh install does not list ~53k poems on first open.
for (const ddl of [
  "CREATE TABLE IF NOT EXISTS poem_row(entityId TEXT NOT NULL, ordinal INTEGER, title TEXT NOT NULL, titleNorm TEXT NOT NULL, poetNorm TEXT NOT NULL, diedKey INTEGER NOT NULL, opening TEXT, verseCount INTEGER, openingNorm TEXT)",
  "CREATE INDEX IF NOT EXISTS poem_row_entity ON poem_row(entityId, ordinal)",
  "CREATE INDEX IF NOT EXISTS poem_row_died ON poem_row(diedKey, titleNorm)",
  "CREATE INDEX IF NOT EXISTS poem_row_title ON poem_row(titleNorm, ordinal)",
  "CREATE INDEX IF NOT EXISTS poem_row_poet ON poem_row(poetNorm, entityId, ordinal)",
]) db.exec(ddl);
const insertPoem = db.prepare("INSERT INTO poem_row(entityId, ordinal, title, titleNorm, poetNorm, diedKey, opening, verseCount, openingNorm) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
let poemRows = 0;
for (const e of catalog.entries) {
  if (e.coll !== "poem" || e.editionOf != null) continue;
  const poetNorm = normalizeArabic(e.personName ?? "");
  const row = (ordinal, title, opening, verses, verseCount) => {
    insertPoem.run(e.id, ordinal, title, normalizeArabic(title), poetNorm, e.died ?? 100000, opening, verseCount,
      verses.length ? verses.map(normalizeArabic).join(" ") : null);
    poemRows++;
  };
  if (e.poems?.length) for (const p of e.poems) row(p.o, p.t, null, [], null);
  else row(null, e.title, JSON.stringify(e.openingVerses ?? []), e.openingVerses ?? [], e.verseCount > 0 ? e.verseCount : null);
}
console.log(`poem_row: ${poemRows} poems`);
// Each entry's line hash as AppContentDocuments.lineHash makes it, so the first sync reads only changed lines.
if (root.light) {
  const lines = readFileSync(path.join(source, root.light.path), "utf8").split("\n").filter(Boolean);
  const people = new Map((JSON.parse(lines[0]).persons ?? []).map((p) => [p.id, p]));
  const insertLine = db.prepare("INSERT OR REPLACE INTO catalogLine(id, sha) VALUES (?, ?)");
  for (const line of lines.slice(1)) {
    const at = line.indexOf('"person":"');
    const person = at < 0 ? undefined : people.get(line.slice(at + 10, line.indexOf('"', at + 10)));
    const text = `${version}\n${line}` + (person ? `\n${person.name}\n${person.died ?? ""}` : "");
    insertLine.run(JSON.parse(line).id, createHash("sha256").update(text, "utf8").digest("hex").slice(0, 32));
  }
  console.log(`catalogLine: ${lines.length - 1} lines`);
}
db.exec("COMMIT");
db.exec("VACUUM");
db.close();

console.log(`bundled generation ${root.generationId}: ${catalog.entries.length} entries, schema ${schema.version}, ${(statSync(dbPath).size / 1e6).toFixed(1)} MB -> ${path.relative(process.cwd(), target)}`);
