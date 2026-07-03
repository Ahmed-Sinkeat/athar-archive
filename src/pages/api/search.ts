// On-demand search endpoint backed by the D1 FTS5 index (SEARCH binding,
// built by scripts/gen-search-index.ts). Query params: q (required),
// type / book / person (optional scope filters).
import type { APIRoute } from "astro";
import { normalizeArabic } from "../../lib/ar-normalize.js";

export const prerender = false;

const TYPES = new Set(["quran", "book", "poem", "article", "question", "term", "person"]);
const SLUG_OK = /^[a-z0-9-]+$/;

interface D1Like {
  prepare(sql: string): { bind(...v: string[]): { all(): Promise<{ results: unknown[] }> } };
}

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "public, max-age=300" },
  });

export const GET: APIRoute = async ({ url }) => {
  const raw = (url.searchParams.get("q") ?? "").slice(0, 200);
  const tokens = normalizeArabic(raw).split(/\s+/).filter(Boolean).slice(0, 12);
  if (tokens.length === 0) return json({ hits: [] });

  // quote every token (neutralizes FTS5 query operators); last token gets *
  // so typing feels incremental ("الصلا" already matches "الصلاة")
  const match = tokens
    .map((t, i) => `"${t.replaceAll('"', "")}"${i === tokens.length - 1 ? "*" : ""}`)
    .join(" ");

  const conds: string[] = ["docs MATCH ?1"];
  const binds: string[] = [match];
  for (const col of ["type", "book", "person"] as const) {
    const v = url.searchParams.get(col) ?? "";
    if (col === "type" ? TYPES.has(v) : SLUG_OK.test(v)) {
      binds.push(v);
      conds.push(`${col} = ?${binds.length}`);
    }
  }

  const { env } = await import("cloudflare:workers");
  const db = (env as unknown as { SEARCH?: D1Like }).SEARCH;
  if (!db) return json({ hits: [], error: "search unavailable" }, 503);

  try {
    const { results } = await db
      .prepare(
        `SELECT type, url, display_title AS title,
                snippet(docs, 1, '<mark>', '</mark>', '…', 16) AS snippet
         FROM docs WHERE ${conds.join(" AND ")} ORDER BY rank LIMIT 20`,
      )
      .bind(...binds)
      .all();
    return json({ hits: results });
  } catch {
    // index not loaded yet (fresh DB) or malformed MATCH — treat as no results
    return json({ hits: [] });
  }
};
