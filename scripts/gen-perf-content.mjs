#!/usr/bin/env node
/**
 * أهل الأثر — Performance Content Generator
 *
 * Generates realistic Arabic Islamic content at scale to benchmark:
 *   - Astro build time (cold and warm)
 *   - Schema validation throughput
 *
 * Usage:
 *   node scripts/gen-perf-content.mjs [--count=N] [--clean] [--dry-run]
 *
 *   --count=N   total content items to generate (default: 500)
 *   --clean     remove all previously generated files (safe, marker-based)
 *   --dry-run   print distribution plan without writing any files
 */

import {
  writeFileSync,
  mkdirSync,
  existsSync,
  readdirSync,
  unlinkSync,
  openSync,
  readSync,
  closeSync,
} from "node:fs";
import { join, dirname } from "node:path";
import { performance } from "node:perf_hooks";
import { parseArgs } from "node:util";

// ---------------------------------------------------------------------------
// CLI args
// ---------------------------------------------------------------------------

const { values: args, positionals } = parseArgs({
  options: {
    count:     { type: "string",  default: "500" },
    clean:     { type: "boolean", default: false },
    "dry-run": { type: "boolean", default: false },
  },
  strict: false,
  allowPositionals: true,
});

const COUNT_GIVEN = process.argv.some(a => a.startsWith("--count"));
const CLEAN_ONLY = (args.clean ?? false) && !COUNT_GIVEN; // --clean with no explicit --count
const TOTAL = CLEAN_ONLY ? 0 : Math.max(1, parseInt(args.count ?? "500", 10));
const CLEAN = args.clean  ?? false;
const DRY   = args["dry-run"] ?? false;

// ---------------------------------------------------------------------------
// Paths
// ---------------------------------------------------------------------------

const ROOT = new URL("../src/content", import.meta.url).pathname;
const DIRS = {
  person:   join(ROOT, "person"),
  subject:  join(ROOT, "subject"),
  topic:    join(ROOT, "topic"),
  book:     join(ROOT, "book"),
  poem:     join(ROOT, "poem"),
  series:   join(ROOT, "series"),
  lesson:   join(ROOT, "lesson"),
  article:  join(ROOT, "article"),
  benefit:  join(ROOT, "benefit"),
  question: join(ROOT, "question"),
};

// Every generated file gets this marker so cleanup is safe and targeted
const MARKER = "# @perf-generated";

// ---------------------------------------------------------------------------
// Arabic Islamic vocabulary pools
// ---------------------------------------------------------------------------

const SCHOLARS = [
  { base: "scholar-taymiyyah",  name: "شيخ الإسلام ابن تيمية",    born: "661 هـ", died: "728 هـ", loc: "حران / دمشق" },
  { base: "scholar-qayyim",     name: "ابن القيم الجوزية",          born: "691 هـ", died: "751 هـ", loc: "دمشق" },
  { base: "scholar-kathir",     name: "ابن كثير الدمشقي",           born: "701 هـ", died: "774 هـ", loc: "دمشق" },
  { base: "scholar-nawawi",     name: "الإمام النووي",               born: "631 هـ", died: "676 هـ", loc: "نوى / دمشق" },
  { base: "scholar-hajar",      name: "ابن حجر العسقلاني",          born: "773 هـ", died: "852 هـ", loc: "القاهرة" },
  { base: "scholar-bukhari",    name: "الإمام البخاري",              born: "194 هـ", died: "256 هـ", loc: "بخارى" },
  { base: "scholar-shafii",     name: "الإمام الشافعي",              born: "150 هـ", died: "204 هـ", loc: "مكة / القاهرة" },
  { base: "scholar-ahmad",      name: "الإمام أحمد بن حنبل",        born: "164 هـ", died: "241 هـ", loc: "بغداد" },
  { base: "scholar-ghazali",    name: "أبو حامد الغزالي",           born: "450 هـ", died: "505 هـ", loc: "طوس / بغداد" },
  { base: "scholar-tirmidhi",   name: "الإمام الترمذي",              born: "209 هـ", died: "279 هـ", loc: "ترمذ" },
];

