// Reading-preferences & chrome enhancement (progressive — content is fully
// readable without it). Persists to localStorage; the pre-paint inline script
// in Base.astro applies theme/scale/vnums before first paint to avoid flash.

import { stripTashkeel, toArabicDigits } from "../lib/display";

const LS = {
  theme: "aa-theme",
  scale: "aa-scale",
  tashkeel: "aa-tashkeel",
  vnums: "aa-vnums",
  mode: "aa-mode",
};

const SCALE_MIN = 0.8;
const SCALE_MAX = 1.6;
const SCALE_STEP = 0.1;

const root = document.documentElement;

function getScale(): number {
  return parseFloat(getComputedStyle(root).getPropertyValue("--reading-scale")) || 1;
}
function setScale(v: number) {
  const clamped = Math.min(SCALE_MAX, Math.max(SCALE_MIN, Math.round(v * 100) / 100));
  root.style.setProperty("--reading-scale", String(clamped));
  localStorage.setItem(LS.scale, String(clamped));
}

// --- theme ---
const THEMES = ["light", "sepia", "dark"] as const;
type Theme = (typeof THEMES)[number];

function setTheme(t: Theme) {
  if (t === "light") root.removeAttribute("data-theme");
  else root.setAttribute("data-theme", t);
  localStorage.setItem(LS.theme, t);
  syncThemeButtons(t);
}
function currentTheme(): Theme {
  return (root.getAttribute("data-theme") as Theme) || "light";
}
function syncThemeButtons(t: Theme) {
  document.querySelectorAll<HTMLElement>("[data-theme-btn]").forEach((b) => {
    b.setAttribute("aria-pressed", String(b.dataset.themeBtn === t));
  });
}

// --- tashkeel (cache full + bare HTML per element) ---
const cache = new WeakMap<Element, { full: string; bare: string }>();

function bareHtml(el: Element): string {
  const clone = el.cloneNode(true) as Element;
  const walk = document.createTreeWalker(clone, NodeFilter.SHOW_TEXT);
  let n: Node | null;
  while ((n = walk.nextNode())) n.textContent = stripTashkeel(n.textContent || "");
  return clone.innerHTML;
}
function applyTashkeel(show: boolean) {
  document.querySelectorAll<HTMLElement>("[data-ar]").forEach((el) => {
    let c = cache.get(el);
    if (!c) { c = { full: el.innerHTML, bare: bareHtml(el) }; cache.set(el, c); }
    el.innerHTML = show ? c.full : c.bare;
  });
  root.classList.toggle("no-tashkeel", !show);
  localStorage.setItem(LS.tashkeel, show ? "1" : "0");
  document.querySelectorAll<HTMLElement>('[data-toggle="tashkeel"]').forEach((b) =>
    b.setAttribute("aria-pressed", String(show)),
  );
}

// --- verse numbers ---
function applyVnums(show: boolean) {
  root.classList.toggle("hide-vnums", !show);
  localStorage.setItem(LS.vnums, show ? "1" : "0");
  document.querySelectorAll<HTMLElement>('[data-toggle="verseNums"]').forEach((b) =>
    b.setAttribute("aria-pressed", String(show)),
  );
}

// --- mobile drawer ---
function setDrawer(open: boolean) {
  const drawer = document.querySelector<HTMLElement>("[data-drawer]");
  const backdrop = document.querySelector<HTMLElement>("[data-drawer-backdrop]");
  if (!drawer || !backdrop) return;
  drawer.toggleAttribute("data-open", open);
  backdrop.toggleAttribute("data-open", open);
  document.body.style.overflow = open ? "hidden" : "";
}

