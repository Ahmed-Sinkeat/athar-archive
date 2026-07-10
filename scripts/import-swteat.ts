#!/usr/bin/env tsx
// swteat_k Telegram export → question/ + audio/ importer.
// Reads a local Telegram Desktop "Export chat history" JSON (result.json),
// pairs each fatwa's question with its answer (audio and/or text), converts
// audio to opus 16kbps mono, and writes draft content files.
//
// Two eras in this channel:
//   - tagged (#رقم-[N]): one message holds السؤال + الجواب, audio attached
//     inline if the answer is spoken. Numbers ~773–1234, near-complete.
//   - legacy (before the first tag): a lone text message ("٦/ السؤال…")
//     immediately followed by an audio_file message. No reliable number —
//     slugged from the question/title text instead.
//
// Usage:  pnpm tsx scripts/import-swteat.ts [flags]
//   --export <dir>     export folder containing result.json (default: the
//                       Downloads path used for this channel)
//   --out <dir>         content root (default: src/content)
//   --audio-out <dir>   converted .opus output dir (default: <out>/../../import-output/swteat-audio)
//   --person <slug>     author slug (default: abu-jafar-al-khalifi)
//   --status <s>        draft|review|published|archived (default: draft)
//   --limit <n>         stop after writing n fatwas (default: unlimited)
//   --skip-convert       skip ffmpeg conversion (just write content, reuse existing .opus files)
//   --dry-run           print what would be written/converted, write nothing
//   --selftest          run built-in assertions and exit

import { existsSync, mkdirSync, readFileSync, writeFileSync, statSync } from "node:fs";
import { join, dirname } from "node:path";
import { execFileSync } from "node:child_process";
import { slugify, uniqueArticleSlug, y } from "./lib/slugify.ts";

// same keyword heuristic as scripts/telegram-import.ts (not imported — that
// module calls main() unconditionally at load time).
const TOPIC_KEYWORDS: Array<{ pattern: RegExp; topic: string }> = [
  { pattern: /إسناد|الأسانيد|رجال الحديث|ضعفه|ضعيف الحديث|وثقه|جرح وتعديل|علل الحديث|تخريج/u, topic: "mustalah-al-hadith" },
  { pattern: /تفسير|تأويل الآي|الحروف المقطعة|علوم القرآن|القراءات/u, topic: "tafsir-al-quran" },
  { pattern: /إلحاد|ملحد|مادي(?:ة|ين)?|علماني|ليبرالي|تنويري|شيوعي/u, topic: "al-firaq-war-rudud" },
  { pattern: /توحيد|شرك بالله|إخلاص العبادة/u, topic: "tahwid-al-ibada" },
  { pattern: /أسماء الله|الصفات الإلهية|الاستواء|العلو لله/u, topic: "al-asma-was-sifat" },
  { pattern: /تزكية النفس|الزهد|الوحشة مع النفس|إصلاح القلب/u, topic: "tazkiyat-al-nafs" },
  { pattern: /شعب الإيمان|حقيقة الإيمان|النفاق/u, topic: "al-iman" },
  { pattern: /القضاء والقدر|الاحتجاج بالقدر/u, topic: "al-qadr" },
  { pattern: /فضائل الصحابة|آل البيت|أمهات المؤمنين/u, topic: "al-imamah-was-sahabah" },
  { pattern: /الولاء والبراء|موالاة الكفار/u, topic: "al-wala-wal-bara" },
  { pattern: /البدعة|البدع|الاعتصام بالسنة/u, topic: "al-sunnah-wal-bidah" },
  { pattern: /آداب طلب العلم|طالب العلم/u, topic: "adab-talab-al-ilm" },
];
function classifyTopics(text: string): string[] {
  const hits: string[] = [];
  for (const { pattern, topic } of TOPIC_KEYWORDS) {
    if (hits.length >= 3) break;
    if (pattern.test(text)) hits.push(topic);
  }
  return hits;
}

const AR_DIGITS = "٠١٢٣٤٥٦٧٨٩";
const toAsciiDigits = (s: string) => s.replace(/[٠-٩]/g, (d) => String(AR_DIGITS.indexOf(d)));

const TAG_RE = /#رقم\s*[-ـ]?\s*\[?\s*([0-9]+)\s*\]?/;
const CHROME_RE = /^\s*\[[^\]]*\]\s*\n+https?:\/\/t\.me\/\S+\s*\n+/; // "[ أسئلة البوت … ]\n\nhttps://t.me/…\n\n"

interface TgMessage {
  id: number;
  date: string;
  type: string;
  text?: string | Array<string | { text: string }>;
  media_type?: string;
  file?: string;
  title?: string;
  duration_seconds?: number;
}

