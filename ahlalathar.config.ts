export const config = {
  domain: "ahlalathar.com", // ratified (2026-06): .com chosen over .net — the eventual home
  // canonical origin for absolute URLs. TEMPORARY: points at the live workers.dev host so
  // pages get indexed while the domain isn't live. Flip back to https://ahlalathar.com at launch.
  siteUrl: "https://athar-archive.ahmedsinkeat2002.workers.dev",
  poemChapterThreshold: 200, // verses — above → chapter pages
  bookChapterThreshold: { words: 6000, chapters: 8 }, // above either → chapter pages
  reportErrorMailto: "tashih@ahlalathar.com",
  topicsMax: 5,
  repo: "Ahmed-Sinkeat/athar-archive", // admin one-click «نشر إلى GitHub» prefill target
  repoBranch: "main", // single branch — status (مسودة/منشور) is a frontmatter field, not a branch
} as const;
