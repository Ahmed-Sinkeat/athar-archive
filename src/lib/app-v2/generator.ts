import { readFile, readdir, open, rename, stat, unlink, writeFile, mkdir, mkdtemp, rm } from "node:fs/promises";
import path from "node:path";
import matter from "gray-matter";
import {
  APP_CONTENT_PREFIX,
  APP_CONTENT_SCHEMA,
  type AudioCatalogRef,
  type CatalogDocument,
  type CatalogEntry,
  type ReadableCollection,
  type RootPayload,
  type SignedEnvelope,
  type TombstoneDocument,
} from "./contract.js";
import { assignStableBlockIds, sha256Hex, type StableIdSidecar } from "./identity.js";
import { parseReadableDocument } from "./parser.js";
import {
  artifactPath,
  contentPath,
  encodePackage,
  jsonBytes,
  signRootPayload,
} from "./package.js";
import type { KeyObject } from "node:crypto";

export interface ContentTarget {
  coll: ReadableCollection;
  id: string;
}

export interface SigningOptions {
  keyId: string;
  privateKey: KeyObject | string | Buffer;
}

export interface GenerateAppContentOptions {
  repositoryRoot: string;
  outputRoot: string;
  sidecarRoot: string;
  targets: readonly ContentTarget[];
  signing?: SigningOptions;
  loadAudio?: (url: string, id: string, format: "opus" | "mp3") => Promise<Buffer>;
}

export interface GeneratedEntitySummary {
  coll: ReadableCollection;
  id: string;
  blocks: number;
  frames: number;
  packageBytes: number;
  version: number;
  reusedIds: number;
  createdIds: number;
}

export interface GenerateAppContentResult {
  outputDirectory: string;
  catalog: CatalogDocument;
  rootPayload: RootPayload;
  envelope?: SignedEnvelope;
  entities: GeneratedEntitySummary[];
}

interface SourceEntry {
  target: ContentTarget;
  filePath: string;
  raw: string;
  data: Record<string, unknown>;
  body: string;
}

const TOMBSTONE_EPOCH = "2026-08-19";

function compareText(a: string, b: string): number {
  return a < b ? -1 : a > b ? 1 : 0;
}

async function exists(filePath: string): Promise<boolean> {
  try {
    await stat(filePath);
    return true;
  } catch {
    return false;
  }
}

async function sourcePath(root: string, target: ContentTarget): Promise<string> {
  const candidates = target.coll === "book"
    ? [path.join(root, "src/content/book", `${target.id}.md`), path.join(root, "src/content/book-lg", `${target.id}.md`)]
    : [path.join(root, `src/content/${target.coll}`, `${target.id}.md`)];
  for (const candidate of candidates) if (await exists(candidate)) return candidate;
  throw new Error(`missing source for ${target.coll}/${target.id}`);
}

async function loadSource(root: string, target: ContentTarget): Promise<SourceEntry> {
  const filePath = await sourcePath(root, target);
  const raw = await readFile(filePath, "utf8");
  const parsed = matter(raw, {});
  if (String(parsed.data.status ?? "draft") !== "published") throw new Error(`${filePath}: app content must be published`);
  return { target, filePath, raw, data: parsed.data as Record<string, unknown>, body: parsed.content };
}

async function readSidecar(filePath: string): Promise<StableIdSidecar | undefined> {
  if (!(await exists(filePath))) return undefined;
  const parsed: unknown = JSON.parse(await readFile(filePath, "utf8"));
  if (typeof parsed !== "object" || parsed == null) throw new Error(`${filePath}: malformed stable-ID sidecar`);
  return parsed as StableIdSidecar;
}

async function writeAtomic(filePath: string, bytes: Buffer | string) {
  await mkdir(path.dirname(filePath), { recursive: true });
  const temporary = `${filePath}.tmp`;
  await writeFile(temporary, bytes);
  await rename(temporary, filePath);
}

async function personMetadata(root: string, id: string | undefined) {
  if (!id) return {};
  const filePath = path.join(root, "src/content/person", `${id}.md`);
  const parsed = matter(await readFile(filePath, "utf8"), {}).data as Record<string, unknown>;
  const diedText = String(parsed.died ?? "");
  const western = diedText.replace(/[٠-٩۰-۹]/g, (digit) => {
    const arabic = "٠١٢٣٤٥٦٧٨٩".indexOf(digit);
    if (arabic !== -1) return String(arabic);
    return String("۰۱۲۳۴۵۶۷۸۹".indexOf(digit));
  });
  const diedMatch = western.match(/\d+/);
  return {
    person: id,
    personName: String(parsed.title),
    ...(diedMatch ? { died: Number.parseInt(diedMatch[0], 10) } : {}),
  };
}

