// Per-page printed-footer footnotes, shared by BOTH book reading routes
// (the on-demand chapter route and the single-page book route). One pass over
// the compiled HTML that makes every footnote work the same way regardless of
// which importer produced the book:
//
//  - GFM markdown footnotes ([^id] refs + [^id]: defs): refs become plain,
//    non-clickable page-numbered <sup class="fn-ref">, defs render in an
//    always-visible footer box at the bottom of their printed page, and
//    remark-gfm's end-of-document endnotes <section> is stripped.
//  - EPUB-import footnotes (<sup data-fn="N" data-sep-page="M"> markers with
//    the page's note texts bundled as a JSON array in the NEXT page-sep's
//    data-notes attribute): the sups become the same plain <sup class="fn-ref">
//    (they were previously styled as click-to-reveal popovers that reader.ts
//    didn't even handle — dead UI), and the data-notes array renders as the
//    page's footer box. Crucially this shows EVERY note on the page, including
//    ones whose inline marker the importer failed to tag (those exist as bare
//    digits in the text); the footer numbering is the array position, which is
//    the original book's own printed numbering, so bare digits still match.
//  - DB حاشية annotations (chapter route only) merge into the same footer,
//    numbered sequentially with the rest of the page's items.
//
// Also normalizes every <hr class="page-sep"> into a <div class="page-sep"
// id="pN"> (so ::before can render the "ص N" chip and #pN deep links work),
// deduped — some imports emit the same page marker more than once.

import { markdownToSafeHtml } from "./sanitize.js";
import { extractFootnotesByPage, extractCaretNotesByPage } from "./chapters.js";

const SEP_SPLIT_RE = /(<hr\s+[^>]*class="page-sep"[^>]*>)/;

// EPUB-import note arrays live on the SOURCE's page-sep markers as
// data-notes='["…","…"]' (single-quote-delimited JSON). Read them from the
// raw source, not the compiled HTML, so attribute entity-escaping can't
// corrupt the JSON.
function extractEpubNotesByPage(content: string): Map<string, string[]> {
  const out = new Map<string, string[]>();
  for (const m of content.matchAll(/<hr[^>]*class="page-sep"[^>]*data-page="(\d+)"[^>]*data-notes='([^']*)'/g)) {
    try {
      const notes = JSON.parse(m[2]);
      if (Array.isArray(notes) && notes.every((n) => typeof n === "string")) out.set(m[1], notes);
    } catch { /* malformed JSON — skip this marker */ }
  }
  return out;
}

// Caret-footnote imports (see extractCaretNotesByPage): the inline marker is
// plain "(^N)" (or, rarely, an unnumbered "(*)") text — like EPUB sups it
// already carries the printed edition's own per-page number, so no lookup
// needed here. Usually wrapped in a <span class="tok-paren"> by
// markdownToSafeHtml's parenthetical tokenizer, but that tokenizer skips
// parens already inside a quote span (or a few other spots), so also catch
// any it left bare. Needed in the main body AND inside footer note bodies
// (one note can reference another).
function convertCaretRefs(html: string): string {
  const toSup = (_: string, digit?: string, star?: string) => `<sup class="fn-ref">${digit ?? star}</sup>`;
  return html
    .replace(/<span class="tok-paren">\((?:\^([٠-٩0-9]+)|(\*))\)<\/span>/g, toSup)
    .replace(/\((?:\^([٠-٩0-9]+)|(\*))\)/g, toSup);
}

function footerHtml(items: Array<{ id: string; num: string; body: string }>): string {
  if (!items.length) return "";
  const lis = items
    .map((it) => `<li id="${it.id}" data-fn-num="${it.num}">${convertCaretRefs(markdownToSafeHtml(it.body))}</li>`)
    .join("");
  return `<aside class="page-footnotes"><ol>${lis}</ol></aside>`;
}

