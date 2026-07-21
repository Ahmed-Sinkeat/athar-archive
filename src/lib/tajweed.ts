// Tajweed rule detection over plain diacritized Arabic text (no Uthmani-mushaf
// print marks needed — our ayah text only ever has base harakat/tanween/shadda/
// sukun, confirmed against src/content/quran/*.md).
//
// Core matching logic ported from kodepandai/colorful-quran (MIT), letter sets
// cross-checked against fcat97/tajweedApi (MIT). Their engines lean on official
// Uthmani-mushaf-only marks (maddah, waqf signs) for the harder madd rules and
// for a few cross-word/font-specific edge cases — those marks don't exist in
// our text, so those specific branches are dropped rather than ported (they'd
// just never fire here). Madd (tabii/wajib/jaiz) is reimplemented from the
// harakat pattern directly instead, the same technique kodepandai's own
// mark-independent "madd arid lissukun" rule already uses.
//
// ponytail: scoped to the common in-word case — no cross-word idgham split,
// no shadda/hamzah-wau font-quirk adjustments (kodepandai carries both for
// mushaf fonts we don't use). Add them if a real ayah needs it.

const BLANK = " ";
const ALIF = "ا";
const ALIF_MAKSURA = "ى";
const BA = "ب";
const TA = "ت";
const TSA = "ث";
const JEEM = "ج";
const DAL = "د";
const DHAL = "ذ";
const RA = "ر";
const ZAY = "ز";
const SIN = "س";
const SYIN = "ش";
const SHAD = "ص";
const DHAD = "ض";
const TTA = "ط";
const DZA = "ظ";
const FA = "ف";
const QAF = "ق";
const KAF = "ك";
const LAM = "ل";
const MEEM = "م";
const NOON = "ن";
const WAW = "و";
// hamza on any of its 4 carrier letters, standalone, or fused with a
// mandatory madd into one codepoint (آ) — that last form also gets its own
// madd-badal rule below, since unlike the other four it inherently carries
// a madd whether or not another hamza follows it.
const HAMZAH_FORMS = ["ء", "أ", "إ", "ؤ", "ئ", "آ"];
const YA = "ي";

const FATHA = "َ";
const KASRA = "ِ";
const DAMMA = "ُ";
const SUKUN = "ْ";
const SHADDA = "ّ";
const TANWEEN = ["ً", "ٌ", "ٍ"];
const HARAKAT = [FATHA, KASRA, DAMMA, SUKUN, SHADDA, ...TANWEEN];

const HURUF = [
  ALIF, BA, TA, TSA, JEEM, "ح", "خ", DAL, DHAL, RA, ZAY, SIN, SYIN, SHAD, DHAD, TTA, DZA,
  "ع", "غ", FA, QAF, KAF, LAM, MEEM, NOON, WAW, "ه", YA, ...HAMZAH_FORMS,
];

const IKHFA_LETTERS = [TA, TSA, JEEM, DAL, DHAL, ZAY, SIN, SYIN, SHAD, DHAD, TTA, DZA, FA, QAF, KAF];
const IDGHAM_BIGHUNNAH_LETTERS = [YA, NOON, MEEM, WAW];
// the 14 "sun letters", LAM included — ال + ل + ه specifically is Allah's
// name (الله/لله, already covered by findElidedMadd) and is excluded by name
// in findShamsiyyah below, but every other ال + ل word (اللَّغْوِ, اللَّيْل,
// ...) is genuine lam-into-lam shamsiyyah and should still fire.
const SUN_LETTERS = [TA, TSA, DAL, DHAL, RA, ZAY, SIN, SYIN, SHAD, DHAD, TTA, DZA, LAM, NOON];

interface Match {
  rule: string;
  start: number;
  end: number;
}

function isChar(c: string | undefined, set: string | string[]): boolean {
  if (c === undefined) return false;
  return Array.isArray(set) ? set.includes(c) : c === set;
}

function isHuruf(c: string | undefined): boolean {
  return isChar(c, HURUF);
}

function nextSkip(a: string[], i: number): number {
  let c = i + 1;
  while (c < a.length && isChar(a[c], [BLANK, ALIF_MAKSURA])) c++;
  return c;
}

function nextHuruf(a: string[], i: number): [number, string | undefined] {
  let c = i + 1;
  while (c < a.length && !isHuruf(a[c])) c++;
  return [c, a[c]];
}

function prevHuruf(a: string[], i: number): [number, string | undefined] {
  let c = i - 1;
  while (c >= 0 && !isHuruf(a[c])) c--;
  return [c, a[c]];
}

