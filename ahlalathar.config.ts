export const config = {
  domain: "ahlalathar.net", // OPEN: extension (.net | .com) undecided — confirm before absolute URLs
  poemChapterThreshold: 200, // verses — above → chapter pages
  bookChapterThreshold: { words: 6000, chapters: 8 }, // above either → chapter pages
  reportErrorMailto: "tashih@ahlalathar.net",
  topicsMax: 5,
} as const;
