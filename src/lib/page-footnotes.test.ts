import { describe, it, expect } from "vitest";
import { markdownToSafeHtml } from "./sanitize.js";
import { wirePageFootnotes } from "./page-footnotes.js";

const wire = (content: string, hashiya?: Map<number, string[]>) =>
  wirePageFootnotes(markdownToSafeHtml(content), content, hashiya);

describe("wirePageFootnotes", () => {
  describe("GFM footnotes ([^id] refs + defs)", () => {
    // defs separated by blank lines, as the importers emit them
    const content = `نص فيه إحالة[^a] وأخرى[^b].

[^a]: أول حاشية.

[^b]: ثانية حاشية.

<hr class="page-sep" data-page="6" />

نص الصفحة التالية.`;

    it("strips the end-of-document endnotes section", () => {
      expect(wire(content)).not.toContain("data-footnotes");
    });

    it("de-linkifies every ref into a plain page-numbered sup", () => {
      const html = wire(content);
      expect(html).not.toContain("data-footnote-ref");
      expect(html).toContain('<sup class="fn-ref">1</sup>');
      expect(html).toContain('<sup class="fn-ref">2</sup>');
    });

    it("renders the defs in a per-page footer box before the page marker", () => {
      const html = wire(content);
      const footerAt = html.indexOf('class="page-footnotes"');
      const sepAt = html.indexOf('id="p6"');
      expect(footerAt).toBeGreaterThan(-1);
      expect(sepAt).toBeGreaterThan(footerAt);
      expect(html).toContain("أول حاشية");
      expect(html).toContain('data-fn-num="2"');
    });

    it("numbers DB حاشية notes and GFM defs in one per-page sequence", () => {
      const html = wire(content, new Map([[5, ["حاشية من قاعدة البيانات."]]]));
      // hashiya takes 1, so the GFM defs shift to 2 and 3
      expect(html).toContain('<sup class="fn-ref">2</sup>');
      expect(html).toContain('<sup class="fn-ref">3</sup>');
      expect(html).toContain("حاشية من قاعدة البيانات");
    });
  });

  describe("EPUB footnotes (data-fn sups + page-sep data-notes)", () => {
    const content = `نص فيه إحالة<sup data-fn="1" data-sep-page="14">1</sup> ورقم عارٍ 2.

<hr class="page-sep" data-page="14" data-juz="1" data-notes='["أولى الصفحة.","ثانية الصفحة بلا علامة."]' />

نص الصفحة التالية.`;

    it("de-clickifies the sup, keeping the printed number", () => {
      const html = wire(content);
      expect(html).not.toContain("data-fn=");
      expect(html).toContain('<sup class="fn-ref">1</sup>');
    });

    it("renders EVERY note in the page footer, including ones with no tagged marker", () => {
      const html = wire(content);
      expect(html).toContain("أولى الصفحة");
      expect(html).toContain("ثانية الصفحة بلا علامة");
      expect(html).toContain('data-fn-num="2"');
    });

    it("places the footer before the page marker and keeps data-juz on it", () => {
      const html = wire(content);
      const footerAt = html.indexOf('class="page-footnotes"');
      const sepAt = html.indexOf('id="p14"');
      expect(footerAt).toBeGreaterThan(-1);
      expect(sepAt).toBeGreaterThan(footerAt);
      expect(html).toContain('data-juz="1"');
    });
  });

  describe("Caret footnotes ((^N) inline + defs, a legacy import format)", () => {
    const content = `نص فيه إحالة(^١) وأخرى(^٢).

(^١) أول حاشية.
(^٢) ثانية حاشية.

<hr class="page-sep" data-page="6" />

نص الصفحة التالية(^١).

(^١) حاشية الصفحة الثانية.`;

    it("converts the inline marker into a plain page-numbered sup", () => {
      const html = wire(content);
      expect(html).not.toContain("(^");
      expect(html).toContain('<sup class="fn-ref">١</sup>');
      expect(html).toContain('<sup class="fn-ref">٢</sup>');
    });

    it("renders the defs in a per-page footer box before the page marker", () => {
      const html = wire(content);
      const footerAt = html.indexOf('class="page-footnotes"');
      const sepAt = html.indexOf('id="p6"');
      expect(footerAt).toBeGreaterThan(-1);
      expect(sepAt).toBeGreaterThan(footerAt);
      expect(html).toContain("أول حاشية");
      expect(html).toContain("ثانية حاشية");
      expect(html).toContain("حاشية الصفحة الثانية");
    });

    it("splices a note that runs past a page break back onto the note it continues", () => {
      const withContinuation = `نص فيه إحالة(^١).

(^١) بداية الحاشية

<hr class="page-sep" data-page="9" />

= وتتمتها بعد كسر الصفحة.

نص الصفحة التالية.`;
      const html = wire(withContinuation);
      expect(html).not.toContain("= وتتمتها");
      expect(html).toContain("بداية الحاشية وتتمتها بعد كسر الصفحة.");
    });
  });

  it("emits each page separator once even when a marker repeats (Muwatta out-of-order pages)", () => {
    const content = `أ

<hr class="page-sep" data-page="90" />

ب

<hr class="page-sep" data-page="91" />

ج

<hr class="page-sep" data-page="90" />

د`;
    const html = wire(content);
    expect(html.match(/id="p90"/g)?.length).toBe(1);
    expect(html.match(/id="p91"/g)?.length).toBe(1);
  });

  it("keeps both volumes' page markers when page numbers repeat across juz (no cross-volume dedup/collision)", () => {
    const content = `أ

<hr class="page-sep" data-page="3" data-juz="1" />

ب

<hr class="page-sep" data-page="3" data-juz="2" />

ج`;
    const html = wire(content);
    expect(html.match(/class="page-sep"/g)?.length).toBe(2);
    expect(html).toContain('id="p3" data-page="3" data-juz="1"');
    expect(html).toMatch(/id="p3-v2" data-page="3" data-juz="2"/);
  });

  it("leaves footnote-free content untouched apart from page-sep normalization", () => {
    const html = wire("مجرد نص عادي بلا حواشٍ.");
    expect(html).not.toContain("page-footnotes");
    expect(html).toContain("مجرد نص عادي");
  });
});
