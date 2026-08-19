import { createPrivateKey, createSign, createVerify, KeyObject, type KeyLike } from "node:crypto";
import { gunzipSync, gzipSync } from "node:zlib";
import {
  APP_CONTENT_PREFIX,
  APP_CONTENT_SCHEMA,
  APP_FRAME_BLOCKS,
  APP_PACKAGE_SPLIT_BYTES,
  type PackageArtifact,
  type PackageBlock,
  type PackageHeader,
  type PackageIndex,
  type ParsedDocument,
  type RootPayload,
  type SignedEnvelope,
} from "./contract.js";
import { sha256Hex } from "./identity.js";

export const MAX_SIGNED_DOCUMENT_BYTES = 64 * 1024;

export function jsonBytes(value: unknown): Buffer {
  return Buffer.from(`${JSON.stringify(value)}\n`, "utf8");
}

function recordBytes(value: unknown): Buffer {
  return Buffer.from(`${JSON.stringify(value)}\n`, "utf8");
}

export function encodePackage(
  document: ParsedDocument,
  blocks: PackageBlock[],
  version: number,
  frameBlocks = APP_FRAME_BLOCKS,
): PackageArtifact {
  if (!Number.isSafeInteger(version) || version < 1) throw new Error("package version must be a positive integer");
  if (!Number.isSafeInteger(frameBlocks) || frameBlocks < 1) throw new Error("frameBlocks must be a positive integer");
  if (blocks.length !== document.blocks.length) throw new Error("identified block count differs from parsed block count");
  const header: PackageHeader = {
    t: "header",
    schema: APP_CONTENT_SCHEMA,
    coll: document.coll,
    id: document.id,
    v: version,
    blocks: blocks.length,
    chapters: document.chapters,
    footnotes: document.footnotes.length,
    ...(document.pages ? { pages: document.pages } : {}),
  };

  const compressed: Buffer[] = [];
  const frames: PackageIndex["frames"] = [];
  let offset = 0;
  let uncompressed = 0;
  for (let ordinal = 0; ordinal < blocks.length; ordinal += frameBlocks) {
    const frame = blocks.slice(ordinal, ordinal + frameBlocks);
    const referenced = new Set(frame.flatMap((block) => block.f ?? []));
    const footnotes = document.footnotes.filter((footnote) => referenced.has(footnote.id));
    const records = [
      ...(ordinal === 0 ? [recordBytes(header)] : []),
      ...frame.map(recordBytes),
      ...footnotes.map((footnote) => recordBytes({ t: "fn", ...footnote })),
    ];
    const raw = Buffer.concat(records);
    const member = gzipSync(raw, { level: 9 });
    compressed.push(member);
    frames.push({
      off: offset,
      len: member.byteLength,
      ord: ordinal,
      n: frame.length,
      sha256: sha256Hex(member),
    });
    offset += member.byteLength;
    uncompressed += raw.byteLength;
  }
  const bytes = Buffer.concat(compressed);
  if (bytes.byteLength > APP_PACKAGE_SPLIT_BYTES) {
    throw new Error(
      `${document.coll}/${document.id}: package is ${bytes.byteLength} bytes; chapter-boundary multipart output is required above ${APP_PACKAGE_SPLIT_BYTES}`,
    );
  }
  const index: PackageIndex = {
    schema: APP_CONTENT_SCHEMA,
    coll: document.coll,
    entityId: document.id,
    v: version,
    frames,
  };
  return { bytes, index, indexBytes: jsonBytes(index), uncompressed };
}

export function decodePackage(artifact: PackageArtifact): unknown[][] {
  return artifact.index.frames.map((frame) => {
    const member = artifact.bytes.subarray(frame.off, frame.off + frame.len);
    if (sha256Hex(member) !== frame.sha256) throw new Error(`frame ${frame.ord} digest mismatch`);
    return gunzipSync(member)
      .toString("utf8")
      .trimEnd()
      .split("\n")
      .map((line) => JSON.parse(line) as unknown);
  });
}

function base64Url(bytes: Buffer): string {
  return bytes.toString("base64url");
}

function decodeBase64Url(value: string, label: string): Buffer {
  if (!/^[A-Za-z0-9_-]*$/.test(value) || value.length % 4 === 1) throw new Error(`malformed ${label} encoding`);
  const decoded = Buffer.from(value, "base64url");
  if (decoded.toString("base64url") !== value) throw new Error(`malformed ${label} encoding`);
  return decoded;
}

