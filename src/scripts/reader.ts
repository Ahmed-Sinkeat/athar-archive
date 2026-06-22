// Reading-preferences & chrome enhancement (progressive — content is fully
// readable without it). Persists to localStorage; the pre-paint inline script
// in Base.astro applies theme/scale/vnums before first paint to avoid flash.

import { stripTashkeel } from "../lib/display";

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

// --- study modes — قراءة (plain) + اختبار (tap a verse to reveal its عجز) ---
const MODES = ["qiraa", "ikhtibar"];
const MODE_HINT: Record<string, string> = {
  ikhtibar: "وضع الاختبار: يظهرُ الصدرُ، انقرِ البيتَ لإظهار العجز.",
};
function setMode(m: string) {
  if (!MODES.includes(m)) m = "qiraa";
  root.setAttribute("data-mode", m);
  localStorage.setItem(LS.mode, m);
  document.querySelectorAll<HTMLElement>("[data-mode-btn]").forEach((b) =>
    b.setAttribute("aria-pressed", String(b.dataset.modeBtn === m)),
  );
  if (m !== "ikhtibar")
    document.querySelectorAll(".revealed").forEach((el) => el.classList.remove("revealed"));
  const hint = document.querySelector<HTMLElement>("[data-mode-hint]");
  if (hint) { hint.textContent = MODE_HINT[m] ?? ""; hint.hidden = !MODE_HINT[m]; }
}

// --- topbar search + popovers ---
const topsearch = document.querySelector<HTMLFormElement>("[data-topsearch]");
const tsInput = document.querySelector<HTMLInputElement>("[data-topsearch-input]");
const filterPop = document.querySelector<HTMLElement>("[data-filter-pop]");
const settingsPop = document.querySelector<HTMLElement>("[data-settings-pop]");

function isSearchOpen(): boolean {
  return !!topsearch?.classList.contains("is-open");
}
function openSearch() {
  topsearch?.classList.add("is-open");
  tsInput?.focus();
}
function closeSearch() {
  if (tsInput && tsInput.value.trim()) return; // keep open while a query is typed
  topsearch?.classList.remove("is-open");
}
function popBtns(action: string) {
  return document.querySelectorAll<HTMLElement>(`[data-action="${action}"]`);
}
function closeAllPops() {
  [filterPop, settingsPop].forEach((p) => { if (p) p.hidden = true; });
  ["search:filter", "settings:toggle"].forEach((a) => popBtns(a).forEach((b) => b.setAttribute("aria-expanded", "false")));
}
function togglePop(pop: HTMLElement | null, action: string) {
  if (!pop) return;
  const willOpen = pop.hidden;
  closeAllPops();
  pop.hidden = !willOpen;
  popBtns(action).forEach((b) => b.setAttribute("aria-expanded", String(willOpen)));
}
function buildSearchUrl(): string {
  const p = new URLSearchParams();
  const q = (tsInput?.value || "").trim();
  if (q) p.set("q", q);
  const checked = (sel: string) =>
    [...(filterPop?.querySelectorAll<HTMLInputElement>(`${sel} input:checked`) || [])].map((i) => i.value);
  const types = checked("[data-filter-types]");
  if (types.length) p.set("types", types.join(","));
  const persons = checked("[data-filter-person]");
  if (persons.length) p.set("person", persons.join(","));
  const subjects = checked("[data-filter-subject]");
  if (subjects.length) p.set("subject", subjects.join(","));
  const qs = p.toString();
  return "/search" + (qs ? `?${qs}` : "");
}
function refreshFilterIndicator() {
  const active =
    !!filterPop?.querySelector("[data-filter-types] input:checked") ||
    !!filterPop?.querySelector("[data-filter-person] input:checked") ||
    !!filterPop?.querySelector("[data-filter-subject] input:checked");
  document.querySelector(".topsearch-filter")?.classList.toggle("has-active", active);
}

