// [[type:slug]] or [[type:slug|label]] — internal cross-reference syntax for bodies.
// Shared by graph.ts (backlink indexing) and sanitize.ts (rendering). Only typed,
// known-entity targets resolve; bare/unknown forms are left as plain text.

export const WIKILINK_TYPES = new Set([
  "book", "poem", "person", "subject", "topic", "series", "article", "question", "term", "benefit",
]);

// [[ type : slug (|label)? ]]  — slug = lowercase/digits/hyphens with -- child separators.
export const WIKILINK_RE =
  /\[\[([a-z]+):([a-z0-9][a-z0-9-]*(?:--[a-z0-9-]+)*)(?:\|([^\]\n|]+))?\]\]/g;

export function parseWikilinks(body: string): { type: string; slug: string }[] {
  const out: { type: string; slug: string }[] = [];
  for (const m of body.matchAll(WIKILINK_RE)) {
    if (WIKILINK_TYPES.has(m[1])) out.push({ type: m[1], slug: m[2] });
  }
  return out;
}
