// Generate dist/_headers with a strict CSP. Scans the built HTML for inline
// executable <script> blocks (the anti-flash pre-paint script, the books
// filter, the search UI) and adds their sha256 hashes to script-src so we can
// drop 'unsafe-inline' for scripts. Runs after `astro build`. (issue #4)

import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";

const DIST = path.resolve("dist");

function walk(dir) {
  return fs.readdirSync(dir).flatMap((f) => {
    const p = path.join(dir, f);
    return fs.statSync(p).isDirectory() ? walk(p) : p.endsWith(".html") ? [p] : [];
  });
}

// inline <script> with no src and not JSON-LD (JSON-LD isn't executed → not script-src governed)
const INLINE_SCRIPT = /<script(?![^>]*\bsrc=)(?![^>]*application\/ld\+json)[^>]*>([\s\S]*?)<\/script>/g;

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
  // scripts: self + pagefind wasm + the hashed inline scripts (no 'unsafe-inline')
  `script-src 'self' 'wasm-unsafe-eval' ${scriptHashes}`,
  // styles: external only (no inline <style> / no style= attrs after the sweep).
  // JS reading-prefs use CSSOM (.style.setProperty) which CSP does not govern.
  "style-src 'self' https://fonts.googleapis.com",
  "font-src 'self' https://fonts.gstatic.com",
  "img-src 'self' data:",
  "media-src 'self' https://r2.ahlalathar.com",
  "connect-src 'self'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'none'",
].join("; ");

const out = `/*
  X-Content-Type-Options: nosniff
  X-Frame-Options: DENY
  Referrer-Policy: strict-origin-when-cross-origin
  Permissions-Policy: geolocation=(), microphone=(), camera=()
  Content-Security-Policy: ${csp}

/pagefind/*
  Cache-Control: public, max-age=86400
`;

fs.writeFileSync(path.join(DIST, "_headers"), out, "utf-8");
console.log(`✓ wrote dist/_headers — CSP with ${hashes.size} inline-script hash(es)`);