// Type-to-filter the (potentially long) عَلَم / موضوع checklists. Matches on the
// visible label text; checked boxes stay checked even while hidden.
function wireChecklistSearch(searchSel: string, listSel: string) {
  const inp = document.querySelector<HTMLInputElement>(searchSel);
  const list = filterPop?.querySelector<HTMLElement>(listSel);
  if (!inp || !list) return;
  inp.addEventListener("input", () => {
    const q = inp.value.trim();
    list.querySelectorAll<HTMLElement>(".pop-check").forEach((lab) => {
      const name = lab.getAttribute("data-name") || lab.textContent || "";
      lab.classList.toggle("is-hidden", q !== "" && !name.includes(q));
    });
  });
}
wireChecklistSearch("[data-filter-person-search]", "[data-filter-person]");
wireChecklistSearch("[data-filter-subject-search]", "[data-filter-subject]");

// Enter in the field (no go-button click) submits; collapsed → just open.
topsearch?.addEventListener("submit", (e) => {
  e.preventDefault();
  if (!isSearchOpen()) { openSearch(); return; }
  location.href = buildSearchUrl();
});
filterPop?.addEventListener("change", refreshFilterIndicator);

// dismiss popovers / collapse search on outside click + Escape
document.addEventListener("click", (e) => {
  const t = e.target as HTMLElement;
  if (!t.closest("[data-filter-pop]") && !t.closest('[data-action="search:filter"]')) {
    if (filterPop && !filterPop.hidden) { filterPop.hidden = true; popBtns("search:filter").forEach((b) => b.setAttribute("aria-expanded", "false")); }
  }
  if (!t.closest("[data-settings-pop]") && !t.closest('[data-action="settings:toggle"]')) {
    if (settingsPop && !settingsPop.hidden) { settingsPop.hidden = true; popBtns("settings:toggle").forEach((b) => b.setAttribute("aria-expanded", "false")); }
  }
  if (!t.closest("[data-topsearch]")) closeSearch();
});
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape") { closeAllPops(); topsearch?.classList.remove("is-open"); }
});
refreshFilterIndicator();

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
  // closed → open the bar; open → just close it (Enter in the field runs the search).
  "search:toggle": () => { if (!isSearchOpen()) openSearch(); else topsearch?.classList.remove("is-open"); },
  "search:filter": () => togglePop(filterPop, "search:filter"),
  "search:apply": () => { location.href = buildSearchUrl(); },
  "settings:toggle": () => togglePop(settingsPop, "settings:toggle"),
  "mode:qiraa": () => setMode("qiraa"),
  "mode:ikhtibar": () => setMode("ikhtibar"),
};

document.addEventListener("click", (e) => {
  const el = (e.target as HTMLElement).closest<HTMLElement>("[data-action]");
  if (!el) return;
  const fn = actions[el.dataset.action || ""];
  if (fn) { e.preventDefault(); fn(); }
});
document.addEventListener("click", (e) => {
  if ((e.target as HTMLElement).closest("[data-drawer-backdrop]")) setDrawer(false);
});

// --- browse «عرض الكل» toggle + flat-list sort (delegated → survives transitions) ---
function applySort(container: HTMLElement) {
  const list = container.querySelector<HTMLElement>(".flat-list");
  if (!list) return;
  const key = container.dataset.sort || "title";
  const dir = container.dataset.dir === "desc" ? -1 : 1;
  [...list.children]
    .sort((a, b) => {
      const A = a as HTMLElement, B = b as HTMLElement;
      if (key === "year") {
        const na = A.dataset.year ? +A.dataset.year : NaN;
        const nb = B.dataset.year ? +B.dataset.year : NaN;
        if (isNaN(na) && isNaN(nb)) return 0;
        if (isNaN(na)) return 1; // undated always last
        if (isNaN(nb)) return -1;
        return (na - nb) * dir;
      }
      return (A.dataset.title || "").localeCompare(B.dataset.title || "", "ar") * dir;
    })
    .forEach((li) => list.appendChild(li));
}
document.addEventListener("click", (e) => {
  const t = e.target as HTMLElement;
  const keyBtn = t.closest<HTMLElement>("[data-sort-key]");
  if (keyBtn) {
    const c = keyBtn.closest<HTMLElement>("[data-sortable]");
    if (c) {
      c.dataset.sort = keyBtn.dataset.sortKey;
      c.querySelectorAll<HTMLElement>("[data-sort-key]").forEach((b) => b.setAttribute("aria-pressed", String(b === keyBtn)));
      applySort(c);
    }
    return;
  }
  const dirBtn = t.closest<HTMLElement>("[data-sort-dir]");
  if (dirBtn) {
    const c = dirBtn.closest<HTMLElement>("[data-sortable]");
    if (c) {
      c.dataset.dir = c.dataset.dir === "desc" ? "asc" : "desc";
      dirBtn.textContent = c.dataset.dir === "desc" ? "↓" : "↑";
      applySort(c);
    }
    return;
  }
  const flatBtn = t.closest<HTMLElement>("[data-flat-toggle]");
  if (flatBtn) {
    const wrap = flatBtn.closest<HTMLElement>("[data-browse]");
    if (wrap) flatBtn.textContent = wrap.classList.toggle("show-flat") ? "عرض مُجمَّع" : "عرض الكل";
  }
});

