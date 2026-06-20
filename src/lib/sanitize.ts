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

const processor = unified()
  .use(remarkParse)
  .use(remarkGfm)
  .use(remarkRehype, { allowDangerousHtml: true })
  .use(rehypeRaw)
  .use(rehypeSanitize, sanitizeSchema)
  .use(rehypeStringify);

export function markdownToSafeHtml(markdown: string): string {
  return String(processor.processSync(markdown));
}
