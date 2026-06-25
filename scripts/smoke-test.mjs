// Post-build smoke test (issue #6): asserts per-template invariants over the
// built dist/ HTML so template regressions fail loudly — not just "build OK".
// Usage (after `pnpm build`): node scripts/smoke-test.mjs

import fs from "node:fs";
import path from "node:path";

// Hybrid Cloudflare build emits prerendered HTML + assets under dist/client/
// (dist/server/ is the on-demand worker).
const DIST = path.resolve("dist/client");
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
ok(/(action|href)="\/search/.test(home), "search affordance present (hero form + drawer link)");

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

// --- composer (unlisted maintainer tool) ---
section("/compose");
const compose = read("compose/index.html");
ok(/id="ctype"/.test(compose), "composer renders");
ok(/name="robots" content="noindex"/.test(compose), "composer is noindex");

// --- poem reader: verses + stacked annotations ---
section("/poem/nwnya-al-qhtany");
const poem = read("poem/nwnya-al-qhtany/index.html");
ok(/rel="canonical" href="https:\/\/[^"]+\/poem\/nwnya-al-qhtany"/.test(poem), "canonical points to the poem (host-agnostic)");
ok(/"@type":\["CreativeWork","Poem"\]/.test(poem), "Poem JSON-LD");
ok(/badge-matn/.test(poem), "متن badge on poem reader");

// --- book reader: prose + audio? + annotation packs (open in the bottom sheet) ---
section("/book/aalm-al-jn-walshyatyn");
const book = read("book/aalm-al-jn-walshyatyn/index.html");
ok(/"@type":"Book"/.test(book), "Book JSON-LD");

// --- person hub lists works ---
section("/person/abd-al-azyz-al-mltany");
const person = read("person/abd-al-azyz-al-mltany/index.html");
ok(/من آثارِه في الأرشيف/.test(person), "person lists works section exists");
ok(/"@type":"ProfilePage"/.test(person), "ProfilePage JSON-LD");

// --- topic hub lists materials ---
section("/topic/aam-other");
const topic = read("topic/aam-other/index.html");
ok((topic.match(/class="card"/g) || []).length >= 1, "topic lists linked materials");

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
const headers = read("_headers");
ok(/Content-Security-Policy/.test(headers), "CSP header present");

console.log(`\n${failures === 0 ? "✓ all smoke assertions passed" : `✗ ${failures} smoke assertion(s) failed`}`);
process.exit(failures === 0 ? 0 : 1);
