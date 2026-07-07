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

// --- Athar-numbered books (e.g. "١٧ - حدثني...") ---
// A corpus of narrations printed as "<number> - <isnad + matn>" per paragraph.
// parseAtharNumber reads the leading number off ONE paragraph's own text
// (Latin or Arabic-Indic digits) — no book-wide counter needed, the number is
// already in the source.

const ARABIC_INDIC = "٠١٢٣٤٥٦٧٨٩";
function digitsToNumber(digits: string): number {
  return Number([...digits].map((c) => { const i = ARABIC_INDIC.indexOf(c); return i >= 0 ? i : c; }).join(""));
}

const ATHAR_NUM_RE = /^\s*([0-9٠-٩]+)\s*-\s*/;

export function parseAtharNumber(paragraphText: string): number | null {
  const m = paragraphText.match(ATHAR_NUM_RE);
  if (!m) return null;
  const n = digitsToNumber(m[1]);
  return Number.isFinite(n) ? n : null;
}

// A book "is" athar-numbered when enough of its paragraphs carry the pattern
// (>=10) — avoids false positives from ordinary prose that happens to open a
// paragraph with a number (page refs, list items, etc.).
export function isAtharNumberedBook(paragraphs: { text: string }[]): boolean {
  let matches = 0;
  for (const p of paragraphs) {
    if (parseAtharNumber(p.text) !== null) matches++;
    if (matches >= 10) return true;
  }
  return false;
}

// The matn (quoted narration text, «…») of an athar paragraph — used to find
// the same narration repeated across other athar-numbered books (takhrij).
// ponytail: exact match on the first «…» quote only — zero false positives;
// upgrade to word-shingle similarity if recall proves poor on the real corpus.
export function parseAtharMatn(paragraphText: string): string | null {
  const m = paragraphText.match(/«([^»]+)»/);
  return m ? m[1].trim() : null;
}

// Inline HTML anchor + citation-link, prepended to each athar paragraph's own
// markdown block. Splits the same way parseBook() does (blank-line blocks,
// heading/hr excluded) so numbering lines up with what isAtharNumberedBook saw.
export interface TakhrijLink { title: string; href: string }

export function injectAtharAnchors(body: string, takhrijFor?: (n: number) => TakhrijLink[] | undefined): string {
  return body
    .split(/\n\s*\n/)
    .map((block) => {
      const trimmed = block.trim();
      if (!trimmed || /^#{1,6}\s/.test(trimmed) || /^-{3,}$/.test(trimmed)) return block;
      const n = parseAtharNumber(trimmed);
      if (n === null) return block;
      const links = takhrijFor?.(n);
      const takhrijHtml = links?.length
        ? `\n\n<div class="athar-takhrij">ورد أيضاً في: ${links.map((l) => `<a href="${l.href}">${l.title}</a>`).join("، ")}</div>`
        : "";
      return `<span id="athar-${n}" class="athar-anchor"></span><a href="#athar-${n}" class="athar-cite" data-athar="${n}" aria-label="نسخ رابط الأثر ${n}">#</a> ${block}${takhrijHtml}`;
    })
    .join("\n\n");
}
