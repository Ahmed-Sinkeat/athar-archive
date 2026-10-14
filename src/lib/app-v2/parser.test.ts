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
  it("keeps each authored poem line as its own verse", () => {
    const poem = parseReadableDocument({ coll: "poem", id: "lines", sourcePath: "lines.md",
      body: "صدر أول --- عجز أول\nصدر ثان --- عجز ثان" });
    expect(poem.blocks.map(block => [block.n, block.s, block.j])).toEqual([[1, "صدر أول", "عجز أول"], [2, "صدر ثان", "عجز ثان"]]);
    const whole = parseReadableDocument({ coll: "poem", id: "whole", sourcePath: "whole.md", body: "بيت أول\nبيت ثان" });
    expect(whole.blocks.map(block => [block.t, block.n, block.x])).toEqual([["verse", 1, "بيت أول"], ["verse", 2, "بيت ثان"]]);
  });

  it("keeps an introduction paragraph inside a poem file as prose", () => {
    const prose = "وقد شرح ألفية ابن مالك كثيرون من أئمة علماء النحو، ".repeat(4);
    const doc = parseReadableDocument({ coll: "poem", id: "intro", sourcePath: "intro.md", body: `شرّاح الألفية:\n${prose}\n\nصدر بيت ... عجز بيت` });
    expect(doc.blocks.map(b => b.t)).toEqual(["p", "p", "verse"]);
  });

  it("parses large books by section without dropping cross-section links, footnotes, or page order", () => {
    const paragraph = "نصّ محفوظ ".repeat(100);
    const body = "## أول\n\n" + (paragraph + "\n\n").repeat(1000)
      + "[رابط][remote] وحاشية[^note]\n\n## ثان\n\n[remote]: https://example.com/source\n\n[^note]: حاشية محفوظة\n\nالنهاية";
    const parsed = parseReadableDocument({ coll: "book", id: "large", body, sourcePath: "large.md" });
    expect(parsed.chapters.map((chapter) => chapter.title)).toEqual(["أول", "ثان"]);
    expect(parsed.blocks.filter((block) => block.x === paragraph.trim())).toHaveLength(1000);
    expect(parsed.blocks.flatMap((block) => block.sp ?? [])).toContainEqual(expect.objectContaining({ target: "https://example.com/source" }));
    expect(parsed.footnotes).toEqual([{ id: "note", x: "حاشية محفوظة" }]);
    expect(parsed.blocks.at(-1)?.x).toBe("النهاية");
    const unsupported = body + "\n\n![bad](image.png)";
    expect(() => parseReadableDocument({ coll: "book", id: "large", body: unsupported, sourcePath: "large.md" }))
      .toThrow(`large.md:${unsupported.split("\n").length}: unsupported Markdown node image`);
  });

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

  it("preserves imported HTML, page markers inside lists, and lists inside footnotes", () => {
    const document = parseReadableDocument({ coll: "book", id: "imported", sourcePath: "imported.md",
      body: '## \n\n- أصل[^note]\n\n  <hr class="page-sep" data-page="3" />\n\n<div><h2>عنوان</h2><p>نص <a href="https://example.org">الرابط</a></p></div>\n\n[^note]: حاشية\n\n    - تفصيل **مهم**\n',
    });
    expect(document.blocks.map((block) => block.t)).toEqual(["li", "page", "h2", "p"]);
    expect(document.blocks.at(-1)).toMatchObject({ x: "نص الرابط", p: 3, sp: [{ k: "link", s: 3, e: 9, target: "https://example.org" }] });
    expect(document.footnotes[0]).toMatchObject({ id: "note", x: "حاشية\n\n• تفصيل مهم" });
  });

  it("fails visibly instead of silently stripping unsupported syntax", () => {
    expect(() => parseReadableDocument({
      coll: "article",
      id: "bad",
      sourcePath: "bad.md",
      body: "before\n\n![unsupported](image.png)",
    })).toThrow("bad.md:3: unsupported Markdown node image");
  });

  it("keeps repeated printed-page footnotes separate across volumes", () => {
    const document = parseReadableDocument({ coll: "book", id: "volumes", sourcePath: "volumes.md", body:
      '<hr class="page-sep" data-page="1" data-vol="1" data-notes="[&quot;الأولى&quot;]" />\n\nنص<sup data-fn="1">١</sup>\n\n<hr class="page-sep" data-page="1" data-vol="2" data-notes="[&quot;الثانية&quot;]" />\n\nآخر<sup data-fn="1">١</sup>',
    });
    expect(document.footnotes.map((note) => note.id)).toEqual(["1:1:1", "2:1:1"]);
    expect(document.blocks.filter((block) => block.t === "p").map((block) => block.f)).toEqual([["1:1:1"], ["2:1:1"]]);
  });

  it("keeps distinct notes on repeated pages within the same volume", () => {
    const document = parseReadableDocument({ coll: "book", id: "repeated-page", sourcePath: "repeated-page.md", body:
      `أول<sup data-fn="1" data-sep-page="33">1</sup>\n\n<hr class="page-sep" data-page="33" data-vol="1" data-notes='["الأولى"]' />\n\nثان<sup data-fn="1" data-sep-page="33">1</sup>\n\n<hr class="page-sep" data-page="33" data-vol="1" data-notes='["الثانية"]' />\n\nضمن<sup data-fn="1">1</sup>`,
    });
    expect(document.footnotes).toEqual([{ id: "1:33:1", x: "الأولى" }, { id: "1:33:1~2", x: "الثانية" }]);
    expect(document.blocks.filter(block => block.t === "page").map(block => block.f)).toEqual([["1:33:1"], ["1:33:1~2"]]);
    expect(document.blocks.filter(block => block.t === "p").map(block => block.f)).toEqual([["1:33:1"], ["1:33:1~2"], ["1:33:1~2"]]);
  });

  it("retains reused Markdown note labels and notes without an inline reference", () => {
    const document = parseReadableDocument({ coll: "book", id: "reused-label", sourcePath: "reused-label.md", body:
      "أول[^same]\n\n[^same]: الأولى\n\nثان[^same]\n\n[^same]: الثانية **محفوظة**\n\n[^unused]: إضافية",
    });
    expect(document.footnotes).toEqual([
      { id: "same", x: "الأولى\n\nالثانية محفوظة", sp: [{ k: "strong", s: 16, e: 22 }] },
      { id: "unused", x: "إضافية" },
    ]);
    expect(new Set(document.blocks.flatMap(block => block.f ?? []))).toEqual(new Set(["same", "unused"]));
  });

  it("preserves quoted lists and inline styling inside a verse", () => {
    const book = parseReadableDocument({ coll: "book", id: "quoted", sourcePath: "quoted.md", body: '> - قول **محفوظ**\n> - تتمة' });
    expect(book.blocks.map((block) => [block.t, block.quoted, block.x])).toEqual([["li", true, "قول محفوظ"], ["li", true, "تتمة"]]);
    const poem = parseReadableDocument({ coll: "poem", id: "verse", sourcePath: "verse.md", body: '١- صدر *بيت* ... عجز بيت' });
    expect(poem.blocks[0]).toMatchObject({ t: "verse", n: 1, x: "صدر بيت ... عجز بيت", sp: [{ k: "emphasis", s: 4, e: 7 }] });
  });

  it("preserves literal text inside imported prose backticks", () => {
    const parsed = parseReadableDocument({ coll: "book", id: "literal", sourcePath: "literal.md", body: 'نص `محفوظ كما هو` ثم تتمة' });
    expect(parsed.blocks[0]?.x).toBe("نص محفوظ كما هو ثم تتمة");
  });
  it("omits imported position markers without losing adjacent text", () => {
    const result = parseReadableDocument({ coll: "book", id: "markers", sourcePath: "markers.md",
      body: "قبل <m s =1> وسط <m s=189720 > بعد" });
    expect(result.blocks.map(block => block.x).join(" ")).toBe("قبل  وسط  بعد");
  });

  it("retains footnote definitions nested in imported lists", () => {
    const result = parseReadableDocument({ coll: "book", id: "list-note", sourcePath: "list-note.md",
      body: "نص[^fn1]\n\n- [^fn1]: حاشية\n- تتمة" });
    expect(result.footnotes).toEqual([{ id: "fn1", x: "حاشية" }]);
    expect(result.blocks.some(block => block.x === "تتمة")).toBe(true);
  });

  it("retains all imported page notes despite malformed reference numbering and volume transitions", () => {
    const document = parseReadableDocument({ coll: "book", id: "page-notes", sourcePath: "page-notes.md", body:
      `<hr class="page-sep" data-page="2" data-vol="1" />\n\nنص<sup data-fn="5" data-sep-page="1">5</sup>\n\n<hr class="page-sep" data-page="1" data-vol="2" data-notes='["أولى","ثانية","ثالثة"]' />\n\nعدد<sup data-fn="1237" data-sep-page="2">1237</sup>\n\n<hr class="page-sep" data-page="2" data-vol="2" />`,
    });
    const paragraphs = document.blocks.filter(block => block.t === "p");
    expect(paragraphs[0]?.f).toEqual(["2:1:1", "2:1:2", "2:1:3"]);
    expect(paragraphs[1]?.x).toBe("عدد1237");
    expect(paragraphs[1]?.f).toBeUndefined();
    expect(document.blocks.find(block => block.t === "page" && block.vol === 2 && block.p === 1)?.f)
      .toEqual(["2:1:1", "2:1:2", "2:1:3"]);
  });

});
