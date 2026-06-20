// Markdown → safe HTML. Untrusted volunteer Markdown may contain raw HTML and
// scripts; this pipeline parses raw HTML (rehype-raw) and strips anything not
// in the schema (rehype-sanitize), so <script> and friends never reach output
// (FR-B-02, SEC-02). Used for inline snippets (verses, benefit quotes) and as
// the controllable rendering path for content bodies.

import { unified } from "unified";
import remarkParse from "remark-parse";
import remarkGfm from "remark-gfm";
import remarkRehype from "remark-rehype";
import rehypeRaw from "rehype-raw";
import rehypeSanitize from "rehype-sanitize";
import rehypeStringify from "rehype-stringify";
import { sanitizeSchema } from "./sanitize-schema.js";
import { slugifyArabic } from "./chapters.js";

// Gather visible text from a hast node subtree.
function collectText(node: any): string {
  if (node.type === "text") return node.value ?? "";
  if (node.children) return node.children.map(collectText).join("");
  return "";
}

// Give headings stable Arabic-slug ids (matches parseLesson() anchors so the
// lesson TOC and rendered headings align). Runs after sanitize.
function rehypeHeadingIds() {
  return (tree: any) => {
    const seen = new Map<string, number>();
    const visit = (node: any) => {
      if (node.type === "element" && /^h[1-6]$/.test(node.tagName)) {
        let slug = slugifyArabic(collectText(node)) || "section";
        const n = seen.get(slug) ?? 0;
        seen.set(slug, n + 1);
        if (n > 0) slug = `${slug}-${n + 1}`;
        node.properties = node.properties || {};
        if (!node.properties.id) node.properties.id = slug;
      }
      if (node.children) node.children.forEach(visit);
    };
    visit(tree);
  };
}

const processor = unified()
  .use(remarkParse)
  .use(remarkGfm)
  .use(remarkRehype, { allowDangerousHtml: true })
  .use(rehypeRaw)
  .use(rehypeSanitize, sanitizeSchema)
  .use(rehypeHeadingIds)
  .use(rehypeStringify);

export function markdownToSafeHtml(markdown: string): string {
  return String(processor.processSync(markdown));
}
