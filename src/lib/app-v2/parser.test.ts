import { readFileSync } from "node:fs";
import matter from "gray-matter";
import { describe, expect, it } from "vitest";
import { extractCard, parseReadableDocument } from "./parser.js";
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
    expect(parsed.footnotes).toEqual([{ id: "note", n: "١", x: "حاشية محفوظة" }]);
    expect(parsed.blocks.filter((block) => block.x).at(-1)?.x).toBe("النهاية");
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
    expect(document.blocks.map((block) => block.t)).toEqual(["li", "notes", "page", "h2", "p"]);
    expect(document.blocks.at(-1)).toMatchObject({ x: "نص الرابط", p: 4, sp: [{ k: "link", s: 3, e: 9, target: "https://example.org" }] });
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
      'نص.<sup data-fn="1" data-sep-page="1">١</sup>\n\n<hr class="page-sep" data-page="1" data-vol="1" data-notes="[&quot;الأولى&quot;]" />\n\nآخر<sup data-fn="1" data-sep-page="1">١</sup>\n\n<hr class="page-sep" data-page="1" data-vol="2" data-notes="[&quot;الثانية&quot;]" />',
    });
    expect(document.footnotes.map((note) => note.id)).toEqual(["1:1:1", "2:1:1"]);
    const paragraphs = document.blocks.filter((block) => block.t === "p");
    // epub-import refs stay plain superscripts: the notes show in each page's footer only.
    expect(paragraphs.map((block) => [block.vol, block.p, block.f, block.sp?.[0]?.k, block.sp?.[0]?.target])).toEqual([[1, 1, undefined, "sup", undefined], [2, 1, undefined, "sup", undefined]]);
  });

  it("keeps distinct notes on repeated pages within the same volume", () => {
    const document = parseReadableDocument({ coll: "book", id: "repeated-page", sourcePath: "repeated-page.md", body:
      `أول.<sup data-fn="1" data-sep-page="33">1</sup>\n\n<hr class="page-sep" data-page="33" data-vol="1" data-notes='["الأولى"]' />\n\nثان.<sup data-fn="1" data-sep-page="33">1</sup>\n\n<hr class="page-sep" data-page="33" data-vol="1" data-notes='["الثانية"]' />\n\nضمن<sup data-fn="1">1</sup>`,
    });
    expect(document.footnotes).toEqual([{ id: "1:33:1", n: "١", x: "الأولى" }, { id: "1:33:1~2", n: "١", x: "الثانية" }]);
    // One printed page split in two by the source: both notes share its one footer.
    expect(document.blocks.filter(block => block.t === "notes").map(block => block.f)).toEqual([["1:33:1", "1:33:1~2"]]);
    expect(document.blocks.filter(block => block.t === "p").map(block => block.f)).toEqual([undefined, undefined, undefined]);
  });

  it("joins a «=» note running on from the previous page instead of shifting the page's refs", () => {
    const document = parseReadableDocument({ coll: "book", id: "run-on", sourcePath: "run-on.md", body:
      `أول<sup data-fn="1" data-sep-page="1">1</sup>\n\n<hr class="page-sep" data-page="1" data-notes='["بداية"]' />\n\nثان<sup data-fn="1" data-sep-page="2">1</sup>\n\n<hr class="page-sep" data-page="2" data-notes='["= تتمة","رواه أبو داود رقم","، وصححه الألباني.","1. الثالثة"]' />`,
    });
    // The «(3251)» the importer cut the second note at is gone, but its tail rejoins it.
    expect(document.footnotes.map(note => [note.id, note.x])).toEqual([["1:1", "بداية تتمة"], ["2:1", "رواه أبو داود رقم ، وصححه الألباني."], ["2:2", "1. الثالثة"]]);
    expect(document.blocks.filter(block => block.t === "notes").map(block => block.f)).toEqual([["1:1"], ["2:1", "2:2"]]);
  });

  it("retains reused Markdown note labels and notes without an inline reference", () => {
    const document = parseReadableDocument({ coll: "book", id: "reused-label", sourcePath: "reused-label.md", body:
      "أول[^same]\n\n[^same]: الأولى\n\nثان[^same]\n\n[^same]: الثانية **محفوظة**\n\n[^unused]: إضافية",
    });
    expect(document.footnotes).toEqual([
      { id: "same", n: "١", x: "الأولى\n\nالثانية محفوظة", sp: [{ k: "strong", s: 16, e: 22 }] },
      { id: "unused", n: "٢", x: "إضافية" },
    ]);
    expect(document.blocks.at(-1)).toMatchObject({ t: "notes", f: ["same", "unused"] });
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
    expect(result.footnotes).toEqual([{ id: "fn1", n: "١", x: "حاشية" }]);
    expect(result.blocks.some(block => block.x === "تتمة")).toBe(true);
  });

  it("retains all imported page notes despite malformed reference numbering and volume transitions", () => {
    const document = parseReadableDocument({ coll: "book", id: "page-notes", sourcePath: "page-notes.md", body:
      `<hr class="page-sep" data-page="2" data-vol="1" />\n\nنص.<sup data-fn="5" data-sep-page="1">5</sup>\n\n<hr class="page-sep" data-page="1" data-vol="2" data-notes='["أولى","ثانية","ثالثة"]' />\n\nعدد<sup data-fn="1237" data-sep-page="2">1237</sup>\n\n<hr class="page-sep" data-page="2" data-vol="2" />`,
    });
    const paragraphs = document.blocks.filter(block => block.t === "p");
    // Ref 5 on a three-note page has nothing to point at; the page's notes still all show.
    expect(paragraphs[0]?.f).toBeUndefined();
    expect(paragraphs[1]?.x).toBe("عدد1237");
    expect(paragraphs[1]?.f).toBeUndefined();
    expect(document.blocks.find(block => block.t === "notes")).toMatchObject({ vol: 2, p: 1, f: ["2:1:1", "2:1:2", "2:1:3"] });
  });

  it("numbers every footnote format per printed page and closes each page with its notes", () => {
    const markdown = parseReadableDocument({ coll: "book", id: "md", sourcePath: "md.md", body:
      "أول[^a] ثان[^b].\n\n[^a]: حاشية أ\n\n[^b]: حاشية ب\n\nالجزء: 1 - الصفحة: 5\n\nثالث[^c]\n\n[^c]: حاشية ج\n\nالجزء: 1 - الصفحة: 6" });
    expect(markdown.blocks.map(block => [block.t, block.p, block.x ?? block.f])).toEqual([
      ["p", 5, "أول١ ثان٢."], ["notes", 5, ["a", "b"]], ["page", 5, undefined],
      ["p", 6, "ثالث١"], ["notes", 6, ["c"]], ["page", 6, undefined],
    ]);
    expect(markdown.blocks[0]?.sp).toEqual([{ k: "sup", s: 3, e: 4, target: "a" }, { k: "sup", s: 8, e: 9, target: "b" }]);
    expect(markdown.footnotes.map(note => [note.id, note.n])).toEqual([["a", "١"], ["b", "٢"], ["c", "١"]]);

    const caret = parseReadableDocument({ coll: "book", id: "caret", sourcePath: "caret.md", body:
      "نص (^١) وتتمة (^٢).\n\n(^١) أولى\n(^٢) ثانية تبدأ\n\n<hr class=\"page-sep\" data-page=\"9\" />\n\n= وتنتهي\n\nبعد" });
    expect(caret.blocks.map(block => [block.t, block.p, block.x ?? block.f])).toEqual([
      ["p", 9, "نص ١ وتتمة ٢."], ["notes", 9, ["9:c1", "9:c2"]], ["page", 9, undefined], ["p", 10, "بعد"],
    ]);
    expect(caret.blocks[0]?.sp?.map(span => span.target)).toEqual(["9:c1", "9:c2"]);
    expect(caret.footnotes).toEqual([{ id: "9:c1", n: "١", x: "أولى" }, { id: "9:c2", n: "٢", x: "ثانية تبدأ وتنتهي" }]);

    const trailing = parseReadableDocument({ coll: "book", id: "trailing", sourcePath: "trailing.md", body:
      "نص (^١)\n\n[٣٦] إسناده **صحيح**.\n(^١) كذا في الأصل\n\n<hr class=\"page-sep\" data-page=\"39\" />" });
    expect(trailing.blocks.map(block => [block.t, block.x ?? block.f])).toEqual([
      ["p", "نص ١"], ["p", "[٣٦] إسناده صحيح."], ["notes", ["39:c1"]], ["page", undefined],
    ]);
    expect(trailing.blocks[1]?.sp).toEqual([{ k: "strong", s: 12, e: 16 }]);
    expect(trailing.footnotes).toEqual([{ id: "39:c1", n: "١", x: "كذا في الأصل" }]);
  });

  it("turns balanced «صدر ... عجز» lines inside book prose into verses", () => {
    const parsed = parseReadableDocument({ coll: "book", id: "verse", sourcePath: "verse.md", body: [
      "قال الشاعر:",
      "سيأتيك **رزقك** في وقته ... ولو كنت في كبد الشعرى[^a]",
      "وهذا بيت حسن.",
      "",
      "وقد روى غيره عن عبد الرحمن بن عوسجة ... به",
      "",
      "[^a]: الشعرى نجم.",
    ].join("\n") });
    expect(parsed.blocks.map(block => [block.t, block.x])).toEqual([
      ["p", "قال الشاعر:"],
      ["verse", "سيأتيك رزقك في وقته\nولو كنت في كبد الشعرى١"],
      ["p", "وهذا بيت حسن."],
      ["p", "وقد روى غيره عن عبد الرحمن بن عوسجة ... به"],
      ["notes", undefined],
    ]);
    expect(parsed.blocks[1]?.sp).toEqual([{ k: "strong", s: 7, e: 11 }, { k: "sup", s: 41, e: 42, target: "a" }]);
    expect(parsed.blocks[1]?.f).toEqual(["a"]);
  });

});

