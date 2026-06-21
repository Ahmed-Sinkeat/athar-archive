// Field specs for the on-site composer (/compose). Mirrors the Zod schema in
// content.config.ts and the scaffold in scripts/new-content.mjs — keep in sync.
// Used client-side to render forms and generate valid frontmatter + body.

import { ERA_VALUES } from "./display";

export type FieldKind =
  | "slug" // becomes the filename, not a frontmatter key
  | "text"
  | "textarea"
  | "date"
  | "number"
  | "select"
  | "array"
  | "verses"
  | "url"
  | "body";

export interface Field {
  key: string;
  label: string;
  kind: FieldKind;
  required?: boolean;
  options?: readonly string[];
  default?: string;
  help?: string;
}

export interface FormDef {
  collection: string;
  label: string; // Arabic type name
  fields: Field[];
}

const STATUS = ["draft", "review", "published", "archived"] as const;

// shared frontmatter at the top of every type
const shared = (): Field[] => [
  { key: "slug", label: "المعرّف (اسم الملف)", kind: "slug", required: true, help: "حروف لاتينية صغيرة وأرقام وشرطات: al-bayquniyyah" },
  { key: "title", label: "العنوان", kind: "text", required: true },
  { key: "status", label: "الحالة", kind: "select", options: STATUS, default: "draft" },
  { key: "published_at", label: "تاريخ النشر", kind: "date", required: true },
];

const PERSON_REF = (key: string, label: string, required = true): Field => ({ key, label, kind: "slug", required, help: "معرّف عَلَمٍ موجود" });

export const FORMS: FormDef[] = [
  {
    collection: "poem",
    label: "منظومة",
    fields: [
      ...shared(),
      PERSON_REF("person", "الناظم"),
      { key: "topics", label: "الموضوعات", kind: "array", help: "معرّفات، ١–٥، سطرٌ لكلٍّ" },
      { key: "description", label: "تعريف المنظومة", kind: "text" },
      { key: "audio_url", label: "رابط التلاوة (اختياري)", kind: "url", help: "يولِّد صوتيةً مرتبطةً" },
      { key: "verses", label: "الأبيات", kind: "verses", required: true, help: "بيتٌ لكلِّ سطر: الصدر --- العجز" },
    ],
  },
  {
    collection: "book",
    label: "كتاب",
    fields: [
      ...shared(),
      PERSON_REF("person", "المؤلف"),
      { key: "kind", label: "النوع", kind: "select", options: ["متن", "مرجع", "مجموع"], default: "متن" },
      { key: "topics", label: "الموضوعات", kind: "array" },
      { key: "description", label: "تعريف الكتاب", kind: "text" },
      { key: "edition", label: "الطبعة", kind: "text" },
      { key: "audio_url", label: "رابط الصوتية (اختياري)", kind: "url" },
      { key: "body", label: "النص", kind: "body", required: true, help: "الأبواب بعناوين ## ، والفقرات نصٌّ عادي" },
    ],
  },
  {
    collection: "person",
    label: "عَلَم",
    fields: [
      ...shared(),
      { key: "bio", label: "نبذة", kind: "text" },
      { key: "born", label: "المولد", kind: "text", help: "نصٌّ حر: ٦٦١ هـ" },
      { key: "died", label: "الوفاة", kind: "text" },
      { key: "location", label: "البلد", kind: "text" },
      { key: "era", label: "العصر", kind: "select", options: ["", ...ERA_VALUES] },
      { key: "also_known_as", label: "أسماء أخرى للبحث", kind: "array", help: "كُنى ومرادفات، سطرٌ لكلٍّ" },
      { key: "body", label: "الترجمة", kind: "body" },
    ],
  },
  {
    collection: "annotation",
    label: "شرح / حاشية",
    fields: [
      ...shared(),
      { key: "target_type", label: "نوع الهدف", kind: "select", options: ["book", "poem"], required: true },
      { key: "target_id", label: "معرّف المتن/المنظومة", kind: "slug", required: true },
      { key: "anchor", label: "الموضع", kind: "text", required: true, help: "v1 لبيت، أو p3 / {#id} لفقرة" },
      { key: "kind", label: "النوع", kind: "select", options: ["شرح", "حاشية", "تخريج", "إعراب"], default: "شرح" },
      PERSON_REF("annotator", "الشارح", false),
      { key: "body", label: "نص الشرح", kind: "body", required: true },
    ],
  },
  {
    collection: "lesson",
    label: "درس",
    fields: [
      ...shared(),
      { key: "series", label: "السلسلة", kind: "slug", required: true, help: "معرّف سلسلةٍ موجودة" },
      { key: "order", label: "الترتيب", kind: "number", required: true, default: "1" },
      { key: "duration", label: "المدة", kind: "text", help: "1:02:30" },
      { key: "audio_url", label: "رابط الصوتية (اختياري)", kind: "url" },
      { key: "body", label: "نص التفريغ", kind: "body", required: true },
    ],
  },
  {
    collection: "series",
    label: "سلسلة",
    fields: [
      ...shared(),
      PERSON_REF("person", "الشارح/المدرّس"),
      { key: "topics", label: "الموضوعات", kind: "array" },
      { key: "source_type", label: "نوع المصدر", kind: "select", options: ["", "book", "poem"] },
      { key: "source_id", label: "معرّف المصدر", kind: "slug", required: false },
      { key: "description", label: "تعريف السلسلة", kind: "text" },
      { key: "body", label: "المحتوى", kind: "body" },
    ],
  },
  {
    collection: "benefit",
    label: "فائدة",
    fields: [
      ...shared(),
      PERSON_REF("person", "صاحب الفائدة"),
      { key: "topics", label: "الموضوعات", kind: "array" },
      { key: "source_type", label: "نوع المصدر", kind: "select", options: ["", "lesson", "book", "article", "poem"] },
      { key: "source_id", label: "معرّف المصدر", kind: "slug", required: false },
      { key: "body", label: "نص الفائدة", kind: "body", required: true },
    ],
  },
  {
    collection: "article",
    label: "مقالة",
    fields: [
      ...shared(),
      PERSON_REF("person", "الكاتب"),
      { key: "topics", label: "الموضوعات", kind: "array" },
      { key: "description", label: "نبذة", kind: "text" },
      { key: "audio_url", label: "رابط الصوتية (اختياري)", kind: "url" },
      { key: "body", label: "النص", kind: "body", required: true },
    ],
  },
  {
    collection: "question",
    label: "مسألة",
    fields: [
      ...shared(),
      { key: "topics", label: "الموضوعات", kind: "array", required: true, help: "إلزامي، ١–٥" },
      PERSON_REF("person", "المُجيب", false),
      { key: "body", label: "نص الجواب", kind: "body", required: true },
    ],
  },
  {
    collection: "subject",
    label: "تصنيف",
    fields: [...shared(), { key: "description", label: "وصف التصنيف", kind: "text" }, { key: "body", label: "المحتوى", kind: "body" }],
  },
  {
    collection: "topic",
    label: "موضوع",
    fields: [
      ...shared(),
      { key: "subject", label: "التصنيف الأب", kind: "slug", required: true },
      { key: "description", label: "وصف الموضوع", kind: "text" },
      { key: "body", label: "المحتوى", kind: "body" },
    ],
  },
  {
    collection: "audio",
    label: "صوتية",
    fields: [
      ...shared(),
      { key: "source_type", label: "نوع المصدر", kind: "select", options: ["lesson", "book", "poem", "article"], required: true },
      { key: "source_id", label: "معرّف المصدر", kind: "slug", required: true },
      { key: "url", label: "الرابط", kind: "url", required: true },
      { key: "format", label: "الصيغة", kind: "select", options: ["opus", "mp3"], default: "opus" },
      { key: "duration", label: "المدة", kind: "text" },
    ],
  },
  {
    collection: "announcement",
    label: "إعلان",
    fields: [
      ...shared(),
      { key: "priority", label: "الأولوية", kind: "number", default: "5", help: "١–١٠" },
      { key: "expires_at", label: "تاريخ الانتهاء", kind: "date", required: false },
      { key: "body", label: "نص الإعلان", kind: "body" },
    ],
  },
];

