import { test, expect } from "vitest";
import { stripFrontmatter } from "./book-asset";

test("stripFrontmatter removes leading YAML block, keeps body", () => {
  const md = '---\ntitle: "x"\nstatus: published\n---\n## باب\nنص';
  expect(stripFrontmatter(md)).toBe("## باب\nنص");
});

test("stripFrontmatter is a no-op without frontmatter", () => {
  const md = "## باب\nنص --- داخل السطر";
  expect(stripFrontmatter(md)).toBe(md);
});
