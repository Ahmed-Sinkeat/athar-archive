// search.arthurarchive.com: the search API on its own (see src/lib/search-handler.ts), plus the
// app's anonymous usage counts (POST /api/hit → Analytics Engine; no IP, no device id is stored).
import { searchResponse } from "../../src/lib/search-handler.js";

type Env = { HITS?: { writeDataPoint(p: { blobs: string[]; indexes: string[] }): void } };
const EVENTS = new Set(["open", "book", "search", "download", "audio"]);
const ok = (s: string | null) => s !== null && s.length <= 100;

export default {
  fetch(request: Request, env: Env): Promise<Response> | Response {
    const url = new URL(request.url);
    if (request.method === "POST" && url.pathname === "/api/hit") {
      const [e, d, v] = ["e", "d", "v"].map((k) => url.searchParams.get(k) ?? "");
      if (!EVENTS.has(e) || !ok(d) || !ok(v)) return new Response(null, { status: 400 });
      env.HITS?.writeDataPoint({ blobs: [e, d, v], indexes: [e] });
      return new Response(null, { status: 204 });
    }
    if (request.method !== "GET") return new Response(null, { status: 405 });
    return searchResponse(url);
  },
};
