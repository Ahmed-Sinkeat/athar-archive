// Personal, device-only reading layer: highlights (خطأ للإبلاغ عنه + فائدة +
// ملاحظة), in-page find with highlighting, and Kindle-style resume (آخر موضع
// قراءة). Nothing leaves the browser — all state in localStorage. "خطأ" marks
// are reviewed and sent as one batch from /benefits (see library.ts), not
// per-page — this file only records and paints them.
//
// Highlights paint via the CSS Custom Highlight API (no DOM mutation, so they
// never fight reader.ts's tashkeel/annotation surgery and inherit every theme).
// Anchors are character offsets into <main>'s text, re-verified against the
// stored quote on paint.
// ponytail: offset anchoring breaks only if a published book's text is edited
// after a highlight is made; the stored quote is then re-found by text search.
// Upgrade path: per-block (heading/verse id) anchoring if drift becomes common.

import { stripTashkeel } from "../lib/display";

type Kind = "mistake" | "benefit" | "note";
interface Mark {
  id: string;
  start: number;
  end: number;
  kind: Kind;
  text: string;
  note?: string;
  title?: string;
}

const HL_NAME: Record<Kind, string> = { mistake: "aa-mistake", benefit: "aa-benefit", note: "aa-note-hl" };
const docId = () => location.pathname.replace(/\/$/, "") || "/";
const keyFor = (path: string) => "aa-marks:" + path;
const posKey = () => "aa-pos:" + docId();
const supportsHL = typeof CSS !== "undefined" && "highlights" in CSS;

function load(path = docId()): Mark[] {
  try { return JSON.parse(localStorage.getItem(keyFor(path)) || "[]"); } catch { return []; }
}
function save(marks: Mark[]) {
  if (marks.length) localStorage.setItem(keyFor(docId()), JSON.stringify(marks));
  else localStorage.removeItem(keyFor(docId()));
}

// --- offset <-> Range over a root's text nodes (deterministic per build) ---
function root(): HTMLElement | null {
  return document.querySelector("main");
}
function textNodes(r: Node): Text[] {
  const walk = document.createTreeWalker(r, NodeFilter.SHOW_TEXT);
  const out: Text[] = [];
  let n: Node | null;
  while ((n = walk.nextNode())) out.push(n as Text);
  return out;
}
function offsetsFromRange(r: HTMLElement, range: Range): { start: number; end: number } | null {
  let acc = 0, start = -1, end = -1;
  for (const t of textNodes(r)) {
    const len = t.nodeValue!.length;
    if (t === range.startContainer) start = acc + range.startOffset;
    if (t === range.endContainer) end = acc + range.endOffset;
    acc += len;
  }
  return start >= 0 && end > start ? { start, end } : null;
}
function rangeFromOffsets(r: HTMLElement, start: number, end: number): Range | null {
  const range = document.createRange();
  let acc = 0, set = 0;
  for (const t of textNodes(r)) {
    const len = t.nodeValue!.length;
    if (set === 0 && start <= acc + len) { range.setStart(t, start - acc); set = 1; }
    if (set === 1 && end <= acc + len) { range.setEnd(t, end - acc); set = 2; break; }
    acc += len;
  }
  return set === 2 ? range : null;
}
// Resilience: if the offset range no longer matches the stored quote, re-find
// the quote (tashkeel-insensitive) in the root's text and rebuild the range.
function findQuote(r: HTMLElement, quote: string): { start: number; end: number } | null {
  const norm = (s: string) => stripTashkeel(s).replace(/\s+/g, " ").trim();
  const needle = norm(quote);
  if (!needle) return null;
  let stripped = "";
  const map: number[] = [];
  let acc = 0;
  for (const t of textNodes(r)) {
    for (const ch of t.nodeValue!) {
      const s = stripTashkeel(ch).replace(/\s+/g, " ");
      if (s.trim() || s === " ") { stripped += s; for (let k = 0; k < s.length; k++) map.push(acc); }
      acc++;
    }
  }
  stripped = stripped.replace(/\s+/g, " ");
  const idx = stripped.indexOf(needle);
  if (idx < 0) return null;
  return { start: map[idx], end: map[idx + needle.length - 1] + 1 };
}

// --- painting ---
const highlights: Partial<Record<Kind, any>> = {};
let findHL: any = null;
function paint() {
  if (!supportsHL) return;
  const r = root();
  if (!r) return;
  (["mistake", "benefit", "note"] as Kind[]).forEach((k) => {
    let hl = highlights[k];
    if (!hl) { hl = new (window as any).Highlight(); highlights[k] = hl; CSS.highlights.set(HL_NAME[k], hl); }
    hl.clear();
  });
  const marks = load();
  let changed = false;
  for (const m of marks) {
    let rng = rangeFromOffsets(r, m.start, m.end);
    if ((!rng || (m.text && stripTashkeel(rng.toString()).replace(/\s+/g, "") !== stripTashkeel(m.text).replace(/\s+/g, ""))) && m.text) {
      const found = findQuote(r, m.text);
      if (found) { m.start = found.start; m.end = found.end; rng = rangeFromOffsets(r, found.start, found.end); changed = true; }
    }
    if (rng) highlights[m.kind]!.add(rng);
  }
  if (changed) save(marks);
}

