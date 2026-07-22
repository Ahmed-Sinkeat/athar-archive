// Display helpers: Arabic-Indic numerals for reading contexts, canonical
// route building, and Arabic entity labels. Western digits stay in URLs/ids.

const AR_DIGITS = ["٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩"];

export function toArabicDigits(input: string | number): string {
  return String(input).replace(/[0-9]/g, (d) => AR_DIGITS[Number(d)]);
}

// Combining harakat + tatweel; shared by the client reader's tashkeel toggle.
export const TASHKEEL_RE = /[ؗ-ًؚ-ْٰـۖ-ۭ࣓-ࣿ]/g;

export function stripTashkeel(text: string): string {
  return text.replace(TASHKEEL_RE, "");
}

// A small minority of audio entries (~15 of 900+) encode the reciter's name
// in their own title as "العمل — القارئ" (e.g. "تائية الإلبيري — سالم
// العنزي") — most don't carry a reciter at all, there's no dedicated schema
// field for it. Pull the name out when it's there instead of showing the
// raw, unsplit label (today's multi-track behavior) or nothing (today's
// single-track behavior).
export function reciterOf(label?: string | null): string | undefined {
  return label?.split(" — ")[1]?.trim() || undefined;
}

// Arabic singular/plural labels + index routes per entity (URL Map §01).
export const ENTITY: Record<
  string,
  { one: string; many: string; index: string }
> = {
  person: { one: "عَلَم", many: "الأعلام", index: "/people" },
  subject: { one: "تصنيف", many: "الموضوعات", index: "/subjects" },
  topic: { one: "موضوع", many: "الموضوعات", index: "/topics" },
  book: { one: "كتاب", many: "الكتب", index: "/books" },
  poem: { one: "منظومة", many: "المنظومات", index: "/poems" },
  // ponytail: series + lesson retired; درس is a book; سلسلة شرح is book.sharh_of
  question: { one: "مسائل", many: "المسائل", index: "/questions" },
  benefit: { one: "فائدة", many: "الفوائد", index: "/benefits" },
  article: { one: "مقالة", many: "مقالات ومحاضرات", index: "/articles" },
  term: { one: "مصطلح", many: "المعجم", index: "/mujam" },
};

// Literary eras (العصر) — keep values in sync with the person.era enum in content.config.ts.
// Ordered chronologically for stable chip ordering.
export const ERA_VALUES = ["الجاهلي", "صدر الإسلام", "الأموي", "العباسي", "الأندلسي", "المتأخّر", "الحديث"] as const;
// الطبقة العلمية (للعلماء) — distinct from the literary العصر (which is for poets).
export const TABAQA_VALUES = ["الصحابة", "التابعون", "أتباع التابعين", "المتقدمون", "المتوسطون", "المتأخرون", "المعاصرون"] as const;
const ERA_LABELS: Record<string, string> = {
  "صدر الإسلام": "صدر الإسلام/المخضرمون",
  "المتأخّر": "المتأخّر (المملوكي/العثماني)",
};
export function eraLabel(value?: string): string {
  return value ? (ERA_LABELS[value] ?? value) : "";
}

// Stable latin slugs for era pages (/era/<slug>). Arabic era values aren't
// URL-safe, so we map them explicitly (and back).
const ERA_SLUGS: Record<string, string> = {
  "الجاهلي": "jahili",
  "صدر الإسلام": "sadr-al-islam",
  "الأموي": "umawi",
  "العباسي": "abbasi",
  "الأندلسي": "andalusi",
  "المتأخّر": "mutaakhkhir",
  "الحديث": "hadith",
};
export function eraSlug(value?: string): string | undefined {
  return value ? ERA_SLUGS[value] : undefined;
}
export function eraFromSlug(slug: string): string | undefined {
  return (Object.keys(ERA_SLUGS) as string[]).find((k) => ERA_SLUGS[k] === slug);
}
export function eraHref(value?: string): string | undefined {
  const s = eraSlug(value);
  return s ? `/era/${s}` : undefined;
}

// Kind-aware display label: books show their kind (متن/مرجع/مجموع); poems are always منظومة.
export function labelFor(collection: string, data: Record<string, any> = {}): string {
  if (collection === "book") return (data.work_type as string) || (data.kind as string) || "كتاب";
  if (collection === "poem") return (data.work_type as string) || "منظومة";
  return ENTITY[collection]?.one ?? collection;
}

// Is this entry a متن (memorizable)? Every poem, and books explicitly marked متن.
export function isMatn(collection: string, data: Record<string, any> = {}): boolean {
  return collection === "poem" || (collection === "book" && data.kind === "متن");
}


export function hrefFor(
  collection: string,
  id: string,
  opts: { series?: string; chapter?: string } = {},
): string {
  switch (collection) {
    case "person": return `/person/${id}`;
    case "subject": return `/subject/${id}`;
    case "topic": return `/topic/${id}`;
    case "book": return opts.chapter ? `/book/${id}/${opts.chapter}` : `/book/${id}`;
    case "poem": return opts.chapter ? `/poem/${id}/${opts.chapter}` : `/poem/${id}`;
    case "benefit": return `/benefit/${id}`;
    case "article": return `/article/${id}`;
    case "question": return `/questions/${id}`;
    case "term": return `/term/${id}`;
    default: return "/";
  }
}

// Source/target type → its reader route (for "appears in" links on embedded entities).
export function sourceHref(type: string, id: string): string {
  return hrefFor(type, id);
}

export interface SearchScope { type?: string; in?: string; person?: string; label?: string }

// /search link carrying a page's scope (collection type, or a specific work's
// "in" scope) — shared by Base.astro's header search and TitleFilter.astro's
// "no matches on this page" fallback, so both hand off to the same slice of
// the index instead of a generic sitewide search.
export function searchHrefFor(scope?: SearchScope): string {
  if (!scope) return "/search";
  const p = new URLSearchParams();
  if (scope.in) { p.set("in", scope.in); if (scope.label) p.set("label", scope.label); }
  else if (scope.person) { p.set("person", scope.person); if (scope.label) p.set("label", scope.label); }
  else if (scope.type) { p.set("scope", scope.type); }
  const qs = p.toString();
  return qs ? `/search?${qs}` : "/search";
}
