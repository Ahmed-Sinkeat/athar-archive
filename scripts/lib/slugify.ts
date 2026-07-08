// Arabic → slug transliteration, shared by the Telegram + Blogspot importers.
// (epub-import.ts keeps its own inline copy — it calls main() unconditionally
// at module scope, so importing from it isn't safe.)
import { existsSync } from "node:fs";
import { join } from "node:path";

const TASHKEEL = /[ً-ْٰـ]/g;
const stripTashkeel = (s: string) => s.replace(TASHKEEL, "");
const TR: Record<string, string> = {
  "ا": "a", "أ": "a", "إ": "i", "آ": "a", "ٱ": "a",
  "ب": "b", "ت": "t", "ث": "th", "ج": "j", "ح": "h",
  "خ": "kh", "د": "d", "ذ": "dh", "ر": "r", "ز": "z",
  "س": "s", "ش": "sh", "ص": "s", "ض": "d", "ط": "t",
  "ظ": "z", "ع": "a", "غ": "gh", "ف": "f", "ق": "q",
  "ك": "k", "ل": "l", "م": "m", "ن": "n", "ه": "h",
  "ة": "a", "و": "w", "ؤ": "w", "ي": "y", "ى": "a",
  "ئ": "y", "ء": "",
  "٠": "0", "١": "1", "٢": "2", "٣": "3", "٤": "4",
  "٥": "5", "٦": "6", "٧": "7", "٨": "8", "٩": "9",
};
function translitWord(w: string): string {
  if (w.startsWith("ال") && w.length > 2) return "al-" + translitWord(w.slice(2));
  return [...w].map((c) => TR[c] ?? "").join("");
}
// ponytail: hard length cap — a source title can run to a whole paragraph (an
// isnad chain with no short title line); an uncapped slug blows past the
// filesystem filename limit (observed: ENAMETOOLONG on a 200+ char slug).
const MAX_SLUG_LEN = 100;

export function slugify(ar: string, fallbackPrefix = "manshoor"): string {
  let s = stripTashkeel(ar)
    .split(/\s+/).map(translitWord).join("-")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/-{2,}/g, "-")
    .replace(/^-+|-+$/g, "");
  if (s.length > MAX_SLUG_LEN) s = s.slice(0, MAX_SLUG_LEN).replace(/-[a-z0-9]*$/, "");
  return /^[a-z0-9]+(-[a-z0-9]+)*$/.test(s) ? s : `${fallbackPrefix}-` + Math.random().toString(36).slice(2, 8);
}

export function uniqueArticleSlug(base: string, articleDir: string): string {
  if (!existsSync(join(articleDir, base + ".md"))) return base;
  let v = 2;
  while (existsSync(join(articleDir, `${base}--v${v}.md`))) v++;
  return `${base}--v${v}`;
}

// YAML double-quoted scalar — a source title/body line can contain a literal
// newline (a multi-line book title, an isnad chain); raw newline bytes inside
// "..." break the YAML parser for anything reading the file back (observed:
// the dedup index crashed on a title spanning several lines).
export function y(s: string): string {
  return `"${s
    .replace(/\\/g, "\\\\")
    .replace(/"/g, '\\"')
    .replace(/\r\n/g, "\\n")
    .replace(/[\r\n]/g, "\\n")
    .replace(/\t/g, "\\t")}"`;
}
