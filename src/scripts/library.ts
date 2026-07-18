// Builds كُناشتي (/benefits) from the device's saved marks. Reads every
// "aa-marks:<path>" key, flattens, and renders the chosen kind. Client-only.
// The "mistake" tab additionally gets a send button that batches every
// mistake mark (across all pages) into one mailto — the only place mistakes
// leave the device, unlike benefit/note which just stay in the list.

type Kind = "mistake" | "benefit" | "note";
type TabKind = Kind | "saved";
type GroupBy = "book" | "topic" | "person";
interface Mark { id: string; kind: Kind; text: string; note?: string; title?: string; section?: string; page?: string }
interface Item extends Mark { path: string }
interface SourceMeta { path: string; person: string; topics: string[]; title: string }
// حفظ (bookmark a specific scroll position — see marks.ts's addBookmark) lives
// in its own flat store, not aa-marks:<path>: it has no quote/offset into the
// page text, just needs to be enumerable as one list. Several entries can
// share the same path (different places in the same long page).
interface SavedPlace { id: string; path: string; title: string; section?: string; savedAt: number; scrollY: number; page?: string }
function loadSavedPlaces(): SavedPlace[] {
  try {
    return (JSON.parse(localStorage.getItem("aa-saved") || "[]") as Partial<SavedPlace>[]).map((s) => ({
      id: s.id ?? Math.random().toString(36).slice(2, 9),
      path: s.path!, title: s.title ?? "", section: s.section, savedAt: s.savedAt ?? Date.now(),
      scrollY: s.scrollY ?? 0, page: s.page,
    }));
  } catch { return []; }
}
function removeSavedPlace(id: string) {
  const list = loadSavedPlaces().filter((s) => s.id !== id);
  if (list.length) localStorage.setItem("aa-saved", JSON.stringify(list));
  else localStorage.removeItem("aa-saved");
}
function renameSavedPlace(id: string, title: string) {
  const list = loadSavedPlaces();
  const s = list.find((x) => x.id === id);
  if (s) { s.title = title; localStorage.setItem("aa-saved", JSON.stringify(list)); }
}

// path → {person, topics}, built at build time (benefits.astro) since marks
// themselves only carry {path, title}. Chapter/page sub-routes
// (/book/id/chapter-slug) fall back to their parent book's entry.
const sourceIndex: SourceMeta[] = (() => {
  try { return JSON.parse(document.getElementById("lib-source-index")?.textContent || "[]"); }
  catch { return []; }
})();
function metaFor(path: string): SourceMeta | undefined {
  return sourceIndex.find((s) => s.path === path) ?? sourceIndex.find((s) => path.startsWith(s.path + "/"));
}

function allMarks(): Item[] {
  const out: Item[] = [];
  for (let i = 0; i < localStorage.length; i++) {
    const k = localStorage.key(i);
    if (!k?.startsWith("aa-marks:")) continue;
    const path = k.slice("aa-marks:".length);
    try {
      for (const m of JSON.parse(localStorage.getItem(k) || "[]") as Mark[]) out.push({ ...m, path });
    } catch {}
  }
  return out;
}

function removeMark(path: string, id: string) {
  const k = "aa-marks:" + path;
  try {
    const marks = (JSON.parse(localStorage.getItem(k) || "[]") as Mark[]).filter((m) => m.id !== id);
    if (marks.length) localStorage.setItem(k, JSON.stringify(marks));
    else localStorage.removeItem(k);
  } catch {}
}

const savedTabKind = localStorage.getItem("aa-lib-kind");
const savedGroupBy = localStorage.getItem("aa-lib-group");
let kind: TabKind =
  savedTabKind === "note" || savedTabKind === "mistake" || savedTabKind === "saved" ? savedTabKind : "benefit";
let groupBy: GroupBy = savedGroupBy === "topic" || savedGroupBy === "person" ? savedGroupBy : "book";

