// Generate dist/_headers with a strict CSP. Scans the built HTML for inline
// executable <script> blocks (the anti-flash pre-paint script, the books
// filter, the search UI) and adds their sha256 hashes to script-src so we can
// drop 'unsafe-inline' for scripts. Runs after `astro build`. (issue #4)

import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";

// dist/client is the asset root the @astrojs/cloudflare adapter serves (and where
// it already wrote a _headers with immutable Cache-Control for /_astro/*).
const DIST = path.resolve("dist/client");

function walk(dir) {
  return fs.readdirSync(dir).flatMap((f) => {
    const p = path.join(dir, f);
    return fs.statSync(p).isDirectory() ? walk(p) : p.endsWith(".html") ? [p] : [];
  });
}

// inline <script> with no src and not a data island (application/json, JSON-LD —
// neither is executed by the browser, so CSP script-src doesn't govern them).
// Without this exclusion, per-page data (e.g. each book's chapter-slug map)
// hashes to a distinct value and the hash list grows without bound as content
// is added, eventually exceeding Cloudflare's 2000-char-per-line _headers limit.
const INLINE_SCRIPT = /<script(?![^>]*\bsrc=)(?![^>]*\btype=["']application\/(?:ld\+json|json)["'])[^>]*>([\s\S]*?)<\/script>/g;

const hashes = new Set();
for (const file of walk(DIST)) {
  const html = fs.readFileSync(file, "utf-8");
  for (const m of html.matchAll(INLINE_SCRIPT)) {
    const code = m[1];
    if (!code.trim()) continue;
    const digest = crypto.createHash("sha256").update(code, "utf-8").digest("base64");
    hashes.add(`'sha256-${digest}'`);
  }
}

const scriptHashes = [...hashes].sort().join(" ");
const csp = [
  "default-src 'self'",
  // scripts: self + the hashed inline scripts (no 'unsafe-inline')
  `script-src 'self' ${scriptHashes}`,
  // styles: external only (no inline <style> / no style= attrs after the sweep).
  // JS reading-prefs use CSSOM (.style.setProperty) which CSP does not govern.
  "style-src 'self' https://fonts.googleapis.com",
  "font-src 'self' https://fonts.gstatic.com",
  "img-src 'self' data:",
  "media-src 'self' https://r2.arthurarchive.com",
  "connect-src 'self'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'none'",
].join("; ");

// /admin (Sveltia CMS) is self-hosted (public/admin/sveltia-cms.js) so script-src
// 'self' still holds, but the CMS itself needs to talk to GitHub's API/OAuth
// worker, load Google Fonts, and preview avatars/images from GitHub. NB:
// Cloudflare does NOT replace headers from a more general _headers rule — it
// appends, and two CSP headers enforce as their intersection (strictest wins) —
// so the /admin blocks must `!`-detach the site-wide CSP before setting theirs.
const adminCsp = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
  "font-src 'self' https://fonts.gstatic.com",
  "img-src 'self' data: https://avatars.githubusercontent.com https://raw.githubusercontent.com",
  "connect-src 'self' https://api.github.com https://raw.githubusercontent.com https://*.workers.dev",
  "frame-src 'self' https://github.com",
  "base-uri 'self'",
  "form-action 'self'",
].join("; ");

const out = `/*
  X-Content-Type-Options: nosniff
  X-Frame-Options: DENY
  Referrer-Policy: strict-origin-when-cross-origin
  Permissions-Policy: geolocation=(), microphone=(), camera=()
  Content-Security-Policy: ${csp}

/admin
  ! Content-Security-Policy
  Content-Security-Policy: ${adminCsp}

/admin/*
  ! Content-Security-Policy
  Content-Security-Policy: ${adminCsp}
`;

// Keep the adapter's existing rules (e.g. /_astro/* immutable Cache-Control) and
// append ours. Different path specificities don't conflict in Cloudflare _headers.
const headersPath = path.join(DIST, "_headers");
const existing = fs.existsSync(headersPath) ? fs.readFileSync(headersPath, "utf-8").trimEnd() + "\n\n" : "";
fs.writeFileSync(headersPath, existing + out, "utf-8");

// _headers only covers static asset responses. On-demand pages (book chapters) are
// Worker responses, so the middleware applies the same set at runtime — emit it as
// JSON for the Worker to read via ASSETS. One source of truth for the CSP hashes.
const runtimeHeaders = {
  "Content-Security-Policy": csp,
  "X-Content-Type-Options": "nosniff",
  "X-Frame-Options": "DENY",
  "Referrer-Policy": "strict-origin-when-cross-origin",
  "Permissions-Policy": "geolocation=(), microphone=(), camera=()",
  // Unique per build so the middleware's Cache API key changes on every deploy —
  // otherwise a stale cached on-demand page keeps pointing at now-deleted
  // /_astro/*.css files (hashed filenames) until it happens to be evicted.
  "X-Build-Id": crypto.randomUUID(),
};
fs.writeFileSync(path.join(DIST, "_headers.json"), JSON.stringify(runtimeHeaders), "utf-8");
console.log(`✓ wrote dist/_headers (+_headers.json) — CSP with ${hashes.size} inline-script hash(es)`);