// tap-to-reveal the عجز in اختبار mode
document.addEventListener("click", (e) => {
  if (root.getAttribute("data-mode") !== "ikhtibar") return;
  const target = e.target as HTMLElement;
  if (target.closest("button, a")) return; // leave controls/links alone
  target.closest<HTMLElement>(".verse")?.classList.toggle("revealed");
});

// --- prose books: wrap each شرح phrase inline (poems mark server-side; prose
// gets marked here from hidden .ann-pack[data-phrase] blocks). First matching
// text node in a .prose wins; matching ignores tashkeel. Re-run per navigation. ---
function enhanceProse() {
  const packs = document.querySelectorAll<HTMLElement>(".ann-pack[data-phrase]");
  if (!packs.length) return;
  const proseEls = [...document.querySelectorAll<HTMLElement>(".prose")];
  if (!proseEls.length) return;

  function wrapIn(root: HTMLElement, needle: string, packId: string): boolean {
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    let node: Node | null;
    while ((node = walker.nextNode())) {
      if ((node.parentElement as HTMLElement | null)?.closest(".ann-mark, a")) continue;
      const text = node.nodeValue || "";
      let stripped = "";
      const map: number[] = [];
      for (let i = 0; i < text.length; i++) {
        const s = stripTashkeel(text[i]);
        if (s) { stripped += s; for (let k = 0; k < s.length; k++) map.push(i); }
      }
      const idx = stripped.indexOf(needle);
      if (idx < 0) continue;
      const start = map[idx];
      let end = map[idx + needle.length - 1] + 1;
      while (end < text.length && !stripTashkeel(text[end])) end++;
      const frag = document.createDocumentFragment();
      if (start > 0) frag.appendChild(document.createTextNode(text.slice(0, start)));
      const a = document.createElement("a");
      a.className = "ann-mark"; a.href = `#${packId}`; a.setAttribute("data-ann", packId); a.setAttribute("aria-haspopup", "dialog");
      a.textContent = text.slice(start, end);
      frag.appendChild(a);
      if (end < text.length) frag.appendChild(document.createTextNode(text.slice(end)));
      node.parentNode?.replaceChild(frag, node);
      return true;
    }
    return false;
  }

  packs.forEach((pack) => {
    const needle = stripTashkeel(pack.getAttribute("data-phrase") || "").replace(/\s+/g, " ").trim();
    if (!needle) return;
    for (const prose of proseEls) if (wrapIn(prose, needle, pack.id)) break;
  });
}