export function wirePageFootnotes(
  html: string,
  content: string,
  dbHashiyasByPage?: Map<number, string[]>,
): string {
  // remark-gfm's own end-of-document endnotes section is replaced by the
  // per-page footers below. (Defs must still be present at compile time —
  // remark-gfm only treats [^id] as a footnote ref when a matching def exists.)
  let out = html.replace(/<section data-footnotes[^>]*>[\s\S]*?<\/section>\n?/, "");

  // GFM defs grouped by printed page, merged after any DB حاشية notes.
  const { itemsByPage: gfmByPage } = extractFootnotesByPage(content);
  const footerItemsByPage = new Map<number, Array<{ id: string; body: string }>>();
  const footnoteAnchorById = new Map<string, string>(); // [^id] → its footer <li> id
  for (const [pNum, bodies] of dbHashiyasByPage ?? []) {
    footerItemsByPage.set(pNum, bodies.map((body, i) => ({ id: `fn-${pNum}-${i}`, body })));
  }
  for (const [pNum, fns] of gfmByPage) {
    const existing = footerItemsByPage.get(pNum) ?? [];
    const items = fns.map((fn, i) => {
      const id = `fn-${pNum}-${existing.length + i}`;
      footnoteAnchorById.set(fn.id, id);
      return { id, body: fn.body };
    });
    footerItemsByPage.set(pNum, [...existing, ...items]);
  }

  // Number every footer item sequentially within its own page (١، ٢، ٣…) —
  // حاشية and تخريج mixed in one simple per-page count, not the compiled
  // footnote's chapter-wide continuous number (which left gaps like "١، ٢، ٧"
  // on pages carrying both kinds).
  const numberByFooterId = new Map<string, string>();
  for (const items of footerItemsByPage.values()) {
    items.forEach((it, i) => numberByFooterId.set(it.id, String(i + 1)));
  }

  // Relabel each GFM ref with its page-relative number and drop the <a>/href
  // entirely — plain text like a printed page's own footnote marker. Matched
  // only on the one load-bearing attribute (data-footnote-ref), not the full
  // tag shape, so remark-gfm's attribute order can't silently break it.
  out = out.replace(
    /<sup><a\s+([^>]*data-footnote-ref[^>]*)>\d+<\/a><\/sup>/g,
    (match, attrs) => {
      const id = attrs.match(/href="#user-content-fn-([a-zA-Z0-9_-]+)"/)?.[1];
      const target = id && footnoteAnchorById.get(id);
      const num = target && numberByFooterId.get(target);
      return num ? `<sup class="fn-ref">${num}</sup>` : match;
    },
  );

  // De-clickify EPUB sups. Their printed number already matches the footer
  // (both come from the source book's own per-page numbering), so keep it.
  out = out.replace(/<sup\s+[^>]*\bdata-fn=[^>]*>([^<]*)<\/sup>/g, '<sup class="fn-ref">$1</sup>');

  out = convertCaretRefs(out);

  const epubNotesByPage = extractEpubNotesByPage(content);
  for (const [page, notes] of extractCaretNotesByPage(content)) {
    epubNotesByPage.set(page, [...(epubNotesByPage.get(page) ?? []), ...notes]);
  }

  // Splice a footer <aside> after each page's content — right before the next
  // page marker — and normalize the marker itself into a static "ص N" <div>.
  // Guard everything with once-per-page sets: some imports (Muwatta) emit page
  // markers out of order (90→91→90→91…).
  const segments = out.split(SEP_SPLIT_RE);
  let curPage = 1;
  for (const seg of segments) {
    const m = seg.match(/data-page="(\d+)"/);
    if (m) { curPage = parseInt(m[1], 10) - 1; break; }
  }
  const footeredPages = new Set<number>();
  const gfmFooterOnce = (p: number) => {
    if (footeredPages.has(p)) return "";
    footeredPages.add(p);
    const items = footerItemsByPage.get(p) ?? [];
    return footerHtml(items.map((it) => ({ id: it.id, num: numberByFooterId.get(it.id)!, body: it.body })));
  };
  const epubFooteredPages = new Set<string>();
  const epubFooterOnce = (p: string) => {
    if (epubFooteredPages.has(p)) return "";
    epubFooteredPages.add(p);
    const notes = epubNotesByPage.get(p) ?? [];
    return footerHtml(notes.map((body, i) => ({ id: `fn-e${p}-${i}`, num: String(i + 1), body })));
  };
  // Keyed by page:juz, not just page — some imports (Muwatta) emit the SAME
  // page marker twice within one volume (dedupe wants that), but a
  // multi-volume book can legitimately have "page 3" in both volume 1 and
  // volume 2 (dedupe must NOT collapse those into one). juz defaults to ""
  // for single-volume books, so that case behaves exactly as before.
  const emittedSeps = new Set<string>();
  const usedIds = new Set<string>();
  let result = "";
  for (let i = 0; i < segments.length; i++) {
    if (i % 2 === 1) {
      const m = segments[i].match(/data-page="(\d+)"/);
      const page = m ? m[1] : String(curPage);
      // GFM/hashiya footers belong to the page whose text just ended; EPUB
      // data-notes belong to this specific marker (its array describes the
      // text right above it). Both land before the "ص N" chip.
      result += gfmFooterOnce(curPage);
      result += epubFooterOnce(page);
      const juzM = segments[i].match(/data-juz="([^"]+)"/);
      const juz = juzM ? juzM[1] : "";
      const dedupeKey = `${page}:${juz}`;
      if (!emittedSeps.has(dedupeKey)) {
        emittedSeps.add(dedupeKey);
        // first claim of a page number gets the plain #pN id (keeps existing
        // links/citations working); a later volume reusing that same page
        // number gets a juz-suffixed id instead of a colliding duplicate id
        const plainId = `p${page}`;
        const id = usedIds.has(plainId) ? `${plainId}-v${juz || emittedSeps.size}` : plainId;
        usedIds.add(plainId);
        result += `<div class="page-sep" id="${id}" data-page="${page}"${juzM ? ` data-juz="${juz}"` : ""}></div>`;
      }
      if (m) curPage = parseInt(m[1], 10);
    } else {
      result += segments[i];
    }
  }
  result += gfmFooterOnce(curPage);
  return result;
}
