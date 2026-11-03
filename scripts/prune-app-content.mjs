// Delete app/v2 objects in R2 that the LIVE generation no longer references (old packages, catalogs, tombstones).
//
//   node scripts/prune-app-content.mjs            # dry run: counts and bytes, deletes nothing
//   node scripts/prune-app-content.mjs --confirm  # delete
//
// Guards: the local signed generation must equal the live app/v2/index.json (so "referenced" is the real live set),
// index.json and dict/ are never touched, and keys are re-checked against the live set right before each DELETE.
// Run it a day or more after publishing: a phone that fetched the previous root minutes ago still wants the old files.
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { listPrefix, makeClient, md5 } from "./lib/r2.mjs";
import { validateLocalGeneration } from "./upload-app-content.mjs";

const PREFIX = "app/v2/";

export function orphans(remoteKeys, liveKeys) {
  const live = new Set(liveKeys);
  return remoteKeys.filter((key) => key !== `${PREFIX}index.json` && !key.startsWith(`${PREFIX}dict/`) && !live.has(key));
}

function selftest() {
  const o = orphans(
    ["app/v2/index.json", "app/v2/dict/dict.zip", "app/v2/content/a", "app/v2/content/old", "app/v2/catalog/c"],
    ["app/v2/content/a", "app/v2/catalog/c"],
  );
  if (o.length !== 1 || o[0] !== "app/v2/content/old") throw new Error(`selftest: ${o}`);
  console.log("prune-app-content selftest ok");
}

async function main() {
  if (process.argv.includes("--selftest")) return selftest();
  const confirm = process.argv.includes("--confirm");
  for (const line of fs.readFileSync(`${process.env.HOME}/.config/athar/publish.env`, "utf8").split("\n")) {
    const m = /^\s*(?:export\s+)?([A-Z0-9_]+)=(.*)$/.exec(line);
    if (m && process.env[m[1]] === undefined) process.env[m[1]] = m[2].replace(/^(['"])(.*)\1$/, "$2");
  }
  const publicKey = crypto.createPublicKey(fs.readFileSync(path.resolve(process.env.ATHAR_CONTENT_SIGNING_PUBLIC_KEY_FILE), "utf8"));
  const generation = validateLocalGeneration(process.env.ATHAR_CONTENT_SIGNING_KEY_ID, publicKey);
  const s3 = makeClient({
    bucket: process.env.ATHAR_APP_R2_BUCKET,
    accessKeyId: process.env.ATHAR_APP_R2_ACCESS_KEY_ID,
    secretAccessKey: process.env.ATHAR_APP_R2_SECRET_ACCESS_KEY,
    credentialNames: "ATHAR_APP_R2_ACCESS_KEY_ID / ATHAR_APP_R2_SECRET_ACCESS_KEY",
  });
  const remote = new Map();
  await listPrefix(s3, PREFIX, (object) => remote.set(object.key, object));
  const root = remote.get(generation.root.key);
  if (!root || root.etag !== md5(generation.root.bytes)) {
    throw new Error("local generation is not the live one (index.json differs): publish first, or regenerate from the live state");
  }
  const live = generation.immutable.map((artifact) => artifact.key);
  const dead = orphans([...remote.keys()], live);
  const bytes = dead.reduce((sum, key) => sum + remote.get(key).size, 0);
  console.log(`${remote.size} objects in R2, ${live.length} live, ${dead.length} unreferenced (${(bytes / 1048576).toFixed(1)} MiB)`);
  if (!confirm) return console.log("dry run: nothing deleted. --confirm to delete.");
  const liveSet = new Set(live);
  for (const key of dead) {
    if (!key.startsWith(PREFIX) || liveSet.has(key)) throw new Error(`refusing to delete ${key}`);
    const res = await s3("DELETE", key);
    if (!res.ok && res.status !== 404) throw new Error(`${key}: HTTP ${res.status}`);
  }
  console.log(`deleted ${dead.length}`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(new URL(import.meta.url).pathname)) {
  main().catch((error) => { console.error(error.message || error); process.exit(1); });
}
