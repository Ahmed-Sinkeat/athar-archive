// search.arthurarchive.com: the search API on its own (see src/lib/search-handler.ts).
import { searchResponse } from "../../src/lib/search-handler.js";

export default {
  fetch(request: Request): Promise<Response> | Response {
    if (request.method !== "GET") return new Response(null, { status: 405 });
    return searchResponse(new URL(request.url));
  },
};
