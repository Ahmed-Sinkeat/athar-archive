// Post-build smoke test (issue #6): asserts per-template invariants over the
// built dist/ HTML so template regressions fail loudly — not just "build OK".
// Usage (after `pnpm build`): node scripts/smoke-test.mjs

import fs from "node:fs";
import path from "node:path";

const DIST = path.resolve("dist");
let failures = 0;
const read = (p) => {
  const f = path.join(DIST, p);
  if (!fs.existsSync(f)) { fail(`missing file: ${p}`); return ""; }
  return fs.readFileSync(f, "utf-8");
};
function ok(cond, msg) { if (cond) { console.log(`  ✓ ${msg}`); } else { console.log(`  ✗ ${msg}`); failures++; } }
function fail(msg) { console.log(`  ✗ ${msg}`); failures++; }
function section(t) { console.log(`\n${t}`); }

// --- home ---
section("home");
const home = read("index.html");
ok(/class="hero-title"/.test(home), "hero title renders");
ok(/class="stats-strip"/.test(home), "stats strip renders");
ok(/"@type":"WebSite"/.test(home) && /SearchAction/.test(home), "WebSite + SearchAction JSON-LD");
ok(/rel="canonical"/.test(home), "home has canonical");
ok(/href="\/poems"/.test(home) && /href="\/questions"/.test(home), "nav splits الكتب/المنظومات + المسائل");
ok(!/>المكتبة</.test(home), "no 'المكتبة' label in nav/footer");
ok(/aria-label="البحث"/.test(home), "search affordance present");

// --- books index (books only, kind filters) ---
section("/books");
const books = read("books/index.html");
ok((books.match(/class="card"/g) || []).length >= 1, "books index lists cards");
ok(/class="masail-subject"/.test(books), "books grouped in تصنيف accordion");
ok(/card-kindrow/.test(books), "book kind pill on cards");

// --- poems index (poems only, متن badge + inherited era) ---
section("/poems");
const poems = read("poems/index.html");
ok((poems.match(/class="card"/g) || []).length >= 2, "poems index lists cards");
ok(/badge-matn/.test(poems), "متن badge on poem cards");
ok(/data-era=/.test(poems), "era tagged on poem cards (inherited from author)");

// --- composer (unlisted maintainer tool) ---
section("/compose");
const compose = read("compose/index.html");
ok(/id="ctype"/.test(compose), "composer renders");
ok(/name="robots" content="noindex"/.test(compose), "composer is noindex");

// --- poem reader: verses + stacked annotations ---
section("/poem/alfiyyah-ibn-malik");
const poem = read("poem/alfiyyah-ibn-malik/index.html");
ok(/class="sadr"/.test(poem), "verses render (sadr)");
ok(/ann-mark/.test(poem), "annotated phrase marked inline");
ok(/id="ann-v1"/.test(poem), "annotation pack present");
ok(/data-kind="شرح"/.test(poem) && /data-kind="إعراب"/.test(poem), "both annotation kinds available");
ok(/rel="canonical" href="https:\/\/ahlalathar\.com\/poem\/alfiyyah-ibn-malik"/.test(poem), "canonical (.com)");
ok(/"@type":\["CreativeWork","Poem"\]/.test(poem), "Poem JSON-LD");
ok(/data-studybar/.test(poem) && /data-matn=/.test(poem), "study bar + tracking container on متن poem");
ok(/badge-matn/.test(poem), "متن badge on poem reader");

// --- book reader: prose + audio? + collapsible حواشٍ ---
section("/book/al-wasitiyyah");
const book = read("book/al-wasitiyyah/index.html");
ok(/class="prose"/.test(book), "matn prose renders");
ok(/data-matn=/.test(book), "متن book tracking container present");
ok(/class="benefit-strip notes-disclosure"/.test(book), "حواشٍ collapsible disclosure renders");
ok(/"@type":"Book"/.test(book), "Book JSON-LD");

// --- lesson reader: TOC anchors must match heading ids (rehype-slug alignment) ---
section("/series/sharh-al-wasitiyyah/lesson-1");
const lesson = read("series/sharh-al-wasitiyyah/lesson-1/index.html");
const tocAnchors = [...lesson.matchAll(/class="toc-box"[\s\S]*?<\/div>/g)].length
  ? [...lesson.matchAll(/href="#([^"]+)"/g)].map((m) => m[1]).filter((a) => !a.startsWith("note"))
  : [];
const headingIds = [...lesson.matchAll(/<h2 id="([^"]+)"/g)].map((m) => m[1]);
const unmatched = tocAnchors.filter((a) => !headingIds.includes(a));
ok(tocAnchors.length > 0 && unmatched.length === 0, `lesson TOC anchors resolve to heading ids (${tocAnchors.length} links)`);
ok(/<audio controls/.test(lesson), "lesson audio player renders");
ok(/class="prevnext"/.test(lesson), "prev/next nav renders");

// --- person hub lists works ---
section("/person/ibn-taymiyyah");
const person = read("person/ibn-taymiyyah/index.html");
ok(/من آثارِه في الأرشيف/.test(person) && (person.match(/class="card"/g) || []).length >= 1, "person lists works");
ok(/"@type":"ProfilePage"/.test(person), "ProfilePage JSON-LD");

// --- topic hub lists materials ---
section("/topic/al-asma-was-sifat");
const topic = read("topic/al-asma-was-sifat/index.html");
ok((topic.match(/class="card"/g) || []).length >= 2, "topic lists linked materials");

// --- questions QAPage ---
section("/questions/masail-al-asma-was-sifat");
const q = read("questions/masail-al-asma-was-sifat/index.html");
ok(/"@type":"QAPage"/.test(q), "QAPage JSON-LD");

// --- search is noindex ---
section("/search");
const search = read("search/index.html");
ok(/name="robots" content="noindex"/.test(search), "search is noindex");
ok(!/rel="canonical"/.test(search), "search has no canonical");

// --- feeds & permanence ---
section("feeds & permanence");
const sitemap = read("sitemap.xml");
ok((sitemap.match(/<url>/g) || []).length >= 20, "sitemap has URLs");
ok(!/\/search</.test(sitemap), "sitemap excludes /search");
const rss = read("rss.xml");
ok((rss.match(/<item>/g) || []).length >= 1, "rss has items");
const redirects = read("_redirects");
ok(/\/poem\/bayquniyyah \/poem\/al-bayquniyyah 301/.test(redirects), "alias 301 emitted");
const headers = read("_headers");
ok(/Content-Security-Policy/.test(headers), "CSP header present");

// --- chrome excluded from search index on every detail page ---
section("search-index scoping");
ok(/data-pagefind-body/.test(poem) && /data-pagefind-ignore/.test(poem.split("<main")[0]), "content indexed, chrome ignored");

console.log(`\n${failures === 0 ? "✓ all smoke assertions passed" : `✗ ${failures} smoke assertion(s) failed`}`);
process.exit(failures === 0 ? 0 : 1);
