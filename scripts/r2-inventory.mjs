// READ-ONLY R2 inventory: object count + bytes per top-level prefix of the
// athar-book-assets bucket, and which prefixes this repo still claims.
// Mutates nothing — it only issues ListObjectsV2 (the client is constructed
// read-only, so a stray PUT/DELETE throws rather than reaching the network).
// Deleting a retired prefix is a deliberate manual step; this report exists so
// that decision is made against real numbers instead of a guess.
//
//   pnpm r2:inventory            # human-readable table
//   pnpm r2:inventory --json     # machine-readable, for CI/dashboards
//   pnpm r2:inventory --selftest # no network: verifies the grouping math
//
// Needs the same credentials as the uploader (see scripts/lib/r2.mjs), except
// Object Read alone is enough.
import assert from "node:assert";
import { BUCKET, OWNED_PREFIXES, RETIRED_PREFIXES, makeClient, listPrefix } from "./lib/r2.mjs";

// Top-level prefix of a key: everything up to and including the first "/".
// Root-level objects (no slash) are grouped under "(root)" so they can't
// silently vanish from the report.
export function topPrefix(key) {
  const i = key.indexOf("/");
  return i < 0 ? "(root)" : key.slice(0, i + 1);
}

/** Fold {key,size} objects into {prefix → {count, bytes}}. Pure, for --selftest. */
export function group(objects) {
  const out = new Map();
  for (const { key, size } of objects) {
    const p = topPrefix(key);
    const row = out.get(p) ?? { count: 0, bytes: 0 };
    row.count++;
    row.bytes += size;
    out.set(p, row);
  }
  return out;
}

const human = (b) => {
  const u = ["B", "KB", "MB", "GB", "TB"];
  let i = 0;
  let n = b;
  while (n >= 1024 && i < u.length - 1) { n /= 1024; i++; }
  return `${n.toFixed(n >= 100 || i === 0 ? 0 : 1)} ${u[i]}`;
};

function status(prefix) {
  if (OWNED_PREFIXES.includes(prefix)) return "owned (uploader manages + prunes)";
  if (RETIRED_PREFIXES.includes(prefix)) return "RETIRED — obsolete, safe to delete after review";
  return "unmanaged (another job owns this — do not prune from here)";
}

function selftest() {
  const g = group([
    { key: "pages/a/b.html", size: 100 },
    { key: "pages/a/c.html", size: 50 },
    { key: "app/v1/catalog.json", size: 7 },
    { key: "loose.txt", size: 3 },
  ]);
  assert.deepStrictEqual(g.get("pages/"), { count: 2, bytes: 150 });
  assert.deepStrictEqual(g.get("app/"), { count: 1, bytes: 7 });
  assert.deepStrictEqual(g.get("(root)"), { count: 1, bytes: 3 });
  assert.strictEqual(topPrefix("app/v1/x.json"), "app/", "nested keys group by FIRST segment");
  console.log("✓ r2-inventory selftest: prefix grouping and byte totals correct");
}

async function main() {
  if (process.argv.includes("--selftest")) return selftest();
  const asJson = process.argv.includes("--json");
  const s3 = makeClient({ readOnly: true });

  const objects = [];
  // one full-bucket pass: unlike the uploader this deliberately looks OUTSIDE
  // the owned prefixes — finding what nobody manages anymore is the whole point
  const total = await listPrefix(s3, "", ({ key, size }) => objects.push({ key, size }));
  const rows = [...group(objects)].sort((a, b) => b[1].bytes - a[1].bytes);
  const totalBytes = rows.reduce((n, [, r]) => n + r.bytes, 0);

  if (asJson) {
    console.log(JSON.stringify({
      bucket: BUCKET, scannedAt: new Date().toISOString(), objects: total, bytes: totalBytes,
      prefixes: rows.map(([prefix, r]) => ({ prefix, ...r, status: status(prefix) })),
    }, null, 2));
    return;
  }

  console.log(`\nR2 inventory — ${BUCKET} (read-only, nothing was modified)`);
  console.log(`${total} object(s), ${human(totalBytes)} total\n`);
  const pad = Math.max(6, ...rows.map(([p]) => p.length));
  console.log(`${"prefix".padEnd(pad)}  ${"objects".padStart(9)}  ${"size".padStart(9)}  status`);
  console.log("-".repeat(pad + 40));
  for (const [prefix, r] of rows) {
    console.log(`${prefix.padEnd(pad)}  ${String(r.count).padStart(9)}  ${human(r.bytes).padStart(9)}  ${status(prefix)}`);
  }

  const obsolete = rows.filter(([p]) => RETIRED_PREFIXES.includes(p));
  if (obsolete.length > 0) {
    const n = obsolete.reduce((a, [, r]) => a + r.count, 0);
    const b = obsolete.reduce((a, [, r]) => a + r.bytes, 0);
    console.log(
      `\n⚠ ${n} object(s) / ${human(b)} under retired prefix(es): ${obsolete.map(([p]) => p).join(", ")}.\n` +
      `  Nothing here deletes them. After confirming the numbers above, delete them deliberately, then\n` +
      `  drop the entry from RETIRED_PREFIXES in scripts/lib/r2.mjs.`,
    );
  }
  const unmanaged = rows.filter(([p]) => !OWNED_PREFIXES.includes(p) && !RETIRED_PREFIXES.includes(p));
  if (unmanaged.length > 0) {
    console.log(`\nℹ unmanaged prefix(es): ${unmanaged.map(([p]) => p).join(", ")} — owned by another job, left alone.`);
  }
}

await main();
