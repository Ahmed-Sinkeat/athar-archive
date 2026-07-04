import { defineMiddleware } from "astro:middleware";

// On-demand reading routes (book chapters) are edge-cached via the Workers Cache
// API and carry the same security headers as static pages. Setting Cache-Control
// alone does NOT populate Cloudflare's cache for a Worker response (cf-cache-status
// stays DYNAMIC) — the Cache API is the documented path. Static asset responses are
// untouched (they get _headers + their own caching).
// /book/<slug>/<chapter>, /series/<slug>/<lesson>, and /tafsir-frag/<surah>/<ayah>.html
// — the on-demand reading routes.
const READING = /^\/(?:book|series)\/[^/]+\/[^/]+\/?$|^\/tafsir-frag\/\d+\/\d+\.html$/;

// Cache key is versioned by build id (below), so a long TTL here is safe — a
// redeploy can't serve a stale entry, it just misses and repopulates.
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

  const headers = await securityHeaders();
  // Cache key carries the build id (query param, ignored by the route itself) so a
  // redeploy always misses old entries instead of serving HTML that references
  // now-deleted hashed /_astro/*.css files — this used to render totally unstyled
  // for up to a day after every redesign deploy.
  const cacheUrl = new URL(ctx.url);
  if (headers["X-Build-Id"]) cacheUrl.searchParams.set("__v", headers["X-Build-Id"]);

  // Never edge-cache in dev: the Cloudflare platform proxy defines `caches`, so the
  // 1-day render cache would otherwise mask edited content behind a server-side cache
  // that survives browser hard-refreshes (nothing shy of a dev-server restart clears it).
  const cache = (typeof caches !== "undefined" && !import.meta.env.DEV)
    ? (caches as unknown as { default: Cache }).default : null;
  const key = cache ? new Request(cacheUrl.toString(), { method: "GET" }) : null;
  if (cache && key) {
    const hit = await cache.match(key);
    // Cache API responses have immutable headers; Astro's i18n finalization mutates
    // the returned response, so hand back a fresh mutable copy.
    if (hit) return new Response(hit.body, hit);
  }

  const res = await next();
  if (res.status !== 200) return res; // don't cache rewritten 404s etc.

  for (const [k, v] of Object.entries(headers)) res.headers.set(k, v);
  res.headers.set("Cache-Control", CACHE_CONTROL);

  if (cache && key) ctx.locals.cfContext?.waitUntil(cache.put(key, res.clone()));
  return res;
});
