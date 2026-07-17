// Ambient stub for `cloudflare:workers`: the Workers runtime provides this module
// at runtime (resolved by @astrojs/cloudflare at build), so tsc can't find it on
// its own. Used by the on-demand reading layer (src/middleware.ts,
// src/lib/book-asset.ts), which casts `env` to the binding shape it needs.
// ponytail: minimal stub, not the full @cloudflare/workers-types dependency.
declare module "cloudflare:workers" {
  export const env: unknown;
}

// Build-run constant injected by vite.define (astro.config.ts) — used by
// Base.astro's <meta name="aa-build"> and reader.ts's cross-deploy check.
declare const __AA_BUILD__: string;
