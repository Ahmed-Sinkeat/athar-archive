// Pushes dist/r2-upload/ (chapter bodies + tafsir fragments — see gen-book-chapters.ts
// and gen-tafsir-frags.ts) to the athar-book-assets R2 bucket over R2's
// S3-compatible API — direct signed HTTPS, no workerd. The previous
// getPlatformProxy/remote-binding version tunneled every PUT through a local
// workerd process whose proxy dropped connections under load (kj disconnects,
// 502s) and took ~28min for a full re-upload; direct PUTs at higher
// concurrency with real retries do it in a few minutes.
//
// Needs env (CI secrets / local shell):
//   CLOUDFLARE_ACCOUNT_ID  — already present for wrangler
//   R2_ACCESS_KEY_ID / R2_SECRET_ACCESS_KEY — dashboard → R2 → Manage API
//     tokens → create token with Object Read & Write on athar-book-assets
//
// Diff strategy: one ListObjectsV2 pass per local top-level prefix (pages/,
// tafsir-frag/) builds a remote {key → md5} map — R2's ETag IS the md5 for
// single-part uploads (both S3 PUTs and the old binding PUTs), so this stays
// compatible with objects uploaded by the previous version and needs no custom
// metadata. Only new/changed files are PUT; stale keys under those prefixes are
// deleted. Listing is scoped to local prefixes so build-data/ (uploaded by
// other jobs into the same bucket) can never be touched or pruned.
// ponytail: a top-level prefix REMOVED from the build entirely stops being
// listed, so its remote objects linger — delete them by hand if that ever
// happens (has never happened; prefixes are pages/ and tafsir-frag/).
//
// `--selftest` verifies the SigV4 signer against the worked example in AWS's
// SigV4 docs (no network) — run it after touching any signing code.
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import assert from "node:assert";

const ROOT = path.resolve("dist/r2-upload");
const BUCKET = "athar-book-assets";
const CONCURRENCY = 64;
const RETRIES = 5;
const CONTENT_TYPE = { ".md": "text/markdown; charset=utf-8", ".html": "text/html; charset=utf-8" };

