// Reading-preferences & chrome enhancement (progressive — content is fully
// readable without it). Persists to localStorage; the pre-paint inline script
// in Base.astro applies theme/scale/vnums before first paint to avoid flash.

import { stripTashkeel } from "../lib/display";

const LS = {
  theme: "aa-theme",
  scale: "aa-scale",
  tashkeel: "aa-tashkeel",
  vnums: "aa-vnums",
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
};

document.addEventListener("click", (e) => {
  const el = (e.target as HTMLElement).closest<HTMLElement>("[data-action]");
  if (!el) return;
  const fn = actions[el.dataset.action || ""];
  if (fn) { e.preventDefault(); fn(); }
});
document.querySelector("[data-drawer-backdrop]")?.addEventListener("click", () => setDrawer(false));

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