describe("extractCard", () => {
  it("lifts the opening info card out of a book and keeps the real text", () => {
    const bullets = extractCard([
      "## المقدمة", "# عنوان الكتاب", "- **عَلَم:** فلان", "- **الكتاب:** عنوان الكتاب",
      "- **المؤلف:** فلان (المتوفى: 241هـ)", "- **الناشر:** دار", "", "## باب الطهارة", "نص",
    ].join("\n"));
    expect(bullets.card).toEqual([{ l: "الكتاب", v: "عنوان الكتاب" }, { l: "المؤلف", v: "فلان (المتوفى: 241هـ)" }, { l: "الناشر", v: "دار" }]);
    expect(bullets.body).toBe("## باب الطهارة\nنص");

    const plain = extractCard([
      "# بطاقة الكتاب وفهرس الموضوعات", "الكتاب: القناعة", "المؤلف: ابن أبي الدنيا", "[ترقيم الكتاب موافق للمطبوع]",
      "صفحة المؤلف: []", "## ـ[القناعة]ـ", "المؤلف: مكرر", "بسم الله الرحمن الرحيم",
    ].join("\n"));
    expect(plain.card).toEqual([{ l: "الكتاب", v: "القناعة" }, { l: "المؤلف", v: "ابن أبي الدنيا" }]);
    expect(plain.body).toBe("بسم الله الرحمن الرحيم");

    // «المقدمة» stays when real prose follows the card.
    expect(extractCard("## المقدمة\n- **الكتاب:** ك\nالحمد لله").body).toBe("## المقدمة\nالحمد لله");
    expect(extractCard("قال: الكتاب: ليس بطاقة").card).toEqual([]);
  });

  it("moves a formatter-marked <!-- card --> region to the card, keeping its page markers in the flow", () => {
    const marked = extractCard([
      "<!-- card -->", "النسخ المعتمدة:", "- **نسخة** خطية", "تحقيق: فلان", '<hr class="page-sep" data-page="2" />', "<!-- /card -->", "اعلم",
    ].join("\n"));
    expect(marked.card).toEqual([{ l: "", v: "النسخ المعتمدة:" }, { l: "", v: "نسخة خطية" }, { l: "تحقيق", v: "فلان" }]);
    expect(marked.body).toBe('<hr class="page-sep" data-page="2" />\nاعلم');
  });
});