const SUBJECTS_DATA = [
  { base: "subj-aqidah",   name: "العقيدة" },
  { base: "subj-fiqh",     name: "الفقه" },
  { base: "subj-hadith",   name: "الحديث النبوي" },
  { base: "subj-tafsir",   name: "التفسير" },
  { base: "subj-sira",     name: "السيرة النبوية" },
  { base: "subj-usul",     name: "أصول الفقه" },
];

const TOPICS_DATA = [
  { base: "tpc-rububiyyah",  subjectIdx: 0, name: "توحيد الربوبية" },
  { base: "tpc-uluhiyyah",   subjectIdx: 0, name: "توحيد الألوهية" },
  { base: "tpc-asma-sifat",  subjectIdx: 0, name: "الأسماء والصفات" },
  { base: "tpc-salah",       subjectIdx: 1, name: "الصلاة وأحكامها" },
  { base: "tpc-zakah",       subjectIdx: 1, name: "الزكاة وأحكامها" },
  { base: "tpc-siyam",       subjectIdx: 1, name: "الصيام وأحكامه" },
  { base: "tpc-mustalah",    subjectIdx: 2, name: "مصطلح الحديث" },
  { base: "tpc-rijal",       subjectIdx: 2, name: "علم الرجال والجرح والتعديل" },
  { base: "tpc-uloom-quran", subjectIdx: 3, name: "علوم القرآن" },
  { base: "tpc-maghazi",     subjectIdx: 4, name: "المغازي والسرايا" },
  { base: "tpc-qawaid",      subjectIdx: 5, name: "القواعد الأصولية" },
];

const LESSON_BODIES = [
  `## المقدمة

افتتح الشيخ الدرس بالبسملة والحمدلة، ثم بيّن أهمية هذا الموضوع وارتباطه بما سبق.

## أولاً: تعريف المسألة

عرّف الشيخ المسألة تعريفاً جامعاً مانعاً، وفرّق بينها وبين ما يشابهها، مستعيناً بأقوال أهل العلم.

## ثانياً: الأدلة الشرعية

استدل الشيخ بجملة من الآيات القرآنية والأحاديث النبوية الصحيحة، وبيّن وجه الدلالة من كل نص.

## ثالثاً: أقوال أهل العلم

استعرض الشيخ أقوال الأئمة في المسألة ووجّه كل قول وبيّن الراجح منها بالدليل والبرهان.

## الخلاصة

ختم الشيخ الدرس بخلاصة جامعة، ونصح الطلاب بمراجعة المصادر الأصلية.`,

  `## الدرس

تناول الشيخ في هذا الدرس جملةً من المسائل المتعلقة بالباب، مستدلاً بالكتاب والسنة وأقوال السلف.

## المسألة الأولى

بيّن الشيخ أن هذه المسألة من المسائل التي وقع فيها الخلاف، وذكر أن الصواب ما كان عليه السلف الصالح.

## المسألة الثانية

انتقل إلى مسألة أخرى وثيقة الصلة، وأجاب عن الإشكالات الواردة عليها بأجوبة علمية مدعومة بالأدلة.

## الفوائد المستخلصة

١. أن العلم بالدليل هو الطريق الأسلم.
٢. اتباع السلف في الفهم والاستدلال.
٣. التثبت قبل إصدار الحكم في المسائل الخلافية.`,

  `## بين يدي الدرس

في هذه الجلسة تناول الشيخ أموراً بالغة الأهمية لطالب العلم، وبدأ بالتنبيه على بعض المسائل الشائكة.

## شرح النص

تلا الشيخ النص المقرر وشرعه كلمةً كلمة، مع ذكر الشواهد والأمثلة التي تُقرّب المعنى للذهن.

## التطبيق

طلب الشيخ من الطلاب تطبيق ما تعلموه على أمثلة من الواقع، وأجاب عن الأسئلة التي أُثيرت.

## الواجب

أوصى الشيخ بقراءة الباب التالي مع استخراج الفوائد والأحكام الواردة فيه.`,
];

