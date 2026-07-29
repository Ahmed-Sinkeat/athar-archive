// One-off extractor: pulls سؤال/جواب units out of the already-compiled
// src/content/book-lg/majmu-al-fatawa.md (37-volume Majmu' al-Fatawa) into
// individual src/content/question/*.md entries, as draft (unpublished until
// reviewed — the source formatting is inconsistent enough that auto-picked
// titles/topics need a human pass before going live). The book file itself
// is read-only here — never modified.
//
// Boundary heuristic (validated against samples across the file):
//   line-start "سئل" / "وسئل"  -> start of a question
//   line-start "فأجاب" / "الجواب[:]" -> start of the answer
//   next question-start, or a markdown heading, or EOF -> end of the answer
// Mid-paragraph mentions of سئل (e.g. "وسئل مالك عن رجل نذر...", a citation
// inside a treatise, not a top-level fatwa) are correctly excluded because
// they aren't at line-start.
//
// Known imprecision (source is genuinely inconsistent, this is best-effort):
// - an answer can bleed into unrelated trailing prose if the next boundary
//   (سئل/heading) is pages away with narrative text in between.
// - footnote defs ([^fnN]:) are kept only within their own entry's line
//   range; a reference split from its definition across entries will dangle.
// - topic tagging is keyword-based, not semantic.
// Run: pnpm exec tsx scripts/extract-majmu-fatawa-qa.ts [--limit N]

import * as fs from "fs";
import * as path from "path";
import { slugify } from "./lib/slugify";

const SRC = "src/content/book-lg/majmu-al-fatawa.md";
const OUT_DIR = "src/content/question";
const PERSON = "ibn-taymiyyah";
const TODAY = new Date().toISOString().slice(0, 10);

const limitArg = process.argv.indexOf("--limit");
const LIMIT = limitArg >= 0 ? Number(process.argv[limitArg + 1]) : Infinity;

const strip = (s: string) => s.replace(/\p{Mn}/gu, "");

const QUESTION_RE = /^(?:و)?سُ?ئ?ل/; // matched against stripped text below (see isQuestionStart)
const HEADING_RE = /^#{1,6}\s/;

// note: no \b — JS regex word-boundary is ASCII-\w-only and never fires
// between two Arabic letters, so it would silently reject every match here.
function isQuestionStart(stripped: string): boolean {
  return /^و?سئل(\s|:|$)/.test(stripped);
}
function isAnswerStart(stripped: string): boolean {
  return /^فأجاب(\s|:|$)/.test(stripped) || /^الجواب\s*:?\s*$/.test(stripped) || /^الجواب\s*:/.test(stripped);
}
// text after the marker's last colon on the same line, if any (some lines
// are "وسئل:" alone, others are "الجواب: الحمد لله..." with content inline)
// FIRST colon, not last: the marker word (وسئل/فأجاب/الجواب) is immediately
// followed by its own ":" at the start of the line. Arabic prose routinely
// has more colons later in the same line (quotes, clause introductions —
// "ومعناه: ..."، "وما يروى: ..."). lastIndexOf() silently discarded
// everything back to the LAST one, truncating real answers down to a
// trailing fragment (caught via a handful of suspiciously short answers).
function afterColon(line: string): string {
  const i = line.indexOf(":");
  if (i < 0) return "";
  const rest = line.slice(i + 1).replace(/\(\*\)/g, "").trim();
  return rest;
}

const raw = fs.readFileSync(SRC, "utf-8");
const lines = raw.split("\n");

type Entry = { question: string[]; answer: string[]; vol: string; page: string; heading: string };
const entries: Entry[] = [];

let mode: "idle" | "question" | "answer" = "idle";
let qBuf: string[] = [];
let aBuf: string[] = [];
let heading = "";
let vol = "", page = "";
let qVol = "", qPage = "";
let stats = { questionMarkers: 0, answerMarkers: 0, skippedUnanswered: 0, skippedEmpty: 0 };

function flush() {
  const q = qBuf.join("\n").trim();
  const a = aBuf.join("\n").trim();
  if (mode === "answer" && q.length >= 5 && a.length >= 5) {
    entries.push({ question: [q], answer: [a], vol: qVol, page: qPage, heading });
  } else if (mode === "question") {
    stats.skippedUnanswered++;
  } else if (mode === "answer") {
    stats.skippedEmpty++;
  }
  qBuf = [];
  aBuf = [];
}

