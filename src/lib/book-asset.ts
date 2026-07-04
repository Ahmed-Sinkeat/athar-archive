// Runtime helpers for on-demand reading routes (book chapters, lessons). DEV (Node
// dev server) reads source files from disk; PROD reads deployed Workers Static Assets
// via the ASSETS binding (bodies copied into dist/client by copy-content-assets.mjs).

const FRONTMATTER = /^---\r?\n[\s\S]*?\r?\n---\r?\n?/;
const ASSET_HOST = "https://assets.local"; // host is ignored by ASSETS.fetch (path match)

export function stripFrontmatter(raw: string): string {
  return raw.replace(FRONTMATTER, "");
}

async function assetText(path: string): Promise<string | null> {
  if (import.meta.env.DEV) {
    const res = await fetch(new URL(path, "http://localhost:4321"));
    return res.ok ? await res.text() : null;
  }
  const { env } = await import("cloudflare:workers");
  const res = await (env as { ASSETS: { fetch(u: URL): Promise<Response> } }).ASSETS.fetch(
    new URL(path, ASSET_HOST),
  );
  return res.ok ? await res.text() : null;
}

export interface ChapterMeta {
  title: string;
  rawTitle?: string;
  slug: string;
  parent?: string;
  parentTitle?: string;
  firstPage?: number;
}

export interface CatalogEntry { label: string; value: string }
export interface ChapterManifest { chapters: ChapterMeta[]; catalog: CatalogEntry[] }

// Per-chapter book assets (M2): gen-book-chapters.ts writes these at build time
// so the chapter route never re-fetches + re-splits the whole book per request.
export async function loadChapterManifest(bookId: string): Promise<ChapterManifest | null> {
  const raw = await assetText(`/content/book/${bookId}.chapters.json`);
  return raw == null ? null : (JSON.parse(raw) as ChapterManifest);
}

// Chapter bodies live in the BOOK_ASSETS R2 bucket (scripts/upload-r2-assets.mjs),
// not Workers Static Assets — a large book is thousands of chapter files, which
// pushed deploys toward the 20k-file asset ceiling. DEV keeps reading the local
// dev-server path (pre-R2 behavior, unchanged).
export async function loadChapterBody(bookId: string, chapterSlug: string): Promise<string | null> {
  if (import.meta.env.DEV) return assetText(`/content/book/${bookId}/${chapterSlug}.md`);
  const { env } = await import("cloudflare:workers");
  const bucket = (env as { BOOK_ASSETS?: { get(key: string): Promise<{ text(): Promise<string> } | null> } }).BOOK_ASSETS;
  const obj = await bucket?.get(`book/${bookId}/${chapterSlug}.md`);
  return obj ? await obj.text() : null;
}

export async function loadContentBody(collection: string, id: string): Promise<string | null> {
  if (import.meta.env.DEV) {
    try {
      const { readFile } = await import("node:fs/promises");
      return stripFrontmatter(await readFile(`src/content/${collection}/${id}.md`, "utf-8"));
    } catch {
      // Fallback if fs.readFile is not implemented or throws (workerd sandbox)
      const raw = await assetText(`/content/${collection}/${id}.md`);
      return raw == null ? null : stripFrontmatter(raw);
    }
  }
  const raw = await assetText(`/content/${collection}/${id}.md`);
  return raw == null ? null : stripFrontmatter(raw);
}

// On-demand routes can't Astro.rewrite("/404") — /404 is prerendered, so the worker
// has no component instance for it. Serve the static 404.html asset with a 404 status.
export async function notFound(): Promise<Response> {
  let html: string | null = null;
  if (import.meta.env.DEV) {
    try {
      const { readFile } = await import("node:fs/promises");
      html = await readFile("dist/client/404.html", "utf-8");
    } catch {
      html = await assetText("/404.html");
    }
  } else {
    html = await assetText("/404.html");
  }
  return new Response(
    html ?? '<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8"><title>٤٠٤</title><h1>الصفحة غير موجودة</h1>',
    { status: 404, headers: { "content-type": "text/html; charset=utf-8" } },
  );
}