function durationSeconds(value: unknown): number {
  if (typeof value !== "string" || !/^\d+(?::\d{1,2}){1,2}$/.test(value)) throw new Error(`invalid audio duration ${String(value)}`);
  return value.split(":").map(Number).reduce((total, part) => total * 60 + part, 0);
}

async function audioReference(
  source: SourceEntry,
  options: GenerateAppContentOptions,
  appRoot: string,
  attachedIds: readonly string[],
  verseNumbers: ReadonlySet<number>,
): Promise<AudioCatalogRef[] | undefined> {
  const explicitId = typeof source.data.audio === "string" ? source.data.audio : undefined;
  const ids = [...new Set([...(explicitId ? [explicitId] : []), ...attachedIds])];
  const recordings: AudioCatalogRef[] = [];
  for (const audioId of ids) {
  const audioPath = path.join(options.repositoryRoot, "src/content/audio", `${audioId}.md`);
  const audio = matter(await readFile(audioPath, "utf8"), {}).data as Record<string, unknown>;
  if (audio.status !== "published") continue;
  if (audio.source_type !== source.target.coll || audio.source_id !== source.target.id) {
    throw new Error(`${audioPath}: audio source does not point back to ${source.target.coll}/${source.target.id}`);
  }
  const format = audio.format === "mp3" ? "mp3" : audio.format === "opus" || audio.format == null ? "opus" : undefined;
  if (!format) throw new Error(`${audioPath}: unsupported audio format ${String(audio.format)}`);
  if (!options.loadAudio) throw new Error(`${audioPath}: audio bytes are required to create the authenticated app artifact`);
  const url = String(audio.url ?? "");
  const bytes = await options.loadAudio(url, audioId, format);
  if (typeof audio.size_bytes === "number" && audio.size_bytes !== bytes.byteLength) {
    throw new Error(`${audioPath}: fetched audio size ${bytes.byteLength} differs from declared ${audio.size_bytes}`);
  }
  const hash = sha256Hex(bytes);
  const relative = `audio/${audioId}/${hash}.${format}`;
  await writeAtomic(path.join(appRoot, relative), bytes);
  const timingPath = path.join(options.repositoryRoot, "src/content/poem-timing", `${source.target.id}.json`);
  let cues: { v: number; t: number }[] | undefined;
  if (await exists(timingPath)) {
    const value: unknown = JSON.parse(await readFile(timingPath, "utf8"));
    if (
      !Array.isArray(value)
      || value.some((cue) => typeof cue !== "object" || cue == null
        || !Number.isSafeInteger((cue as { v?: unknown }).v)
        || typeof (cue as { t?: unknown }).t !== "number")
    ) throw new Error(`${timingPath}: malformed poem timing cues`);
    cues = value as { v: number; t: number }[];
    const seconds = durationSeconds(audio.duration);
    for (let index = 0; index < cues.length; index++) {
      if (
        !verseNumbers.has(cues[index]!.v)
        || cues[index]!.v !== index + 1
        || cues[index]!.t < 0
        || cues[index]!.t > seconds
        || (index > 0 && cues[index]!.t <= cues[index - 1]!.t)
      ) {
        throw new Error(`${timingPath}: cues must be sequential by verse and strictly increasing by time`);
      }
    }
  }
  recordings.push({
    id: audioId,
    title: String(audio.title ?? source.data.title),
    path: relative,
    hash,
    format,
    seconds: durationSeconds(audio.duration),
    size: bytes.byteLength,
    ...(cues ? { cues } : {}),
  });
  }
  return recordings.length ? recordings : undefined;
}

function shortOpening(text: string, maximum = 240): string {
  const compact = text.replace(/\s+/gu, " ").trim();
  if ([...compact].length <= maximum) return compact;
  return `${[...compact].slice(0, maximum - 1).join("")}…`;
}