for (const line of lines) {
  const hrMatch = line.match(/<hr class="page-sep" data-page="(\d+)" data-vol="(\d+)"/);
  if (hrMatch) {
    page = hrMatch[1];
    vol = hrMatch[2];
  }
  const stripped = strip(line);

  if (HEADING_RE.test(line)) {
    const headingText = line.replace(/^#{1,6}\s*/, "");
    // a real heading can never start with a bare combining diacritic — that
    // only happens when Pandoc mis-splits a bolded run (typically a "الحمد
    // لله" answer-opening) mid-word, stranding the trailing diacritic+rest
    // of the sentence as a fake "heading". Fold it back into the current
    // buffer instead of treating it as a section boundary.
    if (/^\p{Mn}/u.test(headingText) && (mode === "question" || mode === "answer")) {
      const text = headingText.replace(/^\p{Mn}+/u, "").trim();
      if (mode === "question") qBuf.push(text);
      else aBuf.push(text);
      continue;
    }
    flush();
    mode = "idle";
    heading = headingText.trim();
    continue;
  }
  if (isQuestionStart(stripped)) {
    flush();
    stats.questionMarkers++;
    mode = "question";
    qVol = vol;
    qPage = page;
    const rest = afterColon(line);
    qBuf = rest ? [rest] : [];
    continue;
  }
  if (mode === "question" && isAnswerStart(stripped)) {
    stats.answerMarkers++;
    mode = "answer";
    const rest = afterColon(line);
    aBuf = rest ? [rest] : [];
    continue;
  }
  if (mode === "question") qBuf.push(line);
  else if (mode === "answer") aBuf.push(line);
}
flush();

console.log(`markers: ${stats.questionMarkers} سئل, ${stats.answerMarkers} فأجاب/الجواب`);
console.log(`entries extracted: ${entries.length}; skipped unanswered: ${stats.skippedUnanswered}; skipped empty: ${stats.skippedEmpty}`);

// --- topic keyword map (best-effort, draft status lets a human refine) ---
const TOPIC_KEYWORDS: Record<string, string[]> = {
  "tahwid-al-ibada": ["التوحيد", "الشرك", "الاستغاثة", "التوسل", "يدعى من دون الله", "عبادة غير الله"],
  "al-asma-was-sifat": ["أسماء الله", "صفات الله", "الاستواء", "النزول", "العلو", "اليد", "الوجه", "السمع والبصر", "صفة"],
  "al-iman": ["الإيمان", "الكفر", "الفسق", "مرتكب الكبيرة", "الإيمان يزيد"],
  "al-qadr": ["القدر", "القضاء والقدر", "الجبر والاختيار"],
  "al-firaq-war-rudud": ["الجهمية", "المعتزلة", "الرافضة", "القدرية", "الاتحادية", "الفلاسفة", "النصارى", "اليهود", "الملاحدة"],
  "al-imamah-was-sahabah": ["الصحابة", "الخلافة", "الإمامة", "علي بن أبي طالب", "معاوية"],
  "al-samiyyat": ["القبر", "عذاب القبر", "الجنة والنار", "الشفاعة", "يوم القيامة", "الصراط", "الميزان"],
  "al-wala-wal-bara": ["الولاء والبراء", "موالاة الكفار", "التشبه بالكفار"],
  "al-sunnah-wal-bidah": ["السنة والجماعة", "البدعة", "أهل السنة"],
  "al-riqaq-wal-adhkar": ["الذكر", "الدعاء", "الرقائق", "الزهد"],
  "tazkiyat-al-nafs": ["تزكية النفس", "الإخلاص", "الرياء", "القلب"],
  "fiqh-hanbali": ["الصلاة", "الزكاة", "الصيام", "الحج", "الطلاق", "النكاح", "البيع", "الطهارة", "الأذان", "الصوم"],
  "al-qawaid-al-fiqhiyyah": ["القواعد الفقهية"],
  "usul-al-fiqh": ["القياس", "الإجماع", "الاجتهاد", "التقليد"],
  "al-siyasah-al-shariyyah": ["الحسبة", "الأمر بالمعروف", "السياسة الشرعية", "الجهاد", "الخراج"],
  "tafsir-al-quran": ["تفسير قوله", "سورة", "معنى الآية"],
  "tarajim-al-ulama": ["ترجمة", "وفاة", "مولد"],
};
function topicsFor(text: string): string[] {
  const s = strip(text);
  const hits: [string, number][] = [];
  for (const [topic, kws] of Object.entries(TOPIC_KEYWORDS)) {
    let n = 0;
    for (const kw of kws) if (s.includes(kw)) n++;
    if (n > 0) hits.push([topic, n]);
  }
  hits.sort((a, b) => b[1] - a[1]);
  const picked = hits.slice(0, 2).map(([t]) => t);
  return picked.length ? picked : ["al-aqeedah-al-aamah"];
}

function titleFor(question: string): string {
  const flat = question.replace(/\s+/g, " ").trim();
  const qMarkIdx = flat.indexOf("؟");
  let title = qMarkIdx > 0 && qMarkIdx < 400 ? flat.slice(0, qMarkIdx + 1) : flat.slice(0, 90) + "…";
  if (title.length > 200) title = title.slice(0, 200).trim() + "…";
  return title.trim();
}

const usedSlugs = new Set<string>();
function uniqueSlug(base: string): string {
  let s = base;
  let v = 2;
  while (usedSlugs.has(s) || fs.existsSync(path.join(OUT_DIR, `${s}.md`))) {
    s = `${base}--v${v}`;
    v++;
  }
  usedSlugs.add(s);
  return s;
}

const y = (s: string) => `"${s.replace(/\\/g, "\\\\").replace(/"/g, '\\"').replace(/\r?\n/g, "\\n")}"`;

let written = 0;
for (const e of entries.slice(0, LIMIT)) {
  const question = e.question[0];
  const answer = e.answer[0];
  const title = titleFor(question);
  const topics = topicsFor(question + " " + answer);
  const slug = uniqueSlug(slugify(title || question, "masalah"));
  const citation = e.vol && e.page ? `\n\n---\n*مصدر: مجموع الفتاوى، المجلد ${e.vol}، ص ${e.page}*\n` : "\n";
  const fm = [
    `title: ${y(title)}`,
    `status: draft`,
    `published_at: ${TODAY}`,
    `person: ${PERSON}`,
    `topics: [${topics.map((t) => `"${t}"`).join(", ")}]`,
  ].join("\n");
  const body = `## السؤال\n\n${question}\n\n## الجواب\n\n${answer}${citation}`;
  fs.writeFileSync(path.join(OUT_DIR, `${slug}.md`), `---\n${fm}\n---\n\n${body}`);
  written++;
}

console.log(`written: ${written} question files -> ${OUT_DIR}/ (status: draft)`);
fs.writeFileSync(
  "scripts/.majmu-fatawa-extraction-report.json",
  JSON.stringify({ ...stats, written }, null, 2),
);