// noon-sakinah / tanween followed (after skipping the space + a connecting
// alif-maksura) by one of `letters` — ikhfa/iqlab/idgham bighunnah/bilaghunnah
// all share this exact shape, only the letter set differs.
function findNoonBased(a: string[], rule: string, letters: string[]): Match[] {
  const out: Match[] = [];
  a.forEach((c, i) => {
    const noonSakin = isChar(c, NOON) && isChar(a[i + 1], SUKUN);
    const tanween = isChar(a[i + 1], TANWEEN);
    if (!noonSakin && !tanween) return;
    const next = nextSkip(a, i + 1);
    if (isChar(a[next], letters)) out.push({ rule, start: i, end: next + 2 });
  });
  return out;
}

// meem-sakinah followed by one of `letters` — ikhfa syafawi / idgham mimi.
function findMeemBased(a: string[], rule: string, letters: string[]): Match[] {
  const out: Match[] = [];
  a.forEach((c, i) => {
    if (!(isChar(c, MEEM) && isChar(a[i + 1], SUKUN))) return;
    const next = nextSkip(a, i + 1);
    if (isChar(a[next], letters)) out.push({ rule, start: i, end: next + 2 });
  });
  return out;
}

function findQalqalah(a: string[]): Match[] {
  const letters = [QAF, TTA, BA, JEEM, DAL];
  const out: Match[] = [];
  a.forEach((c, i) => {
    if (isChar(c, letters) && isChar(a[i + 1], SUKUN)) out.push({ rule: "qalqalah", start: i, end: i + 1 });
  });
  return out;
}

// idgham shamsiyyah: ال + a sun letter silences the lam and doubles the sun
// letter instead (shadda lands on the sun letter, not the lam) — ال + a moon
// letter keeps the lam voiced and carries no shadda, so this shape alone
// tells the two apart without needing to classify the letter itself.
function findShamsiyyah(a: string[]): Match[] {
  const out: Match[] = [];
  a.forEach((c, i) => {
    if (!isChar(c, ALIF) || !isChar(a[i + 1], LAM)) return;
    if (!isChar(a[i + 2], SUN_LETTERS) || !isChar(a[i + 3], SHADDA)) return;
    // ال + ل + ه: Allah's name (الله/لله/واللَّه/...), not a generic lam-into-lam
    // word — findElidedMadd already covers it, so skip it here specifically.
    if (isChar(a[i + 2], LAM) && isChar(a[i + 4], FATHA) && isChar(a[i + 5], "ه")) return;
    out.push({ rule: "idgham-shamsiyyah", start: i + 1, end: i + 4 });
  });
  return out;
}

// shadda-doubled noon/meem (ghunnah), excluding positions that belong to
// idgham-bighunnah instead (noon-sakin/tanween assimilating into a following
// noon/meem looks the same at the point of contact).
function findGhunnah(a: string[]): Match[] {
  const out: Match[] = [];
  a.forEach((c, i) => {
    if (!isChar(c, [NOON, MEEM])) return;
    // shadda then vowel is this corpus's consistent order (verified against
    // the actual content files), but tolerate vowel-then-shadda too — it's
    // one extra cheap check, not worth a silent miscolor if it ever varies.
    const shaddaAt = isChar(a[i + 1], SHADDA) ? i + 1 : isChar(a[i + 2], SHADDA) ? i + 2 : -1;
    if (shaddaAt < 0) return;
    const isFromIdgham = isChar(a[i - 1], TANWEEN) || (isChar(a[i - 1], SUKUN) && isChar(a[i - 2], [NOON, MEEM]));
    if (isFromIdgham) return;
    out.push({ rule: "ghunnah", start: i, end: shaddaAt + 1 });
  });
  return out;
}

// Medial madd waw/ya carry no diacritic of their own here (confirmed against
// the actual content — e.g. "الَّذِينَ" has no sukun codepoint on its ي at
// all); a sukun on waw/ya instead means a consonantal glide (e.g. "يَوْمِ"),
// not a madd letter. Bare letter + matching preceding vowel = madd.
function maddLetterSpan(a: string[], i: number): { end: number } | null {
  if (isChar(a[i], ALIF) && isChar(a[i - 1], FATHA)) return { end: i + 1 };
  if (isChar(a[i], ALIF_MAKSURA) && isChar(a[i - 1], FATHA)) return { end: i + 1 };
  const bare = !isChar(a[i + 1], HARAKAT);
  if (isChar(a[i], WAW) && bare && isChar(a[i - 1], DAMMA)) return { end: i + 1 };
  if (isChar(a[i], YA) && bare && isChar(a[i - 1], KASRA)) return { end: i + 1 };
  return null;
}

