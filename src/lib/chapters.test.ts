import { describe, it, expect } from "vitest";
import {
  slugifyArabic,
  splitChapters,
  parsePoem,
  parseBook,
  parseToc,
  extractAnchors,
} from "./chapters.js";

describe("slugifyArabic", () => {
  it("strips diacritics and hyphenates Arabic text", () => {
    expect(slugifyArabic("بَابُ الكَلامِ")).toBe("باب-الكلام");
  });

  it("lowercases and hyphenates latin", () => {
    expect(slugifyArabic("Chapter One!")).toBe("chapter-one");
  });

  it("trims leading/trailing separators", () => {
    expect(slugifyArabic("  — مقدمة —  ")).toBe("مقدمة");
  });

  it("returns empty string when nothing slug-able remains", () => {
    expect(slugifyArabic("!!! ---")).toBe("");
  });

  it("caps paragraph-long headings under the filesystem name limit", () => {
    const s = slugifyArabic("كلمة ".repeat(100));
    expect(s.length).toBeLessThanOrEqual(80);
    expect(Buffer.byteLength(s)).toBeLessThan(255);
    expect(s.endsWith("-")).toBe(false);
  });
});

describe("splitChapters", () => {
  it("separates preamble from h2 chapters", () => {
    const { preamble, chapters } = splitChapters(
      "تمهيد قبل الأبواب\n\n## الباب الأول\n\nمحتوى\n\n## الباب الثاني\n\nمحتوى آخر",
    );
    expect(preamble).toBe("تمهيد قبل الأبواب");
    expect(chapters).toHaveLength(2);
    expect(chapters[0].title).toBe("الباب الأول");
    expect(chapters[0].order).toBe(1);
    expect(chapters[1].order).toBe(2);
  });

  it("does not treat h3 as a chapter boundary", () => {
    const { chapters } = splitChapters("## باب\n\n### فرع\n\nمحتوى");
    expect(chapters).toHaveLength(1);
    expect(chapters[0].content).toContain("### فرع");
  });

  it("dedupes when a natural slug collides with a generated -N slug", () => {
    // "الغلط","الغلط" → الغلط, الغلط-2; then "الغلط 2" also → الغلط-2 naturally.
    // All three must end unique (the old base-counter map produced a clash).
    const chapters = splitChapters("## الغلط\nأ\n## الغلط\nب\n## الغلط 2\nج").chapters;
    const slugs = chapters.map((c) => c.slug);
    expect(new Set(slugs).size).toBe(slugs.length);
  });

  it("dedupes identical chapter slugs deterministically", () => {
    const { chapters } = splitChapters("## مقدمة\n\nأ\n\n## مقدمة\n\nب");
    expect(chapters[0].slug).toBe("مقدمة");
    expect(chapters[1].slug).toBe("مقدمة-2");
  });

  it("drops chapters that contain no verses or paragraphs (empty-chapter fix)", () => {
    const { chapters } = splitChapters("## باب فارغ\n\n## باب ممتلئ\n\nنص هنا");
    expect(chapters).toHaveLength(1);
    expect(chapters[0].title).toBe("باب ممتلئ");
  });
});

describe("parsePoem", () => {
  it("numbers verses globally and splits hemistichs", () => {
    const poem = parsePoem("## باب\n\nصدر أول --- عجز أول\n\nصدر ثان --- عجز ثان");
    expect(poem.verseCount).toBe(2);
    expect(poem.verses[0]).toMatchObject({ n: 1, sadr: "صدر أول", ajz: "عجز أول", anchor: "v1" });
    expect(poem.verses[1].anchor).toBe("v2");
    expect(poem.openingVerse).toBe("صدر أول");
  });

  it("counts verses in the preamble before chapter verses", () => {
    const poem = parsePoem("بيت تمهيدي --- عجزه\n\n## باب\n\nبيت في الباب --- عجزه");
    expect(poem.verseCount).toBe(2);
    expect(poem.verses[0].sadr).toBe("بيت تمهيدي");
    expect(poem.chapters[0].verses[0].n).toBe(2);
  });

  it("handles a single-hemistich verse (no separator)", () => {
    const poem = parsePoem("بيت بلا عجز");
    expect(poem.verses[0]).toMatchObject({ sadr: "بيت بلا عجز", anchor: "v1" });
    expect(poem.verses[0].ajz).toBeUndefined();
  });
});

describe("parseBook", () => {
  it("uses explicit {#id} anchors and auto-numbers the rest by position", () => {
    const book = parseBook("فقرة أولى {#intro}\n\nفقرة ثانية\n\n## باب\n\nفقرة ثالثة {#b1}");
    expect(book.paragraphs.map((p) => p.id)).toEqual(["intro", "p2", "b1"]);
    expect(book.paragraphs[0].text).toBe("فقرة أولى");
  });

  it("counts words excluding heading lines", () => {
    const book = parseBook("## عنوان طويل جدا\n\nكلمة كلمة كلمة");
    expect(book.wordCount).toBe(3);
  });
});

describe("parseToc", () => {
  it("builds a heading TOC with slugs and depth (h2-h6)", () => {
    const headings = parseToc("## المقدمة\n\nنص\n\n### فرع\n\nنص\n\n## الخاتمة");
    expect(headings).toHaveLength(3);
    expect(headings[0]).toMatchObject({ title: "المقدمة", slug: "المقدمة", depth: 2 });
    expect(headings[1].depth).toBe(3);
  });

  it("ignores h1 (= book title)", () => {
    const headings = parseToc("# العنوان\n\n## فصل\n\nنص");
    expect(headings).toHaveLength(1);
    expect(headings[0].depth).toBe(2);
  });
});

describe("extractAnchors", () => {
  it("returns verse anchors for a poem", () => {
    expect([...extractAnchors("poem", "أ --- ب\n\nج --- د")]).toEqual(["v1", "v2"]);
  });

  it("returns paragraph ids + heading slugs for a book", () => {
    const anchors = extractAnchors("book", "## عنوان\n\nفقرة {#x}\n\nفقرة أخرى");
    expect(anchors.has("x")).toBe(true);
    expect(anchors.has("عنوان")).toBe(true);
  });

  it("returns an empty set for collections without anchors", () => {
    expect(extractAnchors("article", "أي نص").size).toBe(0);
  });
});