describe("verses quoted inside prose", () => {
  it("drops the brackets a commentary puts around its matn verse, keeping the verse number", () => {
    const body = "قال:\n\n(تمسك بحبل الله واتبع الهدى ... ولا تك بدعيا لعلك تفلح)\n\n10 (وقد ينكر الجهمي أيضا يمينه ... وكلتا يديه بالفواضل تنفح)\n\n26 (ولا تكفرن أهل الصلاة وإن عصوا) ... (فكلهم يعصي وذو العرش يصفح)";
    const verses = parseReadableDocument({ coll: "book", id: "x", body, sourcePath: "x.md" }).blocks.filter((block) => block.t === "verse");
    expect(verses.map((verse) => verse.x)).toEqual([
      "تمسك بحبل الله واتبع الهدى\nولا تك بدعيا لعلك تفلح", "10 وقد ينكر الجهمي أيضا يمينه\nوكلتا يديه بالفواضل تنفح",
      "26 ولا تكفرن أهل الصلاة وإن عصوا\nفكلهم يعصي وذو العرش يصفح",
    ]);
  });
});

describe("prose marked as headings", () => {
  it("demotes hadith, isnad, numbered and orphan headings but keeps long titles", () => {
    const hadith = `«${"من كتم حمى يوم أصابه أخرجه الله من ذنوبه ".repeat(4)}»`;
    const bab = `باب ${"قول الله تعالى في كتابه العزيز ".repeat(5)}`;
    const parsed = parseReadableDocument({ coll: "book", id: "heads", sourcePath: "heads.md", body:
      `## الأول\n\n## ${hadith}\n\n### ${bab}\n\n### ُ: رفع القلم عن ثلاثة\n\nنص` });
    expect(parsed.blocks.map(block => block.t)).toEqual(["h2", "p", "h3", "p", "p"]);
    expect(parsed.chapters.map(chapter => chapter.title)).toEqual(["الأول"]);
  });
});

