// Accessibility audit (issue #3): runs axe-core (WCAG 2.0/2.1 A & AA) over a
// representative page of every template, in headless Chromium against dist/.
// Usage (after `pnpm build`): node scripts/a11y-audit.mjs

import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { chromium } from "playwright-core";

const DIST = path.resolve("dist/client");
const AXE = fs.readFileSync(path.resolve("node_modules/axe-core/axe.min.js"), "utf-8");
const MIME = { ".html": "text/html", ".js": "text/javascript", ".json": "application/json", ".wasm": "application/wasm", ".css": "text/css" };

const PAGES = [
  "/", "/books", "/subjects", "/series", "/people", "/benefits", "/articles", "/questions",
  "/poem/alfiyyah-ibn-malik", "/book/al-wasitiyyah", "/series/sharh-al-wasitiyyah",
  "/series/sharh-al-wasitiyyah/lesson-1", "/person/ibn-taymiyyah", "/topic/al-asma-was-sifat",
  "/subject/aqeedah", "/benefit/tawhid-benefit", "/article/maqala-tawhid",
  "/questions/masail-al-asma-was-sifat", "/search", "/about", "/contact", "/404",
];

const server = http.createServer((req, res) => {
  let p = decodeURIComponent(req.url.split("?")[0]);
  let file = path.join(DIST, p);
  try { if (fs.statSync(file).isDirectory()) file = path.join(file, "index.html"); } catch {}
  if (!fs.existsSync(file)) file = path.join(DIST, p, "index.html");
  if (!fs.existsSync(file)) file = path.join(DIST, "404.html");
  fs.readFile(file, (err, buf) => {
    if (err) { res.writeHead(404); res.end(); return; }
    res.writeHead(200, { "content-type": MIME[path.extname(file)] || "application/octet-stream" });
    res.end(buf);
  });
});

await new Promise((r) => server.listen(0, r));
const port = server.address().port;
const browser = await chromium.launch({ executablePath: "/usr/bin/chromium", args: ["--no-sandbox"] });
const page = await browser.newPage();

const agg = new Map(); // ruleId → { impact, help, count, pages:Set, sampleHtml }
let totalNodes = 0;

for (const route of PAGES) {
  await page.goto(`http://localhost:${port}${route}`, { waitUntil: "load" });
  await page.addScriptTag({ content: AXE });
  const { violations } = await page.evaluate(async () => {
    return await window.axe.run(document, { runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"] } });
  });
  for (const v of violations) {
    if (!agg.has(v.id)) agg.set(v.id, { impact: v.impact, help: v.help, count: 0, pages: new Set(), sample: v.nodes[0]?.html?.slice(0, 120) });
    const a = agg.get(v.id);
    a.count += v.nodes.length;
    a.pages.add(route);
    totalNodes += v.nodes.length;
  }
}

await browser.close();
server.close();

const order = { critical: 0, serious: 1, moderate: 2, minor: 3 };
const rows = [...agg.entries()].sort((a, b) => (order[a[1].impact] ?? 9) - (order[b[1].impact] ?? 9));

console.log(`\naxe-core audit — ${PAGES.length} pages, ${rows.length} distinct rule(s), ${totalNodes} node(s)\n`);
if (rows.length === 0) {
  console.log("  ✓ no WCAG A/AA violations\n");
} else {
  for (const [id, a] of rows) {
    console.log(`  [${(a.impact || "?").toUpperCase()}] ${id} ×${a.count}  (${a.pages.size} page${a.pages.size > 1 ? "s" : ""})`);
    console.log(`        ${a.help}`);
    if (a.sample) console.log(`        e.g. ${a.sample}`);
  }
  console.log("");
}
process.exit(rows.some(([, a]) => a.impact === "critical" || a.impact === "serious") ? 1 : 0);
