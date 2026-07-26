// markdown/html → normalized plain text. Moved verbatim from
// scripts/gen-search-index.ts so the app content export (gen-app-content.ts)
// ships the EXACT text the D1 index sees — app-side offline search stays in
// parity with site search by construction, not by a hand-kept port.
import { normalizeArabic } from "./ar-normalize.js";

export function stripMd(md: string): string {
  return normalizeArabic(
    md
      .replace(/<[^>]+>/g, " ")
      .replace(/\{#[^}]*\}/g, " ")
      .replace(/\[\[([^\]|]*\|)?([^\]]*)\]\]/g, "$2")
      .replace(/[#>*_`[\]()|]/g, " ")
      .replace(/\s+/g, " ")
      .trim(),
  );
}