// --- selection toolbar ---
let tools: HTMLElement | null = null;
function toolbar(): HTMLElement {
  if (tools && tools.isConnected) return tools;
  tools = document.createElement("div");
  tools.className = "aa-tools";
  document.body.appendChild(tools);
  return tools;
}
function hideTools() { tools?.removeAttribute("data-open"); }

function currentSelectionRange(): Range | null {
  const sel = window.getSelection();
  if (!sel || sel.isCollapsed || sel.rangeCount === 0) return null;
  const range = sel.getRangeAt(0);
  const r = root();
  if (!r || !r.contains(range.commonAncestorContainer)) return null;
  // only inside actual reading text, not chrome
  const host = (range.commonAncestorContainer.nodeType === 1 ? range.commonAncestorContainer : range.commonAncestorContainer.parentElement) as HTMLElement | null;
  if (!host?.closest(".prose, .verse, .ann-entry-body, .reading-pad")) return null;
  return range.toString().trim() ? range : null;
}

function overlapping(off: { start: number; end: number }): Mark[] {
  return load().filter((m) => m.start < off.end && off.start < m.end);
}

function addMark(kind: Kind, off: { start: number; end: number }, text: string, note?: string): Mark {
  const mark: Mark = { id: Math.random().toString(36).slice(2, 9), ...off, kind, text, note, title: pageTitle() };
  const marks = load();
  marks.push(mark);
  save(marks);
  paint();
  return mark;
}
function removeMarks(ids: Set<string>) {
  save(load().filter((m) => !ids.has(m.id)));
  paint();
}
function updateNote(id: string, note: string) {
  const marks = load();
  const m = marks.find((x) => x.id === id);
  if (m) { m.note = note; save(marks); }
}
function pageTitle(): string {
  return (document.querySelector("h1.reader-title, h1.page-title, .reader-byline")?.textContent || document.title.split(" · ")[0] || "").trim();
}

function positionTools(range: Range) {
  const rect = range.getBoundingClientRect();
  const t = toolbar();
  // Below the selection, not above: the native mobile selection/copy pill
  // renders right above the selection (and its handles), so a toolbar placed
  // there is invisible under it — reported as "can't highlight, the pill
  // covers it". Below stays clear of that OS chrome.
  t.style.top = `${rect.bottom + window.scrollY + 8}px`;
  t.style.left = `${rect.left + rect.width / 2}px`;
}

// citation metadata for the "نسخ مع المصدر" action — book/author come from the
// nearest [data-cite-book] ancestor (set server-side on the reading container),
// page/juz from the last .page-sep marker before the selection start.
function citeMeta(range: Range): { book: string; author: string; page?: string; juz?: string } | null {
  const startEl = range.startContainer.nodeType === 1 ? (range.startContainer as Element) : range.startContainer.parentElement;
  const container = startEl?.closest<HTMLElement>("[data-cite-book]");
  if (!container) return null;
  let page: string | undefined, juz: string | undefined;
  container.querySelectorAll<HTMLElement>(".page-sep[data-page]").forEach((el) => {
    const pos = el.compareDocumentPosition(range.startContainer);
    if (pos === 0 || pos & Node.DOCUMENT_POSITION_FOLLOWING) { page = el.dataset.page; juz = el.dataset.juz; }
  });
  return { book: container.dataset.citeBook || "", author: container.dataset.citeAuthor || "", page, juz };
}
function copyWithSource(range: Range, text: string, onDone: (label: string) => void) {
  const meta = citeMeta(range);
  if (!meta) return;
  const parts = [meta.book, meta.author, meta.page ? `ص ${meta.page}` : "", meta.juz ? `ج ${meta.juz}` : ""].filter(Boolean);
  navigator.clipboard?.writeText(`"${text}"\n— ${parts.join("، ")}`).then(() => onDone("تم النسخ ✓"));
}

