#!/usr/bin/env tsx
// Blogger blog → article/ importer.
// Uses Blogger's classic JSON feed (no auth needed for a public blog) — each
// feed entry already has a real title, ISO date, and label list, so unlike
// the Telegram importer there's no multi-message grouping or title-guessing:
// one feed entry = one article.
//
// Cross-source dedup: unlike Telegram's self-forward links, Blogger has no
// structural pointer back to a same-content Telegram post, so duplicates
// against already-imported articles are caught by fuzzy title/body-opening
// match — not 100% reliable, hence --status defaults to draft here (not
// published like the Telegram importer's agreed-safe default).
//
// Usage:  pnpm import:blogspot <blog-host> [flags]
//   --out <dir>       content root (default: src/content)
//   --person <slug>   author slug (default: abu-jafar-al-khalifi)
//   --status <s>      draft|review|published|archived (default: draft)
//   --limit <n>       write at most n articles (default: unlimited — walks full history)
//   --exclude <label> additional label to exclude (repeatable)
//   --dry-run         print what would be written, write nothing
//   --selftest        run built-in assertions and exit

import { writeFileSync, mkdirSync } from "node:fs";
import { join, dirname } from "node:path";
import { slugify, uniqueArticleSlug, y } from "./lib/slugify.ts";
import { normText, findFuzzy, loadArticleIndex, type ArticleIndexEntry } from "./lib/dedup.ts";

// ─────────────────────────────────────────────
// Labels that are not written-essay content — skip these posts entirely.
// ─────────────────────────────────────────────
const DEFAULT_EXCLUDED_LABELS = new Set([
  "الدروس الصوتية",     // audio lessons — not text
  "صحيح آثار التابعين",  // raw narration compilations, not authored essays
  "صحيح آثار الصحابة",   // same
  "كتب مصنفه",           // full authored books — belongs in the book collection
]);

// Blogger label → existing topic slug(s). Labels with no confident 1:1 match
// (الفقه has no madhhab, متفرقات/تنبيهات are too generic) are left untagged.
const LABEL_TOPIC_MAP: Record<string, string[]> = {
  "التوحيد والعقيدة": ["tahwid-al-ibada", "al-aqeedah-al-aamah"],
  "دراسة الأحاديث والحكم على الأسانيد": ["mustalah-al-hadith"],
  "الردود والتعقيبات": ["al-firaq-war-rudud"],
  "الرد على الأشاعرة الجهمية": ["al-firaq-war-rudud"],
  "أخبار وحكايات مشهورة لا تصح": ["mustalah-al-hadith"],
  "آداب ومواعظ": ["al-akhlaq-wal-adab"],
  "التفسير وعلوم القرآن": ["tafsir-al-quran", "ulum-al-quran"],
  "الدراسات الحديثية": ["mustalah-al-hadith"],
};

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

/** Blogger post HTML → markdown-ish text. Editor output + occasional Word-paste
 *  cruft (o:p, w:*, m:*, xml namespaces) — regex pipeline, no HTML parser dep. */
function blogHtmlToText(html: string): string {
  let s = html;
  s = s.replace(/<style[\s\S]*?<\/style>/gi, "");
  s = s.replace(/<\/?(?:o:p|o|w:[a-z]+|m:[a-z]+|xml)[^>]*>/gi, "");
  s = s.replace(/<img[^>]*>/gi, "");
  s = s.replace(/<iframe[\s\S]*?<\/iframe>/gi, "");
  s = s.replace(/<a\s+href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/gi, (_m, href, label) => {
    const cleanLabel = decode(stripTags(label)).trim();
    return cleanLabel ? `[${cleanLabel}](${decode(href)})` : "";
  });
  // (?=[\s>/]) anchors the tag name exactly — without it "b" also matches the
  // start of "br"/"blockquote", "i" would match "iframe", etc.
  s = s.replace(/<\/?(?:b|strong)(?=[\s>/])[^>]*>/gi, "**");
  s = s.replace(/<\/?(?:i|em)(?=[\s>/])[^>]*>/gi, "*");
  s = s.replace(/<\/(?:div|p|li|h[1-6]|blockquote)(?=[\s>/])[^>]*>/gi, "\n\n");
  s = s.replace(/<(?:div|p|li|h[1-6]|blockquote)(?=[\s>/])[^>]*>/gi, "");
  s = s.replace(/<hr\s*\/?>/gi, "\n\n---\n\n");
  s = s.replace(/<br\s*\/?>/gi, "\n");
  s = decode(stripTags(s));
  s = s.split("\n").map((l) => l.replace(/[ \t]+/g, " ").trim()).join("\n");
  return s.replace(/\n{3,}/g, "\n\n").trim();
}

// ─────────────────────────────────────────────
// Fetch + pagination (Blogger's classic feed self-limits to ~20-25 entries
// per request regardless of max-results, apparently on response size)
// ─────────────────────────────────────────────
const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