// --- study modes (وضع الحفظ) ---
const MODES = ["qiraa", "sharh", "hifz", "tasmee", "ikhtibar", "muraja"];
const MODE_HINT: Record<string, string> = {
  hifz: "وضع الحفظ: النصُّ وحدَه. علِّمْ ما حفظتَه بِـ ✓.",
  tasmee: "وضع التسميع: النصُّ وحدَه بلا شرحٍ ولا حاشية.",
  ikhtibar: "وضع الاختبار: يظهرُ الصدرُ، انقرِ البيتَ لإظهار العجز.",
  muraja: "وضع المراجعة: انقرِ البيتَ لإظهار شرحِه، وعلِّمْ ما يحتاجُ مراجعةً بِـ ★.",
};
function setMode(m: string) {
  if (!MODES.includes(m)) m = "qiraa";
  root.setAttribute("data-mode", m);
  localStorage.setItem(LS.mode, m);
  document.querySelectorAll<HTMLElement>("[data-mode-btn]").forEach((b) =>
    b.setAttribute("aria-pressed", String(b.dataset.modeBtn === m)),
  );
  if (m !== "ikhtibar" && m !== "muraja")
    document.querySelectorAll(".revealed").forEach((el) => el.classList.remove("revealed"));
  const hint = document.querySelector<HTMLElement>("[data-mode-hint]");
  if (hint) { hint.textContent = MODE_HINT[m] ?? ""; hint.hidden = !MODE_HINT[m]; }
  const panel = document.querySelector<HTMLElement>("[data-progress-panel]");
  if (panel) panel.hidden = !(m === "hifz" || m === "muraja");
}

// --- memorization tracking (localStorage, no server, no spaced-rep math) ---
type MemState = { m: string[]; r: string[] };
const memKey = (id: string) => `aa-mem:${id}`;
function readMem(id: string): MemState {
  try {
    const s = JSON.parse(localStorage.getItem(memKey(id)) || "null");
    return s && Array.isArray(s.m) && Array.isArray(s.r) ? s : { m: [], r: [] };
  } catch { return { m: [], r: [] }; }
}
function writeMem(id: string, s: MemState) {
  if (!s.m.length && !s.r.length) localStorage.removeItem(memKey(id));
  else localStorage.setItem(memKey(id), JSON.stringify(s));
}
function matnContainer(): HTMLElement | null {
  return document.querySelector<HTMLElement>("[data-matn]");
}
// Build the ✓/★ control row with DOM APIs (no HTML strings → no XSS sink).
function buildMemCtl(): HTMLElement {
  const wrap = document.createElement("span");
  wrap.className = "mem-ctl";
  const mk = (attr: string, label: string, glyph: string) => {
    const b = document.createElement("button");
    b.className = "mem-btn";
    b.setAttribute(attr, "");
    b.setAttribute("aria-label", label);
    b.title = label;
    b.textContent = glyph;
    return b;
  };
  wrap.append(mk("data-mem", "حفظت", "✓"), mk("data-review", "يحتاج مراجعة", "★"));
  return wrap;
}
// study items: verses, or (prose) top-level paragraphs — tag + inject controls lazily.
function studyItems(c: HTMLElement): HTMLElement[] {
  const verses = [...c.querySelectorAll<HTMLElement>(".verse")];
  if (verses.length) return verses;
  const ps = [...c.querySelectorAll<HTMLElement>(".prose > p")];
  ps.forEach((p, i) => {
    if (!p.id) p.id = `p${i + 1}`;
    if (!p.classList.contains("matn-item")) {
      p.classList.add("matn-item");
      p.append(document.createTextNode(" "), buildMemCtl());
    }
  });
  return ps;
}
function refreshMem() {
  const c = matnContainer();
  if (!c) return;
  const id = c.dataset.matn!;
  const unit = c.dataset.unit || "بيت";
  const s = readMem(id);
  const list = studyItems(c);
  for (const el of list) {
    el.classList.toggle("is-memorized", s.m.includes(el.id));
    el.classList.toggle("needs-review", s.r.includes(el.id));
  }
  const text = document.querySelector<HTMLElement>("[data-progress-text]");
  const fill = document.querySelector<HTMLElement>("[data-progress-fill]");
  if (text) text.textContent = `حفظتَ ${toArabicDigits(s.m.length)} من ${toArabicDigits(list.length)} ${unit}`;
  if (fill) fill.style.width = list.length ? `${(s.m.length / list.length) * 100}%` : "0%";
  const rt = document.querySelector<HTMLElement>("[data-review-today]");
  if (rt) { rt.hidden = s.r.length === 0; rt.textContent = `للمراجعة: ${toArabicDigits(s.r.length)} ${unit}`; }
}
function toggleMem(item: HTMLElement, which: "m" | "r") {
  const c = matnContainer();
  if (!c) return;
  const s = readMem(c.dataset.matn!);
  const arr = s[which];
  const i = arr.indexOf(item.id);
  if (i >= 0) arr.splice(i, 1); else arr.push(item.id);
  writeMem(c.dataset.matn!, s);
  refreshMem();
}
function resetMem() {
  const c = matnContainer();
  if (!c) return;
  if (!confirm("إعادة ضبط الحفظ والمراجعة لهذا المتن؟")) return;
  localStorage.removeItem(memKey(c.dataset.matn!));
  refreshMem();
}
// homepage "مراجعة اليوم" badge — sum review flags across all matns.
function reviewTotal(): number {
  let n = 0;
  for (let i = 0; i < localStorage.length; i++) {
    const k = localStorage.key(i);
    if (k && k.startsWith("aa-mem:")) {
      try { n += (JSON.parse(localStorage.getItem(k) || "{}").r || []).length; } catch {}
    }
  }
  return n;
}