const EMPTY_MSG: Record<TabKind, string> = {
  benefit: "لا فوائدَ بعد. ظلِّلْ نصًّا أثناء القراءة ثمّ اختر «فائدة» لإضافته هنا.",
  note: "لا ملاحظاتٍ بعد. ظلِّلْ نصًّا أثناء القراءة ثمّ اختر «ملاحظة» لإضافتها هنا.",
  mistake: "لا أخطاءَ بعد. ظلِّلْ نصًّا أثناء القراءة ثمّ اختر «خطأ» للإبلاغ عنه هنا.",
  saved: "لا مواضعَ محفوظة بعد. من صفحة القراءة، اضغط «حفظ» ثم «احفظ هذا الموضع» لإضافته هنا.",
};

function sendMistakes(items: Item[]) {
  const byTitle = new Map<string, Item[]>();
  for (const m of items) {
    const key = m.title || "بلا عنوان";
    if (!byTitle.has(key)) byTitle.set(key, []);
    byTitle.get(key)!.push(m);
  }
  const body = [...byTitle.entries()].map(([title, marks]) =>
    `${title}\n` + marks.map((m) => `- موضع: "${m.text}"\n  ملاحظة: ${m.note || "لا توجد ملاحظة إضافية"}`).join("\n"),
  ).join("\n\n");
  window.location.href = `mailto:admin@arthurarchive.com?subject=${encodeURIComponent("إبلاغ عن أخطاء")}&body=${encodeURIComponent(body)}`;
}

// book » chapter » باب, e.g. "زاد المستقنع، كتاب الصلاة – باب الأذان والإقامة".
// title/section may repeat the book name on flat (non-chaptered) pages — drop dupes.
function citationFor(m: Item): string {
  const book = metaFor(m.path)?.title || m.title;
  if (!book) return "";
  const parts = [book];
  if (m.title && m.title !== book) parts.push(m.title);
  if (m.section && m.section !== m.title) parts.push(m.section);
  return "من: " + parts.join("، ");
}

// Shared body-builder for a mark's quote/note/source, used by both the
// mistake tab's card grid (buildCard, boxed independently) and grouped
// benefit/note lists (buildEntry, stacked rows inside one shared box).
function fillMarkBody(container: HTMLElement, m: Item) {
  const q = document.createElement("div");
  q.className = "benefit-quote";
  q.setAttribute("data-ar", "");
  q.textContent = `«${m.text}»`;
  container.appendChild(q);
  if (m.note) { const n = document.createElement("div"); n.className = "lib-note"; n.textContent = m.note; container.appendChild(n); }
  const citation = citationFor(m);
  if (citation) { const s = document.createElement("div"); s.className = "lib-src"; s.textContent = citation; container.appendChild(s); }
}

function buildDeleteBtn(m: Item): HTMLButtonElement {
  const del = document.createElement("button");
  del.className = "lib-del";
  del.type = "button";
  del.setAttribute("aria-label", "حذف");
  del.textContent = "×";
  del.addEventListener("click", (e) => {
    e.preventDefault();
    if (!confirm("حذف هذه الفائدة من الكُناشة؟ لا يمكن التراجع.")) return;
    removeMark(m.path, m.id);
    render();
  });
  return del;
}

// citation + page (if captured — see marks.ts's pageAtRange) + a link back to
// the exact mark on its original page, same shape as the in-page share popover.
function buildShareBtn(m: Item): HTMLButtonElement {
  const share = document.createElement("button");
  share.className = "lib-share";
  share.type = "button";
  share.setAttribute("aria-label", "مشاركة");
  share.textContent = "⇅";
  share.addEventListener("click", (e) => {
    e.preventDefault();
    const parts = [citationFor(m), m.page ? `ص ${m.page}` : ""].filter(Boolean).join("، ");
    const url = `${location.origin}${m.path}#m=${m.id}`;
    const text = `"${m.text}"\n— ${parts}`;
    // url inside text: some Android share targets take only one of {text, url}
    if (navigator.share) navigator.share({ text: `${text}\n${url}` }).catch(() => {});
    else navigator.clipboard?.writeText(`${text}\n${url}`).then(() => { share.textContent = "✓"; setTimeout(() => { share.textContent = "⇅"; }, 900); });
  });
  return share;
}

