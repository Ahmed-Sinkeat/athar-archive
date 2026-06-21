#!/usr/bin/env node
// أهل الأثر — مولّد ملفات المحتوى (السقالة، P7)
// Scaffold a new content entity with a valid-on-fill frontmatter stub.
//
//   node scripts/new-content.mjs <entity> <slug> [title words…]
//   pnpm new <entity> <slug> [title…]
//
// The stub uses `status: draft` and self-describing placeholders for required
// refs (e.g. `person-id-here`). These keep the file Zod-valid but make
// `pnpm validate:content` report exactly which ref to fill. Replace them, then:
//   pnpm validate:content && pnpm build

import { writeFileSync, existsSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const CONTENT = join(ROOT, "src", "content");

// id = filename — same pattern enforced by Zod (config) and validate.ts
const SLUG_RE = /^[a-z0-9]+(--?[a-z0-9]+)*$/;
const TODAY = new Date().toISOString().slice(0, 10);

const fm = (lines) => `---\n${lines.join("\n")}\n---\n`;
const shared = (title) => [
  `title: ${title}`,
  `status: draft            # draft | review | published | archived`,
  `published_at: ${TODAY}`,
  `# updated_at: ${TODAY}   # optional`,
  `# aliases: []            # optional — old ids → 301 redirects`,
];
const PROSE = "\nاكتب المحتوى بالعربية هنا…\n";

// One template per collection. Required refs use `<field>-id-here` placeholders
// (slug-valid, so the only validation error is a clear ref-resolution one).
const TEMPLATES = {
  person: (t) => fm([...shared(t),
    `# bio: نبذة مختصرة      # optional`,
    `# born: 661 هـ          # optional (free-form)`,
    `# died: 728 هـ          # optional`,
    `# location: حرّان        # optional`,
    `# era: العباسي          # optional: الجاهلي | صدر الإسلام | الأموي | العباسي | الأندلسي | المتأخّر | الحديث`,
    `# image: https://r2.ahlalathar.com/img/...   # optional`,
  ]) + "\nالترجمة بالعربية…\n",

  subject: (t) => fm([...shared(t),
    `# description: وصف التصنيف   # optional`,
  ]) + PROSE,

  topic: (t) => fm([...shared(t),
    `subject: subject-id-here   # REQUIRED → معرّف تصنيف موجود`,
    `# description: وصف الموضوع   # optional`,
  ]) + PROSE,

  book: (t) => fm([...shared(t),
    `person: person-id-here     # REQUIRED → معرّف المؤلف`,
    `kind: متن                  # متن | مرجع | مجموع`,
    `# topics: [topic-id-here]   # optional, 1–5`,
    `# description: تعريف الكتاب  # optional`,
    `# edition: ط. دار…          # optional`,
    `# cover: https://r2.ahlalathar.com/img/...   # optional`,
  ]) + "\n## باب أول\n\nنصّ الفقرة الأولى… {#p1}\n",

  poem: (t) => fm([...shared(t),
    `person: person-id-here     # REQUIRED → معرّف الناظم`,
    `# topics: [topic-id-here]   # optional, 1–5`,
    `# description: تعريف المنظومة # optional`,
  ]) + "\nالشطر الأول --- الشطر الثاني\nالشطر الأول --- الشطر الثاني\n",

  series: (t) => fm([...shared(t),
    `person: person-id-here     # REQUIRED → معرّف الشارح/المدرّس`,
    `# topics: [topic-id-here]   # optional, 1–5`,
    `# source_type: book         # optional: book | poem (لازمٌ مع source_id)`,
    `# source_id: source-id-here # optional`,
    `# description: تعريف السلسلة  # optional`,
  ]) + PROSE,

  lesson: (t) => fm([...shared(t),
    `series: series-id-here     # REQUIRED → معرّف السلسلة`,
    `order: 1                   # REQUIRED → ترتيب الدرس`,
    `# audio: audio-id-here      # optional → معرّف صوتية`,
    `# duration: "1:02:30"       # optional`,
  ]) + "\nنصّ التفريغ (transcript) بالعربية — إلزامي قبل النشر…\n",

  question: (t) => fm([...shared(t),
    `topics: [topic-id-here]    # REQUIRED, 1–5`,
    `# person: person-id-here    # optional → المُجيب`,
  ]) + "\nنصّ الجواب بالعربية…\n",

  benefit: (t) => fm([...shared(t),
    `person: person-id-here     # REQUIRED → صاحب الفائدة`,
    `# topics: [topic-id-here]   # optional, 1–5`,
    `# source_type: lesson       # optional: lesson | book | article | poem`,
    `# source_id: source-id-here # optional`,
  ]) + "\nنصّ الفائدة بالعربية…\n",

  article: (t) => fm([...shared(t),
    `person: person-id-here     # REQUIRED → معرّف الكاتب`,
    `# topics: [topic-id-here]   # optional, 1–5`,
    `# audio: audio-id-here      # optional`,
    `# description: نبذة          # optional`,
  ]) + PROSE,

  audio: (t) => fm([...shared(t),
    `source_type: lesson        # REQUIRED: lesson | book | poem | article`,
    `source_id: source-id-here  # REQUIRED → معرّف المصدر`,
    `url: https://r2.ahlalathar.com/audio/...   # REQUIRED`,
    `format: opus               # opus | mp3`,
    `# duration: "1:02:30"       # optional`,
    `# size_bytes: 12345678      # optional`,
  ]) + "\n(الصوتية مضمَّنة — لا صفحة مستقلة لها.)\n",

  annotation: (t) => fm([...shared(t),
    `target_type: poem          # REQUIRED: book | poem`,
    `target_id: target-id-here  # REQUIRED → معرّف المتن/المنظومة`,
    `anchor: v1                 # REQUIRED → v{n} لبيت، أو p{n}/{#id} لفقرة`,
    `kind: شرح                  # شرح | حاشية | إعراب | تخريج`,
    `# annotator: person-id-here # optional`,
  ]) + "\nنصّ الشرح بالعربية…\n",

  announcement: (t) => fm([...shared(t),
    `# body: نصّ الإعلان         # optional`,
    `priority: 5                # 1–10`,
    `# expires_at: ${TODAY}      # optional`,
  ]) + "\n",
};

const known = Object.keys(TEMPLATES);
const rel = (p) => p.replace(ROOT + "/", "");
const die = (msg) => { console.error(`✗ ${msg}`); process.exit(1); };

const [, , entity, slug, ...titleParts] = process.argv;

if (!entity || !slug) {
  console.error("Usage: pnpm new <entity> <slug> [title…]");
  console.error("Entities: " + known.join(", "));
  process.exit(1);
}
if (!known.includes(entity)) die(`unknown entity '${entity}'. Known: ${known.join(", ")}`);
if (!SLUG_RE.test(slug)) die(`invalid slug '${slug}' — lowercase letters, digits, hyphens (-- for child).`);

const file = join(CONTENT, entity, `${slug}.md`);
if (existsSync(file)) die(`already exists: ${rel(file)}`);

const title = titleParts.join(" ").trim() || "العنوان هنا";
mkdirSync(dirname(file), { recursive: true });
writeFileSync(file, TEMPLATES[entity](title), "utf8");

console.log(`✓ created ${rel(file)}`);
console.log(`  next: replace the *-id-here placeholders, then run:`);
console.log(`    pnpm validate:content && pnpm build`);
