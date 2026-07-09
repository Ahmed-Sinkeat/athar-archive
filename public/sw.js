// ponytail: cache-first only for URLs the user explicitly downloaded via
// downloads.ts (aa-downloads cache) — everything else is untouched, so this
// never accidentally makes the rest of the site stale offline.
const CACHE_NAME = "aa-downloads";

self.addEventListener("install", () => self.skipWaiting());
self.addEventListener("activate", (e) => e.waitUntil(self.clients.claim()));

self.addEventListener("fetch", (event) => {
  if (event.request.method !== "GET") return;
  event.respondWith(
    caches.open(CACHE_NAME).then(async (cache) => {
      const cached = await cache.match(event.request);
      if (cached) return cached;
      try {
        return await fetch(event.request);
      } catch (err) {
        if (cached) return cached;
        throw err;
      }
    }),
  );
});
