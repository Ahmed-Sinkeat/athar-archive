export const config = {
  domain: "ahlalathar.com", // ratified (2026-06): .com chosen over .net — the eventual home
  // canonical origin for absolute URLs. TEMPORARY: points at the live workers.dev host so
  // pages get indexed while the domain isn't live. Flip back to https://ahlalathar.com at launch.
  siteUrl: "https://athar-archive.ahmedsinkeat2002.workers.dev",
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
  reportErrorMailto: "tashih@ahlalathar.com",
  topicsMax: 5,
  repo: "Ahmed-Sinkeat/athar-archive", // admin one-click «نشر إلى GitHub» prefill target
  repoBranch: "main", // single branch — status (مسودة/منشور) is a frontmatter field, not a branch
} as const;
