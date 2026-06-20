export const config = {
  domain: "ahlalathar.com", // ratified (2026-06): .com chosen over .net
  siteUrl: "https://ahlalathar.com", // canonical origin for absolute URLs (P5)
  poemChapterThreshold: 200, // verses — above → chapter pages
  bookChapterThreshold: { words: 6000, chapters: 8 }, // above either → chapter pages
  reportErrorMailto: "tashih@ahlalathar.com",
  topicsMax: 5,
} as const;
