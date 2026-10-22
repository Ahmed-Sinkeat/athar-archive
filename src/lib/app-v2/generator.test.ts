import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { gunzipSync } from "node:zlib";
import { generateKeyPairSync } from "node:crypto";
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { generateAppContent, publishedContentTargets } from "./generator.js";
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
  it("emits published people, including those without works, and keeps biographies", async () => {
    const root = fixtureRoot();
    writeFileSync(path.join(root, "src/content/person/empty.md"), "---\ntitle: Empty\nstatus: published\nbio: Biography\n---\nBody");
    writeFileSync(path.join(root, "src/content/person/draft.md"), "---\ntitle: Hidden\nstatus: draft\n---\n");
    const result = await generateAppContent({ repositoryRoot: root, outputRoot: path.join(root, "out"), sidecarRoot: path.join(root, "ids"), targets: [{ coll: "book", id: "fixture" }] });
    expect(result.catalog.persons).toEqual([
      { id: "author", name: "المؤلف", died: 123, works: ["fixture"] },
      { id: "empty", name: "Empty", bio: "Biography\n\nBody", works: [] },
    ]);
  });

  it("groups editions without dropping their downloadable text and rejects missing defaults", async () => {
    const root = fixtureRoot();
    writeFileSync(path.join(root, "src/content/book/other.md"), "---\ntitle: Other\nstatus: published\nedition_of: fixture\nedition: Edited\n---\nOther text");
    const options = { repositoryRoot: root, outputRoot: path.join(root, "out"), sidecarRoot: path.join(root, "ids"), targets: await publishedContentTargets(root) };
    const result = await generateAppContent(options);
    expect(result.catalog.entries).toHaveLength(2);
    expect(result.catalog.entries.find(e => e.id === "fixture")?.editions).toEqual(["other"]);
    expect(result.catalog.entries.find(e => e.id === "other")?.editionOf).toBe("fixture");
    expect(result.catalog.persons?.[0]?.works).toEqual(["fixture"]);
    // An --only run keeps fixture from the previous catalog: its editions must not go stale.
    writeFileSync(path.join(root, "src/content/book/other.md"), "---\ntitle: Other\nstatus: published\n---\nOther text");
    const only = await generateAppContent({ ...options, targets: [{ coll: "book", id: "other" }], keepOthers: true });
    expect(only.catalog.entries.find(e => e.id === "fixture")?.editions).toBeUndefined();
    writeFileSync(path.join(root, "src/content/book/other.md"), "---\ntitle: Other\nstatus: published\nedition_of: missing\n---\nText");
    await expect(generateAppContent(options)).rejects.toThrow("invalid edition_of");
  });

  it("skips opening formulas in article previews while preserving substantive prose", async () => {
    const root = fixtureRoot();
    mkdirSync(path.join(root, "src/content/article"));
    writeFileSync(path.join(root, "src/content/article/article.md"), "---\ntitle: Article\nstatus: published\n---\nالحمد لله والصلاة والسلام على رسول الله وعلى آله وصحبه ومن والاه\nأما بعد :\n\nSubstantive opening");
    const result = await generateAppContent({ repositoryRoot: root, outputRoot: path.join(root, "out"), sidecarRoot: path.join(root, "ids"), targets: [{ coll: "article", id: "article" }] });
    expect(result.catalog.entries[0]?.excerpt).toBe("Substantive opening");
    writeFileSync(path.join(root, "src/content/article/article.md"), "---\ntitle: Article\nstatus: published\n---\nأما بعد :\n\nفمن العبارات السيارة");
    const alone = await generateAppContent({ repositoryRoot: root, outputRoot: path.join(root, "out"), sidecarRoot: path.join(root, "ids"), targets: [{ coll: "article", id: "article" }] });
    expect(alone.catalog.entries[0]?.excerpt).toBe("فمن العبارات السيارة");
  });

  it("the standalone release verifier rejects a corrupted generated package", async () => {
    const root = fixtureRoot();
    const book = path.join(root, "src/content/book/fixture.md");
    writeFileSync(book, readFileSync(book, "utf8") + '\nReferenced[^note] and plain superscript<sup>2</sup>\n\n<hr class="page-sep" data-page="1" />\n\n<hr class="page-sep" data-page="2" />\n\n[^note]: Preserved footnote\n');
    const keys = generateKeyPairSync("rsa", { modulusLength: 3072 });
    const keyFile = path.join(root, "key.pem");
    writeFileSync(keyFile, keys.privateKey.export({ type: "pkcs8", format: "pem" }));
    const result = await generateAppContent({ repositoryRoot: root, outputRoot: path.join(root, "out"), sidecarRoot: path.join(root, "ids"), targets: [{ coll: "book", id: "fixture" }], signing: { keyId: "test", privateKey: keys.privateKey } });
    const script = fileURLToPath(new URL("../../../scripts/verify-app-content.ts", import.meta.url));
    const run = () => spawnSync(process.execPath, ["--import", "tsx", script, result.outputDirectory], { encoding: "utf8", env: { ...process.env, ATHAR_CONTENT_SIGNING_KEY_FILE: keyFile, ATHAR_CONTENT_SIGNING_KEY_ID: "test" } });
    const verified = run();
    expect(verified.status, verified.stderr).toBe(0);
    expect(verified.stdout).toContain("verified 1 texts");
    expect(verified.stdout).toContain("1 adjacent dividers");
    const packageFile = path.join(result.outputDirectory, result.catalog.entries[0]!.pkg.path);
    writeFileSync(packageFile, Buffer.concat([readFileSync(packageFile), Buffer.from("corrupt")]));
    expect(run().status).not.toBe(0);
  });

  it("discovers published sources including large books and rejects ambiguous IDs", async () => {
    const root = fixtureRoot();
    mkdirSync(path.join(root, "src/content/book-lg"), { recursive: true });
    writeFileSync(path.join(root, "src/content/book-lg/large.md"), "---\nstatus: published\ntitle: Large\n---\nFull text");
    writeFileSync(path.join(root, "src/content/book/draft.md"), "---\nstatus: draft\n---\nDraft");
    expect(await publishedContentTargets(root)).toEqual([
      { coll: "book", id: "fixture" }, { coll: "book", id: "large" },
    ]);
    mkdirSync(path.join(root, "src/content/question"), { recursive: true });
    writeFileSync(path.join(root, "src/content/question/fixture.md"), "---\nstatus: published\n---\nQuestion");
    await expect(publishedContentTargets(root)).rejects.toThrow("duplicate app-content entity ID");
  });

  it("includes every published recording attached by source, with its actual lesson title", async () => {
    const root = fixtureRoot();
    mkdirSync(path.join(root, "src/content/audio"), { recursive: true });
    const bytes = Buffer.from("verified audio bytes");
    for (const id of ["lesson-one", "lesson-two"]) {
      writeFileSync(path.join(root, `src/content/audio/${id}.md`), `---\ntitle: ${id}\nstatus: published\nsource_type: book\nsource_id: fixture\nurl: https://example.com/${id}.opus\nduration: '1:00'\nsize_bytes: ${bytes.length}\n---\n`);
    }
    const result = await generateAppContent({ repositoryRoot: root, outputRoot: path.join(root, "out"),
      sidecarRoot: path.join(root, "ids"), targets: [{ coll: "book", id: "fixture" }], loadAudio: async () => bytes });
    expect(result.catalog.entries[0]?.audio?.map((audio) => audio.title)).toEqual(["lesson-one", "lesson-two"]);
  });

  it("keeps published text while omitting its draft recording", async () => {
    const root = fixtureRoot();
    const bookPath = path.join(root, "src/content/book/fixture.md");
    writeFileSync(bookPath, readFileSync(bookPath, "utf8").replace("status: published", "status: published\naudio: draft-audio"));
    mkdirSync(path.join(root, "src/content/audio"), { recursive: true });
    writeFileSync(path.join(root, "src/content/audio/draft-audio.md"), "---\nstatus: draft\n---\n");
    const result = await generateAppContent({ repositoryRoot: root, outputRoot: path.join(root, "out"),
      sidecarRoot: path.join(root, "ids"), targets: [{ coll: "book", id: "fixture" }] });
    expect(result.catalog.entries).toHaveLength(1);
    expect(result.catalog.entries[0]?.audio).toBeUndefined();
  });

  it("carries removals across generations without deleting retained artifacts", async () => {
    const root = fixtureRoot();
    writeFileSync(path.join(root, "src/content/book/removed.md"), "---\nstatus: published\ntitle: Removed\n---\nOld text");
    const options = { repositoryRoot: root, outputRoot: path.join(root, "out"), sidecarRoot: path.join(root, "ids") };
    const first = await generateAppContent({ ...options, targets: [{ coll: "book", id: "fixture" }, { coll: "book", id: "removed" }] });
    const second = await generateAppContent({ ...options, targets: [{ coll: "book", id: "fixture" }] });
    const third = await generateAppContent({ ...options, targets: [{ coll: "book", id: "fixture" }] });
    const deleted = JSON.parse(readFileSync(path.join(second.outputDirectory, third.rootPayload.tombstones.path), "utf8")).deleted;
    expect(deleted).toEqual([expect.objectContaining({ id: "removed", coll: "book" })]);
    expect(existsSync(path.join(first.outputDirectory, first.catalog.entries.find((entry) => entry.id === "removed")!.pkg.path))).toBe(true);
  });

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

  it("delivers published verse commentary and rejects missing anchors and cues", async () => {
    const root = fixtureRoot();
    mkdirSync(path.join(root, "src/content/poem"), { recursive: true });
    mkdirSync(path.join(root, "src/content/annotation"), { recursive: true });
    mkdirSync(path.join(root, "src/content/audio"), { recursive: true });
    mkdirSync(path.join(root, "src/content/poem-timing"), { recursive: true });
    writeFileSync(path.join(root, "src/content/poem/poem.md"), "---\ntitle: القصيدة\nstatus: published\n---\nصدر أول … عجز أول\nصدر ثان … عجز ثان\n");
    const annotation = path.join(root, "src/content/annotation/comment.md");
    writeFileSync(annotation, "---\ntitle: شرح\nstatus: published\ntarget_type: poem\ntarget_id: poem\nanchor: v2\nannotator: author\n---\nتعليق **مهم**\n");
    const options = { repositoryRoot: root, outputRoot: path.join(root, "out"), sidecarRoot: path.join(root, "ids"), targets: [{ coll: "poem", id: "poem" }] as const };
    const first = await generateAppContent(options);
    const poem = first.catalog.entries[0]!;
    expect(poem.verseCount).toBe(2);
    const index = JSON.parse(readFileSync(path.join(root, "out/app/v2", poem.pkg.idxPath), "utf8"));
    expect(poem.pkg.f0).toEqual(index.frames[0]);
    const records = gunzipSync(readFileSync(path.join(root, "out/app/v2", poem.pkg.path))).toString().trim().split("\n").map(line => JSON.parse(line));
    expect(records.find(row => row.n === 2).commentary).toEqual([{ sourceLabel: "شرح · المؤلف", text: "تعليق مهم" }]);
    writeFileSync(annotation, readFileSync(annotation, "utf8").replace("v2", "v3"));
    await expect(generateAppContent(options)).rejects.toThrow("missing verse 3");
    writeFileSync(annotation, readFileSync(annotation, "utf8").replace("v3", "v2"));
    const audio = Buffer.from("audio");
    writeFileSync(path.join(root, "src/content/audio/audio.md"), "---\ntitle: إنشاد\nstatus: published\nsource_type: poem\nsource_id: poem\nurl: https://example.com/audio\nduration: '1:00'\n---\n");
    writeFileSync(path.join(root, "src/content/poem-timing/poem.json"), JSON.stringify([{ v: 1, t: 0 }, { v: 2, t: 1 }, { v: 3, t: 2 }]));
    await expect(generateAppContent({ ...options, loadAudio: async () => audio })).rejects.toThrow("cues must be sequential");
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