function showToolbar(range: Range) {
  const r = root()!;
  const off = offsetsFromRange(r, range);
  if (!off) return;
  const text = range.toString().replace(/\s+/g, " ").trim();
  const t = toolbar();
  t.innerHTML = "";
  const over = overlapping(off);
  const btn = (label: string, dot: string | null, onClick: () => void) => {
    const b = document.createElement("button");
    if (dot) { const d = document.createElement("span"); d.className = "aa-dot " + dot; b.appendChild(d); }
    b.appendChild(document.createTextNode(label));
    b.addEventListener("mousedown", (e) => e.preventDefault());
    b.addEventListener("click", onClick);
    t.appendChild(b);
    return b;
  };
  btn("خطأ", "mistake", () => openNote("mistake", off, text));
  btn("فائدة", "benefit", () => openNote("benefit", off, text));
  btn("ملاحظة", "note", () => openNote("note", off, text));
  if (citeMeta(range)) {
    const c = btn("انسخ مع المصدر", null, () => copyWithSource(range, text, (label) => { c.lastChild!.textContent = label; setTimeout(done, 900); }));
  }
  if (over.length) { const d = btn("حذف", null, () => { removeMarks(new Set(over.map((m) => m.id))); done(); }); d.classList.add("aa-del"); }
  positionTools(range);
  t.setAttribute("data-open", "");
}

function openNote(kind: Kind, off: { start: number; end: number }, text: string) {
  const mark = addMark(kind, off, text); // mark exists immediately; note optional
  const t = toolbar();
  t.innerHTML = "";
  const wrap = document.createElement("div");
  wrap.className = "aa-note";
  const ta = document.createElement("textarea");
  ta.placeholder = kind === "mistake" ? "صِفِ الخطأ… (اختياري)" : kind === "note" ? "أضف ملاحظة… (اختياري)" : "أضف فائدة… (اختياري)";
  ta.addEventListener("mousedown", (e) => e.stopPropagation());
  const ok = document.createElement("button");
  ok.textContent = "حفظ";
  ok.addEventListener("mousedown", (e) => e.preventDefault());
  ok.addEventListener("click", () => { updateNote(mark.id, ta.value.trim()); done(); });
  wrap.append(ta, ok);
  t.appendChild(wrap);
  t.setAttribute("data-open", "");
  ta.focus();
}

function done() { hideTools(); window.getSelection()?.removeAllRanges(); }

// --- in-page find (البحث داخل الكتاب مع التمييز) ---
let findBar: HTMLElement | null = null;
let findMatches: { start: number; end: number }[] = [];
let findIdx = 0;
function ensureFindBar(): HTMLElement {
  if (findBar && findBar.isConnected) return findBar;
  findBar = document.createElement("div");
  findBar.className = "aa-find";
  findBar.hidden = true;
  findBar.innerHTML =
    '<input type="search" aria-label="بحث في الصفحة" placeholder="بحث في الصفحة…" />' +
    '<span class="aa-find-count"></span>' +
    '<button type="button" data-find="prev" aria-label="السابق">‹</button>' +
    '<button type="button" data-find="next" aria-label="التالي">›</button>' +
    '<button type="button" data-find="close" aria-label="إغلاق">×</button>';
  document.body.appendChild(findBar);
  const input = findBar.querySelector("input")!;
  input.addEventListener("input", () => runFind(input.value));
  findBar.addEventListener("click", (e) => {
    const act = (e.target as HTMLElement).closest<HTMLElement>("[data-find]")?.dataset.find;
    if (act === "next") stepFind(1);
    else if (act === "prev") stepFind(-1);
    else if (act === "close") closeFind();
  });
  return findBar;
}
function runFind(q: string) {
  const r = root();
  if (!supportsHL || !r) return;
  if (!findHL) { findHL = new (window as any).Highlight(); CSS.highlights.set("aa-find", findHL); }
  findHL.clear();
  findMatches = [];
  const needle = stripTashkeel(q).replace(/\s+/g, " ").trim();
  const countEl = findBar!.querySelector(".aa-find-count")!;
  if (!needle) { countEl.textContent = ""; return; }
  // map stripped text -> original offsets, same scheme as findQuote
  let stripped = "";
  const map: number[] = [];
  let acc = 0;
  for (const t of textNodes(r)) {
    for (const ch of t.nodeValue!) { const s = stripTashkeel(ch); if (s) { stripped += s; for (let k = 0; k < s.length; k++) map.push(acc); } acc++; }
  }
  let from = 0, i: number;
  while ((i = stripped.indexOf(needle, from)) >= 0) {
    findMatches.push({ start: map[i], end: map[i + needle.length - 1] + 1 });
    from = i + needle.length;
  }
  for (const m of findMatches) { const rng = rangeFromOffsets(r, m.start, m.end); if (rng) findHL.add(rng); }
  findIdx = 0;
  countEl.textContent = findMatches.length ? `1/${findMatches.length}` : "٠";
  if (findMatches.length) scrollToMatch(0);
}
function scrollToMatch(i: number) {
  const r = root(); if (!r || !findMatches[i]) return;
  const rng = rangeFromOffsets(r, findMatches[i].start, findMatches[i].end);
  if (rng) { const rect = rng.getBoundingClientRect(); window.scrollTo({ top: rect.top + window.scrollY - window.innerHeight / 2, behavior: "smooth" }); }
}
function stepFind(dir: number) {
  if (!findMatches.length) return;
  findIdx = (findIdx + dir + findMatches.length) % findMatches.length;
  findBar!.querySelector(".aa-find-count")!.textContent = `${findIdx + 1}/${findMatches.length}`;
  scrollToMatch(findIdx);
}
function openFind() {
  if (!root()?.querySelector(".prose, .verse")) return;
  const bar = ensureFindBar();
  bar.hidden = false;
  bar.querySelector("input")!.focus();
}
function closeFind() {
  if (findBar) findBar.hidden = true;
  findHL?.clear();
  findMatches = [];
}
// topbar opener — reading pages only (mobile has no Ctrl+F); the button
// itself lives in Base.astro, wired via the [data-action="page:find"]
// delegate below — this just shows/hides it per page.
function syncFindFab() {
  const reading = !!root()?.querySelector(".prose, .verse");
  document.querySelectorAll<HTMLElement>('[data-action="page:find"]').forEach((b) => { b.hidden = !reading; });
}

