// Build the D1 FTS5 search index as SQL files under dist/search-index/.
// Load remotely with `pnpm search:index` (or `pnpm search:index:local` for the
// dev server's local D1). Granularity mirrors the site's deep links: quran =
// per ayah, chunked books = per chapter (same slugs as gen-book-chapters.ts),
// everything else = one doc.
//
// Incremental: `pnpm search:hashes` dumps the remote `doc_hash(url, hash)`
// table to `.search-hashes.json` before this runs. When that file has content,
// only docs whose hash changed (or are new/removed) get DELETE+INSERT SQL —
// a full reindex is ~30k row writes against D1's 100k/day free quota, so most
// deploys (a handful of edited books) should cost a few hundred, not 30k.
// Bootstrap/fallback: no hashes file (first run, or `doc_hash` doesn't exist
// yet remotely) → full rebuild (DROP + CREATE + insert everything), same as
// before this was made incremental.
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { loadContentFromDisk } from "../src/lib/load.js";
import { analyzeBook } from "../src/lib/chunk.js";
import { parseBook } from "../src/lib/chapters.js";
import { normalizeArabic } from "../src/lib/ar-normalize.js";
import { isAtharNumberedBook, parseAtharNumber } from "../src/lib/hadith.js";
import { toArabicDigits } from "../src/lib/display.js";

interface Doc {
  type: string;
  book: string;   // owning book/poem/surah id — powers the `in=` scope filter
  person: string; // author slug — powers the `person=` scope filter
  url: string;
  displayTitle: string;
  title: string;  // normalized, searchable
  text: string;   // normalized, searchable
}

