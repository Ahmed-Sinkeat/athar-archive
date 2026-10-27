// markdown/html → normalized plain text. Moved verbatim from
// scripts/gen-search-index.ts so the app content export (gen-app-content.ts)
// ships the EXACT text the D1 index sees — app-side offline search stays in
// parity with site search by construction, not by a hand-kept port.
import { normalizeArabic } from "./ar-normalize.js";

export function stripMd(md: string): string {
  return normalizeArabic(stripMdRaw(md));
}

/** The same plain text with its letters and tashkeel as written: what search results show. */
export function stripMdRaw(md: string): string {
  return md
    .replace(/<[^>]+>/g, " ")
    // footnote markers (^fn12, ^3, ^٣): noise in a search result
    .replace(/\^(?:fn)?[0-9\u0660-\u0669]+/g, " ")
    .replace(/\{#[^}]*\}/g, " ")
    .replace(/\[\[([^\]|]*\|)?([^\]]*)\]\]/g, "$2")
    .replace(/[#>*_`[\]()|]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}
