// Pushes dist/r2-upload/ (book chapter bodies — see gen-book-chapters.ts) to
// the athar-book-assets R2 bucket over R2's S3-compatible API — direct signed
// HTTPS, no workerd. The previous getPlatformProxy/remote-binding version
// tunneled every PUT through a local workerd process whose proxy dropped
// connections under load (kj disconnects, 502s) and took ~28min for a full
// re-upload; direct PUTs at higher concurrency with real retries do it in a
// few minutes. Signer + listing live in scripts/lib/r2.mjs (shared with the
// read-only `pnpm r2:inventory` report).
//
// Diff strategy: one ListObjectsV2 pass per OWNED_PREFIXES entry (lib/r2.mjs)
// builds a remote {key → md5} map — R2's ETag IS the md5 for single-part
// uploads (both S3 PUTs and the old binding PUTs), so this stays compatible
// with objects uploaded by the previous version and needs no custom metadata.
// Only new/changed files are PUT; stale keys under those prefixes are deleted.
// Listing is scoped to the owned prefixes so build-data/ (uploaded by other
// jobs into the same bucket) can never be touched or pruned.
//
// Ownership is DECLARED, not inferred from what dist/r2-upload happens to hold.
// The old version listed only prefixes present locally, so a prefix the build
// stopped emitting silently stopped being listed and its objects lingered in
// R2 forever (app/v1 and tafsir-frag both did exactly that). Now a retired
// prefix stays in OWNED_PREFIXES until its objects are actually gone, and a
// local directory NOT in that list is a hard error rather than a silent
// unmanaged upload.
//
// `--selftest` verifies the SigV4 signer against the worked example in AWS's
// SigV4 docs (no network) — run it after touching any signing code.
import fs from "node:fs";
import path from "node:path";
import assert from "node:assert";
import { BUCKET, OWNED_PREFIXES, RETIRED_PREFIXES, makeClient, listPrefix, md5, sigv4 } from "./lib/r2.mjs";

const ROOT = path.resolve("dist/r2-upload");
const CONCURRENCY = 64;
const CONTENT_TYPE = { ".md": "text/markdown; charset=utf-8", ".html": "text/html; charset=utf-8" };

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

  // ownership: a retired prefix must never still be claimed as owned, or the
  // next deploy would mass-delete it instead of leaving it for manual review
  const overlap = OWNED_PREFIXES.filter((p) => RETIRED_PREFIXES.includes(p));
  assert.deepStrictEqual(overlap, [], `prefix in both OWNED and RETIRED: ${overlap.join(", ")}`);
  assert.ok(OWNED_PREFIXES.every((p) => p.endsWith("/")), "owned prefixes must end in /");
  console.log(`✓ ownership selftest: owns ${OWNED_PREFIXES.join(", ")}; retired (untouched) ${RETIRED_PREFIXES.join(", ") || "none"}`);
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

// out threaded through recursion (not `out.push(...walk(abs))`) — spreading a
// child call's result as push() arguments hits V8's call-stack/argument-count
// ceiling once a single subdirectory holds enough files (~75k chapter pages
// across ~950 books after the hadith import, 2026-07-30: "Maximum call stack
// size exceeded" on the largest book's chapter directory).
function walk(dir, out = []) {
  for (const name of fs.readdirSync(dir)) {
    const abs = path.join(dir, name);
    if (fs.statSync(abs).isDirectory()) walk(abs, out);
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

  // 2. Every local top-level directory must be a declared owned prefix —
  //    an undeclared one would be uploaded but never listed, so it could
  //    never be diffed or pruned again. Fail loudly instead.
  const localPrefixes = fs.readdirSync(ROOT)
    .filter((n) => fs.statSync(path.join(ROOT, n)).isDirectory())
    .map((n) => `${n}/`);
  const undeclared = localPrefixes.filter((p) => !OWNED_PREFIXES.includes(p));
  if (undeclared.length > 0) {
    console.error(
      `✗ upload-r2-assets: dist/r2-upload holds prefix(es) ${undeclared.join(", ")} that are not in ` +
      `OWNED_PREFIXES (scripts/lib/r2.mjs). Add them there — an undeclared prefix would upload but never ` +
      `be listed, so its objects could never be diffed or pruned again.`,
    );
    process.exit(1);
  }

  // 3. One paginated list pass per OWNED prefix — including any the build no
  //    longer emits, so retiring a feature prunes its objects instead of
  //    stranding them (guard below still gates a mass deletion).
  console.log(`Scanning remote bucket (${files.length} local files, owned prefixes: ${OWNED_PREFIXES.join(", ")})…`);
  const remoteIndex = new Map();
  for (const p of OWNED_PREFIXES) await listPrefix(s3, p, ({ key, etag }) => remoteIndex.set(key, etag));
  console.log(`  remote: ${remoteIndex.size} objects indexed`);
  if (RETIRED_PREFIXES.length > 0) {
    console.log(`  note: ${RETIRED_PREFIXES.join(", ")} retired but NOT managed here — see \`pnpm r2:inventory\` before deleting`);
  }

  // 4. Only PUT files that are new or whose md5 changed.
  const toUpload = [...localIndex.entries()].filter(([key, { hash }]) => remoteIndex.get(key) !== hash);
  console.log(`  ${toUpload.length} to upload, ${files.length - toUpload.length} unchanged (skipped)`);

  // 5. Prune remote objects (within the listed prefixes only) that no longer
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