async function fetchJson(url: string, attempts = 4): Promise<any> {
  for (let i = 1; i <= attempts; i++) {
    try {
      const res = await fetch(url, { headers: { "User-Agent": UA } });
      if (!res.ok) throw new Error(`fetch ${url} → ${res.status}`);
      return await res.json();
    } catch (err) {
      if (i === attempts) throw err;
      await sleep(1000 * i);
    }
  }
}

interface BlogPost {
  title: string;
  html: string;
  publishedIso: string;
  labels: string[];
  url: string;
}

function parseEntry(e: any): BlogPost {
  const alt = (e.link ?? []).find((l: any) => l.rel === "alternate");
  return {
    title: decode(e.title?.$t ?? "").trim(),
    html: e.content?.$t ?? "",
    publishedIso: e.published?.$t ?? "",
    labels: (e.category ?? []).map((c: any) => c.term),
    url: alt?.href ?? "",
  };
}

// Blogger permalinks are always /YYYY/MM/slug.html — a reliable fallback when
// `published` is corrupt (observed: one post dated year 2222 in the feed).
function publishedDate(p: BlogPost, today: string): string {
  const year = +p.publishedIso.slice(0, 4);
  const currentYear = +today.slice(0, 4);
  if (year >= 2005 && year <= currentYear) return p.publishedIso.slice(0, 10);
  const m = p.url.match(/\/(\d{4})\/(\d{2})\//);
  return m ? `${m[1]}-${m[2]}-01` : today;
}

async function crawlBlog(host: string, opt: Opt): Promise<BlogPost[]> {
  const all: BlogPost[] = [];
  let startIndex = 1;
  while (true) {
    const url = `https://${host}/feeds/posts/default?alt=json&max-results=50&start-index=${startIndex}`;
    const json = await fetchJson(url);
    const entries: any[] = json.feed?.entry ?? [];
    if (!entries.length) break;
    const posts = entries.map(parseEntry);
    all.push(...posts);
    startIndex += entries.length;
    const total = +(json.feed?.openSearch$totalResults?.$t ?? 0);
    if (startIndex > total) break;
    if (opt.limit && all.length >= opt.limit) break;
    await sleep(400);
  }
  return all;
}

// ─────────────────────────────────────────────
// Frontmatter + file
// ─────────────────────────────────────────────
interface Opt {
  out: string; person: string; status: string;
  limit?: number; excluded: Set<string>; dryRun: boolean; today: string;
}

function topicsFor(labels: string[]): string[] {
  const hits: string[] = [];
  for (const label of labels) {
    for (const t of LABEL_TOPIC_MAP[label] ?? []) {
      if (!hits.includes(t)) hits.push(t);
    }
  }
  return hits.slice(0, 5);
}

function buildArticle(p: BlogPost, opt: Opt, isDup: boolean): { path: string; text: string } {
  const base = slugify(p.title);
  const slug = uniqueArticleSlug(base, join(opt.out, "article"));
  const topics = topicsFor(p.labels);
  const fm = [
    "---",
    `title: ${y(p.title)}`,
    `status: ${opt.status}`,
    `published_at: ${publishedDate(p, opt.today)}`,
    `person: ${opt.person}`,
    topics.length ? `topics: [${topics.map(y).join(", ")}]` : null,
    "---",
    "",
  ].filter((l): l is string => l !== null).join("\n");
  const dupNote = isDup ? "<!-- possible duplicate of an already-imported article — verify before publishing -->\n" : "";
  const provenance = `<!-- blogspot: ${p.url} -->\n${dupNote}\n`;
  const body = blogHtmlToText(p.html);
  return { path: join(opt.out, "article", slug + ".md"), text: fm + provenance + body + "\n" };
}

// ─────────────────────────────────────────────
// Self-test
// ─────────────────────────────────────────────
function selftest() {
  const a = (cond: boolean, msg: string) => { if (!cond) throw new Error("selftest: " + msg); };

  a(blogHtmlToText("<div>فقرة أولى</div><div>فقرة ثانية</div>").includes("فقرة أولى") &&
    blogHtmlToText("<div>فقرة أولى</div><div>فقرة ثانية</div>").includes("فقرة ثانية"), "div → paragraphs");
  a(blogHtmlToText("سطر أول<br/>سطر ثان") === "سطر أول\nسطر ثان", "br → newline");
  a(blogHtmlToText('<a href="https://x.com">نص</a>') === "[نص](https://x.com)", "link → markdown");
  a(blogHtmlToText("<b>مهم</b>") === "**مهم**", "bold kept");
  a(!blogHtmlToText('<o:p>&nbsp;</o:p>نص').includes("o:p"), "MS Office cruft stripped");

  const entry = parseEntry({
    title: { $t: "عنوان تجريبي" },
    content: { $t: "<div>محتوى</div>" },
    published: { $t: "2222-08-01T06:30:00.000-07:00" }, // known bad-year case
    category: [{ term: "الفقه" }],
    link: [{ rel: "alternate", href: "https://x.blogspot.com/2014/08/blog-post_56.html" }],
  });
  a(entry.title === "عنوان تجريبي", "title parsed");
  a(publishedDate(entry, "2026-07-08") === "2014-08-01", "bad-year published falls back to permalink date: " + publishedDate(entry, "2026-07-08"));

  a(topicsFor(["دراسة الأحاديث والحكم على الأسانيد", "الفقه"]).includes("mustalah-al-hadith"), "label→topic mapping");
  a(topicsFor(["متفرقات"]).length === 0, "no forced mapping for generic label");

  const existing: ArticleIndexEntry[] = [{ file: "x.md", publishedAt: "", title: "مقال عن التوحيد", bodyNorm: normText("هذا نص طويل يبدأ بهذه الجملة المكررة ويستمر بعد ذلك في الشرح والتفصيل لفترة") }];
  a(!!findFuzzy("مقال عن التوحيد", "أي محتوى", existing), "duplicate caught by title");
  a(!!findFuzzy("عنوان مختلف تماما", "هذا نص طويل يبدأ بهذه الجملة المكررة ويستمر بعد ذلك في الشرح والتفصيل لفترة", existing), "duplicate caught by body opening despite different title");
  a(!findFuzzy("عنوان جديد كليا", "محتوى جديد كليا لم يسبق نشره من قبل على الإطلاق", existing), "distinct post not flagged");

  console.log("✓ selftest passed (all assertions)");
}

// ─────────────────────────────────────────────
// Main
// ─────────────────────────────────────────────
async function main() {
  const argv = process.argv.slice(2);
  if (argv.includes("--selftest")) return selftest();

  const flag = (name: string) => { const i = argv.indexOf(name); return i >= 0 ? argv[i + 1] : undefined; };
  const flagAll = (name: string) => argv.flatMap((v, i) => (v === name ? [argv[i + 1]] : []));
  const valued = new Set(["--out", "--person", "--status", "--limit", "--exclude"]);
  const positional = argv.filter((v, i) => !v.startsWith("--") && !valued.has(argv[i - 1]));
  const host = positional[0];

  if (!host) {
    console.error(
      "usage: pnpm import:blogspot <blog-host> [flags]\n" +
      "  --out <dir>       content root (default: src/content)\n" +
      "  --person <slug>   author slug (default: abu-jafar-al-khalifi)\n" +
      "  --status <s>      draft|review|published|archived (default: draft)\n" +
      "  --limit <n>       write at most n articles (default: unlimited)\n" +
      "  --exclude <label> additional label to exclude (repeatable)\n" +
      "  --dry-run         print without writing",
    );
    process.exit(1);
  }

  const opt: Opt = {
    out: flag("--out") ?? "src/content",
    person: flag("--person") ?? "abu-jafar-al-khalifi",
    status: flag("--status") ?? "draft",
    limit: flag("--limit") ? +flag("--limit")! : undefined,
    excluded: new Set([...DEFAULT_EXCLUDED_LABELS, ...flagAll("--exclude")]),
    dryRun: argv.includes("--dry-run"),
    today: new Date().toISOString().slice(0, 10),
  };

  const posts = await crawlBlog(host, opt);
  const kept = posts.filter((p) => !p.labels.some((l) => opt.excluded.has(l)));
  const existing = loadArticleIndex(join(opt.out, "article"));

  console.log(`fetched ${posts.length} posts, ${posts.length - kept.length} excluded by label → ${kept.length} candidates`);

  let dupCount = 0;
  for (const p of kept) {
    const body = blogHtmlToText(p.html);
    const bodyNorm = normText(body);
    const dup = findFuzzy(p.title, bodyNorm, existing);
    const isDup = !!dup;
    if (dup) console.log(`   ↳ matched: ${dup.file}`);
    if (isDup) dupCount++;
    const file = buildArticle(p, opt, isDup);
    console.log(`${isDup ? "⚠ " : "📰"} ${file.path}${isDup ? "  (possible duplicate)" : ""}`);
    if (opt.dryRun) continue;
    mkdirSync(dirname(file.path), { recursive: true });
    writeFileSync(file.path, file.text);
    // keep dedup index current within this run too, not just against prior runs
    existing.push({ file: file.path, title: p.title, publishedAt: "", bodyNorm });
  }

  console.log(`\n${dupCount} flagged as possible duplicates — check the <!-- possible duplicate --> comment before publishing.`);
  if (!opt.dryRun) console.log("Next: pnpm validate:content, then review drafts (including duplicate flags) before publishing.");
}

main();
