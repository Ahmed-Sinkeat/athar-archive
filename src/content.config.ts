import { defineCollection, z } from "astro:content";
import { glob } from "astro/loaders";
import { fmLoader } from "./lib/fm-loader.js";
import { SLUG_RE } from "./lib/slug.js";

// --- shared primitives ---

const slug = z.string().regex(SLUG_RE, "invalid slug format — use lowercase letters, digits, hyphens (-- for child separators)");

const shared = {
  title: z.string().min(1),
  status: z.enum(["draft", "review", "published", "archived"]),
  published_at: z.coerce.date(),
  updated_at: z.coerce.date().optional(),
  aliases: z.array(slug).optional(),
};

const topicsField = z.array(slug).min(1).max(5).optional();

// Downloadable attachments (print editions, PDFs, etc.) — served from R2 (P6).
const attachment = z.object({
  label: z.string().min(1),
  url: z.string().url(),
  format: z.enum(["pdf", "epub", "docx", "zip"]).optional(),
  size_bytes: z.number().int().positive().optional(),
});
const attachmentsField = z.array(attachment).optional();

// --- 01. Person (الشخص) ---

const person = defineCollection({
  loader: glob({ pattern: "**/*.md", base: "./src/content/person" }),
  schema: z.object({
    ...shared,
    bio: z.string().optional(),
    image: z.string().optional(),
    born: z.string().optional(),   // free-form (e.g., "661 هـ")
    died: z.string().optional(),
    location: z.string().optional(),
    // العصر الأدبي (للشعراء) — keep in sync with ERA_VALUES in src/lib/display.ts
    era: z.enum(["الجاهلي", "صدر الإسلام", "الأموي", "العباسي", "الأندلسي", "المتأخّر", "الحديث"]).optional(),
    // الطبقة العلمية (للعلماء) — keep in sync with TABAQA_VALUES in src/lib/display.ts
    tabaqa: z.enum(["الصحابة", "التابعون", "أتباع التابعين", "المتقدمون", "المتوسطون", "المتأخرون", "المعاصرون"]).optional(),
    // Arabic synonyms / كُنى surfaced to search (e.g. شيخ الإسلام، أحمد بن عبد الحليم).
    // Note: this is distinct from `aliases` (latin slug → 301 redirects).
    also_known_as: z.array(z.string()).optional(),
    // العقيدة — used in شبكة المعرفة scholar comparison dashboard.
    aqeedah: z.string().optional(),
    // جرح وتعديل verdict (e.g. "ثقة", "صدوق", "ضعيف")
    rutba: z.string().optional(),
    // curated شيوخ — slugs of persons this narrator narrates from; تلاميذ derived in-graph
    narrates_from: z.array(slug).optional(),
  }),
});

// --- 02. Subject (التصنيف) ---

const subject = defineCollection({
  loader: glob({ pattern: "**/*.md", base: "./src/content/subject" }),
  schema: z.object({
    ...shared,
    description: z.string().optional(),
  }),
});

// --- 03. Topic (الموضوع) — exactly one Subject parent, no Topic-in-Topic ---

const topic = defineCollection({
  loader: glob({ pattern: "**/*.md", base: "./src/content/topic" }),
  schema: z.object({
    ...shared,
    subject: slug,          // → Subject (required)
    description: z.string().optional(),
  }),
});

// --- 04. Book (الكتاب) ---

const book = defineCollection({
  loader: fmLoader("./src/content/book"),
  schema: z.object({
    ...shared,
    person: slug,           // → Person (author)
    // study classification — drives the متن badge + study modes (poems are always متن)
    kind: z.enum(["متن", "مرجع", "مجموع"]).optional(),
    // section beyond kind — routes to /quran /hadith /tarajim (still also under /books)
    genre: z.enum(["قرآن", "حديث", "تراجم"]).optional(),
    // a شرح/تعليق of another book (فتح المجيد → كتاب التوحيد) — drives the parent's
    // "شروحه وتعليقاته" list. The lesson/series split was retired: a درس is a book
    // with audio; a سلسلة شرح is a book with sharh_of.
    sharh_of: slug.optional(),
    // دروس مفرّغة (audio transcribed to text) — review state shown as a pill.
    transcript_status: z.enum(["مراجَع", "قيد المراجعة"]).optional(),
    // تصنيف كتب الحديث — drives facets on /hadith
    hadith_category: z.enum(["امهات الكتب", "كتب الآثار", "أجزاء حديثية", "تخريج", "علل", "عام"]).optional(),
    topics: topicsField,
    authored_year: z.number().int().optional(), // hijri سنة التصنيف — default browse sort
    description: z.string().optional(),
    edition: z.string().optional(),
    cover: z.string().optional(),        // cover image (R2)
    attachments: attachmentsField,       // print editions / PDFs (R2)
  }),
});

// --- 05. Poem (المنظومة) ---

const poem = defineCollection({
  loader: fmLoader("./src/content/poem"),
  schema: z.object({
    ...shared,
    person: slug,           // → Person (author)
    topics: topicsField,
    authored_year: z.number().int().optional(), // hijri سنة النظم — default browse sort
    // verse_count / opening_verse are DERIVED from the body (FR-C-06), never
    // hand-stored — see analyzePoem() in src/lib/chunk.ts.
    description: z.string().optional(),
    attachments: attachmentsField,       // print editions / PDFs (R2)
  }),
});

// 06–07. Series + Lesson retired — a درس is a book with audio; a سلسلة شرح is a book
// with `sharh_of`; a محاضرة is an article. One reading entity (book).