// madd letter (alif/waw/ya carrying the right harakat) not at the end of the
// ayah: wajib if a hamzah follows in the same word, jaiz if in the next word,
// tabii otherwise. End-of-ayah is madd-arid-lissukun's job, not this one's.
function findMadd(a: string[]): Match[] {
  const out: Match[] = [];
  a.forEach((c, i) => {
    const span = maddLetterSpan(a, i);
    if (!span || span.end >= a.length) return;
    const [nextIdx, nextC] = nextHuruf(a, span.end - 1);
    if (isChar(nextC, HAMZAH_FORMS)) {
      const crossesWord = a.slice(span.end, nextIdx).includes(BLANK);
      out.push({ rule: crossesWord ? "madd-jaiz" : "madd-wajib", start: i - 1, end: nextIdx + 1 });
    } else {
      out.push({ rule: "madd-tabii", start: i - 1, end: span.end });
    }
  });
  return out;
}

// آ (U+0622) fuses a hamza with a mandatory 2-harakat madd into one
// character ("madd al-badal") — unlike the other madd letters it has no
// preceding harakah to key off (the madd is inherent to the glyph itself),
// so it's matched directly rather than through maddLetterSpan.
function findMaddBadal(a: string[]): Match[] {
  const out: Match[] = [];
  a.forEach((c, i) => {
    if (isChar(c, "آ")) out.push({ rule: "madd-badal", start: i, end: i + 1 });
  });
  return out;
}

// A small closed set of words whose historic rasm elides the alif for a
// phonetically long vowel entirely — the Uthmani mushaf marks the spot with a
// dagger alif, but that mark doesn't exist in this plain text either, so
// there's no madd-letter character to pattern-match at all. Matched on the
// surrounding consonant cluster instead (the same cluster the dagger alif
// sits in), highlighting the vowel-bearing consonant through the next letter
// — the closest analogue to a normal madd-tabii span.
// ponytail: covers what Al-Fatiha needs (Allah, Ar-Rahman). A full-Quran
// rollout needs more entries (هذا, ذلك, هٰؤلاء, لكن, طه, ...).
function findElidedMadd(a: string[]): Match[] {
  const out: Match[] = [];
  a.forEach((c, i) => {
    // لَّه — the doubled lam of "Allah" (intrinsic, or via an assimilated/attached prefix)
    if (isChar(c, LAM) && isChar(a[i + 1], SHADDA) && isChar(a[i + 2], FATHA) && isChar(a[i + 3], "ه")) {
      out.push({ rule: "madd-tabii", start: i, end: i + 4 });
    }
    // حْمَن — the "-hman-" of "Rahman"
    if (isChar(c, "ح") && isChar(a[i + 1], SUKUN) && isChar(a[i + 2], MEEM) && isChar(a[i + 3], FATHA) && isChar(a[i + 4], NOON)) {
      out.push({ rule: "madd-tabii", start: i + 2, end: i + 5 });
    }
  });
  return out;
}

// last letter of the ayah preceded by a madd letter — the 2–6 harakat pause
// madd. Alif-maksura (ى) is checked directly off the array end rather than
// via prevHuruf/isHuruf: it's deliberately left out of HURUF (nextSkip relies
// on it being treated as a connector elsewhere), but a word/ayah-final
// alif-maksura after a fatha is the same long "a" sound as a plain alif here.
function findMaddAridLissukun(a: string[]): Match[] {
  if (isChar(a[a.length - 1], ALIF_MAKSURA) && isChar(a[a.length - 2], FATHA)) {
    return [{ rule: "madd-arid", start: a.length - 2, end: a.length }];
  }
  const [i, c] = prevHuruf(a, a.length);
  if (i < 0) return [];
  const bare = i === a.length - 1 || !isChar(a[i + 1], HARAKAT);
  const isYaMadd = isChar(c, YA) && bare && isChar(a[i - 1], KASRA);
  const isWawMadd = isChar(c, WAW) && bare && isChar(a[i - 1], DAMMA);
  const isAlifMadd = isChar(c, ALIF) && isChar(a[i - 1], FATHA);
  if (!isYaMadd && !isWawMadd && !isAlifMadd) return [];
  return [{ rule: "madd-arid", start: i - 1, end: a.length }];
}

function resolveOverlaps(matches: Match[]): Match[] {
  const sorted = [...matches].sort((a, b) => a.start - b.start || a.end - b.end);
  const out: Match[] = [];
  for (const m of sorted) {
    const prev = out[out.length - 1];
    if (prev && m.start < prev.end) {
      if (m.end <= prev.end) continue;
      out.push({ ...m, start: prev.end });
    } else {
      out.push(m);
    }
  }
  return out.filter((m) => m.end > m.start);
}

