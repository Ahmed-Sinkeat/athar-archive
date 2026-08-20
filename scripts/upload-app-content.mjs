// Publishes one already-generated, signed app/v2 generation to its dedicated
// R2 bucket. Immutable objects go first; the mutable signed root goes last.
// Old immutable generations are deliberately retained for clients that fetched
// the previous root immediately before publication.
import crypto from "node:crypto";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { listPrefix, makeClient, md5 } from "./lib/r2.mjs";

const LOCAL_ROOT = path.resolve("dist/app-content/app/v2");
const PREFIX = "app/v2/";
const IMMUTABLE_CACHE = "public, max-age=31536000, immutable";
const ROOT_CACHE = "no-cache";
const HASH_RE = /^[0-9a-f]{64}$/;

const CONTENT_TYPES = new Map([
  [".json", "application/json; charset=utf-8"],
  [".idx", "application/json; charset=utf-8"],
  [".athar", "application/vnd.athar+gzip"],
  [".opus", "audio/ogg; codecs=opus"],
  [".mp3", "audio/mpeg"],
]);

function sha256(bytes) {
  return crypto.createHash("sha256").update(bytes).digest("hex");
}

function readJson(filePath) {
  try {
    return JSON.parse(fs.readFileSync(filePath, "utf8"));
  } catch (error) {
    throw new Error(`${filePath}: invalid JSON (${error.message || error})`);
  }
}

function safeRelative(relative) {
  if (typeof relative !== "string" || !relative || relative.startsWith("/") || relative.includes("..")) {
    throw new Error(`unsafe app-content object path ${String(relative)}`);
  }
  return relative;
}

function localArtifact(localRoot, relative, expectedHash, expectedSize) {
  safeRelative(relative);
  if (!HASH_RE.test(expectedHash)) throw new Error(`${relative}: expected hash is not full SHA-256`);
  if (path.basename(relative).split(".")[0] !== expectedHash) {
    throw new Error(`${relative}: immutable filename is not its declared SHA-256`);
  }
  const filePath = path.join(localRoot, relative);
  const bytes = fs.readFileSync(filePath);
  if (sha256(bytes) !== expectedHash) throw new Error(`${relative}: SHA-256 differs from signed catalog chain`);
  if (expectedSize != null && bytes.byteLength !== expectedSize) {
    throw new Error(`${relative}: size ${bytes.byteLength} differs from declared ${expectedSize}`);
  }
  return { key: `${PREFIX}${relative}`, filePath, bytes };
}

function decodeEnvelope(localRoot, keyId, publicKey) {
  const envelopePath = path.join(localRoot, "index.json");
  const envelopeBytes = fs.readFileSync(envelopePath);
  const envelope = readJson(envelopePath);
  if (envelope.envelope !== 1 || typeof envelope.payload !== "string" || !Array.isArray(envelope.signatures) || envelope.signatures.length === 0) {
    throw new Error("index.json is not a signed app/v2 envelope");
  }
  if (!/^[A-Za-z0-9_-]+$/.test(envelope.payload)) throw new Error("index.json payload is not canonical base64url");
  const payloadBytes = Buffer.from(envelope.payload, "base64url");
  if (payloadBytes.toString("base64url") !== envelope.payload) throw new Error("index.json payload is not canonical base64url");
  const accepted = envelope.signatures.some((signature) => {
    if (signature?.keyId !== keyId || signature.alg !== "SHA256withRSA" || typeof signature.value !== "string") return false;
    if (!/^[A-Za-z0-9_-]+$/.test(signature.value)) return false;
    const bytes = Buffer.from(signature.value, "base64url");
    if (bytes.toString("base64url") !== signature.value) return false;
    return crypto.createVerify("RSA-SHA256").update(payloadBytes).end().verify(publicKey, bytes);
  });
  if (!accepted) throw new Error(`index.json has no valid signature from ${keyId}`);
  const payload = JSON.parse(payloadBytes.toString("utf8"));
  const unsignedPath = path.join(localRoot, "index.payload.json");
  if (!fs.existsSync(unsignedPath) || !payloadBytes.equals(fs.readFileSync(unsignedPath))) {
    throw new Error("index.json does not sign the adjacent index.payload.json bytes");
  }
  return { envelopeBytes, payload };
}