function textOf(m: TgMessage): string {
  const t = m.text;
  if (Array.isArray(t)) return t.map((x) => (typeof x === "string" ? x : x.text)).join("");
  return t ?? "";
}

interface Fatwa {
  number: number | null;   // null for legacy-era, title-keyed entries
  question: string;
  answer: string | null;   // written answer text, if the answer has no audio
  audioFile: string | null; // relative "files/…" path
  audioTitle: string | null;
  durationSeconds: number | null;
  dateIso: string;
  fallbackTitle: string;   // used for slug when no number
}

// ── tagged-era extraction ──────────────────────────────────────────────
function extractTagged(msgs: TgMessage[]): Fatwa[] {
  const byNumber = new Map<number, Fatwa>();
  for (const m of msgs) {
    const raw = textOf(m);
    const ascii = toAsciiDigits(raw);
    const tagMatch = ascii.match(TAG_RE);
    if (!tagMatch) continue;
    const number = +tagMatch[1];

    const body = raw.replace(CHROME_RE, "");
    const qMatch = body.match(/السؤال\s*(?:#رقم[^\n:]*)?\s*:?\s*\n*/);
    const aMatch = body.match(/\n\s*الجواب\s*:?/);
    let question = body;
    let answer: string | null = null;
    if (qMatch && aMatch && aMatch.index! > (qMatch.index! + qMatch[0].length)) {
      question = body.slice(qMatch.index! + qMatch[0].length, aMatch.index!).trim();
      answer = body.slice(aMatch.index! + aMatch[0].length).trim();
    } else if (qMatch) {
      question = body.slice(qMatch.index! + qMatch[0].length).trim();
    }
    // a second "السؤال :" sub-header sometimes repeats right after the tag — drop it
    question = question.replace(/^\s*السؤال\s*:?\s*\n*/, "").trim();
    if (answer && answer.length < 3) answer = null; // "👇" / ":" placeholder, not real content

    const hasAudio = m.media_type === "audio_file";
    const existing = byNumber.get(number);
    // prefer the occurrence that carries the audio answer over a bare repost
    if (existing && !hasAudio && existing.audioFile) continue;

    byNumber.set(number, {
      number,
      question: question || "(سؤال بلا نص)",
      answer: hasAudio ? null : answer,
      audioFile: hasAudio ? (m.file ?? null) : null,
      audioTitle: hasAudio ? (m.title ?? null) : null,
      durationSeconds: hasAudio ? (m.duration_seconds ?? null) : null,
      dateIso: m.date,
      fallbackTitle: `مسألة رقم ${number}`,
    });
  }
  return [...byNumber.values()].sort((a, b) => a.number! - b.number!);
}

// ── legacy-era extraction: lone text msg immediately followed by audio ──
function extractLegacy(msgs: TgMessage[], firstTaggedId: number): Fatwa[] {
  const era = msgs.filter((m) => m.id < firstTaggedId);
  const out: Fatwa[] = [];
  const used = new Set<number>();
  for (let i = 0; i < era.length; i++) {
    const m = era[i];
    if (m.media_type !== "audio_file" || used.has(m.id)) continue;
    const prev = era[i - 1];
    const hasQuestionText = prev && !prev.media_type && textOf(prev).trim();
    const question = hasQuestionText
      ? textOf(prev).replace(/^\s*[0-9٠-٩]{1,4}\s*[-/\\)\.،:]\s*/, "").trim()
      : (m.title ?? "").trim();
    if (!question) continue; // no usable title or question text — skip
    if (hasQuestionText) used.add(prev.id);
    out.push({
      number: null,
      question,
      answer: null,
      audioFile: m.file ?? null,
      audioTitle: m.title ?? null,
      durationSeconds: m.duration_seconds ?? null,
      dateIso: m.date,
      fallbackTitle: question.slice(0, 80),
    });
  }
  return out;
}

// ── ffmpeg conversion ──────────────────────────────────────────────────
function convertToOpus(srcPath: string, dstPath: string): void {
  mkdirSync(dirname(dstPath), { recursive: true });
  execFileSync("ffmpeg", [
    "-y", "-loglevel", "error", "-i", srcPath,
    "-ac", "1", "-c:a", "libopus", "-b:a", "16k", "-vbr", "on",
    dstPath,
  ], { stdio: "inherit" });
}

function secondsToClock(s: number): string {
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = Math.round(s % 60);
  const mm = String(m).padStart(2, "0"), ss = String(sec).padStart(2, "0");
  return h > 0 ? `${h}:${mm}:${ss}` : `${m}:${ss}`;
}

// ── content file builders ────────────────────────────────────────────
interface Opt {
  out: string; audioOut: string; person: string; status: string;
  limit?: number; skipConvert: boolean; dryRun: boolean; publicAudioBase: string;
}

function buildQuestion(f: Fatwa, opt: Opt, audioSlug: string | null): { path: string; text: string } {
  const title = f.number ? f.fallbackTitle : f.question.split(/[.\n؟!]/)[0].slice(0, 90).trim() || f.fallbackTitle;
  // slugify() only transliterates Arabic — an already-ASCII "swteat-900" would
  // map every character to "" and fall back to a random slug, so skip it here.
  const base = f.number ? `swteat-${f.number}` : slugify(title, "swteat");
  const slug = uniqueArticleSlug(base, join(opt.out, "question"));
  const publishedAt = f.dateIso ? f.dateIso.slice(0, 10) : new Date().toISOString().slice(0, 10);
  const topics = classifyTopics(`${f.question}\n${f.answer ?? ""}`);
  const fm = [
    "---",
    `title: ${y(title)}`,
    `status: ${opt.status}`,
    `published_at: ${publishedAt}`,
    `person: ${opt.person}`,
    `topics: [${(topics.length ? topics : ["aam-other"]).map(y).join(", ")}]`,
    audioSlug ? `audio: ${audioSlug}` : null,
    "---",
    "",
  ].filter((l): l is string => l !== null).join("\n");
  const body = `## السؤال\n\n${f.question}\n\n## الجواب\n\n${f.answer ?? "(انظر التسجيل الصوتي أعلاه)"}\n`;
  return { path: join(opt.out, "question", slug + ".md"), text: fm + body };
}

function buildAudio(f: Fatwa, opt: Opt, questionSlug: string, sizeBytes: number | null): { path: string; text: string; slug: string } {
  const slug = "swteat--" + questionSlug;
  const url = `${opt.publicAudioBase}/${slug}.opus`;
  const fm = [
    "---",
    `title: ${y(f.audioTitle || f.fallbackTitle)}`,
    "status: " + opt.status,
    `published_at: ${f.dateIso.slice(0, 10)}`,
    `source_type: question`,
    `source_id: ${questionSlug}`,
    `url: ${y(url)}`,
    "format: opus",
    f.durationSeconds ? `duration: ${y(secondsToClock(f.durationSeconds))}` : null,
    sizeBytes ? `size_bytes: ${sizeBytes}` : null,
    "---",
    "",
  ].filter((l): l is string => l !== null).join("\n");
  return { path: join(opt.out, "audio", slug + ".md"), text: fm, slug };
}

// ── self-test ────────────────────────────────────────────────────────
function selftest() {
  const a = (cond: boolean, msg: string) => { if (!cond) throw new Error("selftest: " + msg); };

  const tagged = extractTagged([
    {
      id: 1, date: "2024-01-01T00:00:00", type: "message", media_type: "audio_file",
      file: "files/x.m4a", title: "جواب سؤال تجريبي", duration_seconds: 90,
      text: "[ أسئلة البوت ]\n\nhttps://t.me/swteat_k\n\nالسؤال #رقم-[900]  : \n\nهل هذا سؤال تجريبي؟\n\nالجواب :",
    },
    {
      id: 2, date: "2024-01-02T00:00:00", type: "message",
      text: "[ أسئلة البوت ]\n\nhttps://t.me/swteat_k\n\nالسؤال #رقم-[901]  : \n\nسؤال آخر؟\n\nالجواب : انظر المقال هنا.",
    },
  ]);
  a(tagged.length === 2, "extracted 2 tagged fatwas: " + tagged.length);
  a(tagged[0].number === 900, "number parsed: " + tagged[0].number);
  a(tagged[0].question === "هل هذا سؤال تجريبي؟", "question extracted: " + JSON.stringify(tagged[0].question));
  a(tagged[0].audioFile === "files/x.m4a", "audio file captured");
  a(tagged[0].answer === null, "audio answer leaves no written answer text");
  a(tagged[1].answer === "انظر المقال هنا.", "written answer captured: " + JSON.stringify(tagged[1].answer));
  a(tagged[1].audioFile === null, "text-only fatwa has no audio");

  const legacy = extractLegacy([
    { id: 10, date: "2020-01-01T00:00:00", type: "message", text: "٦/ كيف أطلب الفقه؟" },
    { id: 11, date: "2020-01-01T00:05:00", type: "message", media_type: "audio_file", file: "files/y.m4a", title: "كيف أطلب الفقه" },
    { id: 12, date: "2020-01-02T00:00:00", type: "message", media_type: "audio_file", file: "files/z.m4a", title: "سؤال بلا نص سابق" },
  ], 950);
  a(legacy.length === 2, "extracted 2 legacy fatwas: " + legacy.length);
  a(legacy[0].question === "كيف أطلب الفقه؟", "legacy question stripped of leading number: " + JSON.stringify(legacy[0].question));
  a(legacy[0].number === null, "legacy fatwa has no number");
  a(legacy[1].question === "سؤال بلا نص سابق", "legacy fatwa falls back to audio title");

  console.log("✓ selftest passed (all assertions)");
}

// ── main ────────────────────────────────────────────────────────────
async function main() {
  const argv = process.argv.slice(2);
  if (argv.includes("--selftest")) return selftest();

  const flag = (name: string) => { const i = argv.indexOf(name); return i >= 0 ? argv[i + 1] : undefined; };
  const opt: Opt = {
    out: flag("--out") ?? "src/content",
    audioOut: flag("--audio-out") ?? "import-output/swteat-audio",
    person: flag("--person") ?? "abu-jafar-al-khalifi",
    status: flag("--status") ?? "draft",
    limit: flag("--limit") ? +flag("--limit")! : undefined,
    skipConvert: argv.includes("--skip-convert"),
    dryRun: argv.includes("--dry-run"),
    publicAudioBase: "https://r2.arthurarchive.com/audio",
  };
  const exportDir = flag("--export") ?? "/home/sinkeat/Downloads/Telegram Desktop/ChatExport_2026-07-09";
  const resultPath = join(exportDir, "result.json");
  if (!existsSync(resultPath)) {
    console.error(`no result.json at ${resultPath} — pass --export <dir>`);
    process.exit(1);
  }

  const data = JSON.parse(readFileSync(resultPath, "utf8"));
  const msgs: TgMessage[] = data.messages.filter((m: TgMessage) => m.type === "message");
  const asciiText = (m: TgMessage) => toAsciiDigits(textOf(m));
  const firstTaggedId = Math.min(...msgs.filter((m) => TAG_RE.test(asciiText(m))).map((m) => m.id));

  const tagged = extractTagged(msgs);
  const legacy = extractLegacy(msgs, firstTaggedId);
  let all = [...legacy, ...tagged];
  if (opt.limit) all = all.slice(0, opt.limit);

  console.log(`${tagged.length} tagged fatwas (#${Math.min(...tagged.map(f=>f.number!))}–#${Math.max(...tagged.map(f=>f.number!))}), ${legacy.length} legacy fatwas → writing ${all.length}`);

  let written = 0, converted = 0, skippedNoQ = 0;
  for (const f of all) {
    if (!f.question || f.question === "(سؤال بلا نص)" && !f.audioFile) { skippedNoQ++; continue; }

    const qFile = buildQuestion(f, opt, null);
    let audioSlug: string | null = null;
    let audioFileBlock: { path: string; text: string; slug: string } | null = null;

    if (f.audioFile) {
      const questionSlug = qFile.path.split("/").pop()!.replace(/\.md$/, "");
      const srcPath = join(exportDir, f.audioFile);
      const dstPath = join(opt.audioOut, `swteat--${questionSlug}.opus`);
      let sizeBytes: number | null = null;
      if (!opt.dryRun && !opt.skipConvert && existsSync(srcPath)) {
        try {
          convertToOpus(srcPath, dstPath);
          sizeBytes = existsSync(dstPath) ? statSync(dstPath).size : null;
          converted++;
        } catch (err) {
          console.warn(`⚠ ffmpeg failed for ${f.audioFile}: ${(err as Error).message}`);
        }
      }
      audioFileBlock = buildAudio(f, opt, questionSlug, sizeBytes);
      audioSlug = audioFileBlock.slug;
    }

    const finalQ = buildQuestion(f, opt, audioSlug);
    console.log(`📄 ${finalQ.path}${f.audioFile ? "  🔊" : ""}`);
    if (opt.dryRun) { written++; continue; }

    mkdirSync(dirname(finalQ.path), { recursive: true });
    writeFileSync(finalQ.path, finalQ.text);
    if (audioFileBlock) {
      mkdirSync(dirname(audioFileBlock.path), { recursive: true });
      writeFileSync(audioFileBlock.path, audioFileBlock.text);
    }
    written++;
  }

  console.log(`\n${written} question(s) written, ${converted} audio file(s) converted to opus, ${skippedNoQ} skipped (no usable content).`);
  if (!opt.dryRun) {
    console.log(`\nConverted audio sits in ${opt.audioOut}/ — upload it to R2 at ${opt.publicAudioBase}/ before flipping status to published.`);
    console.log(`Next: pnpm validate:content, then review drafts.`);
  }
}

main();