export const SLUG_RE = /^[a-z0-9]+(--?[a-z0-9]+)*$/;

// YAML-safe scalar: quote when it could be misread (colon, hash, leading marker).
function yamlValue(v: string): string {
  if (v === "") return '""';
  if (/[:#]/.test(v) || /^[\s'"[\]{}>|@`&*!%?-]/.test(v)) return JSON.stringify(v);
  return v;
}

export interface BuiltFile {
  path: string;
  content: string;
}

export function buildFiles(def: FormDef, values: Record<string, string>): BuiltFile[] {
  const slug = (values.slug || "").trim();
  const lines = ["---"];
  let bodyField: Field | undefined;

  for (const f of def.fields) {
    if (f.key === "slug") continue; // the filename field, not a frontmatter key
    if (f.kind === "body" || f.kind === "verses") { bodyField = f; continue; }
    if (f.key === "audio_url") continue; // pseudo-field → companion file
    const raw = (values[f.key] ?? "").trim();
    if (!raw && !f.required) continue;

    if (f.kind === "array") {
      const items = raw.split("\n").flatMap((s) => s.split(",")).map((s) => s.trim()).filter(Boolean);
      if (!items.length) continue;
      lines.push(`${f.key}:`);
      items.forEach((it) => lines.push(`  - ${yamlValue(it)}`));
    } else if (f.kind === "number") {
      lines.push(`${f.key}: ${raw || f.default || "0"}`);
    } else {
      lines.push(`${f.key}: ${yamlValue(raw || f.default || "")}`);
    }
  }

  // companion audio ref on types that carry one
  const audioUrl = (values.audio_url || "").trim();
  const companionSlug = `${slug}--audio`;
  if (audioUrl && (def.collection === "lesson" || def.collection === "article")) {
    lines.push(`audio: ${companionSlug}`);
  }

  lines.push("---", "");
  const body = (values[bodyField?.key ?? ""] ?? "").trim();
  const content = lines.join("\n") + "\n" + (body ? body + "\n" : "");
  const files: BuiltFile[] = [{ path: `src/content/${def.collection}/${slug || "SLUG"}.md`, content }];

  // generate the companion audio entity
  if (audioUrl && ["lesson", "book", "poem", "article"].includes(def.collection)) {
    const a = [
      "---",
      `title: ${yamlValue(`صوتية: ${(values.title || "").trim()}`)}`,
      `status: ${values.status || "draft"}`,
      `published_at: ${values.published_at || ""}`,
      `source_type: ${def.collection}`,
      `source_id: ${slug || "SLUG"}`,
      `url: ${yamlValue(audioUrl)}`,
      "format: opus",
      "---",
      "",
    ].join("\n") + "\n";
    files.push({ path: `src/content/audio/${companionSlug}.md`, content: a });
  }

  return files;
}
