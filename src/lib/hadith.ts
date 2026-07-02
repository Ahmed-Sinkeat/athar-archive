// Arabic ordinal-word parsing for "## الحديث <ordinal>" headings (matn hubs
// like the Arbaeen). Mirrors scripts/export-hadith-index.ts on the engine side
// — kept in sync by hand since the two repos don't share code at build time.

function normalizeArabic(text: string): string {
  return text
    .normalize("NFKC")
    .replace(/[ً-ْٰٓ]/g, "")
    .replace(/ـ/g, "")
    .replace(/[إأآ]/g, "ا")
    .replace(/ة/g, "ه")
    .replace(/ى/g, "ي")
    .replace(/\s+/g, " ")
    .trim();
}

const UNITS = ["", "الاول", "الثاني", "الثالث", "الرابع", "الخامس", "السادس", "السابع", "الثامن", "التاسع"];
const UNITS_COMPOUND = ["", "الحادي", "الثاني", "الثالث", "الرابع", "الخامس", "السادس", "السابع", "الثامن", "التاسع"];
const TENS = ["", "العاشر", "العشرون", "الثلاثون", "الاربعون", "الخمسون", "الستون", "السبعون", "الثمانون", "التسعون"];

function buildOrdinalMap(): Map<string, number> {
  const map = new Map<string, number>();
  for (let n = 1; n <= 9; n++) map.set(normalizeArabic(UNITS[n]), n);
  map.set(normalizeArabic(TENS[1]), 10); // العاشر
  for (let n = 11; n <= 19; n++) map.set(normalizeArabic(`${UNITS_COMPOUND[n - 10]} عشر`), n);
  for (let tens = 2; tens <= 9; tens++) {
    map.set(normalizeArabic(TENS[tens]), tens * 10);
    for (let unit = 1; unit <= 9; unit++) {
      map.set(normalizeArabic(`${UNITS_COMPOUND[unit]} و${TENS[tens]}`), tens * 10 + unit);
    }
  }
  // Tens words decline (نصب/جر "العشرين" vs رفع "العشرون") — accept either.
  for (const [key, value] of [...map]) {
    if (key.endsWith("ون")) map.set(`${key.slice(0, -2)}ين`, value);
  }
  return map;
}
const ORDINAL_MAP = buildOrdinalMap();

export function parseHadithOrdinal(heading: string): number | null {
  const m = heading.trim().match(/^الحديث\s+(.+?)[:.]?$/);
  if (!m) return null;
  return ORDINAL_MAP.get(normalizeArabic(m[1])) ?? null;
}
