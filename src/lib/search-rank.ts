// How /api/search orders and shows passages (src/pages/api/search.ts uses it; the app's offline
// search follows the same order). Best first:
//   0. a work whose title starts with what was typed (the متن before its شروح)
//   1. the exact phrase   2. every exact word   3. words that only start the same
//   (any mode: 4. some exact word   5. some word that starts the same)
// Each tier excludes the tiers before it, so pages never repeat a passage. Inside a page, a
// passage with the spelling as typed (إنما over انما) comes first. The words are shown as written.
import { normalizeWithMap } from "./ar-normalize.js";

export type Mode = "all" | "any" | "phrase";

// Short particles (يا، في، من، لا) stay exact: as prefixes they match every word that starts so.
const MIN_PREFIX_LEN = 3;

/** FTS5 match expressions for tiers 1–5, each already excluding the ones before it. */
export function tierExprs(tokens: string[], mode: Mode): string[] {
  const unique = baseTiers(tokens, mode);
  return unique.map((e, i) => (i === 0 ? e : `(${e}) NOT (${unique.slice(0, i).join(" OR ")})`));
}

/** Everything the mode can match (the last tier, whole): for counts and the death-year order. */
export function widestExpr(tokens: string[], mode: Mode): string {
  return baseTiers(tokens, mode).at(-1) ?? '""';
}

function baseTiers(tokens: string[], mode: Mode): string[] {
  const t = tokens.map((x) => x.replaceAll('"', "")).filter(Boolean);
  if (!t.length) return [];
  const exact = (x: string) => `"${x}"`;
  const prefix = (x: string) => (x.length >= MIN_PREFIX_LEN ? `"${x}"*` : `"${x}"`);
  const phrase = `"${t.join(" ")}"`;
  const tiers = mode === "phrase"
    // a phrase whose last word is still being typed
    ? [phrase, t[t.length - 1].length >= MIN_PREFIX_LEN ? `${phrase} *` : phrase]
    : [phrase, t.map(exact).join(" AND "), t.map(prefix).join(" AND "),
      ...(mode === "any" ? [t.map(exact).join(" OR "), t.map(prefix).join(" OR ")] : [])];
  return [...new Set(tiers)];
}

/** Tier 0: the title column starts with the typed words. */
export function titleStartExpr(tokens: string[]): string {
  return `title : ^ "${tokens.map((x) => x.replaceAll('"', "")).join(" ")}"`;
}

const TASHKEEL = /[ؐ-ًؚ-ٰٟۖ-ۭـ]/g;

/**
 * The query as typed, when it says more than its folded form (a hamza, ة, ى or tashkeel):
 * then passages written that way go first. Null when there is nothing to prefer.
 */
export function typedSpelling(query: string, folded: string[]): string[] | null {
  const typed = query.normalize("NFC").trim().split(/\s+/).filter(Boolean);
  const keepsMarks = TASHKEEL.test(query);
  TASHKEEL.lastIndex = 0;
  const words = keepsMarks ? typed : typed.map((w) => w.replace(TASHKEEL, ""));
  return words.join(" ") === folded.join(" ") ? null : words;
}

/** Whether a shown passage has every typed word spelled as typed. */
export function hasTypedSpelling(snippet: string, words: string[]): boolean {
  const plain = snippet.replace(/<\/?mark>/g, "");
  const text = words.some((w) => TASHKEEL.test(w)) ? plain : plain.replace(TASHKEEL, "");
  TASHKEEL.lastIndex = 0;
  return words.every((w) => text.includes(w));
}

/**
 * FTS5's snippet() of the folded column, re-cut from [raw] (the passage as written) with the same
 * words marked. Unchanged when [raw] is missing or the window can't be found in it.
 */
export function exactSnippet(snippet: string, raw: string | null | undefined, ellipsis = "…"): string {
  if (!raw) return snippet;
  const lead = snippet.startsWith(ellipsis) ? ellipsis : "";
  const trail = snippet.endsWith(ellipsis) && snippet.length > lead.length ? ellipsis : "";
  const body = snippet.slice(lead.length, snippet.length - trail.length);
  let plain = "";
  const marks: [number, number][] = [];
  for (const part of body.split(/(<mark>.*?<\/mark>)/)) {
    if (part.startsWith("<mark>")) {
      const word = part.slice(6, -7);
      marks.push([plain.length, plain.length + word.length]);
      plain += word;
    } else plain += part;
  }
  const { text, at } = normalizeWithMap(raw);
  const start = text.indexOf(plain);
  if (start < 0 || !plain) return snippet;
  // a folded char's end is where the next one starts: its tashkeel stays with it
  const end = (i: number) => (start + i < at.length ? at[start + i] : raw.length);
  let out = lead;
  let from = at[start];
  for (const [a, b] of marks) {
    out += raw.slice(from, at[start + a]) + "<mark>" + raw.slice(at[start + a], end(b)) + "</mark>";
    from = end(b);
  }
  // the window's last word keeps its tashkeel, not the space after it
  const stop = end(plain.length);
  return out + raw.slice(from, stop).trimEnd() + trail;
}