// Plain copy, separate from share: share always includes a link back (and on
// devices with navigator.share opens the OS sheet instead of the clipboard),
// copy is just the quote + citation text for pasting elsewhere.
function buildCopyBtn(m: Item): HTMLButtonElement {
  const copy = document.createElement("button");
  copy.className = "lib-copy";
  copy.type = "button";
  copy.setAttribute("aria-label", "نسخ");
  copy.textContent = "⧉";
  copy.addEventListener("click", (e) => {
    e.preventDefault();
    const citation = citationFor(m);
    const text = citation ? `"${m.text}"\n— ${citation}` : `"${m.text}"`;
    navigator.clipboard?.writeText(text).then(() => {
      copy.textContent = "✓";
      setTimeout(() => { copy.textContent = "⧉"; }, 900);
    });
  });
  return copy;
}

function buildCard(m: Item): HTMLElement {
  const a = document.createElement("a");
  a.className = "lib-card";
  a.href = `${m.path}#m=${m.id}`;
  fillMarkBody(a, m);
  a.appendChild(buildCopyBtn(m));
  a.appendChild(buildShareBtn(m));
  a.appendChild(buildDeleteBtn(m));
  return a;
}

function buildGrid(items: Item[]): HTMLElement {
  const grid = document.createElement("div");
  grid.className = "card-grid u44f04b0";
  for (const m of items) grid.appendChild(buildCard(m));
  return grid;
}

// One shared bordered box per group (see .lib-group-box), entries stacked
// with dividers — not a grid of individually-boxed cards.
function buildGroupBox(items: Item[]): HTMLElement {
  const box = document.createElement("div");
  box.className = "lib-group-box";
  for (const m of items) {
    const a = document.createElement("a");
    a.className = `lib-entry k-${m.kind}`;
    a.href = `${m.path}#m=${m.id}`;
    fillMarkBody(a, m);
    a.appendChild(buildCopyBtn(m));
    a.appendChild(buildShareBtn(m));
    a.appendChild(buildDeleteBtn(m));
    box.appendChild(a);
  }
  return box;
}

// "حسب الكتاب" groups by the mark's own title (1 group per source, no lookup
// needed — always available). "حسب الموضوع"/"حسب العَلَم" need the build-time
// path→{person,topics} index (benefits.astro); a topic-less/person-less mark,
// or one whose source isn't in the index, falls into "غير مصنَّف". A mark can
// carry >1 topic, so it can legitimately appear in more than one topic group.
function groupItems(items: Item[]): [string, Item[]][] {
  const groups = new Map<string, Item[]>();
  const add = (key: string, m: Item) => { if (!groups.has(key)) groups.set(key, []); groups.get(key)!.push(m); };
  for (const m of items) {
    // group by the source's real book/poem/article title, not the mark's own
    // title — on a nested كتاب/باب chapter page m.title is the chapter, not
    // the book (see citationFor's comment)
    if (groupBy === "book") { add(metaFor(m.path)?.title || m.title || "بلا عنوان", m); continue; }
    const meta = metaFor(m.path);
    if (groupBy === "person") { add(meta?.person || "غير مصنَّف", m); continue; }
    if (meta?.topics.length) meta.topics.forEach((t) => add(t, m));
    else add("غير مصنَّف", m);
  }
  return [...groups.entries()].sort((a, b) => a[0].localeCompare(b[0], "ar"));
}

