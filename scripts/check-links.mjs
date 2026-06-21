#!/usr/bin/env node
// أهل الأثر — link integrity check (P8)
// Verifies every internal href/src in dist/ resolves to a built page/asset or a
// redirect source, and that _redirects targets resolve. In-page fragment (#anchor)
// misses are reported as warnings (some anchors are JS-enhanced). Run post-build.
//   pnpm check:links

import { readdirSync, readFileSync, existsSync } from "node:fs";
import { join, relative } from "node:path";

const DIST = join(process.cwd(), "dist");
if (!existsSync(DIST)) {
  console.error("✗ dist/ not found — run `pnpm build` first.");
  process.exit(1);
}

const walk = (dir) =>
  readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
    const p = join(dir, e.name);
    return e.isDirectory() ? walk(p) : [p];
  });

const norm = (p) => ((p.length > 1 && p.endsWith("/") ? p.slice(0, -1) : p) || "/");
const toUrl = (file) => "/" + relative(DIST, file).split(/[\\/]/).join("/");
const decode = (s) => { try { return decodeURIComponent(s); } catch { return s; } };
const rel = (f) => relative(process.cwd(), f);

const routeOf = (file) => {
  const raw = toUrl(file);
  if (raw.endsWith("/index.html")) return norm(raw.slice(0, -"index.html".length));
  if (raw.endsWith(".html")) return norm(raw.slice(0, -".html".length));
  return norm(raw);
};

const collectIds = (file) => {
  const html = readFileSync(file, "utf8");
  const ids = new Set();
  for (const m of html.matchAll(/\b(?:id|name)\s*=\s*["']([^"']+)["']/gi)) ids.add(m[1]);
  return ids;
};

// --- 1. build the set of resolvable paths + per-page ids ---
const files = walk(DIST);
const served = new Set();
const idsByPath = new Map();

for (const f of files) {
  const raw = toUrl(f);
  if (raw.endsWith("/index.html")) {
    const route = norm(raw.slice(0, -"index.html".length));
    served.add(route);
    idsByPath.set(route, collectIds(f));
  } else if (raw.endsWith(".html")) {
    const ext = norm(raw);
    const noext = norm(raw.slice(0, -".html".length));
    served.add(ext);
    served.add(noext);
    const ids = collectIds(f);
    idsByPath.set(ext, ids);
    idsByPath.set(noext, ids);
  } else {
    served.add(norm(raw));
  }
}

// redirects: "source  target  [status]"
const redirects = new Map();
const redirFile = join(DIST, "_redirects");
if (existsSync(redirFile)) {
  for (const line of readFileSync(redirFile, "utf8").split("\n")) {
    const t = line.trim();
    if (!t || t.startsWith("#")) continue;
    const [src, dest] = t.split(/\s+/);
    if (src) { served.add(norm(src)); redirects.set(norm(src), dest); }
  }
}

// --- 2. scan every page's links ---
const SKIP = /^(?:https?:|mailto:|tel:|data:|javascript:|\/\/)/i;
const broken = [];
const anchorWarn = [];
let checked = 0;

const htmlFiles = files.filter((f) => f.endsWith(".html"));
for (const f of htmlFiles) {
  const pageDir = toUrl(f).replace(/[^/]*$/, "") || "/";
  const selfIds = idsByPath.get(routeOf(f));
  const html = readFileSync(f, "utf8");

  for (const m of html.matchAll(/\b(?:href|src)\s*=\s*["']([^"']+)["']/gi)) {
    const link = m[1].trim();
    if (!link) continue;

    // same-page fragment → check against this page's ids
    if (link.startsWith("#")) {
      if (link.length > 1) {
        checked++;
        if (selfIds && !selfIds.has(decode(link.slice(1)))) anchorWarn.push({ file: rel(f), link });
      }
      continue;
    }
    if (SKIP.test(link)) continue;

    checked++;
    let abs;
    try { abs = new URL(link, "http://x" + pageDir); }
    catch { broken.push({ file: rel(f), link }); continue; }

    const path = norm(decode(abs.pathname));
    if (!served.has(path)) { broken.push({ file: rel(f), link }); continue; }

    if (abs.hash.length > 1) {
      const ids = idsByPath.get(path);
      if (ids && !ids.has(decode(abs.hash.slice(1)))) anchorWarn.push({ file: rel(f), link });
    }
  }
}

// --- 3. redirect targets must resolve (internal only) ---
for (const [src, dest] of redirects) {
  if (/^https?:/i.test(dest)) continue;
  const path = norm(decode(dest.split("#")[0]));
  if (!served.has(path)) broken.push({ file: "dist/_redirects", link: `${src} → ${dest}` });
}

// --- report ---
console.log(`link-integrity: ${htmlFiles.length} pages · ${checked} links checked · ${served.size} resolvable paths`);
if (anchorWarn.length) {
  console.log(`\n⚠ ${anchorWarn.length} fragment anchor(s) not found in static HTML (may be JS-enhanced):`);
  for (const w of anchorWarn.slice(0, 20)) console.log(`  ${w.file}: ${w.link}`);
  if (anchorWarn.length > 20) console.log(`  …and ${anchorWarn.length - 20} more`);
}
if (broken.length) {
  console.error(`\n✗ ${broken.length} broken internal link(s):`);
  for (const b of broken) console.error(`  ${b.file}: ${b.link}`);
  process.exit(1);
}
console.log("✓ all internal links resolve");
