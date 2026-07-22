// On-demand search endpoint backed by the D1 FTS5 index (SEARCH binding,
// built by scripts/gen-search-index.ts) + a small person_meta side table
// (scripts/gen-search-meta.ts) for author name/death-year — joined at query
// time rather than added to the FTS5 schema, since FTS5 tables can't
// ALTER TABLE ADD COLUMN (would force a full ~22k-doc reindex).
// Query params: q (required), mode (any|all|phrase), field (all|title|text),
// type/book/person (comma-separated, optional scope filters), sort
// (relevance|death), offset.
import type { APIRoute } from "astro";
import { normalizeArabic } from "../../lib/ar-normalize.js";

export const prerender = false;

const TYPES = new Set(["quran", "book", "poem", "article", "question", "term", "person"]);
const SLUG_OK = /^[a-z0-9-]+$/;
const PAGE_SIZE = 20;

interface D1Like {
  prepare(sql: string): { bind(...v: (string | number)[]): { all(): Promise<{ results: unknown[] }> } };
}

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "public, max-age=300" },
  });

// mode=all (default): implicit AND across tokens, matching the current behavior.
// mode=any: OR across tokens — broader recall.
// mode=phrase: tokens must appear consecutively, in order.
// Every token is quoted (neutralizes FTS5 query operators) AND prefix-matched
// (trailing *) — not just the last one, so a typo/incomplete word ANYWHERE in
// a multi-word query ("هذا وأما" typed as "هذا وام") doesn't kill the whole
// AND match on its own; phrase mode keeps the old last-token-only prefix,
// since prefixing every word inside a quoted phrase isn't meaningful FTS5 syntax.
function buildMatch(tokens: string[], mode: string): string {
  const clean = tokens.map((t) => t.replaceAll('"', ""));
  if (mode === "phrase") {
    const words = clean.map((t, i) => (i === clean.length - 1 ? `${t}*` : t));
    return `"${words.join(" ")}"`;
  }
  const quoted = clean.map((t) => `"${t}"*`);
  return quoted.join(mode === "any" ? " OR " : " ");
}

export const GET: APIRoute = async ({ url }) => {
  const { env } = await import("cloudflare:workers");
  const db = (env as unknown as { SEARCH?: D1Like }).SEARCH;
  if (!db) return json({ hits: [], hasMore: false, error: "search unavailable" }, 503);

  // full-text lookup for one result (بحث page's "عرض الكل"/copy) — the main
  // query below only returns a short snippet() to keep result lists light;
  // fetching the whole paragraph is a separate, on-demand call by url (the
  // doc's unique key), not baked into every search response.
  const wantUrl = url.searchParams.get("url");
  if (wantUrl) {
    try {
      const { results } = await db.prepare(`SELECT text FROM docs WHERE url = ?1 LIMIT 1`).bind(wantUrl).all();
      return json({ text: (results[0] as { text?: string } | undefined)?.text ?? "" });
    } catch {
      return json({ text: "" });
    }
  }

  const raw = (url.searchParams.get("q") ?? "").slice(0, 200);
  const tokens = normalizeArabic(raw).split(/\s+/).filter(Boolean).slice(0, 12);
  if (tokens.length === 0) return json({ hits: [], hasMore: false });

  const mode = ["any", "all", "phrase"].includes(url.searchParams.get("mode") ?? "") ? url.searchParams.get("mode")! : "all";
  // field=title restricts matching to the title column only (skips a book's
  // whole text) — e.g. searching for an author or work by name without
  // pulling in every unrelated paragraph that happens to mention the word.
  // field=text is the opposite: body text only, titles excluded. FTS5's
  // `column: (expr)` syntax scopes an already-built match expression to one
  // column without changing how buildMatch() itself works.
  const field = ["title", "text"].includes(url.searchParams.get("field") ?? "") ? url.searchParams.get("field")! : "all";
  const scoped = (expr: string) => (field === "all" ? expr : `${field}: (${expr})`);
  const match = scoped(buildMatch(tokens, mode));

  const conds: string[] = ["docs MATCH ?1"];
  const binds: (string | number)[] = [match];
  for (const col of ["type", "book", "person"] as const) {
    const vals = (url.searchParams.get(col) ?? "")
      .split(",").map((s) => s.trim()).filter(Boolean)
      .filter((v) => (col === "type" ? TYPES.has(v) : SLUG_OK.test(v)));
    if (vals.length) {
      const placeholders = vals.map((v) => { binds.push(v); return `?${binds.length}`; }).join(",");
      conds.push(`${col} IN (${placeholders})`);
    }
  }

  const offset = Math.max(0, Math.min(2000, parseInt(url.searchParams.get("offset") ?? "0", 10) || 0));
  const sort = url.searchParams.get("sort") === "death" ? "death" : "relevance";
  const orderBy = sort === "death" ? "(pm.death_year IS NULL), pm.death_year ASC" : "rank";

  // fetch one extra row to know whether a next page exists, without a COUNT(*)
  const limitBind = binds.length + 1;
  const offsetBind = binds.length + 2;

  // snippet() takes a column INDEX, not a name (0=title, 1=text — see the
  // fts5(title, text, ...) column order in gen-search-index.ts) — a
  // title-only search should quote the title back, not an unrelated body
  // snippet that happens to share no words with what was actually matched.
  const snippetCol = field === "title" ? 0 : 1;
  const sql = `SELECT docs.type, docs.url, docs.display_title AS title,
                snippet(docs, ${snippetCol}, '<mark>', '</mark>', '…', 16) AS snippet,
                pm.name AS person_name, pm.death_year AS death_year
         FROM docs LEFT JOIN person_meta pm ON pm.slug = docs.person
         WHERE ${conds.join(" AND ")}
         ORDER BY ${orderBy}
         LIMIT ?${limitBind} OFFSET ?${offsetBind}`;
  try {
    let { results } = await db.prepare(sql).bind(...binds, PAGE_SIZE + 1, offset).all();
    // strict "all words" found nothing — a single mistyped/missing word
    // shouldn't dead-end the search; retry loosened to "any word" and say so,
    // rather than silently switching modes or just showing "no results"
    let approximate = false;
    if (results.length === 0 && mode === "all") {
      const orBinds = [scoped(buildMatch(tokens, "any")), ...binds.slice(1)];
      ({ results } = await db.prepare(sql).bind(...orBinds, PAGE_SIZE + 1, offset).all());
      approximate = results.length > 0;
    }
    const hasMore = results.length > PAGE_SIZE;
    return json({ hits: results.slice(0, PAGE_SIZE), hasMore, approximate });
  } catch {
    // index not loaded yet (fresh DB) or malformed MATCH — treat as no results
    return json({ hits: [], hasMore: false });
  }
};
