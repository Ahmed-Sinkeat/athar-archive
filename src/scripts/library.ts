// Builds كُناشتي (/benefits) from the device's saved marks. Reads every
// "aa-marks:<path>" key, flattens, and renders the chosen kind. Client-only.
// The "mistake" tab additionally gets a send button that batches every
// mistake mark (across all pages) into one mailto — the only place mistakes
// leave the device, unlike benefit/note which just stay in the list.

type Kind = "mistake" | "benefit" | "note";
interface Mark { id: string; kind: Kind; text: string; note?: string; title?: string }
interface Item extends Mark { path: string }

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

const listEl = document.querySelector<HTMLElement>("[data-lib-list]");
const tabs = document.querySelector<HTMLElement>("[data-lib-tabs]");
let kind: Kind = "benefit";

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

function render() {
  if (!listEl) return;
  const items = allMarks().filter((m) => m.kind === kind);
  listEl.textContent = "";
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
  const grid = document.createElement("div");
  grid.className = "card-grid u44f04b0";
  for (const m of items) {
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

    grid.appendChild(a);
  }
  listEl.appendChild(grid);
}

tabs?.addEventListener("click", (e) => {
  const btn = (e.target as HTMLElement).closest<HTMLElement>("[data-lib-tab]");
  if (!btn) return;
  kind = btn.dataset.libTab as Kind;
  tabs.querySelectorAll<HTMLElement>("[data-lib-tab]").forEach((b) => b.setAttribute("aria-pressed", String(b === btn)));
  render();
});

render();
document.addEventListener("astro:page-load", render);
