// Build the D1 FTS5 search index as SQL files under dist/search-index/.
// Load remotely with `pnpm search:index` (or `pnpm search:index:local` for the
// dev server's local D1). Full rebuild each run: DROP + CREATE + INSERTs.
// Granularity mirrors the site's deep links: quran = per ayah, chunked books =
// per chapter (same slugs as gen-book-chapters.ts), everything else = one doc.
import fs from "node:fs";
import path from "node:path";
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

  for (const e of entries) {
    const title = String(e.data.title ?? "");
    const person = String(e.data.person ?? "");
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
                title, text: strip(p.text),
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
              title, text: strip(p.text),
            });
          }
        } else if (a.chunked) {
          for (const c of a.chapters) {
            docs.push({
              type: "book", book: e.id, person,
              url: `/book/${e.id}/${c.slug}`,
              displayTitle: `${title} — ${c.title}`,
              title: `${title} ${c.title}`, text: strip(c.content),
            });
          }
        } else {
          docs.push({ type: "book", book: e.id, person, url: `/book/${e.id}`, displayTitle: title, title, text: strip(e.body) });
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
          displayTitle: title, title,
          text: strip([e.data.description ?? "", e.data.definition ?? "", e.body].join(" ")),
        });
        break;
      }
      case "question": {
        docs.push({ type: "question", book: "", person, url: `/questions/${e.id}`, displayTitle: title, title, text: strip(e.body) });
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

  // Split long bodies into ~15k-char rows (same url/title): keeps every INSERT
  // under D1's 100 KB statement cap (Arabic ≈ 2 bytes/char) and tightens snippets.
  const CHUNK = 15_000;
  const alive = docs
    .filter((d) => d.text || d.title)
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

  const HEADER =
    "DROP TABLE IF EXISTS docs;\n" +
    "CREATE VIRTUAL TABLE docs USING fts5(title, text, type UNINDEXED, book UNINDEXED, person UNINDEXED, url UNINDEXED, display_title UNINDEXED, tokenize='unicode61 remove_diacritics 2');\n";
  const STMT_MAX = 80_000; // BYTES — D1 caps a single SQL statement at 100 KB
  // one file: `wrangler d1 execute --file` bulk-imports (docs allow up to 5 GB),
  // and a single fixed path keeps the CI d1-load step a static one-liner
  const FILE_MAX = 4_000_000_000;
  const blen = (s: string) => Buffer.byteLength(s, "utf-8");

  let fileNo = 0;
  let buf = HEADER;
  let stmt = "";
  const flushStmt = () => { if (stmt) { buf += `INSERT INTO docs (title,text,type,book,person,url,display_title) VALUES ${stmt};\n`; stmt = ""; } };
  const flushFile = () => {
    flushStmt();
    if (!buf) return;
    fs.writeFileSync(path.join(outDir, `${String(fileNo++).padStart(3, "0")}.sql`), buf, "utf-8");
    buf = "";
  };
  for (const d of alive) {
    const r = row(d);
    if (stmt && blen(stmt) + blen(r) > STMT_MAX) flushStmt();
    if (blen(buf) + blen(stmt) + blen(r) > FILE_MAX) flushFile();
    stmt += stmt ? `,\n${r}` : r;
  }
  flushFile();

  const bytes = fs.readdirSync(outDir).reduce((n, f) => n + fs.statSync(path.join(outDir, f)).size, 0);
  console.log(`search index: ${alive.length} docs → ${fileNo} sql file(s), ${(bytes / 1e6).toFixed(1)} MB in ${outDir}`);
  if (alive.length === 0) throw new Error("search index came out empty — refusing to write a DROP-only script");
}

main();
