// Thin on-demand shim: the real page is prerendered at build time by
// src/pages/book-pages/[slug]/[chapter].astro and stored in the BOOK_ASSETS R2
// bucket by scripts/gen-book-chapters.ts. Serving is one R2 read — no graph
// build, no markdown render — so a request can't hit the free plan's ~10ms CPU
// limit (the old in-route render 1102'd under load). src/middleware.ts adds
// edge caching + security headers on top, so this runs on cache MISSES only.
export const prerender = false;

import type { APIRoute } from "astro";
import { notFound, substitute } from "../../../lib/book-asset";

const HTML = { "content-type": "text/html; charset=utf-8" };

interface Bucket {
  get(key: string): Promise<{ text(): Promise<string>; body: ReadableStream<Uint8Array<ArrayBuffer>> } | null>;
}

/**
 * Read a gzipped page (pages-v2/) and inflate it. DecompressionStream is native
 * — the JS side never touches the compressed bytes — which is what makes
 * storing these gzipped affordable: 90% off the bucket (60.2 GB → 5.7 GB) for
 * ~9ms of CPU here, paid once per edge-cache miss. Returns null when the object
 * is missing OR corrupt, so a bad object 404s rather than 500ing.
 */
async function readGzipped(bucket: Bucket | undefined, key: string): Promise<string | null> {
  const obj = await bucket?.get(key);
  if (!obj) return null;
  try {
    return await new Response(obj.body.pipeThrough(new DecompressionStream("gzip"))).text();
  } catch {
    return null;
  }
}

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
    BOOK_ASSETS?: Bucket;
    CHAPTER_ASSETS?: string;
  };

  // One R2 read. The pages/ → pages-v2/ migration finished 2026-08-20 and the
  // old prefix was deleted, so the CHAPTERS_V2 flag and the per-object fallback
  // that guarded the rollout are both gone with it.
  const html = await readGzipped(bucket, `pages-v2/book/${slug}/${chapter}.html.gz`);

  if (html === null) return notFound();
  return new Response(substitute(html, assetsJson, __AA_BUILD__), { headers: HTML });
};
