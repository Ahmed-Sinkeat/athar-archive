export const APP_CONTENT_SCHEMA = 2 as const;
export const APP_CONTENT_PREFIX = "app/v2";
export const APP_FRAME_BLOCKS = 250;
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
  /** 3 for a باب (h3) inside the كتاب before it; absent for a كتاب (h2). Blocks belong to kitabs only. */
  lv?: 3;
  /** The printed page (and volume) the heading sits on. */
  p?: number;
  vol?: number;
}

export interface PageRange {
  from: number;
  to: number;
  vols: number;
}

export interface DraftBlock {
  /** "notes" closes a printed page (or the document) and lists that page's notes in `f`. */
  t: `h${1 | 2 | 3 | 4 | 5 | 6}` | "p" | "li" | "quote" | "verse" | "page" | "break" | "notes";
  a: string;
  p?: number;
  vol?: number;
  x?: string;
  s?: string;
  j?: string;
  sp?: InlineSpan[];
  f?: string[];
  n?: number;
  commentary?: { sourceLabel: string; text: string }[];
  ordered?: boolean;
  start?: number;
  depth?: number;
  continuation?: boolean;
  quoted?: boolean;
  ha?: string;
}

export interface PackageBlock extends DraftBlock {
  i: number;
  id: string;
  fp: string;
}

export interface FootnoteRecord {
  id: string;
  /** Display number, as printed on its page (per-page, not book-wide). */
  n?: string;
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
  /** The book's opening info card (الكتاب / المؤلف / الناشر …), taken out of the reading flow. */
  card?: CardField[];
}

export interface CardField {
  l: string;
  v: string;
  /** Marked front matter only: "h" a heading, "li" a list item; otherwise a paragraph. */
  k?: "h" | "li";
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
  /** The first frame, so a new book's opening page is fetched without the index first. */
  f0?: FrameIndexEntry;
}

export interface AudioCatalogRef {
  id: string;
  title?: string;
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
  editionOf?: string;
  edition?: string;
  editions?: string[];
  person?: string;
  personName?: string;
  died?: number;
  topics?: string[];
  kind?: string;
  authoredYear?: number;
  publishedAt?: string;
  description?: string;
  /** Build-derived opening prose for list presentation; Android never parses Markdown. */
  excerpt?: string;
  /** Exact authored verse text, normally the first two lines of a poem. */
  openingVerses?: string[];
  verseCount?: number;
  /** Shown in the reader's «المصدر» dialog instead of opening the book. */
  card?: CardField[];
  pkg: PackageCatalogRef;
  audio?: AudioCatalogRef[];
}

export interface CatalogPerson {
  id: string;
  name: string;
  died?: number;
  bio?: string;
  works: string[];
}

export interface CatalogDocument {
  schema: typeof APP_CONTENT_SCHEMA;
  entries: CatalogEntry[];
  persons?: CatalogPerson[];
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
