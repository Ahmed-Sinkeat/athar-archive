import { unified } from "unified";
import remarkGfm from "remark-gfm";
import remarkParse from "remark-parse";
import rehypeRaw from "rehype-raw";
import remarkRehype from "remark-rehype";
import { slugifyArabic, uniqueSlug } from "../chapters.js";
import { normalizePageMarkers } from "../read-body.js";
import {
  APP_CONTENT_SCHEMA,
  type DraftBlock,
  type FootnoteRecord,
  type InlineSpan,
  type ParsedDocument,
  type ReadableCollection,
} from "./contract.js";

interface MarkdownPosition {
  start?: { line?: number; column?: number; offset?: number };
  end?: { offset?: number };
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
  references?: { id: string; offset: number }[];
}

/**
 * Every footnote format becomes one model here, so the app draws one thing:
 * an inline superscript whose target is the note id, and a `notes` block that
 * closes each printed page with that page's notes.
 *
 * A page marker ENDS its page (its data-notes describe the text above it; the
 * Shamela «الجزء: ١ - الصفحة: ٥» footer sits under page 5's text), so a ref is
 * only resolved when the page it sits on closes. Until then it carries a
 * placeholder: `?#N` for a printed per-page number (EPUB sup, caret "(^N)"),
 * `?^id` for a Markdown [^id] ref.
 */
interface NoteHooks {
  /** Per-page display number for a Markdown [^id] ref. */
  markdownNumber(id: string): string;
}

const CARET_REF_RE = /\((?:\^([٠-٩0-9]+)|\*)\)/g;
const CARET_DEF_RE = /^\((?:\^([٠-٩0-9]+)|\*)\)\s+(.*)$/;
const CARET_CONTINUATION_RE = /^=\s+(\S.*)$/s;

function westernDigits(value: string): string {
  return value.replace(/[٠-٩]/g, (digit) => String(digit.charCodeAt(0) - 0x0660));
}

function arabicDigits(value: number): string {
  return String(value).replace(/\d/g, (digit) => String.fromCharCode(0x0660 + Number(digit)));
}

