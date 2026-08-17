// R2 S3-compatible access shared by scripts/upload-r2-assets.mjs (read/write)
// and scripts/r2-inventory.mjs (read-only). Direct signed HTTPS — no workerd,
// no SDK. Extracted from upload-r2-assets.mjs so the inventory report can list
// the bucket without a second copy of the SigV4 signer.
//
// Needs env (CI secrets / local shell):
//   CLOUDFLARE_ACCOUNT_ID  — already present for wrangler
//   R2_ACCESS_KEY_ID / R2_SECRET_ACCESS_KEY — dashboard → R2 → Manage API
//     tokens → create token scoped to the bucket (Object Read for inventory,
//     Object Read & Write for uploads)
import crypto from "node:crypto";

export const BUCKET = "athar-book-assets";

// Top-level prefixes this repo's build OWNS in that bucket. Declared, not
// derived from whatever dist/r2-upload happens to contain: a prefix the build
// stops emitting must still be listed so its now-stale objects get pruned,
// otherwise retiring a feature silently strands its objects in R2 forever
// (that is exactly what happened to app/v1 and tafsir-frag). Removing a name
// here is therefore a deliberate two-step: delete the remote objects under
// review first, THEN drop the entry.
// Not listed here = not ours: build-data/ is written by a different job and
// must never be touched or pruned by this repo's uploader.
export const OWNED_PREFIXES = ["pages/"];

// Prefixes this repo used to own and no longer generates. Reported by
// `pnpm r2:inventory`, never touched by the uploader — deletion is a reviewed,
// manual step (see docs/deploy.md). Empty this list once they're gone.
export const RETIRED_PREFIXES = ["tafsir-frag/", "app/"];

const sha256hex = (d) => crypto.createHash("sha256").update(d).digest("hex");
const hmac = (key, d) => crypto.createHmac("sha256", key).update(d).digest();
export const md5 = (buf) => crypto.createHash("md5").update(buf).digest("hex");
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
// RFC 3986 (S3 canonical form): encodeURIComponent plus the five it leaves bare
const enc = (s) => encodeURIComponent(s).replace(/[!'()*]/g, (c) => "%" + c.charCodeAt(0).toString(16).toUpperCase());

const RETRIES = 5;

// --- SigV4 core (kept pure so --selftest can drive it with the AWS vector) ---
export function sigv4({ method, uri, query, headers, payloadHash, amzDate, region, keyId, secret }) {
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

// --- request helper: sign + fetch + retry on 5xx/network ---
export function makeClient({ readOnly = false } = {}) {
  const account = process.env.CLOUDFLARE_ACCOUNT_ID;
  const keyId = process.env.R2_ACCESS_KEY_ID;
  const secret = process.env.R2_SECRET_ACCESS_KEY;
  if (!account || !keyId || !secret) {
    console.error(
      "✗ missing CLOUDFLARE_ACCOUNT_ID / R2_ACCESS_KEY_ID / R2_SECRET_ACCESS_KEY.\n" +
        "  Create S3 credentials: Cloudflare dashboard → R2 → Manage API tokens →\n" +
        `  Object Read${readOnly ? "" : " & Write"} scoped to ${BUCKET}, then export the two keys.`,
    );
    process.exit(1);
  }
  const host = `${account}.r2.cloudflarestorage.com`;

  return async function s3(method, key, { query = {}, body, contentType } = {}) {
    if (readOnly && method !== "GET" && method !== "HEAD") {
      throw new Error(`read-only client refused ${method} ${key}`);
    }
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

/**
 * Paginate ListObjectsV2 under `prefix` (pass "" for the whole bucket),
 * calling `onObject({key, etag, size})` per object. ETag = md5 for
 * single-part uploads, which is every upload this repo makes.
 */
export async function listPrefix(s3, prefix, onObject) {
  let token;
  let count = 0;
  do {
    const query = { "list-type": "2", "max-keys": "1000", prefix };
    if (token) query["continuation-token"] = token;
    const res = await s3("GET", "", { query });
    if (!res.ok) throw new Error(`list ${prefix || "(bucket root)"}: HTTP ${res.status}: ${(await res.text()).slice(0, 300)}`);
    const xml = await res.text();
    for (const m of xml.matchAll(/<Contents>([\s\S]*?)<\/Contents>/g)) {
      onObject({
        key: unxml(m[1].match(/<Key>([\s\S]*?)<\/Key>/)[1]),
        etag: unxml(m[1].match(/<ETag>([\s\S]*?)<\/ETag>/)[1]).replace(/"/g, ""),
        size: Number(m[1].match(/<Size>(\d+)<\/Size>/)?.[1] ?? 0),
      });
      count++;
    }
    token = /<IsTruncated>true<\/IsTruncated>/.test(xml)
      ? unxml(xml.match(/<NextContinuationToken>([\s\S]*?)<\/NextContinuationToken>/)[1])
      : undefined;
  } while (token);
  return count;
}
