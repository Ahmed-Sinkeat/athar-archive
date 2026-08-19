import { readFile, rename, stat, unlink, writeFile, mkdir } from "node:fs/promises";
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
  const parsed = matter(raw);
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
  const parsed = matter(await readFile(filePath, "utf8")).data as Record<string, unknown>;
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
): Promise<AudioCatalogRef[] | undefined> {
  const audioId = typeof source.data.audio === "string" ? source.data.audio : undefined;
  if (!audioId) return undefined;
  const audioPath = path.join(options.repositoryRoot, "src/content/audio", `${audioId}.md`);
  const audio = matter(await readFile(audioPath, "utf8")).data as Record<string, unknown>;
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
        cues[index]!.v !== index + 1
        || cues[index]!.t < 0
        || cues[index]!.t > seconds
        || (index > 0 && cues[index]!.t <= cues[index - 1]!.t)
      ) {
        throw new Error(`${timingPath}: cues must be sequential by verse and strictly increasing by time`);
      }
    }
  }
  return [{
    id: audioId,
    path: relative,
    hash,
    format,
    seconds: durationSeconds(audio.duration),
    size: bytes.byteLength,
    ...(cues ? { cues } : {}),
  }];
}

function catalogBase(source: SourceEntry, version: number) {
  const topics = Array.isArray(source.data.topics)
    ? source.data.topics.filter((topic): topic is string => typeof topic === "string").sort()
    : undefined;
  const authoredYear = typeof source.data.authored_year === "number" ? source.data.authored_year : undefined;
  const description = typeof source.data.description === "string" ? source.data.description : undefined;
  const kind = source.target.coll === "book"
    ? String(source.data.kind ?? "كتاب")
    : source.target.coll === "poem" ? String(source.data.work_type ?? "قصيدة") : undefined;
  return {
    id: source.target.id,
    coll: source.target.coll,
    v: version,
    title: String(source.data.title),
    ...(topics?.length ? { topics } : {}),
    ...(kind ? { kind } : {}),
    ...(authoredYear != null ? { authoredYear } : {}),
    ...(description ? { description } : {}),
  };
}

export async function generateAppContent(options: GenerateAppContentOptions): Promise<GenerateAppContentResult> {
  if (options.targets.length === 0) throw new Error("at least one app-content target is required");
  const unique = new Set(options.targets.map((target) => `${target.coll}/${target.id}`));
  if (unique.size !== options.targets.length) throw new Error("duplicate app-content target");
  const sources = await Promise.all(
    [...options.targets]
      .sort((a, b) => compareText(a.coll, b.coll) || compareText(a.id, b.id))
      .map((target) => loadSource(options.repositoryRoot, target)),
  );
  const appRoot = path.join(options.outputRoot, APP_CONTENT_PREFIX);
  const pendingSidecars: { filePath: string; sidecar: StableIdSidecar }[] = [];
  const catalogEntries: CatalogEntry[] = [];
  const summaries: GeneratedEntitySummary[] = [];

  for (const source of sources) {
    const parsed = parseReadableDocument({
      coll: source.target.coll,
      id: source.target.id,
      body: source.body,
      sourcePath: path.relative(options.repositoryRoot, source.filePath),
    });
    const sourceHash = sha256Hex(source.raw);
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
    pendingSidecars.push({ filePath: sidecarPath, sidecar: identified.sidecar });

    const base = catalogBase(source, version);
    const person = await personMetadata(options.repositoryRoot, typeof source.data.person === "string" ? source.data.person : undefined);
    const audio = await audioReference(source, options, appRoot);
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
    await writeAtomic(pending.filePath, `${JSON.stringify(pending.sidecar, null, 2)}\n`);
  }
  return { outputDirectory: appRoot, catalog, rootPayload, envelope, entities: summaries };
}
