// Client logic for /compose — renders a form per content type and live-builds
// a valid file.md (plus a companion audio entity when an audio url is given).
// Static + client-only: it generates files to copy/commit; nothing is uploaded.

import { FORMS, SLUG_RE, buildFiles, type FormDef } from "../lib/content-forms";

const typeSel = document.getElementById("ctype") as HTMLSelectElement | null;
const fieldsEl = document.getElementById("cfields");
const previewEl = document.getElementById("cpreview");
if (typeSel && fieldsEl && previewEl) {
  const today = new Date().toISOString().slice(0, 10);

  FORMS.forEach((f) => {
    const o = document.createElement("option");
    o.value = f.collection;
    o.textContent = f.label;
    typeSel.appendChild(o);
  });

  const currentDef = (): FormDef | undefined => FORMS.find((f) => f.collection === typeSel.value);

  function renderFields() {
    fieldsEl!.textContent = "";
    const def = currentDef();
    if (!def) return;
    for (const f of def.fields) {
      const wrap = document.createElement("div");
      wrap.className = "field";

      const label = document.createElement("label");
      label.textContent = f.label + (f.required ? " *" : "");
      label.htmlFor = "f-" + f.key;
      wrap.appendChild(label);

      let input: HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement;
      if (f.kind === "select") {
        const sel = document.createElement("select");
        (f.options || []).forEach((opt) => {
          const o = document.createElement("option");
          o.value = opt;
          o.textContent = opt === "" ? "—" : opt;
          sel.appendChild(o);
        });
        input = sel;
      } else if (f.kind === "body" || f.kind === "verses" || f.kind === "array" || f.kind === "textarea") {
        const ta = document.createElement("textarea");
        ta.rows = f.kind === "body" || f.kind === "verses" ? 8 : 3;
        input = ta;
      } else {
        const inp = document.createElement("input");
        inp.type = f.kind === "date" ? "date" : f.kind === "number" ? "number" : f.kind === "url" ? "url" : "text";
        input = inp;
      }
      input.id = "f-" + f.key;
      input.dataset.key = f.key;
      input.className = "cinput";
      if (f.default) input.value = f.default;
      if (f.kind === "date" && f.required) input.value = today;
      wrap.appendChild(input);

      if (f.help) {
        const h = document.createElement("div");
        h.className = "faint field-help";
        h.textContent = f.help;
        wrap.appendChild(h);
      }
      fieldsEl!.appendChild(wrap);
    }
    update();
  }

  function collectValues(): Record<string, string> {
    const v: Record<string, string> = {};
    fieldsEl!.querySelectorAll<HTMLElement>("[data-key]").forEach((el) => {
      v[el.dataset.key!] = (el as HTMLInputElement).value;
    });
    return v;
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
    const values = collectValues();
    const errs = validate(def, values);
    const files = buildFiles(def, values);
    previewEl!.textContent = "";

    if (errs.length) {
      const e = document.createElement("div");
      e.className = "compose-errors";
      e.textContent = "أكمِلْ: " + errs.join(" · ");
      previewEl!.appendChild(e);
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

      head.append(copyBtn, dlBtn);
      box.appendChild(head);

      const pre = document.createElement("pre");
      pre.className = "compose-pre";
      pre.textContent = file.content;
      box.appendChild(pre);

      previewEl!.appendChild(box);
    });
  }

  typeSel.addEventListener("change", renderFields);
  fieldsEl.addEventListener("input", update);
  renderFields();
}
