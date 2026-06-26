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
import { hrefFor } from "./display.js";
import { WIKILINK_RE, WIKILINK_TYPES } from "./wikilink.js";

// Gather visible text from a hast node subtree.
function collectText(node: any): string {
  if (node.type === "text") return node.value ?? "";
  if (node.children) return node.children.map(collectText).join("");
  return "";
}

// Wrap recognised Arabic spans in colour tokens from punctuation already in the
// text → no database. ﴿…﴾ is unambiguous (Quranic ornate brackets).
// Quotes «…» / “…” all get ONE colour: punctuation can't
// tell a hadith from any other citation, so we don't guess. The distinct
// `tok-hadith` colour stays in CSS for an explicit opt-in later.
// Runs AFTER sanitize. Skips code/pre. First matching opener wins.
// ponytail: \u escapes — esbuild rejects raw Arabic chars in regex literals
const TOK_RE = new RegExp(
  "(﴿[^﴾]*﴾)" +
  "|(\\[[\\u0600-\\u06FF\\s]+:[\\d\\u0660-\\u0669]+(?:[-\\u2013][\\d\\u0660-\\u0669]+)?\\])" +
  "|(«[^»]*»)" +
  "|([“”][^“”]*[“”])" +
  '|("[^"]*")' +
  "|(\\([^)]{1,200}\\))",  // ponytail: cap 200 chars to avoid backtracking
  "g"
);
const TOK_CLASS = ["tok-ayah", "tok-quran-ref", "tok-quote", "tok-quote", "tok-quote", "tok-paren"];

function splitTokens(value: string): any[] {
  const out: any[] = [];
  let last = 0;
  for (const m of value.matchAll(TOK_RE)) {
    const i = m.index!;
    if (i > last) out.push({ type: "text", value: value.slice(last, i) });
    const cls = TOK_CLASS[m.slice(1).findIndex(Boolean)];
    out.push({ type: "element", tagName: "span", properties: { className: [cls] }, children: [{ type: "text", value: m[0] }] });
    last = i + m[0].length;
  }
  if (last < value.length) out.push({ type: "text", value: value.slice(last) });
  return out.length ? out : [{ type: "text", value }];
}

function rehypeArabicTokens() {
  return (tree: any) => {
    const walk = (node: any, parentTag: string | null) => {
      if (!node.children) return;
      const next: any[] = [];
      for (const child of node.children) {
        if (child.type === "text" && parentTag !== "code" && parentTag !== "pre") {
          next.push(...splitTokens(child.value ?? ""));
        } else {
          if (child.type === "element") walk(child, child.tagName);
          next.push(child);
        }
      }
      node.children = next;
    };
    walk(tree, null);
  };
}

// [[type:slug]] / [[type:slug|label]] → internal link (subtle .wikilink). Runs
// after sanitize; only known entity types resolve, unknown forms left as text.
function splitWikiLinks(value: string): any[] {
  const out: any[] = [];
  let last = 0;
  for (const m of value.matchAll(WIKILINK_RE)) {
    const [full, type, slug, label] = m;
    const i = m.index!;
    if (i > last) out.push({ type: "text", value: value.slice(last, i) });
    if (WIKILINK_TYPES.has(type)) {
      out.push({
        type: "element", tagName: "a",
        properties: { href: hrefFor(type, slug), className: ["wikilink"], title: label ?? slug },
        children: [{ type: "text", value: label ?? slug }],
      });
    } else {
      out.push({ type: "text", value: full });
    }
    last = i + full.length;
  }
  if (last < value.length) out.push({ type: "text", value: value.slice(last) });
  return out.length ? out : [{ type: "text", value }];
}

