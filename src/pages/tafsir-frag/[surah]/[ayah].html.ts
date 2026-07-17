// Per-ayah tafsir fragment, on demand. Was a static file at this same URL
// (scripts/gen-tafsir-frags.ts wrote it into dist/client/tafsir-frag/); now the
// fragments live in the BOOK_ASSETS R2 bucket instead (thousands of tiny files
// pushed deploys toward the Workers Static Assets 20k-file ceiling), so this
// route reads the same key from R2 and serves it under the same path — the
// client's fetch(annSrc) in reader.ts needed no change.
import type { APIRoute } from "astro";

export const prerender = false;

const ID_OK = /^\d+$/;
// bare "255" = the per-ayah stub; "255.tafsir-ibn-kathir" = one source's body
// (gen-tafsir-frags.ts v2 — per-source split)
const AYAH_OK = /^\d+(\.[a-z0-9-]+)?$/;

export const GET: APIRoute = async ({ params }) => {
  const { surah, ayah } = params;
  if (!surah || !ayah || !ID_OK.test(surah) || !AYAH_OK.test(ayah)) return new Response("Not found", { status: 404 });

  if (import.meta.env.DEV) {
    try {
      const { readFile } = await import("node:fs/promises");
      const html = await readFile(`dist/r2-upload/tafsir-frag/${surah}/${ayah}.html`, "utf-8");
      return new Response(html, { headers: { "content-type": "text/html; charset=utf-8" } });
    } catch {
      return new Response("Not found", { status: 404 });
    }
  }

  const { env } = await import("cloudflare:workers");
  const bucket = (env as { BOOK_ASSETS?: { get(key: string): Promise<{ text(): Promise<string> } | null> } }).BOOK_ASSETS;
  const obj = await bucket?.get(`tafsir-frag/${surah}/${ayah}.html`);
  if (!obj) return new Response("Not found", { status: 404 });

  return new Response(await obj.text(), {
    headers: { "content-type": "text/html; charset=utf-8", "cache-control": "public, max-age=86400" },
  });
};