describe("refs on a page-number line", () => {
  it("keeps the marker a page break and puts the note in that page's footer", () => {
    const parsed = parseReadableDocument({ coll: "book", id: "lost", sourcePath: "lost.md", body:
      "نص.\n\n[^a] الجزء: 1 - الصفحة: 5\n\nبعد\n\n[^a]: حاشية" });
    expect(parsed.blocks.map(block => [block.t, block.p, block.x ?? block.f])).toEqual([
      ["p", 5, "نص."], ["notes", 5, ["a"]], ["page", 5, undefined], ["p", 6, "بعد"],
    ]);
    expect(parsed.footnotes).toEqual([{ id: "a", n: "١", x: "حاشية" }]);
  });
});

describe("a page marker repeated after each heading", () => {
  it("keeps one divider per page and one footer with all its notes", () => {
    const hr = (page: number) => `<hr class="page-sep" data-page="${page}" />`;
    const parsed = parseReadableDocument({ coll: "book", id: "rep", sourcePath: "rep.md", body:
      `## باب\n\nأول[^a]\n\n${hr(5)}\n\n## باب آخر\n\nثان[^b].\n\n${hr(5)}\n\nثالث\n\n${hr(6)}\n\n[^a]: حاشية\n\n[^b]: أخرى` });
    expect(parsed.blocks.map(block => [block.t, block.p, block.x ?? block.f])).toEqual([
      ["h2", 5, "باب"], ["p", 5, "أول١"], ["h2", 5, "باب آخر"], ["p", 5, "ثان١."],
      ["notes", 5, ["a", "b"]], ["page", 5, undefined], ["p", 6, "ثالث"], ["page", 6, undefined],
    ]);
    expect(parsed.chapters.map(chapter => chapter.block)).toEqual([0, 2]);
  });
});

describe("continuous reading", () => {
  it("joins a sentence the page cut, then shows that page's notes and divider after it", () => {
    const hr = (page: number, notes: string) => `<hr class="page-sep" data-page="${page}" data-notes='${notes}' />`;
    const parsed = parseReadableDocument({ coll: "book", id: "join", sourcePath: "join.md", body: [
      "تم الكلام.", "وقال الشيخ (^1) إن", hr(5, '["أولى"]'), "العلم **نور** (^1) يهدي", hr(6, '["ثانية"]'),
      "به.", hr(7, "[]"), "10- قال آخر", hr(8, "[]"), "**الأول:** لا يجتمع", "## باب", "بعده",
    ].join("\n\n") });
    expect(parsed.blocks.map(block => [block.t, block.p, block.x ?? block.f])).toEqual([
      ["p", 5, "تم الكلام."],
      ["p", 5, "وقال الشيخ 1 إن العلم نور 1 يهدي به."],
      ["notes", 5, ["5:1"]], ["page", 5, undefined], ["notes", 6, ["6:1"]], ["page", 6, undefined], ["page", 7, undefined],
      // A numbered item, a bold label and a heading after the break each stay their own block.
      ["p", 8, "10- قال آخر"], ["page", 8, undefined], ["p", 9, "الأول: لا يجتمع"], ["h2", 9, "باب"], ["p", 9, "بعده"],
    ]);
    // The second page's anchor and bold span move with its text; notes still link.
    expect(parsed.blocks[1]?.sp?.map(span => [span.k, parsed.blocks[1]!.x!.slice(span.s, span.e), span.target])).toEqual([
      ["sup", "1", "5:1"], ["strong", "نور", undefined], ["sup", "1", "6:1"],
    ]);
    expect(parsed.chapters.at(-1)?.block).toBe(10);
  });
});
