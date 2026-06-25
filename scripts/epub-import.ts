#!/usr/bin/env tsx
// EPUB → Markdown importer for athar-archive's content model.
// Targets the المكتبة الشاملة (Shamela) EPUB export — one xhtml per printed
// page, آيات in {…}, chapters in <span class="title">, المحقق notes in
// <span class="footnote">.
//
// Poem detection: Shamela marks each بيت as
//   <span class="red">N-</span>صدر <span class="red">...</span> عجز<br />
// When ≥ POEM_VERSE_THRESHOLD% of lines across the book carry this pattern
// the epub is routed to poem/ and the body is written in `صدر --- عجز` format.
//
// Features:
//   A. Poem detection → poem/ in صدر --- عجز format (no page-sep divs in poems)
//   B. Richer metadata: reads ALL info.xhtml label→value pairs (publisher,
//      volumes, قسم→subject, author bio/طبقة)
//   C. Auto-taxonomy: maps قسم to topic slug + emits topic stub if missing
//      شرح/حاشية linking: --sharh-of <slug> emits annotation stub
//      متن detection: sets kind: متن when title matches متون keywords
//      Edition grouping: adds --v2, --v3 suffix when slug already exists
//      Volume merging: --merge-volumes concatenates a directory of epubs
//
// Usage:  pnpm import:epub <file.epub|dir/> [more…] [flags]
//   --out <dir>            content root (default: src/content)
//   --kind <متن|مرجع|مجموع>   override book.kind (auto-detected if omitted)
//   --status <draft|…>    (default: published)
//   --slug <slug>          override book slug (single-epub only)
//   --person-slug <s>      override author slug (single-epub only)
//   --sharh-of <slug>      emit annotation stub instead of book/poem entry
//   --merge-volumes        treat a directory as one multi-volume work to merge
//   --dry-run              print what would be written, write nothing
//   --selftest             run built-in assertions and exit

import { execFileSync } from "node:child_process";
import {
  mkdtempSync, rmSync, readFileSync, writeFileSync,
  existsSync, mkdirSync, readdirSync, statSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname, basename } from "node:path";

// ─────────────────────────────────────────────
// Arabic → slug
// ─────────────────────────────────────────────
const TASHKEEL = /[ً-ْٰـ]/g; // harakat + tatweel
const stripTashkeel = (s: string) => s.replace(TASHKEEL, "");
const TR: Record<string, string> = {
  "ا": "a", "أ": "a", "إ": "i", "آ": "a", "ٱ": "a",
  "ب": "b", "ت": "t", "ث": "th", "ج": "j", "ح": "h",
  "خ": "kh", "د": "d", "ذ": "dh", "ر": "r", "ز": "z",
  "س": "s", "ش": "sh", "ص": "s", "ض": "d", "ط": "t",
  "ظ": "z", "ع": "a", "غ": "gh", "ف": "f", "ق": "q",
  "ك": "k", "ل": "l", "م": "m", "ن": "n", "ه": "h",
  "ة": "a", "و": "w", "ؤ": "w", "ي": "y", "ى": "a",
  "ئ": "y", "ء": "",
};
const ALIF_LAM = "ال";
function translitWord(w: string): string {
  if (w.startsWith(ALIF_LAM) && w.length > 2) return "al-" + translitWord(w.slice(2));
  return [...w].map((c) => TR[c] ?? "").join("");
}
export function slugify(ar: string): string {
  const s = stripTashkeel(ar)
    .split(/\s+/).map(translitWord).join("-")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/-{2,}/g, "-")
    .replace(/^-+|-+$/g, "");
  return /^[a-z0-9]+(-[a-z0-9]+)*$/.test(s) ? s : "book-" + Math.random().toString(36).slice(2, 8);
}

