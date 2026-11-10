// The search API, served by workers/search (search.arthurarchive.com) and, forwarded or as a
// fallback, by the site's /api/search. Backed by the D1 FTS5 index (SEARCH binding,
// built by scripts/gen-search-index.ts) + a small person_meta side table
// (scripts/gen-search-meta.ts) for author name/death-year — joined at query
// time rather than added to the FTS5 schema, since FTS5 tables can't
// ALTER TABLE ADD COLUMN (would force a full ~22k-doc reindex).
// Query params: q (required), mode (any|all|phrase), field (all|title|text),
// type/book/person (comma-separated, optional scope filters), century (author death
// centuries, e.g. 7,8), topic (topic titles; books only, via work_topic), sort
// (relevance|death|death_desc|book = index order, the book read through), offset, snip (16–64 snippet tokens; the app asks for ~3
// lines), facets=1 (a separate call answering ONLY total match count + top authors
// and types, for the app's «في كل المكتبة · N» line and author chips — it
// reads at most 1000 matches, so it never delays hits).
import { normalizeArabic } from "./ar-normalize.js";
import { exactSnippet, hasTypedSpelling, type Mode, tierExprs, titleStartExpr, typedSpelling, widestExpr } from "./search-rank.js";

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
// ponytail: rank (bm25) is computed for the first RANK_CAP matches in index order only — ranking
// all 176k rows of «الله» took ~1 s, the first 10k ~50 ms. A word that common ranks within a sample;
// raise the cap if results for very common words look arbitrary.
const RANK_CAP = 10000;
// Works shown first because their title starts with the words (the متن before its شروح).
const TITLE_TIER_MAX = 3;
// One work fills at most this many places in a tier, so the page shows many sources.
const WORK_CAP = 2;
// Past this, a request with rows already stops before its next tier (see ranked()).
const TIER_BUDGET_MS = 1500;
type Cursor = { tier: number; skip: number };

type Row = { row: number; type: string; url: string; title: string; book: string; raw: string | null; snippet: string; person_name: string | null; death_year: number | null };

// ponytail: the passage as written (docs.raw) arrives with a full reindex, which takes days on
// D1's free write quota; until then the folded text is shown. doc_meta.prior (source priority)
// arrives with the same reindex; until then every source weighs 1. Checked once per isolate.
let schema: { raw: boolean; prior: boolean } | undefined;

