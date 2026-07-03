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