// markdown/html → normalized plain text for indexing
function strip(md: string): string {
  return normalizeArabic(
    md
      .replace(/<[^>]+>/g, " ")
      .replace(/\{#[^}]*\}/g, " ")
      .replace(/\[\[([^\]|]*\|)?([^\]]*)\]\]/g, "$2")
      .replace(/[#>*_`[\]()|]/g, " ")
      .replace(/\s+/g, " ")
      .trim(),
  );
}

function main() {
  const entries = loadContentFromDisk().filter((e) => e.data.status === "published");
  const docs: Doc[] = [];
  const personName = new Map(
    entries.filter((e) => e.collection === "person").map((e) => [e.id, String(e.data.title ?? "")]),
  );

  for (const e of entries) {
    const title = String(e.data.title ?? "");
    const person = String(e.data.person ?? "");
    // An author's NAME never reached the index — only their slug, in the
    // UNINDEXED `person` column — so searching «عادل بن عزوز» matched his
    // person page and nothing he actually wrote, and the only way to his books
    // was via /person. Fold the display name into the searchable title column.
    // displayTitle (what a result renders) stays the clean work title.
    const byline = personName.get(person);
    const searchTitle = byline ? `${title} ${byline}` : title;
    switch (e.collection) {
      case "quran": {
        for (const p of parseBook(e.body).paragraphs) {
          docs.push({
            type: "quran", book: e.id, person: "",
            url: `/quran/${e.id}#${p.id}`,
            displayTitle: `${title} — الآية ${p.id}`,
            title, text: strip(p.text),
          });
        }
        break;
      }
      case "book": {
        const a = analyzeBook(e.body);
        // Athar-numbered books (e.g. "١٧ - حدثنا...") index one doc per athar
        // instead of per-chapter/whole-book — a search hit should land on the
        // narration itself (#athar-N), not force scanning a whole chapter for it.
        const atharNumbered = isAtharNumberedBook(parseBook(e.body).paragraphs);
        if (atharNumbered && a.chunked) {
          for (const c of a.chapters) {
            for (const p of parseBook(c.content).paragraphs) {
              const n = parseAtharNumber(p.text);
              if (n === null) continue;
              docs.push({
                type: "book", book: e.id, person,
                url: `/book/${e.id}/${c.slug}#athar-${n}`,
                displayTitle: `${title} — الأثر ${toArabicDigits(n)}`,
                title: searchTitle, text: strip(p.text),
              });
            }
          }
        } else if (atharNumbered) {
          for (const p of parseBook(e.body).paragraphs) {
            const n = parseAtharNumber(p.text);
            if (n === null) continue;
            docs.push({
              type: "book", book: e.id, person,
              url: `/book/${e.id}#athar-${n}`,
              displayTitle: `${title} — الأثر ${toArabicDigits(n)}`,
              title: searchTitle, text: strip(p.text),
            });
          }
        } else if (a.chunked) {
          for (const c of a.chapters) {
            docs.push({
              type: "book", book: e.id, person,
              url: `/book/${e.id}/${c.slug}`,
              displayTitle: `${title} — ${c.title}`,
              title: `${searchTitle} ${c.title}`, text: strip(c.content),
            });
          }
        } else {
          docs.push({ type: "book", book: e.id, person, url: `/book/${e.id}`, displayTitle: title, title: searchTitle, text: strip(e.body) });
        }
        break;
      }
      case "poem":
      case "article":
      case "term": {
        const urls: Record<string, string> = { poem: "poem", article: "article", term: "term" };
        docs.push({
          type: e.collection, book: e.collection === "poem" ? e.id : "", person,
          url: `/${urls[e.collection]}/${e.id}`,
          displayTitle: title, title: searchTitle,
          text: strip([e.data.description ?? "", e.data.definition ?? "", e.body].join(" ")),
        });
        break;
      }
      case "question": {
        docs.push({ type: "question", book: "", person, url: `/questions/${e.id}`, displayTitle: title, title: searchTitle, text: strip(e.body) });
        break;
      }
      case "person": {
        const aka = Array.isArray(e.data.also_known_as) ? e.data.also_known_as.join(" ") : "";
        docs.push({
          type: "person", book: "", person: e.id, url: `/person/${e.id}`,
          displayTitle: title, title: `${title} ${normalizeArabic(aka)}`,
          text: strip([e.data.bio ?? "", aka, e.body].join(" ")),
        });
        break;
      }
      // subject/topic/benefit/audio/annotation/announcement/highlight: no own
      // page or embedded-only — nothing search can honestly link to.
    }
  }

  const liveDocs = docs.filter((d) => d.text || d.title);

  // Load the remote doc_hash snapshot (see `pnpm search:hashes`). Empty/missing
  // → full-rebuild mode (can't safely diff against unknown remote state).
  const hashesPath = path.resolve(".search-hashes.json");
  let remoteHashes: Record<string, string> = {};
  try {
    const raw = JSON.parse(fs.readFileSync(hashesPath, "utf-8"));
    const rows = raw?.[0]?.results ?? [];
    for (const r of rows) if (r?.url && r?.hash) remoteHashes[r.url] = r.hash;
  } catch { /* missing/unparseable → treat as empty, full rebuild below */ }
  const incremental = Object.keys(remoteHashes).length > 0;

  const hashOf = (d: Doc) => crypto.createHash("sha256").update([d.title, d.text, d.displayTitle, d.type, d.book, d.person].join("\0")).digest("hex");
  const currentHashes = new Map(liveDocs.map((d) => [d.url, hashOf(d)]));

  const changedUrls = incremental
    ? new Set(liveDocs.filter((d) => remoteHashes[d.url] !== currentHashes.get(d.url)).map((d) => d.url))
    : new Set(liveDocs.map((d) => d.url)); // full rebuild: everything is "changed"
  const removedUrls = incremental
    ? Object.keys(remoteHashes).filter((u) => !currentHashes.has(u))
    : [];

  // Split long bodies into ~15k-char rows (same url/title): keeps every INSERT
  // under D1's 100 KB statement cap (Arabic ≈ 2 bytes/char) and tightens snippets.
  const CHUNK = 15_000;
  const alive = liveDocs
    .filter((d) => changedUrls.has(d.url))
    .flatMap((d) => {
      if (d.text.length <= CHUNK) return [d];
      const parts: Doc[] = [];
      let rest = d.text;
      while (rest.length > 0) {
        let cut = rest.length <= CHUNK ? rest.length : rest.lastIndexOf(" ", CHUNK);
        if (cut <= 0) cut = CHUNK;
        parts.push({ ...d, text: rest.slice(0, cut) });
        rest = rest.slice(cut + 1);
      }
      return parts;
    });
  const q = (s: string) => `'${s.replace(/'/g, "''").replace(/[\u0000-\u001f]/g, " ")}'`;
  const row = (d: Doc) => `(${q(normalizeArabic(d.title))},${q(d.text)},${q(d.type)},${q(d.book)},${q(d.person)},${q(d.url)},${q(d.displayTitle)})`;

  const outDir = path.resolve("dist/search-index");
  fs.rmSync(outDir, { recursive: true, force: true });
  fs.mkdirSync(outDir, { recursive: true });

  const HASH_TABLE = "CREATE TABLE IF NOT EXISTS doc_hash (url TEXT PRIMARY KEY, hash TEXT);\n";
  const HEADER = incremental
    ? "CREATE VIRTUAL TABLE IF NOT EXISTS docs USING fts5(title, text, type UNINDEXED, book UNINDEXED, person UNINDEXED, url UNINDEXED, display_title UNINDEXED, tokenize='unicode61 remove_diacritics 2');\n" + HASH_TABLE
    : "DROP TABLE IF EXISTS docs;\nDROP TABLE IF EXISTS doc_hash;\n" +
      "CREATE VIRTUAL TABLE docs USING fts5(title, text, type UNINDEXED, book UNINDEXED, person UNINDEXED, url UNINDEXED, display_title UNINDEXED, tokenize='unicode61 remove_diacritics 2');\n" + HASH_TABLE;
  const STMT_MAX = 80_000; // BYTES — D1 caps a single SQL statement at 100 KB
  // Real incident: a 53.6 MB single-file incremental batch (109-book import)
  // sat in remote `wrangler d1 execute` for ~14 min then failed with a blank
  // error — one big-enough diff and the whole remote import job stalls out.
  // Keep files small so CI's `for f in dist/search-index/*.sql` loop (already
  // there) does several fast remote calls instead of one that can time out.
  const FILE_MAX = 5_000_000;
  const blen = (s: string) => Buffer.byteLength(s, "utf-8");

  // ponytail: track byte sizes incrementally (running counters) instead of
  // re-running Buffer.byteLength over the whole accumulated buf/stmt on every
  // doc — that was O(total output size) per iteration, i.e. O(n²) overall,
  // and is what made this take 15+ minutes on the real ~22k-doc corpus.
  let fileNo = 0;
  let buf = HEADER;
  let bufBytes = blen(HEADER);
  let stmt = "";
  let stmtBytes = 0;
  const flushStmt = () => {
    if (!stmt) return;
    const line = `INSERT INTO docs (title,text,type,book,person,url,display_title) VALUES ${stmt};\n`;
    buf += line; bufBytes += blen(line);
    stmt = ""; stmtBytes = 0;
  };
  const flushFile = () => {
    flushStmt();
    if (!buf) return;
    fs.writeFileSync(path.join(outDir, `${String(fileNo++).padStart(3, "0")}.sql`), buf, "utf-8");
    buf = ""; bufBytes = 0;
  };
  const write = (line: string) => {
    flushStmt();
    buf += line; bufBytes += blen(line);
  };

  // --- resumable emit ---------------------------------------------------
  // D1's free plan REFUSES writes past 100k rows/day, and a corpus-wide change
  // (folding author names into the title dirtied ~96% of rows) needs far more
  // than one day's worth. The emit order used to make that unrecoverable: every
  // DELETE came first — including `DELETE FROM doc_hash` — and every doc_hash
  // upsert came last, so a run cut short by quota advanced NO hashes while
  // having emptied the hash table. The next run then saw an empty hash set,
  // fell into full-rebuild mode, DROPped docs, and got no further. A thrash
  // loop that leaves search broken rather than merely stale.
  //
  // So: emit one URL at a time as a self-contained unit — its DELETEs, its
  // rows, then its doc_hash upsert — and only ever start a new sql FILE on a
  // unit boundary. A file that fails (or is never reached) leaves exactly those
  // urls' hashes untouched, so the next deploy resumes precisely there.
  // The budget counts REAL rows written, deletes included — D1 bills a deleted
  // row the same as an inserted one. Per changed url that is: its old doc rows
  // deleted (≈ as many as we re-insert), its doc_hash row deleted, the new doc
  // rows, and the new hash row. Default leaves headroom under the free plan's
  // 100k/day; on Workers Paid (50M rows written/month included) raise it and
  // the whole corpus lands in one run.
  // 40k, not 80k: measured on run 30162955298, an 80k budget emitted 20112
  // urls across 36 sql files and D1 refused 21 of them once the day's writes
  // ran out — an hour of CI spent failing. Resumability meant nothing was lost
  // (the failed files' hashes stayed stale and roll to the next deploy), but
  // emitting work the quota can't absorb just burns time. Half the budget
  // lands nearly the same number of urls per day, in half the runtime.
  const ROW_BUDGET = Number(process.env.SEARCH_ROW_BUDGET ?? 40_000);

  const rowsByUrl = new Map<string, Doc[]>();
  for (const d of alive) {
    const list = rowsByUrl.get(d.url);
    if (list) list.push(d); else rowsByUrl.set(d.url, [d]);
  }

  // removed first (cheap, and frees their rows), then the changed ones
  const units: { url: string; rows: Doc[]; hash: string | null }[] = [
    ...removedUrls.map((url) => ({ url, rows: [] as Doc[], hash: null })),
    ...[...changedUrls].map((url) => ({ url, rows: rowsByUrl.get(url) ?? [], hash: currentHashes.get(url)! })),
  ];

  let spent = 0;
  let emitted = 0;
  let done = 0;
  for (const unit of units) {
    // full rebuild dropped the tables, so nothing is deleted there
    const cost = incremental ? unit.rows.length * 2 + 2 : unit.rows.length + 1;
    if (spent + cost > ROW_BUDGET) break; // the rest keep their old hash → next run
    // file boundaries only BETWEEN units, never inside one
    if (bufBytes + stmtBytes > FILE_MAX) flushFile();

    if (incremental) write(`DELETE FROM docs WHERE url = ${q(unit.url)};\nDELETE FROM doc_hash WHERE url = ${q(unit.url)};\n`);
    for (const d of unit.rows) {
      const r = row(d);
      const rBytes = blen(r);
      if (stmt && stmtBytes + rBytes > STMT_MAX) flushStmt();
      const hadStmt = !!stmt;
      stmt += hadStmt ? `,\n${r}` : r;
      stmtBytes += rBytes + (hadStmt ? 2 : 0); // +2 for ",\n"
    }
    if (unit.hash !== null) write(`INSERT OR REPLACE INTO doc_hash (url, hash) VALUES (${q(unit.url)}, ${q(unit.hash)});\n`);
    spent += cost;
    emitted += unit.rows.length;
    done++;
  }
  flushFile();

  const deferred = units.length - done;
  const bytes = fs.readdirSync(outDir).reduce((n, f) => n + fs.statSync(path.join(outDir, f)).size, 0);
  const mode = incremental ? `incremental: ${changedUrls.size} changed, ${removedUrls.length} removed, ${liveDocs.length - changedUrls.size} unchanged (skipped)` : "full rebuild";
  console.log(`search index (${mode}): ${emitted} row(s) → ${fileNo} sql file(s), ${(bytes / 1e6).toFixed(1)} MB in ${outDir}`);
  if (deferred > 0) {
    console.log(
      `::warning::search index budget reached — ${done} of ${units.length} url(s) emitted this run, ` +
        `${deferred} deferred to the next deploy (their doc_hash is deliberately left stale so they get picked up). ` +
        `Raise SEARCH_ROW_BUDGET (currently ${ROW_BUDGET}) only if the D1 plan allows more than 100k row writes/day.`,
    );
  }
  if (!incremental && alive.length === 0) throw new Error("search index came out empty — refusing to write a DROP-only script");
}

main();
