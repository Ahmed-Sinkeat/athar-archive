// Reading-preferences & chrome enhancement (progressive — content is fully
// readable without it). Persists to localStorage; the pre-paint inline script
// in Base.astro applies theme/scale/vnums before first paint to avoid flash.

import { stripTashkeel } from "../lib/display";

const LS = {
  theme: "aa-theme",
  scale: "aa-scale",
  tashkeel: "aa-tashkeel",
  vnums: "aa-vnums",
  pages: "aa-pages",
  footnotes: "aa-footnotes",
  width: "aa-width",
  browseView: "aa-browse-view",
  browseOpen: "aa-browse-open",
  audioSpeed: "aa-audio-speed",
};

const SCALE_MIN = 0.8;
const SCALE_MAX = 1.3; // base 20px * 1.3 = 26px, matches HANDOFF.md §2's 16-26 range
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

// --- reading-column width (narrow/normal/wide) — scales .chap-outer's
// max-width; the sidebar column itself stays a fixed 232px (see global.css
// .chap-grid), so widening only grows the reading column, never the sidebar.
type Width = "narrow" | "normal" | "wide";
function setWidth(w: Width) {
  if (w === "normal") root.removeAttribute("data-width");
  else root.setAttribute("data-width", w);
  localStorage.setItem(LS.width, w);
  syncWidthButtons(w);
}
function syncWidthButtons(w: Width) {
  document.querySelectorAll<HTMLElement>("[data-width-btn]").forEach((b) => {
    b.setAttribute("aria-pressed", String(b.dataset.widthBtn === w));
  });
}

// --- theme (3 states: paper default / noir / mono) ---
type Theme = "paper" | "noir" | "mono";
const THEME_CYCLE: Theme[] = ["paper", "noir", "mono"];