// --- 08. Questions (المسائل) — topics only, no tags/subjects ---

const question = defineCollection({
  loader: fmLoader("./src/content/question"),
  schema: z.object({
    ...shared,
    person: slug.optional(),            // → Person (optional)
    topics: z.array(slug).min(1).max(5), // required for Questions
  }),
});

// --- 09. Benefit (الفائدة) — polymorphic source, optional ---

const benefit = defineCollection({
  loader: glob({ pattern: "**/*.md", base: "./src/content/benefit" }),
  schema: z
    .object({
      ...shared,
      person: slug,         // → Person (required)
      topics: topicsField,
      source_type: z.enum(["book", "article", "poem"]).optional(),
      source_id: slug.optional(),
    })
    .refine(
      (b) => (b.source_type == null) === (b.source_id == null),
      "source_type and source_id must both be set or both omitted",
    ),
});

// --- 10. Article (المقالة) ---

const article = defineCollection({
  loader: fmLoader("./src/content/article"),
  schema: z.object({
    ...shared,
    person: slug,           // → Person (author)
    topics: topicsField,
    audio: slug.optional(), // → Audio (optional)
    description: z.string().optional(),
    attachments: attachmentsField,       // PDFs / handouts (R2)
  }),
});

// --- 11. Audio (الصوتية) — always embedded, no standalone page ---

const audio = defineCollection({
  loader: glob({ pattern: "**/*.md", base: "./src/content/audio" }),
  schema: z.object({
    ...shared,
    source_type: z.enum(["book", "poem", "article"]), // required
    source_id: slug,                                             // required
    url: z.string().url(),
    format: z.enum(["opus", "mp3"]).default("opus"),
    duration: z.string().optional(),
    size_bytes: z.number().int().positive().optional(),
  }),
});

// --- 12. Annotation (الشرح/الحاشية) — always embedded ---

const annotation = defineCollection({
  loader: glob({ pattern: "**/*.md", base: "./src/content/annotation" }),
  schema: z
    .object({
      ...shared,
      target_type: z.enum(["book", "poem", "quran"]),  // required
      target_id: slug,                         // required
      anchor: z.string().min(1),               // e.g., "v5", "p3", heading slug
      // kind drives the note label/accent in the reader (شرح/حاشية/تخريج/إعراب)
      kind: z.enum(["شرح", "حاشية", "تخريج", "إعراب", "تفسير"]).default("شرح"),
      grade: z.enum(["صحيح", "حسن", "ضعيف", "موضوع"]).optional(),
      annotator: slug.optional(),              // → Person
      // Exact phrase within the anchor's text to mark inline (the clickable
      // word(s) that open the شرح chooser + get highlighted). If omitted, the
      // whole line carries the mark.
      phrase: z.string().min(1).optional(),
      // Optional cross-reference: this شرح lives more fully on another page.
      source_type: z.enum(["book", "poem", "article"]).optional(),
      source_id: slug.optional(),
    })
    .refine(
      (a) => (a.source_type == null) === (a.source_id == null),
      "source_type and source_id must both be set or both omitted",
    ),
});

// --- 13. Announcement (الإعلان) — homepage chrome only ---

const announcement = defineCollection({
  loader: glob({ pattern: "**/*.md", base: "./src/content/announcement" }),
  schema: z.object({
    ...shared,
    body: z.string().optional(),
    priority: z.number().int().min(1).max(10).default(5),
    expires_at: z.coerce.date().optional(),
  }),
});

// --- 14. Highlight (مختارات الأسبوع) — homepage chrome only, no page ---
// One curated آية / حديث / بيت; the home rotates a weekly pick of each kind.
// The text itself is the markdown body; `reference` is المصدر/التخريج.

const highlight = defineCollection({
  loader: glob({ pattern: "**/*.md", base: "./src/content/highlight" }),
  schema: z.object({
    ...shared,
    kind: z.enum(["آية", "حديث", "بيت"]),
    reference: z.string().optional(), // المصدر/التخريج: «البقرة ٢٥٥»، «رواه البخاري»، «الناظم»
    // Optional link to where this came from (e.g. a بيت → its منظومة) — makes the
    // reference clickable and lets a بيت show its era + subject instead of a «بيت» pill.
    source_type: z.enum(["poem", "book", "article"]).optional(),
    source_id: slug.optional(),
  }).refine(
    (h) => (h.source_type == null) === (h.source_id == null),
    "source_type and source_id must both be set or both omitted",
  ),
});

// --- 15. Term (المعجم) ---

const term = defineCollection({
  loader: glob({ pattern: "**/*.md", base: "./src/content/term" }),
  schema: z.object({
    ...shared,
    definition: z.string().optional(), // سطرٌ تعريفيٌّ موجز
    topics: topicsField,
    also_known_as: z.array(z.string()).optional(),
  }),
});

// --- 16. Quran (المصحف) — 114 surah entries parsed from mushaf epub ---

const quran = defineCollection({
  loader: glob({ pattern: "**/*.md", base: "./src/content/quran" }),
  schema: z.object({
    ...shared,
    number: z.number().int().min(1).max(114),
    name: z.string().min(1),       // Arabic name without سورة prefix
    start_page: z.number().int().min(1).max(604),
    ayah_count: z.number().int().positive(),
  }),
});

// --- export ---

export const collections = {
  person,
  subject,
  topic,
  book,
  poem,
  question,
  benefit,
  article,
  audio,
  annotation,
  announcement,
  highlight,
  term,
  quran,
};
