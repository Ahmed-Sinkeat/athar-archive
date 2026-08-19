// Runtime helpers for the on-demand routes. The chapter manifest/body loaders
// that used to live here died when book chapters moved to full prerender
// (src/pages/book-pages/[slug]/[chapter].astro + R2, see gen-book-chapters.ts).

const FRONTMATTER = /^---\r?\n[\s\S]*?\r?\n---\r?\n?/;
// stored placeholder → this deploy's hashed asset URL (see substitute())
const ASSET_TOKEN = /\/_astro-live\/([\w.-]+)/g;
const BUILD_META = /(<meta name="aa-build" content=")[^"]*/;
const ASSET_HOST = "https://assets.local"; // host is ignored by ASSETS.fetch (path match)

export function stripFrontmatter(raw: string): string {
  return raw.replace(FRONTMATTER, "");
}

/**
 * Prerendered chapter pages are stored with stable /_astro-live/<name>.<ext>
 * placeholders instead of hashed asset URLs, and a blank build id
 * (gen-book-chapters.ts §4) — so a CSS-only change never re-uploads all ~78k
 * pages to R2. This puts this deploy's real values back, per cache miss.
 *
 * ONE regex pass, not one replaceAll per asset: that was ~24 full scans of a
 * ~450KB string on every miss, and inflating the gzipped body now wants some of
 * the same ~10ms CPU budget this route exists to stay inside. An unknown token
 * is left alone rather than blanked, and pages from before the placeholder era
 * carry no tokens and pass through unchanged.
 *
 * The build id must tell the truth or reader.ts reads a soft-nav as "crossed a
 * deploy" and hard-reloads — serving the same stale meta again, an infinite
 * reload loop on every prerendered chapter (seen live 2026-07-25).
 */
export function substitute(html: string, assetsJson: string | undefined, buildId: string): string {
  const assets = JSON.parse(assetsJson ?? "{}") as Record<string, string>;
  return html
    .replace(ASSET_TOKEN, (full, name: string) => assets[name] ?? full)
    .replace(BUILD_META, `$1${buildId}`);
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
