import { readFileSync } from "node:fs";
import matter from "gray-matter";
import { describe, expect, it } from "vitest";
import { parseReadableDocument } from "./parser.js";
import type { ReadableCollection } from "./contract.js";

function parseFile(coll: ReadableCollection, id: string) {
  const directory = coll === "question" ? "question" : coll;
  const sourcePath = `src/content/${directory}/${id}.md`;
  const body = matter(readFileSync(sourcePath, "utf8")).content;
  return parseReadableDocument({ coll, id, body, sourcePath });
}

describe("app/v2 semantic parser", () => {
  it("parses the exact four-entry vertical slice without losing its structures", () => {
    const article = parseFile(
      "article",
      "byan-kdhb-athr-idha-safr-al-fqr-ila-mkan-ma-qal-al-kfr-khdhny-mak-ala-aby-dhr--v2",
    );
    expect(article.blocks).toHaveLength(3);
    expect(article.blocks.every((block) => block.t === "p")).toBe(true);

    const book = parseFile("book", "nawaqid-al-islam");
    expect(book.chapters).toEqual([{ a: "نواقض-الإسلام", title: "نواقض الإسلام", block: 0 }]);
    expect(book.pages).toEqual({ from: 23, to: 28, vols: 1 });
    expect(book.blocks.filter((block) => block.t === "page")).toHaveLength(6);
    expect(book.blocks.filter((block) => block.t === "li")).toHaveLength(6);
    expect(book.blocks.flatMap((block) => block.sp ?? []).filter((span) => span.k === "strong")).toHaveLength(10);

    const question = parseFile("question", "swteat-866");
    expect(question.chapters.map((chapter) => chapter.title)).toEqual(["السؤال", "الجواب"]);
    expect(question.blocks.flatMap((block) => block.sp ?? []).filter((span) => span.k === "link")).toEqual([
      expect.objectContaining({ target: "http://alkulify.blogspot.com/2015/03/blog-post_17.html?m=1" }),
    ]);

    const poem = parseFile("poem", "qasidat-madh-al-sunnah-wa-ittiba-al-salaf");
    const verses = poem.blocks.filter((block) => block.t === "verse");
    expect(verses).toHaveLength(29);
    expect(verses[0]).toMatchObject({ n: 1, s: "ضلَّ المجسّمُ والمعطّلُ مثلُه", j: "عن منهجِ الحقِّ المبينِ ضلالا" });
    expect(verses[28]).toMatchObject({ n: 29, s: "والأصلُ ما كان الرَّسولُ وصحبُهُ" });
    expect(poem.blocks.at(-1)).toMatchObject({ t: "p", x: "[تمت والحمد لله رب العالمين]" });
  });

  it("uses UTF-16 offsets and preserves authored Unicode", () => {
    const document = parseReadableDocument({
      coll: "article",
      id: "offset-test",
      sourcePath: "offset-test.md",
      body: "😀 **الرَّحمن** و[[book:kitab|كتابٌ]]",
    });
    expect(document.blocks[0]?.x).toBe("😀 الرَّحمن وكتابٌ");
    expect(document.blocks[0]?.sp).toEqual([
      { k: "strong", s: 3, e: 11 },
      { k: "entityRef", s: 13, e: 18, target: "book:kitab" },
    ]);
  });

  it("fails visibly instead of silently stripping unsupported syntax", () => {
    expect(() => parseReadableDocument({
      coll: "article",
      id: "bad",
      sourcePath: "bad.md",
      body: "before\n\n![unsupported](image.png)",
    })).toThrow("bad.md:3: unsupported Markdown node image");
  });
});
