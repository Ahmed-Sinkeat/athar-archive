import { defineMiddleware } from "astro:middleware";

// On-demand reading routes (book chapters) are edge-cached via the Workers Cache
// API and carry the same security headers as static pages. Setting Cache-Control
// alone does NOT populate Cloudflare's cache for a Worker response (cf-cache-status
// stays DYNAMIC) — the Cache API is the documented path. Static asset responses are
// untouched (they get _headers + their own caching).
// /book/<slug>/<chapter> and /series/<slug>/<lesson> — the on-demand reading routes.
const READING = /^\/(?:book|series)\/[^/]+\/[^/]+\/?$/;

// ponytail: 1-day s-maxage, not a year — edit→redeploy cache invalidation isn't
// wired yet (plan risk: cache-versioning/purge). Stale content self-heals within a
// day; switch to a long TTL + purge-on-deploy when that lands.
const CACHE_CONTROL = "public, s-maxage=86400, stale-while-revalidate=604800";

// _headers only covers static asset responses, so the build also writes the same
// header set to _headers.json; read it once per isolate and apply to on-demand pages.
let headersPromise: Promise<Record<string, string>> | null = null;
function securityHeaders(): Promise<Record<string, string>> {
  return (headersPromise ??= (async () => {
    try {
      const { env } = await import("cloudflare:workers");
      const res = await (env as { ASSETS: { fetch(u: URL): Promise<Response> } }).ASSETS.fetch(
        new URL("/_headers.json", "https://assets.local"),
      );
      return res.ok ? ((await res.json()) as Record<string, string>) : {};
    } catch {
      return {}; // dev (no cloudflare:workers / no ASSETS) — astro dev doesn't apply _headers either
    }
  })());
}

export const onRequest = defineMiddleware(async (ctx, next) => {
  if (ctx.request.method !== "GET" || !READING.test(ctx.url.pathname)) return next();

  const cache = typeof caches !== "undefined" ? (caches as { default: Cache }).default : null;
  const key = cache ? new Request(ctx.url.toString(), { method: "GET" }) : null;
  if (cache && key) {
    const hit = await cache.match(key);
    // Cache API responses have immutable headers; Astro's i18n finalization mutates
    // the returned response, so hand back a fresh mutable copy.
    if (hit) return new Response(hit.body, hit);
  }

  const res = await next();
  if (res.status !== 200) return res; // don't cache rewritten 404s etc.

  for (const [k, v] of Object.entries(await securityHeaders())) res.headers.set(k, v);
  res.headers.set("Cache-Control", CACHE_CONTROL);

  if (cache && key) ctx.locals.cfContext?.waitUntil(cache.put(key, res.clone()));
  return res;
});
