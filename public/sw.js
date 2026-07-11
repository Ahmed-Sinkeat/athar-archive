// ponytail: cache-first only for URLs the user explicitly downloaded via
// downloads.ts (aa-downloads cache) — everything else is untouched, so this
// never accidentally makes the rest of the site stale offline.
const CACHE_NAME = "aa-downloads";

self.addEventListener("install", () => self.skipWaiting());
self.addEventListener("activate", (e) => e.waitUntil(self.clients.claim()));

// Media needs Range support: answering a Range request with a full 200 from
// the Cache API makes browsers kill playback. Serve cached audio by slicing
// the stored body into a proper 206; cache miss falls through to network.
async function mediaResponse(request) {
  const cached = await (await caches.open(CACHE_NAME)).match(request.url);
  if (!cached) return fetch(request);
  const range = request.headers.get("range");
  if (!range) return cached;
  const buf = await cached.arrayBuffer();
  const m = /bytes=(\d+)-(\d+)?/.exec(range);
  const start = m ? Number(m[1]) : 0;
  const end = m && m[2] ? Math.min(Number(m[2]), buf.byteLength - 1) : buf.byteLength - 1;
  return new Response(buf.slice(start, end + 1), {
    status: 206,
    headers: {
      "Content-Type": cached.headers.get("Content-Type") || "audio/ogg",
      "Content-Range": `bytes ${start}-${end}/${buf.byteLength}`,
      "Content-Length": String(end - start + 1),
      "Accept-Ranges": "bytes",
    },
  });
}

self.addEventListener("fetch", (event) => {
  if (event.request.method !== "GET") return;
  if (event.request.headers.has("range") || event.request.destination === "audio" || event.request.destination === "video") {
    event.respondWith(mediaResponse(event.request));
    return;
  }
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