export function validateLocalGeneration(keyId, publicKey, localRoot = LOCAL_ROOT) {
  if (!keyId || !publicKey) throw new Error("a trusted content-signing key is required before publication");
  const { envelopeBytes, payload } = decodeEnvelope(localRoot, keyId, publicKey);
  if (payload.schema !== 2 || payload.minAppSchema !== 2) throw new Error("unsupported app-content root schema");
  const catalogArtifact = localArtifact(localRoot, payload.catalog?.path, payload.catalog?.hash, payload.catalog?.size);
  const tombstoneArtifact = localArtifact(localRoot, payload.tombstones?.path, payload.tombstones?.hash, payload.tombstones?.size);
  const catalog = readJson(catalogArtifact.filePath);
  if (catalog.schema !== 2 || !Array.isArray(catalog.entries)) throw new Error("malformed app/v2 catalog");

  const immutable = new Map([
    [catalogArtifact.key, catalogArtifact],
    [tombstoneArtifact.key, tombstoneArtifact],
  ]);
  for (const entry of catalog.entries) {
    const refs = [
      [entry.pkg?.path, entry.pkg?.hash, entry.pkg?.size],
      [entry.pkg?.idxPath, entry.pkg?.idxHash, entry.pkg?.idxSize],
      ...(Array.isArray(entry.audio) ? entry.audio.map((audio) => [audio.path, audio.hash, audio.size]) : []),
    ];
    for (const [relative, hash, size] of refs) {
      const artifact = localArtifact(localRoot, relative, hash, size);
      immutable.set(artifact.key, artifact);
    }
  }
  return {
    immutable: [...immutable.values()].sort((a, b) => a.key < b.key ? -1 : a.key > b.key ? 1 : 0),
    root: { key: `${PREFIX}index.json`, bytes: envelopeBytes },
  };
}

async function upload(s3, artifact, cacheControl) {
  const extension = artifact.key.endsWith(".athar.idx") ? ".idx" : path.extname(artifact.key);
  const response = await s3("PUT", artifact.key, {
    body: artifact.bytes,
    contentType: CONTENT_TYPES.get(extension) ?? "application/octet-stream",
    cacheControl,
  });
  if (!response.ok) throw new Error(`${artifact.key}: HTTP ${response.status}: ${(await response.text()).slice(0, 300)}`);
  const etag = (response.headers.get("etag") ?? "").replaceAll('"', "");
  if (etag && etag !== md5(artifact.bytes)) throw new Error(`${artifact.key}: R2 ETag differs after upload`);
}

async function main() {
  if (process.argv.includes("--selftest")) return selftest();
  const bucket = process.env.ATHAR_APP_R2_BUCKET;
  if (!bucket) throw new Error("ATHAR_APP_R2_BUCKET is required; the app must use its dedicated R2 bucket");
  const publicKeyFile = process.env.ATHAR_CONTENT_SIGNING_PUBLIC_KEY_FILE;
  const signingKeyId = process.env.ATHAR_CONTENT_SIGNING_KEY_ID;
  if (!publicKeyFile || !signingKeyId) {
    throw new Error("ATHAR_CONTENT_SIGNING_PUBLIC_KEY_FILE and ATHAR_CONTENT_SIGNING_KEY_ID are required for pre-publish verification");
  }
  const publicKey = crypto.createPublicKey(fs.readFileSync(path.resolve(publicKeyFile), "utf8"));
  if (publicKey.asymmetricKeyType !== "rsa" || (publicKey.asymmetricKeyDetails?.modulusLength ?? 0) < 3072) {
    throw new Error("the trusted content-signing public key must be RSA-3072 or stronger");
  }
  const generation = validateLocalGeneration(signingKeyId, publicKey);
  if (process.argv.includes("--validate-only")) {
    console.log(`\u2713 app-content generation: signed root and ${generation.immutable.length} immutable artifact(s) verified`);
    return;
  }
  const s3 = makeClient({
    bucket,
    accessKeyId: process.env.ATHAR_APP_R2_ACCESS_KEY_ID,
    secretAccessKey: process.env.ATHAR_APP_R2_SECRET_ACCESS_KEY,
    credentialNames: "ATHAR_APP_R2_ACCESS_KEY_ID / ATHAR_APP_R2_SECRET_ACCESS_KEY",
  });

  const remote = new Map();
  await listPrefix(s3, PREFIX, (object) => remote.set(object.key, object.etag));
  const changed = generation.immutable.filter((artifact) => remote.get(artifact.key) !== md5(artifact.bytes));
  for (const artifact of changed) await upload(s3, artifact, IMMUTABLE_CACHE);
  console.log(`immutable: ${changed.length} uploaded, ${generation.immutable.length - changed.length} unchanged`);

  // Publication point. Never move this above an immutable upload.
  if (remote.get(generation.root.key) !== md5(generation.root.bytes)) {
    await upload(s3, generation.root, ROOT_CACHE);
    console.log("root: app/v2/index.json published last");
  } else {
    console.log("root: unchanged");
  }
}