// ─────────────────────────────────────────────
// HTML helpers
// ─────────────────────────────────────────────
function decode(s: string): string {
  return s
    .replace(/&quot;/g, '"').replace(/&nbsp;/g, " ").replace(/&lt;/g, "<").replace(/&gt;/g, ">")
    .replace(/&#(\d+);/g, (_, d) => String.fromCodePoint(+d))
    .replace(/&#x([0-9a-f]+);/gi, (_, h) => String.fromCodePoint(parseInt(h, 16)))
    .replace(/&amp;/g, "&");
}
const stripTags = (s: string) => s.replace(/<[^>]+>/g, "");
const cleanInline = (s: string) => decode(stripTags(s)).replace(/[ \t]+/g, " ").trim();

// ─────────────────────────────────────────────
// متن / poem title keywords
// ─────────────────────────────────────────────
// Poem titles that Shamela exports as books but are actually منظومات.
// Signal: title contains نونية، منظومة، ألفية، قصيدة، أرجوزة، لامية، دالية، بائية، رائية، ميمية
// ponytail: use Unicode-aware word boundaries (?<=^|\P{L}) to handle prefixes like الـ/للـ and avoid substrings like الإسلامية.
const POEM_TITLE_RE = /(?<=^|\P{L})(?:ال|لل)?(?:منظومة|نونية|[أا]لفية|قصيدة|[أا]رجوزة|لامية|دالية|بائية|رائية|ميمية|مقصورة)(?=$|\P{L})/u;
// Fraction of body lines that must look like verses to auto-classify as poem
const POEM_VERSE_THRESHOLD = 0.35;

// ─────────────────────────────────────────────
// Genre classification: رار فن folder → section (قرآن / حديث / تراجم).
// The هاردسك collection is organized by فن ("06ـ (199) متون الحديث",
// "01ـ (164) التفاسير", …); the epub's parent folder decides its section.
// Absent = ordinary book. Override per-import with --genre.
// ─────────────────────────────────────────────
const GENRE_FOLDER_MAP: Array<{ pattern: RegExp; genre: string }> = [
  { pattern: /تفسير|التفاسير|علوم القرآن|قراءات|تجويد|رسم|مصحف|قرآن/u, genre: "قرآن" },
  { pattern: /حديث|تخريج|زوائد|علل|سؤالات|سنن|مسانيد|مسند|صحيح|جامع|أربعين|أربعون|موطأ/u, genre: "حديث" },
  { pattern: /تراجم|رجال|طبقات|سير|وفيات|أنساب/u, genre: "تراجم" },
];

/** Infer section genre from the epub's parent folder name (the رار فن). */
function genreFor(file: string): string | undefined {
  const folder = basename(dirname(file));
  return GENRE_FOLDER_MAP.find((g) => g.pattern.test(folder))?.genre;
}

// متن keywords → kind: متن
const MATN_TITLE_RE = /(?:^|[\s،(])متن(?:$|[\s،)=])|الأصول الثلاثة|القواعد الأربع|نواقض الإسلام|كشف الشبهات|ثلاثة الأصول|العقيدة الواسطية|الطحاوية|البيقونية|الرحبية|الآجرومية|الجزرية/u;

// ─────────────────────────────────────────────
// Auto-taxonomy: قسم → topic slug map
// Keys are Arabic قسم/section values from info.xhtml, values are topic slugs.
// Extend this map as you import more sections.
// ─────────────────────────────────────────────
const SECTION_TOPIC_MAP: Array<{ pattern: RegExp; topic: string; subject: string }> = [
  // 1. العقيدة العامة
  { pattern: /عقيدة|اعتقاد|الواسطية|الطحاوية|طحاوية|الحموية|التدمرية|أصول السنة|شرح السنة|لمعة الاعتقاد|أصول الدين|الشريعة للآجري|الإبانة/u, topic: "al-aqeedah-al-aamah", subject: "aqeedah" },
  
  // 2. التوحيد
  { pattern: /توحيد|الربوبية|الألوهية|العبادة|شرك|الشرك|نواقض|ثلاثة الأصول|الأصول الثلاثة|كشف الشبهات|القواعد الأربع|إخلاص|تجريد التوحيد|تطهير الاعتقاد/u, topic: "tahwid-al-ibada", subject: "aqeedah" },
  
  // 3. الأسماء والصفات
  { pattern: /أسماء الله|صفات|صفة|الاستواء|الفوقية|العلو|العرش|تفسير أسماء|اشتقاق أسماء|إثبات الصفات|التعطيل|الجهمية والمعطلة/u, topic: "al-asma-was-sifat", subject: "aqeedah" },
  
  // 4. الإيمان
  { pattern: /إيمان|الإيمان|شعب الإيمان|النفاق|نفاق|إرجاء|المرجئة|تكفير|التكفير/u, topic: "al-iman", subject: "aqeedah" },
  
  // 5. القضاء والقدر
  { pattern: /قدر|القدر|القضاء والقدر|القدرية|الجبرية|الاحتجاج بالقدر/u, topic: "al-qadr", subject: "aqeedah" },
  
  // 6. السمعيات
  { pattern: /اليوم الآخر|الآخرة|القبور|عذاب القبر|البعث|الحشر|الميزان|الحوض|الشفاعة|الجنة|النار|أشراط الساعة|الفتن|الملاحم|الدجال|المسيح/u, topic: "al-samiyyat", subject: "aqeedah" },
  
  // 7. الإمامة والصحابة
  { pattern: /الإمامة|السمع والطاعة|الصحابة|الآل والأصحاب|فضائل الصحابة|آل البيت|أمهات المؤمنين|معاوية|الخلافة/u, topic: "al-imamah-was-sahabah", subject: "aqeedah" },
  
  // 8. الولاء والبراء
  { pattern: /الولاء والبراء|موالاة|الهجرة|التشبه|الكفار/u, topic: "al-wala-wal-bara", subject: "aqeedah" },
  
  // 9. السنة والبدعة
  { pattern: /الاعتصام|البدع|بدعة|البدعة|ذم الكلام|الحوادث والبدع|الاتباع/u, topic: "al-sunnah-wal-bidah", subject: "aqeedah" },
  
  // 10. الفرق والردود
  { pattern: /الفرق|الملل والنحل|الأشاعرة|المعتزلة|الجهمية|الرافضة|الشيعة|التصوف|الصوفية|وحدة الوجود|الرد على|نقد|نقض|مقالات/u, topic: "al-firaq-war-rudud", subject: "aqeedah" },

  // Lughah / Nahw
  { pattern: /نحو|صرف|بلاغة|لغة|عربية|الآجرومية|ألفية ابن مالك/u, topic: "al-nahw-al-muyassar", subject: "nahw" },

  // Quran / Tafsir
  { pattern: /تفسير|علوم القرآن|قراءات|تجويد/u,           topic: "tafsir-al-quran",   subject: "quran" },

  // Hadith / Mustalah
  { pattern: /حديث|مصطلح|رجال|سند|تخريج|علل|مسانيد|موطأ/u, topic: "mustalah-al-hadith", subject: "hadith" },

  // Fiqh
  { pattern: /فقه حنبلي|حنابلة/u,                           topic: "fiqh-hanbali",      subject: "fiqh" },
  { pattern: /فقه مالكي|مالكية/u,                           topic: "fiqh-maliki",       subject: "fiqh" },
  { pattern: /فقه شافعي|شافعية/u,                           topic: "fiqh-shafii",       subject: "fiqh" },
  { pattern: /فقه حنفي|حنفية/u,                             topic: "fiqh-hanafi",       subject: "fiqh" },
  { pattern: /فقه مقارن|خلاف عالي/u,                        topic: "fiqh-muqaran",      subject: "fiqh" },
  { pattern: /فقه|أصول الفقه|الرحبية|الفرائض|المعاملات/u,   topic: "usul-al-fiqh",      subject: "fiqh" },

  // Tarajim
  { pattern: /تراجم|طبقات|سير|وفيات|رجال/u,                topic: "tarajim-al-ulama",  subject: "tarajim" },
];

// ─────────────────────────────────────────────
// Hadith category inference
// ─────────────────────────────────────────────
// Known امهات (the six/nine + Muwatta)
const UMMAHAT_TITLES = /البخاري|مسلم|أبو داود|الترمذي|النسائي|ابن ماجه|أحمد|الدارمي|موطأ/u;
// أجزاء: typically short standalone narration collections
const AJZA_FOLDER = /أجزاء|جزء/u;
// تخريج / علل books
const TAKHRIJ_FOLDER = /تخريج|زوائد|علل|سؤالات/u;
// آثار-style: ابن أبي الدنيا, etc.
const ATHAR_AUTHOR = /ابن أبي الدنيا/u;

function inferHadithCategory(meta: Meta, file: string): string | undefined {
  const folder = basename(dirname(file));
  if (UMMAHAT_TITLES.test(meta.title) || UMMAHAT_TITLES.test(meta.creator)) return "امهات الكتب";
  if (TAKHRIJ_FOLDER.test(folder) || TAKHRIJ_FOLDER.test(meta.qism ?? "")) return "تخريج";
  if (AJZA_FOLDER.test(folder) || AJZA_FOLDER.test(meta.title)) return "أجزاء حديثية";
  if (ATHAR_AUTHOR.test(meta.creator)) return "كتب الآثار";
  return undefined;
}

// ─────────────────────────────────────────────
// Poem verse detection
// Shamela verse line: <span class="red">N-</span>صدر <span class="red">...</span> عجز<br />
// ─────────────────────────────────────────────
// Matches one raw verse segment in the XHTML inner content.
// Group 1 = صدر text (may contain inline HTML), Group 2 = عجز text
// The hemistich separator <span class="red">...</span> appears once per verse in
// BOTH numbered (<span class="red">N-</span>صدر…) and unnumbered Shamela poems.
// Anchor on it, not on the optional leading number span (some نونية have no numbers).
const VERSE_SEP_RE = /<span\s+class=["']red["']>\s*\.\.\.\s*<\/span>/gi;
const VERSE_NUM_RE = /<span\s+class=["']red["']>\s*\d+\s*-\s*<\/span>/i;

/** Count how many verse-shaped lines appear in a raw XHTML string. */
function countVerses(xhtml: string): number {
  return [...xhtml.matchAll(VERSE_SEP_RE)].length;
}

/** Extract صدر/عجز pairs from one page's XHTML. Returns poem lines (صدر --- عجز). */
export function pageToVerseLines(
  xhtml: string,
): { lines: string[]; notes: string[]; headings: string[] } {
  // Pull footnotes first (same logic as prose path)
  const fnotes: { n: string; t: string }[] = [];
  // The Shamela footer is a sibling div OUTSIDE book-container:
  //   </div><hr/><div class="center">الجزء: N ¦ الصفحة: M</div></body>
  // Strip it from the full xhtml before extraction so it doesn't pollute inner.
  const cleaned = xhtml.replace(/<div[^>]*class=["']center["'][^>]*>[\s\S]*?<\/div>/gi, "");
  // Greedy capture from book-container open to its matching close (last </div> before </body>).
  let inner = cleaned.match(/<div[^>]*id=["']book-container["'][^>]*>([\s\S]+)<\/div>\s*(?:<hr[^>]*>)?\s*<\/body>/i)?.[1] ??
    cleaned.match(/<div[^>]*id=["']book-container["'][^>]*>([\s\S]*?)<\/div>/i)?.[1] ?? "";


  inner = inner.replace(/<span class=["']footnote-hr["']>[\s\S]*?<\/span>/gi, "");
  inner = inner.replace(/<span class=["']footnote["']>\s*(\d+)\s*([\s\S]*?)<\/span>/gi, (_m, n, t) => {
    fnotes.push({ n, t: cleanInline(t).replace(/\{/g, "﴿").replace(/\}/g, "﴾") });
    return "";
  });

  // Section headings
  const headings: string[] = [];
  inner = inner.replace(/<span class=["']title["']>([\s\S]*?)<\/span>/gi, (_m, t) => {
    const h = cleanInline(t).replace(/^[\d\s.ـ\-–—]+/, "");
    if (h) headings.push(`## ${h}`);
    return "";
  });

  // Now extract verse pairs. Split on <br/>; each hemistich pair is one segment
  // holding exactly one ... separator. Works for numbered and unnumbered poems.
  const lines: string[] = [];
  const ornate = (s: string) => s.replace(/\{/g, "﴿").replace(/\}/g, "﴾");
  for (const seg of inner.split(/<br\s*\/?>/i)) {
    const parts = seg.split(VERSE_SEP_RE);
    if (parts.length !== 2) continue; // not a verse line (0 or >1 separators)
    const sadr = ornate(cleanInline(parts[0].replace(VERSE_NUM_RE, "")));
    const ajuz = ornate(cleanInline(parts[1]));
    // Strip trailing inline footnote digit markers (they live in notes[])
    const clean = (s: string) => s.replace(/\d+$/, "").trim();
    if (sadr || ajuz) lines.push(`${clean(sadr)} --- ${clean(ajuz)}`);
  }

  return { lines, notes: fnotes.map((f) => f.t), headings };
}

// ─────────────────────────────────────────────
// Prose page → markdown  (unchanged logic, now also strips footer div)
// ─────────────────────────────────────────────
export function pageToMd(xhtml: string, pageId: string): { md: string; notes: string[]; parsedPage: string; parsedJuz?: string } {
  // The Shamela footer is a sibling div OUTSIDE book-container:
  //   </div><hr/><div class="center">الجزء: N ¦ الصفحة: M</div></body>
  const footerMatch = xhtml.match(/<div[^>]*class=["']center["'][^>]*>([\s\S]*?)<\/div>/i);
  let parsedPage = pageId;
  let parsedJuz: string | undefined;
  if (footerMatch) {
    const footer = footerMatch[1];
    const juzMatch = footer.match(/الجزء:\s*(\d+)/);
    if (juzMatch) parsedJuz = juzMatch[1];
    const pgMatch = footer.match(/الصفحة:\s*(\d+)/);
    if (pgMatch) parsedPage = pgMatch[1];
  }
  
  // Strip it from the full xhtml before extraction so it doesn't pollute inner.
  const cleaned = xhtml.replace(/<div[^>]*class=["']center["'][^>]*>[\s\S]*?<\/div>/gi, "");
  // Greedy capture: book-container inner content up to the last </div> before </body>.
  let inner = cleaned.match(/<div[^>]*id=["']book-container["'][^>]*>([\s\S]+)<\/div>\s*(?:<hr[^>]*>)?\s*<\/body>/i)?.[1] ??
    cleaned.match(/<div[^>]*id=["']book-container["'][^>]*>([\s\S]*?)<\/div>/i)?.[1] ?? "";

  // footnotes
  const fnotes: { n: string; t: string }[] = [];
  inner = inner.replace(/<span class=["']footnote-hr["']>[\s\S]*?<\/span>/gi, "");
  inner = inner.replace(/<span class=["']footnote["']>\s*(\d+)\s*([\s\S]*?)<\/span>/gi, (_m, n, t) => {
    fnotes.push({ n, t: cleanInline(t).replace(/\{/g, "﴿").replace(/\}/g, "﴾") });
    return "";
  });

  // chapter titles → H2
  inner = inner.replace(/<span class=["']title["']>([\s\S]*?)<\/span>/gi,
    (_m, t) => `\n## ${cleanInline(t).replace(/^[\d\s.ـ\-–—]+/, "")}\n`);
  inner = inner.replace(/<span class=["']red["']>([\s\S]*?)<\/span>/gi, "$1");

  // structure → text
  inner = inner.replace(/<a [^>]*>\s*<\/a>/gi, "");
  inner = inner.replace(/<br\s*\/?>/gi, "\n");
  inner = inner.replace(/\{/g, "﴿").replace(/\}/g, "﴾");
  let text = decode(stripTags(inner));

  // inline footnote sup markers
  for (const fn of fnotes) {
    text = text.replace(
      new RegExp(`(?<=[\\u0600-\\u06FF])${fn.n}(?![0-9])`, "g"),
      `<sup data-fn="${fn.n}" data-sep-page="${parsedPage}">${fn.n}</sup>`,
    );
  }

  const lines = text.split("\n").map((l) => l.replace(/[ \t]+/g, " ").trim()).filter(Boolean);
  const parts: string[] = [];
  let para = "";
  for (const line of lines) {
    if (line.startsWith("#")) {
      if (para) { parts.push(para.trim()); para = ""; }
      parts.push(line);
    } else {
      para += (para ? " " : "") + line;
    }
  }
  if (para) parts.push(para.trim());
  return { md: parts.join("\n\n"), notes: fnotes.map((fn) => fn.t), parsedPage, parsedJuz };
}

// ─────────────────────────────────────────────
// YAML helpers
// ─────────────────────────────────────────────
const y = (s: string) => `"${s.replace(/\\/g, "\\\\").replace(/"/g, '\\"')}"`;

function yList(items: string[]): string {
  // inline YAML list
  return `[${items.map(y).join(", ")}]`;
}

// ─────────────────────────────────────────────
// Richer metadata
// ─────────────────────────────────────────────
interface Meta {
  title: string;
  creator: string;
  edition?: string;
  muhaqqiq?: string;
  publisher?: string;
  volumes?: string;
  died?: string;
  tabaqa?: string;
  qism?: string;        // قسم / subject classification from info.xhtml
  isPoem: boolean;      // detected from markup
  poemByTitle: boolean; // detected from title keywords
  hadithCategory?: string; // inferred in readEpub for genre=حديث books
}

/** Read all info-title → info-desc pairs from info.xhtml */
function readInfoFields(info: string): Record<string, string> {
  const out: Record<string, string> = {};
  // Pattern: <span class="info-title">LABEL:</span><span class="info-desc"> VALUE</span>
  // Both class attr forms: info-title and info-desc (with or without quotes style)
  const re = /<span[^>]*class=["']info-title["'][^>]*>([\s\S]*?)<\/span>\s*<span[^>]*class=["']info-desc["'][^>]*>([\s\S]*?)<\/span>/gi;
  for (const m of info.matchAll(re)) {
    const label = cleanInline(m[1]).replace(/:$/, "").trim();
    const value = cleanInline(m[2]).trim();
    if (label && value && value !== "\u00a0" && value !== "&nbsp;") {
      out[label] = value;
    }
  }
  return out;
}

function readEpub(file: string): { meta: Meta; pages: { id: string; xhtml: string }[] } {
  const dir = mkdtempSync(join(tmpdir(), "epub-"));
  try {
    execFileSync("unzip", ["-o", "-q", file, "-d", dir]);
    const opfPath = findOpf(dir);
    const opf = readFileSync(opfPath, "utf8");
    const opfDir = opfPath.slice(0, opfPath.lastIndexOf("/"));

    const meta: Meta = {
      title: cleanInline(opf.match(/<dc:title[^>]*>([\s\S]*?)<\/dc:title>/i)?.[1] ?? "بدون عنوان"),
      creator: cleanInline(opf.match(/<dc:creator[^>]*>([\s\S]*?)<\/dc:creator>/i)?.[1] ?? ""),
      isPoem: false,
      poemByTitle: false,
    };

    // manifest id → href, spine order
    const manifest = new Map<string, string>();
    for (const m of opf.matchAll(/<item\s+[^>]*id=["']([^"']+)["'][^>]*href=["']([^"']+)["']/gi))
      manifest.set(m[1], m[2]);
    const order = [...opf.matchAll(/<itemref\s+[^>]*idref=["']([^"']+)["']/gi)].map((m) => m[1]);

    // ── Richer metadata from info.xhtml ──
    const infoHref = manifest.get("info");
    if (infoHref) {
      const infoPath = join(opfDir, infoHref);
      if (existsSync(infoPath)) {
        const info = readFileSync(infoPath, "utf8");
        const fields = readInfoFields(info);

        meta.edition   = fields["الطبعة"];
        meta.muhaqqiq  = fields["المحقق"];
        meta.publisher = fields["الناشر"];
        meta.volumes   = fields["عدد الأجزاء"];
        meta.qism      = fields["قسم"] ?? fields["القسم"];

        // author طبقة — if the info page mentions a طبقة / category
        const tabaqaRaw = fields["الطبقة"] ?? fields["طبقة المؤلف"];
        if (tabaqaRaw) meta.tabaqa = tabaqaRaw;

        // died year — try "(المتوفى: NNNهـ)" first, then bare "NNNهـ"
        const diedMatch =
          info.match(/المتوفى[:\s]+(\d{2,4})\s*هـ/u) ??
          info.match(/\((\d{2,4})\s*هـ\)/u);
        if (diedMatch) meta.died = diedMatch[1];
      }
    }

    // Fallback: year from filename (e.g. "0378هـ نونية")
    if (!meta.died) meta.died = (file.match(/(\d{2,4})\s*هـ/) ?? [])[1];

    // Title-keyword poem detection
    meta.poemByTitle = POEM_TITLE_RE.test(meta.title);

    // Load pages (skip info + cover)
    const pages: { id: string; xhtml: string }[] = [];
    for (const id of order) {
      if (id === "info" || /cover/i.test(id)) continue;
      const href = manifest.get(id);
      if (!href) continue;
      const fullPath = join(opfDir, href);
      if (!existsSync(fullPath)) continue;
      pages.push({ id, xhtml: readFileSync(fullPath, "utf8") });
    }

    // ── Verse-ratio poem detection ──
    let totalVerses = 0;
    let totalLines = 0;
    for (const p of pages) {
      totalVerses += countVerses(p.xhtml);
      // count <br /> as approximate line count
      totalLines += (p.xhtml.match(/<br\s*\/?>/gi) ?? []).length + 1;
    }
    if (totalLines > 0 && totalVerses / totalLines >= POEM_VERSE_THRESHOLD) {
      meta.isPoem = true;
    }

    // Infer hadith_category for hadith-genre books
    if (genreFor(file) === "حديث" || (meta.qism && /حديث|سنن/u.test(meta.qism))) {
      meta.hadithCategory = inferHadithCategory(meta, file);
    }

    return { meta, pages };
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

function findOpf(dir: string): string {
  const container = readFileSync(join(dir, "META-INF", "container.xml"), "utf8");
  const rel = container.match(/full-path=["']([^"']+\.opf)["']/i)?.[1];
  if (!rel) throw new Error("no .opf in container.xml");
  return join(dir, rel);
}

// ─────────────────────────────────────────────
// Auto-taxonomy helpers
// ─────────────────────────────────────────────
const FOLDER_SUBJECT_MAP = [
  { pattern: /عقيدة|توحيد|أصول الدين|إيمان/u, subjectSlug: "aqeedah" },
  { pattern: /حديث|سنن|مسانيد|أجزاء|تخريج|مصطلح|رجال|علل|موطأ/u, subjectSlug: "hadith" },
  { pattern: /فقه|أصول الفقه|فرائض/u, subjectSlug: "fiqh" },
  { pattern: /لغة|نحو|صرف|بلاغة|أدب/u, subjectSlug: "lughah" },
  { pattern: /تفسير|قرآن|قراءات|تجويد/u, subjectSlug: "quran" },
  { pattern: /تراجم|طبقات|سير|وفيات/u, subjectSlug: "tarajim" },
  { pattern: /تاريخ/u, subjectSlug: "tarikh" },
  { pattern: /أخلاق|رقائق|زهد/u, subjectSlug: "raqaq" }
];

import { appendFileSync } from "node:fs";

const TOPIC_TITLE_MAP: Record<string, string> = {
  "al-aqeedah-al-aamah": "العقيدة العامة",
  "tahwid-al-ibada": "التوحيد",
  "al-asma-was-sifat": "الأسماء والصفات",
  "al-iman": "الإيمان",
  "al-qadr": "القضاء والقدر",
  "al-samiyyat": "السمعيات",
  "al-imamah-was-sahabah": "الإمامة والصحابة",
  "al-wala-wal-bara": "الولاء والبراء",
  "al-sunnah-wal-bidah": "السنة والبدعة",
  "al-firaq-war-rudud": "الفرق والردود",

  "al-nahw-al-muyassar": "النحو الميسر",
  "tafsir-al-quran": "تفسير القرآن",
  "mustalah-al-hadith": "مصطلح الحديث",
  "fiqh-hanbali": "الفقه الحنبلي",
  "fiqh-maliki": "الفقه المالكي",
  "fiqh-shafii": "الفقه الشافعي",
  "fiqh-hanafi": "الفقه الحنفي",
  "fiqh-muqaran": "الفقه المقارن",
  "usul-al-fiqh": "أصول الفقه",
  "tarajim-al-ulama": "تراجم العلماء",

  "aam-aqeedah": "عقيدة عامة",
  "aam-hadith": "حديث عام",
  "aam-fiqh": "فقه عام",
  "aam-lughah": "لغة عامة",
  "aam-quran": "قرآن عام",
  "aam-tarajim": "تراجم عامة",
  "aam-tarikh": "تاريخ عام",
  "aam-raqaq": "رقائق وأخلاق",
  "aam-other": "عام",
};

const SUBJECT_TITLE_MAP: Record<string, string> = {
  "aqeedah": "العقيدة",
  "hadith": "الحديث",
  "fiqh": "الفقه",
  "lughah": "اللغة العربية",
  "quran": "القرآن الكريم",
  "tarajim": "التراجم والسير",
  "tarikh": "التاريخ",
  "raqaq": "الرقائق والآداب",
  "nahw": "النحو والصرف",
  "other": "أخرى",
};

function resolveTopics(meta: Meta, file: string, contentRoot: string, today: string, dryRun: boolean): string[] {
  const folder = basename(dirname(file));
  const qism = meta.qism ?? "";
  
  // 1. Assign subject stub with high confidence
  let subjectSlug = "other";
  for (const { pattern, subjectSlug: s } of FOLDER_SUBJECT_MAP) {
    if (pattern.test(folder) || pattern.test(qism)) {
      subjectSlug = s;
      break;
    }
  }
  const stubTopic = `aam-${subjectSlug}`;

  // 2. Suggest specific topics to JSON sidecar
  const suggested: string[] = [];
  for (const { pattern, topic } of SECTION_TOPIC_MAP) {
    if (pattern.test(qism) || pattern.test(meta.title)) {
      suggested.push(topic);
    }
  }
  
  if (suggested.length > 0 && !dryRun) {
    const suggestionLine = JSON.stringify({ book: slugify(meta.title), title: meta.title, suggestedTopics: suggested });
    appendFileSync(join(dirname(contentRoot), "../scripts/topic-suggestions.jsonl"), suggestionLine + "\n");
  }

  // Deduplicate and limit to maximum of 5 topics (Astro schema limit)
  const uniqueSuggested = [...new Set(suggested)];
  const topicsToReturn = uniqueSuggested.length > 0 ? uniqueSuggested.slice(0, 5) : [stubTopic];

  // Emit stubs for all resolved topics
  for (const t of topicsToReturn) {
    const entry = SECTION_TOPIC_MAP.find((e) => e.topic === t);
    const subject = entry ? entry.subject : subjectSlug;
    maybeEmitTopicStub(t, subject, contentRoot, today, dryRun);
  }

  return topicsToReturn;
}

function maybeEmitSubjectStub(subjectSlug: string, contentRoot: string, today: string, dryRun: boolean): void {
  const subjectPath = join(contentRoot, "subject", subjectSlug + ".md");
  if (existsSync(subjectPath)) return;
  const title = SUBJECT_TITLE_MAP[subjectSlug] ?? subjectSlug;
  const text = [
    "---",
    `title: ${y(title)}`,
    "status: published",
    `published_at: ${today}`,
    "---",
    "",
  ].join("\n");
  if (!dryRun) writeFileMk(subjectPath, text);
  console.log(`   → subject stub: ${subjectPath}`);
}

/** Emit a topic stub if the slug doesn't yet exist. */
function maybeEmitTopicStub(
  topicSlug: string,
  subjectSlug: string,
  contentRoot: string,
  today: string,
  dryRun: boolean,
): void {
  const topicPath = join(contentRoot, "topic", topicSlug + ".md");
  if (existsSync(topicPath)) return;

  // Make sure the subject stub exists
  maybeEmitSubjectStub(subjectSlug, contentRoot, today, dryRun);

  const title = TOPIC_TITLE_MAP[topicSlug] ?? topicSlug;
  const text = [
    "---",
    `title: ${y(title)}`,
    `subject: ${subjectSlug}`,
    "status: published",
    `published_at: ${today}`,
    "---",
    "",
  ].join("\n");
  if (!dryRun) writeFileMk(topicPath, text);
  console.log(`   → topic stub: ${topicPath}`);
}

// ─────────────────────────────────────────────
// Edition grouping: find an unused slug
// ─────────────────────────────────────────────
function uniqueSlug(base: string, collection: string, contentRoot: string): string {
  const dir = join(contentRoot, collection);
  if (!existsSync(join(dir, base + ".md"))) return base;
  let v = 2;
  while (existsSync(join(dir, `${base}--v${v}.md`))) v++;
  return `${base}--v${v}`;
}

// ─────────────────────────────────────────────
// Detect متن kind
// ─────────────────────────────────────────────
function detectKind(meta: Meta, override?: string): string | undefined {
  if (override) return override;
  if (MATN_TITLE_RE.test(meta.title)) return "متن";
  return undefined;
}

// ─────────────────────────────────────────────
// Build poem markdown body
// ─────────────────────────────────────────────
function buildPoemBody(pages: { id: string; xhtml: string }[]): string {
  const parts: string[] = [];
  for (const p of pages) {
    const { lines, headings } = pageToVerseLines(p.xhtml);
    for (const h of headings) {
      parts.push(h);
    }
    for (const line of lines) {
      if (line.trim()) parts.push(line);
    }
  }
  // Each verse on its own line, headings separated by blank lines
  const out: string[] = [];
  for (const part of parts) {
    if (part.startsWith("##")) {
      out.push(""); // blank line before heading
      out.push(part);
      out.push(""); // blank line after heading
    } else {
      out.push(part);
    }
  }
  return out.join("\n").replace(/\n{3,}/g, "\n\n").trim();
}

// ─────────────────────────────────────────────
// Build prose book markdown body
// ─────────────────────────────────────────────
function buildBookBody(pages: { id: string; xhtml: string }[]): string {
  const bodyParts: string[] = [];
  let pageNum = 0;
  for (const p of pages) {
    pageNum++;
    const { md, notes, parsedPage, parsedJuz } = pageToMd(p.xhtml, String(pageNum));
    if (!md.trim()) { pageNum--; continue; }
    const na = notes.length ? ` data-notes='${JSON.stringify(notes).replace(/'/g, "&#39;")}'` : "";
    const juzAttr = parsedJuz ? ` data-juz="${parsedJuz}"` : "";
    bodyParts.push(`${md}\n\n<hr class="page-sep" data-page="${parsedPage}"${juzAttr}${na} />`);
  }
  return bodyParts.join("\n\n");
}

// Matches Shamela hadith-number span: <span class="red">9 - </span> or <span class="red">9/ 2 - </span>
// ponytail: canonical N is the first number (volume/sequence prefix ignored)
const HADITH_NUM_RE = /<span\s+class=["']red["']>\s*(\d+)(?:\/\s*\d+)?\s*-\s*<\/span>/gi;
// Also matches Muwatta footer: الحديث: N (used in selftest to verify footer anchor)
const HADITH_FOOTER_RE = /الحديث:\s*(\d+)/;

function buildHadithBody(pages: { id: string; xhtml: string }[]): {
  md: string;
  takhrij: { anchor: string; text: string }[];
} {
  const bodyParts: string[] = [];
  const takhrij: { anchor: string; text: string }[] = [];
  let pageNum = 0;
  let lastHadithNum = 0;

  for (const p of pages) {
    pageNum++;
    // Pre-process: replace hadith-number red spans with a placeholder that
    // survives pageToMd's {→﴿ replacement; fixed to {#hN} after.
    const preprocessed = p.xhtml.replace(HADITH_NUM_RE, (_, n) => {
      lastHadithNum = +n;
      return `__HDT${n}__${n} - `;
    });

    // Also extract canonical number from Shamela footer (الحديث: N) as fallback
    const footerMatch = p.xhtml.match(HADITH_FOOTER_RE);
    if (footerMatch && !HADITH_NUM_RE.test(p.xhtml)) {
      lastHadithNum = +footerMatch[1];
    }
    // Reset lastIndex after exec
    HADITH_NUM_RE.lastIndex = 0;

    const { md: rawMd, notes, parsedPage, parsedJuz } = pageToMd(preprocessed, String(pageNum));
    if (!rawMd.trim()) { pageNum--; continue; }
    // Fix placeholder back to {#hN} anchor syntax
    const md = rawMd.replace(/__HDT(\d+)__/g, (_, n) => `{#h${n}}\n\n`);

    // Collect [التخريج] footnotes — tag them to the last seen hadith number
    for (const note of notes) {
      if (/\[التَّخْرِيجُ\]|\[التخريج\]/.test(note) && lastHadithNum > 0) {
        takhrij.push({ anchor: `h${lastHadithNum}`, text: note });
      }
    }

    const na = notes.length ? ` data-notes='${JSON.stringify(notes).replace(/'/g, "&#39;")}'` : "";
    const juzAttr = parsedJuz ? ` data-juz="${parsedJuz}"` : "";
    bodyParts.push(`${md}\n\n<hr class="page-sep" data-page="${parsedPage}"${juzAttr}${na} />`);
  }

  return { md: bodyParts.join("\n\n"), takhrij };
}

// ─────────────────────────────────────────────
// Main build function
// ─────────────────────────────────────────────
interface Opt {
  out: string;
  kind?: string;
  genre?: string;       // --genre override; else inferred from source فن folder
  status: string;
  slug?: string;
  personSlug?: string;
  shahOf?: string;      // --sharh-of <target-slug>
  mergeVolumes: boolean;
  dryRun: boolean;
  today: string;
}

interface BuildResult {
  primary: { path: string; text: string; collection: string };
  person: { path: string; text: string } | null;
  annotations: { path: string; text: string }[];
  topicsEmitted: string[];
}

function build(file: string, opt: Opt): BuildResult {
  const { meta, pages } = readEpub(file);

  const isPoem = meta.isPoem || meta.poemByTitle;
  const collection = isPoem ? "poem" : "book";
  const kind = detectKind(meta, opt.kind);
  const genre = opt.genre ?? genreFor(file);

  const personSlug = opt.personSlug ?? slugify(meta.creator || "unknown");
  const baseSlug   = opt.slug ?? slugify(meta.title);
  const bookSlug   = opt.slug ? baseSlug : uniqueSlug(baseSlug, collection, opt.out);

  // ── Resolve topics ──
  const topics = resolveTopics(meta, file, opt.out, opt.today, opt.dryRun);

  // ── Person frontmatter ──
  const bareName   = stripTashkeel(meta.creator);
  const personPath = join(opt.out, "person", personSlug + ".md");
  let personResult: BuildResult["person"] = null;
  if (!existsSync(personPath)) {
    const personLines = [
      "---",
      `title: ${y(bareName || personSlug)}`,
      `status: ${opt.status}`,
      `published_at: ${opt.today}`,
      meta.died ? `died: ${y(meta.died + "هـ")}` : null,
      meta.creator !== bareName ? `also_known_as: [${y(meta.creator)}]` : null,
      // طبقة if detected
      meta.tabaqa ? `tabaqa: ${y(meta.tabaqa)}` : null,
      "---",
      "",
    ].filter((l) => l !== null).join("\n");
    personResult = { path: personPath, text: personLines };
  }

  // ── Primary content frontmatter ──
  const fm = [
    "---",
    `title: ${y(meta.title)}`,
    `status: ${opt.status}`,
    `published_at: ${opt.today}`,
    `person: ${personSlug}`,
    kind ? `kind: ${kind}` : null,
    genre ? `genre: ${genre}` : null,
    (genre === "حديث" && meta.hadithCategory) ? `hadith_category: ${meta.hadithCategory}` : null,
    topics.length ? `topics: ${yList(topics)}` : null,
    meta.edition  ? `edition: ${y(meta.edition)}`  : null,
    meta.muhaqqiq ? `description: ${y("بتحقيق " + meta.muhaqqiq)}` : null,
    "---",
    "",
  ].filter((l) => l !== null).join("\n");

  // ── Body ──
  let body: string;
  let takhrijStubs: { path: string; text: string }[] = [];
  if (genre === "حديث" && !isPoem) {
    const { md, takhrij } = buildHadithBody(pages);
    body = md;
    takhrijStubs = takhrij.map(({ anchor, text }) => {
      const annSlug = `${bookSlug}--takhrij-${anchor}`;
      const annPath = join(opt.out, "annotation", annSlug + ".md");
      const annText = [
        "---",
        `title: ${y("تخريج " + meta.title + " " + anchor)}`,
        `status: ${opt.status}`,
        `published_at: ${opt.today}`,
        `target_type: book`,
        `target_id: ${bookSlug}`,
        `anchor: ${anchor}`,
        `kind: تخريج`,
        `annotator: ${personSlug}`,
        "---",
        "",
        text,
        "",
      ].join("\n");
      return { path: annPath, text: annText };
    });
  } else {
    body = isPoem ? buildPoemBody(pages) : buildBookBody(pages);
  }

  const primaryPath = join(opt.out, collection, bookSlug + ".md");
  const primaryText = fm + body;

  // ── شرح/حاشية annotation stub ──
  const annotations: BuildResult["annotations"] = [...takhrijStubs];
  if (opt.shahOf) {
    const annSlug = `${opt.shahOf}--sharh-${bookSlug}`;
    const annPath = join(opt.out, "annotation", annSlug + ".md");
    const annText = [
      "---",
      `title: ${y("شرح " + meta.title)}`,
      `status: ${opt.status}`,
      `published_at: ${opt.today}`,
      `target_type: ${collection}`,
      `target_id: ${opt.shahOf}`,
      `anchor: p1`,
      `kind: شرح`,
      `annotator: ${personSlug}`,
      "---",
      "",
    ].join("\n");
    annotations.push({ path: annPath, text: annText });
  }

  return {
    primary: { path: primaryPath, text: primaryText, collection },
    person: personResult,
    annotations,
    topicsEmitted: topics,
  };
}

// ─────────────────────────────────────────────
// Volume merging
// ─────────────────────────────────────────────
function buildMerged(files: string[], opt: Opt): BuildResult {
  // Read all epubs, use first file's meta as canonical
  let mergedMeta: Meta | null = null;
  const allPages: { id: string; xhtml: string }[] = [];

  for (const file of files) {
    const { meta, pages } = readEpub(file);
    if (!mergedMeta) mergedMeta = meta;
    allPages.push(...pages);
  }

  if (!mergedMeta) throw new Error("no epubs found to merge");

  const isPoem = mergedMeta.isPoem || mergedMeta.poemByTitle;
  const collection = isPoem ? "poem" : "book";
  const kind = detectKind(mergedMeta, opt.kind);
  const personSlug = opt.personSlug ?? slugify(mergedMeta.creator || "unknown");
  const baseSlug   = opt.slug ?? slugify(mergedMeta.title);
  const bookSlug   = opt.slug ? baseSlug : uniqueSlug(baseSlug, collection, opt.out);
  const topics     = resolveTopics(mergedMeta, files[0], opt.out, opt.today, opt.dryRun);

  const bareName   = stripTashkeel(mergedMeta.creator);
  const personPath = join(opt.out, "person", personSlug + ".md");
  let personResult: BuildResult["person"] = null;
  if (!existsSync(personPath)) {
    const personLines = [
      "---",
      `title: ${y(bareName || personSlug)}`,
      `status: ${opt.status}`,
      `published_at: ${opt.today}`,
      mergedMeta.died ? `died: ${y(mergedMeta.died + "هـ")}` : null,
      mergedMeta.creator !== bareName ? `also_known_as: [${y(mergedMeta.creator)}]` : null,
      mergedMeta.tabaqa ? `tabaqa: ${y(mergedMeta.tabaqa)}` : null,
      "---",
      "",
    ].filter((l) => l !== null).join("\n");
    personResult = { path: personPath, text: personLines };
  }

  const genre = opt.genre ?? genreFor(files[0]);
  const fm = [
    "---",
    `title: ${y(mergedMeta.title)}`,
    `status: ${opt.status}`,
    `published_at: ${opt.today}`,
    `person: ${personSlug}`,
    kind ? `kind: ${kind}` : null,
    genre ? `genre: ${genre}` : null,
    topics.length ? `topics: ${yList(topics)}` : null,
    mergedMeta.edition  ? `edition: ${y(mergedMeta.edition)}`  : null,
    mergedMeta.muhaqqiq ? `description: ${y("بتحقيق " + mergedMeta.muhaqqiq)}` : null,
    "---",
    "",
  ].filter((l) => l !== null).join("\n");

  let body: string;
  let takhrijStubs: { path: string; text: string }[] = [];
  if (genre === "حديث" && !isPoem) {
    const { md, takhrij } = buildHadithBody(allPages);
    body = md;
    takhrijStubs = takhrij.map(({ anchor, text }) => {
      const annSlug = `${bookSlug}--takhrij-${anchor}`;
      const annPath = join(opt.out, "annotation", annSlug + ".md");
      const annText = [
        "---",
        `title: ${y("تخريج " + mergedMeta!.title + " " + anchor)}`,
        `status: ${opt.status}`,
        `published_at: ${opt.today}`,
        `target_type: book`,
        `target_id: ${bookSlug}`,
        `anchor: ${anchor}`,
        `kind: تخريج`,
        `annotator: ${personSlug}`,
        "---",
        "",
        text,
        "",
      ].join("\n");
      return { path: annPath, text: annText };
    });
  } else {
    body = isPoem ? buildPoemBody(allPages) : buildBookBody(allPages);
  }
  return {
    primary: { path: join(opt.out, collection, bookSlug + ".md"), text: fm + body, collection },
    person: personResult,
    annotations: takhrijStubs,
    topicsEmitted: topics,
  };
}

// ─────────────────────────────────────────────
// File writing
// ─────────────────────────────────────────────
function writeFileMk(path: string, text: string) {
  mkdirSync(path.slice(0, path.lastIndexOf("/")), { recursive: true });
  writeFileSync(path, text);
}

// ─────────────────────────────────────────────
// Self-test
// ─────────────────────────────────────────────
function selftest() {
  const a = (cond: boolean, msg: string) => { if (!cond) throw new Error("selftest: " + msg); };

  // ── slugify ──
  const iman = "الإيمان";
  a(/^[a-z0-9]+(-[a-z0-9]+)*$/.test(slugify(iman)), "slug valid: " + slugify(iman));

  // ── prose pageToMd ──
  // Wraps exactly like real Shamela XHTML: book-container has a nested .center footer,
  // then a closing </div> + <hr/> + </body>.
  const sample =
    `<body><div id="book-container"><hr/><a id='C1'></a>1<span class="title">ـ باب الإيمان</span>` +
    `<br />قال تعالى {إنا} [ال:1] ` +
    `<span class="red">9- </span>حدثي1<span class="footnote-hr">&nbsp;</span>` +
    `<span class="footnote">1 إسناده صحيح</span>` +
    `<div class="center">الجزء: 1 ¦ الصفحة: 3</div>` +
    `</div><hr/></body>`;
  const { md, notes, parsedPage, parsedJuz } = pageToMd(sample, "P1");
  a(md.includes("## باب الإيمان"), "title → H2: " + md);
  a(md.includes("﴿إنا﴾"),          "braces → ornate: " + md);
  a(md.includes("9- "),            "red number kept");
  a(!md.includes("الجزء"),         "footer stripped from prose: " + md);
  a(!md.includes("[^P1_1]"),       "footnote ref stripped from text: " + md);
  a(notes[0] === "إسناده صحيح",   "note text in notes array: " + notes[0]);
  a(parsedJuz === "1",             "parsed juz: " + parsedJuz);
  a(parsedPage === "3",            "parsed page: " + parsedPage);

  // ── poem verse extraction ──
  const versePage =
    `<body><div id="book-container"><hr/>` +
    `<span class="red">1-</span>يا منزل الآيات والفرقان <span class="red">...</span> بيني وبينك حرمة القرآن<br />` +
    `<span class="red">2-</span>اشرح به صدري <span class="red">...</span> واعصم به قلبي من الشيطان<br />` +
    `<div class="center">الجزء: 1 ¦ الصفحة: 17</div>` +
    `</div><hr/></body>`;
  a(countVerses(versePage) === 2, "countVerses=2: " + countVerses(versePage));
  const { lines } = pageToVerseLines(versePage);
  a(lines.length === 2,                          "2 verse lines extracted: " + lines.length);
  a(lines[0].includes(" --- "),                  "صدر --- عجز separator: " + lines[0]);
  a(lines[0].includes("يا منزل الآيات"),         "صدر content correct: " + lines[0]);
  a(lines[0].includes("حرمة القرآن"),            "عجز content correct: " + lines[0]);

  // ── unnumbered poem (نونية ابن القيم style: no leading N- span, heading inline) ──
  const versePageUnnum =
    `<body><div id="book-container"><hr/>` +
    `<span class="title">فصل</span><br />والناس بينهم خلاف هل بها <span class="red">...</span> حبل وفي هذا لهم قولان<br />` +
    `فنفاه طاوس وإبراهيم ثم <span class="red">...</span> مجاهد وهم أولو العرفان<br /></div><hr/></body>`;
  a(countVerses(versePageUnnum) === 2,           "unnumbered countVerses=2: " + countVerses(versePageUnnum));
  const { lines: unnum } = pageToVerseLines(versePageUnnum);
  a(unnum.length === 2,                          "unnumbered: 2 verses extracted: " + unnum.length);
  a(unnum[0] === "والناس بينهم خلاف هل بها --- حبل وفي هذا لهم قولان", "unnumbered verse text (no num/heading leak): " + unnum[0]);

  // ── poem title detection ──
  a(POEM_TITLE_RE.test("نونية القحطاني"), "poem title detection: نونية");
  a(POEM_TITLE_RE.test("ألفية ابن مالك"), "poem title detection: ألفية");
  a(POEM_TITLE_RE.test("اللامية في النحو"), "poem title detection: اللامية");
  a(POEM_TITLE_RE.test("المنظومة البيقونية"), "poem title detection: المنظومة");
  a(POEM_TITLE_RE.test("القصيدة التائية"), "poem title detection: القصيدة");
  a(!POEM_TITLE_RE.test("كتاب العقيدة"), "not a poem title: كتاب العقيدة");
  a(!POEM_TITLE_RE.test("أثر الإيمان في تحصين الأمة الإسلامية"), "not a poem title: الأمة الإسلامية");

  // ── متن detection ──
  a(MATN_TITLE_RE.test("متن الأجرومية"), "matn detection");
  a(MATN_TITLE_RE.test("العقيدة الواسطية"), "matn detection: الواسطية");

  // ── genre from فن folder ──
  a(genreFor("/x/06ـ (199) متون الحديث/k.epub") === "حديث", "genre folder → حديث");
  a(genreFor("/x/01ـ (164) التفاسير/k.epub") === "قرآن", "genre folder → قرآن");
  a(genreFor("/x/13ـ (147) كتب التخريج والزوائد/k.epub") === "حديث", "genre folder → حديث (تخريج)");
  a(genreFor("/x/aqeeda/k.epub") === undefined, "no genre for aqeeda folder");

  // ── info.xhtml field reading ──
  const fakeInfo = `<span class="info-title">الناشر:</span><span class="info-desc"> دار الذكرى</span>` +
    `<span class="info-title">الطبعة:</span><span class="info-desc"> الأولى</span>` +
    `<span class="info-title">عدد الأجزاء:</span><span class="info-desc"> 1</span>`;
  const fields = readInfoFields(fakeInfo);
  a(fields["الناشر"] === "دار الذكرى", "publisher parsed: " + fields["الناشر"]);
  a(fields["الطبعة"] === "الأولى",     "edition parsed: " + fields["الطبعة"]);
  a(fields["عدد الأجزاء"] === "1",     "volumes parsed: " + fields["عدد الأجزاء"]);

  // ── hadith buildHadithBody ──
  // Muwatta-style page: red number span + [التخريج] footnote + Hadith footer
  const hadithPage = [
    `<body><div id="book-container"><hr/>`,
    `<span class="title">كتاب الصلاة</span><br />`,
    `<span class="red">9 - </span>حَدَّثَنِي مَالِكٌ عَنِ ابْنِ شِهَابٍ`,
    `<span class="footnote-hr">&nbsp;</span>`,
    `<span class="footnote">1 [التخريج] أخرجه البخاري (520) ومسلم (607)</span>`,
    `<div class="center">الحديث: 9 ¦ الجزء: 1 ¦ الصفحة: 10</div>`,
    `</div><hr/></body>`,
  ].join("");
  const { md: hmd, takhrij } = buildHadithBody([{ id: "p1", xhtml: hadithPage }]);
  a(hmd.includes("{#h9}"),                    "hadith anchor emitted: " + hmd);
  a(hmd.includes("9 - "),                     "hadith number kept in text: " + hmd);
  a(hmd.includes("## كتاب الصلاة"),           "كتاب title → H2: " + hmd);
  a(!hmd.includes("الحديث:"),                 "hadith footer stripped: " + hmd);
  a(takhrij.length === 1,                     "one تخريج extracted: " + takhrij.length);
  a(takhrij[0].anchor === "h9",               "تخريج anchor: " + takhrij[0].anchor);
  a(takhrij[0].text.includes("البخاري"),      "تخريج text: " + takhrij[0].text);

  // ── uniqueSlug ──
  // (can't fully test without filesystem; just smoke the function)
  const slug1 = uniqueSlug("does-not-exist-xyz", "book", "src/content");
  a(slug1 === "does-not-exist-xyz", "uniqueSlug passthrough: " + slug1);

  // ── footer stripping from poem page ──
  const { lines: verseLines2 } = pageToVerseLines(versePage);
  const joined = verseLines2.join("\n");
  a(!joined.includes("الجزء"), "footer stripped from poem: " + joined);

  // ── hadith_category inference ──
  const fakeMeta = (title: string, creator = "", qism = "") =>
    ({ title, creator, qism, isPoem: false, poemByTitle: false }) as Meta;
  a(inferHadithCategory(fakeMeta("صحيح البخاري"), "/x/حديث/k.epub") === "امهات الكتب", "امهات: البخاري");
  a(inferHadithCategory(fakeMeta("كتاب الزوائد"), "/x/13ـ كتب التخريج والزوائد/k.epub") === "تخريج", "تخريج folder");
  a(inferHadithCategory(fakeMeta("جزء من حديث"), "/x/أجزاء حديثية/k.epub") === "أجزاء حديثية", "أجزاء folder");
  a(inferHadithCategory(fakeMeta("كتاب الصمت", "ابن أبي الدنيا"), "/x/حديث/k.epub") === "كتب الآثار", "آثار: ابن أبي الدنيا");


  console.log("✓ selftest passed (all assertions)");
}

// ─────────────────────────────────────────────
// Main
// ─────────────────────────────────────────────
function main() {
  const argv = process.argv.slice(2);
  if (argv.includes("--selftest")) return selftest();

  const flag = (name: string) => { const i = argv.indexOf(name); return i >= 0 ? argv[i + 1] : undefined; };
  const valued = new Set(["--out", "--kind", "--status", "--slug", "--person-slug", "--sharh-of", "--genre"]);
  const positional = argv.filter((a, i) => !a.startsWith("--") && !valued.has(argv[i - 1]));

  if (!positional.length) {
    console.error(
      "usage: pnpm import:epub <file.epub|dir/> [more…]\n" +
      "  --out <dir>           content root (default: src/content)\n" +
      "  --kind <متن|مرجع|مجموع>  override detected kind\n" +
      "  --status <published>  (default: published)\n" +
      "  --slug <slug>         override slug (single epub only)\n" +
      "  --person-slug <slug>  override author slug (single epub only)\n" +
      "  --sharh-of <slug>     emit annotation stub (شرح) targeting this slug\n" +
      "  --merge-volumes       merge a directory of epubs into one file\n" +
      "  --dry-run             print without writing",
    );
    process.exit(1);
  }

  const opt: Opt = {
    out:          flag("--out") ?? "src/content",
    kind:         flag("--kind"),
    genre:        flag("--genre"),
    status:       flag("--status") ?? "published",
    slug:         flag("--slug"),
    personSlug:   flag("--person-slug"),
    shahOf:       flag("--sharh-of"),
    mergeVolumes: argv.includes("--merge-volumes"),
    dryRun:       argv.includes("--dry-run"),
    today:        new Date().toISOString().slice(0, 10),
  };

  if ((opt.slug || opt.personSlug) && positional.length > 1 && !opt.mergeVolumes) {
    console.error("--slug/--person-slug only make sense with a single epub (or --merge-volumes)");
    process.exit(1);
  }

  // Expand positional paths to .epub files
  const files: string[] = [];
  for (const p of positional) {
    if (!existsSync(p)) { console.error("✗ not found: " + p); continue; }
    if (statSync(p).isDirectory()) {
      files.push(...readdirSync(p).filter((f) => f.endsWith(".epub")).sort().map((f) => join(p, f)));
    } else {
      files.push(p);
    }
  }

  if (!files.length) { console.error("no .epub files found"); process.exit(1); }

  // ── Volume merge mode ──
  if (opt.mergeVolumes) {
    const result = buildMerged(files, opt);
    printResult(result, "merged");
    if (!opt.dryRun) commitResult(result, opt);
    if (!opt.dryRun) console.log("\nNext: pnpm validate:content");
    return;
  }

  // ── Per-file mode ──
  for (const file of files) {
    const result = build(file, opt);
    printResult(result, file);
    if (opt.dryRun) {
      console.log("   [dry-run] " + result.primary.text.split("\n").slice(0, 14).join("\n   "));
      continue;
    }
    commitResult(result, opt);

    // Emit topic stubs for any auto-resolved topics
    for (const t of result.topicsEmitted) {
      // Find which subject this topic belongs to
      const entry = SECTION_TOPIC_MAP.find((e) => e.topic === t);
      if (entry) maybeEmitTopicStub(t, entry.subject, opt.out, opt.today, opt.dryRun);
    }
  }

  if (!opt.dryRun) console.log("\nNext: pnpm validate:content");
}

function printResult(result: BuildResult, label: string) {
  const icon = result.primary.collection === "poem" ? "📜" : "📖";
  console.log([
    `${icon}  ${label}`,
    `   → ${result.primary.path} (${result.primary.text.length} bytes)`,
    result.person ? `   → ${result.person.path} (new author stub)` : `   . author exists — reused`,
    ...result.annotations.map((a) => `   → ${a.path} (annotation stub)`),
  ].filter(Boolean).join("\n"));
}

function commitResult(result: BuildResult, _opt: Opt) {
  if (result.person) writeFileMk(result.person.path, result.person.text);
  for (const ann of result.annotations) writeFileMk(ann.path, ann.text);
  writeFileMk(result.primary.path, result.primary.text);
}

main();