function render() {
  // Re-query rather than cache at module scope: this script is a plain
  // <script> (not a module re-run per navigation), and this page can be
  // reached via a browser back-navigation after visiting a book/article to
  // highlight text — if that restore doesn't fire astro:page-load, a stale
  // reference here would write into a detached node, leaving the visible
  // (current) list empty until a manual reload.
  const listEl = document.querySelector<HTMLElement>("[data-lib-list]");
  const groupTabs = document.querySelector<HTMLElement>("[data-lib-group-tabs]");
  if (!listEl) return;
  listEl.textContent = "";
  if (groupTabs) groupTabs.hidden = kind === "mistake" || kind === "saved";
  if (kind === "saved") {
    const saved = loadSavedPlaces().sort((a, b) => b.savedAt - a.savedAt);
    if (!saved.length) {
      const empty = document.createElement("div");
      empty.className = "lib-empty";
      empty.textContent = EMPTY_MSG.saved;
      listEl.appendChild(empty);
      return;
    }
    const box = document.createElement("div");
    box.className = "lib-group-box";
    for (const s of saved) {
      const a = document.createElement("a");
      a.className = "lib-entry k-saved";
      a.href = `${s.path}#s=${s.id}`; // marks.ts scrolls to s.scrollY on arrival
      const title = document.createElement("div");
      title.className = "lib-entry-title";
      const label = s.page ? `صفحة ${s.page}` : s.section;
      title.textContent = label ? `${s.title} — ${label}` : s.title;
      a.appendChild(title);
      const rename = document.createElement("button");
      rename.className = "lib-rename";
      rename.type = "button";
      rename.setAttribute("aria-label", "إعادة تسمية");
      rename.textContent = "✎";
      rename.addEventListener("click", (e) => {
        e.preventDefault();
        const next = prompt("اسم هذا الموضع:", s.title || "");
        if (next != null && next.trim()) { renameSavedPlace(s.id, next.trim()); render(); }
      });
      a.appendChild(rename);
      const del = document.createElement("button");
      del.className = "lib-del";
      del.type = "button";
      del.setAttribute("aria-label", "إزالة من المحفوظات");
      del.textContent = "×";
      del.addEventListener("click", (e) => { e.preventDefault(); removeSavedPlace(s.id); render(); });
      a.appendChild(del);
      box.appendChild(a);
    }
    listEl.appendChild(box);
    return;
  }
  const items = allMarks().filter((m) => m.kind === kind);
  if (kind === "mistake" && items.length) {
    const send = document.createElement("button");
    send.type = "button";
    send.className = "btn-accent lib-send";
    send.textContent = `أرسل كلّ الأخطاء (${items.length}) عبر البريد`;
    send.addEventListener("click", () => sendMistakes(items));
    listEl.appendChild(send);
  }
  if (!items.length) {
    const empty = document.createElement("div");
    empty.className = "lib-empty";
    empty.textContent = EMPTY_MSG[kind];
    listEl.appendChild(empty);
    return;
  }
  if (kind === "mistake") { listEl.appendChild(buildGrid(items)); return; }
  for (const [label, groupItemsList] of groupItems(items)) {
    const details = document.createElement("details");
    details.className = "lib-group";
    details.open = true;
    const summary = document.createElement("summary");
    const countEl = document.createElement("span"); countEl.className = "lib-group-count"; countEl.textContent = String(groupItemsList.length);
    const labelWrap = document.createElement("span"); labelWrap.className = "lib-group-label";
    const titleEl = document.createElement("span"); titleEl.className = "lib-group-title"; titleEl.textContent = label;
    labelWrap.appendChild(titleEl);
    // "حسب الكتاب" only: show the chapter under the book title, when every
    // item in the group shares one (a book group can span several chapters)
    if (groupBy === "book") {
      const chapters = new Set(groupItemsList.map((m) => m.title || ""));
      const chapter = chapters.size === 1 ? [...chapters][0] : "";
      if (chapter && chapter !== label) {
        const chapEl = document.createElement("span"); chapEl.className = "lib-group-chapter"; chapEl.textContent = chapter;
        labelWrap.appendChild(chapEl);
      }
    }
    summary.append(labelWrap, countEl);
    details.appendChild(summary);
    details.appendChild(buildGroupBox(groupItemsList));
    listEl.appendChild(details);
  }
}

// Delegated on document (not the tab containers directly) for the same
// stale-reference reason as render()'s re-query above.
document.addEventListener("click", (e) => {
  const t = e.target as HTMLElement;
  const tabBtn = t.closest<HTMLElement>("[data-lib-tabs] [data-lib-tab]");
  if (tabBtn) {
    kind = tabBtn.dataset.libTab as TabKind;
    localStorage.setItem("aa-lib-kind", kind);
    tabBtn.parentElement?.querySelectorAll<HTMLElement>("[data-lib-tab]").forEach((b) => b.setAttribute("aria-pressed", String(b === tabBtn)));
    render();
    return;
  }
  const groupBtn = t.closest<HTMLElement>("[data-lib-group-tabs] [data-lib-group]");
  if (groupBtn) {
    groupBy = groupBtn.dataset.libGroup as GroupBy;
    localStorage.setItem("aa-lib-group", groupBy);
    groupBtn.parentElement?.querySelectorAll<HTMLElement>("[data-lib-group]").forEach((b) => b.setAttribute("aria-pressed", String(b === groupBtn)));
    render();
  }
});

