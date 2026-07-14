// Client-side "download for offline reading" — books/poems/surahs, cached via
// the Cache API and served offline by sw.js. No server changes: the URL list
// for a multi-page book/poem is read straight off the TOC already rendered on
// its landing page, so there's nothing new to generate or keep in sync.
const CACHE_NAME = "aa-downloads";
const MANIFEST_KEY = "aa-downloads-manifest";

interface DownloadEntry {
  kind: string;
  id: string;
  title: string;
  urls: string[];
  bytes: number;
  ts: number;
  path?: string; // landing page it was downloaded from (older manifests lack it)
}

const KIND_LABEL: Record<string, string> = { book: "كتاب", poem: "منظومة", quran: "سورة" };

function keyOf(kind: string, id: string): string {
  return `${kind}:${id}`;
}

function getManifest(): Record<string, DownloadEntry> {
  try {
    return JSON.parse(localStorage.getItem(MANIFEST_KEY) || "{}");
  } catch {
    return {};
  }
}

function saveManifest(m: Record<string, DownloadEntry>) {
  localStorage.setItem(MANIFEST_KEY, JSON.stringify(m));
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

// Same-book/poem chapter links are already on the landing page's own TOC —
// reuse them instead of asking the server for a manifest. No TOC (single-page
// book/poem, or a Quran surah page) → just download the landing page itself.
// `doc` may be a DOMParser document of a NOT-currently-open landing page —
// that's how the quick download buttons on list rows work without navigating.
function collectUrls(doc: Document = document, pagePath: string = location.pathname): string[] {
  const links = doc.querySelectorAll<HTMLAnchorElement>(".toc-box a[href], .card-grid a[href]");
  const urls = new Set<string>([pagePath]); // the landing page itself is part of the download
  for (const a of links) {
    const href = a.getAttribute("href");
    if (href && href.startsWith("/")) urls.add(href);
  }
  // audio on this page (single track or lesson-series playlist) — cross-origin
  // R2 URLs, fetchable because the bucket allows this origin via CORS
  for (const el of doc.querySelectorAll<HTMLElement>("[data-audio]")) {
    const tracks = el.dataset.audioTracks;
    if (tracks) {
      try { for (const t of JSON.parse(tracks)) if (t?.url) urls.add(t.url); } catch { /* malformed JSON → skip */ }
    } else {
      const src = el.querySelector<HTMLSourceElement>("[data-audio-source]")?.getAttribute("src");
      if (src) urls.add(src);
    }
  }
  return [...urls];
}

// exact per-entity download sizes, computed at build time (gen-dl-sizes.mjs)
// — one small JSON instead of a HEAD probe per page
let sizesPromise: Promise<Record<string, number>> | null = null;
function getSizes(): Promise<Record<string, number>> {
  sizesPromise ??= fetch("/dl-sizes.json").then((r) => (r.ok ? r.json() : {})).catch(() => ({}));
  return sizesPromise;
}

function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);
}

