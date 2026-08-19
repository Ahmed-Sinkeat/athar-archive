export const APP_CONTENT_SCHEMA = 2 as const;
export const APP_CONTENT_PREFIX = "app/v2";
export const APP_FRAME_BLOCKS = 2_000;
export const APP_PACKAGE_SPLIT_BYTES = 25 * 1024 * 1024;

export type ReadableCollection = "book" | "article" | "question" | "poem";

export interface InlineSpan {
  k: "strong" | "emphasis" | "link" | "sup" | "entityRef";
  s: number;
  e: number;
  target?: string;
}

export interface ChapterRecord {
  a: string;
  title: string;
  block: number;
}

export interface PageRange {
  from: number;
  to: number;
  vols: number;
}

export interface DraftBlock {
  t: `h${1 | 2 | 3 | 4 | 5 | 6}` | "p" | "li" | "quote" | "verse" | "page" | "break";
  a: string;
  p?: number;
  vol?: number;
  x?: string;
  s?: string;
  j?: string;
  sp?: InlineSpan[];
  f?: string[];
  n?: number;
  ordered?: boolean;
  start?: number;
  depth?: number;
  continuation?: boolean;
  ha?: string;
}

export interface PackageBlock extends DraftBlock {
  i: number;
  id: string;
  fp: string;
}

export interface FootnoteRecord {
  id: string;
  x: string;
  sp?: InlineSpan[];
}

export interface ParsedDocument {
  schema: typeof APP_CONTENT_SCHEMA;
  coll: ReadableCollection;
  id: string;
  blocks: DraftBlock[];
  chapters: ChapterRecord[];
  footnotes: FootnoteRecord[];
  pages?: PageRange;
}

export interface PackageHeader {
  t: "header";
  schema: typeof APP_CONTENT_SCHEMA;
  coll: ReadableCollection;
  id: string;
  v: number;
  blocks: number;
  chapters: ChapterRecord[];
  footnotes: number;
  pages?: PageRange;
}

export interface FrameIndexEntry {
  off: number;
  len: number;
  ord: number;
  n: number;
  sha256: string;
}

export interface PackageIndex {
  schema: typeof APP_CONTENT_SCHEMA;
  coll: ReadableCollection;
  entityId: string;
  v: number;
  frames: FrameIndexEntry[];
}

export interface PackageArtifact {
  bytes: Buffer;
  index: PackageIndex;
  indexBytes: Buffer;
  uncompressed: number;
}

export interface PackageCatalogRef {
  path: string;
  hash: string;
  size: number;
  idxPath: string;
  idxHash: string;
  idxSize: number;
  uncompressed: number;
  blocks: number;
  chapters: number;
  pages?: PageRange;
}

export interface AudioCatalogRef {
  id: string;
  path: string;
  hash: string;
  format: "opus" | "mp3";
  seconds: number;
  size: number;
  cues?: { v: number; t: number }[];
}

export interface CatalogEntry {
  id: string;
  coll: ReadableCollection;
  v: number;
  hash: string;
  title: string;
  person?: string;
  personName?: string;
  died?: number;
  topics?: string[];
  kind?: string;
  authoredYear?: number;
  description?: string;
  pkg: PackageCatalogRef;
  audio?: AudioCatalogRef[];
}

export interface CatalogDocument {
  schema: typeof APP_CONTENT_SCHEMA;
  entries: CatalogEntry[];
}

export interface TombstoneDocument {
  schema: typeof APP_CONTENT_SCHEMA;
  since: string;
  deleted: { id: string; coll: ReadableCollection; at: string; supersededBy?: string }[];
}

export interface RootPayload {
  schema: typeof APP_CONTENT_SCHEMA;
  generationId: string;
  catalog: { path: string; hash: string; size: number };
  tombstones: { path: string; hash: string; size: number };
  minAppSchema: typeof APP_CONTENT_SCHEMA;
}

export interface SignedEnvelope {
  envelope: 1;
  payload: string;
  signatures: { keyId: string; alg: "SHA256withRSA"; value: string }[];
}