function rehypeWikiLinks() {
  return (tree: any) => {
    const walk = (node: any, parentTag: string | null) => {
      if (!node.children) return;
      const next: any[] = [];
      for (const child of node.children) {
        if (child.type === "text" && parentTag !== "code" && parentTag !== "pre" && parentTag !== "a") {
          next.push(...splitWikiLinks(child.value ?? ""));
        } else {
          if (child.type === "element") walk(child, child.tagName);
          next.push(child);
        }
      }
      node.children = next;
    };
    walk(tree, null);
  };
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

// Color السؤال/الجواب bold markers in مسائل content (runs after sanitize; trusted).
function rehypeMasailQA() {
  return (tree: any) => {
    const visit = (node: any) => {
      if (node.type === "element" && node.tagName === "strong") {
        const text = collectText(node).trim();
        if (text.startsWith("السؤال")) node.properties.className = [...(node.properties.className ?? []), "masail-q"];
        else if (text.startsWith("الجواب")) node.properties.className = [...(node.properties.className ?? []), "masail-a"];
      }
      if (node.children) node.children.forEach(visit);
    };
    visit(tree);
  };
}

const SENTENCE_BREAK_RE = /([^0-9\u0660-\u0669\s٫ـ\.]{2,})\.(\s+)/g;

function splitSentenceBreaks(value: string): any[] {
  const out: any[] = [];
  let last = 0;
  for (const m of value.matchAll(SENTENCE_BREAK_RE)) {
    const i = m.index!;
    const wordWithDot = m[1] + ".";
    const preText = value.slice(last, i) + wordWithDot;
    if (preText) {
      out.push({ type: "text", value: preText });
    }
    out.push({
      type: "element",
      tagName: "br",
      properties: { className: ["sentence-br"] },
      children: [],
    });
    last = i + m[0].length;
  }
  if (last < value.length) {
    const postText = value.slice(last);
    if (postText) {
      out.push({ type: "text", value: postText });
    }
  }
  return out.length ? out : [{ type: "text", value }];
}

function rehypeSentenceBreaks() {
  const skipTags = new Set(["code", "pre", "a", "h1", "h2", "h3", "h4", "h5", "h6", "title", "sup"]);
  return (tree: any) => {
    const walk = (node: any, parentTag: string | null) => {
      if (!node.children) return;

      // Walk children elements first
      for (const child of node.children) {
        if (child.type === "element") {
          walk(child, child.tagName);
        }
      }

      if (skipTags.has(parentTag ?? "")) return;

      const next: any[] = [];
      const len = node.children.length;
      for (let i = 0; i < len; i++) {
        const child = node.children[i];

        // Case 2: Sibling text node ending with '.' (and word of length >=2), followed by <sup>, followed by text node starting with space.
        if (
          child.type === "text" &&
          /([^0-9\u0660-\u0669\s٫ـ\.]{2,})\.$/.test(child.value ?? "") &&
          node.children[i + 1]?.type === "element" &&
          node.children[i + 1].tagName === "sup" &&
          node.children[i + 2]?.type === "text" &&
          /^\s+/.test(node.children[i + 2].value ?? "")
        ) {
          next.push(child);
          next.push(node.children[i + 1]);
          next.push({
            type: "element",
            tagName: "br",
            properties: { className: ["sentence-br"] },
            children: [],
          });
          node.children[i + 2].value = node.children[i + 2].value.replace(/^\s+/, "");
          i++;
          continue;
        }

        // Case 3: Sibling text node ending with word of length >= 2, followed by <sup>, followed by text node starting with '.' and space.
        if (
          child.type === "text" &&
          /([^0-9\u0660-\u0669\s٫ـ\.]{2,})$/.test(child.value ?? "") &&
          node.children[i + 1]?.type === "element" &&
          node.children[i + 1].tagName === "sup" &&
          node.children[i + 2]?.type === "text" &&
          /^\.(\s+)/.test(node.children[i + 2].value ?? "")
        ) {
          next.push(child);
          next.push(node.children[i + 1]);
          next.push({ type: "text", value: "." });
          next.push({
            type: "element",
            tagName: "br",
            properties: { className: ["sentence-br"] },
            children: [],
          });
          node.children[i + 2].value = node.children[i + 2].value.replace(/^\.(\s+)/, "");
          i++;
          continue;
        }

        if (child.type === "text") {
          next.push(...splitSentenceBreaks(child.value ?? ""));
        } else {
          next.push(child);
        }
      }
      node.children = next;
    };
    walk(tree, null);
  };
}

const processor = unified()
  .use(remarkParse)
  .use(remarkGfm)
  .use(remarkRehype, { allowDangerousHtml: true })
  .use(rehypeRaw)
  .use(rehypeSanitize, sanitizeSchema)
  .use(rehypeSentenceBreaks)
  .use(rehypeWikiLinks)
  .use(rehypeArabicTokens)
  .use(rehypeHeadingIds)
  .use(rehypeStringify);

const masailProcessor = unified()
  .use(remarkParse)
  .use(remarkGfm)
  .use(remarkRehype, { allowDangerousHtml: true })
  .use(rehypeRaw)
  .use(rehypeSanitize, sanitizeSchema)
  .use(rehypeSentenceBreaks)
  .use(rehypeWikiLinks)
  .use(rehypeMasailQA)
  .use(rehypeArabicTokens)
  .use(rehypeHeadingIds)
  .use(rehypeStringify);

export function markdownToSafeHtml(markdown: string): string {
  return String(processor.processSync(markdown));
}

export function markdownMasailToHtml(markdown: string): string {
  return String(masailProcessor.processSync(markdown));
}