function catalogBase(source: SourceEntry, version: number, parsed: ReturnType<typeof parseReadableDocument>) {
  const topics = Array.isArray(source.data.topics)
    ? source.data.topics.filter((topic): topic is string => typeof topic === "string").sort()
    : undefined;
  const authoredYear = typeof source.data.authored_year === "number" ? source.data.authored_year : undefined;
  const publishedAt = source.data.published_at instanceof Date
    ? source.data.published_at.toISOString().slice(0, 10)
    : typeof source.data.published_at === "string" ? source.data.published_at : undefined;
  const description = typeof source.data.description === "string" ? source.data.description : undefined;
  const kind = source.target.coll === "book"
    ? String(source.data.kind ?? "كتاب")
    : source.target.coll === "poem" ? String(source.data.work_type ?? "قصيدة") : undefined;
  const firstParagraph = parsed.blocks.find((block) => block.t === "p" && block.x?.trim())?.x;
  const excerpt = (source.target.coll === "article" || source.target.coll === "question") && firstParagraph
    ? shortOpening(firstParagraph)
    : undefined;
  const openingVerses = source.target.coll === "poem"
    ? parsed.blocks
      .filter((block) => block.t === "verse")
      .slice(0, 2)
      .map((block) => block.x ?? `${block.s} … ${block.j}`)
    : undefined;
  return {
    id: source.target.id,
    coll: source.target.coll,
    v: version,
    title: String(source.data.title),
    ...(topics?.length ? { topics } : {}),
    ...(kind ? { kind } : {}),
    ...(authoredYear != null ? { authoredYear } : {}),
    ...(publishedAt ? { publishedAt } : {}),
    ...(description ? { description } : {}),
    ...(excerpt ? { excerpt } : {}),
    ...(openingVerses?.length ? { openingVerses } : {}),
    ...(source.target.coll === "poem" ? { verseCount: parsed.blocks.filter(block => block.t === "verse").length } : {}),
  };
}

/** Every published readable source, with large books mapped to the Android book collection. */
export async function publishedContentTargets(repositoryRoot: string): Promise<ContentTarget[]> {
  const targets: ContentTarget[] = [];
  const identities = new Set<string>();
  for (const directory of ["article", "book", "book-lg", "question", "poem"] as const) {
    const root = path.join(repositoryRoot, "src/content", directory);
    if (!(await exists(root))) continue;
    for (const filename of (await readdir(root)).filter((name) => name.endsWith(".md")).sort()) {
      const handle = await open(path.join(root, filename), "r");
      let header: string;
      try {
        const bytes = Buffer.alloc(64 * 1024);
        const { bytesRead } = await handle.read(bytes, 0, bytes.length, 0);
        const prefix = bytes.subarray(0, bytesRead).toString("utf8");
        const end = /\n---(?:\r?\n|$)/.exec(prefix.slice(3));
        if (!prefix.startsWith("---") || !end) throw new Error(`${filename}: missing or oversized frontmatter`);
        header = prefix.slice(0, 3 + end.index + end[0].length);
      } finally { await handle.close(); }
      if (matter(header, {}).data.status !== "published") continue;
      const id = filename.slice(0, -3);
      // Android user data keys by entity ID, so cross-collection collisions must fail too.
      if (identities.has(id)) throw new Error(`duplicate app-content entity ID: ${id}`);
      identities.add(id);
      targets.push({ coll: directory === "book-lg" ? "book" : directory, id });
    }
  }
  return targets;
}

export async function generateAppContent(options: GenerateAppContentOptions): Promise<GenerateAppContentResult> {
  await mkdir(options.outputRoot, { recursive: true });
  // Stable IDs for a full corpus exceed common RAM-backed temporary quotas.
  const stagingRoot = await mkdtemp(path.join(options.outputRoot, ".sidecars-"));
  try { return await generate(options, stagingRoot); }
  finally { await rm(stagingRoot, { recursive: true, force: true }); }
}

