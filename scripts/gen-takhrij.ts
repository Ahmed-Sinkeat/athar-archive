// Auto takhrij ("ورد أيضاً في…"): cross-links between athar-numbered books that
// quote the exact same narration matn. Runs after gen-book-chapters.ts (reuses
// the same chunked/non-chunked chapter split so hrefs match real routes).
import fs from "node:fs";
import path from "node:path";
import { loadContentFromDisk } from "../src/lib/load.js";
import { analyzeBook } from "../src/lib/chunk.js";
import { parseBook } from "../src/lib/chapters.js";
import { isAtharNumberedBook, parseAtharNumber, parseAtharMatn } from "../src/lib/hadith.js";
import { normalizeArabic } from "../src/lib/ar-normalize.js";

interface Member { bookId: string; atharN: number; title: string; href: string }

function normalizeMatn(s: string): string {
  return normalizeArabic(s)
    .replace(/[.,،؛:؟!"'«»()\[\]{}\-–—٠-٩0-9]/g, "")
    .replace(/\s+/g, " ")
    .trim();
}

function main() {
  const entries = loadContentFromDisk().filter((e) => e.data.status === "published" && e.collection === "book");
  const groups = new Map<string, Member[]>();

  for (const e of entries) {
    const title = String(e.data.title ?? "");
    const wholeParagraphs = parseBook(e.body).paragraphs;
    if (!isAtharNumberedBook(wholeParagraphs)) continue;

    const a = analyzeBook(e.body);
    const chapterSets = a.chunked
      ? a.chapters.map((c) => ({ slug: c.slug as string | undefined, paragraphs: parseBook(c.content).paragraphs }))
      : [{ slug: undefined, paragraphs: wholeParagraphs }];

    for (const { slug, paragraphs } of chapterSets) {
      for (const p of paragraphs) {
        const n = parseAtharNumber(p.text);
        if (n === null) continue;
        const matn = parseAtharMatn(p.text);
        if (!matn) continue;
        const key = normalizeMatn(matn);
        if (!key) continue;
        const href = slug ? `/book/${e.id}/${slug}#athar-${n}` : `/book/${e.id}#athar-${n}`;
        const member: Member = { bookId: e.id, atharN: n, title, href };
        const list = groups.get(key);
        if (list) list.push(member);
        else groups.set(key, [member]);
      }
    }
  }

  const takhrij: Record<string, { title: string; href: string }[]> = {};
  let crossLinkedGroups = 0;
  for (const members of groups.values()) {
    if (members.length < 2) continue;
    crossLinkedGroups++;
    for (const m of members) {
      const others = members.filter((o) => o !== m).map((o) => ({ title: o.title, href: o.href }));
      takhrij[`${m.bookId}:${m.atharN}`] = others;
    }
  }

  fs.writeFileSync(path.resolve("src/data/takhrij.json"), JSON.stringify(takhrij), "utf-8");
  console.log(`✓ gen-takhrij: ${crossLinkedGroups} matching group(s) → ${Object.keys(takhrij).length} athar entries → src/data/takhrij.json`);
}

main();
