import { normalizeArabic } from "./ar-normalize.js";

// Phase 2 formatting pass: a book may only gain or lose markup. Text and punctuation must stay
// in the same order (diacritics and whitespace ignored), frontmatter byte-identical, page markers
// identical apart from data-notes, whose notes may be re-split/merged but not reworded.
// Run scripts/format-verify.ts before committing.
const PAGE_MARKS = /<hr\b[^>]*\bpage-sep\b[^>]*>/gi;
const NOTES_ATTR = /\s*data-notes='([^']*)'/;

function split(file: string): { front: string; body: string } {
  const match = file.match(/^---\n[\s\S]*?\n---\n/);
  return { front: match?.[0] ?? "", body: file.slice(match?.[0].length ?? 0) };
}

function notesText(tag: string): string {
  const attr = tag.match(NOTES_ATTR)?.[1];
  if (!attr) return "";
  const notes: unknown = JSON.parse(attr.replaceAll("&quot;", '"').replaceAll("&#39;", "'").replaceAll("&amp;", "&"));
  return Array.isArray(notes) ? notes.join("") : "";
}

function text(body: string, importFix = false): string {
  // import-fix: merging a split page renumbers its anchors; the anchor digits are not the author's text
  const unmarked = (importFix ? body.replace(/\(\^[0-9]+\)/g, "") : body)
    .replace(PAGE_MARKS, (tag) => `\u0001${notesText(tag)}\u0001`)
    .replace(/<[^>]+>/g, "")
    .replace(/^[ \t]*(?:#{1,6}[ \t]+|[-*+][ \t]+|>[ \t]?|(?:-{3,}|\*{3,})[ \t]*$)/gm, "")
    .replace(/\*\*|\{#[\w-]+\}/g, "")
    // ponytail: «...»/«…» is how imports draw the gap between two hemistichs, i.e. layout; a prose
    // ellipsis added or dropped slips through. Count only spaced ones if that ever matters.
    .replace(/\.\.\.|…/g, "")
    // a footnote anchor may be bare «1», «(1)» or a caret «(^1)»: all the same printed number
    .replace(/\((?:\^)?([0-9٠-٩]+)\)/g, "$1");
  return normalizeArabic(unmarked).replace(/\s+/g, "");
}

function pages(body: string): string {
  return (body.match(PAGE_MARKS) ?? []).map((tag) => tag.replace(NOTES_ATTR, "").replace(/\s+/g, " ").trim()).join("\n");
}

/**
 * Returns an error message, or undefined when `after` is a markup-only edit of `before` (whole files).
 * `importFix` is for repairing import damage: page numbers may change and text may move (a
 * scrambled verse, a marker inside a reference), but not one character may be added or lost.
 */
export function verifyFormatting(before: string, after: string, importFix = false): string | undefined {
  const [a, b] = [split(before), split(after)];
  if (a.front !== b.front) return "frontmatter changed";
  // import-fix: numbers may change, and of two consecutive markers with one number (a page the import
  // split at a heading) the first may go.
  const unnumbered = (body: string) => !importFix ? pages(body) : pages(body).split("\n")
    .filter((tag, index, all) => tag.match(/data-page="[^"]*"/)?.[0] !== all[index + 1]?.match(/data-page="[^"]*"/)?.[0])
    .map((tag) => tag.replace(/\sdata-page="[^"]*"/, "")).join("\n");
  if (unnumbered(a.body) !== unnumbered(b.body)) return "page markers differ";
  const sorted = (body: string) => importFix ? [...text(body, true).replaceAll("\u0001", "")].sort().join("") : text(body);
  const [ta, tb] = [sorted(a.body), sorted(b.body)];
  if (ta === tb) return undefined;
  if (importFix) return "text differs: a character was added or lost, not just moved";
  let at = 0;
  while (ta[at] === tb[at]) at++;
  return `text differs at char ${at}: «${ta.slice(Math.max(0, at - 20), at + 20)}» vs «${tb.slice(Math.max(0, at - 20), at + 20)}»`;
}
