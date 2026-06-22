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
  | "ref"  // single reference to an existing entity — searchable picker, stores its id
  | "refs" // many references — searchable checklist, stores ids
  | "body";

export interface Field {
  key: string;
  label: string;
  kind: FieldKind;
  required?: boolean;
  options?: readonly string[];
  optionLabels?: Record<string, string>; // Arabic display for english enum values
  default?: string;
  help?: string;
  ref?: string;       // for ref/refs: pipe-joined collection(s) to pick from, e.g. "book|poem"
  syncType?: string;  // for ref: sibling select key to auto-set to the picked entity's collection
}

export interface FormDef {
  collection: string;
  label: string; // Arabic type name
  fields: Field[];
}

// Arabic labels for the english enum values, so non-technical maintainers read معاني not codes.
const STATUS_LABELS = { draft: "مسودة", review: "قيد المراجعة", published: "منشور", archived: "مؤرشف" };
const COLL_LABELS: Record<string, string> = { book: "كتاب", poem: "منظومة", lesson: "درس", article: "مقالة" };

const STATUS = ["draft", "review", "published", "archived"] as const;

// shared frontmatter at the top of every type
const shared = (): Field[] => [
  { key: "slug", label: "اسم الملف", kind: "slug", required: true, help: "بالإنجليزية: حروف صغيرة وأرقام وشَرَطات. مثال: al-bayquniyyah" },
  { key: "title", label: "العنوان", kind: "text", required: true, help: "كما يظهر للقارئ" },
  { key: "status", label: "الحالة", kind: "select", options: STATUS, optionLabels: STATUS_LABELS, default: "draft", help: "ابدأ بمسودة، واجعلها «منشور» عند الجاهزية" },
  { key: "published_at", label: "تاريخ النشر", kind: "date", required: true },
];

// single searchable reference to an existing entity (stores its id)
const REF = (key: string, label: string, ref: string, o: { required?: boolean; help?: string; syncType?: string } = {}): Field =>
  ({ key, label, kind: "ref", ref, required: o.required ?? true, help: o.help ?? "ابحث بالاسم واختَر من القائمة", syncType: o.syncType });
// many searchable references (stores ids)
const REFS = (key: string, label: string, ref: string, o: { required?: boolean; help?: string } = {}): Field =>
  ({ key, label, kind: "refs", ref, required: o.required, help: o.help ?? "اكتب للبحث، وأشِّر على ما يناسب" });

