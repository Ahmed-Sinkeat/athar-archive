// Serves the aa-downloads cache (pages + audio + their JS/CSS, stored by
// downloads.ts and opportunistically by the fetch handlers below). HTML pages
// are network-first (see htmlNetworkFirst) so a deploy is visible on the very
// next online open; CSS/JS/images/fonts are stale-while-revalidate for an
// instant open. Both fall back to whatever's cached when offline.
const CACHE_NAME = "aa-downloads";

// App shell: the hub/nav pages, precached so the installed app opens
// instantly and works offline right after installing — separate cache from
// aa-downloads (that one is for books/audio the user explicitly chose to
// save; this one is chrome, not content, and is fine to silently evict/
// refresh). CSS/JS need no entry here: /_astro/* already ships immutable
// Cache-Control, so the browser's own HTTP cache handles those for free.
// ~2.8MB across 13 pages — same reasoning as QURAN_URLS below: NOT part of
// the unconditional install step (every visitor, installed or not, would pay
// for it), only triggered once display-mode is standalone. A casual browser
// visitor still gets each shell page cached the normal way, on-demand, the
// first time they actually visit it (see shellCacheFirst).
const SHELL_CACHE = "aa-shell";
const SHELL_URLS = [
  "/", "/books", "/quran", "/hadith", "/poems", "/people", "/articles",
  "/questions", "/benefits", "/subjects", "/search", "/downloads", "/about",
  "/dl-sizes.json", // per-entity download sizes for the list-row buttons
  "/fonts/fonts.css", // self-hosted font faces — woff2s cached on use (SWR)
];
async function precacheShell() {
  const cache = await caches.open(SHELL_CACHE);
  // NOT cache.match("/") — "/" is the app's start_url, so it's already cached
  // the instant anyone just opens the app via the normal cache-first-then-
  // refresh path in shellCacheFirst, regardless of whether this bulk precache
  // ever ran. That made the check always true and silently skipped every
  // other shell page for basically everyone. A marker only this function ever
  // writes is the only reliable "already done" signal.
  if (await cache.match("/__shell-precached__")) return;
  await Promise.allSettled(SHELL_URLS.map((u) => cache.add(u)));
  await cache.put("/__shell-precached__", new Response(""));
}

// Web fonts (Google Fonts CSS + woff2) — cache-first in their own cache:
// they're versioned URLs that basically never change, they're render-critical
// for Arabic, and without this they're the one thing still needing network
// on an otherwise fully-offline app.
const FONT_CACHE = "aa-fonts";

// The full mus-haf (114 surah pages), precached into aa-downloads — same
// cache downloads.ts writes to — so the whole Quran reads offline with no
// explicit "download" needed. NOT part of the install step: that runs for
// every visitor, installed or not, and this is several MB. Only triggered by
// a postMessage from Base.astro, sent once display-mode is standalone (see
// there for the reasoning). Each surah is its own single-page route (no
// per-surah TOC), unlike multi-chapter books.
const QURAN_URLS = ["/quran", "/quran/mushaf", ...Array.from({ length: 114 }, (_, i) => `/quran/${i + 1}`)];

// Mirrors downloads.ts's own asset-scrape: a precached page is useless if its
// hashed JS/CSS never lands in any cache for an offline-from-first-open visit.
async function precacheQuran() {
  const cache = await caches.open(CACHE_NAME);
  // NOT cache.match("/quran/1") — this cache also holds anything the user
  // opens normally (htmlNetworkFirst caches every visited page here too), so
  // if they'd simply read Al-Fatiha first — likely, it's the first surah —
  // the check would already read true despite surahs 2-114 never having run
  // through this function. Same class of bug as precacheShell's marker fix.
  if (await cache.match("/__quran-precached__")) return;
  const assets = new Set();
  await Promise.allSettled(QURAN_URLS.map(async (url) => {
    const res = await fetch(url);
    if (!res.ok) return;
    const text = await res.clone().text();
    await cache.put(url, res);
    for (const m of text.matchAll(/\/_astro\/[\w.@-]+\.\w+/g)) assets.add(m[0]);
  }));
  await Promise.allSettled([...assets].map(async (u) => {
    const res = await fetch(u);
    if (res.ok) await cache.put(u, res);
  }));
  await cache.put("/__quran-precached__", new Response(""));
}