const BENEFIT_QUOTES = [
  "قال شيخ الإسلام ابن تيمية رحمه الله: «أفضل العبادة ما وافق السنة وإن قلّ، وشر العبادة ما خالفها وإن كثر».",
  "ذكر ابن القيم رحمه الله في مدارج السالكين أن القلب لا يصلح إلا بذكر الله تعالى، وأن الغفلة هي موت القلب.",
  "قال الإمام الشافعي رحمه الله: «إذا وجدتم في كتابي ما يخالف سنة رسول الله ﷺ فخذوا بالسنة ودعوا قولي».",
  "قال الإمام أحمد رحمه الله: «عليك بالأثر وأصحاب الأثر، فإنهم هم الذين يُقتدى بهم في الدين».",
  "قال ابن كثير رحمه الله: التوحيد هو أصل الدين وقاعدته، ومن أخل به فقد أخل بالدين كله.",
  "قال النووي رحمه الله: الإخلاص شرط في قبول كل عمل، فمن أشرك في عمله فعمله مردود عليه.",
  "قال ابن القيم: من آيات التوفيق أن يُوفَّق المرء لطالب العلم النافع الذي يثمر العمل الصالح.",
];

const QUESTION_TEXTS = [
  "ما حكم تارك الصلاة عمداً مع الإقرار بوجوبها؟ وما الفرق بين تركها جحوداً وتركها كسلاً؟",
  "ما معنى قول أهل السنة في صفات الله: الإيمان بها من غير تأويل ولا تعطيل ولا تكييف ولا تمثيل؟",
  "ما الحكمة من تكرار قصص الأنبياء في القرآن الكريم؟ وما الفوائد المستخلصة منها؟",
  "ما حد الغيبة المحرمة، وما المستثنى منها؟ وكيف يفرق العالم بين الغيبة والجرح والتعديل المشروع؟",
  "ما أقسام التوحيد عند أهل السنة والجماعة؟ وهل هذا التقسيم توقيفي أم اصطلاحي؟",
  "ما المراد بأهل الأثر؟ وما صفاتهم التي يُعرفون بها عند العلماء؟",
];

const POEM_VERSES = [
  "وَكُلُّ خَيْرٍ فِي اتِّبَاعِ مَنْ سَلَفْ    وَكُلُّ شَرٍّ فِي ابْتِدَاعِ مَنْ خَلَفْ",
  "طَلَبُ الْعِلْمِ فَرِيضَةٌ عَلَى كُلِّ مُسْلِمٍ    وَلِلْعِلْمِ أَهْلٌ يُعْرَفُونَ بِالْهُدَى",
  "مَنْ لَا يُذَلِّلُ نَفْسَهُ لِأُسْتَاذِهِ    يَبْقَى جَاهِلاً لَا يَنْتَفِعُ بِعِلْمِهِ",
  "إِذَا الْمَرْءُ لَمْ يَحْفَظْ كَلَامَ أَئِمَّةٍ    فَكَيْفَ يَنَالُ الْعِلْمَ بِاللَّهِ وَالنَّبَا",
];

// ---------------------------------------------------------------------------
// Utilities
// ---------------------------------------------------------------------------

const pick  = (arr)      => arr[Math.floor(Math.random() * arr.length)];
const rInt  = (lo, hi)   => Math.floor(Math.random() * (hi - lo + 1)) + lo;
const pad2  = (n)        => String(n).padStart(2, "0");
const rDate = (y1, y2)   => `${rInt(y1, y2)}-${pad2(rInt(1, 12))}-${pad2(rInt(1, 28))}`;
const seqId = (base, i)  => `${base}-${String(i).padStart(4, "0")}`;

/** Check if a file contains the perf marker (reads only first 300 bytes). */
function hasMarker(filePath) {
  try {
    const fd  = openSync(filePath, "r");
    const buf = Buffer.alloc(300);
    const n   = readSync(fd, buf, 0, 300, 0);
    closeSync(fd);
    return buf.subarray(0, n).toString("utf8").includes("@perf-generated");
  } catch {
    return false;
  }
}