async function generate(options: GenerateAppContentOptions, stagingRoot: string): Promise<GenerateAppContentResult> {
  if (options.targets.length === 0) throw new Error("at least one app-content target is required");
  const unique = new Set(options.targets.map((target) => target.id));
  if (unique.size !== options.targets.length) throw new Error("duplicate app-content target");
  const targets = [...options.targets].sort((a, b) => compareText(a.coll, b.coll) || compareText(a.id, b.id));
  const audioBySource = new Map<string, string[]>();
  const audioRoot = path.join(options.repositoryRoot, "src/content/audio");
  if (await exists(audioRoot)) {
    for (const filename of (await readdir(audioRoot)).filter((name) => name.endsWith(".md")).sort()) {
      const data = matter(await readFile(path.join(audioRoot, filename), "utf8"), {}).data;
      if (data.status !== "published") continue;
      const key = `${data.source_type}/${data.source_id}`;
      audioBySource.set(key, [...(audioBySource.get(key) ?? []), filename.slice(0, -3)]);
    }
  }
  const topicTitles = new Map<string, string>();
  const topicRoot = path.join(options.repositoryRoot, "src/content/topic");
  if (await exists(topicRoot)) {
    for (const filename of (await readdir(topicRoot)).filter((name) => name.endsWith(".md"))) {
      const data = matter(await readFile(path.join(topicRoot, filename), "utf8"), {}).data;
      if (typeof data.title === "string") topicTitles.set(filename.slice(0, -3), data.title);
    }
  }
  const poemCommentary = new Map<string, Map<number, { sourceLabel: string; text: string }[]>>();
  const annotationRoot = path.join(options.repositoryRoot, "src/content/annotation");
  if (await exists(annotationRoot)) {
    for (const filename of (await readdir(annotationRoot)).filter(name => name.endsWith(".md")).sort()) {
      const annotation = matter(await readFile(path.join(annotationRoot, filename), "utf8"));
      if (annotation.data.status !== "published" || annotation.data.target_type !== "poem") continue;
      const verse = /^v([1-9]\d*)$/.exec(String(annotation.data.anchor));
      if (!verse) throw new Error(`${filename}: unsupported poem commentary anchor`);
      const target = String(annotation.data.target_id);
      const rows = poemCommentary.get(target) ?? new Map();
      const number = Number(verse[1]);
      const person = await personMetadata(options.repositoryRoot, annotation.data.annotator);
      const text = parseReadableDocument({ coll: "article", id: filename.slice(0, -3), body: annotation.content, sourcePath: filename })
        .blocks.map(block => block.x ?? [block.s, block.j].filter(Boolean).join(" … ")).filter(Boolean).join("\n\n");
      if (!text.trim()) throw new Error(`${filename}: empty published commentary`);
      rows.set(number, [...(rows.get(number) ?? []), { sourceLabel: [annotation.data.kind ?? "شرح", person.personName].filter(Boolean).join(" · "), text }]);
      poemCommentary.set(target, rows);
    }
  }
  const appRoot = path.join(options.outputRoot, APP_CONTENT_PREFIX);
  const pendingSidecars: { filePath: string; stagedPath: string }[] = [];
  const catalogEntries: CatalogEntry[] = [];
  const summaries: GeneratedEntitySummary[] = [];

  for (const target of targets) {
    const source = await loadSource(options.repositoryRoot, target);
    const parsed = parseReadableDocument({
      coll: source.target.coll,
      id: source.target.id,
      body: source.body,
      sourcePath: path.relative(options.repositoryRoot, source.filePath),
    });
    const commentary = target.coll === "poem" ? poemCommentary.get(target.id) : undefined;
    if (commentary) {
      const numbers = new Set(parsed.blocks.filter(block => block.t === "verse").map(block => block.n));
      for (const number of commentary.keys()) if (!numbers.has(number)) throw new Error(`${target.id}: commentary refers to missing verse ${number}`);
      for (const block of parsed.blocks) if (block.t === "verse" && block.n != null && commentary.has(block.n)) block.commentary = commentary.get(block.n);
    }
    const sourceHash = sha256Hex(source.raw + (commentary ? JSON.stringify([...commentary]) : ""));
    const sidecarPath = path.join(options.sidecarRoot, source.target.coll, `${source.target.id}.ids.json`);
    const identified = assignStableBlockIds(parsed.blocks, sourceHash, await readSidecar(sidecarPath));
    const version = identified.sidecar.generation;
    const artifact = encodePackage(parsed, identified.blocks, version);
    const packageHash = sha256Hex(artifact.bytes);
    const indexHash = sha256Hex(artifact.indexBytes);
    const packageRelative = contentPath(source.target.coll, source.target.id, packageHash, ".athar");
    const indexRelative = contentPath(source.target.coll, source.target.id, indexHash, ".athar.idx");
    await writeAtomic(path.join(appRoot, packageRelative), artifact.bytes);
    await writeAtomic(path.join(appRoot, indexRelative), artifact.indexBytes);
    const stagedPath = path.join(stagingRoot, `${pendingSidecars.length}.json`);
    await writeFile(stagedPath, `${JSON.stringify(identified.sidecar, null, 2)}\n`);
    pendingSidecars.push({ filePath: sidecarPath, stagedPath });

    const base = catalogBase(source, version, parsed);
    if (base.topics) base.topics = base.topics.map((topic) => topicTitles.get(topic) ?? topic);
    const person = await personMetadata(options.repositoryRoot, typeof source.data.person === "string" ? source.data.person : undefined);
    const audio = await audioReference(source, options, appRoot, audioBySource.get(`${target.coll}/${target.id}`) ?? [], new Set(parsed.blocks.filter(block => block.t === "verse").map(block => block.n!)));
    const pkg = {
      path: packageRelative,
      hash: packageHash,
      size: artifact.bytes.byteLength,
      idxPath: indexRelative,
      idxHash: indexHash,
      idxSize: artifact.indexBytes.byteLength,
      uncompressed: artifact.uncompressed,
      blocks: parsed.blocks.length,
      chapters: parsed.chapters.length,
      ...(parsed.pages ? { pages: parsed.pages } : {}),
      ...(artifact.index.frames[0] ? { f0: artifact.index.frames[0] } : {}),
    };
    const hash = sha256Hex(jsonBytes({ ...base, ...person, pkg, ...(audio ? { audio } : {}) }));
    catalogEntries.push({ ...base, hash, ...person, pkg, ...(audio ? { audio } : {}) });
    summaries.push({
      coll: source.target.coll,
      id: source.target.id,
      blocks: parsed.blocks.length,
      frames: artifact.index.frames.length,
      packageBytes: artifact.bytes.byteLength,
      version,
      reusedIds: identified.reused,
      createdIds: identified.created,
    });
  }

  const catalog: CatalogDocument = { schema: APP_CONTENT_SCHEMA, entries: catalogEntries };
  const tombstones: TombstoneDocument = { schema: APP_CONTENT_SCHEMA, since: TOMBSTONE_EPOCH, deleted: [] };
  const previousPath = path.join(appRoot, "index.payload.json");
  if (await exists(previousPath)) {
    const previous = JSON.parse(await readFile(previousPath, "utf8")) as RootPayload;
    for (const [kind, reference] of [["catalog", previous.catalog], ["tombstones", previous.tombstones]] as const) {
      if (!reference || !/^[a-f0-9]{64}$/.test(reference.hash) || reference.path !== artifactPath(kind, reference.hash)) {
        throw new Error("previous generation has an unsafe artifact reference");
      }
      const bytes = await readFile(path.join(appRoot, reference.path));
      if (sha256Hex(bytes) !== reference.hash || bytes.length !== reference.size) throw new Error("previous generation is corrupt");
      const document = JSON.parse(bytes.toString("utf8"));
      if (kind === "catalog") {
        for (const entry of (document as CatalogDocument).entries) {
          if (!unique.has(entry.id)) tombstones.deleted.push({ id: entry.id, coll: entry.coll, at: TOMBSTONE_EPOCH });
        }
      } else tombstones.deleted.push(...(document as TombstoneDocument).deleted.filter((entry) => !unique.has(entry.id)));
    }
    tombstones.deleted = [...new Map(tombstones.deleted.map((entry) => [entry.id, entry])).values()].sort((a, b) => compareText(a.id, b.id));
  }
  const catalogBytes = jsonBytes(catalog);
  const tombstoneBytes = jsonBytes(tombstones);
  const catalogHash = sha256Hex(catalogBytes);
  const tombstoneHash = sha256Hex(tombstoneBytes);
  const catalogRelative = artifactPath("catalog", catalogHash);
  const tombstoneRelative = artifactPath("tombstones", tombstoneHash);
  await writeAtomic(path.join(appRoot, catalogRelative), catalogBytes);
  await writeAtomic(path.join(appRoot, tombstoneRelative), tombstoneBytes);
  const rootPayload: RootPayload = {
    schema: APP_CONTENT_SCHEMA,
    generationId: sha256Hex(`${catalogHash}\u0000${tombstoneHash}`).slice(0, 16),
    catalog: { path: catalogRelative, hash: catalogHash, size: catalogBytes.byteLength },
    tombstones: { path: tombstoneRelative, hash: tombstoneHash, size: tombstoneBytes.byteLength },
    minAppSchema: APP_CONTENT_SCHEMA,
  };
  await writeAtomic(path.join(appRoot, "index.payload.json"), jsonBytes(rootPayload));
  const envelope = options.signing
    ? signRootPayload(rootPayload, options.signing.keyId, options.signing.privateKey)
    : undefined;
  const indexPath = path.join(appRoot, "index.json");
  if (envelope) await writeAtomic(indexPath, jsonBytes(envelope));
  else if (await exists(indexPath)) await unlink(indexPath);

  for (const pending of pendingSidecars) {
    await writeAtomic(pending.filePath, await readFile(pending.stagedPath));
  }
  return { outputDirectory: appRoot, catalog, rootPayload, envelope, entities: summaries };
}
