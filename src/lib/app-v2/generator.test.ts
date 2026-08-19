import { generateKeyPairSync } from "node:crypto";
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { generateAppContent } from "./generator.js";
import { jsonBytes, verifyRootEnvelope } from "./package.js";

const temporaryRoots: string[] = [];

function fixtureRoot() {
  const root = mkdtempSync(path.join(tmpdir(), "athar-app-v2-"));
  temporaryRoots.push(root);
  mkdirSync(path.join(root, "src/content/book"), { recursive: true });
  mkdirSync(path.join(root, "src/content/person"), { recursive: true });
  writeFileSync(path.join(root, "src/content/person/author.md"), [
    "---",
    'title: "المؤلف"',
    "status: published",
    'died: "١٢٣ هـ"',
    "---",
    "",
  ].join("\n"));
  writeFileSync(path.join(root, "src/content/book/fixture.md"), [
    "---",
    'title: "الكتاب"',
    "status: published",
    "person: author",
    "topics: [topic-b, topic-a]",
    "---",
    "## الباب",
    "",
    "نص **مهمّ**",
    "",
  ].join("\n"));
  return root;
}

afterEach(() => {
  for (const root of temporaryRoots.splice(0)) rmSync(root, { recursive: true, force: true });
});

describe("app/v2 content generator", () => {
  it("writes a signed content-addressed generation and reuses its stable IDs", async () => {
    const root = fixtureRoot();
    const outputRoot = path.join(root, "out");
    const sidecarRoot = path.join(root, "content-ids");
    const keys = generateKeyPairSync("rsa", { modulusLength: 3_072 });
    const options = {
      repositoryRoot: root,
      outputRoot,
      sidecarRoot,
      targets: [{ coll: "book", id: "fixture" }] as const,
    };
    const first = await generateAppContent({
      ...options,
      signing: { keyId: "test", privateKey: keys.privateKey },
    });
    expect(first.entities[0]).toMatchObject({ version: 1, blocks: 2, createdIds: 2, reusedIds: 0 });
    expect(first.catalog.entries[0]).toMatchObject({
      title: "الكتاب",
      personName: "المؤلف",
      died: 123,
      kind: "كتاب",
      topics: ["topic-a", "topic-b"],
    });
    expect(verifyRootEnvelope(jsonBytes(first.envelope), new Map([["test", keys.publicKey]]))).toEqual(first.rootPayload);
    const appRoot = path.join(outputRoot, "app/v2");
    expect(existsSync(path.join(appRoot, "index.json"))).toBe(true);
    const catalogBytes = readFileSync(path.join(appRoot, first.rootPayload.catalog.path));
    expect(catalogBytes.byteLength).toBe(first.rootPayload.catalog.size);

    const second = await generateAppContent(options);
    expect(second.entities[0]).toMatchObject({ version: 1, createdIds: 0, reusedIds: 2 });
    expect(second.rootPayload).toEqual(first.rootPayload);
    expect(existsSync(path.join(appRoot, "index.json"))).toBe(false);
  });
});