/** Remove all marker-tagged files from all content dirs. */
function cleanAll() {
  let removed = 0;
  for (const dir of Object.values(DIRS)) {
    if (!existsSync(dir)) continue;
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      if (!entry.isFile() || !entry.name.endsWith(".md")) continue;
      const fp = join(dir, entry.name);
      if (hasMarker(fp)) { unlinkSync(fp); removed++; }
    }
  }
  return removed;
}

/** Write a file, creating parent dirs as needed. No-op in DRY mode. */
function write(filePath, content) {
  if (DRY) return;
  mkdirSync(dirname(filePath), { recursive: true });
  writeFileSync(filePath, content, "utf8");
}

// ---------------------------------------------------------------------------
// Content generators — each returns { id, path, content }
// ---------------------------------------------------------------------------

function genPerson(i) {
  const s  = SCHOLARS[i % SCHOLARS.length];
  const id = seqId(s.base, i);
  return {
    id,
    path: join(DIRS.person, `${id}.md`),
    content: [
      "---",
      MARKER,
      `title: "${s.name} (${i})"`,
      `status: published`,
      `published_at: ${rDate(2023, 2025)}`,
      `born: "${s.born}"`,
      `died: "${s.died}"`,
      `location: ${s.loc}`,
      "---",
      "",
      `أحد علماء أهل السنة والجماعة، طلب العلم من صغره وأخذه عن كبار شيوخ عصره. خلّف آثاراً علمية قيّمة في مختلف الفنون. رحمه الله رحمةً واسعة.`,
      "",
    ].join("\n"),
  };
}

function genSubject(i) {
  const s  = SUBJECTS_DATA[i % SUBJECTS_DATA.length];
  const id = seqId(s.base, i);
  return {
    id,
    path: join(DIRS.subject, `${id}.md`),
    content: [
      "---",
      MARKER,
      `title: "${s.name} (${i})"`,
      `status: published`,
      `published_at: ${rDate(2023, 2025)}`,
      `description: "تصنيف يشمل المواد المتعلقة بـ${s.name} من كتب وسلاسل ودروس."`,
      "---",
      "",
      `${s.name} من أهم علوم الشريعة الإسلامية التي ينبغي للمسلم الاعتناء بها وطلبها على وجهها الصحيح.`,
      "",
    ].join("\n"),
  };
}

function genTopic(i, subjectId) {
  const t  = TOPICS_DATA[i % TOPICS_DATA.length];
  const id = seqId(t.base, i);
  return {
    id,
    path: join(DIRS.topic, `${id}.md`),
    content: [
      "---",
      MARKER,
      `title: "${t.name} (${i})"`,
      `subject: ${subjectId}`,
      `status: published`,
      `published_at: ${rDate(2023, 2025)}`,
      `description: "موضوع في ${t.name}، يتناول جوانب متعددة بالأدلة والتفاصيل."`,
      "---",
      "",
      `هذا الموضوع من أهم المواضيع المندرجة تحت هذا التصنيف، وقد اعتنى به العلماء في مؤلفاتهم عناية بالغة.`,
      "",
    ].join("\n"),
  };
}

function genBook(i, personId, topicId) {
  const id = seqId("kitab", i);
  return {
    id,
    path: join(DIRS.book, `${id}.md`),
    content: [
      "---",
      MARKER,
      `title: "كتاب في المسائل الشرعية (${i})"`,
      `person: ${personId}`,
      `topics:`,
      `  - ${topicId}`,
      `status: published`,
      `published_at: ${rDate(2023, 2025)}`,
      `description: "من أمهات الكتب في هذا الباب، ألّفه العالم جواباً لسؤال ورد إليه."`,
      "---",
      "",
      `## مقدمة الكتاب`,
      "",
      `بسم الله الرحمن الرحيم. هذا كتاب جمعت فيه خلاصة ما وقفت عليه من كلام أهل العلم في هذا الباب، أسأل الله أن ينفع به.`,
      "",
      `## الفصل الأول`,
      "",
      `اعلم أن هذه المسألة من أهم المسائل التي تعرض لطالب العلم، وقد اعتنى بها العلماء قديماً وحديثاً اعتناءً بالغاً.`,
      "",
      `## الفصل الثاني`,
      "",
      `ذكر المؤلف جملةً من الأدلة الشرعية الدالة على الحكم، وناقش المخالفين بأسلوب علمي رصين مع الإنصاف والعدل.`,
      "",
      `## الخاتمة`,
      "",
      `وفي الختام أوصي نفسي والقارئ بتقوى الله والعمل بما علمنا، فالعلم بلا عمل حجة على صاحبه.`,
      "",
    ].join("\n"),
  };
}