function setTheme(t: Theme) {
  if (t === "paper") root.removeAttribute("data-theme");
  else root.setAttribute("data-theme", t);
  localStorage.setItem(LS.theme, t);
  syncThemeButtons(t);
}
function currentTheme(): Theme {
  const t = root.getAttribute("data-theme");
  return t === "noir" || t === "mono" ? t : "paper";
}
function syncThemeButtons(t: Theme) {
  document.querySelectorAll<HTMLElement>("[data-theme-btn]").forEach((b) => {
    b.setAttribute("aria-pressed", String(b.dataset.themeBtn === t));
  });
  document.querySelectorAll<HTMLElement>("[data-theme-icon]").forEach((el) => {
    el.hidden = el.dataset.themeIcon !== t;
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

// --- footnotes (الحواشي والتخريج) ---
function applyFootnotes(show: boolean) {
  root.classList.toggle("hide-footnotes", !show);
  localStorage.setItem(LS.footnotes, show ? "1" : "0");
  document.querySelectorAll<HTMLElement>('[data-toggle="footnotes"]').forEach((b) =>
    b.setAttribute("aria-pressed", String(show)),
  );
}

// --- inline-note شرح tab picker (InlineNoteGroup.astro) ---
document.addEventListener("change", (e) => {
  const input = e.target as HTMLElement;
  if (!(input instanceof HTMLInputElement) || !input.classList.contains("inline-note-tab-input")) return;
  const details = input.closest(".inline-note");
  details?.querySelectorAll<HTMLElement>(".inline-note-pane").forEach((p) => {
    p.hidden = p.dataset.pane !== input.id;
  });
});

// --- mobile nav dropdown ---
function setDrawer(open: boolean) {
  const drawer = document.querySelector<HTMLElement>("[data-drawer]");
  const backdrop = document.querySelector<HTMLElement>("[data-drawer-backdrop]");
  if (!drawer || !backdrop) return;
  drawer.toggleAttribute("data-open", open);
  backdrop.toggleAttribute("data-open", open);
  document.querySelectorAll<HTMLElement>('[data-action="menu:toggle"]').forEach((b) =>
    b.setAttribute("aria-expanded", String(open)),
  );
}

// --- topbar search + popovers ---
// The topbar is transition:persist (same DOM node kept across SPA navigations),
// but a bfcache restore (back button) followed by a forward SPA nav can leave
// Astro's persist-tracking out of sync, cloning a fresh header and orphaning
// these references — the gear/filter buttons then silently stop responding
// until a manual reload. `let` + re-querying in requeryChrome() (called from
// onPage()/pageshow below) keeps them pointed at whatever's actually live.
let topsearch = document.querySelector<HTMLFormElement>("[data-topsearch]");
let tsInput = document.querySelector<HTMLInputElement>("[data-topsearch-input]");
let filterPop = document.querySelector<HTMLElement>("[data-filter-pop]");
let settingsPop = document.querySelector<HTMLElement>("[data-settings-pop]");
function requeryChrome() {
  topsearch = document.querySelector<HTMLFormElement>("[data-topsearch]");
  tsInput = document.querySelector<HTMLInputElement>("[data-topsearch-input]");
  filterPop = document.querySelector<HTMLElement>("[data-filter-pop]");
  settingsPop = document.querySelector<HTMLElement>("[data-settings-pop]");
}

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
// Delegated on document (not bound to the specific input) — the persisted
// topbar can occasionally get cloned by the router (see requeryChrome above),
// which would otherwise leave a direct listener attached to a detached node.
function wireChecklistSearch(searchSel: string, listSel: string) {
  document.addEventListener("input", (e) => {
    const inp = (e.target as HTMLElement).closest<HTMLInputElement>(searchSel);
    if (!inp) return;
    const list = filterPop?.querySelector<HTMLElement>(listSel);
    const q = inp.value.trim();
    list?.querySelectorAll<HTMLElement>(".pop-check").forEach((lab) => {
      const name = lab.getAttribute("data-name") || lab.textContent || "";
      lab.classList.toggle("is-hidden", q !== "" && !name.includes(q));
    });
  });
}
wireChecklistSearch("[data-filter-person-search]", "[data-filter-person]");
wireChecklistSearch("[data-filter-subject-search]", "[data-filter-subject]");

// Enter in the field (no go-button click) submits; collapsed → just open.
document.addEventListener("submit", (e) => {
  if (!(e.target as HTMLElement).closest("[data-topsearch]")) return;
  e.preventDefault();
  if (!isSearchOpen()) { openSearch(); return; }
  location.href = buildSearchUrl();
});
document.addEventListener("change", (e) => {
  if ((e.target as HTMLElement).closest("[data-filter-pop]")) refreshFilterIndicator();
});

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
  const chapToc = document.querySelector<HTMLDetailsElement>(".chap-mobile-toc");
  if (chapToc?.open && !t.closest(".chap-mobile-toc") && !t.closest('[data-action="chaptoc:toggle"]')) {
    chapToc.open = false;
    document.querySelectorAll<HTMLElement>('[data-action="chaptoc:toggle"]').forEach((b) => b.setAttribute("aria-expanded", "false"));
  }
  // native <details> popovers (تفسير/شرح tabs, edition info) never auto-close
  // on outside click — browsers only do that for the newer popover= attribute,
  // which these predate. Close any that aren't ambient sidebar navigation.
  document.querySelectorAll<HTMLDetailsElement>("details.inline-note[open], details.book-catalog[open]").forEach((d) => {
    if (!d.contains(t)) d.open = false;
  });
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
  "toggle:footnotes": () => applyFootnotes(root.classList.contains("hide-footnotes")),
  "theme:cycle": () => setTheme(THEME_CYCLE[(THEME_CYCLE.indexOf(currentTheme()) + 1) % THEME_CYCLE.length]),
  "theme:paper": () => setTheme("paper"),
  "theme:noir": () => setTheme("noir"),
  "theme:mono": () => setTheme("mono"),
  "width:narrow": () => setWidth("narrow"),
  "width:normal": () => setWidth("normal"),
  "width:wide": () => setWidth("wide"),
  "menu:toggle": () => setDrawer(true),
  "menu:close": () => setDrawer(false),
  // closed → open the bar; open → just close it (Enter in the field runs the search).
  "search:toggle": () => { if (!isSearchOpen()) openSearch(); else topsearch?.classList.remove("is-open"); },
  "search:filter": () => togglePop(filterPop, "search:filter"),
  "search:apply": () => { location.href = buildSearchUrl(); },
  "settings:toggle": () => togglePop(settingsPop, "settings:toggle"),
  // topbar "chapter/heading list" icon → open the current page's sidebar
  // popup (ReaderSidebar.astro's <details>, mobile-only via CSS).
  "sidebar:mobile-toggle": () => {
    const el = document.querySelector<HTMLDetailsElement>("[data-mobile-sidebar]");
    if (!el) return;
    el.open = !el.open;
    if (el.open) el.scrollIntoView({ block: "nearest" });
  },
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

// topbar Quran juz/page jump (persisted header, bound once)
document.addEventListener("change", (e) => {
  const el = e.target as HTMLElement;
  if (!el.matches("[data-quran-jump-juz]")) return;
  const href = (el as HTMLSelectElement).value;
  if (href) location.href = href;
});
document.addEventListener("keydown", (e) => {
  const el = e.target as HTMLElement;
  if (!el.matches("[data-quran-jump-page]") || e.key !== "Enter") return;
  const n = parseInt((el as HTMLInputElement).value, 10);
  if (!(n >= 1 && n <= 604)) return;
  const pages: { id: string; start: number }[] = JSON.parse(document.querySelector('script[data-quran-surah-pages]')?.textContent || "[]");
  let s = pages[0];
  for (const cand of pages) { if (cand.start <= n) s = cand; else break; }
  if (s) location.href = `/quran/${s.id}#p${n}`;
});

// book page/volume jump (BookPageJump.astro) — rendered twice by
// ReaderSidebar (desktop aside + mobile popup), so this is event-delegated
// rather than keyed by id.
document.addEventListener("submit", (e) => {
  const form = (e.target as HTMLElement).closest<HTMLFormElement>("[data-book-jump]");
  if (!form) return;
  e.preventDefault();
  const page = form.querySelector<HTMLInputElement>("[data-book-jump-page]")?.value;
  const n = page ? parseInt(page, 10) : NaN;
  if (!(n >= 1)) return;
  const vol = form.querySelector<HTMLSelectElement>("[data-book-jump-vol]")?.value;
  const qs = vol ? `?v=${encodeURIComponent(vol)}` : "";
  location.href = `${form.dataset.bookHref}${qs}#p${n}`;
});

// Volume-qualified page anchors: a multi-volume book can reuse a page number
// across volumes, so only the first-seen one keeps the plain #pN id — later
// volumes get #pN-vJ (lib/page-footnotes.ts). If the URL names a volume
// (?v=) and the plain-id element belongs to a different one, retarget.
function fixVolumeAnchor() {
  const m = location.hash.match(/^#p(\d+)$/);
  const v = new URLSearchParams(location.search).get("v");
  if (!m || !v) return;
  const plain = document.getElementById(`p${m[1]}`);
  if (plain && plain.dataset.juz === v) return;
  document.getElementById(`p${m[1]}-v${v}`)?.scrollIntoView();
}
fixVolumeAnchor();
document.addEventListener("astro:page-load", fixVolumeAnchor);

// per-athar permalink: "#" link injected before each numbered narration
// (see injectAtharAnchors in src/lib/hadith.ts) — copies the deep link +
// a plain-text citation alongside its native hash navigation.
document.addEventListener("click", (e) => {
  const a = (e.target as HTMLElement).closest<HTMLAnchorElement>(".athar-cite");
  if (!a) return;
  const n = a.dataset.athar;
  const book = a.closest<HTMLElement>("[data-cite-book]")?.dataset.citeBook || document.title.split(" · ")[0];
  const url = `${location.origin}${location.pathname}#athar-${n}`;
  navigator.clipboard?.writeText(`${url}\n«${book}» — أثر ${n}`);
});

// Footnotes need no JS: every marker is a plain <sup class="fn-ref"> and the
// notes sit in always-visible per-page footer boxes (lib/page-footnotes.ts).
// The old click-to-reveal popover path (data-note sups) is gone.

// --- page-transition loader (top progress bar + center seal) ---
// Mirrors the "Page Loader" design: don't show anything for a fast/cached
// navigation, but once shown, don't let it flash for less than MIN_VISIBLE.
(() => {
  const bar = document.querySelector<HTMLElement>("[data-page-progress]");
  const barFill = document.querySelector<HTMLElement>("[data-page-progress-bar]");
  const seal = document.querySelector<HTMLElement>("[data-page-loader]");
  if (!bar || !barFill || !seal) return;

  // 150ms, then 300ms, were both tight enough that ordinary mobile round-trip
  // latency (~400-600ms TTFB even on static pages) crossed them on almost every
  // navigation — the loader fired constantly and its forced minimum display
  // time made pages FEEL slower than they were. Only show it for genuinely
  // slow loads, and once shown just avoid a sub-200ms flash.
  const SHOW_DELAY = 650;
  const MIN_VISIBLE = 200;
  let showT = 0, shown = false, shownAt = 0;

  document.addEventListener("astro:before-preparation", () => {
    shown = false;
    showT = window.setTimeout(() => {
      shown = true;
      shownAt = Date.now();
      bar.toggleAttribute("data-visible", true);
      seal.toggleAttribute("data-visible", true);
      barFill.style.width = "16%";
      requestAnimationFrame(() => requestAnimationFrame(() => { barFill.style.width = "74%"; }));
    }, SHOW_DELAY);
  });

  const finish = () => {
    clearTimeout(showT);
    if (!shown) return;
    const wait = Math.max(0, MIN_VISIBLE - (Date.now() - shownAt));
    barFill.style.width = "100%";
    setTimeout(() => {
      bar.removeAttribute("data-visible");
      seal.removeAttribute("data-visible");
      setTimeout(() => { barFill.style.width = "0%"; }, 250);
    }, wait + 160);
  };
  document.addEventListener("astro:page-load", finish);
  // bfcache restore (mobile back/swipe) doesn't fire astro:page-load, so a
  // loader shown right before leaving the page restores stuck "visible" —
  // its pointer-events:auto covers the full viewport, blocking every click
  // until a hard reload. Force it closed on restore.
  window.addEventListener("pageshow", (e) => {
    if (!e.persisted) return;
    clearTimeout(showT);
    shown = false;
    bar.removeAttribute("data-visible");
    seal.removeAttribute("data-visible");
  });
})();

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
    if (wrap) {
      const flat = flatBtn.dataset.flatToggle === "flat";
      wrap.classList.toggle("show-flat", flat);
      wrap.querySelectorAll<HTMLElement>("[data-flat-toggle]").forEach((b) =>
        b.setAttribute("aria-pressed", String(b === flatBtn)),
      );
      localStorage.setItem(LS.browseView, flat ? "flat" : "grouped");
    }
  }
});

// Restore the flat/grouped browse preference (shared across all browse pages)
// and each <details> subject/topic open state (per-page, since the tree
// differs per listing) — both reset to defaults on every navigation
// otherwise, since view-transition swaps replace the DOM.
function restoreBrowseState() {
  const wrap = document.querySelector<HTMLElement>("[data-browse]");
  if (!wrap) return;
  const view = localStorage.getItem(LS.browseView);
  if (view === "grouped") {
    wrap.classList.remove("show-flat");
    wrap.querySelectorAll<HTMLElement>("[data-flat-toggle]").forEach((b) =>
      b.setAttribute("aria-pressed", String(b.dataset.flatToggle === "grouped")),
    );
  }
  const openKey = LS.browseOpen + ":" + location.pathname;
  const open = new Set(JSON.parse(localStorage.getItem(openKey) || "[]") as string[]);
  const detailsEls = [...wrap.querySelectorAll<HTMLDetailsElement>(".masail-subject, .masail-topic")];
  detailsEls.forEach((d, i) => {
    const key = d.querySelector("summary")?.textContent?.trim() || String(i);
    d.dataset.persistKey = key;
    if (open.has(key)) d.open = true;
    d.addEventListener("toggle", () => {
      const cur = new Set(JSON.parse(localStorage.getItem(openKey) || "[]") as string[]);
      if (d.open) cur.add(key); else cur.delete(key);
      localStorage.setItem(openKey, JSON.stringify([...cur]));
    });
  });
}
document.addEventListener("astro:page-load", restoreBrowseState);

// --- person page: filter work cards by category (كتب/مقالات/أخرى) ---
document.addEventListener("click", (e) => {
  const btn = (e.target as HTMLElement).closest<HTMLElement>("[data-person-tab]");
  if (!btn) return;
  const group = btn.closest<HTMLElement>("[data-person-tabs]");
  const works = btn.closest<HTMLElement>("[data-person-works]");
  if (!group || !works) return;
  const cat = btn.dataset.personTab!;
  group.querySelectorAll<HTMLElement>("[data-person-tab]").forEach((b) => b.setAttribute("aria-pressed", String(b === btn)));
  works.querySelectorAll<HTMLElement>(".card, .flat-list li").forEach((c) => {
    c.hidden = cat !== "all" && c.dataset.kind !== cat;
  });
});

// Browse page in-page filter: person (searchable text input) + topic/kind <select>
// dropdowns on listing pages. Works on BrowseGroups flat list (data-person /
// data-person-name / data-topics / data-kind on each <li>), the lesson list in
// series/index.astro, and the masail accordion ([data-masail-browse]). Person
// matches by name substring (like the title search below) since its list is too
// long to pick an exact id from a <select> — see data-filter-key="person" markup.
const norm = (s: string) => stripTashkeel(s.trim().toLowerCase());
function applyBrowseFilter(el: HTMLInputElement | HTMLSelectElement | HTMLButtonElement) {
  const key = el.dataset.filterKey!;
  const val = el.value;
  const needle = key === "person" ? norm(val) : val;
  const personMatch = (name: string | undefined) => !!needle && norm(name ?? "").includes(needle);

  // flat-list (BrowseGroups + lessons)
  const scope = el.closest("[data-browse]") ?? el.closest(".wrap-mid");
  if (scope) {
    scope.querySelectorAll<HTMLElement>(".flat-list li").forEach((li) => {
      if (!val) { li.classList.remove("is-hidden"); return; }
      const match = key === "kind"
        ? li.dataset.kind === val
        : key === "person"
          ? personMatch(li.dataset.personName)
          : (li.dataset.topics ?? "").split(",").includes(val);
      li.classList.toggle("is-hidden", !match);
    });
  }

  // masail accordion ([data-masail-browse])
  const masailScope = el.closest<HTMLElement>("[data-masail-browse]");
  if (masailScope) {
    masailScope.querySelectorAll<HTMLElement>(".masail-list li[data-person]").forEach((li) => {
      if (!val) { li.classList.remove("is-hidden"); return; }
      const match = key === "person"
        ? personMatch(li.dataset.personName)
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
}
document.addEventListener("change", (e) => {
  const sel = (e.target as HTMLElement).closest<HTMLSelectElement>("select[data-filter-key]");
  if (sel) applyBrowseFilter(sel);
});
document.addEventListener("input", (e) => {
  const el = (e.target as HTMLElement).closest<HTMLInputElement>('input[data-filter-key="person"]');
  if (el) applyBrowseFilter(el);
});
// mobile chip row (design 2a) — same filter engine as the select, plus
// active-chip UI state; keeps the paired <select> in sync so switching
// between mobile/desktop widths (or a resize) never shows two different filters
document.addEventListener("click", (e) => {
  const chip = (e.target as HTMLElement).closest<HTMLButtonElement>(".browse-filter-chips button[data-filter-key]");
  if (!chip) return;
  chip.parentElement!.querySelectorAll<HTMLButtonElement>("button[data-filter-key]").forEach((b) =>
    b.setAttribute("aria-pressed", String(b === chip)),
  );
  const sel = chip.closest(".browse-filters")?.querySelector<HTMLSelectElement>(`select[data-filter-key="${chip.dataset.filterKey}"]`);
  if (sel) sel.value = chip.value;
  applyBrowseFilter(chip);
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
  // Tab label ≠ stored kind: غريب the DB kind stays as-is (matches annotation
  // data), but the tab reads "الغريب والمعاني" — clearer than the bare word.
  const KIND_LABEL: Record<string, string> = { غريب: "الغريب والمعاني", تفسير: "التفسير" };
  const toAr = (n: number) => String(n).replace(/[0-9]/g, (d) => "٠١٢٣٤٥٦٧٨٩"[+d]);

  let sheet: HTMLElement | null = null;
  let titleEl!: HTMLElement, ayahEl!: HTMLElement, tabsEl!: HTMLElement, chipsEl!: HTMLElement, bodyEl!: HTMLElement, footEl!: HTMLElement;
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
    ayahEl = document.createElement("span");
    ayahEl.className = "ann-sheet-ayah"; ayahEl.hidden = true; ayahEl.setAttribute("data-ar", "");
    const x = document.createElement("button");
    x.type = "button"; x.className = "ann-sheet-close"; x.setAttribute("aria-label", "إغلاق"); x.textContent = "×";
    x.addEventListener("click", close);
    head.append(titleEl, ayahEl, x);
    tabsEl = document.createElement("div"); tabsEl.className = "ann-sheet-tabs";
    chipsEl = document.createElement("div"); chipsEl.className = "ann-sheet-chips";
    bodyEl = document.createElement("div"); bodyEl.className = "ann-sheet-body"; bodyEl.setAttribute("data-ar", "");
    footEl = document.createElement("div"); footEl.className = "ann-sheet-foot";
    sheet.append(head, tabsEl, chipsEl, bodyEl, footEl);
    // Surah pages provide an anchor slot below the ayat — only used today to
    // pick the gold تفسير accent (.ann-panel); placement itself is CSS fixed
    // positioning (docked sidebar on wide screens, floating popup below that),
    // shared by every kind of annotation, so where it lands in the DOM no
    // longer matters for layout.
    const slot = document.getElementById("tafsir-panel-slot");
    if (slot) { sheet.classList.add("ann-panel"); slot.appendChild(sheet); }
    else document.body.appendChild(sheet);
    wireDragAndResize(sheet, head);
    return sheet;
  }

  // Floating/movable/resizable pop menu: drag by the header, resize via the
  // native CSS `resize: both` handle (bottom-inline-end corner). Either one
  // switches the panel to explicit fixed left/top (is-dragged) so the
  // desktop docked-sidebar rule and the centering transform stop fighting
  // the user's placement; the position/size then persists for the session.
  function wireDragAndResize(sheet: HTMLElement, handle: HTMLElement) {
    let dragging = false, dx = 0, dy = 0;
    const toFixedBox = () => {
      const r = sheet.getBoundingClientRect();
      sheet.style.left = `${r.left}px`;
      sheet.style.top = `${r.top}px`;
      sheet.style.right = "auto";
      sheet.style.bottom = "auto";
      sheet.classList.add("is-dragged");
    };
    handle.addEventListener("pointerdown", (e) => {
      if ((e.target as HTMLElement).closest(".ann-sheet-close")) return;
      toFixedBox();
      dragging = true;
      dx = e.clientX - sheet.offsetLeft;
      dy = e.clientY - sheet.offsetTop;
      handle.setPointerCapture(e.pointerId);
    });
    handle.addEventListener("pointermove", (e) => {
      if (!dragging) return;
      sheet.style.left = `${e.clientX - dx}px`;
      sheet.style.top = `${e.clientY - dy}px`;
    });
    const stop = () => { dragging = false; };
    handle.addEventListener("pointerup", stop);
    handle.addEventListener("pointercancel", stop);
    // Native `resize: both` (CSS) sets width/height as inline styles when the
    // user drags the corner handle — inline style already outranks the
    // desktop sidebar's class-based width/height, so it just works without
    // extra JS. Over-constrained top+bottom+height resolves per spec by
    // dropping `bottom` once `height` is explicit, so vertical resize is
    // still free to move even though the sidebar rule sets both.
  }

  // Quran pack fragments are fetched on demand and only exist in the DOM once
  // opened, so [data-ann-pack] alone would only ever list ayat already tapped
  // this session — the ayah-number buttons are all present from first render
  // and cover the whole surah, so prefer them when available (quran pages only).
  const packIds = () => {
    const btnIds = [...document.querySelectorAll<HTMLElement>(".ayah-num-btn[data-ann]")].map((b) => b.dataset.ann!);
    if (btnIds.length) return btnIds;
    return [...document.querySelectorAll<HTMLElement>("[data-ann-pack]")].map((p) => p.id);
  };

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
      if (!targetId) { b.disabled = true; return b; }
      b.addEventListener("click", () => {
        const jump = () => {
          (document.getElementById(targetId)?.closest(".verse") || document.getElementById(targetId))
            ?.scrollIntoView({ behavior: "smooth", block: "center" });
          openSheet(targetId);
        };
        // quran packs are fetched fragments, only appended to <body> once opened
        // — prev/next used to only reach ayat already tapped this session
        // because packIds() (and thus this button's target) only counted packs
        // already in the DOM. The ayah-number buttons cover the whole surah
        // from first render, so use them to resolve + fetch a not-yet-opened
        // target instead of silently doing nothing.
        if (document.getElementById(targetId)) { jump(); return; }
        const srcBtn = document.querySelector<HTMLElement>(`[data-ann="${targetId}"][data-ann-src]`);
        const annSrc = srcBtn?.dataset.annSrc;
        if (!annSrc) return;
        fetch(annSrc).then((r) => (r.ok ? r.text() : "")).then((html) => {
          if (html) document.body.insertAdjacentHTML("beforeend", html);
          jump();
        });
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
    // quran packs are fetched fragments appended to <body>, so closest(".verse")
    // finds nothing — resolve the ayah span from the pack id instead
    const mQuran = packId.match(/^ann-quran-\d+-(\d+)$/);
    activeVerse =
      pack.closest<HTMLElement>(".verse") ??
      (mQuran ? document.getElementById(mQuran[1])?.closest<HTMLElement>(".verse") ?? null : null);
    activeVerse?.classList.add("ann-active-verse");

    titleEl.textContent = anchorLabel(pack);
    // anchored panel head shows the selected ayah itself, single line + ellipsis
    const ayahText = sheet!.classList.contains("ann-panel")
      ? activeVerse?.querySelector(".ayah-text")?.textContent?.trim()
      : undefined;
    ayahEl.hidden = !ayahText;
    ayahEl.textContent = ayahText ? `﴿ ${ayahText} ﴾` : "";
    const byKind = entriesByKind(pack);
    const kinds = [...byKind.keys()].sort((a, b) => {
      const ia = KIND_ORDER.indexOf(a), ib = KIND_ORDER.indexOf(b);
      return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib);
    });
    tabsEl.textContent = "";
    tabsEl.hidden = kinds.length < 2;
    kinds.forEach((k) => {
      const t = document.createElement("button");
      t.type = "button"; t.className = "ann-tab"; t.setAttribute("data-kind", k); t.textContent = KIND_LABEL[k] || k;
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

  document.addEventListener("click", (e) => {
    const t = e.target as HTMLElement;
    // Use composedPath (captured at dispatch) instead of t.closest: nav buttons'
    // own click handler runs first (target phase) and calls renderFoot(), which
    // replaces the footer buttons — including the one just clicked — before this
    // delegated listener runs in the bubble phase. t.closest(".ann-sheet") on a
    // now-detached button finds nothing, so this used to misread the click as
    // "outside the sheet" and immediately close it right after it reopened.
    if (e.composedPath().some((el) => el instanceof Element && el.classList.contains("ann-sheet"))) return;
    const mark = t.closest<HTMLElement>(".ann-mark");
    if (mark) {
      e.preventDefault();
      const packId = mark.getAttribute("data-ann") || "";
      const annSrc = mark.dataset.annSrc;
      if (annSrc && !document.getElementById(packId)) {
        fetch(annSrc).then((r) => (r.ok ? r.text() : "")).then((html) => {
          if (html) document.body.insertAdjacentHTML("beforeend", html);
          openSheet(packId);
        });
      } else {
        openSheet(packId);
      }
      return;
    }
    // whole-paragraph note: tap the فقرة
    const para = t.closest<HTMLElement>("[data-ann-para]");
    if (para && !t.closest("a, button")) { openSheet(para.dataset.annPara || ""); return; }
    // tapping a verse that carries notes opens its sheet
    const pack = t.closest<HTMLElement>(".verse")?.querySelector<HTMLElement>("[data-ann-pack]");
    if (pack && !t.closest("a, button")) { openSheet(pack.id); return; }
    // anchored panel is part of the page flow — outside clicks shouldn't dismiss
    // it (✕ and Escape still do); the floating modal keeps outside-click close
    if (sheet && !sheet.hidden && !sheet.classList.contains("ann-panel")) close();
  });
  function setup() {
    // Clean up any stale sheets in the DOM (e.g. from cached pages) — ALL of
    // them, not just the first: a sheet dragged via wireDragAndResize gets
    // reparented (fixed positioning, sometimes onto document.body directly),
    // so more than one can end up coexisting after a swap, which read as a
    // "duplicated menu" when the user had moved the panel before navigating.
    document.querySelectorAll(".ann-sheet").forEach((el) => el.remove());

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

// resume playback position — keyed by the audio file's own URL (stable across
// domain/path changes to the page itself), saved on pause/seek/unload and
// restored once metadata loads so seeking to a saved time is valid
function audioProgressKey(el: HTMLAudioElement): string | null {
  const src = el.currentSrc || el.querySelector("source")?.src;
  return src ? "aa-audio-progress:" + src : null;
}
document.addEventListener(
  "loadedmetadata",
  (e) => {
    const el = e.target as HTMLElement;
    if (!(el instanceof HTMLAudioElement) || !el.hasAttribute("data-audio-el")) return;
    const key = audioProgressKey(el);
    const saved = key ? parseFloat(localStorage.getItem(key) || "") : NaN;
    if (!isNaN(saved) && saved > 0 && saved < el.duration - 2) el.currentTime = saved;
    // playbackRate resets on load() in some browsers — reapply the site-wide preference
    const speed = parseFloat(localStorage.getItem(LS.audioSpeed) || "1");
    if (!isNaN(speed)) el.playbackRate = speed;
  },
  true,
);
["pause", "seeked"].forEach((evt) =>
  document.addEventListener(
    evt,
    (e) => {
      const el = e.target as HTMLElement;
      if (!(el instanceof HTMLAudioElement) || !el.hasAttribute("data-audio-el")) return;
      const key = audioProgressKey(el);
      if (key) localStorage.setItem(key, String(el.currentTime));
    },
    true,
  ),
);

// --- persistent audio bar: play/pause, seek, speed popover, minimize ⇆ FAB ---
// Real playback is the hidden native <audio data-audio-el>; this drives a
// custom fixed bar off its events so the poem/book reader never needs to
// scroll back to the top to control it. See AudioPlayer.astro for markup.
const formatTime = (s: number): string => {
  if (!isFinite(s) || s < 0) s = 0;
  return `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, "0")}`;
};
function setIcons(scope: HTMLElement, playing: boolean) {
  scope.querySelectorAll<HTMLElement>("[data-audio-icon-play]").forEach((el) => { el.hidden = playing; });
  scope.querySelectorAll<HTMLElement>("[data-audio-icon-pause]").forEach((el) => { el.hidden = !playing; });
}
function syncAudioUI(fig: HTMLElement, audio: HTMLAudioElement) {
  setIcons(fig, !audio.paused);
  const pct = audio.duration ? Math.min(100, (audio.currentTime / audio.duration) * 100) : 0;
  fig.querySelector<HTMLElement>("[data-audio-fill]")?.style.setProperty("width", `${pct}%`);
  fig.querySelector<HTMLElement>("[data-audio-handle]")?.style.setProperty("left", `${pct}%`);
  fig.querySelector<HTMLElement>("[data-audio-fab]")?.style.setProperty("--audio-progress", `${pct}%`);
  const time = fig.querySelector("[data-audio-time]");
  if (time) time.textContent = `${formatTime(audio.currentTime)} / ${formatTime(audio.duration || 0)}`;
}
function seekFromClientX(fig: HTMLElement, audio: HTMLAudioElement, rail: HTMLElement, clientX: number) {
  if (!audio.duration) return;
  const rect = rail.getBoundingClientRect();
  // physical left edge is always 0%, regardless of RTL — timelines read left→right
  const frac = Math.min(1, Math.max(0, (clientX - rect.left) / rect.width));
  audio.currentTime = frac * audio.duration;
  syncAudioUI(fig, audio);
}
// drag-to-seek (touch + mouse): pointer capture on the rail so a finger can
// scrub anywhere on screen once the drag starts — a bare click still seeks
// (pointerdown fires once with the tap position)
let seekingRail: HTMLElement | null = null;
document.addEventListener("pointerdown", (e) => {
  const rail = (e.target as HTMLElement).closest<HTMLElement>("[data-audio-rail]");
  if (!rail) return;
  const fig = rail.closest<HTMLElement>("[data-audio]");
  const audio = fig?.querySelector<HTMLAudioElement>("[data-audio-el]");
  if (!fig || !audio) return;
  seekingRail = rail;
  try { rail.setPointerCapture(e.pointerId); } catch {}
  seekFromClientX(fig, audio, rail, e.clientX);
});
document.addEventListener("pointermove", (e) => {
  if (!seekingRail) return;
  const fig = seekingRail.closest<HTMLElement>("[data-audio]")!;
  const audio = fig.querySelector<HTMLAudioElement>("[data-audio-el]");
  if (audio) seekFromClientX(fig, audio, seekingRail, e.clientX);
});
["pointerup", "pointercancel"].forEach((evt) =>
  document.addEventListener(evt, () => { seekingRail = null; }),
);

function closeSpeedMenu(fig: HTMLElement) {
  const menu = fig.querySelector<HTMLElement>("[data-audio-speed-menu]");
  if (menu) menu.hidden = true;
}
// --- multi-track lesson series: swap the <source> from data-audio-tracks JSON ---
function setTrack(fig: HTMLElement, idx: number, autoplay: boolean) {
  const tracks = JSON.parse(fig.dataset.audioTracks || "[]") as { url: string; format?: string; label?: string }[];
  const t = tracks[idx];
  const audio = fig.querySelector<HTMLAudioElement>("[data-audio-el]");
  const source = fig.querySelector<HTMLSourceElement>("[data-audio-source]");
  if (!t || !audio || !source) return;
  fig.dataset.audioIndex = String(idx);
  source.src = t.url;
  source.type = t.format === "mp3" ? "audio/mpeg" : "audio/ogg; codecs=opus";
  audio.load();
  if (autoplay) audio.play().catch(() => {});
  const label = fig.querySelector<HTMLElement>("[data-audio-track-label]");
  if (label) label.textContent = t.label || "";
  const dl = fig.querySelector<HTMLAnchorElement>("[data-audio-dl]");
  if (dl) dl.href = t.url;
  fig.querySelectorAll<HTMLElement>("[data-audio-list-menu] [data-track]").forEach((b) =>
    b.setAttribute("aria-pressed", String(b.dataset.track === String(idx))),
  );
}
document.addEventListener("click", (e) => {
  const t = e.target as HTMLElement;
  const fig = t.closest<HTMLElement>("[data-audio]");

  const toggle = t.closest<HTMLElement>("[data-audio-toggle]");
  if (toggle) {
    const audio = fig?.querySelector<HTMLAudioElement>("[data-audio-el]");
    if (audio) {
      if (audio.paused) {
        // a failed/stalled element (network blip, SW hiccup) leaves play()
        // rejecting forever — reload the source once and retry before giving up
        audio.play().catch(() => {
          const at = audio.currentTime;
          audio.load();
          audio.addEventListener("loadedmetadata", () => { if (at > 0) audio.currentTime = at; }, { once: true });
          audio.play().catch(() => {});
        });
      } else {
        audio.pause();
      }
    }
    return;
  }
  // (rail seeking handled by the pointer-drag handlers below)
  const minimize = t.closest<HTMLElement>("[data-audio-minimize]");
  if (minimize && fig) {
    fig.querySelector<HTMLElement>("[data-audio-bar]")!.hidden = true;
    fig.querySelector<HTMLElement>("[data-audio-fab]")!.hidden = false;
    document.body.classList.remove("has-audio-bar");
    closeSpeedMenu(fig);
    return;
  }
  const fab = t.closest<HTMLElement>("[data-audio-fab]");
  if (fab && fig) {
    fig.querySelector<HTMLElement>("[data-audio-fab]")!.hidden = true;
    fig.querySelector<HTMLElement>("[data-audio-bar]")!.hidden = false;
    document.body.classList.add("has-audio-bar");
    return;
  }
  const listToggle = t.closest<HTMLElement>("[data-audio-list-toggle]");
  if (listToggle && fig) {
    const menu = fig.querySelector<HTMLElement>("[data-audio-list-menu]")!;
    menu.hidden = !menu.hidden;
    closeSpeedMenu(fig);
    return;
  }
  const trackBtn = t.closest<HTMLElement>("[data-audio-list-menu] [data-track]");
  if (trackBtn && fig) {
    setTrack(fig, Number(trackBtn.dataset.track), true);
    fig.querySelector<HTMLElement>("[data-audio-list-menu]")!.hidden = true;
    return;
  }
  const speedToggle = t.closest<HTMLElement>("[data-audio-speed-toggle]");
  if (speedToggle && fig) {
    const menu = fig.querySelector<HTMLElement>("[data-audio-speed-menu]")!;
    menu.hidden = !menu.hidden;
    return;
  }
  const speedBtn = t.closest<HTMLElement>("[data-audio-speed-menu] [data-speed]");
  if (speedBtn && fig) {
    const rate = parseFloat(speedBtn.dataset.speed!);
    const audio = fig.querySelector<HTMLAudioElement>("[data-audio-el]");
    if (audio) audio.playbackRate = rate;
    localStorage.setItem(LS.audioSpeed, String(rate));
    fig.querySelectorAll<HTMLElement>("[data-audio-speed-menu] [data-speed]").forEach((b) =>
      b.setAttribute("aria-pressed", String(b === speedBtn)),
    );
    const pill = fig.querySelector("[data-audio-speed-toggle]");
    if (pill) pill.textContent = `×${speedBtn.dataset.speed}`;
    closeSpeedMenu(fig);
    return;
  }
  // click outside an open speed/track popover closes it
  document.querySelectorAll<HTMLElement>("[data-audio-speed-menu]:not([hidden]), [data-audio-list-menu]:not([hidden])").forEach((menu) => {
    if (!menu.closest("[data-audio]")?.contains(t) || (!menu.contains(t) && !t.closest("[data-audio-speed-toggle], [data-audio-list-toggle]"))) menu.hidden = true;
  });
});
// lesson series: when a track ends, move to the next one and keep playing
document.addEventListener(
  "ended",
  (e) => {
    const el = e.target as HTMLElement;
    if (!(el instanceof HTMLAudioElement) || !el.hasAttribute("data-audio-el")) return;
    const fig = el.closest<HTMLElement>("[data-audio]");
    if (!fig?.dataset.audioTracks) return;
    const next = Number(fig.dataset.audioIndex || "0") + 1;
    if (next < (JSON.parse(fig.dataset.audioTracks) as unknown[]).length) setTrack(fig, next, true);
  },
  true,
);
["play", "pause", "ended", "timeupdate", "loadedmetadata"].forEach((evt) =>
  document.addEventListener(
    evt,
    (e) => {
      const el = e.target as HTMLElement;
      if (!(el instanceof HTMLAudioElement) || !el.hasAttribute("data-audio-el")) return;
      const fig = el.closest<HTMLElement>("[data-audio]");
      if (fig) syncAudioUI(fig, el);
    },
    true,
  ),
);
// mount: reveal the bar (starts expanded) + reserve reading-column space;
// re-runs per onPage() since view transitions swap in a fresh <audio> (or none)
function initAudioBar() {
  document.body.classList.remove("has-audio-bar");
  const fig = document.querySelector<HTMLElement>("[data-audio]");
  const audio = fig?.querySelector<HTMLAudioElement>("[data-audio-el]");
  if (!fig || !audio) return;
  fig.querySelector<HTMLElement>("[data-audio-bar]")!.hidden = false;
  fig.querySelector<HTMLElement>("[data-audio-fab]")!.hidden = true;
  document.body.classList.add("has-audio-bar");
  const speed = parseFloat(localStorage.getItem(LS.audioSpeed) || "1");
  if (!isNaN(speed)) {
    audio.playbackRate = speed;
    const label = String(speed);
    fig.querySelectorAll<HTMLElement>("[data-audio-speed-menu] [data-speed]").forEach((b) =>
      b.setAttribute("aria-pressed", String(b.dataset.speed === label)),
    );
    const pill = fig.querySelector("[data-audio-speed-toggle]");
    if (pill) pill.textContent = `×${label}`;
  }
  syncAudioUI(fig, audio);
}

// direct download — the plain <a download> is ignored cross-origin (R2's
// domain differs from the site's), so fetch the file as a blob and download
// that instead (same-origin blob: URL, which browsers always honor). Needs
// CORS enabled on the R2 bucket for the site's origin; falls back to letting
// the link navigate normally (old behavior) if the fetch fails.
document.addEventListener("click", (e) => {
  const dl = (e.target as HTMLElement).closest<HTMLAnchorElement>("[data-audio-dl]");
  if (!dl) return;
  e.preventDefault();
  const url = dl.href;
  const filename = url.split("/").pop() || "audio";
  fetch(url)
    .then((r) => { if (!r.ok) throw new Error(String(r.status)); return r.blob(); })
    .then((blob) => {
      const a = document.createElement("a");
      a.href = URL.createObjectURL(blob);
      a.download = filename;
      a.click();
      URL.revokeObjectURL(a.href);
    })
    .catch(() => { window.location.href = url; });
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

// --- auto-hide header: scroll down hides it, scroll up (or reaching the top)
// brings it back — frees up screen for reading without losing the toolbar.
const topbar = document.querySelector<HTMLElement>(".topbar");
let lastY = window.scrollY;
function updateTopbarVisibility() {
  if (!topbar) return;
  const y = window.scrollY;
  const anyPopoverOpen = document.querySelector(".popover:not([hidden])") || document.querySelector("[data-drawer][data-open]");
  if (y < 80 || y < lastY - 4 || anyPopoverOpen) topbar.classList.remove("topbar-hidden");
  else if (y > lastY + 4) topbar.classList.add("topbar-hidden");
  lastY = y;
}
window.addEventListener("scroll", updateTopbarVisibility, { passive: true });

// --- sync control states from storage on load (values already applied pre-paint) ---
syncThemeButtons(currentTheme());
syncWidthButtons((root.getAttribute("data-width") as Width) || "normal");
applyVnums(localStorage.getItem(LS.vnums) !== "0");
applyPages(localStorage.getItem(LS.pages) !== "flow");
applyFootnotes(localStorage.getItem(LS.footnotes) !== "0");
if (localStorage.getItem(LS.tashkeel) === "0") applyTashkeel(false);
else document.querySelectorAll<HTMLElement>('[data-toggle="tashkeel"]').forEach((b) => b.setAttribute("aria-pressed", "true"));

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
  requeryChrome();
  enhanceProse();
  // audio speed preference: sync button UI + already-rendered <audio> elements
  // (loadedmetadata may already have fired before this listener existed on a
  // freshly-swapped page, e.g. preload="metadata" resolving fast from cache)
  const savedSpeed = localStorage.getItem(LS.audioSpeed);
  if (savedSpeed) {
    document.querySelectorAll<HTMLElement>("[data-audio-speed]").forEach((group) => {
      group.querySelectorAll<HTMLElement>("[data-speed]").forEach((b) =>
        b.setAttribute("aria-pressed", String(b.dataset.speed === savedSpeed)),
      );
    });
    document.querySelectorAll<HTMLAudioElement>("[data-audio-el]").forEach((el) => {
      el.playbackRate = parseFloat(savedSpeed);
    });
  }
  if (localStorage.getItem(LS.tashkeel) === "0") applyTashkeel(false); // re-bare new content
  updateActiveNav();
  updateProgress();
  setDrawer(false); // a navigation always closes the (persisted) drawer
  // sidebar topbar icons: only meaningful on pages that actually have a
  // ReaderSidebar (book chapters, small books, poems, articles)
  const hasSidebar = !!document.querySelector("[data-mobile-sidebar]");
  document.querySelectorAll<HTMLElement>('[data-action="sidebar:mobile-toggle"]').forEach((b) => { b.hidden = !hasSidebar; });
  const quranJump = document.querySelector<HTMLElement>("[data-quran-jump]");
  if (quranJump) quranJump.hidden = document.querySelector("main")?.dataset.activeNav !== "quran";
  // reading-settings gear: only meaningful on pages with actual body content
  // to read (book/poem/article/quran/question), not browse/listing pages
  const isReadingPage = document.querySelector("main")?.dataset.reading === "1";
  document.querySelectorAll<HTMLElement>('[data-action="settings:toggle"]').forEach((b) => { b.hidden = !isReadingPage; });
  if (!isReadingPage) document.querySelector<HTMLElement>("[data-settings-pop]")?.setAttribute("hidden", "");
  // بيت numbering only means anything on poems — hide the toggle elsewhere
  const isPoemPage = document.querySelector("main")?.dataset.activeNav === "poems";
  document.querySelectorAll<HTMLElement>('[data-toggle="verseNums"]').forEach((b) => { b.hidden = !isPoemPage; });
  syncTocCurrent();
  initAudioBar();
}
onPage();
document.addEventListener("astro:page-load", onPage);

// in-page heading TOC (nestedToc / flat فهرس العناوين list): mark the link for
// the current #hash active, since these anchors have no server-known "current
// chapter" the way the chapter-list TOC does
function syncTocCurrent() {
  document.querySelectorAll<HTMLElement>(".toc-nested-link, .chap-toc-aside .toc-box a[href^='#'], .chap-mobile-toc a[href^='#']").forEach((a) => {
    a.classList.toggle("toc-current", a.getAttribute("href") === location.hash);
  });
}
document.addEventListener("click", (e) => {
  const a = (e.target as HTMLElement).closest<HTMLAnchorElement>("a[href^='#']");
  if (a && (a.matches(".toc-nested-link") || a.closest(".chap-toc-aside .toc-box, .chap-mobile-toc"))) {
    requestAnimationFrame(syncTocCurrent);
  }
});
window.addEventListener("hashchange", syncTocCurrent);
// bfcache restore (mobile back/swipe gesture) doesn't fire Astro's router
// events, so a drawer/popover left open before navigating away could restore
// "stuck" open with no working close handler — reset chrome state on it too.
window.addEventListener("pageshow", (e) => { if (e.persisted) { requeryChrome(); setDrawer(false); closeAllPops(); } });

// View transitions swap <html>, wiping the attributes the pre-paint inline script
// set → re-apply saved theme/scale/numbers/mode after each swap (before paint, so
// no flash). Mirrors the inline script in Base.astro.
function applyStoredPrefs() {
  try {
    const t = localStorage.getItem(LS.theme);
    if (t === "noir" || t === "mono") root.setAttribute("data-theme", t);
    else root.removeAttribute("data-theme");
    const s = localStorage.getItem(LS.scale);
    if (s) root.style.setProperty("--reading-scale", s);
    const w = localStorage.getItem(LS.width);
    if (w === "narrow" || w === "wide") root.setAttribute("data-width", w);
    else root.removeAttribute("data-width");
    root.classList.toggle("hide-vnums", localStorage.getItem(LS.vnums) === "0");
    root.classList.toggle("no-tashkeel", localStorage.getItem(LS.tashkeel) === "0");
    root.classList.toggle("pages-flow", localStorage.getItem(LS.pages) === "flow");
    root.classList.toggle("hide-footnotes", localStorage.getItem(LS.footnotes) === "0");
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
