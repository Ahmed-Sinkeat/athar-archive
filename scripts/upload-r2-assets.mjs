// Pushes dist/r2-upload/ (chapter bodies + tafsir fragments — see gen-book-chapters.ts
// and gen-tafsir-frags.ts) to the BOOK_ASSETS R2 bucket. Separate from `pnpm build`
// (like search:index for D1) since it hits the network and shouldn't run on every
// local build — invoke explicitly (CI / before deploy) via `pnpm r2:upload`.
//
// Uses getPlatformProxy (in-process R2 binding, wrangler.toml has remote=true on
// this bucket) rather than shelling out to `wrangler r2 object put` per file — the
// CLI's per-invocation startup cost made ~10k small files take over an hour;
// this does the same ~10k puts through one live binding in a couple of minutes.
import fs from "node:fs";
import path from "node:path";
import { getPlatformProxy } from "wrangler";

const ROOT = path.resolve("dist/r2-upload");
const CONCURRENCY = 12;
const CONTENT_TYPE = { ".md": "text/markdown; charset=utf-8", ".html": "text/html; charset=utf-8" };

function walk(dir) {
  const out = [];
  for (const name of fs.readdirSync(dir)) {
    const abs = path.join(dir, name);
    if (fs.statSync(abs).isDirectory()) out.push(...walk(abs));
    else out.push(abs);
  }
  return out;
}

async function main() {
  if (!fs.existsSync(ROOT)) {
    console.log("✓ upload-r2-assets: nothing to upload (dist/r2-upload missing — run the build first)");
    return;
  }
  const { env, dispose } = await getPlatformProxy({ configPath: "wrangler.toml" });
  const bucket = env.BOOK_ASSETS;

  const files = walk(ROOT);
  let done = 0;
  let failed = 0;
  let next = 0;
  async function worker() {
    while (next < files.length) {
      const file = files[next++];
      const key = path.relative(ROOT, file).split(path.sep).join("/");
      const body = fs.readFileSync(file);
      const opts = { httpMetadata: { contentType: CONTENT_TYPE[path.extname(file)] || "application/octet-stream" } };
      // The remote-binding tunnel occasionally drops a connection under load
      // (seen under concurrency 32); one retry clears it without re-running
      // the whole batch.
      try {
        try {
          await bucket.put(key, body, opts);
        } catch {
          await bucket.put(key, body, opts);
        }
        done++;
        if (done % 500 === 0) console.log(`  ${done}/${files.length} uploaded…`);
      } catch (e) {
        failed++;
        console.error(`${key}: ${e.message || e}`);
      }
    }
  }
  await Promise.all(Array.from({ length: CONCURRENCY }, worker));
  await dispose();
  console.log(`✓ upload-r2-assets: ${done} uploaded, ${failed} failed, ${files.length} total`);
  if (failed > 0) process.exit(1);
}

main();
