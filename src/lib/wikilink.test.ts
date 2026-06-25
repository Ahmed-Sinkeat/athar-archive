import { describe, it, expect } from "vitest";
import { parseWikilinks } from "./wikilink";
import { markdownToSafeHtml } from "./sanitize";

describe("wikilinks", () => {
  it("parses typed wikilinks from a body", () => {
    expect(parseWikilinks("see [[person:malik]] and [[book:al-muwatta|الموطأ]] x")).toEqual([
      { type: "person", slug: "malik" },
      { type: "book", slug: "al-muwatta" },
    ]);
  });

  it("ignores unknown types", () => {
    expect(parseWikilinks("[[wat:foo]] [[person:x]]")).toEqual([{ type: "person", slug: "x" }]);
  });

  it("renders a wikilink to an internal anchor", () => {
    const html = markdownToSafeHtml("راجع [[person:malik]].");
    expect(html).toContain('href="/person/malik"');
    expect(html).toContain("wikilink");
  });

  it("renders a labelled wikilink with its label text", () => {
    const html = markdownToSafeHtml("[[book:al-muwatta|الموطأ]]");
    expect(html).toContain('href="/book/al-muwatta"');
    expect(html).toContain("الموطأ");
  });

  it("leaves unknown-type wikilinks as literal text", () => {
    const html = markdownToSafeHtml("[[wat:foo]]");
    expect(html).toContain("[[wat:foo]]");
    expect(html).not.toContain("<a");
  });
});