function genPoem(i, personId, topicId) {
  const id    = seqId("manzuma", i);
  const verse = pick(POEM_VERSES);
  const count = rInt(20, 150);
  return {
    id,
    path: join(DIRS.poem, `${id}.md`),
    content: [
      "---",
      MARKER,
      `title: "منظومة في العلم الشرعي (${i})"`,
      `person: ${personId}`,
      `topics:`,
      `  - ${topicId}`,
      `status: published`,
      `published_at: ${rDate(2023, 2025)}`,
      `description: "منظومة علمية من ${count} بيتاً في هذا الباب."`,
      "---",
      "",
      verse,
      "",
      "وَمَنْ يَطْلُبُ الْعِلْمَ الشَّرِيفَ بِنِيَّةٍ    يُوَفِّقُهُ اللهُ لِلرَّشَادِ وَالْهُدَى",
      "",
      "وَخَيْرُ الْعِلْمِ مَا بُنِيَ عَلَى الدَّلِيلِ    وَشَرُّهُ مَا جَاءَ مِنْ قَوْلٍ عَلِيلِ",
      "",
    ].join("\n"),
  };
}

function genSeries(i, personId, topicId, sourceType, sourceId) {
  const id = seqId("silsila", i);
  return {
    id,
    path: join(DIRS.series, `${id}.md`),
    content: [
      "---",
      MARKER,
      `title: "سلسلة دروس علمية (${i})"`,
      `person: ${personId}`,
      `topics:`,
      `  - ${topicId}`,
      `source_type: ${sourceType}`,
      `source_id: ${sourceId}`,
      `status: published`,
      `published_at: ${rDate(2023, 2025)}`,
      `description: "سلسلة متكاملة من الدروس تستعرض هذا الموضوع بشكل منهجي ومتسلسل."`,
      "---",
      "",
    ].join("\n"),
  };
}

function genLesson(i, seriesId, order) {
  const id  = `${seriesId}--dars-${String(order).padStart(3, "0")}`;
  const h   = rInt(0, 2), m = rInt(0, 59), s = rInt(0, 59);
  const dur = `${h}:${pad2(m)}:${pad2(s)}`;
  return {
    id,
    path: join(DIRS.lesson, `${id}.md`),
    content: [
      "---",
      MARKER,
      `title: "الدرس ${order} — سلسلة ${i}"`,
      `series: ${seriesId}`,
      `order: ${order}`,
      `status: published`,
      `published_at: ${rDate(2023, 2025)}`,
      `duration: "${dur}"`,
      "---",
      "",
      pick(LESSON_BODIES),
      "",
    ].join("\n"),
  };
}

function genArticle(i, personId, topicId) {
  const id = seqId("maqala", i);
  return {
    id,
    path: join(DIRS.article, `${id}.md`),
    content: [
      "---",
      MARKER,
      `title: "مقالة في المسائل الشرعية (${i})"`,
      `person: ${personId}`,
      `topics:`,
      `  - ${topicId}`,
      `status: published`,
      `published_at: ${rDate(2023, 2025)}`,
      `description: "مقالة تعنى بشرح وبيان أحكام شرعية مهمة يحتاج إليها المسلم."`,
      "---",
      "",
      `## المقدمة`,
      "",
      `الحمد لله رب العالمين، والصلاة والسلام على نبينا محمد وعلى آله وصحبه أجمعين. أما بعد:`,
      "",
      `## المسألة الأولى`,
      "",
      `في هذه المسألة ذهب جمهور العلماء إلى أن الحكم الشرعي مبني على ما دلت عليه النصوص الصريحة من الكتاب والسنة، ولا يُعدل عنه إلى غيره إلا بدليل.`,
      "",
      `## المسألة الثانية`,
      "",
      `أما هذه المسألة فقد اختلف فيها العلماء على قولين مشهورين، والراجح ما كان موافقاً لظاهر الدليل ومذهب السلف الصالح.`,
      "",
      `## الخاتمة`,
      "",
      `والله أعلم بالصواب، وصلى الله وسلم على نبينا محمد وعلى آله وصحبه أجمعين.`,
      "",
    ].join("\n"),
  };
}

