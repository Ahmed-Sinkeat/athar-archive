import { describe, expect, it, test } from "vitest";
import { stripFrontmatter, substitute } from "./book-asset";

test("stripFrontmatter removes leading YAML block, keeps body", () => {
  const md = '---\ntitle: "x"\nstatus: published\n---\n## باب\nنص';
  expect(stripFrontmatter(md)).toBe("## باب\nنص");
});

test("stripFrontmatter is a no-op without frontmatter", () => {
  const md = "## باب\nنص --- داخل السطر";
  expect(stripFrontmatter(md)).toBe(md);
});

const ASSETS = JSON.stringify({ "Base.css": "/_astro/Base.AbCdEfGh.css", "page.js": "/_astro/page.Zz123456.js" });

describe("substitute", () => {
  it("swaps every placeholder occurrence and stamps the build id", () => {
    const stored =
      '<meta name="aa-build" content="">' +
      '<link href="/_astro-live/Base.css"><script src="/_astro-live/page.js"></script>' +
      '<link href="/_astro-live/Base.css">';
    expect(substitute(stored, ASSETS, "b42")).toBe(
      '<meta name="aa-build" content="b42">' +
      '<link href="/_astro/Base.AbCdEfGh.css"><script src="/_astro/page.Zz123456.js"></script>' +
      '<link href="/_astro/Base.AbCdEfGh.css">',
    );
  });

  it("leaves an unknown token alone rather than blanking the URL", () => {
    expect(substitute('<link href="/_astro-live/gone.css">', ASSETS, "b42")).toBe('<link href="/_astro-live/gone.css">');
  });

  it("passes pre-placeholder pages through untouched", () => {
    const old = '<link href="/_astro/Base.OldHash1.css"><p>بابٌ في الأثر</p>';
    expect(substitute(old, ASSETS, "b42")).toBe(old);
  });
});
