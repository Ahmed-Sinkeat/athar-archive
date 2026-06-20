// Pagefind Arabic spike: does a bare (un-diacritized) query match diacritized
// text, and does a stripped index fix it? Runs REAL Pagefind searches in
// headless Chromium against a controlled 2-page corpus.
//
// Usage: pnpm exec pagefind --site scripts/pagefind-spike/site
//        node scripts/pagefind-spike/spike-search.mjs

import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { chromium } from "playwright-core";

const SITE = path.resolve("scripts/pagefind-spike/site");
const MIME = {
  ".html": "text/html", ".js": "text/javascript", ".mjs": "text/javascript",
  ".json": "application/json", ".wasm": "application/wasm", ".css": "text/css",
};

const server = http.createServer((req, res) => {
  const urlPath = decodeURIComponent(req.url.split("?")[0]);
  const file = path.join(SITE, urlPath === "/" ? "/diacritized.html" : urlPath);
  fs.readFile(file, (err, buf) => {
    if (err) { res.writeHead(404); res.end("not found"); return; }
    res.writeHead(200, { "content-type": MIME[path.extname(file)] || "application/octet-stream" });
    res.end(buf);
  });
});

const QUERIES = [
  "كلام", // bare prefix of diacritized كَلَامُنَا
  "كَلَامُنَا", // fully diacritized query
  "لفظ", // bare standalone word
  "الإيمان", // bare, with definite article (matches الإِيمَان)
  "إيمان", // bare root WITHOUT the ال the text uses → proclitic test
  "أسماء", // bare root; text has بِأَسْمَاء (with بـ proclitic) → proclitic test
  "صفات", // bare root; text has وَصِفَاتِه (و + ـه) → clitic test
];

await new Promise((r) => server.listen(0, r));
const port = server.address().port;

const browser = await chromium.launch({ executablePath: "/usr/bin/chromium", args: ["--no-sandbox"] });
const page = await browser.newPage();
await page.goto(`http://localhost:${port}/diacritized.html`);

const rows = await page.evaluate(async (queries) => {
  const pf = await import("/pagefind/pagefind.js");
  if (pf.init) await pf.init();
  const out = [];
  for (const q of queries) {
    const r = await pf.search(q);
    const data = await Promise.all(r.results.map((x) => x.data()));
    out.push({ q, hits: data.map((d) => d.meta.title).sort() });
  }
  return out;
}, QUERIES);

await browser.close();
server.close();

console.log("\nPagefind Arabic spike — which index does each bare/diacritized query hit?\n");
console.log("query".padEnd(14), "→ matched pages");
console.log("-".repeat(50));
for (const { q, hits } of rows) {
  console.log(q.padEnd(14), "→", hits.length ? hits.join(", ") : "(no match)");
}
console.log("");
