import { describe, it, expect } from "vitest";
import { markdownToSafeHtml } from "./sanitize.js";

describe("markdownToSafeHtml", () => {
  it("renders ordinary markdown", () => {
    const html = markdownToSafeHtml("## عنوان\n\nنص **عريض** و[رابط](https://example.com)");
    expect(html).toContain("<h2");
    expect(html).toContain("<strong>عريض</strong>");
    expect(html).toContain('href="https://example.com"');
  });

  it("neutralizes a raw <script> tag", () => {
    const html = markdownToSafeHtml("# مرحبا\n\n<script>alert('xss')</script>");
    expect(html).toContain("<h1");
    expect(html).not.toContain("<script");
    expect(html).not.toContain("alert(");
  });

  it("strips inline event handlers and dangerous attributes", () => {
    const html = markdownToSafeHtml('<img src="x" onerror="alert(1)">');
    expect(html).not.toContain("onerror");
    expect(html).not.toContain("alert(");
  });

  it("drops javascript: protocol links", () => {
    const html = markdownToSafeHtml("[click](javascript:alert(1))");
    expect(html).not.toContain("javascript:");
  });

  it("preserves RTL Arabic content unchanged", () => {
    const html = markdownToSafeHtml("بسم الله الرحمن الرحيم");
    expect(html).toContain("بسم الله الرحمن الرحيم");
  });

  it("wraps آية in its own token and quotes in the quote token", () => {
    const html = markdownToSafeHtml('قال تعالى ﴿إنا أعطيناك الكوثر﴾ وقال «من حسن إسلام المرء» وقال "كلمة"');
    expect(html).toContain('<span class="tok-ayah">﴿إنا أعطيناك الكوثر﴾</span>');
    expect(html).toContain('<span class="tok-quote">«من حسن إسلام المرء»</span>');
    expect(html).toContain('<span class="tok-quote">"كلمة"</span>');
  });

  it("does not tokenise inside code spans", () => {
    const html = markdownToSafeHtml('`«not a hadith»`');
    expect(html).not.toContain("tok-hadith");
    expect(html).toContain("<code>");
  });

  describe("sentence breaks", () => {
    it("breaks sentences after standard Arabic words", () => {
      const html = markdownToSafeHtml("وَبِاللَّهِ التَّوْفِيقُ. اعْلَمْ رَحِمَكَ اللَّهُ");
      expect(html).toContain('التَّوْفِيقُ.<br class="sentence-br">اعْلَمْ');
    });

    it("does not break on abbreviations or single Arabic letters", () => {
      const html1 = markdownToSafeHtml("د. أحمد طبيب");
      expect(html1).not.toContain("br");
      expect(html1).toContain("د. أحمد");

      const html2 = markdownToSafeHtml("أ.د. محمد أستاذ");
      expect(html2).not.toContain("br");

      const html3 = markdownToSafeHtml("ص. ١٢ من الكتاب");
      expect(html3).not.toContain("br");

      const html4 = markdownToSafeHtml("ط. الأولى");
      expect(html4).not.toContain("br");

      const html5 = markdownToSafeHtml("هـ. تاريخ");
      expect(html5).not.toContain("br");
    });

    it("does not break on numbers or decimals", () => {
      const html1 = markdownToSafeHtml("3.14 is pi");
      expect(html1).not.toContain("br");

      const html2 = markdownToSafeHtml("سنة ١٤٢١. طبعة");
      expect(html2).not.toContain("br");

      const html3 = markdownToSafeHtml("١.٥ للنسبة");
      expect(html3).not.toContain("br");
    });

    it("breaks sentences ending with brackets or quotes", () => {
      const html1 = markdownToSafeHtml("عَلَيْهِ وَسَلَّمَ (سبحانه وتعالى). اعْلَمْ أَنَّ");
      expect(html1).toContain('<span class="tok-paren">(سبحانه وتعالى)</span>.<br class="sentence-br">اعْلَمْ');

      const html2 = markdownToSafeHtml("قَالَتِ الطَّائِفَةُ «الْإِيمَانُ». فَقَالَتِ الْأُخْرَى");
      expect(html2).toContain('<span class="tok-quote">«الْإِيمَانُ»</span>.<br class="sentence-br">فَقَالَتِ');
    });

    it("does not break inside heading tags, code blocks, or custom tags", () => {
      const html1 = markdownToSafeHtml("## بَابُ نَعْتِ الْإِيمَانِ. اسْتِكْمَالِهِ");
      expect(html1).not.toContain("br");

      const html2 = markdownToSafeHtml("`التَّوْفِيقُ. اعْلَمْ`");
      expect(html2).not.toContain("br");

      const html3 = markdownToSafeHtml("[رابط. هنا](https://example.com)");
      expect(html3).not.toContain("br");
    });
  });
});
