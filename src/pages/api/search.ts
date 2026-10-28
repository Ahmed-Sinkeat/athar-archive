// The search API lives in its own Worker (workers/search, search.arthurarchive.com) so it deploys
// in seconds, apart from the site. This route stays for the site's search box and older app
// builds: it forwards there, or answers itself when the binding is missing (local dev).
import type { APIRoute } from "astro";
import { searchResponse } from "../../lib/search-handler.js";

export const prerender = false;

export const GET: APIRoute = async ({ url, request }) => {
  const { env } = await import("cloudflare:workers");
  const api = (env as unknown as { SEARCH_API?: { fetch(r: Request): Promise<Response> } }).SEARCH_API;
  return api ? api.fetch(request) : searchResponse(url);
};
