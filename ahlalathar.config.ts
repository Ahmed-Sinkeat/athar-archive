export const config = {
  domain: "athar.arthurarchive.com", // TEMPORARY: placeholder domain, real one (athararchive.com) pending purchase
  siteUrl: "https://athar.arthurarchive.com",
  poemChapterThreshold: 200, // verses — above → chapter pages
  bookChapterThreshold: { words: 6000, chapters: 8 }, // above either → chapter pages
  // Below this printed-page span, never split into chapter routes even if the
  // word/chapter-count threshold above is crossed — a short book with many
  // brief chapters (e.g. a small fiqh matn) reads better as one page with an
  // inline h3/h4 jump-TOC than fragmented across chapter routes. Measured
  // from the source's own <hr data-page="N"> markers (max - min + 1); books
  // with no page markers at all can't be measured this way and fall back to
  // the word/chapter thresholds unchanged.
  bookMaxPagesForNoSplit: 100,
  reportErrorMailto: "tashih@arthurarchive.com",
  topicsMax: 5,
  repo: "Ahmed-Sinkeat/athar-archive", // admin one-click «نشر إلى GitHub» prefill target
  repoBranch: "main", // single branch — status (مسودة/منشور) is a frontmatter field, not a branch
} as const;
