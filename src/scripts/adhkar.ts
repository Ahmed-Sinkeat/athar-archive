// أذكار الصباح والمساء — tap-to-count session, persisted per day. The full
// text of both periods is already server-rendered (progressive enhancement:
// readable and correct with no JS at all); this just adds counting, tabs,
// the jump sheet, and a completion screen on top of that markup.
const STORAGE_KEY = "aa-azkar-v1";
const RING_R = 44;
const RING_C = 2 * Math.PI * RING_R;

type Period = "morning" | "evening";
type Counts = Record<string, number>;

function todayStr(): string {
  const d = new Date();
  return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`;
}

function itemEls(root: HTMLElement): HTMLElement[] {
  return Array.from(root.querySelectorAll<HTMLElement>("[data-azkar-item]"));
}

function defaultCounts(root: HTMLElement, period: Period): Counts {
  const o: Counts = {};
  for (const el of itemEls(root)) {
    if (el.dataset.period === period) o[el.dataset.id!] = Number(el.dataset.count);
  }
  return o;
}

function loadState(root: HTMLElement): { morning: Counts; evening: Counts } {
  let saved: any = null;
  try {
    saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || "null");
  } catch {}
  const base = { morning: defaultCounts(root, "morning"), evening: defaultCounts(root, "evening") };
  if (saved && saved.date === todayStr()) {
    return {
      morning: Object.assign({}, base.morning, saved.morning),
      evening: Object.assign({}, base.evening, saved.evening),
    };
  }
  return base;
}

function saveState(counts: { morning: Counts; evening: Counts }) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ date: todayStr(), morning: counts.morning, evening: counts.evening }));
  } catch {}
}

function toArabicDigits(n: number): string {
  const d = ["٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩"];
  return String(n).replace(/[0-9]/g, (c) => d[Number(c)]);
}

function init() {
  const el = document.querySelector<HTMLElement>("[data-azkar]");
  if (!el || el.dataset.ready) return;
  el.dataset.ready = "1";
  const root: HTMLElement = el; // concretely typed (not HTMLElement|null) so the closures below don't need re-checking



  let counts = loadState(root);
  let period: Period = (root.dataset.period as Period) || "morning";
  let completeTimer: number | undefined;
  let toastTimer: number | undefined;
  let resetFlashTimer: number | undefined;
  const copyTimers = new WeakMap<HTMLElement, number>();

  const listOf = (p: Period) => itemEls(root).filter((el) => el.dataset.period === p);
  const doneCountOf = (p: Period) => listOf(p).filter((el) => counts[p][el.dataset.id!] === 0).length;

  function renderItem(el: HTMLElement) {
    const id = el.dataset.id!;
    const total = Number(el.dataset.count);
    const remaining = counts[el.dataset.period as Period][id];
    const done = remaining === 0;
    const fraction = total ? (total - remaining) / total : 0;
    el.classList.toggle("is-done", done);
    el.setAttribute("aria-pressed", String(done));
    const ring = el.querySelector<SVGCircleElement>("[data-azkar-ring]");
    if (ring) ring.style.strokeDashoffset = String(RING_C * (1 - fraction));
    const num = el.querySelector<HTMLElement>("[data-azkar-num]");
    if (num) num.textContent = done ? "" : toArabicDigits(remaining);
    const mark = el.querySelector<HTMLElement>("[data-azkar-check]");
    if (mark) mark.style.display = done ? "" : "none";
  }

  function renderProgress(p: Period) {
    const list = listOf(p);
    const done = doneCountOf(p);
    const pct = list.length ? Math.round((done / list.length) * 100) : 0;
    const fill = root.querySelector<HTMLElement>("[data-azkar-progress-fill]");
    if (fill) fill.style.width = `${pct}%`;
    const label = root.querySelector<HTMLElement>("[data-azkar-progress-label]");
    if (label) label.textContent = `${toArabicDigits(done)} من ${toArabicDigits(list.length)}`;
    root.querySelectorAll<HTMLElement>(`[data-azkar-sheet-row][data-period="${p}"]`).forEach((row) => {
      const rowDone = counts[p][row.dataset.id!] === 0;
      row.classList.toggle("is-done", rowDone);
      const status = row.querySelector<HTMLElement>("[data-azkar-sheet-status]");
      if (status) status.textContent = rowDone ? "✓" : `×${toArabicDigits(Number(row.dataset.count))}`;
    });
  }

  function renderAll(p: Period) {
    listOf(p).forEach(renderItem);
    renderProgress(p);
  }

  function switchPeriod(p: Period) {
    period = p;
    root.dataset.period = p;
    root.querySelectorAll<HTMLElement>("[data-azkar-list]").forEach((s) => (s.hidden = s.dataset.period !== p));
    root.querySelectorAll<HTMLElement>("[data-azkar-sheet-list]").forEach((s) => (s.hidden = s.dataset.period !== p));
    root.querySelectorAll<HTMLElement>("[data-azkar-tab]").forEach((t) => t.setAttribute("aria-selected", String(t.dataset.period === p)));
    const title = root.querySelector<HTMLElement>("[data-azkar-sheet-title]");
    if (title) title.textContent = p === "morning" ? "أذكار الصباح" : "أذكار المساء";
    renderAll(p);
    closeSheet();
    root.querySelector<HTMLElement>("[data-azkar-complete]")!.hidden = doneCountOf(p) !== listOf(p).length;
  }

  function openSheet() {
    root.querySelector<HTMLElement>("[data-azkar-sheet]")!.hidden = false;
    root.querySelector<HTMLElement>("[data-azkar-scrim]")!.hidden = false;
  }
  function closeSheet() {
    root.querySelector<HTMLElement>("[data-azkar-sheet]")!.hidden = true;
    root.querySelector<HTMLElement>("[data-azkar-scrim]")!.hidden = true;
  }

  function showComplete() {
    const el = root.querySelector<HTMLElement>("[data-azkar-complete]")!;
    const heading = el.querySelector<HTMLElement>("[data-azkar-complete-heading]");
    if (heading) heading.textContent = period === "morning" ? "أتممتَ أذكار الصباح" : "أتممتَ أذكار المساء";
    el.hidden = false;
    closeSheet();
  }

  function tapItem(el: HTMLElement) {
    const id = el.dataset.id!;
    const remaining = counts[period][id];
    if (remaining <= 0) return;
    counts[period][id] = remaining - 1;
    saveState(counts);
    renderItem(el);
    renderProgress(period);
    if (counts[period][id] === 0 && doneCountOf(period) === listOf(period).length) {
      clearTimeout(completeTimer);
      completeTimer = window.setTimeout(showComplete, 700);
    }
  }

  function resetSession() {
    counts[period] = defaultCounts(root, period);
    saveState(counts);
    renderAll(period);
    root.querySelector<HTMLElement>("[data-azkar-complete]")!.hidden = true;
    const btn = root.querySelector<HTMLElement>('[data-action="azkar:reset"]');
    if (btn) {
      btn.classList.add("is-flash");
      clearTimeout(resetFlashTimer);
      resetFlashTimer = window.setTimeout(() => btn.classList.remove("is-flash"), 1400);
    }
  }

  function toast(message: string) {
    const el = root.querySelector<HTMLElement>("[data-azkar-toast]");
    if (!el) return;
    el.textContent = message;
    el.classList.add("is-shown");
    clearTimeout(toastTimer);
    toastTimer = window.setTimeout(() => el.classList.remove("is-shown"), 1600);
  }

  function share() {
    const title = `أهل الأثر — ${period === "morning" ? "أذكار الصباح" : "أذكار المساء"}`;
    const url = location.href;
    if (navigator.share) {
      navigator.share({ title, url }).catch(() => {});
      return;
    }
    navigator.clipboard?.writeText(url).then(() => toast("تم نسخ رابط الصفحة"));
  }

  // shared by the per-item copy button and "copy all" — one dhikr, its
  // repeat count, and its source (when we have one) on their own lines
  function formatItem(text: string, count: number, source: string): string {
    const suffix = count > 1 ? ` (×${toArabicDigits(count)})` : "";
    return source ? `${text}${suffix}\n${source}` : `${text}${suffix}`;
  }

  function copyItem(btn: HTMLElement) {
    const text = formatItem(btn.dataset.text || "", Number(btn.dataset.count || "1"), btn.dataset.source || "");
    navigator.clipboard?.writeText(text).then(() => {
      const icon = btn.querySelector<HTMLElement>(".azkar-copy-icon");
      const done = btn.querySelector<HTMLElement>(".azkar-copy-done");
      btn.classList.add("is-copied");
      if (icon) icon.style.display = "none";
      if (done) done.style.display = "";
      clearTimeout(copyTimers.get(btn));
      copyTimers.set(btn, window.setTimeout(() => {
        btn.classList.remove("is-copied");
        if (icon) icon.style.display = "";
        if (done) done.style.display = "none";
      }, 1200));
    });
  }

  // full period list, formatted for forwarding as-is (WhatsApp/Telegram) —
  // independent of the reader's own tap progress, since this is meant to be
  // sent to someone else, not a snapshot of "what I've done so far"
  function copyAll() {
    const items = Array.from(root.querySelectorAll<HTMLElement>(`[data-action="azkar:copy-item"][data-period="${period}"]`));
    const header = period === "morning" ? "*أذكار الصباح*" : "*أذكار المساء*";
    const body = items
      .map((btn) => formatItem(btn.dataset.text || "", Number(btn.dataset.count || "1"), btn.dataset.source || ""))
      .join("\n────────────────\n");
    const footer = `أهل الأثر — ${location.origin}/adhkar`;
    navigator.clipboard?.writeText(`${header}\n\n${body}\n\n${footer}`).then(() => toast("تم نسخ كل الأذكار"));
  }

  root.addEventListener("click", (e) => {
    const t = e.target as HTMLElement;
    const item = t.closest<HTMLElement>("[data-azkar-item]");
    if (item) { tapItem(item); return; }
    const tab = t.closest<HTMLElement>("[data-azkar-tab]");
    if (tab) { switchPeriod(tab.dataset.period as Period); return; }
    const sheetRow = t.closest<HTMLElement>("[data-azkar-sheet-row]");
    if (sheetRow) { closeSheet(); return; } // native #anchor jump does the scrolling
    const actionEl = t.closest<HTMLElement>("[data-action]");
    const action = actionEl?.dataset.action;
    if (action === "azkar:index") return openSheet();
    if (action === "azkar:index-close") return closeSheet();
    if (action === "azkar:share") return share();
    if (action === "azkar:copy-all") return copyAll();
    if (action === "azkar:copy-item") return copyItem(actionEl!);
    if (action === "azkar:reset") return resetSession();
    if (action === "azkar:review") { root.querySelector<HTMLElement>("[data-azkar-complete]")!.hidden = true; return; }
    // outside click closes the sheet — mainly for desktop, where there's no
    // scrim to catch it (mobile's scrim has its own listener below)
    const sheet = root.querySelector<HTMLElement>("[data-azkar-sheet]");
    if (sheet && !sheet.hidden && !t.closest("[data-azkar-sheet]")) closeSheet();
  });
  root.querySelector("[data-azkar-scrim]")?.addEventListener("click", closeSheet);

  // auto-pick morning/evening by local hour on every load — the reader can
  // always tap the other tab manually, same as the approved design.
  const hour = new Date().getHours();
  period = hour >= 4 && hour < 12 ? "morning" : "evening";
  switchPeriod(period);
}

init();
document.addEventListener("astro:page-load", init);