export function findTajweedMatches(ayahText: string): Match[] {
  const a = ayahText.split("");
  return resolveOverlaps([
    ...findQalqalah(a),
    ...findShamsiyyah(a),
    ...findNoonBased(a, "ikhfa", IKHFA_LETTERS),
    ...findNoonBased(a, "iqlab", [BA]),
    ...findNoonBased(a, "idgham-bighunnah", IDGHAM_BIGHUNNAH_LETTERS),
    ...findNoonBased(a, "idgham-bilaghunnah", [LAM, RA]),
    ...findMeemBased(a, "idgham-mimi", [MEEM]),
    ...findMeemBased(a, "ikhfa-syafawi", [BA]),
    ...findGhunnah(a),
    ...findMadd(a),
    ...findMaddBadal(a),
    ...findElidedMadd(a),
    ...findMaddAridLissukun(a),
  ]);
}

export function applyTajweed(ayahText: string): string {
  const a = ayahText.split("");
  const matches = findTajweedMatches(ayahText);
  let out = "";
  let cursor = 0;
  for (const m of matches) {
    if (m.start > cursor) out += a.slice(cursor, m.start).join("");
    out += `<span class="tw-${m.rule}">${a.slice(m.start, m.end).join("")}</span>`;
    cursor = m.end;
  }
  if (cursor < a.length) out += a.slice(cursor).join("");
  return out;
}

// Defaults cross-referenced against independent open-source tajweed color
// legends (noor-app, fcat97/tajweedApi) — customizable later, not canonical.
export const TAJWEED_COLORS: Record<string, string> = {
  qalqalah: "#DD1111",
  ikhfa: "#FF8C00",
  "ikhfa-syafawi": "#FF8C00",
  iqlab: "#8B008B",
  "idgham-bighunnah": "#209090",
  "idgham-bilaghunnah": "#3060AA",
  "idgham-mimi": "#209090",
  "idgham-shamsiyyah": "#787878",
  ghunnah: "#3A8C3A",
  "madd-tabii": "#CC8A00",
  "madd-wajib": "#7B3F00",
  "madd-jaiz": "#A0522D",
  "madd-arid": "#CC5500",
  "madd-badal": "#B8860B",
};

// Arabic names, same keys/order as TAJWEED_COLORS — drives the settings UI.
export const TAJWEED_LABELS: Record<string, string> = {
  qalqalah: "القلقلة",
  ikhfa: "الإخفاء",
  "ikhfa-syafawi": "الإخفاء الشفوي",
  iqlab: "الإقلاب",
  "idgham-bighunnah": "الإدغام بغنة",
  "idgham-bilaghunnah": "الإدغام بلا غنة",
  "idgham-mimi": "الإدغام الميمي",
  "idgham-shamsiyyah": "الإدغام الشمسي",
  ghunnah: "الغنة",
  "madd-tabii": "المد الطبيعي",
  "madd-wajib": "المد الواجب",
  "madd-jaiz": "المد الجائز",
  "madd-arid": "مد العارض للسكون",
  "madd-badal": "مد البدل",
};

// Alternate palettes for the settings UI. "default" just mirrors
// TAJWEED_COLORS; "vivid"/"muted" are the same 14 keys at higher/lower
// saturation, for visibility preference rather than any different legend.
export const TAJWEED_PRESETS: Record<string, Record<string, string>> = {
  default: TAJWEED_COLORS,
  vivid: {
    qalqalah: "#FF0000",
    ikhfa: "#FF9800",
    "ikhfa-syafawi": "#FF9800",
    iqlab: "#E91E63",
    "idgham-bighunnah": "#00BCD4",
    "idgham-bilaghunnah": "#2196F3",
    "idgham-mimi": "#00BCD4",
    "idgham-shamsiyyah": "#9E9E9E",
    ghunnah: "#4CAF50",
    "madd-tabii": "#FFC107",
    "madd-wajib": "#8B4513",
    "madd-jaiz": "#D2691E",
    "madd-arid": "#FF5722",
    "madd-badal": "#DAA520",
  },
  muted: {
    qalqalah: "#B85C5C",
    ikhfa: "#C99A66",
    "ikhfa-syafawi": "#C99A66",
    iqlab: "#8C6B8C",
    "idgham-bighunnah": "#6B9B9B",
    "idgham-bilaghunnah": "#6B84A8",
    "idgham-mimi": "#6B9B9B",
    "idgham-shamsiyyah": "#999999",
    ghunnah: "#7B9B7B",
    "madd-tabii": "#B99A66",
    "madd-wajib": "#8C7355",
    "madd-jaiz": "#A08066",
    "madd-arid": "#B87F5C",
    "madd-badal": "#A68A4D",
  },
};