async function startDownload(btn: HTMLElement) {
  const kind = btn.dataset.dlKind!;
  const id = btn.dataset.dlId!;
  const title = btn.dataset.dlTitle || document.title;
  const path = btn.dataset.dlPath || location.pathname;
  const label = btn.querySelector("[data-dl-label]");
  const sizeEl = btn.querySelector<HTMLElement>("[data-dl-size]");
  btn.setAttribute("data-dl-busy", "1");
  // quick download from a list row: the landing page isn't open, so fetch it
  // and read its TOC/audio from the parsed HTML instead of the live DOM
  let urls: string[];
  if (path !== location.pathname) {
    try {
      const res = await fetch(path);
      if (!res.ok) throw new Error(String(res.status));
      urls = collectUrls(new DOMParser().parseFromString(await res.text(), "text/html"), path);
    } catch {
      btn.removeAttribute("data-dl-busy");
      if (sizeEl) sizeEl.textContent = "تعذّر";
      return;
    }
  } else {
    urls = collectUrls();
  }
  const cache = await caches.open(CACHE_NAME);
  let bytes = 0;
  let done = 0;
  // hashed JS/CSS the downloaded pages reference — without them an offline
  // page renders but no script boots (no audio player, no TOC). Scraped from
  // each page's HTML as it's downloaded.
  // ponytail: misses fonts and dynamically-imported chunks; add if offline
  // pages visibly break without them.
  const assets = new Set<string>();
  // ponytail: sequential fetch — simple and gentle on the Worker for big
  // books; parallelize with a concurrency cap if downloads feel too slow.
  for (const url of urls) {
    try {
      const res = await fetch(url);
      if (res.ok) {
        const clone = res.clone();
        await cache.put(url, res);
        const blob = await clone.blob();
        bytes += blob.size;
        if (blob.type.includes("text/html")) {
          for (const m of (await blob.text()).matchAll(/\/_astro\/[\w.@-]+\.\w+/g)) assets.add(m[0]);
        }
      }
    } catch {
      // one failed page shouldn't abort the whole download
    }
    done++;
    if (label) label.textContent = `جارٍ التنزيل… ${done}/${urls.length}`;
    if (sizeEl) sizeEl.textContent = `${done}/${urls.length}`;
  }
  for (const url of assets) {
    try {
      const res = await fetch(url);
      if (res.ok) {
        bytes += (await res.clone().blob()).size;
        await cache.put(url, res);
        urls.push(url); // recorded in the manifest so removeDownload cleans them up
      }
    } catch {
      // a missing asset shouldn't abort the download
    }
  }
  const manifest = getManifest();
  manifest[keyOf(kind, id)] = { kind, id, title, urls, bytes, ts: Date.now(), path };
  saveManifest(manifest);
  btn.removeAttribute("data-dl-busy");
  renderDownloadButton(btn);
  renderDownloadsList();
}

async function removeDownload(kind: string, id: string) {
  const manifest = getManifest();
  const entry = manifest[keyOf(kind, id)];
  if (!entry) return;
  const cache = await caches.open(CACHE_NAME);
  for (const url of entry.urls) await cache.delete(url);
  delete manifest[keyOf(kind, id)];
  saveManifest(manifest);
  renderDownloadsList();
  document
    .querySelectorAll<HTMLElement>(`[data-action="download:toggle"][data-dl-kind="${kind}"][data-dl-id="${id}"]`)
    .forEach(renderDownloadButton);
}

// pre-download size estimate: HEAD each page (cheap — headers only, no body)
// and sum Content-Length. Cached in-memory per kind:id so re-opening the
// popover or navigating back doesn't re-probe the same book/poem/surah.
const sizeEstimateCache = new Map<string, number>();
async function estimateSize(kind: string, id: string, urls: string[]): Promise<number | null> {
  const key = keyOf(kind, id);
  if (sizeEstimateCache.has(key)) return sizeEstimateCache.get(key)!;
  try {
    const lengths = await Promise.all(
      urls.map((u) => fetch(u, { method: "HEAD" }).then((r) => Number(r.headers.get("content-length")) || 0)),
    );
    const total = lengths.reduce((a, b) => a + b, 0);
    sizeEstimateCache.set(key, total);
    return total;
  } catch {
    return null; // offline/blocked HEAD — fall back to the page-count label
  }
}

function renderDownloadButton(btn: HTMLElement) {
  const kind = btn.dataset.dlKind!;
  const id = btn.dataset.dlId!;
  const key = keyOf(kind, id);
  const entry = getManifest()[key];
  const label = btn.querySelector("[data-dl-label]");
  const sizeEl = btn.querySelector<HTMLElement>("[data-dl-size]");
  if (entry) {
    btn.setAttribute("aria-pressed", "true");
    if (label) label.textContent = `متوفر دون اتصال (${formatSize(entry.bytes)}) — إزالة`;
    if (sizeEl) sizeEl.textContent = "✓";
    return;
  }
  btn.setAttribute("aria-pressed", "false");
  // exact size from the build-time manifest; per-page HEAD probing stays as a
  // fallback for anything the manifest misses (e.g. Quran surahs)
  getSizes().then((sizes) => {
    if (btn.getAttribute("aria-pressed") === "true" || btn.hasAttribute("data-dl-busy")) return;
    const bytes = sizes[key];
    if (bytes != null) {
      if (label) label.textContent = `تنزيل للقراءة دون اتصال (${formatSize(bytes)})`;
      if (sizeEl) sizeEl.textContent = formatSize(bytes);
      return;
    }
    if (sizeEl) { sizeEl.textContent = ""; return; } // compact rows: no probe storm
    const urls = collectUrls();
    const cached = sizeEstimateCache.get(key);
    if (label) {
      label.textContent = cached != null
        ? `تنزيل للقراءة دون اتصال (${formatSize(cached)})`
        : urls.length > 1 ? `تنزيل للقراءة دون اتصال (${urls.length} صفحة)` : "تنزيل للقراءة دون اتصال";
    }
    if (cached == null && !btn.hasAttribute("data-dl-busy")) {
      estimateSize(kind, id, urls).then((bytes2) => {
        if (bytes2 == null || btn.getAttribute("aria-pressed") === "true") return;
        const l = btn.querySelector("[data-dl-label]");
        if (l) l.textContent = `تنزيل للقراءة دون اتصال (${formatSize(bytes2)})`;
      });
    }
  });
}

