// Runtime helpers for the on-demand routes. The chapter manifest/body loaders
// that used to live here died when book chapters moved to full prerender
// (src/pages/book-pages/[slug]/[chapter].astro + R2, see gen-book-chapters.ts).

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