// --- action dispatch ---
const actions: Record<string, () => void> = {
  "font:inc": () => setScale(getScale() + SCALE_STEP),
  "font:dec": () => setScale(getScale() - SCALE_STEP),
  "toggle:tashkeel": () => applyTashkeel(root.classList.contains("no-tashkeel")),
  "toggle:verseNums": () => applyVnums(root.classList.contains("hide-vnums")),
  "theme:light": () => setTheme("light"),
  "theme:sepia": () => setTheme("sepia"),
  "theme:dark": () => setTheme("dark"),
  "theme:cycle": () => setTheme(THEMES[(THEMES.indexOf(currentTheme()) + 1) % THEMES.length]),
  "menu:toggle": () => setDrawer(true),
  "menu:close": () => setDrawer(false),
  "mode:qiraa": () => setMode("qiraa"),
  "mode:sharh": () => setMode("sharh"),
  "mode:hifz": () => setMode("hifz"),
  "mode:tasmee": () => setMode("tasmee"),
  "mode:ikhtibar": () => setMode("ikhtibar"),
  "mode:muraja": () => setMode("muraja"),
  "mem:reset": () => resetMem(),
};

document.addEventListener("click", (e) => {
  const el = (e.target as HTMLElement).closest<HTMLElement>("[data-action]");
  if (!el) return;
  const fn = actions[el.dataset.action || ""];
  if (fn) { e.preventDefault(); fn(); }
});
document.querySelector("[data-drawer-backdrop]")?.addEventListener("click", () => setDrawer(false));

// memorization controls (✓/★) + tap-to-reveal in اختبار/مراجعة
document.addEventListener("click", (e) => {
  const target = e.target as HTMLElement;
  const memBtn = target.closest<HTMLElement>("[data-mem]");
  const revBtn = target.closest<HTMLElement>("[data-review]");
  if (memBtn || revBtn) {
    const item = (memBtn ?? revBtn)!.closest<HTMLElement>(".verse, .matn-item");
    if (item) { e.preventDefault(); toggleMem(item, memBtn ? "m" : "r"); }
    return;
  }
  const mode = root.getAttribute("data-mode");
  if (mode === "ikhtibar" || mode === "muraja") {
    if (target.closest("button, a")) return; // leave controls/links alone
    target.closest<HTMLElement>(".verse, .matn-item")?.classList.toggle("revealed");
  }
});

// --- reading progress bar ---
const bar = document.querySelector<HTMLElement>("[data-progress]");
if (bar) {
  const update = () => {
    const h = document.documentElement.scrollHeight - window.innerHeight;
    bar.style.width = `${h > 0 ? (window.scrollY / h) * 100 : 0}%`;
  };
  window.addEventListener("scroll", update, { passive: true });
  window.addEventListener("resize", update);
  update();
}

// --- sync control states from storage on load (values already applied pre-paint) ---
syncThemeButtons(currentTheme());
applyVnums(localStorage.getItem(LS.vnums) !== "0");
if (localStorage.getItem(LS.tashkeel) === "0") applyTashkeel(false);
else document.querySelectorAll<HTMLElement>('[data-toggle="tashkeel"]').forEach((b) => b.setAttribute("aria-pressed", "true"));

// study mode + memorization init
setMode(localStorage.getItem(LS.mode) || "qiraa");
refreshMem();
const reviewHome = document.querySelector<HTMLElement>("[data-review-home]");
if (reviewHome) {
  const n = reviewTotal();
  if (n > 0) {
    reviewHome.hidden = false;
    const slot = reviewHome.querySelector("[data-review-home-n]");
    if (slot) slot.textContent = toArabicDigits(n);
  }
}
