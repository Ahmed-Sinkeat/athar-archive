// Serves the aa-downloads cache (pages + audio + their JS/CSS, stored by
// downloads.ts). Pages/assets are network-first: online always gets the
// freshly deployed version, the cache is only a fallback for offline or for
// hashed assets deleted by a redeploy. Cache-first here caused stale pages
// pointing at dead /_astro bundles → broken player until a hard reload.
const CACHE_NAME = "aa-downloads";

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE_NAME).then((c) => c.add("/offline.html")));
  self.skipWaiting();
});
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

async function networkFirst(request) {
  const cache = await caches.open(CACHE_NAME);
  let res;
  try {
    res = await fetch(request);
  } catch (err) {
    const cached = await cache.match(request);
    if (cached) return cached;
    if (request.mode === "navigate") return cache.match("/offline.html");
    throw err;
  }
  if (!res.ok) {
    // e.g. a redeploy deleted the hashed /_astro asset an offline-downloaded
    // page still references — the copy stored at download time keeps it working
    const cached = await cache.match(request);
    if (cached) return cached;
  }
  return res;
}

self.addEventListener("fetch", (event) => {
  const req = event.request;
  if (req.method !== "GET") return;
  // audio by destination, Range header, or extension (a plain fetch() for the
  // blob download has destination "" and no Range — catch it by extension)
  if (req.headers.has("range") || req.destination === "audio" || req.destination === "video" || /\.(opus|mp3|m4a|ogg)(\?|$)/.test(req.url)) {
    event.respondWith(mediaResponse(req));
    return;
  }
  // other cross-origin traffic (GitHub API from /admin, analytics, …) is none
  // of our business — intercepting it only adds failure modes
  if (new URL(req.url).origin !== location.origin) return;
  event.respondWith(networkFirst(req));
});
