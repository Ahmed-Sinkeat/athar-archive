import { unified } from "unified";
import remarkGfm from "remark-gfm";
import remarkParse from "remark-parse";
import { slugifyArabic, uniqueSlug } from "../chapters.js";
import {
  APP_CONTENT_SCHEMA,
  type DraftBlock,
  type FootnoteRecord,
  type InlineSpan,
  type ParsedDocument,
  type ReadableCollection,
} from "./contract.js";

interface MarkdownPosition {
  start?: { line?: number; column?: number };
}

interface MarkdownNode {
  type: string;
  value?: string;
  depth?: number;
  url?: string;
  identifier?: string;
  ordered?: boolean | null;
  start?: number | null;
  children?: MarkdownNode[];
  position?: MarkdownPosition;
}

interface InlineResult {
  text: string;
  spans: InlineSpan[];
  footnotes: string[];
}

export interface ParseReadableInput {
  coll: ReadableCollection;
  id: string;
  body: string;
  sourcePath: string;
}

const PAGE_TAG_RE = /^\s*<hr\b[^>]*\bclass=(?:"[^"]*\bpage-sep\b[^"]*"|'[^']*\bpage-sep\b[^']*')[^>]*\/?\s*>\s*$/i;
const COMMENT_RE = /^\s*<!--[\s\S]*?-->\s*$/;
const ATTR_RE = /([\w:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')/g;
const EXPLICIT_ANCHOR_RE = /\s*\{#([A-Za-z0-9_-]+)\}\s*$/;
const VERSE_NUMBER_RE = /^[0-9٠-٩۰-۹]+\s*[-–—.)ـ]\s*/;
const HEMISTICH_RE = /(?:\s+(?:---|\.\.\.|‏…|…)\s*|\s*(?:---|\.\.\.|‏…|…)\s+)/;
const ENTITY_REF_RE = /\[\[([^\]\n]+)\]\]/g;

function compareText(a: string, b: string): number {
  return a < b ? -1 : a > b ? 1 : 0;
}

function fail(sourcePath: string, node: MarkdownNode, detail?: string): never {
  const line = node.position?.start?.line;
  const at = line ? `:${line}` : "";
  throw new Error(`${sourcePath}${at}: unsupported Markdown node ${node.type}${detail ? ` (${detail})` : ""}`);
}

function attributes(html: string): Map<string, string> {
  const result = new Map<string, string>();
  for (const match of html.matchAll(ATTR_RE)) result.set(match[1]!.toLowerCase(), match[2] ?? match[3] ?? "");
  return result;
}

function addTextWithEntityRefs(value: string, output: { text: string; spans: InlineSpan[] }) {
  let cursor = 0;
  for (const match of value.matchAll(ENTITY_REF_RE)) {
    const index = match.index ?? 0;
    output.text += value.slice(cursor, index);
    const raw = match[1]!.trim();
    const separator = raw.indexOf("|");
    const target = (separator === -1 ? raw : raw.slice(0, separator)).trim();
    const label = (separator === -1 ? raw : raw.slice(separator + 1)).trim();
    if (!target || !label) throw new Error(`invalid entity reference [[${raw}]]`);
    const start = output.text.length;
    output.text += label;
    output.spans.push({ k: "entityRef", s: start, e: output.text.length, target });
    cursor = index + match[0].length;
  }
  output.text += value.slice(cursor);
}

function renderInline(
  nodes: MarkdownNode[],
  definitions: Map<string, string>,
  sourcePath: string,
  printedPage?: number,
): InlineResult {
  const output = { text: "", spans: [] as InlineSpan[] };
  const footnotes = new Set<string>();
  const openSup: { start: number; target?: string; footnoteId?: string; node: MarkdownNode }[] = [];

  const walk = (node: MarkdownNode) => {
    switch (node.type) {
      case "text":
        addTextWithEntityRefs(node.value ?? "", output);
        return;
      case "break":
        output.text += "\n";
        return;
      case "strong":
      case "emphasis": {
        const start = output.text.length;
        for (const child of node.children ?? []) walk(child);
        if (output.text.length > start) output.spans.push({ k: node.type, s: start, e: output.text.length });
        return;
      }
      case "link":
      case "linkReference": {
        const target = node.type === "link" ? node.url : definitions.get((node.identifier ?? "").toLowerCase());
        if (!target) fail(sourcePath, node, "unresolved link reference");
        const start = output.text.length;
        for (const child of node.children ?? []) walk(child);
        if (output.text.length > start) output.spans.push({ k: "link", s: start, e: output.text.length, target });
        return;
      }
      case "footnoteReference": {
        const id = node.identifier ?? "";
        if (!id) fail(sourcePath, node, "missing footnote identifier");
        footnotes.add(id);
        return;
      }
      case "html": {
        const html = node.value ?? "";
        if (COMMENT_RE.test(html)) return;
        const complete = html.match(/^\s*<sup\b([^>]*)>([\s\S]*?)<\/sup>\s*$/i);
        if (complete) {
          const attrs = attributes(`<sup ${complete[1]}>`);
          const start = output.text.length;
          output.text += complete[2]!.replace(/<[^>]+>/g, "");
          const target = attrs.get("data-fn");
          if (output.text.length > start) output.spans.push({ k: "sup", s: start, e: output.text.length, ...(target ? { target } : {}) });
          if (target) footnotes.add(`${attrs.get("data-sep-page") ?? printedPage ?? ""}:${target}`);
          return;
        }
        if (/^\s*<sup\b[^>]*>\s*$/i.test(html)) {
          const attrs = attributes(html);
          const target = attrs.get("data-fn");
          const notePage = attrs.get("data-sep-page") ?? printedPage;
          openSup.push({
            start: output.text.length,
            target,
            ...(target ? { footnoteId: `${notePage ?? ""}:${target}` } : {}),
            node,
          });
          return;
        }
        if (/^\s*<\/sup>\s*$/i.test(html)) {
          const opened = openSup.pop();
          if (!opened) fail(sourcePath, node, "closing sup without opening sup");
          if (output.text.length > opened.start) {
            output.spans.push({
              k: "sup",
              s: opened.start,
              e: output.text.length,
              ...(opened.target ? { target: opened.target } : {}),
            });
          }
          if (opened.footnoteId) footnotes.add(opened.footnoteId);
          return;
        }
        fail(sourcePath, node, html.slice(0, 60));
      }
      default:
        fail(sourcePath, node);
    }
  };

  for (const node of nodes) walk(node);
  if (openSup.length > 0) fail(sourcePath, openSup.at(-1)!.node, "unclosed sup");
  output.spans.sort((a, b) => a.s - b.s || b.e - a.e || compareText(a.k, b.k));
  return { text: output.text, spans: output.spans, footnotes: [...footnotes] };
}

function pageMarker(html: string) {
  if (!PAGE_TAG_RE.test(html)) return undefined;
  const attrs = attributes(html);
  const page = Number.parseInt(attrs.get("data-page") ?? "", 10);
  if (!Number.isSafeInteger(page) || page < 1) throw new Error(`invalid page marker: ${html}`);
  const rawVol = attrs.get("data-vol") ?? attrs.get("data-juz");
  const parsedVol = rawVol == null ? undefined : Number.parseInt(rawVol, 10);
  const vol = parsedVol != null && Number.isSafeInteger(parsedVol) && parsedVol > 0 ? parsedVol : undefined;
  const notesRaw = attrs.get("data-notes");
  let notes: string[] = [];
  if (notesRaw) {
    const decoded = notesRaw.replaceAll("&quot;", '"').replaceAll("&#39;", "'").replaceAll("&amp;", "&");
    const parsed: unknown = JSON.parse(decoded);
    if (!Array.isArray(parsed) || parsed.some((note) => typeof note !== "string")) {
      throw new Error(`invalid data-notes on page ${page}`);
    }
    notes = parsed;
  }
  return { page, vol, notes };
}

function trimAnchor(inline: InlineResult) {
  const match = inline.text.match(EXPLICIT_ANCHOR_RE);
  if (!match) return { inline, anchor: undefined };
  const text = inline.text.slice(0, match.index).trimEnd();
  return {
    anchor: match[1],
    inline: {
      ...inline,
      text,
      spans: inline.spans.filter((span) => span.s < text.length).map((span) => ({ ...span, e: Math.min(span.e, text.length) })),
    },
  };
}

function inlineFields(inline: InlineResult) {
  return {
    x: inline.text,
    ...(inline.spans.length ? { sp: inline.spans } : {}),
    ...(inline.footnotes.length ? { f: inline.footnotes } : {}),
  };
}

export function parseReadableDocument(input: ParseReadableInput): ParsedDocument {
  const root = unified().use(remarkParse).use(remarkGfm).parse(input.body) as unknown as MarkdownNode;
  const definitions = new Map<string, string>();
  for (const node of root.children ?? []) {
    if (node.type === "definition" && node.identifier && node.url) definitions.set(node.identifier.toLowerCase(), node.url);
  }

  const blocks: DraftBlock[] = [];
  const chapters: ParsedDocument["chapters"] = [];
  const footnotes: FootnoteRecord[] = [];
  const seenAnchors = new Set<string>();
  let currentAnchor: string | undefined;
  let currentPage: number | undefined;
  let currentVol: number | undefined;
  let verseNumber = 0;

  const ensureChapter = () => {
    if (currentAnchor) return currentAnchor;
    const base = input.coll === "poem" ? "matla" : "main";
    currentAnchor = uniqueSlug(base, seenAnchors);
    chapters.push({ a: currentAnchor, title: "", block: blocks.length });
    return currentAnchor;
  };

  const add = (block: Omit<DraftBlock, "a" | "p" | "vol"> & { a?: string; p?: number; vol?: number }) => {
    blocks.push({
      ...block,
      a: block.a ?? ensureChapter(),
      ...(block.p ?? currentPage ? { p: block.p ?? currentPage } : {}),
      ...(block.vol ?? currentVol ? { vol: block.vol ?? currentVol } : {}),
    });
  };

  const addParagraph = (node: MarkdownNode, type: "p" | "quote" | "li" = "p", list?: Partial<DraftBlock>) => {
    const inline = renderInline(node.children ?? [], definitions, input.sourcePath, currentPage);
    if (input.coll === "poem" && type === "p") {
      const numbered = VERSE_NUMBER_RE.test(inline.text);
      const withoutNumber = inline.text.replace(VERSE_NUMBER_RE, "");
      const separator = withoutNumber.match(HEMISTICH_RE);
      if (numbered || separator) {
        if (inline.spans.length || inline.footnotes.length) {
          fail(input.sourcePath, node, "inline semantics inside a verse are not representable in schema 2");
        }
        verseNumber++;
        if (separator?.index != null) {
          const sadr = withoutNumber.slice(0, separator.index).trim();
          const ajuz = withoutNumber.slice(separator.index + separator[0].length).trim();
          add({ t: "verse", n: verseNumber, s: sadr, j: ajuz });
        } else {
          add({ t: "verse", n: verseNumber, x: withoutNumber.trim() });
        }
        return;
      }
    }
    add({ t: type, ...inlineFields(inline), ...list });
  };

  const processList = (node: MarkdownNode, depth = 0) => {
    const ordered = node.ordered === true;
    const start = node.start ?? 1;
    for (const [itemIndex, item] of (node.children ?? []).entries()) {
      if (item.type !== "listItem") fail(input.sourcePath, item);
      let paragraphIndex = 0;
      for (const child of item.children ?? []) {
        if (child.type === "paragraph") {
          addParagraph(child, "li", {
            ordered,
            start: start + itemIndex,
            depth,
            ...(paragraphIndex++ > 0 ? { continuation: true } : {}),
          });
        } else if (child.type === "list") {
          processList(child, depth + 1);
        } else {
          fail(input.sourcePath, child);
        }
      }
    }
  };

  const processFootnote = (node: MarkdownNode) => {
    const id = node.identifier ?? "";
    if (!id) fail(input.sourcePath, node, "missing footnote identifier");
    const parts: InlineResult[] = [];
    for (const child of node.children ?? []) {
      if (child.type !== "paragraph") fail(input.sourcePath, child, "footnote bodies support paragraphs only in schema 2");
      parts.push(renderInline(child.children ?? [], definitions, input.sourcePath, currentPage));
    }
    const text = parts.map((part) => part.text).join("\n\n");
    const spans: InlineSpan[] = [];
    let offset = 0;
    for (const part of parts) {
      spans.push(...part.spans.map((span) => ({ ...span, s: span.s + offset, e: span.e + offset })));
      offset += part.text.length + 2;
    }
    footnotes.push({ id, x: text, ...(spans.length ? { sp: spans } : {}) });
  };

  const process = (node: MarkdownNode, quote = false) => {
    switch (node.type) {
      case "definition":
        return;
      case "footnoteDefinition":
        processFootnote(node);
        return;
      case "heading": {
        const depth = node.depth;
        if (!depth || depth < 1 || depth > 6) fail(input.sourcePath, node, "invalid heading depth");
        const rendered = trimAnchor(renderInline(node.children ?? [], definitions, input.sourcePath, currentPage));
        if (!rendered.inline.text) fail(input.sourcePath, node, "empty heading");
        const headingAnchor = uniqueSlug(
          rendered.anchor ?? (slugifyArabic(rendered.inline.text) || `heading-${blocks.length + 1}`),
          seenAnchors,
        );
        if (depth === 2) {
          currentAnchor = headingAnchor;
          chapters.push({ a: currentAnchor, title: rendered.inline.text, block: blocks.length });
        }
        add({
          t: `h${depth as 1 | 2 | 3 | 4 | 5 | 6}`,
          a: depth === 2 ? headingAnchor : ensureChapter(),
          ...(depth === 2 ? {} : { ha: headingAnchor }),
          ...inlineFields(rendered.inline),
        });
        return;
      }
      case "paragraph":
        addParagraph(node, quote ? "quote" : "p");
        return;
      case "list":
        if (quote) fail(input.sourcePath, node, "lists inside blockquotes are not representable in schema 2");
        processList(node);
        return;
      case "blockquote":
        for (const child of node.children ?? []) process(child, true);
        return;
      case "thematicBreak":
        add({ t: "break" });
        return;
      case "html": {
        const html = node.value ?? "";
        if (COMMENT_RE.test(html)) return;
        const marker = pageMarker(html);
        if (!marker) fail(input.sourcePath, node, html.slice(0, 60));
        currentPage = marker.page;
        currentVol = marker.vol ?? currentVol;
        add({ t: "page", p: currentPage, ...(currentVol ? { vol: currentVol } : {}) });
        marker.notes.forEach((text, index) => footnotes.push({ id: `${marker.page}:${index + 1}`, x: text }));
        return;
      }
      default:
        fail(input.sourcePath, node);
    }
  };

  for (const node of root.children ?? []) process(node);
  if (blocks.length === 0) throw new Error(`${input.sourcePath}: readable document has no semantic blocks`);

  const pages = blocks.map((block) => block.p).filter((page): page is number => page != null);
  const vols = new Set(blocks.map((block) => block.vol).filter((vol): vol is number => vol != null));
  return {
    schema: APP_CONTENT_SCHEMA,
    coll: input.coll,
    id: input.id,
    blocks,
    chapters,
    footnotes: footnotes.sort((a, b) => compareText(a.id, b.id)),
    ...(pages.length ? { pages: { from: Math.min(...pages), to: Math.max(...pages), vols: Math.max(1, vols.size) } } : {}),
  };
}