// Export/import all "aa-marks:*" keys as one JSON file — the only way this
// data survives a domain change or a new device, since localStorage never
// crosses origins on its own.
function exportMarks() {
  const data: Record<string, unknown> = {};
  for (let i = 0; i < localStorage.length; i++) {
    const k = localStorage.key(i);
    if (k?.startsWith("aa-marks:") || k === "aa-saved") data[k] = JSON.parse(localStorage.getItem(k) || "[]");
    // reading positions ride along so a new device resumes every book/surah
    // where the old one left off (plain string / JSON-object values)
    if (k?.startsWith("aa-pos:") || k?.startsWith("aa-book-progress:") || k === "aa-recent")
      data[k] = localStorage.getItem(k);
  }
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = `athar-kunasha-${new Date().toISOString().slice(0, 10)}.json`;
  a.click();
  URL.revokeObjectURL(a.href);
}

async function importMarks(file: File) {
  let data: Record<string, unknown>;
  try { data = JSON.parse(await file.text()); }
  catch { alert("الملف غير صالح."); return; }
  let count = 0;
  for (const [k, v] of Object.entries(data)) {
    // reading positions (exported as raw strings): restore only when this
    // device has none — never clobber a position the reader set here
    if ((k.startsWith("aa-pos:") || k.startsWith("aa-book-progress:") || k === "aa-recent") &&
        typeof v === "string") {
      if (!localStorage.getItem(k)) localStorage.setItem(k, v);
      continue;
    }
    if (!Array.isArray(v)) continue;
    if (k === "aa-saved") {
      // several places can share a path now — de-dup by id, not path; a
      // pre-multi-bookmark export has no id, so fall back to path there
      const existing = loadSavedPlaces();
      const seen = new Set(existing.map((s) => s.id || s.path));
      const merged = [...existing, ...(v as Partial<SavedPlace>[]).filter((s) => !seen.has(s.id || s.path!))];
      localStorage.setItem(k, JSON.stringify(merged));
      count += merged.length;
      continue;
    }
    if (!k.startsWith("aa-marks:")) continue;
    // merge with any existing marks on this device rather than overwrite,
    // de-duped by mark id
    const existing = JSON.parse(localStorage.getItem(k) || "[]") as Mark[];
    const seen = new Set(existing.map((m) => m.id));
    const merged = [...existing, ...(v as Mark[]).filter((m) => !seen.has(m.id))];
    localStorage.setItem(k, JSON.stringify(merged));
    count += merged.length;
  }
  render();
  alert(`تمّ الاستيراد (${count} عنصرًا في المجموع).`);
}

document.addEventListener("click", (e) => {
  const t = e.target as HTMLElement;
  if (t.closest("[data-lib-export]")) exportMarks();
  if (t.closest("[data-lib-import-btn]")) document.querySelector<HTMLInputElement>("[data-lib-import]")?.click();
});
document.addEventListener("change", (e) => {
  const input = (e.target as HTMLElement).closest<HTMLInputElement>("[data-lib-import]");
  const file = input?.files?.[0];
  if (file) importMarks(file).finally(() => { input.value = ""; });
});

// server-rendered tabs always show الفوائد/حسب الكتاب pressed — sync to
// whatever kind/groupBy localStorage actually restored above
function syncTabUI() {
  document.querySelectorAll<HTMLElement>("[data-lib-tab]").forEach((b) =>
    b.setAttribute("aria-pressed", String(b.dataset.libTab === kind)),
  );
  document.querySelectorAll<HTMLElement>("[data-lib-group]").forEach((b) =>
    b.setAttribute("aria-pressed", String(b.dataset.libGroup === groupBy)),
  );
}
syncTabUI();
render();
document.addEventListener("astro:page-load", () => { syncTabUI(); render(); });
// bfcache restore (back button) doesn't fire astro:page-load — see render()'s
// comment on why a stale reference there would otherwise show an empty list.
window.addEventListener("pageshow", (e) => { if (e.persisted) { syncTabUI(); render(); } });
