// Verify Pagefind search over the REAL built site (dist/). Serves dist and
// runs queries that exist in the fixtures, printing matched title + type.
// Usage (after `pnpm build`): node scripts/pagefind-spike/verify-real.mjs

import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { chromium } from "playwright-core";

const DIST = path.resolve("dist");
const MIME = { ".html": "text/html", ".js": "text/javascript", ".json": "application/json", ".wasm": "application/wasm", ".css": "text/css" };

const server = http.createServer((req, res) => {
  let p = decodeURIComponent(req.url.split("?")[0]);
  let file = path.join(DIST, p);
  if (fs.existsSync(file) && fs.statSync(file).isDirectory()) file = path.join(file, "index.html");
  if (!fs.existsSync(file)) file = path.join(DIST, "404.html");
  fs.readFile(file, (err, buf) => {
    if (err) { res.writeHead(404); res.end(); return; }
    res.writeHead(200, { "content-type": MIME[path.extname(file)] || "application/octet-stream" });
    res.end(buf);
  });
});

const QUERIES = ["كلامنا", "التوحيد", "العقيدة", "الأسماء", "توقيفية", "الألفية"];

await new Promise((r) => server.listen(0, r));
const port = server.address().port;
const browser = await chromium.launch({ executablePath: "/usr/bin/chromium", args: ["--no-sandbox"] });
const page = await browser.newPage();
await page.goto(`http://localhost:${port}/search`);

const rows = await page.evaluate(async (queries) => {
  const pf = await import("/pagefind/pagefind.js");
  if (pf.init) await pf.init();
  const out = [];
  for (const q of queries) {
    const r = await pf.search(q);
    const data = await Promise.all(r.results.slice(0, 5).map((x) => x.data()));
    out.push({ q, hits: data.map((d) => `${d.meta?.title} [${(d.filters?.type || [])[0] || "?"}]`) });
  }
  return out;
}, QUERIES);

await browser.close();
server.close();

console.log("\nReal-content search verification (dist/):\n");
for (const { q, hits } of rows) {
  console.log(`  «${q}»  →  ${hits.length ? hits.join("  ·  ") : "(no match)"}`);
}
console.log("");
