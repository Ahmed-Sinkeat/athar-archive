// On-demand search endpoint backed by the D1 FTS5 index (SEARCH binding,
// built by scripts/gen-search-index.ts) + a small person_meta side table
// (scripts/gen-search-meta.ts) for author name/death-year — joined at query
// time rather than added to the FTS5 schema, since FTS5 tables can't
// ALTER TABLE ADD COLUMN (would force a full ~22k-doc reindex).
// Query params: q (required), mode (any|all|phrase), field (all|title|text),
// type/book/person (comma-separated, optional scope filters), sort
// (relevance|death), offset, snip (16–64 snippet tokens; the app asks for ~3
// lines), facets=1 (a separate call answering ONLY total match count + top authors
// and types, for the app's «في كل المكتبة · N» line and author chips — it
// reads the whole match set, ~1.5 s on a common word, so it never delays hits).
import type { APIRoute } from "astro";
import { normalizeArabic } from "../../lib/ar-normalize.js";
import { exactSnippet, hasTypedSpelling, type Mode, tierExprs, titleStartExpr, typedSpelling, widestExpr } from "../../lib/search-rank.js";

export const prerender = false;

const TYPES = new Set(["book", "poem", "article", "question", "term", "person"]);
const SLUG_OK = /^[a-z0-9-]+$/;
const PAGE_SIZE = 20;

interface D1Like {
  prepare(sql: string): { bind(...v: (string | number)[]): { all(): Promise<{ results: unknown[] }> } };
}

// 30 min, not 5. Every repeat of a query inside the window is a D1 read pass
// that never happens — and D1's free plan refuses reads once the daily
// allowance is gone, which shows up as an empty search rather than an error.
// The index only changes on deploy, so half an hour of staleness costs
// nothing; stale-while-revalidate keeps a popular query warm for a day.
const CACHE = "public, max-age=1800, stale-while-revalidate=86400";
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    // never cache a failure — the next request should get a real attempt
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": status === 200 ? CACHE : "no-store" },
  });

// Ranking lives in src/lib/search-rank.ts: a title that starts with the words, then the exact
// phrase, every exact word, words that only start the same (and in any mode, single words).
// Each tier is its own query, run only while the page still needs rows, and excludes the
// tiers before it, so a passage never shows twice and a common phrase usually costs one query.
const TITLE_SCAN_CAP = 5000;
// Works shown first because their title starts with the words (the متن before its شروح).
const TITLE_TIER_MAX = 3;

type Row = { row: number; type: string; url: string; title: string; book: string; raw: string | null; snippet: string; person_name: string | null; death_year: number | null };

// ponytail: the passage as written (docs.raw) arrives with a full reindex, which takes days on
// D1's free write quota; until then the folded text is shown. Checked once per isolate.
let hasRaw: boolean | undefined;

