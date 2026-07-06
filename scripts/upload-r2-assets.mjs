// Pushes dist/r2-upload/ (chapter bodies + tafsir fragments — see gen-book-chapters.ts
// and gen-tafsir-frags.ts) to the BOOK_ASSETS R2 bucket. Separate from `pnpm build`
// (like search:index for D1) since it hits the network and shouldn't run on every
// local build — invoke explicitly (CI / before deploy) via `pnpm r2:upload`.
//
// Uses getPlatformProxy (in-process R2 binding, wrangler.toml has remote=true on
// this bucket) rather than shelling out to `wrangler r2 object put` per file — the
// CLI's per-invocation startup cost made ~10k small files take over an hour.
//
// Diff strategy: one R2 list() pass builds a remote {key → md5} map up front, then
// only files whose local md5 differs (or are missing remotely) get PUT. This costs
// ~16k÷1000 = ~17 list pages instead of ~16k individual HEAD requests, cutting the
// "nothing changed" path from minutes to seconds.
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { getPlatformProxy } from "wrangler";

const ROOT = path.resolve("dist/r2-upload");
const CONCURRENCY = 24;
const CONTENT_TYPE = { ".md": "text/markdown; charset=utf-8", ".html": "text/html; charset=utf-8" };
const md5 = (buf) => crypto.createHash("md5").update(buf).digest("hex");

function walk(dir) {
  const out = [];
  for (const name of fs.readdirSync(dir)) {
    const abs = path.join(dir, name);
    if (fs.statSync(abs).isDirectory()) out.push(...walk(abs));
    else out.push(abs);
  }
  return out;
}

/** Paginate through bucket.list() and return {key → md5} for every object. */
async function buildRemoteIndex(bucket) {
  const index = new Map();
  let cursor;
  do {
    const page = await bucket.list({ limit: 1000, cursor, include: ["customMetadata"] });
    for (const obj of page.objects) {
      if (obj.customMetadata?.md5) index.set(obj.key, obj.customMetadata.md5);
    }
    cursor = page.truncated ? page.cursor : undefined;
  } while (cursor);
  return index;
}

async function main() {
  if (!fs.existsSync(ROOT)) {
    console.log("✓ upload-r2-assets: nothing to upload (dist/r2-upload missing — run the build first)");
    return;
  }
  const { env, dispose } = await getPlatformProxy({ configPath: "wrangler.toml" });
  const bucket = env.BOOK_ASSETS;

  // 1. Enumerate local files and compute their md5s (all local, fast).
  const files = walk(ROOT);
  const localIndex = new Map(
    files.map((f) => [path.relative(ROOT, f).split(path.sep).join("/"), { file: f, hash: md5(fs.readFileSync(f)) }])
  );

  // 2. One paginated list() pass to know what's already in R2.
  console.log(`Scanning remote bucket (${files.length} local files)…`);
  const remoteIndex = await buildRemoteIndex(bucket);
  console.log(`  remote: ${remoteIndex.size} objects indexed`);

  // 3. Only PUT files that are new or whose md5 changed.
  const toUpload = [...localIndex.entries()].filter(
    ([key, { hash }]) => remoteIndex.get(key) !== hash
  );
  console.log(`  ${toUpload.length} to upload, ${files.length - toUpload.length} unchanged (skipped)`);

  if (toUpload.length === 0) {
    await dispose();
    console.log("✓ upload-r2-assets: everything up to date");
    return;
  }

  let done = 0;
  let failed = 0;
  let next = 0;

  async function worker() {
    while (next < toUpload.length) {
      const [key, { file, hash }] = toUpload[next++];
      const body = fs.readFileSync(file);
      const opts = {
        httpMetadata: { contentType: CONTENT_TYPE[path.extname(file)] || "application/octet-stream" },
        customMetadata: { md5: hash },
      };
      try {
        // One retry — the remote-binding tunnel occasionally drops under load.
        try { await bucket.put(key, body, opts); }
        catch { await bucket.put(key, body, opts); }
        done++;
        if (done % 500 === 0) console.log(`  ${done}/${toUpload.length} uploaded…`);
      } catch (e) {
        failed++;
        console.error(`${key}: ${e.message || e}`);
      }
    }
  }

  await Promise.all(Array.from({ length: CONCURRENCY }, worker));
  await dispose();
  console.log(`✓ upload-r2-assets: ${done} uploaded, ${files.length - toUpload.length} unchanged, ${failed} failed — ${files.length} total`);
  if (failed > 0) process.exit(1);
}

main();