// --- inline شرح chooser (annotation popover) ---
// Marks are <a class="ann-mark" data-ann="ann-<anchor>">; the hidden
// [data-ann-pack] sibling holds one .ann-entry per شرح. One entry → show it;
// several → show a chooser menu first. All data is build-time (no network).
(() => {
  let pop: HTMLElement | null = null;
  let activeMark: HTMLElement | null = null;
  let currentPack: HTMLElement | null = null;
  let justLongPressed = false;

  function ensurePop(): HTMLElement {
    if (pop) return pop;
    pop = document.createElement("div");
    pop.className = "ann-pop";
    pop.setAttribute("role", "dialog");
    pop.hidden = true;
    document.body.appendChild(pop);
    return pop;
  }
  function close() {
    if (pop) { pop.hidden = true; pop.classList.remove("is-shown"); }
    if (activeMark) { activeMark.classList.remove("ann-active"); activeMark = null; }
    currentPack = null;
  }
  function position() {
    if (!pop || !activeMark) return;
    const r = activeMark.getBoundingClientRect();
    const pw = pop.offsetWidth, ph = pop.offsetHeight;
    const vw = document.documentElement.clientWidth, vh = window.innerHeight;
    let left = r.left + r.width / 2 - pw / 2;
    left = Math.max(8, Math.min(left, vw - pw - 8));
    let top = r.bottom + 8;
    if (top + ph > vh - 8) top = Math.max(8, r.top - ph - 8);
    pop.style.left = `${left}px`;
    pop.style.top = `${top}px`;
  }
  function head(title: string, withBack: boolean): HTMLElement {
    const h = document.createElement("div");
    h.className = "ann-pop-head";
    if (withBack) {
      const back = document.createElement("button");
      back.type = "button"; back.className = "ann-pop-back"; back.textContent = "‹ الشروح";
      back.addEventListener("click", (ev) => { ev.stopPropagation(); if (currentPack) renderMenu(currentPack); });
      h.appendChild(back);
    }
    const t = document.createElement("span");
    t.className = "ann-pop-kind"; t.textContent = title;
    const x = document.createElement("button");
    x.type = "button"; x.className = "ann-pop-close"; x.setAttribute("aria-label", "إغلاق"); x.textContent = "×";
    x.addEventListener("click", close);
    h.append(t, x);
    return h;
  }
  function renderEntry(entry: HTMLElement, withBack: boolean) {
    const p = ensurePop();
    p.innerHTML = "";
    p.append(head(entry.getAttribute("data-label") || "شرح", withBack));
    const body = document.createElement("div");
    body.className = "ann-pop-body"; body.setAttribute("data-ar", "");
    // clone the already-sanitized (build-time rehype-sanitize) note DOM — no
    // innerHTML, no runtime parsing.
    const src0 = entry.querySelector(".ann-entry-body");
    if (src0) body.append(...[...src0.cloneNode(true).childNodes]);
    p.appendChild(body);
    const src = entry.querySelector<HTMLAnchorElement>(".ann-source-link");
    if (src) {
      const a = document.createElement("a");
      a.className = "ann-source-link"; a.href = src.href; a.textContent = src.textContent || "";
      p.appendChild(a);
    }
    position();
  }
  function renderMenu(pack: HTMLElement) {
    const entries = [...pack.querySelectorAll<HTMLElement>(".ann-entry")];
    const p = ensurePop();
    p.innerHTML = "";
    p.append(head("اختر الشرح", false));
    const menu = document.createElement("div");
    menu.className = "ann-menu";
    entries.forEach((en) => {
      const b = document.createElement("button");
      b.type = "button";
      b.className = "ann-menu-item " + (en.className.match(/k-\w+/)?.[0] ?? "");
      b.textContent = en.getAttribute("data-label") || "شرح";
      b.addEventListener("click", (ev) => { ev.stopPropagation(); renderEntry(en, true); });
      menu.appendChild(b);
    });
    p.appendChild(menu);
    position();
  }
  function open(mark: HTMLElement) {
    const id = mark.getAttribute("data-ann");
    const pack = id ? document.getElementById(id) : null;
    if (!pack) return;
    if (activeMark && activeMark !== mark) activeMark.classList.remove("ann-active");
    activeMark = mark; mark.classList.add("ann-active");
    currentPack = pack;
    const p = ensurePop();
    p.hidden = false;
    const entries = [...pack.querySelectorAll<HTMLElement>(".ann-entry")];
    if (entries.length > 1) renderMenu(pack);
    else renderEntry(entries[0], false);
    requestAnimationFrame(() => p.classList.add("is-shown")); // fade-in

  }

  document.addEventListener("click", (e) => {
    const mark = (e.target as HTMLElement).closest<HTMLElement>(".ann-mark");
    if (mark) {
      e.preventDefault();
      if (justLongPressed) { justLongPressed = false; return; }
      if (activeMark === mark && pop && !pop.hidden) close();
      else open(mark);
      return;
    }
    if (pop && !pop.hidden && !(e.target as HTMLElement).closest(".ann-pop")) close();
  });

  let pressTimer: number | undefined;
  document.addEventListener("touchstart", (e) => {
    const mark = (e.target as HTMLElement).closest<HTMLElement>(".ann-mark");
    if (!mark) return;
    pressTimer = window.setTimeout(() => { justLongPressed = true; open(mark); }, 420);
  }, { passive: true });
  const cancelPress = () => { if (pressTimer) { clearTimeout(pressTimer); pressTimer = undefined; } };
  document.addEventListener("touchmove", cancelPress, { passive: true });
  document.addEventListener("touchend", cancelPress);

  document.addEventListener("keydown", (e) => { if (e.key === "Escape") close(); });
  window.addEventListener("scroll", () => { if (pop && !pop.hidden) close(); }, { passive: true });
  window.addEventListener("resize", position);
})();

