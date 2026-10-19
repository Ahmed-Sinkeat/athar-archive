// Delete every object under ONE retired top-level R2 prefix.
//
// This is the manual, reviewed counterpart to the uploader's automatic prune.
// The uploader deliberately refuses to touch RETIRED_PREFIXES (scripts/lib/r2.mjs)
// so retiring a feature can never mass-delete live objects as a side effect of a
// deploy. When you actually want those objects gone, you run this, on purpose,
// against one named prefix.
//
//   pnpm r2:inventory                        # ALWAYS look first
//   node scripts/r2-delete-prefix.mjs app/            # dry run, deletes nothing
//   node scripts/r2-delete-prefix.mjs app/ --confirm  # actually deletes
//   node scripts/r2-delete-prefix.mjs --selftest      # no network
//
// Guard rails, in order:
//   1. the prefix must be listed in RETIRED_PREFIXES — you cannot point this at
//      pages-v2/ (live chapter bodies) or build-data/ (another job owns it)
//   2. dry run is the default; --confirm is required to delete anything
//   3. it prints the object count and byte total and re-verifies the prefix of
//      every single key immediately before issuing its DELETE
//
// Deploy the site BEFORE running this: until the new Worker ships, the old one
// may still be serving objects out of the prefix you are about to empty.
import assert from "node:assert";
import { BUCKET, RETIRED_PREFIXES, makeClient, listPrefix } from "./lib/r2.mjs";

const CONCURRENCY = 64;

const human = (b) => {
  const u = ["B", "KB", "MB", "GB", "TB"];
  let i = 0, n = b;
  while (n >= 1024 && i < u.length - 1) { n /= 1024; i++; }
  return `${n.toFixed(n >= 100 || i === 0 ? 0 : 1)} ${u[i]}`;
};

/**
 * Is `key` genuinely under `prefix`? Checked per key right before deletion, so a
 * listing bug or an unexpected server response can never widen the blast radius
 * beyond the prefix the operator named. Pure, for --selftest.
 */
export function keyIsUnder(key, prefix) {
  return typeof key === "string" && key.length > prefix.length && key.startsWith(prefix);
}

function selftest() {
  assert.strictEqual(keyIsUnder("app/v1/catalog.json", "app/"), true);
  assert.strictEqual(keyIsUnder("app/", "app/"), false, "the bare prefix marker is not an object to delete");
  assert.strictEqual(keyIsUnder("apple/x.json", "app/"), false, "sibling prefix must not match");
  assert.strictEqual(keyIsUnder("pages/book/x.html", "app/"), false, "unrelated prefix must not match");
  assert.strictEqual(keyIsUnder("", "app/"), false);
  assert.strictEqual(keyIsUnder(null, "app/"), false);
  console.log("✓ keyIsUnder selftest: only true descendants of the named prefix match");

  // the whole point of the tool: it must be impossible to aim at a live prefix
  // pages/ held that role until 2026-08-20; pages-v2/ serves the chapters now,
  // so the guard follows the live prefix rather than the historical name.
  assert.ok(!RETIRED_PREFIXES.includes("pages-v2/"), "pages-v2/ must never be retired while it serves the site");
  assert.ok(!RETIRED_PREFIXES.includes("build-data/"), "build-data/ is owned by another job");
  assert.ok(RETIRED_PREFIXES.every((p) => p.endsWith("/")), "retired prefixes must end in /");
  console.log(`✓ target selftest: deletable prefixes are exactly [${RETIRED_PREFIXES.join(", ")}]`);
}

async function main() {
  const args = process.argv.slice(2);
  if (args.includes("--selftest")) return selftest();

  const confirm = args.includes("--confirm");
  const prefix = args.find((a) => !a.startsWith("--"));

  if (!prefix) {
    console.error(
      `usage: node scripts/r2-delete-prefix.mjs <prefix> [--confirm]\n` +
      `  deletable prefixes (from RETIRED_PREFIXES): ${RETIRED_PREFIXES.join(", ") || "(none left)"}`,
    );
    process.exit(1);
  }
  if (!RETIRED_PREFIXES.includes(prefix)) {
    console.error(
      `✗ refusing: "${prefix}" is not in RETIRED_PREFIXES (scripts/lib/r2.mjs).\n` +
      `  Deletable: ${RETIRED_PREFIXES.join(", ") || "(none)"}\n` +
      `  If a prefix is genuinely retired, add it there first — that edit is the review step.`,
    );
    process.exit(1);
  }

  const s3 = makeClient();
  process.stdout.write(`Listing ${BUCKET}/${prefix} …\n`);
  const keys = [];
  let bytes = 0;
  await listPrefix(s3, prefix, ({ key, size }) => { keys.push(key); bytes += size; });

  if (keys.length === 0) {
    console.log(`✓ nothing under ${prefix} — already empty. You can drop it from RETIRED_PREFIXES.`);
    return;
  }
  console.log(`  ${keys.length} object(s), ${human(bytes)}`);
  console.log(`  sample: ${keys.slice(0, 3).join(", ")}${keys.length > 3 ? " …" : ""}`);

  if (!confirm) {
    console.log(
      `\nDRY RUN — nothing was deleted.\n` +
      `Re-run with --confirm to delete these ${keys.length} object(s) permanently:\n` +
      `  node scripts/r2-delete-prefix.mjs ${prefix} --confirm`,
    );
    return;
  }

  let deleted = 0, failed = 0, next = 0;
  await Promise.all(Array.from({ length: Math.min(CONCURRENCY, keys.length) }, async () => {
    while (next < keys.length) {
      const key = keys[next++];
      // re-verify per key: the listing said so, but this is the last gate
      if (!keyIsUnder(key, prefix)) { failed++; console.error(`skipped out-of-prefix key: ${key}`); continue; }
      try {
        const res = await s3("DELETE", key);
        if (!res.ok && res.status !== 404) throw new Error(`HTTP ${res.status}`);
        deleted++;
        if (deleted % 500 === 0) console.log(`  ${deleted}/${keys.length} deleted…`);
      } catch (e) {
        failed++;
        console.error(`${key}: ${e.message || e}`);
      }
    }
  }));

  console.log(`✓ ${prefix}: ${deleted} deleted, ${failed} failed`);
  if (failed === 0) {
    console.log(`  Now remove "${prefix}" from RETIRED_PREFIXES in scripts/lib/r2.mjs — the cleanup isn't done until the list says so.`);
  }
  if (failed > 0) process.exit(1);
}

await main();
