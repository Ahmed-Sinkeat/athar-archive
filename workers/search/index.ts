// search.arthurarchive.com: the search API on its own (see src/lib/search-handler.ts), plus the
// app's anonymous usage counts (POST /api/hit → Analytics Engine; no IP, no device id is stored).
import { searchResponse } from "../../src/lib/search-handler.js";

type Env = { HITS?: { writeDataPoint(p: { blobs: string[]; indexes: string[] }): void } };
const EVENTS = new Set(["open", "book", "search", "download", "audio"]);
const ok = (s: string | null) => s !== null && s.length <= 100;

export default {
  async fetch(request: Request, env: Env, ctx: { waitUntil(p: Promise<unknown>): void }): Promise<Response> {
    const url = new URL(request.url);
    if (request.method === "POST" && url.pathname === "/api/hit") {
      const [e, d, v] = ["e", "d", "v"].map((k) => url.searchParams.get(k) ?? "");
      if (!EVENTS.has(e) || !ok(d) || !ok(v)) return new Response(null, { status: 400 });
      env.HITS?.writeDataPoint({ blobs: [e, d, v], indexes: [e] });
      return new Response(null, { status: 204 });
    }
    if (request.method !== "GET") return new Response(null, { status: 405 });
    // Worker responses skip Cloudflare's cache unless put there: a repeat query (same URL) is
    // answered from this data center's cache, honouring the handler's cache-control (30 min).
    const cache = (caches as unknown as { default: Cache }).default;
    const hit = await cache.match(request);
    if (hit) {
      // The stored copy carries the first request's timing; say it came from the cache instead.
      const r = new Response(hit.body, hit);
      r.headers.set("server-timing", "cache;desc=hit");
      return r;
    }
    const res = await searchResponse(url);
    if (res.status === 200) ctx.waitUntil(cache.put(request, res.clone()));
    return res;
  },
};