export const FORMS: FormDef[] = [
  {
    collection: "poem",
    label: "منظومة",
    fields: [
      ...shared(),
      REF("person", "الناظم", "person"),
      REFS("topics", "الموضوعات", "topic", { help: "موضوع إلى خمسة" }),
      { key: "authored_year", label: "سنة النظم (هجري)", kind: "number", help: "رقمٌ بالهجري — تُرتَّب به القوائم. مثال: 672" },
      { key: "description", label: "تعريف المنظومة", kind: "text" },
      { key: "audio_url", label: "رابط التلاوة (اختياري)", kind: "url", help: "يولِّد صوتيةً مرتبطةً تلقائيًّا" },
      { key: "verses", label: "الأبيات", kind: "verses", required: true, help: "بيتٌ لكلِّ سطر: الصدر --- العجز" },
    ],
  },
  {
    collection: "book",
    label: "كتاب",
    fields: [
      ...shared(),
      REF("person", "المؤلف", "person"),
      { key: "kind", label: "النوع", kind: "select", options: ["متن", "مرجع", "مجموع"], default: "متن", help: "متن = أصلٌ يُحفظ ويُدرَّس" },
      REFS("topics", "الموضوعات", "topic"),
      { key: "authored_year", label: "سنة التصنيف (هجري)", kind: "number", help: "رقمٌ بالهجري — تُرتَّب به القوائم. مثال: 728" },
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
      { key: "bio", label: "نبذة", kind: "text", help: "سطرٌ تعريفيٌّ موجز" },
      { key: "born", label: "المولد", kind: "text", help: "نصٌّ حر: ٦٦١ هـ" },
      { key: "died", label: "الوفاة", kind: "text" },
      { key: "location", label: "البلد", kind: "text" },
      { key: "era", label: "العصر", kind: "select", options: ["", ...ERA_VALUES] },
      { key: "also_known_as", label: "أسماء أخرى للبحث", kind: "array", help: "كُنى ومرادفات يبحث بها الناس — سطرٌ لكلٍّ" },
      { key: "body", label: "الترجمة", kind: "body" },
    ],
  },
  {
    collection: "annotation",
    label: "شرح / حاشية",
    fields: [
      ...shared(),
      REF("target_id", "على أيِّ متنٍ أو منظومة؟", "book|poem", { syncType: "target_type", help: "ابحث عن المتن/المنظومة التي تشرحها واختَرها" }),
      { key: "target_type", label: "نوع الهدف", kind: "select", options: ["book", "poem"], optionLabels: COLL_LABELS, required: true, help: "يُملأ تلقائيًّا عند اختيار المتن" },
      { key: "phrase", label: "الجملة التي يشرحها", kind: "text", help: "انسخِ الكلمات من المتن كما هي — تُعلَّم ويُفتح عليها الشرح (اتركها فارغة لتعليم البيت كلّه)" },
      { key: "anchor", label: "الموضع", kind: "text", required: true, help: "أيُّ بيتٍ أو فقرة؟ v1 لأوّل بيت، p1 لأوّل فقرة" },
      { key: "kind", label: "نوع الشرح", kind: "select", options: ["شرح", "حاشية", "تخريج", "إعراب"], default: "شرح" },
      REF("annotator", "الشارح", "person", { required: false, help: "اختياري — من قائلُ هذا الشرح؟" }),
      { key: "source_type", label: "نوع مصدر الشرح", kind: "select", options: ["", "lesson", "book", "poem", "article"], optionLabels: COLL_LABELS, help: "اختياري — إن كان الشرح مأخوذًا من درسٍ أو كتاب" },
      REF("source_id", "مصدر الشرح", "lesson|book|poem|article", { required: false, syncType: "source_type", help: "اختياري — الصفحة التي فيها الشرح كاملًا" }),
      { key: "body", label: "نصّ الشرح", kind: "body", required: true, help: "الصقِ الشرح هنا" },
    ],
  },
  {
    collection: "lesson",
    label: "درس",
    fields: [
      ...shared(),
      REF("series", "السلسلة", "series"),
      { key: "order", label: "الترتيب", kind: "number", required: true, default: "1", help: "رقم الدرس في السلسلة" },
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
      REF("person", "الشارح/المدرّس", "person"),
      REFS("topics", "الموضوعات", "topic"),
      { key: "source_type", label: "نوع المصدر", kind: "select", options: ["", "book", "poem"], optionLabels: COLL_LABELS, help: "اختياري — الكتاب/المنظومة التي تشرحها السلسلة" },
      REF("source_id", "المصدر", "book|poem", { required: false, syncType: "source_type" }),
      { key: "description", label: "تعريف السلسلة", kind: "text" },
      { key: "body", label: "المحتوى", kind: "body" },
    ],
  },
  {
    collection: "benefit",
    label: "فائدة",
    fields: [
      ...shared(),
      REF("person", "صاحب الفائدة", "person"),
      REFS("topics", "الموضوعات", "topic"),
      { key: "source_type", label: "نوع المصدر", kind: "select", options: ["", "lesson", "book", "article", "poem"], optionLabels: COLL_LABELS, help: "اختياري — من أين هذه الفائدة؟" },
      REF("source_id", "المصدر", "lesson|book|article|poem", { required: false, syncType: "source_type" }),
      { key: "body", label: "نص الفائدة", kind: "body", required: true },
    ],
  },
  {
    collection: "article",
    label: "مقالة",
    fields: [
      ...shared(),
      REF("person", "الكاتب", "person"),
      REFS("topics", "الموضوعات", "topic"),
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
      REFS("topics", "الموضوعات", "topic", { required: true, help: "إلزامي — موضوع إلى خمسة" }),
      REF("person", "المُجيب", "person", { required: false }),
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
      REF("subject", "التصنيف الأب", "subject", { help: "التصنيف العام الذي يندرج تحته هذا الموضوع" }),
      { key: "description", label: "وصف الموضوع", kind: "text" },
      { key: "body", label: "المحتوى", kind: "body" },
    ],
  },
  {
    collection: "audio",
    label: "صوتية",
    fields: [
      ...shared(),
      { key: "source_type", label: "نوع المصدر", kind: "select", options: ["lesson", "book", "poem", "article"], optionLabels: COLL_LABELS, required: true },
      REF("source_id", "المصدر", "lesson|book|poem|article", { syncType: "source_type" }),
      { key: "url", label: "الرابط", kind: "url", required: true },
      { key: "format", label: "الصيغة", kind: "select", options: ["opus", "mp3"], default: "opus" },
      { key: "duration", label: "المدة", kind: "text" },
    ],
  },
  {
    collection: "highlight",
    label: "مختار الأسبوع",
    fields: [
      ...shared(),
      { key: "kind", label: "النوع", kind: "select", options: ["آية", "حديث", "بيت"], default: "حديث", help: "آية قرآنية، أو حديث، أو بيت من منظومة" },
      { key: "reference", label: "المصدر / التخريج", kind: "text", help: "مثال: «البقرة ٢٥٥» للآية، «رواه البخاري» للحديث، اسم الناظم للبيت" },
      { key: "source_type", label: "نوع المصدر", kind: "select", options: ["", "poem", "book", "article"], optionLabels: COLL_LABELS, help: "اختياري — لربط «بيت» بمنظومته فيُفتح المصدرُ ويظهر العصرُ والفنّ" },
      REF("source_id", "المصدر", "poem|book|article", { syncType: "source_type" }),
      { key: "body", label: "النص", kind: "body", required: true, help: "الصقِ نصّ الآية أو الحديث أو البيت" },
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

export { SLUG_RE } from "./slug";

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

    if (f.kind === "array" || f.kind === "refs") {
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
