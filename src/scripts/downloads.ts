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
// book/poem, or a Quran surah page) → just download the current page.
function collectUrls(): string[] {
  const links = document.querySelectorAll<HTMLAnchorElement>(".toc-box a[href], .card-grid a[href]");
  const urls = new Set<string>();
  for (const a of links) {
    const href = a.getAttribute("href");
    if (href && href.startsWith("/")) urls.add(href);
  }
  if (urls.size === 0) urls.add(location.pathname);
  // audio on this page (single track or lesson-series playlist) — cross-origin
  // R2 URLs, fetchable because the bucket allows this origin via CORS
  for (const el of document.querySelectorAll<HTMLElement>("[data-audio]")) {
    const tracks = el.dataset.audioTracks;
    if (tracks) {
      try { for (const t of JSON.parse(tracks)) if (t?.url) urls.add(t.url); } catch { /* malformed JSON → skip */ }
    } else {
      const src = el.querySelector<HTMLSourceElement>("[data-audio-source]")?.src;
      if (src) urls.add(src);
    }
  }
  return [...urls];
}

function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);
}

async function startDownload(btn: HTMLElement) {
  const kind = btn.dataset.dlKind!;
  const id = btn.dataset.dlId!;
  const title = btn.dataset.dlTitle || document.title;
  const urls = collectUrls();
  const label = btn.querySelector("[data-dl-label]");
  btn.setAttribute("data-dl-busy", "1");
  const cache = await caches.open(CACHE_NAME);
  let bytes = 0;
  let done = 0;
  // ponytail: sequential fetch — simple and gentle on the Worker for big
  // books; parallelize with a concurrency cap if downloads feel too slow.
  for (const url of urls) {
    try {
      const res = await fetch(url);
      if (res.ok) {
        bytes += (await res.clone().blob()).size;
        await cache.put(url, res);
      }
    } catch {
      // one failed page shouldn't abort the whole download
    }
    done++;
    if (label) label.textContent = `جارٍ التنزيل… ${done}/${urls.length}`;
  }
  const manifest = getManifest();
  manifest[keyOf(kind, id)] = { kind, id, title, urls, bytes, ts: Date.now(), path: location.pathname };
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
  const entry = getManifest()[keyOf(kind, id)];
  const label = btn.querySelector("[data-dl-label]");
  if (entry) {
    btn.setAttribute("aria-pressed", "true");
    if (label) label.textContent = `متوفر دون اتصال (${formatSize(entry.bytes)}) — إزالة`;
    return;
  }
  btn.setAttribute("aria-pressed", "false");
  const urls = collectUrls();
  const cached = sizeEstimateCache.get(keyOf(kind, id));
  if (label) {
    label.textContent = cached != null
      ? `تنزيل للقراءة دون اتصال (${formatSize(cached)})`
      : urls.length > 1 ? `تنزيل للقراءة دون اتصال (${urls.length} صفحة)` : "تنزيل للقراءة دون اتصال";
  }
  if (cached == null && !btn.hasAttribute("data-dl-busy")) {
    estimateSize(kind, id, urls).then((bytes) => {
      if (bytes == null || btn.getAttribute("aria-pressed") === "true") return;
      const l = btn.querySelector("[data-dl-label]");
      if (l) l.textContent = `تنزيل للقراءة دون اتصال (${formatSize(bytes)})`;
    });
  }
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
});

function onPage() {
  document.querySelectorAll<HTMLElement>('[data-action="download:toggle"]').forEach(renderDownloadButton);
  renderDownloadsList();
}
onPage();
document.addEventListener("astro:page-load", onPage);

if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("/sw.js").catch(() => {});
}

export {};
