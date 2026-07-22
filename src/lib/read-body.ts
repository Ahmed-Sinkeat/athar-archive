import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import matter from "gray-matter";

// A batch of imports (~230 books) left page breaks as plain "الجزء: N -
// الصفحة: M" text instead of the <hr class="page-sep" data-page="M"
// data-juz="N" /> marker every page-footnote/chapter helper (chapters.ts,
// page-footnotes.ts) actually looks for — so those books got a stray
// unstyled line breaking the reading flow instead of the small "ص M" chip,
// AND their [^id] footnotes never got grouped by page (no marker to group
// by), so remark-gfm numbered them by first appearance in the WHOLE file
// instead of per printed page. Normalizing to the real marker here, once,
// upstream of every consumer, fixes both without touching either book.
//
// The marker line isn't always that clean — surveyed variants across the
// corpus:
//   الجزء: 5 - الصفحة: 165                    (plain — most common)
//   الحديث: 129 - الجزء: 5 - الصفحة: 165       (hadith collections number
//                                               each narration too — dropped,
//                                               the site has no slot for it)
//   الجزء: مقدمة - الصفحة: 2                   (non-numeric جزء: مقدمة/
//                                               الفهارس/"م 3" — kept as the
//                                               opaque data-juz string; only
//                                               data-page has to be numeric)
//   الجزء: 1/ 2 ¦ الصفحة: 5                    ("¦" separator, "N/ N" جزء)
//   [^fn419] الجزء: 1 - الصفحة: 264            (a stray footnote ref glued
//                                               onto the marker line by the
//                                               importer — kept in place,
//                                               ahead of the marker, so it
//                                               still resolves against its
//                                               [^fn419]: definition)
const PLAIN_PAGE_MARKER_RE =
  /^((?:\[\^[a-zA-Z0-9_-]+\]\s*)*)(?:الحديث: \d+ - )?الجزء: (.+?) [-¦] الصفحة: (\d+)$/gm;
// A handful of books (40) also carry a bare "الجزء: N" line (occasionally a
// range, "الجزء: 69 - 70") — no page number — once at the very start of each
// new volume, right next to a proper "[الجزء الأول]" heading that already
// says the same thing. No page number means there's nothing to build a real
// page-sep marker from, so unlike the marker above this one is just dropped.
// Deliberately narrow (digits/dashes only, nothing else on the line): at
// least one book (عمدة الحفاظ) has a real sentence — a dictionary entry
// DEFINING the word "جزء" — that also happens to start with "الجزء: "; a
// loose ".+$" here would silently delete real prose instead of noise.
const BARE_VOLUME_MARKER_RE = /^الجزء: \d+(?:\s*-\s*\d+)?$\n?/gm;
export function normalizePageMarkers(content: string): string {
  const withPageSeps = content.replace(
    PLAIN_PAGE_MARKER_RE,
    (_, leadingRefs, juz, page) => `${leadingRefs}<hr class="page-sep" data-page="${page}" data-juz="${juz.trim()}" />`,
  );
  return withPageSeps.replace(BARE_VOLUME_MARKER_RE, "");
}

export async function readBody(entry: { filePath?: string; body?: string }): Promise<string> {
  if (entry.body !== undefined && entry.body !== "") return normalizePageMarkers(entry.body);
  if (!entry.filePath) return "";
  const raw = await readFile(resolve(entry.filePath), "utf-8");
  return normalizePageMarkers(matter(raw).content);
}
