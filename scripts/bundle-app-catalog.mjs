// Copies the signed root, catalog and tombstones of a generated app-content build into the
// Android app's assets, so a fresh install has a usable library before (or without) a network.
// Run after `npm run app:gen`, before building a release:
//   node scripts/bundle-app-catalog.mjs [dist/app-content/app/v2]
// The app only applies the snapshot to an EMPTY catalog and verifies it like a download.
import { copyFileSync, mkdirSync, readFileSync, rmSync, statSync } from "node:fs";
import path from "node:path";

const source = path.resolve(process.argv[2] ?? "dist/app-content/app/v2");
const target = path.resolve("android/app/src/main/assets/app-content");

const envelope = JSON.parse(readFileSync(path.join(source, "index.json"), "utf8"));
const payload = typeof envelope.payload === "string"
  ? JSON.parse(Buffer.from(envelope.payload, "base64url").toString("utf8"))
  : envelope.payload;
const files = ["index.json", payload.catalog.path, payload.tombstones.path];

rmSync(target, { recursive: true, force: true });
let bytes = 0;
for (const file of files) {
  const from = path.join(source, file);
  const to = path.join(target, file);
  mkdirSync(path.dirname(to), { recursive: true });
  copyFileSync(from, to);
  bytes += statSync(to).size;
}
console.log(`bundled generation ${payload.generationId}: ${files.length} files, ${(bytes / 1e6).toFixed(1)} MB -> ${path.relative(process.cwd(), target)}`);
