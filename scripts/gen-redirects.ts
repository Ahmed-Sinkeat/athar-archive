// Generate dist/_redirects (Cloudflare 301 map) from entity `aliases`.
// A changed slug moves the old id into aliases[]; this emits old→new 301s so
// published URLs never break (FR-P-04). Runs after `astro build`.

import fs from "node:fs";
import path from "node:path";
import { loadContentFromDisk } from "../src/lib/load.js";
import { hrefFor } from "../src/lib/display.js";

function main() {
  const entries = loadContentFromDisk();
  // static page moves (not slug aliases): تواصل merged into /about
  const lines: string[] = [
    "/contact /about#contact 301",
    // Retired specialist sections. Their works are all still here as ordinary
    // books, so the honest destination for every one of these hubs is /books —
    // not a 404, and not a subject page that only covers part of what they held.
    // /quran/* and /tafsir-frag/* had per-surah/per-ayah URLs with no
    // one-to-one successor; the wildcards fold them into the same landing page.
    "/quran /books 301",
    "/quran/* /books 301",
    "/hadith /books 301",
    "/tafsir /books 301",
    "/tafsir-frag/* /books 301",
  ];

  for (const e of entries) {
    if (e.data.status === "archived") continue; // archived keeps its own URL
    const aliases = Array.isArray(e.data.aliases) ? (e.data.aliases as string[]) : [];
    if (aliases.length === 0) continue;

    for (const old of aliases) {
      const to = hrefFor(e.collection, e.id);
      if (to !== "/") lines.push(`${hrefFor(e.collection, old)} ${to} 301`);
      // Books are chunked into chapter subpaths — redirect those too.
      if (e.collection === "book") lines.push(`${hrefFor(e.collection, old)}/* ${to}/:splat 301`);
    }
  }


  const out = path.resolve("dist/client/_redirects"); // adapter asset root
  fs.writeFileSync(out, lines.join("\n") + (lines.length ? "\n" : ""), "utf-8");
  console.log(`✓ wrote ${lines.length} redirect(s) → dist/client/_redirects`);
}

main();
