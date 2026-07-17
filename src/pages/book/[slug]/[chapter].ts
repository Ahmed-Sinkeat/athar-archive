// Thin on-demand shim: the real page is prerendered at build time by
// src/pages/book-pages/[slug]/[chapter].astro and stored in the BOOK_ASSETS R2
// bucket (pages/book/<slug>/<chapter>.html) by scripts/gen-book-chapters.ts.
// Serving is one R2 read — no graph build, no markdown render — so a request
// can't hit the free plan's ~10ms CPU limit (the old in-route render 1102'd
// under load). src/middleware.ts adds edge caching + security headers on top.
export const prerender = false;

import type { APIRoute } from "astro";
import { notFound } from "../../../lib/book-asset";

const HTML = { "content-type": "text/html; charset=utf-8" };

export const GET: APIRoute = async ({ params, url }) => {
  const { slug, chapter } = params as { slug: string; chapter: string };

  // dev has no R2 — render the shadow route through the dev server instead
  if (import.meta.env.DEV) {
    const res = await fetch(new URL(`/book-pages/${slug}/${chapter}`, url.origin));
    if (!res.ok) return notFound();
    return new Response(await res.text(), { headers: HTML });
  }

  const { env } = await import("cloudflare:workers");
  const { BOOK_ASSETS: bucket, CHAPTER_ASSETS: assetsJson } = env as unknown as {
    BOOK_ASSETS?: { get(key: string): Promise<{ text(): Promise<string> } | null> };
    CHAPTER_ASSETS?: string;
  };
  const obj = await bucket?.get(`pages/book/${slug}/${chapter}.html`);
  if (!obj) return notFound();
  // Stored pages carry stable /_astro-live/<name>.<ext> placeholders instead of
  // hashed asset URLs (gen-book-chapters.ts §4) — swap in this deploy's real
  // hashes here, so a CSS/JS-only change never re-uploads R2. Pages from before
  // the placeholder era have no tokens and pass through byte-identical.
  let html = await obj.text();
  for (const [logical, hashed] of Object.entries(JSON.parse(assetsJson ?? "{}") as Record<string, string>)) {
    html = html.replaceAll(`/_astro-live/${logical}`, hashed);
  }
  return new Response(html, { headers: HTML });
};
