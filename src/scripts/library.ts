// Builds كُناشتي (/benefits) from the device's saved marks. Reads every
// "aa-marks:<path>" key, flattens, and renders the chosen kind. Client-only.
// The "mistake" tab additionally gets a send button that batches every
// mistake mark (across all pages) into one mailto — the only place mistakes
// leave the device, unlike benefit/note which just stay in the list.

type Kind = "mistake" | "benefit" | "note";
type GroupBy = "book" | "topic" | "person";
interface Mark { id: string; kind: Kind; text: string; note?: string; title?: string }
interface Item extends Mark { path: string }
interface SourceMeta { path: string; person: string; topics: string[] }

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

let kind: Kind = "benefit";
let groupBy: GroupBy = "book";

const EMPTY_MSG: Record<Kind, string> = {
  benefit: "لا فوائدَ بعد. ظلِّلْ نصًّا أثناء القراءة ثمّ اختر «فائدة» لإضافته هنا.",
  note: "لا ملاحظاتٍ بعد. ظلِّلْ نصًّا أثناء القراءة ثمّ اختر «ملاحظة» لإضافتها هنا.",
  mistake: "لا أخطاءَ بعد. ظلِّلْ نصًّا أثناء القراءة ثمّ اختر «خطأ» للإبلاغ عنه هنا.",
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
  window.location.href = `mailto:admin@ahlalathar.com?subject=${encodeURIComponent("إبلاغ عن أخطاء")}&body=${encodeURIComponent(body)}`;
}

function buildCard(m: Item): HTMLElement {
  const a = document.createElement("a");
  a.className = "lib-card";
  a.href = `${m.path}#m=${m.id}`;

  const q = document.createElement("div");
  q.className = "benefit-quote";
  q.setAttribute("data-ar", "");
  q.textContent = `«${m.text}»`;
  a.appendChild(q);

  if (m.note) { const n = document.createElement("div"); n.className = "lib-note"; n.textContent = m.note; a.appendChild(n); }
  if (m.title) { const s = document.createElement("div"); s.className = "lib-src"; s.textContent = "من: " + m.title; a.appendChild(s); }

  const del = document.createElement("button");
  del.className = "lib-del";
  del.type = "button";
  del.setAttribute("aria-label", "حذف");
  del.textContent = "×";
  del.addEventListener("click", (e) => { e.preventDefault(); removeMark(m.path, m.id); render(); });
  a.appendChild(del);
  return a;
}

function buildGrid(items: Item[]): HTMLElement {
  const grid = document.createElement("div");
  grid.className = "card-grid u44f04b0";
  for (const m of items) grid.appendChild(buildCard(m));
  return grid;
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
    if (groupBy === "book") { add(m.title || "بلا عنوان", m); continue; }
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
  const items = allMarks().filter((m) => m.kind === kind);
  listEl.textContent = "";
  if (groupTabs) groupTabs.hidden = kind === "mistake";
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
    const labelEl = document.createElement("span"); labelEl.textContent = label;
    const countEl = document.createElement("span"); countEl.className = "faint"; countEl.textContent = ` (${groupItemsList.length})`;
    summary.append(labelEl, countEl);
    details.appendChild(summary);
    details.appendChild(buildGrid(groupItemsList));
    listEl.appendChild(details);
  }
}

// Delegated on document (not the tab containers directly) for the same
// stale-reference reason as render()'s re-query above.
document.addEventListener("click", (e) => {
  const t = e.target as HTMLElement;
  const tabBtn = t.closest<HTMLElement>("[data-lib-tabs] [data-lib-tab]");
  if (tabBtn) {
    kind = tabBtn.dataset.libTab as Kind;
    tabBtn.parentElement?.querySelectorAll<HTMLElement>("[data-lib-tab]").forEach((b) => b.setAttribute("aria-pressed", String(b === tabBtn)));
    render();
    return;
  }
  const groupBtn = t.closest<HTMLElement>("[data-lib-group-tabs] [data-lib-group]");
  if (groupBtn) {
    groupBy = groupBtn.dataset.libGroup as GroupBy;
    groupBtn.parentElement?.querySelectorAll<HTMLElement>("[data-lib-group]").forEach((b) => b.setAttribute("aria-pressed", String(b === groupBtn)));
    render();
  }
});

render();
document.addEventListener("astro:page-load", render);
// bfcache restore (back button) doesn't fire astro:page-load — see render()'s
// comment on why a stale reference there would otherwise show an empty list.
window.addEventListener("pageshow", (e) => { if (e.persisted) render(); });