function genBenefit(i, personId, topicId) {
  const id = seqId("faida", i);
  return {
    id,
    path: join(DIRS.benefit, `${id}.md`),
    content: [
      "---",
      MARKER,
      `title: "فائدة شرعية (${i})"`,
      `person: ${personId}`,
      `topics:`,
      `  - ${topicId}`,
      `status: published`,
      `published_at: ${rDate(2023, 2025)}`,
      "---",
      "",
      pick(BENEFIT_QUOTES),
      "",
    ].join("\n"),
  };
}

function genQuestion(i, topicId) {
  const id = seqId("masala", i);
  const q  = pick(QUESTION_TEXTS);
  return {
    id,
    path: join(DIRS.question, `${id}.md`),
    content: [
      "---",
      MARKER,
      `title: "مسألة شرعية (${i})"`,
      `topics:`,
      `  - ${topicId}`,
      `status: published`,
      `published_at: ${rDate(2023, 2025)}`,
      "---",
      "",
      `## السؤال`,
      "",
      q,
      "",
      `## الجواب`,
      "",
      `الجواب على هذه المسألة يستلزم استحضار الأدلة الشرعية من الكتاب والسنة وأقوال السلف الصالح. ولا يسوغ الجواب من مجرد الرأي والاستحسان دون دليل يسنده.`,
      "",
    ].join("\n"),
  };
}

// ---------------------------------------------------------------------------
// Distribution: percentages of TOTAL per content type
// ---------------------------------------------------------------------------

