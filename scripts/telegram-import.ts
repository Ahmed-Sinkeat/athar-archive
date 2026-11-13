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
//   --threads         channels without the template: one article per post, replies
//                     and a photo's commentary joined in; photos kept (see groupThreads)
//   --posts <json>    use posts already crawled (JSON array of RawPost) instead of crawling;
//                     run soon after — Telegram's photo links expire
//   --dry-run         print what would be written, write nothing
//   --selftest        run built-in assertions and exit

import { writeFileSync, mkdirSync, existsSync, readFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { slugify, uniqueArticleSlug, y } from "./lib/slugify.ts";
import { normText, findByBody, loadArticleIndex, type ArticleIndexEntry } from "./lib/dedup.ts";

// ─────────────────────────────────────────────
// HTML helpers
// ─────────────────────────────────────────────
function decode(s: string): string {
  return s
    .replace(/&[rl]rm;/g, "") // direction marks Telegram puts inside "(٢/‏٢٥٧)"; invisible, and they break search
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
    if (cleanHref.startsWith("?q=")) return cleanLabel; // a #hashtag: Telegram's in-channel search, plain text here
    return `[${cleanLabel}](${cleanHref})`;
  });
  s = s.replace(/(?:<br\s*\/?>\s*){2,}/gi, "\n\n").replace(/<br\s*\/?>/gi, "\n");
  s = decode(stripTags(s));
  // A "~~~~" or "````" decoration line opens a Markdown code fence that swallows the rest of the post.
  s = s.split("\n").map((l) => l.replace(/[ \t]+/g, " ").trim()).filter((l) => !/^(?:~{3,}|`{3,})$/.test(l)).join("\n");
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
interface RawPost { id: number; dateIso: string; isForward: boolean; text: string; photos: string[]; replyTo?: number }

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
    const reply = b.match(new RegExp(`class="tgme_widget_message_reply[^"]*" href="https://t\\.me/${channel}/(\\d+)"`));
    posts.push({
      id: +idMatch[1],
      dateIso: dateMatch?.[1] ?? "",
      isForward,
      text: inner ? toText(inner, channel) : "",
      photos: [...b.matchAll(/class="tgme_widget_message_photo_wrap[^"]*"[^>]*background-image:url\('([^']+)'\)/g)].map((m) => m[1]),
      ...(reply ? { replyTo: +reply[1] } : {}),
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
// --threads: channels with no template. A post opens an article; a post that
// replies to an earlier post joins that post's article. A photo-only post goes
// with the text post right after it (the commentary on it) — unless that post
// opens with a 『title』, which marks the photo as a title card: dropped, and the
// bracketed text becomes the title. Short leftovers (greetings, ads) are dropped.
// ─────────────────────────────────────────────
const TITLE_CARD_RE = /^『\s*([^』]+?)\s*』\s*/;
// Openings that are never a title: greetings/formulas, and lead-ins ending in ":".
const GREETING_RE = /^(?:(?:و)?عليكم السلام|السلام عليكم|بسم الله|الحمد لله|إن الحمد لله|إنا لله)/;
const NOT_TITLE_RE = new RegExp(GREETING_RE.source + "|[:：]-?$|^[^ء-ي]*$"); // also a lead-in "…:" and a bare "=====" rule
const TITLE_TRIM_RE = /^[\s\[«•▪▫️~*\-–—]+|[\s\]»:：\-–—]+$/gu;
// A post opening with "و…" minutes after the previous one continues it (split for length).
// So does one opening with a "=====" rule or a "~ إضافة" (an addition to the post above).
const CONTINUES_RE = /^(?:و\S|={3,}|~\s*إضافة)/;
const CONTINUE_WINDOW_MS = 10 * 60_000;
// Short benefits (a quote, a du'a) are kept from 150; below that it is chatter. A short post
// with a link is a promo for a video/voice note, and "X pinned «…»" is a service notice.
const MIN_TEXT = 150, MIN_PHOTO_TEXT = 100, PROMO_UNDER = 300;
const SERVICE_RE = / pinned «/;
export interface ThreadDraft extends ArticleDraft { photos: Array<{ postId: number; url: string }>; titleLine?: boolean }

function groupThreads(posts: RawPost[], dropped: string[]): ThreadDraft[] {
  const own = posts.filter((p) => !p.isForward && (p.text || p.photos.length)).sort((a, b) => a.id - b.id);
  const groupOf = new Map<number, RawPost[]>();
  const groups: RawPost[][] = [];
  const lone = (id?: number) => { const g = id === undefined ? undefined : groupOf.get(id); return g?.length === 1 && !g[0].text && g[0].photos.length ? g : undefined; };
  for (let i = 0; i < own.length; i++) {
    const p = own[i];
    const prev = own[i - 1];
    if (TITLE_CARD_RE.test(p.text)) {
      // a 『title』 always opens its own article; the photo it follows or answers is its title card,
      // and later replies to that card are this article's continuation
      const g = [p];
      groups.push(g);
      groupOf.set(p.id, g);
      for (const card of new Set([lone(p.replyTo), lone(prev?.id)])) {
        if (!card) continue;
        groups.splice(groups.indexOf(card), 1);
        dropped.push(`${card[0].id}: title card`);
        groupOf.set(card[0].id, g);
      }
      continue;
    }
    let g = p.replyTo !== undefined ? groupOf.get(p.replyTo) : undefined;
    if (p.text) g ??= lone(prev?.id); // a photo after a photo is not its commentary
    const lastText = own.slice(0, i).findLast((q) => q.text);
    if (!g && CONTINUES_RE.test(p.text) && lastText && Date.parse(p.dateIso) - Date.parse(lastText.dateIso) <= CONTINUE_WINDOW_MS) g = groupOf.get(lastText.id);
    if (!g) { g = []; groups.push(g); }
    g.push(p);
    groupOf.set(p.id, g);
  }
  const out: ThreadDraft[] = [];
  for (const g of groups) {
    const text = g.map((p) => p.text).filter(Boolean).join("\n\n");
    const photos = g.flatMap((p) => p.photos.map((url) => ({ postId: p.id, url })));
    const titled = TITLE_CARD_RE.test(text);
    // Long URLs pass the length bar, so a post of just a title and links is measured without them.
    const linkOnly = !photos.length && text.replace(TITLE_CARD_RE, "").replace(/\S*https?:\/\/\S*/g, "").trim().length < 20;
    if (SERVICE_RE.test(text) || linkOnly || (!titled && (text.length < (photos.length ? MIN_PHOTO_TEXT : MIN_TEXT) || (text.length < PROMO_UNDER && /\]\(https?:/.test(text))))) {
      dropped.push(`${g.map((p) => p.id).join(",")}: ${text.length} chars${photos.length ? " + photo" : ""}`);
      continue;
    }
    let title: string, body: string, titleLine = false;
    const card = text.match(TITLE_CARD_RE);
    const firstLine = text.split("\n")[0].trim();
    if (card) { title = card[1].replace(/\s+/g, " "); body = text.slice(card[0].length); }
    else if (firstLine.length <= 80 && !NOT_TITLE_RE.test(firstLine) && /\n\s*\n/.test(text.slice(firstLine.length, firstLine.length + 3))) { title = firstLine; body = text.slice(firstLine.length).trim(); titleLine = true; }
    else {
      // first line that says something: no greeting, no bare rule, not a two-word "قلتُ:"
      const lead = text.split("\n").map((x) => x.replace(TITLE_TRIM_RE, "").slice(0, 80).split(/[.؟!:،]/)[0].trim())
        .find((x) => x.length >= 12 && !GREETING_RE.test(x) && /[ء-ي]/.test(x)) ?? firstLine;
      title = lead;
      body = text;
    }
    // A title line can carry a link ("[label](url)", or one cut short at 80 chars): keep the words only.
    title = title.replace(/\[([^\]]*)\]\([^)]*\)?/g, "$1").replace(/\]\(https?\S*/g, "").replace(/[[\]]/g, "").replace(TITLE_TRIM_RE, "");
    out.push({ title: title || `منشور ${g[0].id}`, dateIso: g[0].dateIso, postIds: g.map((p) => p.id), body, photos, titleLine });
  }
  return out;
}

/** Each photo goes above the text of the post that carried it. */
function threadBody(t: ThreadDraft, posts: RawPost[], image: (postId: number, k: number) => string): string {
  const byId = new Map(posts.map((p) => [p.id, p]));
  let first = true; // the title comes off the first post that has text
  return t.postIds.map((id) => {
    const p = byId.get(id)!;
    let text = p.text;
    if (text && first) {
      first = false;
      const card = text.match(TITLE_CARD_RE);
      if (card) text = text.slice(card[0].length);
      else if (t.titleLine) text = text.slice(text.indexOf("\n") + 1);
    }
    return [...p.photos.map((_, k) => image(id, k)), text.trim()].filter(Boolean).join("\n\n");
  }).filter(Boolean).join("\n\n");
}

// ponytail: Telegram's CDN links expire, so photos are fetched at import time into
// /media/ (gitignored, staging for scripts/upload-media.sh → R2), like /audio/.
const MEDIA_DIR = "media/telegram";
const MEDIA_URL = "https://app-content.arthurarchive.com/media/telegram";

async function savePhoto(url: string, file: string): Promise<void> {
  if (existsSync(file)) return;
  const res = await fetch(url, { headers: { "User-Agent": UA } });
  if (!res.ok) throw new Error(`photo ${url} → ${res.status}`);
  mkdirSync(dirname(file), { recursive: true });
  writeFileSync(file, Buffer.from(await res.arrayBuffer()));
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
  limit?: number; since?: number; dryRun: boolean; threads: boolean; channel: string; today: string;
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

  // ── --threads: title card dropped, photo joined to its commentary, reply joined, short post dropped ──
  const photo = (id: number, replyTo?: number) => `
    <div class="tgme_widget_message_wrap js-widget_message_wrap"><div class="tgme_widget_message js-widget_message" data-post="${CH}/${id}">
    ${replyTo ? `<a class="tgme_widget_message_reply user-color-default" href="https://t.me/${CH}/${replyTo}"><div class="tgme_widget_message_text js-message_reply_text">q</div></a>` : ""}
    <a class="tgme_widget_message_photo_wrap 1 2" href="https://t.me/${CH}/${id}" style="width:1px;background-image:url('https://cdn/p${id}.jpg')"></a>
    <time datetime="2026-03-01T00:00:00+00:00"></time></div></div>`;
  const long = "نص ".repeat(120);
  const posts3 = parseMessages(
    photo(20) + wrap(21, "2026-03-01T00:00:00+00:00", `『 عنوان البطاقة 』<br/><br/>${long}`) +
    photo(22) + wrap(23, "2026-03-01T00:00:00+00:00", `تعليق على الصورة وهو كاف ليبقى مع الصورة في المقال نفسه بلا شك ولا ريب أبدا ههنا`) +
    wrap(24, "2026-03-01T00:00:00+00:00", "سلام عليكم") +
    wrap(25, "2026-03-01T00:00:00+00:00", `تكملة ${long}`).replace('data-post="TestChan/25">', `data-post="TestChan/25"><a class="tgme_widget_message_reply x" href="https://t.me/${CH}/22"></a>`),
    CH);
  a(posts3.find((p) => p.id === 25)!.replyTo === 22, "reply parsed");
  a(posts3.find((p) => p.id === 22)!.photos[0] === "https://cdn/p22.jpg", "photo parsed");
  const d3: string[] = [];
  const t3 = groupThreads(posts3, d3);
  a(t3.length === 2, "2 thread articles: " + JSON.stringify(t3.map((t) => t.postIds)));
  a(t3[0].title === "عنوان البطاقة" && t3[0].photos.length === 0, "title card dropped, title taken: " + t3[0].title);
  const t4 = groupThreads(parseMessages(photo(30) + photo(31, 30).replace(/<a class="tgme_widget_message_photo_wrap[^]*?<\/a>/, `<div class="tgme_widget_message_text js-message_text">『 ع 』<br/>${long}</div>`) + photo(32, 30).replace(/<a class="tgme_widget_message_photo_wrap[^]*?<\/a>/, `<div class="tgme_widget_message_text js-message_text">${long}</div>`), CH), []);
  a(t4.length === 1 && t4[0].postIds.join(",") === "31,32" && !t4[0].photos.length, "reply to a title card continues the titled article: " + JSON.stringify(t4.map((t) => t.postIds)));
  a(t3[1].postIds.join(",") === "22,23,25" && t3[1].photos.length === 1, "photo+comment+reply joined: " + t3[1].postIds);
  a(d3.some((d) => d.startsWith("20:")) && d3.some((d) => d.startsWith("24:")), "card + short post reported: " + d3);
  const b3 = threadBody(t3[1], posts3, (id, k) => `IMG${id}-${k}`);
  a(b3.startsWith("IMG22-0\n\n") && b3.includes("تكملة"), "photo above its text: " + b3.slice(0, 40));
  a(!threadBody(t3[0], posts3, () => "").includes("『"), "card title not repeated in body");

  console.log("✓ selftest passed (all assertions)");
}

// ─────────────────────────────────────────────
// Main
// ─────────────────────────────────────────────
async function main() {
  const argv = process.argv.slice(2);
  if (argv.includes("--selftest")) return selftest();

  const flag = (name: string) => { const i = argv.indexOf(name); return i >= 0 ? argv[i + 1] : undefined; };
  const valued = new Set(["--out", "--person", "--status", "--limit", "--since", "--posts"]);
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
    threads: argv.includes("--threads"),
    channel,
    today: new Date().toISOString().slice(0, 10),
  };

  const postsFile = flag("--posts");
  const posts: RawPost[] = postsFile ? JSON.parse(readFileSync(postsFile, "utf8")) : await crawl(opt);
  const warnings: string[] = [];
  const dropped: string[] = [];
  const grouped = opt.threads ? groupThreads(posts, dropped) : groupArticles(posts, warnings);
  const articles = opt.limit ? grouped.slice(0, opt.limit) : grouped;

  console.log(`fetched ${posts.length} posts (${posts.filter((p) => p.isForward).length} forwards skipped) → ${grouped.length} articles` + (articles.length < grouped.length ? ` (writing first ${articles.length} per --limit)` : ""));
  for (const w of warnings) console.warn("⚠ " + w);
  if (dropped.length) console.log(`dropped ${dropped.length} short/title-card group(s): ` + dropped.join(" | "));

  // Same-source repost dedup: the shaykh sometimes re-posts old text as a
  // fresh (non-forward) message, which carries no structural "forwarded"
  // marker — catch it here by exact body match instead. Seeded from disk,
  // then updated as we go so duplicates *within* this run are caught too.
  const index: ArticleIndexEntry[] = loadArticleIndex(join(opt.out, "article"));
  let skipped = 0;

  for (const draft of articles) {
    if (opt.threads) {
      const t = draft as ThreadDraft;
      t.body = threadBody(t, posts, (id, k) => `![](${MEDIA_URL}/${opt.channel}/${id}-${k}.jpg)`);
    }
    const bodyNorm = normText(draft.body);
    // --threads channels repost each other (a student copying his shaykh's post, the shaykh's two
    // channels): the same opening 200 letters, images aside, is the same text — the first kept wins.
    // Unless this one runs longer: a short quote and the full post that opens with it are both kept.
    const plain = normText(draft.body.replace(/!\[\]\([^)]*\)/g, ""));
    const start = plain.slice(0, 200);
    const dup = findByBody(bodyNorm, index)
      ?? (opt.threads && start.length >= 120 ? index.find((e) => {
        const kept = e.bodyNorm.replace(/!https?\S+/g, "").trimStart();
        return kept.startsWith(start) && plain.length <= kept.length * 1.05 + 20;
      }) : undefined);
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
    for (const ph of (draft as ThreadDraft).photos ?? []) {
      const k = (draft as ThreadDraft).photos.filter((x) => x.postId === ph.postId).indexOf(ph);
      await savePhoto(ph.url, join(MEDIA_DIR, opt.channel, `${ph.postId}-${k}.jpg`));
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

if (process.argv[1]?.endsWith("telegram-import.ts")) main();
export { parseMessages, groupThreads, threadBody, crawl };
