// عدد الزوار — the footer's visitor counter. One row in the existing SEARCH D1
// (no new binding to provision); the CREATE runs inline so there's no migration
// step to remember on first deploy — it's a no-op on every call after the first.
//
// Counting granularity is a SESSION, not a page view: the client (Base.astro)
// caches the returned number in sessionStorage and only POSTs when that cache is
// empty. So this is one write per visitor per session, not one per page — which
// is what «زائر» should mean, and keeps a vanity counter from eating D1's free
// 100k-writes/day budget that the search index also draws on.
export const prerender = false;

import type { APIRoute } from "astro";

interface D1Like {
  prepare(sql: string): { first<T = unknown>(col: string): Promise<T | null> };
  batch(stmts: unknown[]): Promise<unknown>;
}

const TABLE = "CREATE TABLE IF NOT EXISTS site_counter (key TEXT PRIMARY KEY, n INTEGER NOT NULL DEFAULT 0)";
const BUMP =
  "INSERT INTO site_counter (key, n) VALUES ('visits', 1) ON CONFLICT(key) DO UPDATE SET n = n + 1 RETURNING n";

const json = (visits: number | null) =>
  new Response(JSON.stringify(visits === null ? {} : { visits }), {
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });

export const POST: APIRoute = async () => {
  try {
    const { env } = await import("cloudflare:workers");
    const db = (env as unknown as { SEARCH?: D1Like }).SEARCH;
    if (!db) return json(null);
    // batched so table-creation and the increment are one round trip
    const [, bumped] = (await db.batch([db.prepare(TABLE), db.prepare(BUMP)])) as { results?: { n: number }[] }[];
    return json(bumped?.results?.[0]?.n ?? null);
  } catch {
    // a counter is decoration — never surface its failure to the reader
    return json(null);
  }
};
