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
});
