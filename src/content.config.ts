import { defineCollection, z } from "astro:content";
import { glob } from "astro/loaders";

// --- shared primitives ---

// Single hyphens for word segments; double hyphens separate parent from child
// e.g., "sharh-al-wasitiyyah--lesson-1", "alfiyyah-ibn-malik--v1--sharh"
const slugPattern = /^[a-z0-9]+(--?[a-z0-9]+)*$/;
const slug = z.string().regex(slugPattern, "invalid slug format — use lowercase letters, digits, hyphens (-- for child separators)");

const shared = {
  title: z.string().min(1),
  status: z.enum(["draft", "review", "published", "archived"]),
  published_at: z.coerce.date(),
  updated_at: z.coerce.date().optional(),
  aliases: z.array(slug).optional(),
};

const topicsField = z.array(slug).min(1).max(5).optional();

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
  loader: glob({ pattern: "**/*.md", base: "./src/content/book" }),
  schema: z.object({
    ...shared,
    person: slug,           // → Person (author)
    topics: topicsField,
    description: z.string().optional(),
    edition: z.string().optional(),
  }),
});

// --- 05. Poem (المنظومة) ---

const poem = defineCollection({
  loader: glob({ pattern: "**/*.md", base: "./src/content/poem" }),
  schema: z.object({
    ...shared,
    person: slug,           // → Person (author)
    topics: topicsField,
    verse_count: z.number().int().positive().optional(), // set in frontmatter; also derived in P2
    opening_verse: z.string().optional(),
    description: z.string().optional(),
  }),
});

// --- 06. Series (السلسلة) — polymorphic source (book | poem), optional ---

const series = defineCollection({
  loader: glob({ pattern: "**/*.md", base: "./src/content/series" }),
  schema: z
    .object({
      ...shared,
      person: slug,         // → Person (teacher)
      topics: topicsField,
      source_type: z.enum(["book", "poem"]).optional(),
      source_id: slug.optional(),
      description: z.string().optional(),
    })
    .refine(
      (s) => (s.source_type == null) === (s.source_id == null),
      "source_type and source_id must both be set or both omitted",
    ),
});

// --- 07. Lesson (الدرس) — body IS the transcript ---

const lesson = defineCollection({
  loader: glob({ pattern: "**/*.md", base: "./src/content/lesson" }),
  schema: z.object({
    ...shared,
    series: slug,           // → Series (required)
    order: z.number().int().positive(),
    audio: slug.optional(), // → Audio entity
    duration: z.string().optional(), // "1:23:45"
  }),
});

// --- 08. Questions (المسائل) — topics only, no tags/subjects ---

const question = defineCollection({
  loader: glob({ pattern: "**/*.md", base: "./src/content/question" }),
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
      source_type: z.enum(["lesson", "book", "article", "poem"]).optional(),
      source_id: slug.optional(),
    })
    .refine(
      (b) => (b.source_type == null) === (b.source_id == null),
      "source_type and source_id must both be set or both omitted",
    ),
});

// --- 10. Article (المقالة) ---

const article = defineCollection({
  loader: glob({ pattern: "**/*.md", base: "./src/content/article" }),
  schema: z.object({
    ...shared,
    person: slug,           // → Person (author)
    topics: topicsField,
    audio: slug.optional(), // → Audio (optional)
    description: z.string().optional(),
  }),
});

// --- 11. Audio (الصوتية) — always embedded, no standalone page ---

const audio = defineCollection({
  loader: glob({ pattern: "**/*.md", base: "./src/content/audio" }),
  schema: z.object({
    ...shared,
    source_type: z.enum(["lesson", "book", "poem", "article"]), // required
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
  schema: z.object({
    ...shared,
    target_type: z.enum(["book", "poem"]),  // required
    target_id: slug,                         // required
    anchor: z.string().min(1),               // e.g., "v5", "p3", heading slug
    annotator: slug.optional(),              // → Person
  }),
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

// --- export ---

export const collections = {
  person,
  subject,
  topic,
  book,
  poem,
  series,
  lesson,
  question,
  benefit,
  article,
  audio,
  annotation,
  announcement,
};
