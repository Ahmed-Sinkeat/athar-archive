// Client logic for /compose — renders a form per content type and live-builds a
// valid file.md (plus a companion audio entity when an audio url is given).
// Two modes: "add" (blank) and "edit" (pick an existing item → prefill).
// Reference fields (الناظم/الموضوعات/المتن…) are searchable pickers over the
// content index embedded in #cdata, so maintainers pick names, never raw slugs.
// Static + client-only: it generates files to copy/commit; nothing is uploaded.

import { FORMS, SLUG_RE, buildFiles, type FormDef, type Field } from "../lib/content-forms";
import { config } from "../../ahlalathar.config";

interface Item { c: string; id: string; title: string; data: Record<string, unknown>; body: string }

// One-line Arabic explainer per type, shown under the chosen type card.
const TYPE_DESC: Record<string, string> = {
  book: "متنٌ أو مرجعٌ أو مجموعٌ نثريّ — يُحقَّق ويُشكَّل ويُقرأ في صفحته.",
  poem: "منظومةٌ علميةٌ: أبياتٌ مُرقَّمةٌ مُشكَّلةٌ، مع شروحها وتخريجها.",
  lesson: "درسٌ مُفرَّغٌ من سلسلة شرح: نصُّ الدرس مع صوته وفوائده.",
  series: "سلسلةُ دروسٍ تشرح كتابًا أو منظومة، تجمع دروسها بالترتيب.",
  person: "ترجمةُ عَلَمٍ: ناظمٍ أو مصنِّفٍ أو شارح، وما له في الأرشيف.",
  annotation: "شرحٌ أو حاشيةٌ أو تخريجٌ يُعلَّق على بيتٍ أو فقرةٍ في متنٍ بعينه.",
  benefit: "فائدةٌ مستخرَجةٌ من درسٍ أو كتابٍ أو مقال.",
  highlight: "مختارُ الأسبوع: آيةٌ أو حديثٌ أو بيتٌ يُعرض في الصفحة الرئيسية.",
  article: "مقالةٌ علميةٌ مستقلّة.",
  question: "مسألةٌ وجوابُها، مُصنَّفةٌ تحت موضوعاتها.",
  subject: "تصنيفٌ عام (فنٌّ) تندرج تحته موضوعات.",
  topic: "موضوعٌ يندرج تحت تصنيف، تُربط به الكتبُ والمنظوماتُ والمسائل.",
  audio: "صوتيةٌ مرتبطةٌ بكتابٍ أو منظومةٍ أو درسٍ أو مقال.",
  announcement: "إعلانٌ يظهر في الصفحة الرئيسية.",
};

