#!/usr/bin/env tsx
// Telegram public channel → article/ importer.
// Scrapes t.me/s/<channel> (public HTML preview, no login/bot token needed),
// paging backward via ?before=<id>. This channel's posts follow a fixed
// template: every article opens with a du'a formula and closes with another,
// so we split/merge on those instead of guessing paragraph boundaries.
//
// Dedup: self-reposts carry Telegram's own "Forwarded from" marker + a link
// back to the original post id in the same channel — skip forwards outright
// rather than fuzzy-matching text.
//
// Usage:  pnpm import:telegram <channel> [flags]
//   --out <dir>       content root (default: src/content)
//   --person <slug>   author slug (default: abu-jafar-al-khalifi)
//   --status <s>      draft|review|published|archived (default: draft — review before publish)
//   --limit <n>       write at most n articles (default: unlimited — walks full history)
//   --since <id>      only import posts newer than this post id (incremental re-runs)
//   --dry-run         print what would be written, write nothing
//   --selftest        run built-in assertions and exit

import { writeFileSync, mkdirSync } from "node:fs";
import { join, dirname } from "node:path";
import { slugify, uniqueArticleSlug, y } from "./lib/slugify.ts";
import { normText, findByBody, loadArticleIndex, type ArticleIndexEntry } from "./lib/dedup.ts";

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
const escapeRe = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

// ponytail: assumes message text has no nested <div> (true for this channel's
// plain-text template) — a manual depth scan since we don't have an HTML parser dep.
function extractBalancedDiv(html: string, openMarker: RegExp): string | null {
  const m = html.match(openMarker);
  if (!m || m.index === undefined) return null;
  let i = m.index + m[0].length;
  let depth = 1;
  while (depth > 0 && i < html.length) {
    if (html.slice(i, i + 4) === "<div") { depth++; i += 4; }
    else if (html.slice(i, i + 6) === "</div>") { depth--; if (depth === 0) break; i += 6; }
    else i++;
  }
  return depth === 0 ? html.slice(m.index + m[0].length, i) : null;
}

/** Message HTML → plain text: decode entities, <br><br> → paragraph break,
 *  links → markdown (except the bare channel self-link, which is chrome). */
function toText(inner: string, channel: string): string {
  const selfUrl = `https://t.me/${channel}`;
  let s = inner.replace(/<a\s+href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/gi, (_m, href, label) => {
    const cleanLabel = decode(stripTags(label)).trim();
    const cleanHref = decode(href);
    if (cleanHref === selfUrl && cleanLabel === selfUrl) return "";
    return `[${cleanLabel}](${cleanHref})`;
  });
  s = s.replace(/(?:<br\s*\/?>\s*){2,}/gi, "\n\n").replace(/<br\s*\/?>/gi, "\n");
  s = decode(stripTags(s));
  s = s.split("\n").map((l) => l.replace(/[ \t]+/g, " ").trim()).join("\n");
  return s.replace(/\n{3,}/g, "\n\n").trim();
}

// ─────────────────────────────────────────────
// Fixed per-article template markers (verified against t.me/s/Abdullah_Alkulify)
// ─────────────────────────────────────────────
const OPENER = "الحمد لله والصلاة والسلام على رسول الله وعلى آله وصحبه ومن والاه";
const CLOSER = "هذا وصل اللهم على محمد وعلى آله وصحبه وسلم";
const AFTER_OPENER_RE = new RegExp("^" + escapeRe(OPENER) + "\\s*\\n*\\s*(?:أما بعد\\s*:?\\s*\\n*)?");

// ─────────────────────────────────────────────
// Parse t.me/s/<channel> HTML → per-post records
// ─────────────────────────────────────────────
interface RawPost { id: number; dateIso: string; isForward: boolean; text: string }

