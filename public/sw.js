// Serves the aa-downloads cache (pages + audio + their JS/CSS, stored by
// downloads.ts). Pages/assets are network-first: online always gets the
// freshly deployed version, the cache is only a fallback for offline or for
// hashed assets deleted by a redeploy. Cache-first here caused stale pages
// pointing at dead /_astro bundles → broken player until a hard reload.
const CACHE_NAME = "aa-downloads";

// App shell: the hub/nav pages, precached at install so the app opens
// instantly and works offline right after installing — separate cache from
// aa-downloads (that one is for books/audio the user explicitly chose to
// save; this one is chrome, not content, and is fine to silently evict/
// refresh). CSS/JS need no entry here: /_astro/* already ships immutable
// Cache-Control, so the browser's own HTTP cache handles those for free.
const SHELL_CACHE = "aa-shell";
const SHELL_URLS = [
  "/", "/books", "/quran", "/hadith", "/poems", "/people", "/articles",
  "/questions", "/benefits", "/subjects", "/search", "/downloads", "/about",
];

self.addEventListener("install", (e) => {
  e.waitUntil(Promise.all([
    caches.open(CACHE_NAME).then((c) => c.add("/offline.html")),
    // best-effort: one page failing to precache (offline install, a route
    // renamed) shouldn't block the rest from caching
    caches.open(SHELL_CACHE).then((c) =>
      Promise.allSettled(SHELL_URLS.map((u) => c.add(u))),
    ),
  ]));
  self.skipWaiting();
});
self.addEventListener("activate", (e) => e.waitUntil(self.clients.claim()));

// Shell pages: cache-first for an instant open, then silently refetch and
// update the cache for next time — the count/list on these hub pages can be
// a visit stale, never absent.
async function shellCacheFirst(request) {
  const cache = await caches.open(SHELL_CACHE);
  const cached = await cache.match(request);
  const refresh = fetch(request).then((res) => {
    if (res.ok) cache.put(request, res.clone());
    return res;
  }).catch(() => null);
  return cached || (await refresh) || cache.match("/offline.html");
}

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
  const url = new URL(req.url);
  if (url.origin !== location.origin) return;
  if (SHELL_URLS.includes(url.pathname.replace(/\/$/, "") || "/")) {
    event.respondWith(shellCacheFirst(req));
    return;
  }
  event.respondWith(networkFirst(req));
});
