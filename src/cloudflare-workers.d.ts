// Ambient stub for `cloudflare:workers`: the Workers runtime provides this module
// at runtime (resolved by @astrojs/cloudflare at build), so tsc can't find it on
// its own. Used by the on-demand reading layer (src/middleware.ts,
// src/lib/book-asset.ts), which casts `env` to the binding shape it needs.
// ponytail: minimal stub, not the full @cloudflare/workers-types dependency.
declare module "cloudflare:workers" {
  export const env: unknown;
}