function computeCounts(total) {
  return {
    persons:   Math.max(SCHOLARS.length,     Math.ceil(total * 0.05)),
    subjects:  Math.max(SUBJECTS_DATA.length, Math.ceil(total * 0.02)),
    topics:    Math.max(TOPICS_DATA.length,   Math.ceil(total * 0.03)),
    books:     Math.ceil(total * 0.10),
    poems:     Math.ceil(total * 0.05),
    series:    Math.ceil(total * 0.10),
    lessons:   Math.ceil(total * 0.40),  // largest: real archives are lesson-heavy
    articles:  Math.ceil(total * 0.10),
    benefits:  Math.ceil(total * 0.10),
    questions: Math.ceil(total * 0.05),
  };
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

const t0 = performance.now();

console.log(`\n⚙️  أهل الأثر — Performance Content Generator`);

if (CLEAN) {
  process.stdout.write("🧹 Cleaning previously generated files… ");
  const removed = cleanAll();
  console.log(`removed ${removed} file(s).`);
  if (CLEAN_ONLY || TOTAL === 0) process.exit(0);
}

const counts    = computeCounts(TOTAL);
const totalFiles = Object.values(counts).reduce((a, b) => a + b, 0);

console.log(`\n   Target: ~${TOTAL} items  →  ${totalFiles} actual files`);
if (DRY) console.log(`   Mode  : DRY RUN — no files written`);
console.log(`\n   Distribution:`);
for (const [k, v] of Object.entries(counts)) {
  const bar = "█".repeat(Math.round(v / totalFiles * 40));
  console.log(`     ${k.padEnd(12)} ${String(v).padStart(5)}  ${bar}`);
}

if (DRY) {
  console.log(`\n✓ Dry run complete (${((performance.now() - t0) / 1000).toFixed(2)}s)\n`);
  process.exit(0);
}

// Ensure all directories exist
for (const d of Object.values(DIRS)) mkdirSync(d, { recursive: true });

// ── Generate all content types ───────────────────────────────────────────────

const personIds  = [];
const subjectIds = [];
const topicIds   = [];
const bookIds    = [];
const poemIds    = [];
const seriesIds  = [];

// 1. Persons
process.stdout.write(`\n📝 Persons  (${counts.persons})… `);
for (let i = 0; i < counts.persons; i++) {
  const f = genPerson(i); write(f.path, f.content); personIds.push(f.id);
}
console.log("done");

// 2. Subjects
process.stdout.write(`📝 Subjects (${counts.subjects})… `);
for (let i = 0; i < counts.subjects; i++) {
  const f = genSubject(i); write(f.path, f.content); subjectIds.push(f.id);
}
console.log("done");

// 3. Topics (linked to subjects)
process.stdout.write(`📝 Topics   (${counts.topics})… `);
for (let i = 0; i < counts.topics; i++) {
  const f = genTopic(i, subjectIds[i % subjectIds.length]);
  write(f.path, f.content); topicIds.push(f.id);
}
console.log("done");

// 4. Books
process.stdout.write(`📝 Books    (${counts.books})… `);
for (let i = 0; i < counts.books; i++) {
  const f = genBook(i, personIds[i % personIds.length], topicIds[i % topicIds.length]);
  write(f.path, f.content); bookIds.push(f.id);
}
console.log("done");

// 5. Poems
process.stdout.write(`📝 Poems    (${counts.poems})… `);
for (let i = 0; i < counts.poems; i++) {
  const f = genPoem(i, personIds[i % personIds.length], topicIds[i % topicIds.length]);
  write(f.path, f.content); poemIds.push(f.id);
}
console.log("done");

// 6. Series (alternates between book and poem sources)
process.stdout.write(`📝 Series   (${counts.series})… `);
for (let i = 0; i < counts.series; i++) {
  const useBook  = i % 2 === 0;
  const sourceId = useBook ? bookIds[i % bookIds.length] : poemIds[i % poemIds.length];
  const f = genSeries(
    i,
    personIds[i % personIds.length],
    topicIds[i % topicIds.length],
    useBook ? "book" : "poem",
    sourceId,
  );
  write(f.path, f.content); seriesIds.push(f.id);
}
console.log("done");

// 7. Lessons (distributed evenly across series)
process.stdout.write(`📝 Lessons  (${counts.lessons})… `);
const lessonOrderMap = {};
for (let i = 0; i < counts.lessons; i++) {
  const sId = seriesIds[i % seriesIds.length];
  lessonOrderMap[sId] = (lessonOrderMap[sId] ?? 0) + 1;
  const f = genLesson(i, sId, lessonOrderMap[sId]);
  write(f.path, f.content);
}
console.log("done");

// 8. Articles
process.stdout.write(`📝 Articles (${counts.articles})… `);
for (let i = 0; i < counts.articles; i++) {
  const f = genArticle(i, personIds[i % personIds.length], topicIds[i % topicIds.length]);
  write(f.path, f.content);
}
console.log("done");

// 9. Benefits
process.stdout.write(`📝 Benefits (${counts.benefits})… `);
for (let i = 0; i < counts.benefits; i++) {
  const f = genBenefit(i, personIds[i % personIds.length], topicIds[i % topicIds.length]);
  write(f.path, f.content);
}
console.log("done");

// 10. Questions
process.stdout.write(`📝 Questions(${counts.questions})… `);
for (let i = 0; i < counts.questions; i++) {
  const f = genQuestion(i, topicIds[i % topicIds.length]);
  write(f.path, f.content);
}
console.log("done");

// ── Summary ──────────────────────────────────────────────────────────────────

const genSec = ((performance.now() - t0) / 1000).toFixed(2);
const byteCount = totalFiles * 800; // rough avg bytes per file
console.log(`
✅ Generation complete
   Files written : ${totalFiles}
   Approx. size  : ~${(byteCount / 1024).toFixed(0)} KB
   Time elapsed  : ${genSec}s

Next steps:
  pnpm build              → measure full build time
  pnpm perf:budget        → check per-page render weight
  node scripts/gen-perf-content.mjs --clean  → remove generated files
`);