export const GET: APIRoute = async ({ url }) => {
  const { env } = await import("cloudflare:workers");
  const db = (env as unknown as { SEARCH?: D1Like }).SEARCH;
  if (!db) return json({ hits: [], hasMore: false, error: "search unavailable" }, 503);
  hasRaw ??= (await db.prepare(`SELECT name FROM pragma_table_info('docs') WHERE name = 'raw'`).bind().all()).results.length > 0;
  const rawCol = hasRaw ? "docs.raw" : "NULL";

  // One passage in full, as written, for «المزيد» and copy: by row (instant), or by url from
  // older clients (a scan of the whole table, ~1.5 s).
  const wantRow = Number(url.searchParams.get("row"));
  const wantUrl = url.searchParams.get("url");
  if (wantRow > 0 || wantUrl) {
    try {
      const { results } = wantRow > 0
        ? await db.prepare(`SELECT ${rawCol} AS raw, text FROM docs WHERE rowid = ?1`).bind(wantRow).all()
        : await db.prepare(`SELECT ${rawCol} AS raw, text FROM docs WHERE url = ?1 LIMIT 1`).bind(wantUrl!).all();
      const r = results[0] as { raw?: string | null; text?: string } | undefined;
      return json({ text: r?.raw || r?.text || "" });
    } catch {
      return json({ text: "" });
    }
  }

  const query = (url.searchParams.get("q") ?? "").slice(0, 200);
  const tokens = normalizeArabic(query).replaceAll('"', " ").split(/\s+/).filter(Boolean).slice(0, 12);
  if (tokens.length === 0) return json({ hits: [], hasMore: false });

  const modeParam = url.searchParams.get("mode");
  const mode: Mode = modeParam === "any" || modeParam === "phrase" ? modeParam : "all";
  // field=title: titles only, one row per work (a chunked book has a row per chapter, all with
  // its title). field=text: body text only.
  const field = ["title", "text"].includes(url.searchParams.get("field") ?? "") ? url.searchParams.get("field")! : "all";
  const scoped = (expr: string) => (field === "all" ? expr : `${field} : (${expr})`);

  // ?1 is always the match expression; the scope filters follow it.
  const filterBinds: string[] = [];
  let filters = "";
  for (const col of ["type", "book", "person"] as const) {
    const vals = (url.searchParams.get(col) ?? "")
      .split(",").map((s) => s.trim()).filter(Boolean)
      .filter((v) => (col === "type" ? TYPES.has(v) : SLUG_OK.test(v)));
    if (vals.length) {
      filters += ` AND docs.${col} IN (${vals.map((v) => { filterBinds.push(v); return `?${filterBinds.length + 1}`; }).join(",")})`;
    }
  }
  const work = "COALESCE(NULLIF(docs.book, ''), docs.url)";

  const offset = Math.max(0, Math.min(2000, parseInt(url.searchParams.get("offset") ?? "0", 10) || 0));
  const sortParam = url.searchParams.get("sort");
  const byDeath = sortParam === "death" || sortParam === "death_desc";
  // Unknown death years stay last in both directions.
  const deathOrder = `(pm.death_year IS NULL), pm.death_year ${sortParam === "death_desc" ? "DESC" : "ASC"}`;

  // snippet() takes a column INDEX: 0 = title, 1 = text (gen-search-index.ts column order).
  const snip = Math.max(16, Math.min(64, parseInt(url.searchParams.get("snip") ?? "16", 10) || 16));
  const rowSelect = `docs.rowid AS row, docs.type AS type, docs.url AS url, docs.display_title AS title, docs.book AS book, ${rawCol} AS raw,
    snippet(docs, ${field === "title" ? 0 : 1}, '<mark>', '</mark>', '…', ${snip}) AS snippet,
    pm.name AS person_name, pm.death_year AS death_year`;
  const fromJoin = "FROM docs LEFT JOIN person_meta pm ON pm.slug = docs.person";

  // Row ids of one tier, best first; grouped = one per work (its first row).
  const pick = async (expr: string, grouped: boolean, limit: number, exclude: { rows: number[]; works: string[] }) => {
    const binds: (string | number)[] = [expr, ...filterBinds];
    let not = exclude.rows.length ? ` AND docs.rowid NOT IN (${exclude.rows.join(",")})` : "";
    if (exclude.works.length) not += ` AND ${work} NOT IN (${exclude.works.map((w) => { binds.push(w); return `?${binds.length}`; }).join(",")})`;
    const sql = grouped
      ? `SELECT min(r) AS row, w AS work FROM (SELECT docs.rowid AS r, ${work} AS w, rank AS k FROM docs WHERE docs MATCH ?1${filters}${not}
           ORDER BY rank LIMIT ${TITLE_SCAN_CAP}) GROUP BY w ORDER BY min(k) LIMIT ${limit}`
      : `SELECT docs.rowid AS row, ${work} AS work FROM docs WHERE docs MATCH ?1${filters}${not} ORDER BY rank LIMIT ${limit}`;
    return (await db.prepare(sql).bind(...binds).all()).results as { row: number; work: string }[];
  };
  // The rows themselves; the snippet marks what [expr] matched.
  const details = async (expr: string, rows: number[]) =>
    rows.length
      ? ((await db.prepare(`SELECT ${rowSelect} ${fromJoin} WHERE docs MATCH ?1 AND docs.rowid IN (${rows.join(",")})`).bind(expr).all()).results as Row[])
      : [];

  // Every tier in turn until the page (plus one row, to know there is a next) is full.
  const ranked = async (m: Mode) => {
    const tiers = tierExprs(tokens, m).map((e) => ({ expr: scoped(e), grouped: field === "title", title: false }));
    if (field !== "text") tiers.unshift({ expr: titleStartExpr(tokens), grouped: true, title: true });
    const need = PAGE_SIZE + 1;
    let skip = offset;
    const exclude = { rows: [] as number[], works: [] as string[] };
    const pages: { expr: string; rows: number[] }[] = [];
    let count = 0;
    for (const tier of tiers) {
      if (count >= need) break;
      const got = await pick(tier.expr, tier.grouped, tier.title ? TITLE_TIER_MAX : skip + need - count, exclude);
      if (tier.title) {
        exclude.rows = got.map((r) => r.row);
        if (field === "title") exclude.works = got.map((r) => r.work);
      }
      if (got.length <= skip) { skip -= got.length; continue; }
      const rows = got.slice(skip, skip + need - count).map((r) => r.row);
      skip = 0;
      count += rows.length;
      // the title tier's rows also carry the passage's own words marked
      pages.push({ expr: tier.title ? `(${tier.expr}) OR (${scoped(widestExpr(tokens, m))})` : tier.expr, rows });
    }
    // the look-ahead row only says there is a next page: it must not be sorted into this one
    const more = count > PAGE_SIZE;
    if (more) pages.at(-1)!.rows.pop();
    const spelling = typedSpelling(query, tokens);
    const out: Row[] = [];
    for (const page of pages) {
      const byRow = new Map((await details(page.expr, page.rows)).map((r) => [r.row, r]));
      const rows = page.rows.map((id) => byRow.get(id)).filter((r): r is Row => !!r);
      for (const r of rows) r.snippet = exactSnippet(r.snippet, r.raw);
      // within a tier, the spelling as typed first (stable: rank order otherwise)
      if (spelling) rows.sort((a, b) => Number(hasTypedSpelling(b.snippet, spelling)) - Number(hasTypedSpelling(a.snippet, spelling)));
      out.push(...rows);
    }
    return { rows: out, more };
  };

  const widest = scoped(widestExpr(tokens, mode));
  // Facets read the whole match set once more, without snippet(): only for the app's count and
  // author chips, on their own call. Capped so a stop-word query can't scan the whole index.
  const FACET_CAP = 20000;
  const facetSql = `SELECT d.type AS type, d.person AS person, pm.name AS person_name, pm.death_year AS death_year, count(*) AS n
       FROM (SELECT docs.type AS type, docs.person AS person FROM docs WHERE docs MATCH ?1${filters} LIMIT ${FACET_CAP}) d
       LEFT JOIN person_meta pm ON pm.slug = d.person
       GROUP BY d.type, d.person`;
  type FacetRow = { type: string; person: string; person_name: string | null; death_year: number | null; n: number };
  const facetsOf = (rows: FacetRow[]) => {
    const persons = new Map<string, { slug: string; name: string | null; death_year: number | null; n: number }>();
    const types: Record<string, number> = {};
    let total = 0;
    for (const r of rows) {
      total += r.n;
      types[r.type] = (types[r.type] ?? 0) + r.n;
      if (!r.person) continue;
      const p = persons.get(r.person) ?? { slug: r.person, name: r.person_name, death_year: r.death_year, n: 0 };
      p.n += r.n;
      persons.set(r.person, p);
    }
    return { total, capped: total >= FACET_CAP, types, persons: [...persons.values()].sort((a, b) => b.n - a.n).slice(0, 12) };
  };

  try {
    if (url.searchParams.get("facets") === "1") {
      const { results: rows } = await db.prepare(facetSql).bind(widest, ...filterBinds).all();
      return json({ facets: facetsOf(rows as FacetRow[]) });
    }
    let results: Row[];
    let approximate = false;
    let more = false;
    if (byDeath) {
      // the death-year order replaces relevance, so there are no tiers to walk
      const sql = field === "title"
        ? `SELECT ${rowSelect} ${fromJoin} WHERE docs.rowid IN (SELECT min(r) FROM (SELECT docs.rowid AS r, ${work} AS w FROM docs
             WHERE docs MATCH ?1${filters} ORDER BY rank LIMIT ${TITLE_SCAN_CAP}) GROUP BY w) AND docs MATCH ?1
           ORDER BY ${deathOrder} LIMIT ${PAGE_SIZE + 1} OFFSET ${offset}`
        : `SELECT ${rowSelect} ${fromJoin} WHERE docs MATCH ?1${filters} ORDER BY ${deathOrder} LIMIT ${PAGE_SIZE + 1} OFFSET ${offset}`;
      results = (await db.prepare(sql).bind(widest, ...filterBinds).all()).results as Row[];
      for (const r of results) r.snippet = exactSnippet(r.snippet, r.raw);
      more = results.length > PAGE_SIZE;
    } else {
      ({ rows: results, more } = await ranked(mode));
      // Every word found nothing: one mistyped word shouldn't dead-end the search. Retry as
      // «بعض الكلمات» and say so. Only for a first page of a short query (a long OR scan is costly).
      if (results.length === 0 && mode === "all" && offset === 0 && tokens.length > 1 && tokens.length <= 4) {
        ({ rows: results, more } = await ranked("any"));
        approximate = results.length > 0;
      }
    }
    // ponytail: a passage indexed twice (left over by an interrupted index update) shows once
    const seen = new Set<string>();
    const hits = results.slice(0, PAGE_SIZE)
      .filter((r) => !seen.has(r.url + r.snippet) && seen.add(r.url + r.snippet))
      .map(({ raw: _raw, ...hit }) => hit);
    return json({ hits, hasMore: more, approximate });
  } catch (err) {
    // A broken search must not look like an empty one: the site shows «تعذَّر البحث الآن» for a
    // non-ok answer, and the Worker log gets the reason.
    console.error("search query failed", { message: String(err), tokens: tokens.length, field, mode });
    return json({ hits: [], hasMore: false, error: "search unavailable" }, 503);
  }
};
