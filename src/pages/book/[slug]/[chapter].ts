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
  const bucket = (env as unknown as { BOOK_ASSETS?: { get(key: string): Promise<{ text(): Promise<string> } | null> } }).BOOK_ASSETS;
  const obj = await bucket?.get(`pages/book/${slug}/${chapter}.html`);
  if (!obj) return notFound();
  return new Response(await obj.text(), { headers: HTML });
};