export function signRootPayload(payload: RootPayload, keyId: string, privateKey: KeyObject | string | Buffer): SignedEnvelope {
  if (!keyId) throw new Error("content signing keyId is required");
  const key = privateKey instanceof KeyObject ? privateKey : createPrivateKey(privateKey);
  if (key.asymmetricKeyType !== "rsa" || (key.asymmetricKeyDetails?.modulusLength ?? 0) < 3_072) {
    throw new Error("content signing requires an RSA-3072 or stronger private key");
  }
  const payloadBytes = jsonBytes(payload);
  if (payloadBytes.byteLength > MAX_SIGNED_DOCUMENT_BYTES) throw new Error("signed root payload exceeds 64 KiB");
  const signature = createSign("RSA-SHA256").update(payloadBytes).end().sign(key);
  const envelope: SignedEnvelope = {
    envelope: 1,
    payload: base64Url(payloadBytes),
    signatures: [{ keyId, alg: "SHA256withRSA", value: base64Url(signature) }],
  };
  if (jsonBytes(envelope).byteLength > MAX_SIGNED_DOCUMENT_BYTES) throw new Error("signed root envelope exceeds 64 KiB");
  return envelope;
}

export function verifyRootEnvelope(envelopeBytes: Buffer, trustedKeys: Map<string, KeyLike>): RootPayload {
  if (envelopeBytes.byteLength > MAX_SIGNED_DOCUMENT_BYTES) throw new Error("signed root envelope exceeds 64 KiB");
  let value: unknown;
  try {
    value = JSON.parse(envelopeBytes.toString("utf8"));
  } catch {
    throw new Error("malformed signed root envelope");
  }
  if (typeof value !== "object" || value == null) throw new Error("malformed signed root envelope");
  const envelope = value as Partial<SignedEnvelope>;
  if (envelope.envelope !== 1 || typeof envelope.payload !== "string" || !Array.isArray(envelope.signatures)) {
    throw new Error("malformed signed root envelope");
  }
  const payloadBytes = decodeBase64Url(envelope.payload, "root payload");
  if (payloadBytes.byteLength > MAX_SIGNED_DOCUMENT_BYTES) throw new Error("signed root payload exceeds 64 KiB");
  const accepted = envelope.signatures.some((signature) => {
    if (typeof signature !== "object" || signature == null) return false;
    if (signature.alg !== "SHA256withRSA") return false;
    const key = trustedKeys.get(signature.keyId);
    if (!key) return false;
    try {
      if (typeof signature.value !== "string") return false;
      return createVerify("RSA-SHA256").update(payloadBytes).end().verify(key, decodeBase64Url(signature.value, "root signature"));
    } catch {
      return false;
    }
  });
  if (!accepted) throw new Error("root signature is not trusted");
  let parsed: unknown;
  try {
    parsed = JSON.parse(payloadBytes.toString("utf8"));
  } catch {
    throw new Error("malformed signed root payload");
  }
  if (typeof parsed !== "object" || parsed == null) throw new Error("malformed signed root payload");
  const payload = parsed as Partial<RootPayload>;
  if (
    payload.schema !== APP_CONTENT_SCHEMA
    || typeof payload.generationId !== "string"
    || payload.minAppSchema !== APP_CONTENT_SCHEMA
    || typeof payload.catalog?.path !== "string"
    || !/^[0-9a-f]{64}$/.test(payload.catalog.hash)
    || payload.catalog.path !== `catalog/${payload.catalog.hash}.json`
    || !Number.isSafeInteger(payload.catalog.size)
    || typeof payload.tombstones?.path !== "string"
    || !/^[0-9a-f]{64}$/.test(payload.tombstones.hash)
    || payload.tombstones.path !== `tombstones/${payload.tombstones.hash}.json`
    || !Number.isSafeInteger(payload.tombstones.size)
  ) throw new Error("malformed signed root payload");
  return payload as RootPayload;
}

export function artifactPath(kind: "catalog" | "tombstones", hash: string): string {
  return `${kind}/${hash}.json`;
}

export function contentPath(coll: string, id: string, hash: string, suffix: ".athar" | ".athar.idx"): string {
  return `content/${coll}/${id}/${hash}${suffix}`;
}

export function rootObjectPath(relativePath: string): string {
  return `${APP_CONTENT_PREFIX}/${relativePath}`;
}

export { sha256Hex };
