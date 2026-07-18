#!/usr/bin/env node
// أهل الأثر — render-quality budget (P8, NFR-01 / FR-P-05)
// Over dist/, asserts for every page:
//   1. render-critical weight (HTML + local CSS/JS, excluding media & web fonts)
//      stays under budget — content pages must stay light (NFR-01);
//   2. meaningful content is present WITHOUT JS (server-rendered, FR-P-05);
//   3. the page is RTL Arabic (<html dir="rtl" lang="ar">).
// `/search` is a JS-driven tool page (D1/FTS5-backed) and is exempt from 1–2.
// tafsir-frag/*.html are ann-pack markup fetched by JS and injected into an
// already-RTL poem/quran page (gen-tafsir-frags.ts) — not standalone
// documents, so they're excluded from all three checks entirely.
//   pnpm perf:budget

import { readdirSync, readFileSync, existsSync, statSync } from "node:fs";
import { join, relative } from "node:path";

const DIST = join(process.cwd(), "dist/client"); // hybrid build: prerendered HTML + assets live here
if (!existsSync(DIST)) {
  console.error("✗ dist/ not found — run `pnpm build` first.");
  process.exit(1);
}

const BUDGET = 220 * 1024; // bytes: render-critical CSS+JS per page (HTML/content excluded — real Arabic books run 1 MB+ of legit prose; NFR-01 = ship light code). 150→160 for mobile app chrome (tab bar + bottom sheets CSS); 160→164 when /benefits' library.ts grew for the حفظ/"المحفوظات" bookmark tab; 164→204 when the mobile app redesign (settings/drawer anchored dropdowns + per-category icons) landed across every page's shared Base.css, plus /benefits' own copy-button addition — heaviest page (benefits, its own scoped CSS+JS on top of the shared bundle) measured 200.5 KB. 204→212 for the 2026-07-17 UX batch (تسميع mode, lazy per-source tafsir + sheet download button, surah jump nav, cross-deploy soft-nav guard) — measured 204.6 KB. 212→220 for the multi-place bookmark feature (popover markup/CSS + marks.ts/reader.ts/library.ts growth) — heaviest page (benefits) measured 217.1 KB.
const TEXT_FLOOR = 100;    // min visible chars rendered without JS
const JS_DRIVEN = new Set(["/search", "/compose", "/graph", "/admin"]); // JS-driven tool pages exempt from weight/text

const walk = (d) =>
  readdirSync(d, { withFileTypes: true }).flatMap((e) => {
    const p = join(d, e.name);
    return e.isDirectory() ? walk(p) : [p];
  });
const rel = (f) => relative(DIST, f);
const routeOf = (f) => "/" + rel(f).replace(/(?:^|\/)index\.html$/, "").replace(/\.html$/, "");
const sizeOf = (urlPath) => {
  const f = join(DIST, urlPath.split(/[?#]/)[0].replace(/^\//, ""));
  try { return statSync(f).size; } catch { return 0; }
};
const visibleText = (html) =>
  html
    .replace(/<script[\s\S]*?<\/script>/gi, "")
    .replace(/<style[\s\S]*?<\/style>/gi, "")
    .replace(/<[^>]+>/g, " ")
    .replace(/&[a-z#0-9]+;/gi, " ")
    .replace(/\s+/g, " ")
    .trim();
const DIACRITICS = /[ً-ٰٟ]/; // Arabic tashkeel

const htmlFiles = walk(DIST).filter((f) => f.endsWith(".html") && !relative(DIST, f).startsWith("tafsir-frag/"));
const over = [], thin = [], notRtl = [];
let maxWeight = 0, minText = Infinity, diacriticPages = 0;

for (const f of htmlFiles) {
  const route = routeOf(f);
  const exempt = JS_DRIVEN.has(route);
  const html = readFileSync(f, "utf8");

  // 1. render-critical CODE weight — the CSS/JS each page pulls in. HTML/content
  //    bytes are excluded on purpose: real books run to 1 MB+ of legit Arabic prose,
  //    and NFR-01's "light" is about shipping light code, not capping content.
  //    (Heavy un-chaptered books are a chunking concern, tracked separately.)
  let codeWeight = 0;
  const assets = new Set();
  for (const m of html.matchAll(/<link\b[^>]*\brel\s*=\s*["']stylesheet["'][^>]*>/gi)) {
    const h = m[0].match(/\bhref\s*=\s*["']([^"']+)["']/i);
    if (h && h[1].startsWith("/")) assets.add(h[1]);
  }
  for (const m of html.matchAll(/<script\b[^>]*\bsrc\s*=\s*["']([^"']+)["']/gi)) {
    if (m[1].startsWith("/")) assets.add(m[1]);
  }
  for (const a of assets) codeWeight += sizeOf(a);
  if (!exempt) {
    maxWeight = Math.max(maxWeight, codeWeight);
    if (codeWeight > BUDGET) over.push({ f: rel(f), kb: (codeWeight / 1024).toFixed(1) });
  }

  // 2. JS-free content presence
  const text = visibleText(html);
  if (!exempt) {
    minText = Math.min(minText, text.length);
    if (text.length < TEXT_FLOOR) thin.push({ f: rel(f), len: text.length });
  }
  if (DIACRITICS.test(text)) diacriticPages++;

  // 3. RTL Arabic
  const htmlTag = (html.match(/<html\b[^>]*>/i) || [""])[0];
  if (!/\bdir\s*=\s*["']rtl["']/i.test(htmlTag) || !/\blang\s*=\s*["']ar/i.test(htmlTag)) {
    notRtl.push(rel(f));
  }
}

console.log(
  `render-budget: ${htmlFiles.length} pages · heaviest code ${(maxWeight / 1024).toFixed(1)} KB ` +
    `(code budget ${BUDGET / 1024} KB) · min text ${minText === Infinity ? "n/a" : minText + " chars"} · ` +
    `${diacriticPages} pages with tashkeel`,
);

let failed = false;
if (over.length) {
  failed = true;
  console.error(`\n✗ ${over.length} page(s) over ${BUDGET / 1024} KB render-critical code budget:`);
  for (const o of over) console.error(`  ${o.f}: ${o.kb} KB`);
}
if (thin.length) {
  failed = true;
  console.error(`\n✗ ${thin.length} page(s) render < ${TEXT_FLOOR} chars without JS:`);
  for (const t of thin) console.error(`  ${t.f}: ${t.len} chars`);
}
if (notRtl.length) {
  failed = true;
  console.error(`\n✗ ${notRtl.length} page(s) missing dir="rtl"/lang="ar":`);
  for (const n of notRtl) console.error(`  ${n}`);
}
if (failed) process.exit(1);
console.log("✓ render budget, JS-free content & RTL Arabic all pass");
