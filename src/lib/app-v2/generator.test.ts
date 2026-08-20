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

  it("derives list excerpts and opening verses at build time", async () => {
    const root = fixtureRoot();
    mkdirSync(path.join(root, "src/content/article"), { recursive: true });
    mkdirSync(path.join(root, "src/content/poem"), { recursive: true });
    writeFileSync(path.join(root, "src/content/article/article.md"), [
      "---",
      'title: "المقال"',
      "status: published",
      "published_at: 2026-08-20",
      "---",
      "",
      "هذه **افتتاحية** المقال كما ألّفها صاحبها.",
      "",
    ].join("\n"));
    writeFileSync(path.join(root, "src/content/poem/poem.md"), [
      "---",
      'title: "القصيدة"',
      "status: published",
      "---",
      "",
      "١ - صدر أول … عجز أول",
      "",
      "٢ - صدر ثان … عجز ثان",
      "",
      "٣ - صدر ثالث … عجز ثالث",
      "",
    ].join("\n"));

    const result = await generateAppContent({
      repositoryRoot: root,
      outputRoot: path.join(root, "out"),
      sidecarRoot: path.join(root, "content-ids"),
      targets: [{ coll: "article", id: "article" }, { coll: "poem", id: "poem" }],
    });

    expect(result.catalog.entries.find((entry) => entry.id === "article")).toMatchObject({
      publishedAt: "2026-08-20",
      excerpt: "هذه افتتاحية المقال كما ألّفها صاحبها.",
    });
    expect(result.catalog.entries.find((entry) => entry.id === "poem")?.openingVerses).toEqual([
      "صدر أول … عجز أول",
      "صدر ثان … عجز ثان",
    ]);
  });
});