function renderDownloadsList() {
  const list = document.querySelector<HTMLElement>("[data-downloads-list]");
  const empty = document.querySelector<HTMLElement>("[data-downloads-empty]");
  const totalEl = document.querySelector<HTMLElement>("[data-downloads-total]");
  if (!list) return;
  const entries = Object.values(getManifest()).sort((a, b) => b.ts - a.ts);
  list.innerHTML = entries
    .map((e) => {
      const title = e.path
        ? `<a class="dl-row-title" href="${escapeHtml(e.path)}">${escapeHtml(e.title)}</a>`
        : `<div class="dl-row-title">${escapeHtml(e.title)}</div>`;
      const date = new Date(e.ts).toLocaleDateString("ar", { year: "numeric", month: "long", day: "numeric" });
      return `<div class="dl-row">${title}<div class="dl-row-meta"><span class="faint">${KIND_LABEL[e.kind] ?? e.kind} · ${formatSize(e.bytes)} · ${date}</span><button type="button" class="dl-row-remove" data-action="downloads:remove" data-dl-kind="${escapeHtml(e.kind)}" data-dl-id="${escapeHtml(e.id)}">إزالة</button></div></div>`;
    })
    .join("");
  if (empty) empty.hidden = entries.length > 0;
  const totalBytes = entries.reduce((s, e) => s + e.bytes, 0);
  if (totalEl) totalEl.textContent = entries.length > 0 ? `· ${formatSize(totalBytes)}` : "";
}

// Capture phase: these buttons sit inside a card <a href>, and ClientRouter's
// own click listener (Base.astro, bubble phase) decides whether to soft-navigate
// by checking ev.defaultPrevented — since it's registered earlier in the page
// than this module script, its bubble-phase handler would otherwise run BEFORE
// this one and already navigate into the card. Capture always runs first.
document.addEventListener("click", (e) => {
  const target = e.target as HTMLElement;

  const toggleBtn = target.closest<HTMLElement>('[data-action="download:toggle"]');
  if (toggleBtn) {
    e.preventDefault();
    if (toggleBtn.hasAttribute("data-dl-busy")) return;
    const kind = toggleBtn.dataset.dlKind!;
    const id = toggleBtn.dataset.dlId!;
    const entry = getManifest()[keyOf(kind, id)];
    if (entry) {
      if (!confirm(`إزالة «${entry.title}» من التنزيلات؟`)) return;
      removeDownload(kind, id).then(() => renderDownloadButton(toggleBtn));
    } else startDownload(toggleBtn);
    return;
  }

  const removeBtn = target.closest<HTMLElement>('[data-action="downloads:remove"]');
  if (removeBtn) {
    e.preventDefault();
    const entry = getManifest()[keyOf(removeBtn.dataset.dlKind!, removeBtn.dataset.dlId!)];
    if (entry && !confirm(`إزالة «${entry.title}» من التنزيلات؟`)) return;
    removeDownload(removeBtn.dataset.dlKind!, removeBtn.dataset.dlId!);
    return;
  }
}, true);

function onPage() {
  document.querySelectorAll<HTMLElement>('[data-action="download:toggle"]').forEach(renderDownloadButton);
  renderDownloadsList();
}
onPage();
document.addEventListener("astro:page-load", onPage);

export {};
