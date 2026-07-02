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
  pages: "aa-pages",
};

const SCALE_MIN = 0.8;
const SCALE_MAX = 1.6;
const SCALE_STEP = 0.1;

let root = document.documentElement;

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

// --- page separators (عرض الصفحات / عرض مستمر) ---
function applyPages(show: boolean) {
  root.classList.toggle("pages-flow", !show);
  localStorage.setItem(LS.pages, show ? "paged" : "flow");
  document.querySelectorAll<HTMLElement>('[data-toggle="pages"]').forEach((b) =>
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
const MODE_HINT: Record<string, string> = {}; // اختبار is self-evident — no hint text
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
  "toggle:pages": () => applyPages(root.classList.contains("pages-flow")),
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
  "search:next": () => { const q = document.querySelector<HTMLInputElement>("[data-inpage-search]")?.value.trim(); if (q) (window as any).find(q, false, false, true, false, true, false); },
  "search:prev": () => { const q = document.querySelector<HTMLInputElement>("[data-inpage-search]")?.value.trim(); if (q) (window as any).find(q, false, true, true, false, true, false); },
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

// --- footnote popover (markdown [^refs] + EPUB <sup data-fn> refs) ---
let fnPop: HTMLElement | null = null;
function positionFnPop(anchor: HTMLElement) {
  if (!fnPop) return;
  fnPop.hidden = false;
  const rect = anchor.getBoundingClientRect();
  fnPop.style.top = `${rect.bottom + window.scrollY + 8}px`;
  let left = rect.left + rect.width / 2 - fnPop.offsetWidth / 2;
  left = Math.max(8, Math.min(left, window.innerWidth - fnPop.offsetWidth - 8));
  fnPop.style.left = `${left}px`;
}
function ensureFnPop(): HTMLElement {
  // ClientRouter swaps <body> on navigation, detaching a previously-created popover,
  // so recreate it when it's gone OR no longer in the document (footnotes were dead
  // after navigating to another page/chapter).
  if (!fnPop || !fnPop.isConnected) {
    fnPop = document.createElement("div");
    fnPop.className = "popover fn-popover";
    document.body.appendChild(fnPop);
  }
  return fnPop;
}
let activeFnRef: HTMLElement | null = null;
document.addEventListener("click", (e) => {
  const t = e.target as HTMLElement;
  // standard markdown footnote refs
  const ref = t.closest<HTMLAnchorElement>("[data-footnote-ref]");
  if (ref) {
    e.preventDefault();
    // Paginated book content: this ref was stamped (book/[slug]/[chapter].astro)
    // with which page's notes sheet it now belongs to — let the ann-sheet click
    // listener below handle opening that instead of the plain popover, so it's
    // grouped with the page's other footnotes and clearly labelled which page.
    if (ref.dataset.sepPage) return;
    const id = ref.getAttribute("href")?.slice(1);
    const target = id ? document.getElementById(id) : null;
    if (target) {
      const pop = ensureFnPop();
      const clone = target.cloneNode(true) as HTMLElement;
      clone.querySelector(".data-footnote-backref")?.remove();
      // Adjacent refs (e.g. a hadith citing 3 sources back to back, [^12][^13][^14])
      // sit only ~2px apart — without a number label the popup can't be told
      // apart from its neighbours', so label it with the same number the
      // superscript itself shows, and highlight which ref is currently open.
      const label = document.createElement("div");
      label.className = "fn-popover-num";
      label.textContent = ref.textContent || "";
      pop.replaceChildren(label, ...Array.from(clone.childNodes));
      activeFnRef?.classList.remove("fn-ref-active");
      ref.classList.add("fn-ref-active");
      activeFnRef = ref;
      positionFnPop(ref);
    }
    return;
  }
  // EPUB inline footnote sups with embedded note text (data-note set at server render)
  const fnSup = t.closest<HTMLElement>("sup[data-fn][data-note]");
  if (fnSup && !fnSup.dataset.sepPage) {
    e.preventDefault();
    const pop = ensureFnPop();
    // ponytail: textContent not innerHTML — note is plain text from the book
    pop.textContent = fnSup.dataset.note || "";
    positionFnPop(fnSup);
    return;
  }
  if (fnPop && !fnPop.hidden && !t.closest(".fn-popover")) {
    fnPop.hidden = true;
    activeFnRef?.classList.remove("fn-ref-active");
    activeFnRef = null;
  }
});

// --- in-page search ---
const inpageSearch = document.querySelector<HTMLInputElement>("[data-inpage-search]");
if (inpageSearch) {
  inpageSearch.addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      const q = inpageSearch.value.trim();
      if (q) (window as any).find(q, false, e.shiftKey, true, false, true, false);
    }
  });
}

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
        if (isNaN(na)) return 1;
        if (isNaN(nb)) return -1;
        return (na - nb) * dir;
      }
      if (key === "works") {
        const na = A.dataset.works ? +A.dataset.works : 0;
        const nb = B.dataset.works ? +B.dataset.works : 0;
        return (nb - na) * dir; // default: most works first
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

// Browse page in-page filter: person + topic + kind <select> dropdowns on listing pages.
// Works on BrowseGroups flat list (data-person / data-topics / data-kind on each <li>),
// the lesson list in series/index.astro, and the masail accordion ([data-masail-browse]).
document.addEventListener("change", (e) => {
  const sel = (e.target as HTMLElement).closest<HTMLSelectElement>("[data-filter-key]");
  if (!sel) return;
  const key = sel.dataset.filterKey!;
  const val = sel.value;

  // flat-list (BrowseGroups + lessons)
  const scope = sel.closest("[data-browse]") ?? sel.closest(".wrap-mid");
  if (scope) {
    scope.querySelectorAll<HTMLElement>(".flat-list li").forEach((li) => {
      if (!val) { li.classList.remove("is-hidden"); return; }
      const match = key === "kind"
        ? li.dataset.kind === val
        : key === "person"
          ? li.dataset.person === val
          : (li.dataset.topics ?? "").split(",").includes(val);
      li.classList.toggle("is-hidden", !match);
    });
  }

  // masail accordion ([data-masail-browse])
  const masailScope = sel.closest<HTMLElement>("[data-masail-browse]");
  if (masailScope) {
    masailScope.querySelectorAll<HTMLElement>(".masail-list li[data-person]").forEach((li) => {
      if (!val) { li.classList.remove("is-hidden"); return; }
      const match = key === "person"
        ? li.dataset.person === val
        : (li.dataset.topics ?? "").split(",").includes(val);
      li.classList.toggle("is-hidden", !match);
    });
    // collapse empty topics then empty subjects
    masailScope.querySelectorAll<HTMLElement>(".masail-topic").forEach((dt) => {
      const visible = [...dt.querySelectorAll<HTMLElement>(".masail-list li")].some((li) => !li.classList.contains("is-hidden"));
      dt.style.display = (visible || !val) ? "" : "none";
    });
    masailScope.querySelectorAll<HTMLElement>(".masail-subject").forEach((ds) => {
      const visible = [...ds.querySelectorAll<HTMLElement>(".masail-topic")].some((t) => t.style.display !== "none");
      ds.style.display = (visible || !val) ? "" : "none";
    });
  }
});

// --- local in-page search / filtering for listing pages ---
document.addEventListener("submit", (e) => {
  const form = e.target as HTMLFormElement;
  if (form.classList.contains("section-search")) {
    e.preventDefault(); // Prevent going to Google site search from listing pages!
  }
});

document.addEventListener("input", (e) => {
  const input = e.target as HTMLInputElement;
  if (!input.classList.contains("section-search-input")) return;
  
  const query = stripTashkeel(input.value.trim().toLowerCase());
  const scope = input.closest(".wrap-mid") || document.body;
  
  // 1. Filter flat list items (SortableList)
  scope.querySelectorAll<HTMLElement>(".flat-list li").forEach((li) => {
    const title = stripTashkeel((li.dataset.title || li.textContent || "").toLowerCase());
    const matches = title.includes(query);
    li.classList.toggle("is-search-hidden", !matches);
  });

  // 2. Filter card grids (BrowseGroups)
  scope.querySelectorAll<HTMLElement>(".card-grid .card, .browse-cards .card").forEach((card) => {
    const titleText = card.querySelector(".card-title")?.textContent || card.textContent || "";
    const title = stripTashkeel(titleText.toLowerCase());
    const matches = title.includes(query);
    card.classList.toggle("is-search-hidden", !matches);
  });

  // 3. Filter masail list items
  scope.querySelectorAll<HTMLElement>(".masail-list li").forEach((li) => {
    const title = stripTashkeel((li.textContent || "").toLowerCase());
    const matches = title.includes(query);
    li.classList.toggle("is-search-hidden", !matches);
  });

  // Hide empty topics and empty subjects in the accordion view (BrowseGroups + Questions) when searching
  scope.querySelectorAll<HTMLElement>(".masail-topic").forEach((dt) => {
    const hasVisibleCards = [...dt.querySelectorAll<HTMLElement>(".card")].some((c) => !c.classList.contains("is-search-hidden"));
    const hasVisibleLi = [...dt.querySelectorAll<HTMLElement>(".masail-list li")].some((li) => !li.classList.contains("is-search-hidden") && !li.classList.contains("is-hidden"));
    
    const hasContent = dt.querySelectorAll<HTMLElement>(".card, .masail-list li").length > 0;
    const isVisible = hasVisibleCards || hasVisibleLi;
    dt.style.display = (hasContent && !isVisible) ? "none" : "";
  });

  scope.querySelectorAll<HTMLElement>(".masail-subject").forEach((ds) => {
    const totalTopics = ds.querySelectorAll<HTMLElement>(".masail-topic").length;
    const hiddenTopics = [...ds.querySelectorAll<HTMLElement>(".masail-topic")].filter((dt) => dt.style.display === "none").length;
    
    const hasVisibleCards = [...ds.querySelectorAll<HTMLElement>(".card")].some((c) => !c.classList.contains("is-search-hidden"));
    const hasVisibleLi = [...ds.querySelectorAll<HTMLElement>(".masail-list li")].some((li) => !li.classList.contains("is-search-hidden") && !li.classList.contains("is-hidden"));
    
    const allTopicsHidden = totalTopics > 0 && totalTopics === hiddenTopics;
    const hasContent = ds.querySelectorAll<HTMLElement>(".masail-topic, .card, .masail-list li").length > 0;
    
    if (totalTopics > 0) {
      ds.style.display = allTopicsHidden ? "none" : "";
    } else {
      ds.style.display = (hasContent && !(hasVisibleCards || hasVisibleLi)) ? "none" : "";
    }
  });
});


// type filter on topic/subject/era card grids — hide cards whose kind ≠ chosen type
document.addEventListener("click", (e) => {
  const btn = (e.target as HTMLElement).closest<HTMLElement>("[data-typefilter] button");
  if (!btn) return;
  const scope = btn.closest<HTMLElement>("[data-typefilter-scope]");
  if (!scope) return;
  const val = btn.dataset.type || "";
  scope.querySelectorAll<HTMLElement>("[data-typefilter] button").forEach((b) => b.setAttribute("aria-pressed", String(b === btn)));
  scope.querySelectorAll<HTMLElement>(".card[data-kind]").forEach((c) => c.classList.toggle("is-hidden", !!val && c.dataset.kind !== val));
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
  const allPacks = [...document.querySelectorAll<HTMLElement>(".ann-pack")];
  if (!allPacks.length) return;
  const proseEls = [...document.querySelectorAll<HTMLElement>(".prose")];
  if (!proseEls.length) return;
  const packs = allPacks.filter((p) => {
    if (!p.getAttribute("data-phrase")) return false;
    const entries = [...p.querySelectorAll<HTMLElement>(".ann-entry")];
    const hasNonHashiya = entries.some((en) => en.getAttribute("data-kind") !== "حاشية");
    return hasNonHashiya;
  });

  // whole-paragraph notes (no phrase): anchor ann-p{n} → the n-th <p> in the prose,
  // made tappable (opens the sheet). ponytail: n-th <p> heuristic — fine for plain
  // prose; if a matn mixes lists/quotes the index could drift, then add an explicit phrase.
  const paras = proseEls.flatMap((pr) => [...pr.querySelectorAll<HTMLElement>("p")]);
  allPacks.forEach((pack) => {
    if (pack.getAttribute("data-phrase")) return;
    const m = pack.id.match(/ann-p(\d+)$/);
    const para = m ? paras[+m[1] - 1] : null;
    if (para && !para.dataset.annPara) {
      const entries = [...pack.querySelectorAll<HTMLElement>(".ann-entry")];
      const hasNonHashiya = entries.some((en) => en.getAttribute("data-kind") !== "حاشية");
      if (hasNonHashiya) {
        para.dataset.annPara = pack.id; para.classList.add("has-ann");
      }
    }
  });

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

// --- annotation bottom-sheet (Quran-app style) ---
// Tap a verse (or a highlighted phrase) → a sheet rises from the bottom with one
// TAB per kind (شرح / إعراب / حاشية / تخريج), source CHIPS within a kind, and
// prev/next between annotated anchors. All data comes from the hidden
// [data-ann-pack] blocks (build-time, no network, sanitized at build).
(() => {
  const KIND_ORDER = ["شرح", "تفسير", "غريب", "إعراب", "حاشية", "تخريج", "حكم", "فوائد"];
  const KIND_SLUG: Record<string, string> = { شرح: "sharh", حاشية: "hashiya", تخريج: "takhrij", إعراب: "iraab", تفسير: "tafsir", غريب: "tafsir", حكم: "takhrij", فوائد: "sharh" };
  const toAr = (n: number) => String(n).replace(/[0-9]/g, (d) => "٠١٢٣٤٥٦٧٨٩"[+d]);

  let sheet: HTMLElement | null = null;
  let titleEl!: HTMLElement, tabsEl!: HTMLElement, chipsEl!: HTMLElement, bodyEl!: HTMLElement, footEl!: HTMLElement;
  let activeVerse: HTMLElement | null = null;

  function build(): HTMLElement {
    if (sheet && sheet.isConnected) return sheet;
    sheet = document.createElement("div");
    sheet.className = "ann-sheet";
    sheet.setAttribute("role", "dialog");
    sheet.hidden = true;
    const head = document.createElement("div");
    head.className = "ann-sheet-head";
    titleEl = document.createElement("span");
    titleEl.className = "ann-sheet-title";
    const x = document.createElement("button");
    x.type = "button"; x.className = "ann-sheet-close"; x.setAttribute("aria-label", "إغلاق"); x.textContent = "×";
    x.addEventListener("click", close);
    head.append(titleEl, x);
    tabsEl = document.createElement("div"); tabsEl.className = "ann-sheet-tabs";
    chipsEl = document.createElement("div"); chipsEl.className = "ann-sheet-chips";
    bodyEl = document.createElement("div"); bodyEl.className = "ann-sheet-body"; bodyEl.setAttribute("data-ar", "");
    footEl = document.createElement("div"); footEl.className = "ann-sheet-foot";
    sheet.append(head, tabsEl, chipsEl, bodyEl, footEl);
    document.body.appendChild(sheet);
    return sheet;
  }

  const packIds = () => [...document.querySelectorAll<HTMLElement>("[data-ann-pack]")].map((p) => p.id);

  function anchorLabel(pack: HTMLElement): string {
    const mPage = pack.id.match(/^ann-page-(\d+)$/);
    if (mPage) return `الصفحة ${toAr(+mPage[1])}`;
    const mQuran = pack.id.match(/^ann-quran-(\d+)-(\d+)$/);
    if (mQuran) return `الآية ${toAr(+mQuran[2])}`;
    const verse = pack.closest<HTMLElement>(".verse");
    const n = verse?.querySelector(".vnum")?.textContent?.trim();
    if (n) return `البيت ${n}`;
    const ph = pack.getAttribute("data-phrase");
    return ph ? `«${ph}»` : "هذا الموضع";
  }

  function entriesByKind(pack: HTMLElement): Map<string, HTMLElement[]> {
    const m = new Map<string, HTMLElement[]>();
    pack.querySelectorAll<HTMLElement>(".ann-entry").forEach((en) => {
      const k = en.getAttribute("data-kind") || "شرح";
      if (!m.has(k)) m.set(k, []);
      m.get(k)!.push(en);
    });
    return m;
  }

  const sourceLabel = (en: HTMLElement, i: number) =>
    (en.getAttribute("data-label") || "").split(" — ")[1] || `المصدر ${toAr(i + 1)}`;

  function showEntry(en: HTMLElement) {
    bodyEl.textContent = "";
    const src0 = en.querySelector(".ann-entry-body");
    if (src0) bodyEl.append(...[...src0.cloneNode(true).childNodes]); // already sanitized at build
    const src = en.querySelector<HTMLAnchorElement>(".ann-source-link");
    if (src) {
      const a = document.createElement("a");
      a.className = "ann-source-link"; a.href = src.href; a.textContent = src.textContent || "";
      bodyEl.appendChild(a);
    }
  }

  function renderChips(entries: HTMLElement[], selectIndex = 0) {
    chipsEl.textContent = "";
    chipsEl.hidden = entries.length < 2;
    entries.forEach((en, i) => {
      if (entries.length < 2) return;
      const b = document.createElement("button");
      b.type = "button"; b.className = "ann-chip"; b.textContent = sourceLabel(en, i);
      b.setAttribute("aria-pressed", String(i === selectIndex));
      b.addEventListener("click", () => {
        chipsEl.querySelectorAll(".ann-chip").forEach((c) => c.setAttribute("aria-pressed", "false"));
        b.setAttribute("aria-pressed", "true");
        showEntry(en);
      });
      chipsEl.appendChild(b);
    });
    showEntry(entries[selectIndex] || entries[0]);
  }

  function selectKind(byKind: Map<string, HTMLElement[]>, kind: string, entryIndex = 0) {
    tabsEl.querySelectorAll(".ann-tab").forEach((t) => t.setAttribute("aria-pressed", String(t.getAttribute("data-kind") === kind)));
    renderChips(byKind.get(kind)!, entryIndex);
  }

  function renderFoot(packId: string) {
    footEl.textContent = "";
    const ids = packIds();
    const i = ids.indexOf(packId);
    const nav = (label: string, targetId: string | undefined, cls: string) => {
      const b = document.createElement("button");
      b.type = "button"; b.className = "ann-nav " + cls; b.textContent = label;
      if (!targetId) b.disabled = true;
      else b.addEventListener("click", () => {
        (document.getElementById(targetId)?.closest(".verse") || document.getElementById(targetId))
          ?.scrollIntoView({ behavior: "smooth", block: "center" });
        openSheet(targetId);
      });
      return b;
    };
    footEl.append(nav("‹ السابق", ids[i - 1], "ann-prev"), nav("التالي ›", ids[i + 1], "ann-next"));
  }

  function openSheet(packId: string, entryIndex = 0) {
    const pack = document.getElementById(packId);
    if (!pack) return;
    build();
    if (activeVerse) activeVerse.classList.remove("ann-active-verse");
    activeVerse = pack.closest<HTMLElement>(".verse");
    activeVerse?.classList.add("ann-active-verse");

    titleEl.textContent = anchorLabel(pack);
    const byKind = entriesByKind(pack);
    const kinds = [...byKind.keys()].sort((a, b) => {
      const ia = KIND_ORDER.indexOf(a), ib = KIND_ORDER.indexOf(b);
      return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib);
    });
    tabsEl.textContent = "";
    tabsEl.hidden = kinds.length < 2;
    kinds.forEach((k) => {
      const t = document.createElement("button");
      t.type = "button"; t.className = "ann-tab"; t.setAttribute("data-kind", k); t.textContent = k;
      t.addEventListener("click", () => selectKind(byKind, k));
      tabsEl.appendChild(t);
    });
    selectKind(byKind, kinds[0], entryIndex);
    renderFoot(packId);
    sheet!.hidden = false;
    requestAnimationFrame(() => sheet!.classList.add("is-shown"));
  }

  function close() {
    if (sheet) { sheet.hidden = true; sheet.classList.remove("is-shown"); }
    if (activeVerse) { activeVerse.classList.remove("ann-active-verse"); activeVerse = null; }
  }

  // build synthetic ann-packs for page-sep notes so openSheet() works unchanged
  function injectPageNotes() {
    document.querySelectorAll<HTMLElement>(".page-sep[data-notes]").forEach((sep) => {
      const packId = "ann-page-" + sep.dataset.page;
      if (document.getElementById(packId)) return;
      let notes: any[];
      try { notes = JSON.parse(sep.dataset.notes!); } catch { return; }
      if (!notes || !notes.length) return;

      const pack = document.createElement("div");
      pack.className = "ann-pack"; pack.id = packId; pack.setAttribute("data-ann-pack", ""); pack.hidden = true;

      if (typeof notes[0] === "string") {
        notes.forEach((n, idx) => {
          const entry = document.createElement("div");
          entry.className = "ann-entry k-hashiya"; 
          entry.setAttribute("data-kind", "حاشية"); 
          entry.setAttribute("data-label", `حاشية — حاشية ${toAr(idx + 1)}`);
          const body = document.createElement("div");
          body.className = "ann-entry-body"; body.setAttribute("data-ar", "");
          const p = document.createElement("p"); 
          p.textContent = n; 
          body.appendChild(p);
          entry.appendChild(body);
          pack.appendChild(entry);
        });
      } else {
        notes.forEach((nt) => {
          const entry = document.createElement("div");
          const kSlug = KIND_SLUG[nt.kind] || "sharh";
          entry.className = `ann-entry k-${kSlug}`;
          entry.setAttribute("data-kind", nt.kind);
          entry.setAttribute("data-label", nt.label || nt.kind);
          const body = document.createElement("div");
          body.className = "ann-entry-body"; body.setAttribute("data-ar", "");
          body.innerHTML = nt.body;
          entry.appendChild(body);
          if (nt.sourceHref) {
            const a = document.createElement("a");
            a.className = "ann-source-link"; a.href = nt.sourceHref;
            a.textContent = `اقرأ في موضعه${nt.sourceLabel ? `: ${nt.sourceLabel}` : ""} ←`;
            entry.appendChild(a);
          }
          pack.appendChild(entry);
        });
      }

      sep.after(pack);
      sep.dataset.ann = packId;
    });
  }

  document.addEventListener("click", (e) => {
    const t = e.target as HTMLElement;
    // Use composedPath (captured at dispatch) instead of t.closest: nav buttons'
    // own click handler runs first (target phase) and calls renderFoot(), which
    // replaces the footer buttons — including the one just clicked — before this
    // delegated listener runs in the bubble phase. t.closest(".ann-sheet") on a
    // now-detached button finds nothing, so this used to misread the click as
    // "outside the sheet" and immediately close it right after it reopened.
    if (e.composedPath().some((el) => el instanceof Element && el.classList.contains("ann-sheet"))) return;
    // page separator click → open its حاشية in the sheet
    const sep = t.closest<HTMLElement>(".page-sep[data-ann]");
    if (sep) { e.preventDefault(); openSheet(sep.dataset.ann!); return; }
    // inline footnote sup (future EPUB imports): <sup data-fn="n" data-sep-page="p">
    const fnSup = t.closest<HTMLElement>("sup[data-fn][data-sep-page]");
    if (fnSup) {
      e.preventDefault();
      const fnNum = parseInt(fnSup.dataset.fn || "1", 10);
      openSheet("ann-page-" + fnSup.dataset.sepPage, fnNum - 1);
      return;
    }
    // standard markdown footnote ref, stamped with which page's notes sheet it
    // belongs to (book/[slug]/[chapter].astro) — grouped there instead of a
    // lone popover so it's clear which page's citations it's part of.
    const fnRef = t.closest<HTMLAnchorElement>("[data-footnote-ref][data-sep-page]");
    if (fnRef) {
      e.preventDefault();
      openSheet("ann-page-" + fnRef.dataset.sepPage, parseInt(fnRef.dataset.fnIndex || "0", 10));
      return;
    }
    const mark = t.closest<HTMLElement>(".ann-mark");
    if (mark) { e.preventDefault(); openSheet(mark.getAttribute("data-ann") || ""); return; }
    // whole-paragraph note: tap the فقرة
    const para = t.closest<HTMLElement>("[data-ann-para]");
    if (para && !t.closest("a, button")) { openSheet(para.dataset.annPara || ""); return; }
    // tapping a verse that carries notes (reading mode only) opens its sheet
    if (root.getAttribute("data-mode") !== "ikhtibar") {
      const pack = t.closest<HTMLElement>(".verse")?.querySelector<HTMLElement>("[data-ann-pack]");
      if (pack && !t.closest("a, button")) { openSheet(pack.id); return; }
    }
    if (sheet && !sheet.hidden) close(); // outside click
  });
  function setup() {
    injectPageNotes();

    // Clean up any stale sheets in the DOM (e.g. from cached pages)
    const oldSheet = document.querySelector(".ann-sheet");
    if (oldSheet) oldSheet.remove();
    
    // Re-create the sheet and bind references to the current page DOM
    build();
  }

  document.addEventListener("keydown", (e) => { if (e.key === "Escape") close(); });
  document.addEventListener("astro:after-swap", () => {
    root = document.documentElement;
    close();
    sheet = null;
  });
  setup();
  document.addEventListener("astro:page-load", setup);
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
applyPages(localStorage.getItem(LS.pages) !== "flow");
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
  root = document.documentElement;
  enhanceProse();
  if (localStorage.getItem(LS.tashkeel) === "0") applyTashkeel(false); // re-bare new content
  updateActiveNav();
  updateProgress();
  setDrawer(false); // a navigation always closes the (persisted) drawer
}
onPage();
document.addEventListener("astro:page-load", onPage);

// View transitions swap <html>, wiping the attributes the pre-paint inline script
// set → re-apply saved theme/scale/numbers/mode after each swap (before paint, so
// no flash). Mirrors the inline script in Base.astro.
function applyStoredPrefs() {
  try {
    const t = localStorage.getItem(LS.theme);
    if (t && t !== "light") root.setAttribute("data-theme", t);
    else root.removeAttribute("data-theme");
    const s = localStorage.getItem(LS.scale);
    if (s) root.style.setProperty("--reading-scale", s);
    root.classList.toggle("hide-vnums", localStorage.getItem(LS.vnums) === "0");
    root.classList.toggle("no-tashkeel", localStorage.getItem(LS.tashkeel) === "0");
    root.classList.toggle("pages-flow", localStorage.getItem(LS.pages) === "flow");
    const md = localStorage.getItem(LS.mode);
    if (md && md !== "qiraa") root.setAttribute("data-mode", md);
    else root.removeAttribute("data-mode");
  } catch (e) {}
}
document.addEventListener("astro:after-swap", () => {
  root = document.documentElement;
  applyStoredPrefs();
});

// Close the drawer the moment a link inside it is clicked (the SPA router would
// otherwise leave the persisted drawer open over the new page).
document.addEventListener("click", (e) => {
  if ((e.target as HTMLElement).closest("[data-drawer] a")) setDrawer(false);
});
