// Arabic normalization shared by the search indexer (scripts/gen-search-index.ts)
// and the query route (src/pages/api/search.ts). Both sides MUST normalize the
// same way or matches silently miss. FTS5's `remove_diacritics` only strips
// Latin diacritics, so Arabic tashkeel/quranic marks are handled here.

// harakat, quranic annotation marks, superscript alef, tatweel
const TASHKEEL = /[ؐ-ًؚ-ٰٟۖ-ۭـ]/g;

export function normalizeArabic(s: string): string {
  return s
    .normalize("NFC")
    .replace(TASHKEEL, "")
    .replace(/[أإآٱ]/g, "ا")
    .replace(/ى/g, "ي")
    .replace(/ة/g, "ه");
}

/**
 * [normalizeArabic] of an NFC string, with where each output char came from: `at[i]` is the
 * index in [s] of `text[i]`. Search maps a match found in the folded text back onto the words
 * as written. One pass over char codes (Worker CPU), kept equal to normalizeArabic by its test.
 */
export function normalizeWithMap(s: string): { text: string; at: number[] } {
  let text = "";
  const at: number[] = [];
  for (let i = 0; i < s.length; i++) {
    const c = s.charCodeAt(i);
    if ((c >= 0x610 && c <= 0x61a) || (c >= 0x64b && c <= 0x65f) || c === 0x670 || (c >= 0x6d6 && c <= 0x6ed) || c === 0x640) continue;
    const f = c === 0x623 || c === 0x625 || c === 0x622 || c === 0x671 ? 0x627 : c === 0x649 ? 0x64a : c === 0x629 ? 0x647 : c;
    text += String.fromCharCode(f);
    at.push(i);
  }
  return { text, at };
}