// --- audio recitation switcher (متون/منظومات with multiple recordings) ---
// Delegated so it keeps working on content swapped in by view transitions.
document.addEventListener("change", (e) => {
  const sel = (e.target as HTMLElement).closest<HTMLSelectElement>("[data-audio-pick]");
  if (!sel) return;
  const fig = sel.closest<HTMLElement>("[data-audio]");
  const opt = sel.selectedOptions[0];
  if (!fig || !opt) return;
  const audio = fig.querySelector<HTMLAudioElement>("[data-audio-el]");
  const src = fig.querySelector<HTMLSourceElement>("[data-audio-source]");
  const dl = fig.querySelector<HTMLAnchorElement>("[data-audio-dl]");
  if (src) { src.src = opt.dataset.url || ""; src.type = opt.dataset.type || ""; }
  if (audio) audio.load();
  if (dl) dl.href = opt.dataset.url || "";
});

// --- reading progress bar (header persists across transitions; recompute per page) ---
const bar = document.querySelector<HTMLElement>("[data-progress]");
function updateProgress() {
  if (!bar) return;
  const h = document.documentElement.scrollHeight - window.innerHeight;
  bar.style.width = `${h > 0 ? (window.scrollY / h) * 100 : 0}%`;
}
window.addEventListener("scroll", updateProgress, { passive: true });
window.addEventListener("resize", updateProgress);

// --- sync control states from storage on load (values already applied pre-paint) ---
syncThemeButtons(currentTheme());
applyVnums(localStorage.getItem(LS.vnums) !== "0");
if (localStorage.getItem(LS.tashkeel) === "0") applyTashkeel(false);
else document.querySelectorAll<HTMLElement>('[data-toggle="tashkeel"]').forEach((b) => b.setAttribute("aria-pressed", "true"));

// study mode init
setMode(localStorage.getItem(LS.mode) || "qiraa");

// Highlight the nav item for the current page (header is persisted, so its
// aria-current would otherwise stay on whatever page first rendered it).
function updateActiveNav() {
  const cur = document.querySelector("main")?.getAttribute("data-active-nav") || "";
  document.querySelectorAll<HTMLElement>("a[data-nav]").forEach((a) =>
    a.dataset.nav === cur && cur ? a.setAttribute("aria-current", "page") : a.removeAttribute("aria-current"),
  );
}

// Per-page setup: re-run content enhancers after each view-transition navigation
// (and once on first load). Chrome wiring above runs once; delegated listeners
// and the persisted header keep working without re-binding.
function onPage() {
  enhanceProse();
  if (localStorage.getItem(LS.tashkeel) === "0") applyTashkeel(false); // re-bare new content
  updateActiveNav();
  updateProgress();
}
onPage();
document.addEventListener("astro:page-load", onPage);
