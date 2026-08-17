// Build the D1 FTS5 search index as SQL files under dist/search-index/.
// Load remotely with `pnpm search:index` (or `pnpm search:index:local` for the
// dev server's local D1). Granularity mirrors the site's deep links: chunked
// books = per chapter (same slugs as gen-book-chapters.ts), everything else =
// one doc. Every book is indexed by the same rules — no per-collection or
// per-narration special cases.
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
import { loadContentMetaFromDisk } from "../src/lib/load.js";
import { readBody } from "../src/lib/read-body.js";
import { analyzeBook } from "../src/lib/chunk.js";
import { normalizeArabic } from "../src/lib/ar-normalize.js";
import { stripMd as strip } from "../src/lib/strip-md.js";

interface Doc {
  type: string;
  book: string;   // owning book/poem id — powers the `in=` scope filter
  person: string; // author slug — powers the `person=` scope filter
  url: string;
  displayTitle: string;
  title: string;  // normalized, searchable
  text: string;   // normalized, searchable
}

async function main() {
  // Metadata only — body.js loads lazily per entry (readBody below), never
  // all at once. book-lg alone is 3.2GB+; the old code loaded every body into
  // one `entries` array AND built a same-order-of-magnitude `docs` array
  // alongside it, so both were fully live at once — the ~6.5GB CI OOM ceiling
  // hit on 2026-07-29/30 (same "hold two copies of the corpus" shape as the
  // Astro getStaticPaths props fix). Now each entry's text exists only for
  // the moment it's hashed and (if changed) written to a SQL file; nothing
  // beyond that survives past its own iteration.
  const metaEntries = loadContentMetaFromDisk().filter((e) => e.data.status === "published");
  const personName = new Map(
    metaEntries.filter((e) => e.collection === "person").map((e) => [e.id, String(e.data.title ?? "")]),
  );

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

  // --- resumable, streaming emit ----------------------------------------
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

  // Split one doc's text into ~15k-char rows sharing its url (keeps every
  // INSERT under D1's 100 KB statement cap; Arabic ≈ 2 bytes/char).
  const CHUNK = 15_000;
  const splitDoc = (d: Doc): Doc[] => {
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
  };

  let spent = 0;
  let emitted = 0;
  let candidates = 0; // urls whose hash actually differs (or is new) — would-be emits
  let done = 0;
  const emitUnit = (url: string, rows: Doc[], hash: string | null): boolean => {
    const cost = incremental ? rows.length * 2 + 2 : rows.length + 1;
    if (spent + cost > ROW_BUDGET) return false; // the rest keep their old hash → next run
    if (bufBytes + stmtBytes > FILE_MAX) flushFile(); // file boundaries only BETWEEN units, never inside one
    if (incremental) write(`DELETE FROM docs WHERE url = ${q(url)};\nDELETE FROM doc_hash WHERE url = ${q(url)};\n`);
    for (const d of rows) {
      const r = row(d);
      const rBytes = blen(r);
      if (stmt && stmtBytes + rBytes > STMT_MAX) flushStmt();
      const hadStmt = !!stmt;
      stmt += hadStmt ? `,\n${r}` : r;
      stmtBytes += rBytes + (hadStmt ? 2 : 0); // +2 for ",\n"
    }
    if (hash !== null) write(`INSERT OR REPLACE INTO doc_hash (url, hash) VALUES (${q(url)}, ${q(hash)});\n`);
    spent += cost;
    emitted += rows.length;
    done++;
    return true;
  };

  // Only currentHashes.size stays live for the WHOLE run (short url/hash
  // strings, tens of MB at most for ~200k docs) — everything text-shaped is
  // scoped to processDoc's single call.
  const currentHashes = new Map<string, string>();
  const processDoc = (d: Doc) => {
    if (!(d.text || d.title)) return; // matches the old liveDocs filter
    const hash = hashOf(d);
    currentHashes.set(d.url, hash);
    if (incremental && remoteHashes[d.url] === hash) return; // unchanged, nothing to do
    candidates++;
    emitUnit(d.url, splitDoc(d), hash);
  };

  for (const e of metaEntries) {
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
      case "book": {
        const body = await readBody(e);
        const a = analyzeBook(body);
        if (a.chunked) {
          for (const c of a.chapters) {
            processDoc({
              type: "book", book: e.id, person,
              url: `/book/${e.id}/${c.slug}`,
              displayTitle: `${title} — ${c.title}`,
              title: `${searchTitle} ${c.title}`, text: strip(c.content),
            });
          }
        } else {
          processDoc({ type: "book", book: e.id, person, url: `/book/${e.id}`, displayTitle: title, title: searchTitle, text: strip(body) });
        }
        break;
      }
      case "poem":
      case "article":
      case "term": {
        const body = await readBody(e);
        const urls: Record<string, string> = { poem: "poem", article: "article", term: "term" };
        processDoc({
          type: e.collection, book: e.collection === "poem" ? e.id : "", person,
          url: `/${urls[e.collection]}/${e.id}`,
          displayTitle: title, title: searchTitle,
          text: strip([e.data.description ?? "", e.data.definition ?? "", body].join(" ")),
        });
        break;
      }
      case "question": {
        const body = await readBody(e);
        processDoc({ type: "question", book: "", person, url: `/questions/${e.id}`, displayTitle: title, title: searchTitle, text: strip(body) });
        break;
      }
      case "person": {
        const body = await readBody(e);
        const aka = Array.isArray(e.data.also_known_as) ? e.data.also_known_as.join(" ") : "";
        processDoc({
          type: "person", book: "", person: e.id, url: `/person/${e.id}`,
          displayTitle: title, title: `${title} ${normalizeArabic(aka)}`,
          text: strip([e.data.bio ?? "", aka, body].join(" ")),
        });
        break;
      }
      // subject/topic/benefit/audio/annotation/announcement/highlight: no own
      // page or embedded-only — nothing search can honestly link to.
    }
  }

  // Removed urls need the FULL currentHashes set to diff against, so they're
  // only knowable once every entry above has run — same reasoning as before,
  // just computed at the end of one pass instead of before a second one. One
  // real behavior change from the old "removed first" ordering: removed urls
  // now queue AFTER changed ones, so under sustained budget pressure a stale
  // (deleted-book) search hit could take longer to get swept than new/changed
  // content does to land — cosmetic, not a correctness issue (still resumable,
  // still converges next run).
  const removedUrls = incremental ? Object.keys(remoteHashes).filter((u) => !currentHashes.has(u)) : [];
  for (const url of removedUrls) {
    candidates++;
    emitUnit(url, [], null);
  }
  flushFile();

  const deferred = candidates - done;
  const bytes = fs.readdirSync(outDir).reduce((n, f) => n + fs.statSync(path.join(outDir, f)).size, 0);
  const mode = incremental ? `incremental: ${candidates} changed/removed, ${currentHashes.size - (candidates - removedUrls.length)} unchanged (skipped)` : "full rebuild";
  console.log(`search index (${mode}): ${emitted} row(s) → ${fileNo} sql file(s), ${(bytes / 1e6).toFixed(1)} MB in ${outDir}`);
  if (deferred > 0) {
    console.log(
      `::warning::search index budget reached — ${done} of ${candidates} url(s) emitted this run, ` +
        `${deferred} deferred to the next deploy (their doc_hash is deliberately left stale so they get picked up). ` +
        `Raise SEARCH_ROW_BUDGET (currently ${ROW_BUDGET}) only if the D1 plan allows more than 100k row writes/day.`,
    );
  }
  if (!incremental && emitted === 0) throw new Error("search index came out empty — refusing to write a DROP-only script");
}

await main();