const typeSel = document.getElementById("ctype") as HTMLSelectElement | null;
const fieldsEl = document.getElementById("cfields");
const previewEl = document.getElementById("cpreview");
if (typeSel && fieldsEl && previewEl) {
  const today = new Date().toISOString().slice(0, 10);
  let curMode: "add" | "edit" = "add"; // drives the «نشر إلى GitHub» link (new-file vs edit-file)
  // topics the maintainer creates inline (الموضوعات field) → emitted as extra topic files
  let pendingTopics: { slug: string; title: string; subject: string }[] = [];

  // --- content index (for pickers + edit prefill) ---
  const items: Item[] = JSON.parse(document.getElementById("cdata")?.textContent || "[]");
  const collLabel: Record<string, string> = Object.fromEntries(FORMS.map((f) => [f.collection, f.label]));
  const itemsOf = (colls: string[]) => items.filter((i) => colls.includes(i.c));
  function titleToId(colls: string[], title: string): Item | null {
    const t = title.trim();
    // ponytail: first title match wins across the union (cross-type dupes are rare).
    return itemsOf(colls).find((i) => i.title === t) || items.find((i) => colls.includes(i.c) && i.id === t) || null;
  }
  const idToTitle = (colls: string[], id: string) => itemsOf(colls).find((i) => i.id === id)?.title ?? id;

  // build a <datalist> per ref signature on demand; option value = searchable title
  function ensureDatalist(colls: string[]): string {
    const dlId = "dl-" + colls.join("_");
    if (!document.getElementById(dlId)) {
      const dl = document.createElement("datalist");
      dl.id = dlId;
      itemsOf(colls).forEach((i) => {
        const o = document.createElement("option");
        o.value = i.title;
        if (colls.length > 1) o.label = collLabel[i.c] || i.c;
        dl.appendChild(o);
      });
      document.body.appendChild(dl);
    }
    return dlId;
  }

  // --- type chooser (add mode) ---
  FORMS.forEach((f) => {
    const o = document.createElement("option");
    o.value = f.collection;
    o.textContent = f.label;
    typeSel.appendChild(o);
  });
  const cardsEl = document.getElementById("ctypecards");
  const descEl = document.getElementById("ctypedesc");
  function setType(col: string) {
    typeSel!.value = col;
    cardsEl?.querySelectorAll<HTMLElement>("[data-type]").forEach((b) =>
      b.setAttribute("aria-pressed", String(b.dataset.type === col)),
    );
    if (descEl) descEl.textContent = TYPE_DESC[col] ?? "";
    renderFields();
  }
  // the everyday types up front; the rest tucked under «أنواع أخرى»
  const FEATURED = ["book", "poem", "lesson", "series", "person"];
  function makeCard(f: FormDef, onClick: () => void = () => setType(f.collection)): HTMLButtonElement {
    const b = document.createElement("button");
    b.type = "button";
    b.className = "compose-type";
    b.dataset.type = f.collection;
    b.textContent = f.label;
    b.setAttribute("aria-pressed", "false");
    b.addEventListener("click", onClick);
    return b;
  }
  if (cardsEl) {
    FEATURED.map((c) => FORMS.find((f) => f.collection === c))
      .filter((f): f is FormDef => !!f)
      .forEach((f) => cardsEl.appendChild(makeCard(f)));
    const rest = FORMS.filter((f) => !FEATURED.includes(f.collection));
    if (rest.length) {
      const more = document.createElement("details");
      more.className = "compose-more";
      const sum = document.createElement("summary");
      sum.textContent = "أنواع أخرى";
      const box = document.createElement("div");
      box.className = "compose-types";
      rest.forEach((f) => box.appendChild(makeCard(f)));
      more.append(sum, box);
      cardsEl.appendChild(more);
    }
  }

  const currentDef = (): FormDef | undefined => FORMS.find((f) => f.collection === typeSel.value);

  // group fields into guided, one-at-a-time sections beside the live preview
  const SHARED_KEYS = new Set(["slug", "title", "status", "published_at"]);
  function makeGroup(label: string, open: boolean): { d: HTMLDetailsElement; box: HTMLElement } {
    const d = document.createElement("details");
    d.className = "cf-group";
    // ponytail: independent sections (no exclusive `name`) — opening one no longer slams the others shut.
    d.open = open;
    const s = document.createElement("summary");
    s.textContent = label;
    const box = document.createElement("div");
    box.className = "cf-group-body";
    d.append(s, box);
    return { d, box };
  }

  function renderFields() {
    fieldsEl!.textContent = "";
    pendingTopics = []; // new-topic drafts are per-form
    const def = currentDef();
    if (!def) return;
    const basics = makeGroup("أساسيات", true);
    const details = makeGroup("تفاصيل", false);
    const text = makeGroup("النص", false);
    for (const f of def.fields) {
      const wrap = renderField(f);
      const box = SHARED_KEYS.has(f.key) ? basics.box
        : f.kind === "body" || f.kind === "verses" ? text.box
        : details.box;
      box.appendChild(wrap);
    }
    [basics, details, text].forEach((g) => { if (g.box.children.length) fieldsEl!.appendChild(g.d); });
    update();
  }

  function renderField(f: Field): HTMLElement {
    {
      const wrap = document.createElement("div");
      wrap.className = "field";

      const label = document.createElement("label");
      label.textContent = f.label + (f.required ? " *" : "");
      label.htmlFor = "f-" + f.key;
      wrap.appendChild(label);

      let input: HTMLElement;
      if (f.kind === "refs") {
        input = buildRefs(f);
      } else if (f.kind === "ref") {
        input = buildRef(f);
      } else if (f.kind === "select") {
        const sel = document.createElement("select");
        (f.options || []).forEach((opt) => {
          const o = document.createElement("option");
          o.value = opt;
          o.textContent = f.optionLabels?.[opt] ?? (opt === "" ? "—" : opt);
          sel.appendChild(o);
        });
        sel.dataset.key = f.key;
        sel.className = "cinput";
        input = sel;
      } else if (f.kind === "body" || f.kind === "verses" || f.kind === "array" || f.kind === "textarea") {
        const ta = document.createElement("textarea");
        ta.rows = f.kind === "body" || f.kind === "verses" ? 8 : 3;
        ta.dataset.key = f.key;
        ta.className = "cinput";
        input = ta;
      } else {
        const inp = document.createElement("input");
        inp.type = f.kind === "date" ? "date" : f.kind === "number" ? "number" : f.kind === "url" ? "url" : "text";
        inp.dataset.key = f.key;
        inp.className = "cinput";
        input = inp;
      }
      if (input.tagName !== "DIV") input.id = "f-" + f.key; // ref/refs are wrappers
      if (f.default && "value" in input) (input as HTMLInputElement).value = f.default;
      if (f.kind === "date" && f.required) (input as HTMLInputElement).value = today;
      wrap.appendChild(input);

      // long text (a whole book/متن) → upload a .txt/.md file instead of pasting
      if (f.kind === "body" || f.kind === "verses") {
        const up = document.createElement("label");
        up.className = "file-up";
        up.textContent = "رفع ملفّ نصّي (.txt / .md)";
        const fi = document.createElement("input");
        fi.type = "file";
        fi.accept = ".txt,.md,text/plain,text/markdown";
        fi.className = "sr-only";
        fi.addEventListener("change", () => {
          const file = fi.files?.[0];
          if (file) file.text().then((txt) => { (input as HTMLTextAreaElement).value = txt; update(); });
        });
        up.appendChild(fi);
        wrap.appendChild(up);
      }

      if (f.help) {
        const h = document.createElement("div");
        h.className = "faint field-help";
        h.textContent = f.help;
        wrap.appendChild(h);
      }
      return wrap;
    }
  }

  // single searchable reference → text input bound to a <datalist> of titles.
  function buildRef(f: Field): HTMLInputElement {
    const colls = (f.ref || "").split("|");
    const inp = document.createElement("input");
    inp.type = "text";
    inp.className = "cinput";
    inp.dataset.key = f.key;
    inp.dataset.ref = f.ref || "";
    inp.setAttribute("list", ensureDatalist(colls));
    inp.autocomplete = "off";
    if (f.syncType) {
      inp.addEventListener("input", () => {
        const hit = titleToId(colls, inp.value);
        if (hit) {
          const sel = fieldsEl!.querySelector<HTMLSelectElement>(`[data-key="${f.syncType}"]`);
          if (sel && sel.value !== hit.c) sel.value = hit.c;
        }
      });
    }
    return inp;
  }

  // many references → searchable checklist; a hidden input mirrors the chosen ids.
  // The الموضوعات field (ref="topic") is grouped by تصنيف, filterable by تصنيف,
  // and lets the maintainer create a new موضوع inline (≤5 total).
  function buildRefs(f: Field): HTMLElement {
    const colls = (f.ref || "").split("|");
    const isTopic = f.ref === "topic";
    const wrap = document.createElement("div");
    wrap.className = "ref-checklist";
    wrap.dataset.refsKey = f.key;

    const hidden = document.createElement("input");
    hidden.type = "hidden";
    hidden.dataset.key = f.key;

    const list = document.createElement("div");
    list.className = "ref-list";

    const syncHidden = () => {
      hidden.value = [...list.querySelectorAll<HTMLInputElement>("input:checked")].map((c) => c.value).join("\n");
      update();
    };
    const addCheck = (id: string, title: string, subject = "", isNew = false): HTMLInputElement => {
      const lab = document.createElement("label");
      lab.className = "ref-check";
      lab.dataset.name = title;
      lab.dataset.subject = subject;
      const cb = document.createElement("input");
      cb.type = "checkbox";
      cb.value = id;
      cb.addEventListener("change", () => {
        if (cb.checked && list.querySelectorAll<HTMLInputElement>("input:checked").length > 5) { cb.checked = false; return; } // ≤5
        syncHidden();
      });
      lab.append(cb, document.createTextNode(" " + title + (isNew ? " (جديد)" : "")));
      list.appendChild(lab);
      return cb;
    };

    const search = document.createElement("input");
    search.type = "text";
    search.className = "cinput ref-search";
    search.placeholder = "بحث…";
    search.autocomplete = "off";

    if (!isTopic) {
      itemsOf(colls).forEach((i) => addCheck(i.id, i.title));
      search.addEventListener("input", () => {
        const q = search.value.trim();
        list.querySelectorAll<HTMLElement>(".ref-check").forEach((lab) =>
          lab.classList.toggle("is-hidden", q !== "" && !(lab.dataset.name || "").includes(q)),
        );
      });
      wrap.append(hidden, search, list);
      return wrap;
    }

    // --- topics: group by تصنيف + filter + inline add ---
    const subjects = items.filter((i) => i.c === "subject");
    const subjTitle = (sid: unknown) => subjects.find((s) => s.id === sid)?.title || "بلا تصنيف";

    const filter = document.createElement("select");
    filter.className = "cinput ref-subject-filter";
    const optAll = document.createElement("option");
    optAll.value = ""; optAll.textContent = "اختَرِ التصنيف…";
    filter.appendChild(optAll);
    subjects.forEach((s) => { const o = document.createElement("option"); o.value = s.title; o.textContent = s.title; filter.appendChild(o); });

    // grouped checkboxes (heading per تصنيف)
    const byGroup = new Map<string, Item[]>();
    itemsOf(colls).forEach((t) => {
      const st = subjTitle((t.data as Record<string, unknown>).subject);
      if (!byGroup.has(st)) byGroup.set(st, []);
      byGroup.get(st)!.push(t);
    });
    for (const [st, topics] of byGroup) {
      const head = document.createElement("div");
      head.className = "ref-group-head"; head.dataset.subject = st; head.textContent = st;
      list.appendChild(head);
      topics.forEach((t) => addCheck(t.id, t.title, st));
    }

    const applyFilter = () => {
      const q = search.value.trim(), sf = filter.value;
      list.querySelectorAll<HTMLElement>(".ref-check").forEach((lab) => {
        const ok = (q === "" || (lab.dataset.name || "").includes(q)) && (sf === "" || lab.dataset.subject === sf);
        lab.classList.toggle("is-hidden", !ok);
      });
      list.querySelectorAll<HTMLElement>(".ref-group-head").forEach((h) =>
        h.classList.toggle("is-hidden", sf !== "" && h.dataset.subject !== sf),
      );
    };
    search.addEventListener("input", applyFilter);
    filter.addEventListener("change", applyFilter);

    // inline "add a new موضوع" under the chosen تصنيف
    const addBox = document.createElement("div");
    addBox.className = "ref-add";
    const nameI = document.createElement("input"); nameI.className = "cinput"; nameI.placeholder = "اسم موضوعٍ جديد";
    const slugI = document.createElement("input"); slugI.className = "cinput"; slugI.placeholder = "اسم الملف (إنجليزي)";
    const addBtn = document.createElement("button"); addBtn.type = "button"; addBtn.className = "btn"; addBtn.textContent = "+ موضوع";
    const note = document.createElement("div"); note.className = "faint field-help";
    note.textContent = "اختَرِ التصنيفَ من الأعلى، ثم أضِفْ موضوعًا تحته (يُنشأ ملفُّه مع الحفظ).";
    addBtn.addEventListener("click", () => {
      const title = nameI.value.trim(), slug = slugI.value.trim(), st = filter.value;
      if (!st) { note.textContent = "اختَرِ التصنيفَ أولًا."; return; }
      if (!title || !slug) { note.textContent = "اكتبِ الاسمَ واسمَ الملف."; return; }
      if (!SLUG_RE.test(slug)) { note.textContent = "اسمُ الملف: إنجليزيٌّ صغيرٌ وأرقامٌ وشَرَطات."; return; }
      if (items.some((i) => i.c === "topic" && i.id === slug) || pendingTopics.some((p) => p.slug === slug)) { note.textContent = "اسمُ الملفِّ مستخدَم."; return; }
      const subjectId = subjects.find((s) => s.title === st)?.id || "";
      pendingTopics.push({ slug, title, subject: subjectId });
      // ensure the group exists, then add a checked box under it
      if (!list.querySelector(`.ref-group-head[data-subject="${st}"]`)) {
        const head = document.createElement("div"); head.className = "ref-group-head"; head.dataset.subject = st; head.textContent = st; list.appendChild(head);
      }
      addCheck(slug, title, st, true).checked = true;
      syncHidden();
      nameI.value = ""; slugI.value = "";
      note.textContent = "أُضيفَ ✓ سيظهر ملفُّه أسفلَ المعاينة.";
    });
    addBox.append(nameI, slugI, addBtn, note);

    wrap.append(hidden, filter, search, list, addBox);
    return wrap;
  }

  function collectValues(): Record<string, string> {
    const v: Record<string, string> = {};
    fieldsEl!.querySelectorAll<HTMLElement>("[data-key]").forEach((el) => {
      v[el.dataset.key!] = (el as HTMLInputElement).value;
    });
    return v;
  }

  // ref inputs hold the visible title — resolve to the entity id before building.
  function resolveRefs(def: FormDef, values: Record<string, string>): Record<string, string> {
    const out = { ...values };
    for (const f of def.fields) {
      if (f.kind === "ref") {
        const hit = titleToId((f.ref || "").split("|"), out[f.key] || "");
        if (hit) out[f.key] = hit.id;
      }
    }
    return out;
  }

  function validate(def: FormDef, values: Record<string, string>): string[] {
    const errs: string[] = [];
    for (const f of def.fields) {
      const val = (values[f.key] || "").trim();
      if (f.required && !val) errs.push(`${f.label}: مطلوب`);
      if (f.kind === "slug" && val && !SLUG_RE.test(val)) errs.push(`${f.label}: صيغة غير صحيحة`);
    }
    return errs;
  }

  function update() {
    const def = currentDef();
    if (!def) return;
    const raw = collectValues();
    const values = resolveRefs(def, raw);
    const errs = validate(def, values);
    // A single ref that didn't resolve to an existing entity → block & tell the
    // maintainer to add it first (raw name left in → would break the build).
    for (const f of def.fields) {
      if (f.kind !== "ref") continue;
      const v = (values[f.key] || "").trim();
      if (v && !itemsOf((f.ref || "").split("|")).some((i) => i.id === v))
        errs.push(`${f.label}: «${(raw[f.key] || "").trim()}» غير موجود — أضِفْه أولًا ثم اختَرْه من القائمة`);
    }
    const files = buildFiles(def, values);
    // emit a topic file for each inline-created موضوع that's actually selected
    if (pendingTopics.length) {
      const topicDef = FORMS.find((d) => d.collection === "topic");
      const selected = new Set((values.topics || "").split("\n").map((s) => s.trim()).filter(Boolean));
      for (const pt of pendingTopics) {
        if (selected.has(pt.slug) && topicDef)
          files.push(...buildFiles(topicDef, { slug: pt.slug, title: pt.title, status: "published", published_at: today, subject: pt.subject }));
      }
    }
    previewEl!.textContent = "";

    if (errs.length) {
      const e = document.createElement("div");
      e.className = "compose-errors";
      e.textContent = "أكمِلْ: " + errs.join(" · ");
      previewEl!.appendChild(e);
    }

    // The #1 "I published it but it's not on the site" cause: status stayed مسودة.
    if ((values.status || "").trim() === "draft") {
      const d = document.createElement("div");
      d.className = "compose-draft-note";
      d.textContent = "ملاحظة: الحالة «مسودة» — لن يظهرَ على الموقع. اجعلِ الحالةَ «منشور» ليظهر.";
      previewEl!.appendChild(d);
    }

    files.forEach((file) => {
      const box = document.createElement("div");
      box.className = "compose-file";

      const head = document.createElement("div");
      head.className = "compose-file-head";
      const path = document.createElement("code");
      path.textContent = file.path;
      head.appendChild(path);

      const copyBtn = document.createElement("button");
      copyBtn.className = "btn";
      copyBtn.type = "button";
      copyBtn.textContent = "نسخ";
      copyBtn.onclick = () =>
        navigator.clipboard?.writeText(file.content).then(() => {
          copyBtn.textContent = "✓ نُسخ";
          setTimeout(() => (copyBtn.textContent = "نسخ"), 1200);
        });

      const dlBtn = document.createElement("button");
      dlBtn.className = "btn";
      dlBtn.type = "button";
      dlBtn.textContent = "تنزيل .md";
      dlBtn.onclick = () => {
        const blob = new Blob([file.content], { type: "text/markdown" });
        const a = document.createElement("a");
        a.href = URL.createObjectURL(blob);
        a.download = file.path.split("/").pop() || "content.md";
        a.click();
        URL.revokeObjectURL(a.href);
      };

      const ghBtn = document.createElement("button");
      ghBtn.className = "btn-accent";
      ghBtn.type = "button";
      ghBtn.textContent = "نشر إلى GitHub";
      ghBtn.disabled = errs.length > 0;
      if (errs.length) ghBtn.title = "أكمِلِ الحقولَ المطلوبةَ أولًا";
      ghBtn.onclick = () => {
        // New file: prefill BOTH path + content via the URL → GitHub opens the editor
        // already filled, just press Commit (no paste). file.path is URL-safe; only the
        // content needs encoding. Also copy to clipboard as a backup if a very long
        // body got truncated. Edit: GitHub can't prefill the edit page → copy + paste.
        const base = `https://github.com/${config.repo}`;
        navigator.clipboard?.writeText(file.content);
        if (curMode === "edit") {
          window.open(`${base}/edit/${config.repoBranch}/${file.path}`, "_blank", "noopener");
          ghBtn.textContent = "نُسخ ✓ — الصقْه فوقَ القديم واحفظ";
        } else {
          const u = `${base}/new/${config.repoBranch}?filename=${file.path}&value=${encodeURIComponent(file.content)}`;
          window.open(u, "_blank", "noopener");
          ghBtn.textContent = "فُتح GitHub — اضغطْ Commit";
        }
        setTimeout(() => (ghBtn.textContent = "نشر إلى GitHub"), 3500);
      };

      head.append(ghBtn, copyBtn, dlBtn);
      box.appendChild(head);

      const pre = document.createElement("pre");
      pre.className = "compose-pre";
      pre.textContent = file.content;
      box.appendChild(pre);

      previewEl!.appendChild(box);
    });
  }

  // --- add / edit modes ---
  const addPane = document.getElementById("addpane");
  const editPane = document.getElementById("editpane");
  const editSearch = document.getElementById("cedit-search") as HTMLInputElement | null;
  const editHint = document.getElementById("cedit-hint");
  const editDelBtn = document.getElementById("cedit-del") as HTMLButtonElement | null;
  const editTypesEl = document.getElementById("cedittypes");
  const dlAll = document.getElementById("dl-all");

  // Edit flow: pick the type first, then search only within that type.
  let editType = "";
  function fillEditDatalist() {
    if (!dlAll) return;
    dlAll.textContent = "";
    items.filter((i) => i.c === editType).forEach((i) => {
      const o = document.createElement("option");
      o.value = i.title;
      dlAll.appendChild(o);
    });
  }
  function setEditType(col: string) {
    editType = col;
    editTypesEl?.querySelectorAll<HTMLElement>("[data-type]").forEach((b) =>
      b.setAttribute("aria-pressed", String(b.dataset.type === col)),
    );
    fillEditDatalist();
    if (editSearch) {
      editSearch.value = "";
      editSearch.placeholder = `ابحثْ في ${collLabel[col] || col}…`;
      editSearch.disabled = false;
      editSearch.focus();
    }
    if (editHint) editHint.textContent = "";
  }
  if (editTypesEl) FORMS.forEach((f) => editTypesEl.appendChild(makeCard(f, () => setEditType(f.collection))));

  function setMode(mode: "add" | "edit") {
    curMode = mode;
    document.querySelectorAll<HTMLElement>("[data-cmode]").forEach((b) =>
      b.setAttribute("aria-pressed", String(b.dataset.cmode === mode)),
    );
    if (addPane) addPane.hidden = mode !== "add";
    if (editPane) editPane.hidden = mode !== "edit";
    if (mode === "add") { if (editHint) editHint.textContent = ""; if (editDelBtn) editDelBtn.hidden = true; setType(typeSel!.value || FORMS[0].collection); }
    if (mode === "edit" && editSearch && !editType) {
      editSearch.placeholder = "اختَرِ النوع أولًا…";
      editSearch.disabled = true;
    }
  }
  document.querySelectorAll<HTMLElement>("[data-cmode]").forEach((b) =>
    b.addEventListener("click", () => setMode(b.dataset.cmode as "add" | "edit")),
  );

  function setInput(key: string, value: string) {
    const el = fieldsEl!.querySelector<HTMLInputElement>(`[data-key="${key}"]`);
    if (el) el.value = value;
  }
  function setRefs(key: string, ids: string[]) {
    const wrap = fieldsEl!.querySelector<HTMLElement>(`[data-refs-key="${key}"]`);
    if (!wrap) return;
    wrap.querySelectorAll<HTMLInputElement>('input[type="checkbox"]').forEach((cb) => (cb.checked = ids.includes(cb.value)));
    const hidden = wrap.querySelector<HTMLInputElement>('input[type="hidden"]');
    if (hidden) hidden.value = ids.join("\n");
  }
  function loadItem(it: Item) {
    setType(it.c);
    const def = currentDef();
    if (!def) return;
    for (const f of def.fields) {
      if (f.key === "slug") { setInput("slug", it.id); continue; }
      if (f.kind === "body" || f.kind === "verses") { setInput(f.key, it.body); continue; }
      const v = it.data[f.key];
      if (v == null) continue;
      const arr = Array.isArray(v) ? (v as unknown[]).map(String) : [String(v)];
      if (f.kind === "refs") setRefs(f.key, arr);
      else if (f.kind === "array") setInput(f.key, arr.join("\n"));
      else if (f.kind === "ref") setInput(f.key, idToTitle((f.ref || "").split("|"), String(v)));
      else setInput(f.key, String(v));
    }
    // ponytail: a companion audio_url isn't reconstructed on edit — edit the صوتية entity directly.
    update();
    if (editHint) editHint.textContent = `تُعدِّل: ${it.title} — اضغطْ «نشر إلى GitHub» ليُنسخ النصُّ وتُفتح صفحةُ التعديل، ثم الصقْه واحفظ.`;
    if (editDelBtn) {
      const filePath = `src/content/${it.c}/${it.id}.md`;
      editDelBtn.hidden = false;
      editDelBtn.onclick = () => {
        if (!confirm(`حذف «${it.title}»؟\n${filePath}\n\nسيُفتح GitHub لتأكيد الحذف.`)) return;
        window.open(`https://github.com/${config.repo}/delete/${config.repoBranch}/${filePath}`, "_blank", "noopener");
      };
    }
  }
  function tryLoadFromSearch() {
    const q = editSearch!.value.trim();
    const it = items.find((i) => i.c === editType && i.title === q);
    if (it) loadItem(it);
  }
  editSearch?.addEventListener("change", tryLoadFromSearch);
  editSearch?.addEventListener("input", tryLoadFromSearch);

  typeSel.addEventListener("change", renderFields);
  fieldsEl.addEventListener("input", update);
  setType(FORMS[0].collection);
}