export async function searchResponse(url: URL): Promise<Response> {
  const { env } = await import("cloudflare:workers");
  const db = (env as unknown as { SEARCH?: D1Like }).SEARCH;
  if (!db) return json({ hits: [], hasMore: false, error: "search unavailable" }, 503);
  if (!schema) {
    // An overloaded D1 fails this too: answer 503 (the app retries), not an uncaught 500.
    try {
      const [cols] = (await db.prepare(`SELECT (SELECT count(*) FROM pragma_table_info('docs') WHERE name = 'raw') AS raw,
        (SELECT count(*) FROM pragma_table_info('doc_meta') WHERE name = 'prior') AS prior`).bind().all()).results as { raw: number; prior: number }[];
      schema = { raw: cols.raw > 0, prior: cols.prior > 0 };
    } catch (err) {
      console.error("search schema probe failed", { message: String(err) });
      return json({ hits: [], hasMore: false, error: "search unavailable" }, 503);
    }
  }
  const rawCol = schema.raw ? "docs.raw" : "NULL";
  const priorK = schema.prior ? " * m.prior" : "";

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

  const modeParam = url.searchParams.get("mode");
  const mode: Mode = modeParam === "any" || modeParam === "phrase" || modeParam === "root" ? modeParam : "all";
  // a root's forms run long (كتب has ~60); anything else is a typed query
  const root = mode === "root";
  const query = (url.searchParams.get("q") ?? "").slice(0, root ? 1500 : 200);
  const tokens = normalizeArabic(query).replaceAll('"', " ").split(/\s+/).filter(Boolean).slice(0, root ? 120 : 12);
  if (tokens.length === 0) return json({ hits: [], hasMore: false });
  // field=title: titles only, one row per work (a chunked book has a row per chapter, all with
  // its title). field=text: body text only.
  const field = ["title", "text"].includes(url.searchParams.get("field") ?? "") ? url.searchParams.get("field")! : "all";
  // «all» is the passage text plus the title tier below: matching the title column too counted
  // every passage of «نونية القحطاني» as a hit for نونية, though only one has the word.
  const scoped = (expr: string) => `${field === "title" ? "title" : "text"} : (${expr})`;

  // ?1 is always the match expression; the scope filters follow it.
  const filterBinds: string[] = [];
  let filters = "";
  // One book asked for (the app's in-book search): every matching chapter, not WORK_CAP of them.
  let workCap = WORK_CAP;
  for (const col of ["type", "book", "person"] as const) {
    const vals = (url.searchParams.get(col) ?? "")
      .split(",").map((s) => s.trim()).filter(Boolean)
      .filter((v) => (col === "type" ? TYPES.has(v) : SLUG_OK.test(v)));
    if (col === "book" && vals.length === 1) workCap = RANK_CAP;
    if (vals.length) {
      filters += ` AND m.${col} IN (${vals.map((v) => { filterBinds.push(v); return `?${filterBinds.length + 1}`; }).join(",")})`;
    }
  }
  const bind = (v: string) => { filterBinds.push(v); return `?${filterBinds.length + 1}`; };
  const centuries = (url.searchParams.get("century") ?? "").split(",").map((s) => parseInt(s, 10)).filter((n) => n >= 1 && n <= 15);
  if (centuries.length) filters += ` AND m.person IN (SELECT slug FROM person_meta WHERE (death_year - 1) / 100 + 1 IN (${centuries.join(",")}))`;
  // ponytail: a section filter keeps books only (work_topic maps book → topic title); poems and articles join it if asked
  const topics = (url.searchParams.get("topic") ?? "").split(",").map((s) => s.trim()).filter((s) => s && s.length <= 60).slice(0, 40);
  if (topics.length) filters += ` AND m.book IN (SELECT book FROM work_topic WHERE topic IN (${topics.map(bind).join(",")}))`;
  // type/book/person/url are UNINDEXED in docs, so reading them loads the whole row (text and
  // all): a poem filter on a common word read 108k rows in 7.5 s. doc_meta holds the same four
  // per rowid (gen-search-index.ts keeps it in step), ~100 ms for the same filter.
  const fromMeta = "FROM docs CROSS JOIN doc_meta m ON m.rowid = docs.rowid";
  const work = "COALESCE(NULLIF(m.book, ''), m.url)";

  const offset = Math.max(0, Math.min(2000, parseInt(url.searchParams.get("offset") ?? "0", 10) || 0));
  // `after=<tier>.<rows>`: the `next` of the page before; replaces offset for the relevance order.
  const after = /^\d{1,2}\.\d{1,4}$/.test(url.searchParams.get("after") ?? "") ? url.searchParams.get("after")! : null;
  const start: Cursor = after ? { tier: Number(after.split(".")[0]), skip: Number(after.split(".")[1]) } : { tier: 0, skip: offset };
  const sortParam = url.searchParams.get("sort");
  const byBook = sortParam === "book";
  const byDeath = byBook || sortParam === "death" || sortParam === "death_desc";
  // Unknown death years stay last in both directions.
  const deathOrder = `(pm.death_year IS NULL), pm.death_year ${sortParam === "death_desc" ? "DESC" : "ASC"}`;

  // snippet() takes a column INDEX: 0 = title, 1 = text (gen-search-index.ts column order).
  const snip = Math.max(16, Math.min(64, parseInt(url.searchParams.get("snip") ?? "16", 10) || 16));
  const rowSelect = `docs.rowid AS row, docs.type AS type, docs.url AS url, docs.display_title AS title, docs.book AS book, ${rawCol} AS raw,
    snippet(docs, ${field === "title" ? 0 : 1}, '<mark>', '</mark>', '…', ${snip}) AS snippet,
    pm.name AS person_name, pm.death_year AS death_year`;
  const fromJoin = "FROM docs LEFT JOIN person_meta pm ON pm.slug = docs.person";

  // One tier's rows, best first, with their snippets: ranking and snippet() in one statement, so a
  // tier costs one D1 round trip (~200 ms each from the Worker), not two. grouped = one per work
  // (its first row). bm25 is negative (lower = better), so a larger m.prior (earlier author, a
  // متن) pulls a row up; [names] puts the author's page first when the words are a name. The
  // snippet marks what [shown] matched (a superset of [expr]).
  // ponytail: rows skipped by a later page's offset get snippets too; cheap next to a round trip.
  const pick = async (expr: string, shown: string, grouped: boolean, limit: number, exclude: { rows: number[]; works: string[] }, names = false) => {
    const binds: (string | number)[] = [expr, ...filterBinds];
    let not = exclude.rows.length ? ` AND docs.rowid NOT IN (${exclude.rows.join(",")})` : "";
    if (exclude.works.length) not += ` AND ${work} NOT IN (${exclude.works.map((w) => { binds.push(w); return `?${binds.length}`; }).join(",")})`;
    const k = `rank${priorK}${names ? " * (CASE WHEN m.type = 'person' THEN 2 ELSE 1 END)" : ""}`;
    const inner = `SELECT docs.rowid AS r, ${work} AS w, ${k} AS k ${fromMeta} WHERE docs MATCH ?1${filters}${not} LIMIT ${RANK_CAP}`;
    // ponytail: at most WORK_CAP rows per work within one tier; a work can still recur in a later tier
    const best = grouped
      ? `SELECT min(r) AS row, w AS work, min(k) AS k FROM (${inner}) GROUP BY w ORDER BY min(k) LIMIT ${limit}`
      : `SELECT r AS row, w AS work, k FROM (SELECT r, w, k, row_number() OVER (PARTITION BY w ORDER BY k) AS n FROM (${inner})) WHERE n <= ${workCap} ORDER BY k LIMIT ${limit}`;
    binds.push(shown);
    const sql = `SELECT ${rowSelect}, p.work AS work FROM (${best}) p CROSS JOIN docs ON docs.rowid = p.row
      LEFT JOIN person_meta pm ON pm.slug = docs.person WHERE docs MATCH ?${binds.length} ORDER BY p.k`;
    return (await db.prepare(sql).bind(...binds).all()).results as (Row & { work: string })[];
  };
  // The rows themselves; the snippet marks what [expr] matched.
  const details = async (expr: string, rows: number[]) =>
    rows.length
      ? ((await db.prepare(`SELECT ${rowSelect} ${fromJoin} WHERE docs MATCH ?1 AND docs.rowid IN (${rows.join(",")})`).bind(expr).all()).results as Row[])
      : [];

  // Every tier in turn until the page (plus one row, to know there is a next) is full. [start] is
  // where the last page stopped (`after`): its tier and the rows of it already shown, so a later
  // page runs only the tiers it still needs. Old clients send `offset`: tier 0, that many rows in.
  const ranked = async (m: Mode, start: Cursor) => {
    const tiers = tierExprs(tokens, m).map((e) => ({ expr: scoped(e), grouped: field === "title", title: false }));
    if (field !== "text" && m !== "root") tiers.unshift({ expr: titleStartExpr(tokens), grouped: true, title: true });
    const need = PAGE_SIZE + 1;
    let skip = start.skip;
    const exclude = { rows: [] as number[], works: [] as string[] };
    const pages: (Row & { work: string })[][] = [];
    let count = 0;
    let next: Cursor | null = null;
    let last = { tier: 0, taken: 0 };
    const began = Date.now();
    for (let i = 0; i < tiers.length; i++) {
      const tier = tiers[i];
      if (count >= need) break;
      // The title tier is three rows; a later page still runs it, for the rows the others exclude.
      const resumed = i < start.tier;
      if (resumed && !tier.title) continue;
      // One request never walks every tier of a heavy query: once the page has rows and the
      // budget is spent, it stops and the next page carries on from this tier. A long walk held
      // D1 for 3–8 s and every search in the next 10 s was refused («overloaded», 2026-11-10).
      if (!resumed && count > 0 && Date.now() - began > TIER_BUDGET_MS) { next = { tier: i, skip: 0 }; break; }
      // the title tier's rows also carry the passage's own words marked
      const shown = tier.title ? `(${tier.expr}) OR (${scoped(widestExpr(tokens, m))})` : tier.expr;
      const got = await pick(tier.expr, shown, tier.grouped, tier.title ? TITLE_TIER_MAX : skip + need - count, exclude, tier.title);
      if (tier.title) {
        exclude.rows = got.map((r) => r.row);
        if (field === "title") exclude.works = got.map((r) => r.work);
      }
      if (resumed) continue;
      if (got.length <= skip) { skip -= got.length; continue; }
      const rows = got.slice(skip, skip + need - count);
      last = { tier: i, taken: skip + rows.length };
      skip = 0;
      count += rows.length;
      pages.push(rows);
    }
    // the look-ahead row only says there is a next page: it must not be sorted into this one
    if (count > PAGE_SIZE) {
      pages.at(-1)!.pop();
      next = { tier: last.tier, skip: last.taken - 1 };
    }
    const more = next !== null;
    const spelling = m === "root" ? null : typedSpelling(query, tokens);
    const out: Row[] = [];
    for (const rows of pages) {
      for (const r of rows) r.snippet = exactSnippet(r.snippet, r.raw);
      // within a tier, the spelling as typed first (stable: rank order otherwise)
      if (spelling) rows.sort((a, b) => Number(hasTypedSpelling(b.snippet, spelling)) - Number(hasTypedSpelling(a.snippet, spelling)));
      out.push(...rows.map(({ work: _work, ...r }) => r));
    }
    return { rows: out, more, next };
  };

  const widest = scoped(widestExpr(tokens, mode));
  // Facets read the whole match set once more, without snippet(): only for the app's count and
  // author chips, on their own call. Capped so a common word answers «أكثر من ١٠٠٠» in under a
  // second (20k took ~10 s). ponytail: past the cap the author chips come from the first 1000
  // matches in index order; a per-word facet table if they look skewed.
  const FACET_CAP = 1000;
  const facetSql = `SELECT d.type AS type, d.person AS person, pm.name AS person_name, pm.death_year AS death_year, count(*) AS n
       FROM (SELECT m.type AS type, m.person AS person ${fromMeta} WHERE docs MATCH ?1${filters} LIMIT ${FACET_CAP}) d
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
    let next: Cursor | null = null;
    if (byDeath) {
      // the death-year order replaces relevance, so there are no tiers to walk. Row ids first
      // (doc_meta only), then the page's rows with their snippets.
      const inner = `SELECT docs.rowid AS r, ${work} AS w, m.person AS p ${fromMeta} WHERE docs MATCH ?1${filters} LIMIT ${RANK_CAP}`;
      const ids = (await db.prepare(`SELECT ${field === "title" ? "min(r)" : "r"} AS row FROM (${inner}) d
           LEFT JOIN person_meta pm ON pm.slug = d.p${field === "title" ? " GROUP BY w" : ""}
           ORDER BY ${byBook ? "row" : deathOrder} LIMIT ${PAGE_SIZE + 1} OFFSET ${offset}`).bind(widest, ...filterBinds).all()).results as { row: number }[];
      const byRow = new Map((await details(widest, ids.map((r) => r.row))).map((r) => [r.row, r]));
      results = ids.map((r) => byRow.get(r.row)).filter((r): r is Row => !!r);
      for (const r of results) r.snippet = exactSnippet(r.snippet, r.raw);
      more = results.length > PAGE_SIZE;
    } else {
      ({ rows: results, more, next } = await ranked(mode, start));
      // Every word found nothing: one mistyped word shouldn't dead-end the search. Retry as
      // «بعض الكلمات» and say so. Only for a first page of a short query (a long OR scan is costly).
      if (results.length === 0 && mode === "all" && offset === 0 && !after && tokens.length > 1 && tokens.length <= 4) {
        ({ rows: results, more, next } = await ranked("any", start));
        approximate = results.length > 0;
      }
    }
    // ponytail: a passage indexed twice (left over by an interrupted index update) shows once
    const seen = new Set<string>();
    const hits = results.slice(0, PAGE_SIZE)
      .filter((r) => !seen.has(r.url + r.snippet) && seen.add(r.url + r.snippet))
      .map(({ raw: _raw, ...hit }) => hit);
    // `next` resumes the walk (`after=`); an «approximate» page came from «any», which a later page
    // with mode=all would not walk, so it pages by offset as before.
    return json({ hits, hasMore: more, approximate, ...(next && !approximate ? { next: `${next.tier}.${next.skip}` } : {}) });
  } catch (err) {
    // A broken search must not look like an empty one: the site shows «تعذَّر البحث الآن» for a
    // non-ok answer, and the Worker log gets the reason.
    console.error("search query failed", { message: String(err), tokens: tokens.length, field, mode });
    return json({ hits: [], hasMore: false, error: "search unavailable" }, 503);
  }
}