function parseMessages(html: string, channel: string): RawPost[] {
  const blocks = html.split(/(?=<div class="tgme_widget_message_wrap)/);
  const idRe = new RegExp(`data-post="${channel}\\/(\\d+)"`);
  const posts: RawPost[] = [];
  for (const b of blocks) {
    const idMatch = b.match(idRe);
    if (!idMatch) continue;
    const dateMatch = b.match(/<time\s+datetime="([^"]+)"/);
    const isForward = /class="tgme_widget_message_forwarded_from\b/.test(b);
    const inner = extractBalancedDiv(b, /<div class="tgme_widget_message_text js-message_text"[^>]*>/);
    posts.push({
      id: +idMatch[1],
      dateIso: dateMatch?.[1] ?? "",
      isForward,
      text: inner ? toText(inner, channel) : "",
    });
  }
  return posts;
}

// ─────────────────────────────────────────────
// Group posts into articles via OPENER/CLOSER, skip forwards + standalone noise
// ─────────────────────────────────────────────
interface ArticleDraft { title: string; dateIso: string; postIds: number[]; body: string }

function groupArticles(posts: RawPost[], warnings: string[]): ArticleDraft[] {
  const sorted = posts.filter((p) => !p.isForward).sort((a, b) => a.id - b.id);
  const articles: ArticleDraft[] = [];
  let cur: { titlePart: string; dateIso: string; postIds: number[]; parts: string[] } | null = null;

  const closeCur = () => {
    if (!cur) return;
    const last = cur.parts.length - 1;
    if (last >= 0) cur.parts[last] = cur.parts[last].trim(); // closer kept in body
    let title = cur.titlePart;
    if (!title) {
      // opener has no separate title line — derive a short one, stripping the
      // opener/"أما بعد" chrome for THIS purpose only (body keeps them).
      const afterOpener = (cur.parts[0] ?? "").replace(AFTER_OPENER_RE, "").trim();
      title = afterOpener.slice(0, 80).split(/[.\n؟!]/)[0].trim() || `منشور ${cur.postIds[0]}`;
    }
    articles.push({
      title,
      dateIso: cur.dateIso,
      postIds: [...cur.postIds],
      body: cur.parts.join("\n\n").replace(/\n{3,}/g, "\n\n").trim(),
    });
    cur = null;
  };

  for (const p of sorted) {
    const opens = p.text.includes(OPENER);
    const closes = p.text.includes(CLOSER);

    if (opens) {
      if (cur) {
        warnings.push(`article at post ${cur.postIds[0]} never closed before post ${p.id} — force-closed`);
        closeCur();
      }
      const idx = p.text.indexOf(OPENER);
      // Cap title length: a post can contain the opener phrase deep inside a long
      // isnad chain with no real "title line" before it — without a cap that whole
      // stretch becomes the title/slug and blows past the filesystem filename limit.
      const rawTitlePart = p.text.slice(0, idx).trim();
      const titlePart = rawTitlePart.length > 100
        ? rawTitlePart.slice(0, 80).split(/[.\n؟!]/)[0].trim()
        : rawTitlePart;
      const rest = p.text.slice(idx).trim(); // opener kept in body — only used to split off the title
      cur = { titlePart, dateIso: p.dateIso, postIds: [p.id], parts: [rest] };
    } else if (cur) {
      cur.postIds.push(p.id);
      if (p.text) cur.parts.push(p.text);
      else warnings.push(`post ${p.id} inside article ${cur.postIds[0]} has no text (media-only?) — content may be incomplete`);
    } else {
      continue; // standalone noise: no open article, no opener (e.g. bare-link/media post)
    }

    if (cur && closes) closeCur();
  }
  if (cur) {
    warnings.push(`article at post ${cur.postIds[0]} never closed (reached end of fetched history)`);
    closeCur();
  }
  return articles;
}

// ─────────────────────────────────────────────
// Fetch + pagination
// ─────────────────────────────────────────────
const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

// ponytail: plain retry+backoff, not a circuit breaker — a multi-minute crawl over
// hundreds of requests will hit occasional ECONNRESET; that's the only case this covers.
async function fetchPage(url: string, attempts = 4): Promise<string> {
  for (let i = 1; i <= attempts; i++) {
    try {
      const res = await fetch(url, { headers: { "User-Agent": UA } });
      if (!res.ok) throw new Error(`fetch ${url} → ${res.status}`);
      return await res.text();
    } catch (err) {
      if (i === attempts) throw err;
      await sleep(1000 * i);
    }
  }
  throw new Error("unreachable");
}

async function crawl(opt: Opt): Promise<RawPost[]> {
  const all: RawPost[] = [];
  const seen = new Set<number>();
  let cursor: number | undefined;
  while (true) {
    const url = `https://t.me/s/${opt.channel}` + (cursor ? `?before=${cursor}` : "");
    const html = await fetchPage(url);
    const posts = parseMessages(html, opt.channel);
    if (!posts.length) break;
    const fresh = posts.filter((p) => !seen.has(p.id));
    for (const p of fresh) seen.add(p.id);
    all.push(...fresh);
    const minId = Math.min(...posts.map((p) => p.id));
    if (cursor === minId || !fresh.length) break; // no progress guard
    if (opt.since && minId <= opt.since) break;
    if (opt.limit && all.length >= opt.limit) break;
    cursor = minId;
    await sleep(400);
  }
  const since = opt.since;
  return (since ? all.filter((p) => p.id > since) : all).sort((a, b) => a.id - b.id);
}

// ─────────────────────────────────────────────
// Best-effort topic tagging — keyword match against EXISTING topic slugs
// (src/content/topic/*.md). Heuristic, no قسم field to key off like the epub
// importer has; status stays draft so misses/misfires get caught on review.
// ponytail: conservative distinctive phrases only, not bare religious terms
// (e.g. not "النبي") — those would match nearly every post and tag nothing.
// ─────────────────────────────────────────────
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

// ─────────────────────────────────────────────
// Frontmatter + file
// ─────────────────────────────────────────────
interface Opt {
  out: string; person: string; status: string;
  limit?: number; since?: number; dryRun: boolean; channel: string; today: string;
}

function buildArticle(a: ArticleDraft, opt: Opt): { path: string; text: string } {
  const base = slugify(a.title);
  const slug = uniqueArticleSlug(base, join(opt.out, "article"));
  const publishedAt = a.dateIso ? a.dateIso.slice(0, 10) : opt.today;
  const topics = classifyTopics(`${a.title}\n${a.body}`);
  const fm = [
    "---",
    `title: ${y(a.title)}`,
    `status: ${opt.status}`,
    `published_at: ${publishedAt}`,
    `person: ${opt.person}`,
    topics.length ? `topics: [${topics.map(y).join(", ")}]` : null,
    "---",
    "",
  ].filter((l): l is string => l !== null).join("\n");
  const more = a.postIds.length > 1 ? ` (+${a.postIds.length - 1} more)` : "";
  const provenance = `<!-- telegram: https://t.me/${opt.channel}/${a.postIds[0]}${more} -->\n\n`;
  return { path: join(opt.out, "article", slug + ".md"), text: fm + provenance + a.body + "\n" };
}

// ─────────────────────────────────────────────
// Self-test
// ─────────────────────────────────────────────
function selftest() {
  const a = (cond: boolean, msg: string) => { if (!cond) throw new Error("selftest: " + msg); };
  const CH = "TestChan";

  const wrap = (id: number, dateIso: string, textHtml: string, forwardOf?: number) => `
    <div class="tgme_widget_message_wrap js-widget_message_wrap">
    <div class="tgme_widget_message text_not_supported_wrap js-widget_message" data-post="${CH}/${id}">
    ${forwardOf ? `<div class="tgme_widget_message_forwarded_from accent_color">Forwarded from&nbsp;<a class="tgme_widget_message_forwarded_from_name" href="https://t.me/${CH}/${forwardOf}">X</a></div>` : ""}
    <div class="tgme_widget_message_text js-message_text" dir="auto">${textHtml}</div>
    <div class="tgme_widget_message_meta"><a class="tgme_widget_message_date" href="https://t.me/${CH}/${id}"><time datetime="${dateIso}" class="time">t</time></a></div>
    </div></div>`;

  const OPENER_HTML = `${OPENER}<br/>أما بعد :<br/>`;
  const CLOSER_HTML = `${CLOSER}<br/><br/><a href="https://t.me/${CH}" target="_blank">https://t.me/${CH}</a>`;

  // single-message article
  const posts1 = parseMessages(wrap(1, "2026-01-01T00:00:00+00:00", `عنوان تجريبي<br/><br/>${OPENER_HTML}محتوى أول${CLOSER_HTML}`), CH);
  a(posts1.length === 1, "parsed 1 post");
  a(posts1[0].id === 1, "post id parsed");
  a(!posts1[0].isForward, "not a forward");
  a(posts1[0].text.includes("محتوى أول"), "text extracted: " + posts1[0].text);

  a(!posts1[0].text.includes(`https://t.me/${CH}`), "self-link dropped from parsed text: " + posts1[0].text);

  const w1: string[] = [];
  const arts1 = groupArticles(posts1, w1);
  a(arts1.length === 1, "1 article grouped");
  a(arts1[0].title === "عنوان تجريبي", "title extracted: " + arts1[0].title);
  a(arts1[0].body.includes(OPENER), "opener kept: " + arts1[0].body);
  a(arts1[0].body.includes(CLOSER), "closer kept: " + arts1[0].body);
  a(arts1[0].body.includes("محتوى أول"), "content kept: " + arts1[0].body);
  a(!arts1[0].body.includes(`https://t.me/${CH}`), "self-link dropped from body: " + arts1[0].body);

  // two-message article + self-forward (must be skipped) + standalone noise (must be skipped)
  const htmlMulti =
    wrap(10, "2026-02-01T00:00:00+00:00", `عنوان طويل<br/><br/>${OPENER_HTML}جزء واحد`) +
    wrap(11, "2026-02-01T00:01:00+00:00", `جزء اثنين${CLOSER_HTML}`) +
    wrap(12, "2026-02-01T00:02:00+00:00", `<a href="https://t.me/${CH}" target="_blank">https://t.me/${CH}</a>`) +
    wrap(13, "2026-02-01T00:03:00+00:00", `عنوان مكرر<br/><br/>${OPENER_HTML}محتوى معاد${CLOSER_HTML}`, 5);

  const posts2 = parseMessages(htmlMulti, CH);
  a(posts2.length === 4, "parsed 4 posts: " + posts2.length);
  a(posts2.find((p) => p.id === 13)!.isForward, "post 13 detected as forward");

  const w2: string[] = [];
  const arts2 = groupArticles(posts2, w2);
  a(arts2.length === 1, "forward + noise excluded, only 1 real article: " + arts2.length);
  a(arts2[0].postIds.join(",") === "10,11", "article spans posts 10+11: " + arts2[0].postIds.join(","));
  a(arts2[0].body.includes("جزء واحد") && arts2[0].body.includes("جزء اثنين"), "both message parts merged: " + arts2[0].body);

  // ── topic classification ──
  a(classifyTopics("الكلام عن إسناد الحديث وقد ضعّف العلماء هذا الخبر").includes("mustalah-al-hadith"), "topic classify: hadith isnad");
  a(classifyTopics("رد على الملحد في إنكاره").includes("al-firaq-war-rudud"), "topic classify: atheism refutation");
  a(classifyTopics("نص عادي لا شيء مميز فيه").length === 0, "topic classify: no false positive on plain text");

  console.log("✓ selftest passed (all assertions)");
}

// ─────────────────────────────────────────────
// Main
// ─────────────────────────────────────────────
async function main() {
  const argv = process.argv.slice(2);
  if (argv.includes("--selftest")) return selftest();

  const flag = (name: string) => { const i = argv.indexOf(name); return i >= 0 ? argv[i + 1] : undefined; };
  const valued = new Set(["--out", "--person", "--status", "--limit", "--since"]);
  const positional = argv.filter((v, i) => !v.startsWith("--") && !valued.has(argv[i - 1]));
  const channel = positional[0];

  if (!channel) {
    console.error(
      "usage: pnpm import:telegram <channel> [flags]\n" +
      "  --out <dir>       content root (default: src/content)\n" +
      "  --person <slug>   author slug (default: abu-jafar-al-khalifi)\n" +
      "  --status <s>      draft|review|published|archived (default: draft)\n" +
      "  --limit <n>       stop after fetching n posts (default: unlimited)\n" +
      "  --since <id>      only import posts newer than this post id (incremental re-runs)\n" +
      "  --dry-run         print without writing",
    );
    process.exit(1);
  }

  const opt: Opt = {
    out: flag("--out") ?? "src/content",
    person: flag("--person") ?? "abu-jafar-al-khalifi",
    status: flag("--status") ?? "draft",
    limit: flag("--limit") ? +flag("--limit")! : undefined,
    since: flag("--since") ? +flag("--since")! : undefined,
    dryRun: argv.includes("--dry-run"),
    channel,
    today: new Date().toISOString().slice(0, 10),
  };

  const posts = await crawl(opt);
  const warnings: string[] = [];
  const grouped = groupArticles(posts, warnings);
  const articles = opt.limit ? grouped.slice(0, opt.limit) : grouped;

  console.log(`fetched ${posts.length} posts (${posts.filter((p) => p.isForward).length} forwards skipped) → ${grouped.length} articles` + (articles.length < grouped.length ? ` (writing first ${articles.length} per --limit)` : ""));
  for (const w of warnings) console.warn("⚠ " + w);

  // Same-source repost dedup: the shaykh sometimes re-posts old text as a
  // fresh (non-forward) message, which carries no structural "forwarded"
  // marker — catch it here by exact body match instead. Seeded from disk,
  // then updated as we go so duplicates *within* this run are caught too.
  const index: ArticleIndexEntry[] = loadArticleIndex(join(opt.out, "article"));
  let skipped = 0;

  for (const draft of articles) {
    const bodyNorm = normText(draft.body);
    const dup = findByBody(bodyNorm, index);
    if (dup) {
      skipped++;
      console.log(`⏭  skipped (duplicate of ${dup.file}): ${draft.title}  (posts ${draft.postIds.join(",")})`);
      continue;
    }
    const file = buildArticle(draft, opt);
    console.log(`📰 ${file.path}  (posts ${draft.postIds.join(",")})`);
    if (opt.dryRun) {
      console.log("   [dry-run] " + file.text.split("\n").slice(0, 8).join("\n   "));
      continue;
    }
    mkdirSync(dirname(file.path), { recursive: true });
    writeFileSync(file.path, file.text);
    index.push({ file: file.path, title: draft.title, publishedAt: "", bodyNorm });
  }
  if (skipped) console.log(`\n${skipped} repost(s) skipped as duplicates of already-imported content.`);

  const maxId = posts.length ? Math.max(...posts.map((p) => p.id)) : opt.since;
  if (opt.dryRun) console.log(`\n(dry run — resume marker would be --since ${maxId})`);
  else console.log(`\nNext: pnpm validate:content, then review drafts before flipping status: published.\nResume later with --since ${maxId}`);
}

main();