const sha256hex = (d) => crypto.createHash("sha256").update(d).digest("hex");
const hmac = (key, d) => crypto.createHmac("sha256", key).update(d).digest();
const md5 = (buf) => crypto.createHash("md5").update(buf).digest("hex");
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
// RFC 3986 (S3 canonical form): encodeURIComponent plus the five it leaves bare
const enc = (s) => encodeURIComponent(s).replace(/[!'()*]/g, (c) => "%" + c.charCodeAt(0).toString(16).toUpperCase());

// --- SigV4 core (kept pure so --selftest can drive it with the AWS vector) ---
function sigv4({ method, uri, query, headers, payloadHash, amzDate, region, keyId, secret }) {
  const date = amzDate.slice(0, 8);
  const canonicalQuery = Object.entries(query)
    .map(([k, v]) => `${enc(k)}=${enc(String(v))}`)
    .sort()
    .join("&");
  // callers pass lowercase header names — canonical form needs no re-casing
  const names = Object.keys(headers).sort();
  const canonicalHeaders = names.map((h) => `${h}:${String(headers[h]).trim()}\n`).join("");
  const signedHeaders = names.join(";");
  const canonicalRequest = [method, uri, canonicalQuery, canonicalHeaders, signedHeaders, payloadHash].join("\n");
  const scope = `${date}/${region}/s3/aws4_request`;
  const stringToSign = ["AWS4-HMAC-SHA256", amzDate, scope, sha256hex(canonicalRequest)].join("\n");
  const kSigning = hmac(hmac(hmac(hmac(`AWS4${secret}`, date), region), "s3"), "aws4_request");
  const signature = crypto.createHmac("sha256", kSigning).update(stringToSign).digest("hex");
  return {
    signature,
    authorization: `AWS4-HMAC-SHA256 Credential=${keyId}/${scope}, SignedHeaders=${signedHeaders}, Signature=${signature}`,
    canonicalQuery,
  };
}

function selftest() {
  // Worked example from AWS "Signature Version 4 signing process" docs
  // (GET test.txt, examplebucket, us-east-1, 20130524) — documented signature
  // below. If this asserts, the signer core is wrong; do not upload with it.
  const { signature } = sigv4({
    method: "GET",
    uri: "/test.txt",
    query: {},
    headers: {
      host: "examplebucket.s3.amazonaws.com",
      range: "bytes=0-9",
      "x-amz-content-sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      "x-amz-date": "20130524T000000Z",
    },
    payloadHash: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    amzDate: "20130524T000000Z",
    region: "us-east-1",
    keyId: "AKIAIOSFODNN7EXAMPLE",
    secret: "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
  });
  assert.strictEqual(signature, "f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41");
  console.log("✓ sigv4 selftest: matches the AWS documentation vector");

  // prune guard: the real incident (36941 of 75290) must be refused; a genuine
  // removal — even a big multi-hundred-chapter book — must still prune.
  assert.strictEqual(pruneTooLarge(36941, 75290), true, "catastrophic mass-prune must be refused");
  assert.strictEqual(pruneTooLarge(600, 75290), false, "removing one big book must still prune");
  assert.strictEqual(pruneTooLarge(400, 3000), false, "under the absolute floor → prune");
  assert.strictEqual(pruneTooLarge(700, 1000), true, "70% of a small bucket → refuse");
  console.log("✓ prune-guard selftest: mass deletions refused, real removals allowed");
}

// --- request helper: sign + fetch + retry on 5xx/network ---
function makeClient() {
  const account = process.env.CLOUDFLARE_ACCOUNT_ID;
  const keyId = process.env.R2_ACCESS_KEY_ID;
  const secret = process.env.R2_SECRET_ACCESS_KEY;
  if (!account || !keyId || !secret) {
    console.error(
      "✗ upload-r2-assets: missing CLOUDFLARE_ACCOUNT_ID / R2_ACCESS_KEY_ID / R2_SECRET_ACCESS_KEY.\n" +
        "  Create S3 credentials: Cloudflare dashboard → R2 → Manage API tokens →\n" +
        `  Object Read & Write scoped to ${BUCKET}, then export the two keys.`
    );
    process.exit(1);
  }
  const host = `${account}.r2.cloudflarestorage.com`;

  return async function s3(method, key, { query = {}, body, contentType } = {}) {
    const uri = `/${BUCKET}` + (key ? "/" + key.split("/").map(enc).join("/") : "");
    const payloadHash = sha256hex(body ?? "");
    let lastErr;
    for (let attempt = 1; attempt <= RETRIES; attempt++) {
      const amzDate = new Date().toISOString().replace(/[-:]|\.\d{3}/g, "");
      // host is signed but not passed to fetch (the URL supplies it; undici
      // treats an explicit Host header as forbidden)
      const signHeaders = { host, "x-amz-content-sha256": payloadHash, "x-amz-date": amzDate };
      if (contentType) signHeaders["content-type"] = contentType;
      const { authorization, canonicalQuery } = sigv4({
        method, uri, query, headers: signHeaders, payloadHash, amzDate, region: "auto", keyId, secret,
      });
      const { host: _h, ...sendHeaders } = signHeaders;
      try {
        const res = await fetch(`https://${host}${uri}${canonicalQuery ? "?" + canonicalQuery : ""}`, {
          method,
          headers: { ...sendHeaders, authorization },
          body,
        });
        if (res.status >= 500) throw new Error(`HTTP ${res.status}: ${(await res.text()).slice(0, 200)}`);
        return res;
      } catch (e) {
        lastErr = e;
        if (attempt < RETRIES) await sleep(attempt * 2000);
      }
    }
    throw lastErr;
  };
}

const unxml = (s) =>
  s.replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&amp;/g, "&");

/** Paginate ListObjectsV2 under `prefix` → {key → md5}. ETag = md5 for single-part uploads. */
async function listPrefix(s3, prefix) {
  const index = new Map();
  let token;
  do {
    const query = { "list-type": "2", "max-keys": "1000", prefix };
    if (token) query["continuation-token"] = token;
    const res = await s3("GET", "", { query });
    if (!res.ok) throw new Error(`list ${prefix}: HTTP ${res.status}: ${(await res.text()).slice(0, 300)}`);
    const xml = await res.text();
    for (const m of xml.matchAll(/<Contents>([\s\S]*?)<\/Contents>/g)) {
      const key = unxml(m[1].match(/<Key>([\s\S]*?)<\/Key>/)[1]);
      const etag = unxml(m[1].match(/<ETag>([\s\S]*?)<\/ETag>/)[1]).replace(/"/g, "");
      index.set(key, etag);
    }
    token = /<IsTruncated>true<\/IsTruncated>/.test(xml)
      ? unxml(xml.match(/<NextContinuationToken>([\s\S]*?)<\/NextContinuationToken>/)[1])
      : undefined;
  } while (token);
  return index;
}

// Prune safety valve. A healthy deploy prunes a handful of objects (a book
// edited or unpublished). Deleting a large FRACTION of the whole bucket means
// the LOCAL build is incomplete — a broken chapter-shard merge, a failed
// download — not that that much content was really removed. Pruning then wipes
// live pages that are merely absent from THIS build (real incident: a
// half-empty merge pruned ~37k live chapter pages). Refuse in that case. Both
// conditions must hold, so removing one big multi-hundred-chapter book (a real
// action, well under the fraction) still prunes normally. Pure for --selftest.
const PRUNE_FRACTION_LIMIT = 0.1;
const PRUNE_ABS_FLOOR = 500;
function pruneTooLarge(deleteCount, remoteSize) {
  return deleteCount > PRUNE_ABS_FLOOR && deleteCount > remoteSize * PRUNE_FRACTION_LIMIT;
}

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
  if (process.argv.includes("--selftest")) return selftest();
  if (!fs.existsSync(ROOT)) {
    console.log("✓ upload-r2-assets: nothing to upload (dist/r2-upload missing — run the build first)");
    return;
  }
  const s3 = makeClient();

  // 1. Enumerate local files and compute their md5s (all local, fast).
  const files = walk(ROOT);
  const localIndex = new Map(
    files.map((f) => [path.relative(ROOT, f).split(path.sep).join("/"), { file: f, hash: md5(fs.readFileSync(f)) }])
  );

  // 2. One paginated list pass per local top-level prefix.
  const prefixes = fs.readdirSync(ROOT).filter((n) => fs.statSync(path.join(ROOT, n)).isDirectory());
  console.log(`Scanning remote bucket (${files.length} local files, prefixes: ${prefixes.join(", ")})…`);
  const remoteIndex = new Map();
  for (const p of prefixes) for (const [k, v] of await listPrefix(s3, `${p}/`)) remoteIndex.set(k, v);
  console.log(`  remote: ${remoteIndex.size} objects indexed`);

  // 3. Only PUT files that are new or whose md5 changed.
  const toUpload = [...localIndex.entries()].filter(([key, { hash }]) => remoteIndex.get(key) !== hash);
  console.log(`  ${toUpload.length} to upload, ${files.length - toUpload.length} unchanged (skipped)`);

  // 4. Prune remote objects (within the listed prefixes only) that no longer
  //    exist in the build — otherwise unpublishing/renaming a book leaves its
  //    old chapter pages live in R2, still served by the route.
  const toDelete = [...remoteIndex.keys()].filter((key) => !localIndex.has(key));
  let deleted = 0;
  let pruneSkipped = 0;
  if (toDelete.length > 0 && pruneTooLarge(toDelete.length, remoteIndex.size) && process.env.PRUNE_ALLOW_LARGE !== "1") {
    // dead-man's switch: don't wipe live pages a broken build merely omitted
    pruneSkipped = toDelete.length;
    console.error(
      `⚠ REFUSING to prune ${toDelete.length} object(s) — ${((toDelete.length / remoteIndex.size) * 100).toFixed(1)}% ` +
      `of the ${remoteIndex.size} remote objects. A deletion that large almost always means an INCOMPLETE local ` +
      `build (e.g. a broken chapter-shard merge), not a real removal — pruning would take live pages offline. ` +
      `Skipping deletion; uploads still proceed. If this mass removal is genuinely intended, re-run with PRUNE_ALLOW_LARGE=1.`
    );
  } else if (toDelete.length > 0) {
    console.log(`  ${toDelete.length} stale remote object(s) to delete`);
    let nextDel = 0;
    await Promise.all(
      Array.from({ length: Math.min(CONCURRENCY, toDelete.length) }, async () => {
        while (nextDel < toDelete.length) {
          await s3("DELETE", toDelete[nextDel++]);
          deleted++;
        }
      })
    );
    console.log(`  deleted ${deleted} stale object(s)`);
  }

  if (toUpload.length === 0) {
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
      try {
        const res = await s3("PUT", key, {
          body,
          contentType: CONTENT_TYPE[path.extname(file)] || "application/octet-stream",
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}: ${(await res.text()).slice(0, 200)}`);
        // single-part PUT ⇒ ETag must equal our md5 — catches silent corruption
        const etag = (res.headers.get("etag") || "").replace(/"/g, "");
        if (etag && etag !== hash) throw new Error(`etag mismatch (${etag} ≠ ${hash})`);
        done++;
        if (done % 500 === 0) console.log(`  ${done}/${toUpload.length} uploaded…`);
      } catch (e) {
        failed++;
        console.error(`${key}: ${e.message || e}`);
      }
    }
  }

  await Promise.all(Array.from({ length: CONCURRENCY }, worker));
  const pruneNote = pruneSkipped ? `, ${pruneSkipped} prune SKIPPED (guard)` : "";
  console.log(`✓ upload-r2-assets: ${done} uploaded, ${files.length - toUpload.length} unchanged, ${deleted} pruned${pruneNote}, ${failed} failed — ${files.length} total`);
  if (failed > 0) process.exit(1);
}

main();
