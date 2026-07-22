import { describe, it, expect } from "vitest";
import { normalizePageMarkers } from "./read-body.js";
import { markdownToSafeHtml } from "./sanitize.js";
import { wirePageFootnotes } from "./page-footnotes.js";

describe("normalizePageMarkers", () => {
  it("converts a plain-text page marker into the real <hr class=page-sep> the rest of the pipeline expects", () => {
    const out = normalizePageMarkers('نص.\n\nالجزء: 1 - الصفحة: 59\n\nنص آخر.');
    expect(out).toContain('<hr class="page-sep" data-page="59" data-juz="1" />');
    expect(out).not.toContain("الجزء: 1 - الصفحة: 59");
  });

  it("leaves content with no plain-text markers untouched", () => {
    const content = 'نص فيه <hr class="page-sep" data-page="6" /> علامة صحيحة أصلاً.';
    expect(normalizePageMarkers(content)).toBe(content);
  });

  it("converts the الحديث-prefixed and non-numeric-جزء marker variants too", () => {
    expect(normalizePageMarkers("الحديث: 129 - الجزء: 5 - الصفحة: 165"))
      .toBe('<hr class="page-sep" data-page="165" data-juz="5" />');
    expect(normalizePageMarkers("الجزء: مقدمة ¦ الصفحة: 2"))
      .toBe('<hr class="page-sep" data-page="2" data-juz="مقدمة" />');
  });

  it("keeps a footnote ref that's glued onto the marker line, ahead of the converted marker", () => {
    const out = normalizePageMarkers("[^fn419] الجزء: 1 - الصفحة: 264");
    expect(out).toBe('[^fn419] <hr class="page-sep" data-page="264" data-juz="1" />');
  });

  it("drops a bare volume-start marker (no page number) instead of leaving it as stray text", () => {
    const out = normalizePageMarkers("قبله.\n\nالجزء: 1\n\nبعده.");
    expect(out).not.toContain("الجزء: 1");
    expect(out).toContain("قبله.");
    expect(out).toContain("بعده.");
    expect(normalizePageMarkers("الجزء: 69 - 70\n")).toBe("");
  });

  it("does not eat real prose that happens to start the same way as a bare volume marker", () => {
    // a dictionary entry defining the word "جزء" itself — not a marker
    const content = "الجزء: بعض الكل، وجمعه أجزاء، وقيل: جزء الشيء ما تتقوم به جملته.";
    expect(normalizePageMarkers(content)).toBe(content);
  });

  it("end-to-end: once normalized, footnotes group by their real page instead of by document-wide order", () => {
    const raw = `نص فيه إحالة[^a] وأخرى[^b].

[^a]: أول حاشية.

[^b]: ثانية حاشية.

الجزء: 1 - الصفحة: 59

نص الصفحة التالية.`;
    const content = normalizePageMarkers(raw);
    const html = wirePageFootnotes(markdownToSafeHtml(content), content);
    expect(html).toContain('<sup class="fn-ref">1</sup>');
    expect(html).toContain('<sup class="fn-ref">2</sup>');
    expect(html).toContain('id="p59"');
    expect(html).not.toContain("data-footnote-ref"); // de-linkified like every other import path
  });
});
