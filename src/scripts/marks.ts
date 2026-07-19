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
  section?: string;
  page?: string; // nearest preceding .page-sep at save time — see pageAtRange()
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

function addMark(kind: Kind, off: { start: number; end: number }, text: string, note?: string, page?: string): Mark {
  const mark: Mark = { id: Math.random().toString(36).slice(2, 9), ...off, kind, text, note, title: pageTitle(), section: pageSection(), page };
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
// .reader-subtitle only exists on nested كتاب/باب chapter pages — the sub-heading
// one level below pageTitle() (see book/[slug]/[chapter].astro's reader-subtitle)
function pageSection(): string | undefined {
  return document.querySelector(".reader-subtitle")?.textContent?.trim() || undefined;
}
// nearest .page-sep marker at or before a point in the DOM — used both for
// citing a live selection (citeMeta) and for stamping a page onto a saved
// kunashti mark so it can be cited later, off the original page.
function pageAtNode(container: HTMLElement, node: Node): string | undefined {
  let page: string | undefined;
  container.querySelectorAll<HTMLElement>(".page-sep[data-page]").forEach((el) => {
    const pos = el.compareDocumentPosition(node);
    if (pos === 0 || pos & Node.DOCUMENT_POSITION_FOLLOWING) page = el.dataset.page;
  });
  return page;
}
function pageAtRange(range: Range): string | undefined {
  const container = document.querySelector<HTMLElement>("[data-cite-book]");
  if (!container) return undefined;
  return pageAtNode(container, range.startContainer);
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

// Cheap check for the مشاركة button's visibility — the full citation build
// (page/بيت/آية extraction) is heavier and lives in share.ts, loaded only
// once the button is actually clicked (see showToolbar below).
function hasCiteContext(range: Range): boolean {
  const el = range.startContainer.nodeType === 1 ? (range.startContainer as Element) : range.startContainer.parentElement;
  return !!el?.closest("[data-cite-book]");
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
  btn("خطأ", "mistake", () => openNote("mistake", off, text, range));
  btn("فائدة", "benefit", () => openNote("benefit", off, text, range));
  btn("ملاحظة", "note", () => openNote("note", off, text, range));
  if (hasCiteContext(range)) {
    // citeMeta + the composer itself are a separate, dynamically-loaded
    // chunk — keeps them out of the site-wide critical bundle (render-budget gate)
    btn("مشاركة", null, () => import("./share.ts").then(({ openShare }) => openShare(t, range, text, done)));
  }
  if (over.length) { const d = btn("حذف", null, () => { removeMarks(new Set(over.map((m) => m.id))); done(); }); d.classList.add("aa-del"); }
  positionTools(range);
  t.setAttribute("data-open", "");
}

function openNote(kind: Kind, off: { start: number; end: number }, text: string, range?: Range) {
  const mark = addMark(kind, off, text, undefined, range ? pageAtRange(range) : undefined); // mark exists immediately; note optional
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

// --- حفظ: bookmark a specific PLACE for later (distinct from فائدة, which
// saves a selected quote) — no selection needed, one tap saves the current
// scroll position on the current book/poem/chapter/article/question. Several
// places per page are allowed (page 30 and page 40 of the same continuous
// route are two different scrollY values, not the same "page" in the old
// one-bookmark-per-URL sense). Own store, not aa-marks:<path>: a bookmark
// isn't anchored to an offset inside the page's text, it just needs to be
// enumerable as a flat list (see library.ts's "المحفوظات" tab). ---
interface Saved { id: string; path: string; title: string; section?: string; savedAt: number; scrollY: number; page?: string }
const SAVED_KEY = "aa-saved";
function loadSaved(): Saved[] {
  try {
    // pre-multi-bookmark entries have no id/scrollY — keep them listed (title
    // still works, jump just lands at the top) instead of dropping them.
    return (JSON.parse(localStorage.getItem(SAVED_KEY) || "[]") as Partial<Saved>[]).map((s) => ({
      id: s.id ?? Math.random().toString(36).slice(2, 9),
      path: s.path!, title: s.title ?? "", section: s.section, savedAt: s.savedAt ?? Date.now(),
      scrollY: s.scrollY ?? 0, page: s.page,
    }));
  } catch { return []; }
}
function writeSaved(list: Saved[]) {
  if (list.length) localStorage.setItem(SAVED_KEY, JSON.stringify(list));
  else localStorage.removeItem(SAVED_KEY);
}
function savedForPath(path = docId()): Saved[] {
  return loadSaved().filter((s) => s.path === path);
}
// Best-effort page label off whatever .page-sep the viewport is currently
// over — nice-to-have for books (which have them); Quran/poems just fall
// back to the plain title, which is still enough to tell entries apart.
function currentPageLabel(): string | undefined {
  const el = document.elementFromPoint(window.innerWidth / 2, 96) as HTMLElement | null;
  const r = root();
  return el && r ? pageAtNode(r, el) : undefined;
}
function addBookmark() {
  const list = loadSaved();
  list.push({
    id: crypto.randomUUID?.() ?? Math.random().toString(36).slice(2, 9),
    path: docId(), title: pageTitle(), section: pageSection(),
    savedAt: Date.now(), scrollY: window.scrollY, page: currentPageLabel(),
  });
  writeSaved(list);
  syncSaveBtn();
}
function removeBookmark(id: string) { writeSaved(loadSaved().filter((s) => s.id !== id)); syncSaveBtn(); }
function renameBookmark(id: string, title: string) {
  const list = loadSaved();
  const s = list.find((x) => x.id === id);
  if (s) { s.title = title; writeSaved(list); }
}
function syncSaveBtn() {
  const on = savedForPath().length > 0;
  document.querySelectorAll<HTMLElement>('[data-action="page:save"]').forEach((b) => b.setAttribute("aria-pressed", String(on)));
}

// One click/tap, phone or desktop: opens a small popover under the save
// button with "+ احفظ هذا الموضع" on top and this page's saved places below
// it (jump / rename / remove) — no gesture to remember, no separate desktop
// entry point needed.
let savedPop: HTMLElement | null = null;
function savedPopover(): HTMLElement {
  if (savedPop && savedPop.isConnected) return savedPop;
  savedPop = document.createElement("div");
  savedPop.className = "aa-saved-pop";
  savedPop.hidden = true;
  document.body.appendChild(savedPop);
  return savedPop;
}
function closeSavedPopover() { savedPopover().hidden = true; }
function renderSavedPopover() {
  const pop = savedPopover();
  pop.textContent = "";
  const addBtn = document.createElement("button");
  addBtn.type = "button"; addBtn.className = "aa-saved-add";
  addBtn.textContent = "+ احفظ هذا الموضع";
  addBtn.addEventListener("click", () => { addBookmark(); renderSavedPopover(); });
  pop.appendChild(addBtn);
  const list = savedForPath().sort((a, b) => b.savedAt - a.savedAt);
  if (!list.length) return;
  const box = document.createElement("div"); box.className = "aa-saved-list";
  for (const s of list) {
    const row = document.createElement("div"); row.className = "aa-saved-row";
    const jump = document.createElement("button");
    jump.type = "button"; jump.className = "aa-saved-jump";
    jump.textContent = s.page ? `${s.title || pageTitle()} — صفحة ${s.page}` : (s.title || pageTitle());
    jump.addEventListener("click", () => { window.scrollTo({ top: s.scrollY, behavior: "smooth" }); closeSavedPopover(); });
    const rename = document.createElement("button");
    rename.type = "button"; rename.className = "aa-saved-rename"; rename.setAttribute("aria-label", "إعادة تسمية"); rename.textContent = "✎";
    rename.addEventListener("click", () => {
      const next = prompt("اسم هذا الموضع:", s.title || "");
      if (next != null && next.trim()) { renameBookmark(s.id, next.trim()); renderSavedPopover(); }
    });
    const del = document.createElement("button");
    del.type = "button"; del.className = "aa-saved-del"; del.setAttribute("aria-label", "حذف"); del.textContent = "×";
    del.addEventListener("click", () => { removeBookmark(s.id); renderSavedPopover(); });
    row.append(jump, rename, del);
    box.appendChild(row);
  }
  pop.appendChild(box);
}
function positionSavedPopover(btn: HTMLElement) {
  const r = btn.getBoundingClientRect();
  const pop = savedPopover();
  // pop must already be unhidden (real layout) when this runs — the mobile
  // tab bar's حفظ button sits at the very bottom of the viewport, so
  // "below the button" (the desktop icon's placement, which has room under
  // it) would render the popover entirely off-screen there. Flip above the
  // button instead whenever there isn't room below.
  const h = pop.getBoundingClientRect().height;
  const top = (r.bottom + 6 + h > window.innerHeight)
    ? r.top + window.scrollY - h - 6
    : r.bottom + window.scrollY + 6;
  pop.style.top = `${Math.max(8, top)}px`;
  pop.style.left = `${Math.max(8, Math.min(r.left, window.innerWidth - 260))}px`;
}
function toggleSavedPopover(btn: HTMLElement) {
  const pop = savedPopover();
  if (!pop.hidden) { closeSavedPopover(); return; }
  renderSavedPopover();
  pop.hidden = false; // unhide first — positionSavedPopover needs real layout to measure height
  positionSavedPopover(btn);
}
// cross-page jump from كناشة (/benefits): #s=<id> in the href
function jumpToSavedHash() {
  const m = location.hash.match(/^#s=([\w-]+)$/);
  if (!m) return;
  const s = loadSaved().find((x) => x.id === m[1]);
  if (s) window.scrollTo({ top: s.scrollY, behavior: "smooth" });
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
  const t = e.target as HTMLElement;
  if (t.closest('[data-action="page:find"]')) { e.preventDefault(); openFind(); return; }
  const saveBtn = t.closest<HTMLElement>('[data-action="page:save"]');
  if (saveBtn) { e.preventDefault(); toggleSavedPopover(saveBtn); return; }
  if (savedPop && !savedPop.hidden && !t.closest(".aa-saved-pop")) closeSavedPopover();
});

// --- book chapter progress (تابع القراءة on the book's main page) — which
// chapter you last opened, separate from maybeResume()'s in-page scroll offset ---
function recordBookProgress() {
  const el = document.querySelector<HTMLElement>("[data-book-progress]");
  const bookId = el?.dataset.bookProgress;
  if (!el || !bookId) return;
  try {
    localStorage.setItem("aa-book-progress:" + bookId, JSON.stringify({
      href: location.pathname,
      title: el.dataset.bookProgressTitle,
      idx: Number(el.dataset.bookProgressIdx),
      total: Number(el.dataset.bookProgressTotal),
    }));
  } catch {}
}
function showBookResume() {
  const el = document.querySelector<HTMLElement>("[data-book-resume]");
  const bookId = el?.dataset.bookResume;
  if (!el || !bookId) return;
  let saved: { href: string; title: string; idx: number; total: number } | null = null;
  try { saved = JSON.parse(localStorage.getItem("aa-book-progress:" + bookId) || "null"); } catch {}
  if (!saved?.href || !saved.total) { el.hidden = true; return; }
  const link = el.querySelector<HTMLAnchorElement>("[data-book-resume-link]");
  const bar = el.querySelector<HTMLElement>("[data-book-resume-bar]");
  if (link) { link.href = saved.href; link.textContent = `${saved.title} — متابعة القراءة ←`; }
  if (bar) bar.style.width = `${Math.round((saved.idx / saved.total) * 100)}%`;
  el.hidden = false;
}

// --- واصل القراءة (home shelf) — most recent reading pages, one entry per
// work (deduped by title), rendered on the home page by reader.ts ---
function recordRecent() {
  const main = document.querySelector<HTMLElement>("main");
  if (main?.dataset.reading !== "1") return;
  const title = main.dataset.readerMain;
  if (!title) return;
  try {
    const list: { path: string; title: string; sub?: string; ts: number }[] =
      JSON.parse(localStorage.getItem("aa-recent") || "[]");
    const entry = { path: docId(), title, sub: main.dataset.readerSub || undefined, ts: Date.now() };
    localStorage.setItem("aa-recent",
      JSON.stringify([entry, ...list.filter((e) => e.title !== title)].slice(0, 4)));
  } catch { /* best-effort */ }
}

// mushaf resume: latest /quran/ entry from aa-recent (recordRecent below) —
// the mushaf grid is where a reading session starts, so surface the way back
// in; the surah page's own "تابع القراءة ↓" then restores the exact spot.
function showQuranResume() {
  const el = document.querySelector<HTMLElement>("[data-quran-resume]");
  if (!el) return;
  let list: { path: string; title: string }[] = [];
  try { list = JSON.parse(localStorage.getItem("aa-recent") || "[]"); } catch { /* best-effort */ }
  const hit = list.find((e) => e.path.startsWith("/quran/") && e.path !== "/quran/mushaf");
  const link = el.querySelector<HTMLAnchorElement>("a");
  if (!hit || !link) { el.hidden = true; return; }
  link.href = hit.path;
  link.textContent = `${hit.title} — تابعِ القراءة ←`;
  el.hidden = false;
}

function onPage() {
  hideTools();
  closeFind();
  findHL = null;
  paint();
  syncFindFab();
  maybeResume();
  jumpToHash();
  jumpToSavedHash();
  closeSavedPopover();
  recordBookProgress();
  recordRecent();
  showBookResume();
  showQuranResume();
  syncSaveBtn();
}
onPage();
document.addEventListener("astro:page-load", onPage);
window.addEventListener("hashchange", jumpToHash);
window.addEventListener("hashchange", jumpToSavedHash);
