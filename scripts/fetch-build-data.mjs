// Fetch build-time data files from R2 (athar-book-assets/build-data/).
// These used to live in git LFS, whose 1GB/month bandwidth quota kept
// killing CI checkouts; R2 egress is free. Files already on disk are kept
// (local dev never redownloads). Needs CLOUDFLARE_API_TOKEN in CI.
import { execFileSync } from "node:child_process";
import fs from "node:fs";

const FILES = [
  "src/data/quran-tafsir-index.json",
  "src/data/hadith-index.json",
  // takhrij.json deliberately absent: gen-takhrij.ts regenerates it every build
  "src/content/book-lg/tafsir-ibn-kathir.md",
  "src/content/book-lg/tafsir-muyassar.md",
];

for (const f of FILES) {
  // >200 bytes: a stale git-LFS pointer file (~130 bytes) doesn't count as present
  if (fs.existsSync(f) && fs.statSync(f).size > 200) {
    console.log(`✓ ${f} (present)`);
    continue;
  }
  console.log(`↓ ${f}`);
  execFileSync(
    "pnpm",
    ["exec", "wrangler", "r2", "object", "get", `athar-book-assets/build-data/${f.split("/").pop()}`, "--file", f, "--remote"],
    { stdio: "inherit" },
  );
}
