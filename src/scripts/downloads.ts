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

const KIND_LABEL: Record<string, string> = { book: "كتاب", poem: "منظومة", quran: "سورة", article: "مقالة" };

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
  window.dispatchEvent(new Event("aa:downloads-changed")); // list rows re-sort downloaded-first
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
  // tafsir/annotation fragments fetched on demand by reader.ts (surah pages'
  // ayah buttons carry data-ann-src) — without these a downloaded surah's
  // tafsir popup is dead offline; sw.js serves them from this same cache.
  for (const el of doc.querySelectorAll<HTMLElement>("[data-ann-src]")) {
    const src = el.dataset.annSrc;
    if (src && src.startsWith("/")) urls.add(src);
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
  let urls: string[];
  // a whole tafsir (the ann-sheet's "تنزيل هذا التفسير كاملًا" button): the
  // url list — that source's per-ayah stubs + bodies — comes from the
  // build-time manifest, since a tafsir has no landing page to scrape
  if (kind === "tafsir") {
    try {
      const res = await fetch(`/tafsir-dl/${id}.json`);
      if (!res.ok) throw new Error(String(res.status));
      const man = (await res.json()) as { urls: string[]; bytes?: number };
      // big tafsirs (ابن كثير ≈ 105MB) deserve a heads-up on metered connections
      if (man.bytes && man.bytes > 20 * 1024 * 1024 &&
          !confirm(`حجم هذا التفسير كاملًا نحو ${formatSize(man.bytes)} — أتريد المتابعة؟`)) {
        btn.removeAttribute("data-dl-busy");
        renderDownloadButton(btn);
        return;
      }
      urls = man.urls;
    } catch {
      btn.removeAttribute("data-dl-busy");
      const l = btn.querySelector("[data-dl-label]");
      if (l) l.textContent = "تعذّر التنزيل";
      return;
    }
  } else if (path !== location.pathname) {
    // quick download from a list row: the landing page isn't open, so fetch it
    // and read its TOC/audio from the parsed HTML instead of the live DOM
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
  // ask the browser not to evict the offline library under storage pressure —
  // silent grant/deny, idempotent, so just ask on every download
  navigator.storage?.persist?.().catch(() => {});
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

// "download all" on the downloads page — reuses startDownload per item
// (same sequential, gentle-on-the-Worker fetch), skipping ones already saved.
// No per-item rows exist on this page anymore (just the group's own "تنزيل
// الكل" button), so the item list travels as a data-dl-items JSON blob on
// the button itself instead of being read off sibling DOM elements; each
// item becomes a detached (never-appended) element just to satisfy
// startDownload's dataset-reading contract — its optional label/size lookups
// (querySelector on an empty element) no-op harmlessly.
async function downloadAllIn(allBtn: HTMLElement) {
  const kind = allBtn.dataset.dlKind!;
  const label = allBtn.querySelector<HTMLElement>("[data-dl-all-label]");
  // items may override the button-level kind (person pages mix books/poems/
  // articles under one "download all" button)
  let items: { id: string; title: string; path: string; kind?: string }[] = [];
  try { items = JSON.parse(allBtn.dataset.dlItems || "[]"); } catch { /* malformed → nothing to download */ }
  const pending = items
    .filter((it) => !getManifest()[keyOf(it.kind ?? kind, it.id)])
    .map((it) => {
      const el = document.createElement("div");
      el.dataset.dlKind = it.kind ?? kind;
      el.dataset.dlId = it.id;
      el.dataset.dlTitle = it.title;
      el.dataset.dlPath = it.path;
      return el;
    });
  if (pending.length === 0) {
    if (label) { label.textContent = "الكل متوفر بالفعل"; setTimeout(() => { label.textContent = "تنزيل الكل"; }, 1200); }
    return;
  }
  allBtn.setAttribute("data-dl-busy", "1");
  for (let i = 0; i < pending.length; i++) {
    if (label) label.textContent = `جارٍ التنزيل… ${i + 1}/${pending.length}`;
    await startDownload(pending[i]);
  }
  allBtn.removeAttribute("data-dl-busy");
  if (label) label.textContent = "تنزيل الكل";
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
  // the sheet's tafsir button has no landing page to size-probe — plain label
  if (kind === "tafsir") {
    if (label) label.textContent = "تنزيل هذا التفسير كاملًا";
    return;
  }
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

// Storage estimate is origin-wide (everything the SW/app caches, not just
// downloads), so it's the "out of" denominator only — the number itself
// stays our own manifest total, the one thing the user actually chose to save.
function renderUsageBar(totalBytes: number) {
  const box = document.querySelector<HTMLElement>("[data-downloads-usage]");
  const label = document.querySelector<HTMLElement>("[data-downloads-usage-label]");
  const fill = document.querySelector<HTMLElement>("[data-downloads-usage-fill]");
  if (!box || !label || !fill || !totalBytes) { if (box) box.hidden = true; return; }
  box.hidden = false;
  label.textContent = `${formatSize(totalBytes)} مُستخدَمة`;
  navigator.storage?.estimate?.().then((est) => {
    if (!est.quota) return;
    label.textContent = `${formatSize(totalBytes)} من ${formatSize(est.quota)} مساحة متاحة`;
    fill.style.width = `${Math.min(100, (totalBytes / est.quota) * 100)}%`;
  }).catch(() => {});
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
  renderUsageBar(totalBytes);
}

// Capture phase: these buttons sit inside a card <a href>, and ClientRouter's
// own click listener (Base.astro, bubble phase) decides whether to soft-navigate
// by checking ev.defaultPrevented — since it's registered earlier in the page
// than this module script, its bubble-phase handler would otherwise run BEFORE
// this one and already navigate into the card. Capture always runs first.
document.addEventListener("click", (e) => {
  const target = e.target as HTMLElement;

  const allBtn = target.closest<HTMLElement>('[data-action="download:all"]');
  if (allBtn) {
    e.preventDefault();
    if (allBtn.hasAttribute("data-dl-busy")) return;
    downloadAllIn(allBtn);
    return;
  }

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
