import { generateKeyPairSync } from "node:crypto";
import { describe, expect, it } from "vitest";
import { assignStableBlockIds, sha256Hex } from "./identity.js";
import { decodePackage, encodePackage, jsonBytes, signRootPayload, verifyRootEnvelope } from "./package.js";
import { parseReadableDocument } from "./parser.js";
import type { RootPayload, SignedEnvelope } from "./contract.js";

function fixture() {
  const document = parseReadableDocument({
    coll: "book",
    id: "fixture",
    sourcePath: "fixture.md",
    body: "## باب\n\nالأول\n\nالثاني\n\nالثالث",
  });
  const identified = assignStableBlockIds(document.blocks, sha256Hex("fixture"));
  return { document, blocks: identified.blocks };
}

describe("app/v2 package framing", () => {
  it("emits deterministic independently verifiable gzip members", () => {
    const { document, blocks } = fixture();
    const first = encodePackage(document, blocks, 1, 2);
    const second = encodePackage(document, blocks, 1, 2);
    expect(first.bytes).toEqual(second.bytes);
    expect(first.index).toEqual(second.index);
    expect(first.index.frames).toHaveLength(2);
    const decoded = decodePackage(first);
    expect(decoded[0]?.[0]).toMatchObject({ t: "header", schema: 2, coll: "book", id: "fixture", blocks: 4 });
    expect(decoded.flat().filter((record) => (record as { id?: string }).id && (record as { t?: string }).t !== "header")).toHaveLength(4);

    first.bytes[first.index.frames[1]!.off] ^= 1;
    expect(() => decodePackage(first)).toThrow("frame 2 digest mismatch");
  });
});

describe("app/v2 signed root", () => {
  const payload: RootPayload = {
    schema: 2,
    generationId: "0123456789abcdef",
    catalog: { path: `catalog/${"a".repeat(64)}.json`, hash: "a".repeat(64), size: 10 },
    tombstones: { path: `tombstones/${"b".repeat(64)}.json`, hash: "b".repeat(64), size: 10 },
    minAppSchema: 2,
  };
  const keys = generateKeyPairSync("rsa", { modulusLength: 3072 });

  it("accepts a trusted RSA-3072 signature", () => {
    const envelope = signRootPayload(payload, "test-key", keys.privateKey);
    expect(verifyRootEnvelope(jsonBytes(envelope), new Map([["test-key", keys.publicKey]]))).toEqual(payload);
  });

  it("rejects payload tampering, unknown keys, and unsupported algorithms", () => {
    const signed = signRootPayload(payload, "test-key", keys.privateKey);
    expect(() => verifyRootEnvelope(jsonBytes(signed), new Map())).toThrow("not trusted");

    const tampered: SignedEnvelope = { ...signed, payload: Buffer.from('{"schema":2}\n').toString("base64url") };
    expect(() => verifyRootEnvelope(jsonBytes(tampered), new Map([["test-key", keys.publicKey]]))).toThrow("not trusted");

    const wrongAlgorithm = structuredClone(signed);
    wrongAlgorithm.signatures[0]!.alg = "unsupported" as "SHA256withRSA";
    expect(() => verifyRootEnvelope(jsonBytes(wrongAlgorithm), new Map([["test-key", keys.publicKey]]))).toThrow("not trusted");
  });

  it("rejects malformed base64url before parsing a payload", () => {
    const malformed = signRootPayload(payload, "test-key", keys.privateKey);
    malformed.payload = "%%%";
    expect(() => verifyRootEnvelope(jsonBytes(malformed), new Map([["test-key", keys.publicKey]]))).toThrow(
      "malformed root payload encoding",
    );
  });

  it("refuses signing keys below the RSA-3072 contract", () => {
    const weak = generateKeyPairSync("rsa", { modulusLength: 2_048 });
    expect(() => signRootPayload(payload, "weak", weak.privateKey)).toThrow("RSA-3072 or stronger");
  });
});
