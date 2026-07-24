// /matn (متون طالب العلم): level expand/collapse, read/memorize/سharh progress
// (localStorage only — same pattern as favorites/reading-scale elsewhere),
// subject/madhab/search filtering. The list is static and server-rendered —
// this only toggles classes/attributes on existing nodes, never re-renders.
import { toArabicDigits, stripTashkeel } from "../lib/display";

const LS_PROGRESS = "aa-matn-progress";
const LS_EXPANDED = "aa-matn-expanded";

interface Progress { read?: boolean; memo?: boolean; sharh?: boolean }

function norm(s: string): string {
  return stripTashkeel(s).replace(/[إأآٱ]/g, "ا").replace(/ة/g, "ه").replace(/ى/g, "ي").toLowerCase();
}

function setup(root: HTMLElement) {
  if (root.dataset.wired) return;
  root.dataset.wired = "1";

  let progress: Record<string, Progress> = {};
  try { progress = JSON.parse(localStorage.getItem(LS_PROGRESS) || "{}"); } catch { /* corrupt/blocked storage — start fresh */ }
  let expanded: Record<string, boolean> = {};
  try { expanded = JSON.parse(localStorage.getItem(LS_EXPANDED) || "{}"); } catch { /* corrupt/blocked storage — start fresh */ }
  const persistProgress = () => { try { localStorage.setItem(LS_PROGRESS, JSON.stringify(progress)); } catch { /* ignore */ } };
  const persistExpanded = () => { try { localStorage.setItem(LS_EXPANDED, JSON.stringify(expanded)); } catch { /* ignore */ } };

  const items = Array.from(root.querySelectorAll<HTMLElement>("[data-matn-item]"));
  const levelEls = Array.from(root.querySelectorAll<HTMLElement>("[data-matn-level]"));

  function applyItemProgress(el: HTMLElement) {
    const key = el.dataset.itemKey || "";
    const p = progress[key] || {};
    el.classList.toggle("is-read", !!p.read);
    const sharhBtn = el.querySelector<HTMLElement>("[data-item-sharh]");
    const memoBtn = el.querySelector<HTMLElement>("[data-item-memo]");
    if (sharhBtn) { sharhBtn.setAttribute("aria-pressed", String(!!p.sharh)); sharhBtn.textContent = p.sharh ? "أتم الشرح" : "الشرح"; }
    if (memoBtn) { memoBtn.setAttribute("aria-pressed", String(!!p.memo)); memoBtn.textContent = p.memo ? "محفوظ" : "حفظ"; }
  }
  items.forEach(applyItemProgress);

  function updateLevel(lvlEl: HTMLElement) {
    const visible = Array.from(lvlEl.querySelectorAll<HTMLElement>("[data-matn-item]")).filter((it) => !it.hidden);
    const total = visible.length;
    const read = visible.filter((it) => it.classList.contains("is-read")).length;
    const meta = lvlEl.querySelector("[data-level-meta]");
    if (meta) meta.textContent = total ? `${toArabicDigits(total)} متنًا — أُتمّ ${toArabicDigits(read)}` : "";
    const fill = lvlEl.querySelector<HTMLElement>("[data-level-progress]");
    if (fill) fill.style.width = (total ? Math.round((read / total) * 100) : 0) + "%";
    const wasComplete = lvlEl.classList.contains("is-complete");
    const nowComplete = total > 0 && read === total;
    lvlEl.classList.toggle("is-complete", nowComplete);
    if (!wasComplete && nowComplete) {
      lvlEl.classList.add("is-celebrating");
      setTimeout(() => lvlEl.classList.remove("is-celebrating"), 1200);
    }
  }
  levelEls.forEach(updateLevel);

  // overall "أُتمّ N" in the header stat line — counted across every item
  // regardless of the current subject/madhab/search filter (a simplification
  // vs. the source design, which recounts against the subject/madhab-filtered
  // set only; total completion read doesn't feel right hidden behind a filter)
  const completedEl = root.querySelector<HTMLElement>("[data-matn-completed]");
  function updateOverallStat() {
    if (!completedEl) return;
    const read = items.filter((el) => el.classList.contains("is-read")).length;
    completedEl.textContent = toArabicDigits(read);
  }
  updateOverallStat();

  // --- level expand/collapse ---
  levelEls.forEach((lvlEl) => {
    const id = lvlEl.dataset.levelId || "";
    const header = lvlEl.querySelector<HTMLElement>("[data-level-toggle]");
    if (!header) return;
    const startOpen = expanded[id] !== false; // default: expanded (only a handful of items total)
    header.setAttribute("aria-expanded", String(startOpen));
    header.addEventListener("click", () => {
      const open = header.getAttribute("aria-expanded") !== "true";
      header.setAttribute("aria-expanded", String(open));
      expanded[id] = open;
      persistExpanded();
    });
  });

  // --- read / سharh / حفظ toggles ---
  items.forEach((el) => {
    const key = el.dataset.itemKey || "";
    const readBtn = el.querySelector<HTMLElement>("[data-item-read-toggle]");
    readBtn?.addEventListener("click", () => {
      const cur = progress[key] || {};
      progress[key] = { ...cur, read: !cur.read };
      persistProgress();
      applyItemProgress(el);
      const lvlEl = el.closest<HTMLElement>("[data-matn-level]");
      if (lvlEl) updateLevel(lvlEl);
      updateOverallStat();
    });
    el.querySelector<HTMLElement>("[data-item-sharh]")?.addEventListener("click", () => {
      const cur = progress[key] || {};
      progress[key] = { ...cur, sharh: !cur.sharh };
      persistProgress();
      applyItemProgress(el);
    });
    el.querySelector<HTMLElement>("[data-item-memo]")?.addEventListener("click", () => {
      const cur = progress[key] || {};
      progress[key] = { ...cur, memo: !cur.memo };
      persistProgress();
      applyItemProgress(el);
    });
  });

  // --- combined filter state: subject chip + madhab + search (scope+query) ---
  let subjectFilter = "all";
  let madhabFilter = "all";
  let scope: "all" | "title" | "text" = "all";
  let query = "";

  const searchInput = root.querySelector<HTMLInputElement>("[data-matn-search]");
  const clearBtn = root.querySelector<HTMLElement>("[data-matn-clear]");
  const emptyEl = root.querySelector<HTMLElement>("[data-matn-empty]");

  function applyFilters() {
    const q = norm(query.trim());
    let anyVisible = false;
    levelEls.forEach((lvlEl) => {
      let levelHasVisible = false;
      lvlEl.querySelectorAll<HTMLElement>("[data-matn-item]").forEach((el) => {
        const subj = el.dataset.subject || "";
        const madhab = el.dataset.madhab || "";
        const subjOk = subjectFilter === "all" || subj === subjectFilter;
        const madhabOk = subj !== "fiqh" || madhabFilter === "all" || madhab === madhabFilter;
        let searchOk = true;
        if (q) {
          const title = norm(el.dataset.title || "");
          const author = norm(el.dataset.author || "");
          const text = norm(el.dataset.text || "");
          const titleHit = title.includes(q) || author.includes(q);
          const textHit = text.includes(q);
          searchOk = scope === "title" ? titleHit : scope === "text" ? textHit : titleHit || textHit;
        }
        const visible = subjOk && madhabOk && searchOk;
        el.hidden = !visible;
        if (visible) levelHasVisible = true;
      });
      lvlEl.hidden = !levelHasVisible;
      if (levelHasVisible) anyVisible = true;
      // reveal matches while actively searching, regardless of the saved
      // collapsed state — restored once the query is cleared
      const header = lvlEl.querySelector<HTMLElement>("[data-level-toggle]");
      if (header && q) header.setAttribute("aria-expanded", "true");
      else if (header) header.setAttribute("aria-expanded", String(expanded[lvlEl.dataset.levelId || ""] !== false));
      updateLevel(lvlEl);
    });
    if (emptyEl) emptyEl.hidden = anyVisible;
    if (clearBtn) clearBtn.hidden = !query;
  }

  // --- subject chips ---
  root.querySelectorAll<HTMLElement>("[data-subject-chip]").forEach((chip) => {
    chip.addEventListener("click", () => {
      root.querySelectorAll<HTMLElement>("[data-subject-chip]").forEach((b) => b.setAttribute("aria-pressed", String(b === chip)));
      subjectFilter = chip.dataset.subjectChip || "all";
      const madhabWrap = root.querySelector<HTMLElement>("[data-madhab-wrap]");
      if (madhabWrap) madhabWrap.hidden = !(subjectFilter === "all" || subjectFilter === "fiqh");
      applyFilters();
    });
  });

  // --- madhab picker ---
  const madhabWrap = root.querySelector<HTMLElement>("[data-madhab-wrap]");
  const madhabBtn = root.querySelector<HTMLElement>("[data-madhab-toggle]");
  const madhabPopover = root.querySelector<HTMLElement>("[data-madhab-popover]");
  const madhabLabel = root.querySelector<HTMLElement>("[data-madhab-label]");
  const MADHAB_BTN_LABEL: Record<string, string> = { all: "كل المذاهب", hanbali: "حنبلي", maliki: "مالكي", shafii: "شافعي" };
  madhabBtn?.addEventListener("click", () => {
    const open = madhabBtn.getAttribute("aria-expanded") !== "true";
    madhabBtn.setAttribute("aria-expanded", String(open));
    if (madhabPopover) madhabPopover.hidden = !open;
  });
  document.addEventListener("click", (e) => {
    if (!madhabPopover || madhabPopover.hidden) return;
    if (madhabWrap?.contains(e.target as Node)) return;
    madhabPopover.hidden = true;
    madhabBtn?.setAttribute("aria-expanded", "false");
  });
  root.querySelectorAll<HTMLElement>("[data-madhab-opt]").forEach((opt) => {
    opt.addEventListener("click", () => {
      madhabFilter = opt.dataset.madhabOpt || "all";
      root.querySelectorAll<HTMLElement>("[data-madhab-opt]").forEach((b) => b.setAttribute("aria-pressed", String(b === opt)));
      if (madhabLabel) madhabLabel.textContent = "المذهب: " + MADHAB_BTN_LABEL[madhabFilter];
      madhabWrap?.classList.toggle("is-active", madhabFilter !== "all");
      if (madhabPopover) madhabPopover.hidden = true;
      madhabBtn?.setAttribute("aria-expanded", "false");
      applyFilters();
    });
  });

  // --- search scope + input ---
  root.querySelectorAll<HTMLElement>("[data-matn-scope]").forEach((btn) => {
    btn.addEventListener("click", () => {
      root.querySelectorAll<HTMLElement>("[data-matn-scope]").forEach((b) => b.setAttribute("aria-pressed", String(b === btn)));
      scope = (btn.dataset.matnScope as typeof scope) || "all";
      applyFilters();
    });
  });
  searchInput?.addEventListener("input", () => { query = searchInput.value; applyFilters(); });
  clearBtn?.addEventListener("click", () => { query = ""; if (searchInput) searchInput.value = ""; applyFilters(); });
}

document.addEventListener("astro:page-load", () => {
  const root = document.querySelector<HTMLElement>("[data-matn-page]");
  if (root) setup(root);
});
