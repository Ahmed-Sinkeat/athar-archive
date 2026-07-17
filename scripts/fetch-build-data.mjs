// Fetch build-time data files from R2 (athar-book-assets/build-data/).
// These used to live in git LFS, whose 1GB/month bandwidth quota kept
// killing CI checkouts; R2 egress is free. Files already on disk are kept
// (local dev never redownloads). Needs CLOUDFLARE_API_TOKEN in CI.
import { execFileSync } from "node:child_process";
import fs from "node:fs";
import zlib from "node:zlib";

const FILES = [
  "src/data/quran-tafsir-index.json",
  "src/data/hadith-index.json",
  // takhrij.json deliberately absent: gen-takhrij.ts regenerates it every build
  "src/content/book-lg/tafsir-ibn-kathir.md",
  "src/content/book-lg/tafsir-muyassar.md",
];

// Stored gzip-compressed in R2 — quran-tafsir-index.json is 336MB raw but
// ~31MB gzipped (Arabic prose repeats enough that it compresses ~11x), so
// this cuts the CI transfer to a fraction of the raw size. Decompressed
// locally right after download; every other file here is small enough to
// fetch as-is.
const GZIPPED = new Set(["src/data/quran-tafsir-index.json"]);

for (const f of FILES) {
  if (fs.existsSync(f) && fs.statSync(f).size > 200) {
    console.log(`✓ ${f} (present)`);
    continue;
  }
  console.log(`↓ ${f}`);
  const name = f.split("/").pop();
  if (GZIPPED.has(f)) {
    const gzPath = `${f}.gz`;
    execFileSync(
      "pnpm",
      ["exec", "wrangler", "r2", "object", "get", `athar-book-assets/build-data/${name}.gz`, "--file", gzPath, "--remote"],
      { stdio: "inherit" },
    );
    fs.writeFileSync(f, zlib.gunzipSync(fs.readFileSync(gzPath)));
    fs.unlinkSync(gzPath);
  } else {
    execFileSync(
      "pnpm",
      ["exec", "wrangler", "r2", "object", "get", `athar-book-assets/build-data/${name}`, "--file", f, "--remote"],
      { stdio: "inherit" },
    );
  }
}