function htmlBlocks(value: string, sourcePath: string): MarkdownNode[] {
  interface HtmlNode { type: string; tagName?: string; value?: string; properties?: Record<string, unknown>; children?: HtmlNode[] }
  const processor = unified().use(remarkParse).use(remarkRehype, { allowDangerousHtml: true }).use(rehypeRaw);
  const tree = processor.runSync(processor.parse(value)) as unknown as HtmlNode;
  const convert = (node: HtmlNode): MarkdownNode[] => {
    if (node.type === "comment") return [];
    if (node.type === "text") return [{ type: "text", value: node.value }];
    const children = (node.children ?? []).flatMap(convert);
    const tag = node.tagName;
    if (!tag || tag === "div" || tag === "span") return children;
    if (tag === "br") return [{ type: "break" }];
    if (tag === "hr") return [{ type: "thematicBreak" }];
    if (/^s\d+$/.test(tag)) return children;
    if (tag === "a") return [{ type: "link", url: String(node.properties?.href ?? ""), children }];
    if (tag === "strong" || tag === "b") return [{ type: "strong", children }];
    if (tag === "em" || tag === "i") return [{ type: "emphasis", children }];
    if (tag === "p") return [{ type: "paragraph", children }];
    if (/^h[1-6]$/.test(tag)) return [{ type: "heading", depth: Number(tag[1]), children }];
    throw new Error(`${sourcePath}: unsupported HTML element ${tag}`);
  };
  const nodes = convert(tree);
  const blocks: MarkdownNode[] = [];
  let inline: MarkdownNode[] = [];
  const flush = () => { if (inline.length) blocks.push({ type: "paragraph", children: inline }); inline = []; };
  for (const node of nodes) {
    if (node.type === "paragraph" || node.type === "heading" || node.type === "thematicBreak") { flush(); blocks.push(node); }
    else inline.push(node);
  }
  flush();
  return blocks;
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
  hooks?: NoteHooks,
): InlineResult {
  const output = { text: "", spans: [] as InlineSpan[] };
  const footnotes = new Set<string>();
  const references: { id: string; offset: number }[] = [];
  const note = (id: string, offset = output.text.length) => { footnotes.add(id); references.push({ id, offset }); };
  const openSup: { start: number; key?: string; node: MarkdownNode }[] = [];
  const supKey = (fn: string | undefined) => fn ? `?#${westernDigits(fn)}` : undefined;

  const walk = (node: MarkdownNode) => {
    switch (node.type) {
      case "text": {
        // Caret imports keep their marker as literal "(^N)" text.
        const value = node.value ?? "";
        let cursor = 0;
        for (const match of hooks ? value.matchAll(CARET_REF_RE) : []) {
          addTextWithEntityRefs(value.slice(cursor, match.index), output);
          const key = `?#${match[1] ? westernDigits(match[1]) : "*"}`;
          const start = output.text.length;
          output.text += match[1] ?? "*";
          output.spans.push({ k: "sup", s: start, e: output.text.length, target: key });
          note(key, start);
          cursor = match.index! + match[0].length;
        }
        addTextWithEntityRefs(value.slice(cursor), output);
        return;
      }
      case "inlineCode":
        // shortcut: imported prose keeps its literal code text; add code typography for technical texts.
        output.text += node.value ?? "";
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
        const key = `?^${id}`;
        const start = output.text.length;
        output.text += hooks?.markdownNumber(id) ?? "";
        if (output.text.length > start) output.spans.push({ k: "sup", s: start, e: output.text.length, target: key });
        note(key, start);
        return;
      }
      case "html": {
        const html = node.value ?? "";
        if (COMMENT_RE.test(html)) return;
        // Imported source-position markers contain no readable text.
        if (/^\s*<m\s+s\s*=\s*\d+\s*>\s*$/i.test(html)) return;
        const complete = html.match(/^\s*<sup\b([^>]*)>([\s\S]*?)<\/sup>\s*$/i);
        if (complete) {
          const attrs = attributes(`<sup ${complete[1]}>`);
          const start = output.text.length;
          output.text += complete[2]!.replace(/<[^>]+>/g, "");
          // Every imported data-sep-page names the very next marker, so the ref resolves when that page closes.
          const key = supKey(attrs.get("data-fn"));
          if (output.text.length > start) output.spans.push({ k: "sup", s: start, e: output.text.length, ...(key ? { target: key } : {}) });
          if (key) note(key, start);
          return;
        }
        if (/^\s*<sup\b[^>]*>\s*$/i.test(html)) {
          openSup.push({ start: output.text.length, key: supKey(attributes(html).get("data-fn")), node });
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
              ...(opened.key ? { target: opened.key } : {}),
            });
          }
          if (opened.key) note(opened.key, opened.start);
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
  return { text: output.text, spans: output.spans, footnotes: [...footnotes], references };
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

const VERSE_LINE_RE = /^\s*[^.…\n]{6,70}?\s+(?:\.\.\.|…)\s+[^.…\n]{6,70}$/;
const HALF_SEPARATOR_RE = /\s+(?:\.\.\.|…)\s+/;

/**
 * Where a prose line splits into two hemistichs, or undefined when it is not verse.
 * ponytail: halves within 0.6 of each other's length; across the corpus that keeps ~95k
 * verses and leaves ~1.2k prose ellipses and quoted half-lines as prose. Tighten if prose slips in.
 */
function verseHalves(line: string): { start: number; end: number } | undefined {
  if (!VERSE_LINE_RE.test(line) || /[﴾﴿]/.test(line)) return undefined;
  const separator = line.match(HALF_SEPARATOR_RE)!;
  const start = separator.index!;
  const end = start + separator[0].length;
  const size = (half: string) => half.trim().replace(VERSE_NUMBER_RE, "").replace(/[\sً-ٰٟ]/g, "").length;
  const sadr = size(line.slice(0, start));
  const ajuz = size(line.slice(end));
  return Math.min(sadr, ajuz) >= 0.6 * Math.max(sadr, ajuz) ? { start, end } : undefined;
}

function sliceInline(inline: InlineResult, start: number, end: number): InlineResult {
  return {
    text: inline.text.slice(start, end),
    spans: inline.spans.map(span => ({ ...span, s: Math.max(0, span.s - start), e: Math.min(end - start, span.e - start) }))
      .filter(span => span.e > span.s),
    footnotes: inline.footnotes.filter(id => inline.references?.some(ref => ref.id === id && ref.offset >= start && ref.offset <= end)),
  };
}

/** One verse line as "sadr\najuz": the app draws the two lines centred, like a poem's verses. */
function joinHalves(row: InlineResult, halves: { start: number; end: number }): InlineResult {
  const lead = row.text.length - row.text.trimStart().length;
  const tail = row.text.trimEnd().length;
  const text = `${row.text.slice(lead, halves.start)}\n${row.text.slice(halves.end, tail)}`;
  const at = (offset: number) => Math.min(text.length, Math.max(0,
    (offset < halves.start ? offset : offset >= halves.end ? offset - (halves.end - halves.start) + 1 : halves.start) - lead));
  return {
    text,
    spans: row.spans.map(span => ({ ...span, s: at(span.s), e: at(span.e) })).filter(span => span.e > span.s),
    footnotes: row.footnotes,
  };
}

function plainText(node: MarkdownNode): string {
  if (node.type === "text") return node.value ?? "";
  if (node.type === "break") return "\n";
  return (node.children ?? []).map(plainText).join("");
}

/** The paragraph's first `length` characters of plainText; cut at a line break, so only top-level text splits. */
function truncated(node: MarkdownNode, length: number): MarkdownNode {
  const children: MarkdownNode[] = [];
  let used = 0;
  for (const child of node.children ?? []) {
    const size = plainText(child).length;
    if (used + size <= length) children.push(child);
    else if (child.type === "text") children.push({ ...child, value: child.value!.slice(0, length - used) });
    used += size;
    if (used >= length) break;
  }
  return { ...node, children };
}

export function parseReadableDocument(input: ParseReadableInput): ParsedDocument {
  const parser = unified().use(remarkParse).use(remarkGfm);
  // Same normalization the site applies: plain «الجزء: N - الصفحة: M» lines become real page markers.
  const body = normalizePageMarkers(input.body);
  // Large books are parsed one section at a time; a whole-book Markdown AST can exceed RAM.
  const chunks = body.length > 1_000_000 ? body.split(/\n(?=#{1,6} |[ \t]*<hr\b)/) : [body];
  const smallTree = chunks.length === 1 ? parser.parse(body) as unknown as MarkdownNode : undefined;
  const definitions = new Map<string, string>();
  const definitionSources = new Map<string, string>();
  for (const chunk of chunks) {
    if (!smallTree && !/^ {0,3}\[[^\]\n]+\]:/m.test(chunk)) continue;
    const tree = smallTree ?? parser.parse(chunk) as unknown as MarkdownNode;
    for (const node of tree.children ?? []) {
      if (node.type === "definition" && node.identifier && node.url) definitions.set(node.identifier.toLowerCase(), node.url);
      if ((node.type === "definition" || node.type === "footnoteDefinition") && node.position?.start?.offset != null && node.position.end?.offset != null) {
        definitionSources.set(`${node.type === "footnoteDefinition" ? "^" : ""}${node.identifier?.toLowerCase()}`,
          chunk.slice(node.position.start.offset, node.position.end.offset));
      }
    }
  }
  const referencesFor = (chunk: string) => {
    const needed = new Map<string, string>();
    const pending = [chunk];
    while (pending.length) {
      for (const match of pending.pop()!.matchAll(/\[([^\]\n]+)\]/g)) {
        const key = match[1]!.trim().replace(/\s+/g, " ").toLowerCase();
        const source = definitionSources.get(key);
        if (source && !needed.has(key)) { needed.set(key, source); pending.push(source); }
      }
    }
    return "\n\n" + [...needed.values()].join("\n\n");
  };

  const blocks: DraftBlock[] = [];
  const chapters: ParsedDocument["chapters"] = [];
  const footnotes: FootnoteRecord[] = [];
  const markdownNotes = new Map<string, FootnoteRecord>();
  const pageOccurrences = new Map<string, number>();
  const noteNumbers = new Map<string, string>();
  const placedNotes = new Set<string>();
  // The page still being read: its Markdown refs, its caret note lines, and where its blocks begin.
  let markdownOnPage = new Map<string, string>();
  let caretOnPage: { key: string; n: string; x: string }[] = [];
  let lastCaretNote: FootnoteRecord | undefined;
  let pageStart = 0;
  const hooks: NoteHooks = {
    markdownNumber(id) {
      if (!markdownOnPage.has(id)) markdownOnPage.set(id, arabicDigits(markdownOnPage.size + 1));
      return markdownOnPage.get(id)!;
    },
  };
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
    const inline = renderInline(node.children ?? [], definitions, input.sourcePath, hooks);
    if (input.coll === "poem" && (type === "p" || (type === "li" && list?.ordered))) {
      const lines = inline.text.split("\n");
      // A long unseparated, unnumbered line is prose (an introduction inside a poem file);
      // its paragraph must not turn every line into a verse.
      const prose = lines.some(line => line.length > 160 && !VERSE_NUMBER_RE.test(line) && !HEMISTICH_RE.test(line));
      let offset = 0;
      for (const line of lines) {
        const start = offset;
        offset += line.length + 1;
        const spans = inline.spans.map(span => ({ ...span, s: Math.max(0, span.s - start), e: Math.min(line.length, span.e - start) }))
          .filter(span => span.e > span.s);
        const row = { text: line, spans, footnotes: inline.footnotes.filter(id =>
          inline.references?.some(ref => ref.id === id && ref.offset >= start && ref.offset <= start + line.length)) };
        const numbered = VERSE_NUMBER_RE.test(row.text);
        const withoutNumber = row.text.replace(VERSE_NUMBER_RE, "");
        const separator = withoutNumber.match(HEMISTICH_RE);
        if (numbered || separator || (lines.length > 1 && !prose) || type === "li") {
          verseNumber++;
          if (row.spans.length || row.footnotes.length) {
            const prefix = row.text.length - withoutNumber.length + withoutNumber.length - withoutNumber.trimStart().length;
            const text = withoutNumber.trim();
            add({ t: "verse", n: verseNumber, x: text,
              ...(row.spans.length ? { sp: row.spans.map((span) => ({ ...span, s: Math.max(0, span.s - prefix), e: Math.min(text.length, span.e - prefix) })).filter((span) => span.e > span.s) } : {}),
              ...(row.footnotes.length ? { f: row.footnotes } : {}),
            });
            continue;
          }
          if (separator?.index != null) {
            const sadr = withoutNumber.slice(0, separator.index).trim();
            const ajuz = withoutNumber.slice(separator.index + separator[0].length).trim();
            add({ t: "verse", n: verseNumber, s: sadr, j: ajuz });
          } else {
            add({ t: "verse", n: verseNumber, x: withoutNumber.trim() });
          }
          continue;
        }
        add({ t: type, ...inlineFields(row), ...list });
      }
      return;
    }
    // Books quote poetry as «صدر ... عجز» lines inside prose; each such line becomes a verse.
    const lines = inline.text.split("\n");
    if (type !== "li" && lines.some(line => verseHalves(line))) {
      let start = 0;
      let prose = -1;
      const flush = (end: number) => {
        if (prose < 0) return;
        const row = sliceInline(inline, prose, end);
        prose = -1;
        if (row.text.trim()) add({ t: type, ...inlineFields(row), ...list });
      };
      for (const line of lines) {
        const halves = verseHalves(line);
        if (!halves) {
          if (prose < 0) prose = start;
        } else {
          flush(start - 1);
          add({ t: "verse", ...inlineFields(joinHalves(sliceInline(inline, start, start + line.length), halves)) });
        }
        start += line.length + 1;
      }
      flush(inline.text.length);
      return;
    }
    add({ t: type, ...inlineFields(inline), ...list });
  };

  const processList =(node: MarkdownNode, depth = 0, quote = false) => {
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
            ...(quote ? { quoted: true } : {}),
            ...(paragraphIndex++ > 0 ? { continuation: true } : {}),
          });
        } else if (child.type === "list") {
          processList(child, depth + 1, quote);
        } else if (child.type === "html" || child.type === "blockquote" || child.type === "heading" || child.type === "footnoteDefinition" || child.type === "definition") {
          process(child, quote);
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
    const collect = (child: MarkdownNode, marker = "") => {
      if (child.type === "paragraph") {
        parts.push(renderInline([{ type: "text", value: marker }, ...(child.children ?? [])], definitions, input.sourcePath, hooks));
      } else if (child.type === "list") {
        for (const [index, item] of (child.children ?? []).entries()) {
          for (const [partIndex, part] of (item.children ?? []).entries()) {
            collect(part, partIndex === 0 ? (child.ordered ? `${(child.start ?? 1) + index}. ` : "• ") : "");
          }
        }
      } else fail(input.sourcePath, child, "unsupported footnote body");
    };
    for (const child of node.children ?? []) collect(child);
    const text = parts.map((part) => part.text).join("\n\n");
    const spans: InlineSpan[] = [];
    let offset = 0;
    for (const part of parts) {
      spans.push(...part.spans.map((span) => ({ ...span, s: span.s + offset, e: span.e + offset })));
      offset += part.text.length + 2;
    }
    const previous = markdownNotes.get(id);
    if (previous) {
      // Reused source labels are ambiguous; retain both bodies under their authored label.
      if (previous.x !== text || JSON.stringify(previous.sp ?? []) !== JSON.stringify(spans)) {
        const offset = previous.x.length + 2;
        previous.x += `\n\n${text}`;
        if (spans.length) previous.sp = [...(previous.sp ?? []), ...spans.map(span => ({ ...span, s: span.s + offset, e: span.e + offset }))];
      }
    } else {
      const note = { id, x: text, ...(spans.length ? { sp: spans } : {}) };
      footnotes.push(note);
      markdownNotes.set(id, note);
    }
  };

  // Closes the page being read: its notes get ids and display numbers, its refs get their
  // targets, and one `notes` block carries the page's notes right before the marker ending it.
  const closePage = (marker?: { notes: string[]; base: string; suffix: string }) => {
    const byKey = new Map<string, string>();
    const ids: string[] = [];
    const place = (key: string, id: string, n: string) => {
      if (!byKey.has(key)) byKey.set(key, id);
      ids.push(id);
      if (!noteNumbers.has(id)) noteNumbers.set(id, n);
    };
    marker?.notes.forEach((x, index) => {
      const id = `${marker.base}${index + 1}${marker.suffix}`;
      footnotes.push({ id, x });
      place(`?#${index + 1}`, id, arabicDigits(index + 1));
    });
    caretOnPage.forEach((caret, index) => {
      const id = `${marker?.base ?? "end:"}c${caret.key}${marker?.suffix ?? ""}`;
      const unique = byKey.has(`?#${caret.key}`) ? `${id}-${index}` : id;
      lastCaretNote = { id: unique, x: caret.x };
      footnotes.push(lastCaretNote);
      place(`?#${caret.key}`, unique, caret.n);
    });
    for (const [id, n] of markdownOnPage) place(`?^${id}`, id, n);
    for (const block of blocks.slice(pageStart)) {
      if (block.f) {
        const resolved = [...new Set(block.f.flatMap((key) => byKey.get(key) ?? []))];
        if (resolved.length) block.f = resolved;
        else delete block.f;
      }
      for (const span of block.sp ?? []) {
        if (!span.target?.startsWith("?")) continue;
        const id = byKey.get(span.target);
        if (id) span.target = id;
        else delete span.target;
      }
    }
    const unique = [...new Set(ids)];
    if (unique.length) add({ t: "notes", f: unique });
    unique.forEach((id) => placedNotes.add(id));
    markdownOnPage = new Map();
    caretOnPage = [];
    pageStart = blocks.length;
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
        const rendered = trimAnchor(renderInline(node.children ?? [], definitions, input.sourcePath, hooks));
        if (!rendered.inline.text) return;
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
      case "paragraph": {
        // Caret imports keep each page's notes as literal "(^N) text" lines.
        // They can also trail a paragraph that opens with other text (e.g. "[٣٦] إسناده صحيح.").
        const lines = plainText(node).split("\n");
        const firstDef = quote ? -1 : lines.findIndex((line) => CARET_DEF_RE.test(line));
        if (firstDef >= 0) {
          if (firstDef > 0) addParagraph(truncated(node, lines.slice(0, firstDef).join("\n").length), "p");
          for (const line of lines.slice(firstDef)) {
            const def = line.match(CARET_DEF_RE);
            if (def) caretOnPage.push({ key: def[1] ? westernDigits(def[1]) : "*", n: def[1] ?? "*", x: def[2]!.trim() });
            else caretOnPage.at(-1)!.x += ` ${line.trim()}`;
          }
          return;
        }
        // "= text" opening a page continues the previous page's last caret note.
        const continuation = lines.join("\n").match(CARET_CONTINUATION_RE);
        if (!quote && continuation && lastCaretNote && caretOnPage.length === 0) {
          lastCaretNote.x += ` ${continuation[1]!.trim()}`;
          return;
        }
        addParagraph(node, quote ? "quote" : "p");
        return;
      }
      case "list":
        processList(node, 0, quote);
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
        if (!marker) {
          for (const block of htmlBlocks(html, input.sourcePath)) process(block, quote);
          return;
        }
        currentVol = marker.vol ?? currentVol;
        const base = `${currentVol != null ? `${currentVol}:` : ""}${marker.page}:`;
        const occurrence = (pageOccurrences.get(base) ?? 0) + 1;
        pageOccurrences.set(base, occurrence);
        closePage({ notes: marker.notes, base, suffix: occurrence === 1 ? "" : `~${occurrence}` });
        currentPage = marker.page;
        add({ t: "page", p: currentPage, ...(currentVol ? { vol: currentVol } : {}) });
        pageStart = blocks.length;
        return;
      }
      default:
        fail(input.sourcePath, node);
    }
  };

  let lineOffset = 0;
  for (const chunk of chunks) {
    const tree = smallTree ?? parser.parse(chunk + referencesFor(chunk)) as unknown as MarkdownNode;
    const shiftLines = (node: MarkdownNode) => {
      if (node.position?.start?.line != null) node.position.start.line += lineOffset;
      for (const child of node.children ?? []) shiftLines(child);
    };
    for (const node of tree.children ?? []) {
      if ((node.position?.start?.offset ?? 0) < chunk.length) { shiftLines(node); process(node); }
    }
    lineOffset += (chunk.match(/\n/g)?.length ?? 0) + 1;
  }
  // Notes defined but never referenced still reach the reader, at the end.
  for (const id of markdownNotes.keys()) if (!placedNotes.has(id)) hooks.markdownNumber(id);
  closePage();
  if (blocks.length === 0) throw new Error(`${input.sourcePath}: readable document has no semantic blocks`);

  // A marker ENDS its page: each block takes the page of the marker after it, and text
  // after the last marker sits on the page that follows it.
  const lastMarker = blocks.findLast((block) => block.t === "page");
  let nextMarker: DraftBlock | undefined;
  for (let index = blocks.length - 1; index >= 0; index--) {
    const block = blocks[index]!;
    if (block.t === "page") { nextMarker = block; continue; }
    delete block.p;
    delete block.vol;
    if (nextMarker) Object.assign(block, { p: nextMarker.p }, nextMarker.vol != null ? { vol: nextMarker.vol } : {});
    else if (lastMarker) Object.assign(block, { p: lastMarker.p! + 1 }, lastMarker.vol != null ? { vol: lastMarker.vol } : {});
  }

  const noteIds = new Set<string>();
  for (const note of footnotes) {
    if (noteIds.has(note.id)) throw new Error(`${input.sourcePath}: duplicate footnote identifier ${note.id}`);
    noteIds.add(note.id);
    const n = noteNumbers.get(note.id);
    if (n) note.n = n;
    // A ref inside a note body has no page of its own to resolve against.
    for (const span of note.sp ?? []) if (span.target?.startsWith("?")) delete span.target;
  }
  for (const block of blocks) {
    if (block.f) {
      block.f = block.f.filter((id) => noteIds.has(id));
      if (block.f.length === 0) delete block.f;
    }
    for (const span of block.sp ?? []) if (span.k === "sup" && span.target && !noteIds.has(span.target)) delete span.target;
  }

  const pages = blocks.map((block) => block.p).filter((page): page is number => page != null);
  const vols = new Set(blocks.map((block) => block.vol).filter((vol): vol is number => vol != null));
  return {
    schema: APP_CONTENT_SCHEMA,
    coll: input.coll,
    id: input.id,
    blocks,
    chapters,
    footnotes: footnotes.sort((a, b) => compareText(a.id, b.id)),
    ...(pages.length ? { pages: { from: pages.reduce((a, b) => Math.min(a, b)), to: pages.reduce((a, b) => Math.max(a, b)), vols: Math.max(1, vols.size) } } : {}),
  };
}