function writeFixture(root, relative, bytes) {
  const filePath = path.join(root, relative);
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, bytes);
}

function selftest() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "athar-app-publish-"));
  try {
    const pkg = Buffer.from("package fixture");
    const idx = Buffer.from('{"schema":2}\n');
    const pkgHash = sha256(pkg);
    const idxHash = sha256(idx);
    const pkgPath = `content/book/fixture/${pkgHash}.athar`;
    const idxPath = `content/book/fixture/${idxHash}.athar.idx`;
    writeFixture(root, pkgPath, pkg);
    writeFixture(root, idxPath, idx);
    const catalogBytes = Buffer.from(`${JSON.stringify({
      schema: 2,
      entries: [{ id: "fixture", coll: "book", pkg: {
        path: pkgPath, hash: pkgHash, size: pkg.byteLength,
        idxPath, idxHash, idxSize: idx.byteLength,
      } }],
    })}\n`);
    const tombstoneBytes = Buffer.from('{"schema":2,"since":"2026-08-19","deleted":[]}\n');
    const catalogHash = sha256(catalogBytes);
    const tombstoneHash = sha256(tombstoneBytes);
    const catalogPath = `catalog/${catalogHash}.json`;
    const tombstonePath = `tombstones/${tombstoneHash}.json`;
    writeFixture(root, catalogPath, catalogBytes);
    writeFixture(root, tombstonePath, tombstoneBytes);
    const payloadBytes = Buffer.from(`${JSON.stringify({
      schema: 2,
      generationId: "selftest",
      catalog: { path: catalogPath, hash: catalogHash, size: catalogBytes.byteLength },
      tombstones: { path: tombstonePath, hash: tombstoneHash, size: tombstoneBytes.byteLength },
      minAppSchema: 2,
    })}\n`);
    const keys = crypto.generateKeyPairSync("rsa", { modulusLength: 3072 });
    const signature = crypto.createSign("RSA-SHA256").update(payloadBytes).end().sign(keys.privateKey).toString("base64url");
    const envelopeBytes = Buffer.from(`${JSON.stringify({
      envelope: 1,
      payload: payloadBytes.toString("base64url"),
      signatures: [{ keyId: "selftest", alg: "SHA256withRSA", value: signature }],
    })}\n`);
    writeFixture(root, "index.payload.json", payloadBytes);
    writeFixture(root, "index.json", envelopeBytes);
    const valid = validateLocalGeneration("selftest", keys.publicKey, root);
    if (valid.immutable.length !== 4) throw new Error("selftest did not collect the complete immutable chain");
    fs.writeFileSync(path.join(root, pkgPath), "tampered");
    try {
      validateLocalGeneration("selftest", keys.publicKey, root);
      throw new Error("selftest accepted a tampered package");
    } catch (error) {
      if (!String(error.message || error).includes("SHA-256 differs")) throw error;
    }
    console.log("✓ app-content publisher: signature and full artifact hash chain verified; tamper rejected");
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(new URL(import.meta.url).pathname)) {
  main().catch((error) => {
    console.error(`✗ app-content publish failed: ${error.message || error}`);
    process.exitCode = 1;
  });
}
