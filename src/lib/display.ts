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

// Arabic singular/plural labels + index routes per entity (URL Map §01).
export const ENTITY: Record<
  string,
  { one: string; many: string; index: string }
> = {
  person: { one: "عَلَم", many: "الأعلام", index: "/people" },
  subject: { one: "تصنيف", many: "الموضوعات", index: "/subjects" },
  topic: { one: "موضوع", many: "الموضوعات", index: "/topics" },
  book: { one: "متن", many: "المكتبة", index: "/books" },
  poem: { one: "منظومة", many: "المنظومات", index: "/poems" },
  series: { one: "سلسلة", many: "الدروس", index: "/series" },
  lesson: { one: "درس", many: "الدروس", index: "/series" },
  question: { one: "مسائل", many: "المسائل", index: "/questions" },
  benefit: { one: "فائدة", many: "الفوائد", index: "/benefits" },
  article: { one: "مقالة", many: "المقالات", index: "/articles" },
};

// Lesson route param = lesson id minus its "<series>--" prefix.
export function lessonParam(lessonId: string, seriesId: string): string {
  return lessonId.startsWith(`${seriesId}--`) ? lessonId.slice(seriesId.length + 2) : lessonId;
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
    case "series": return `/series/${id}`;
    case "lesson": return `/series/${opts.series}/${lessonParam(id, opts.series ?? "")}`;
    case "benefit": return `/benefit/${id}`;
    case "article": return `/article/${id}`;
    case "question": return `/questions/${id}`;
    default: return "/";
  }
}

// Source/target type → its reader route (for "appears in" links on embedded entities).
export function sourceHref(type: string, id: string): string {
  return hrefFor(type, id);
}