// --- resume (آخر موضع قراءة) ---
let resumeEl: HTMLButtonElement | null = null;
function saveScroll() {
  const h = document.documentElement.scrollHeight - window.innerHeight;
  if (h <= 0) return;
  const ratio = window.scrollY / h;
  if (ratio > 0.04 && ratio < 0.97) localStorage.setItem(posKey(), ratio.toFixed(4));
  else localStorage.removeItem(posKey());
}
function maybeResume() {
  if (!root()?.querySelector(".prose, .verse")) return;
  const saved = parseFloat(localStorage.getItem(posKey()) || "");
  if (!saved || window.scrollY > 80) return;
  if (!resumeEl) {
    resumeEl = document.createElement("button");
    resumeEl.className = "aa-resume";
    resumeEl.type = "button";
    resumeEl.textContent = "تابع القراءة ↓";
    resumeEl.addEventListener("click", () => {
      const h = document.documentElement.scrollHeight - window.innerHeight;
      window.scrollTo({ top: saved * h, behavior: "smooth" });
      resumeEl!.hidden = true;
    });
    document.body.appendChild(resumeEl);
  }
  resumeEl.hidden = false;
  // auto-dismiss once the reader scrolls on their own
  const dismiss = () => { if (resumeEl) resumeEl.hidden = true; window.removeEventListener("wheel", dismiss); window.removeEventListener("touchmove", dismiss); };
  window.addEventListener("wheel", dismiss, { once: true, passive: true });
  window.addEventListener("touchmove", dismiss, { once: true, passive: true });
}

// jump to a specific mark from /benefits (#m=<id>)
function jumpToHash() {
  const m = location.hash.match(/^#m=(\w+)$/);
  if (!m) return;
  const r = root(); if (!r) return;
  const mark = load().find((x) => x.id === m[1]);
  if (!mark) return;
  const rng = rangeFromOffsets(r, mark.start, mark.end) || (mark.text ? (() => { const f = findQuote(r, mark.text); return f && rangeFromOffsets(r, f.start, f.end); })() : null);
  if (rng) { const rect = rng.getBoundingClientRect(); window.scrollTo({ top: rect.top + window.scrollY - window.innerHeight / 3, behavior: "smooth" }); }
}

// --- wiring (delegated + per-page) ---
let scrollTimer: number | undefined;
document.addEventListener("selectionchange", () => {
  const range = currentSelectionRange();
  if (!range) { if (!tools?.querySelector(".aa-note")) hideTools(); return; }
});
function onSelectEnd() {
  const range = currentSelectionRange();
  if (range) showToolbar(range);
}
document.addEventListener("mouseup", () => setTimeout(onSelectEnd, 0));
document.addEventListener("touchend", () => setTimeout(onSelectEnd, 0));
document.addEventListener("mousedown", (e) => {
  if (!(e.target as HTMLElement).closest(".aa-tools")) hideTools();
});
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape") { hideTools(); closeFind(); }
});
window.addEventListener("scroll", () => { clearTimeout(scrollTimer); scrollTimer = window.setTimeout(saveScroll, 400); }, { passive: true });
window.addEventListener("beforeunload", saveScroll);
document.addEventListener("astro:before-swap", saveScroll); // SPA nav won't fire beforeunload

// expose the find opener for an optional button (data-action="page:find")
document.addEventListener("click", (e) => {
  if ((e.target as HTMLElement).closest('[data-action="page:find"]')) { e.preventDefault(); openFind(); }
});

function onPage() {
  hideTools();
  closeFind();
  findHL = null;
  paint();
  syncFindFab();
  maybeResume();
  jumpToHash();
}
onPage();
document.addEventListener("astro:page-load", onPage);
window.addEventListener("hashchange", jumpToHash);