self.addEventListener("install", (e) => {
  // tiny, and needed by ANY visitor's offline fallback (see htmlNetworkFirst)
  // — everything else below is app-only, see precacheShell/precacheQuran.
  e.waitUntil(caches.open(CACHE_NAME).then((c) => c.add("/offline.html")));
  self.skipWaiting();
});
self.addEventListener("activate", (e) => e.waitUntil(self.clients.claim()));
self.addEventListener("message", (e) => {
  if (e.data?.type === "precache-app") e.waitUntil(Promise.all([precacheShell(), precacheQuran()]));
});

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
  // opaque bodies are unreadable — slicing one yields an empty broken 206;
  // treat it like a miss and let the network serve it
  if (!cached || cached.type === "opaque") return fetch(request);
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

async function fontCacheFirst(request) {
  const cache = await caches.open(FONT_CACHE);
  const cached = await cache.match(request);
  if (cached) return cached;
  const res = await fetch(request);
  // stylesheet <link> fetches are no-cors → opaque (ok:false) but still cacheable
  if (res.ok || res.type === "opaque") cache.put(request, res.clone());
  return res;
}

// /api/* only: search results must be live — cache is an offline fallback
async function networkFirst(request) {
  const cache = await caches.open(CACHE_NAME);
  try {
    return await fetch(request);
  } catch (err) {
    const cached = await cache.match(request);
    if (cached) return cached;
    throw err;
  }
}

// HTML documents (top-level navigations) go network-first below — SWR served
// a stale page on every open until a SECOND visit, which meant a shipped fix
// (e.g. the <audio crossorigin> attribute for Android playback) stayed broken
// on a phone until a hard reload. Non-HTML assets keep stale-while-revalidate:
// instant + offline, and staleness there is harmless (/_astro/* is hashed and
// immutable anyway; images/fonts rarely change).
function cacheable(request) {
  return request.destination === "style" || request.destination === "script" ||
    request.destination === "image" || request.destination === "font"; // self-hosted /fonts/gf/*.woff2
}

// Explicitly downloaded pages (via downloads.ts) stay available offline since
// they're written straight into this same cache; network-first just means an
// online visit always gets the latest copy instead of a stuck one.
async function htmlNetworkFirst(request) {
  const cache = await caches.open(CACHE_NAME);
  try {
    const res = await fetch(request);
    if (res.ok) cache.put(request, res.clone());
    return res;
  } catch {
    return (await cache.match(request)) || cache.match("/offline.html");
  }
}
async function staleWhileRevalidate(event) {
  const request = event.request;
  const cache = await caches.open(CACHE_NAME);
  const cached = await cache.match(request);
  const refresh = fetch(request).then((res) => {
    if (res.ok && cacheable(request)) cache.put(request, res.clone());
    return res;
  }).catch(() => null);
  if (cached) {
    event.waitUntil(refresh); // keep the SW alive until the refresh lands
    return cached;
  }
  const res = await refresh;
  if (res && res.ok) return res;
  if (res) {
    // e.g. a redeploy deleted the hashed /_astro asset an offline-downloaded
    // page still references — the copy stored at download time keeps it working
    const fallback = await cache.match(request);
    return fallback || res;
  }
  throw new Error("offline and uncached: " + request.url);
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
  const url = new URL(req.url);
  if (url.hostname === "fonts.googleapis.com" || url.hostname === "fonts.gstatic.com") {
    event.respondWith(fontCacheFirst(req));
    return;
  }
  // other cross-origin traffic (GitHub API from /admin, analytics, …) is none
  // of our business — intercepting it only adds failure modes
  if (url.origin !== location.origin) return;
  if (url.pathname.startsWith("/api/")) {
    event.respondWith(networkFirst(req));
    return;
  }
  if (SHELL_URLS.includes(url.pathname.replace(/\/$/, "") || "/")) {
    event.respondWith(shellCacheFirst(req));
    return;
  }
  if (req.mode === "navigate") {
    event.respondWith(htmlNetworkFirst(req));
    return;
  }
  event.respondWith(staleWhileRevalidate(event));
});
