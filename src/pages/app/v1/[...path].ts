// Thin route for the Android app's content JSON (scripts/gen-app-content.ts →
// BOOK_ASSETS R2 under app/v1/). One R2 read per request, same shape as the
// chapter shim (src/pages/book/[slug]/[chapter].ts); src/middleware.ts adds
// edge caching keyed by build id. The app cache-busts individual paths with
// ?v=<manifest hash> — the query is ignored here.
export const prerender = false;

import type { APIRoute } from "astro";
import { notFound } from "../../../lib/book-asset";

const JSON_H = { "content-type": "application/json; charset=utf-8" };

// app/v1 keys are generated from content ids + chapter slugs (Arabic allowed);
// reject traversal and anything that isn't a .json leaf.
const OK_PATH = /^[^\0]+\.json$/;

export const GET: APIRoute = async ({ params }) => {
  const path = params.path ?? "";
  if (!OK_PATH.test(path) || path.split("/").some((seg) => seg === "" || seg === "." || seg === "..")) {
    return notFound();
  }

  // dev has no R2 — serve the last local `pnpm app:gen` output instead
  if (import.meta.env.DEV) {
    const fs = await import("node:fs");
    const p = new URL(`../../../../dist/r2-upload/app/v1/${path}`, import.meta.url);
    if (!fs.existsSync(p)) return notFound();
    return new Response(fs.readFileSync(p), { headers: JSON_H });
  }

  const { env } = await import("cloudflare:workers");
  const { BOOK_ASSETS: bucket } = env as unknown as {
    BOOK_ASSETS?: { get(key: string): Promise<{ text(): Promise<string> } | null> };
  };
  const obj = await bucket?.get(`app/v1/${path}`);
  if (!obj) return notFound();
  return new Response(await obj.text(), { headers: JSON_H });
};
