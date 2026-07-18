// Rebuilds correct "## <surah>" headings for تفسير القرآن العزيز لابن أبي
// زمنين. The imported markdown's own headings are broken (Athar-Engine
// compiler bug, confirmed reproducible from a fresh epub re-import: only
// ~29 headings exist for a 114-surah book, inserted at roughly-even
// intervals unrelated to actual content — one literally splits a word in
// half). The book's PROSE is intact and correctly ordered start to finish
// (verified against a fresh epub download); only the heading markers are
// wrong. Athar-Engine's own NCX-based heading fix (rebuildQuranChaptersFromNcx
// in compile-canonical.ts) also fails here — it zips toc.ncx's per-page-anchor
// sequence against Pandoc's paragraphs by POSITION, and the two lists have
// different lengths (5175 vs 2341 in this book), so it drifts out of sync.
//
// This sidesteps both bugs with a two-tier, ground-truth-first approach:
// 1. A literal "تفسير سورة <name>" phrase (this edition's own opening
//    banner) is a direct text match, no scoring — 100% reliable whenever
//    present (109/114 surahs here).
// 2. For the rest (short surahs whose banner uses alternate first-words
//    phrasing instead), walk every ﴿quote﴾ within the bounded gap between
//    the two nearest phrase-anchored neighbors, scoring it against the
//    mushaf (tokenScore/quoteTokens, same machinery already proven on this
//    book's tafsir-index repair, 559/559 verified). Bounding the search to
//    known-good neighbors matters: an earlier unbounded forward-walk let one
//    over-confident match strand the pointer inside سورة يوسف for the rest
//    of the book, since a forward-only pointer can never self-correct.
// Every resulting anchor is then re-verified against the mushaf before
// being trusted (see the audit pass below) — propose, then verify.
//
// Usage: tsx scripts/fix-zamanin-headings.ts [--write]
// (dry-run by default: reports what it would do; --write rewrites the file)
import fs from "node:fs";
import path from "node:path";
import matter from "gray-matter";
import { parseBook } from "../src/lib/chapters.js";
import { loadMushaf, quoteTokens, tokenScore } from "./lib-mushaf.js";

// Position-preserving normalization (same approach as gen-tafsir-index-
// zamanin.ts's normalizeWithMap): EXPLICIT \uXXXX escapes only — a literal
// Arabic combining-mark char class can silently span the base letter block
// depending on how it round-trips through the shell/editor, deleting real
// letters instead of just diacritics (bit us once already this session).
const TASHKEEL_RE = /[ؐ-ًؚ-ٰٟۖ-ۭ]/;
function normalizeWithMap(s: string): { norm: string; map: number[] } {
  let out = "";
  const map: number[] = [];
  for (let i = 0; i < s.length; i++) {
    let ch = s[i];
    if (TASHKEEL_RE.test(ch)) continue;
    if (ch === "آ" || ch === "أ" || ch === "إ" || ch === "ٱ") ch = "ا"; // آأإٱ -> ا
    else if (ch === "ى") ch = "ي"; // ى -> ي
    else if (ch === "ة") ch = "ه"; // ة -> ه
    out += ch;
    map.push(i);
  }
  return { norm: out, map };
}

const FILE = "src/content/book-lg/tafsir-al-quran-al-aziz-ibn-abi-zamanin.md";
const PASS_MIN = 0.5;

function loadSurahNames(): string[] {
  const dir = path.resolve("src/content/quran");
  const names: string[] = new Array(115);
  for (const file of fs.readdirSync(dir)) {
    if (!file.endsWith(".md")) continue;
    const { data } = matter(fs.readFileSync(path.join(dir, file), "utf-8"));
    names[Number(data.number)] = String(data.name);
  }
  return names;
}

interface Block { start: number; end: number; kind: "heading" | "hr" | "para"; text: string }
function splitBlocks(body: string): Block[] {
  const blocks: Block[] = [];
  const push = (start: number, end: number) => {
    const trimmed = body.slice(start, end).trim();
    if (!trimmed) return;
    if (/^#{1,6}\s/.test(trimmed)) { blocks.push({ start, end, kind: "heading", text: trimmed }); return; }
    if (/^-{3,}$/.test(trimmed)) { blocks.push({ start, end, kind: "hr", text: trimmed }); return; }
    blocks.push({ start, end, kind: "para", text: trimmed });
  };
  const re = /\n\s*\n/g;
  let last = 0, m: RegExpExecArray | null;
  while ((m = re.exec(body))) { push(last, m.index); last = re.lastIndex; }
  push(last, body.length);
  return blocks;
}

function stripTags(raw: string): string {
  return raw.replace(/<sup[^>]*>.*?<\/sup>/g, "").replace(/<[^>]+>/g, " ");
}

function main() {
  const write = process.argv.includes("--write");
  const raw = fs.readFileSync(path.resolve(FILE), "utf-8");
  const { content: body } = matter(raw);
  // Keep the original frontmatter block byte-for-byte — matter.stringify()
  // re-serializes YAML (quoting style, date format, array style) and turns
  // an otherwise tiny diff into unreviewable noise.
  const frontmatterBlock = raw.slice(0, raw.indexOf("---", 3) + 3);
  const surahNames = loadSurahNames();
  const mushaf = loadMushaf();

  // Same paragraph identity as parseBook (auto p{n} ids) + our own block scan
  // for raw offsets — zipped together, in order, to get id -> raw file offset.
  const parsedParagraphs = parseBook(body).paragraphs;
  const blocks = splitBlocks(body);
  const paraBlocks = blocks.filter((b) => b.kind === "para");
  if (paraBlocks.length !== parsedParagraphs.length) {
    console.error(`✗ paragraph count mismatch: parseBook=${parsedParagraphs.length} vs block-scan=${paraBlocks.length} — aborting, logic assumption broken.`);
    process.exit(1);
  }

  const HR_ONLY_RE = /^<hr[^>]*\/>$/;
  const realParas = parsedParagraphs
    .map((p, i) => ({ ...p, rawStart: paraBlocks[i].start }))
    .filter((p) => !HR_ONLY_RE.test(p.text.trim()));

  let concat = "";
  const spans: { start: number; end: number; id: string; rawStart: number }[] = [];
  for (const p of realParas) {
    const start = concat.length;
    concat += stripTags(p.text) + "\n\n";
    spans.push({ start, end: concat.length, id: p.id, rawStart: p.rawStart });
  }
  const spanAt = (offset: number) => spans.find((s) => offset >= s.start && offset < s.end) ?? spans[0];

  const { norm: normConcat, map: posMap } = normalizeWithMap(concat);
  const ALT_NAMES: Record<string, string[]> = {
    9: ["براءه"], 40: ["المؤمن"], 41: ["حم السجده"], 42: ["حم عسق"], 50: ["قاف"],
  };

  // Ground truth first: a literal "تفسير سورة <name>." (or "تفسير فاتحة
  // الكتاب" for the first surah) is a direct text match, no scoring
  // involved — 100% reliable whenever it's actually there. Scan for it
  // monotonically (each search starts right after the previous hit) so a
  // later cross-reference ("كما تقدم في تفسير سورة النمل") can't be
  // mistaken for that surah's own opening.
  const anchorOffset = new Map<number, number>();
  let cursor = 0;
  for (let s = 1; s <= 114; s++) {
    const candidates = s === 1 ? ["فاتحه الكتاب"] : [surahNames[s], ...(ALT_NAMES[s] ?? [])].map((n) => normalizeWithMap(n).norm);
    let bestPos = -1;
    for (const name of candidates) {
      const re = new RegExp(`تفسير\\s+سوره\\s+${name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}`);
      const m = normConcat.slice(cursor).match(re);
      if (m && (bestPos < 0 || m.index! < bestPos)) bestPos = cursor + m.index!;
    }
    if (bestPos >= 0) { anchorOffset.set(s, posMap[bestPos]); cursor = bestPos; }
  }
  console.log(`✓ phrase anchors: ${anchorOffset.size}/114`);

  // Quote-tracking fallback for whatever the phrase scan missed (short
  // surahs whose commentary opens with an alternate first-words title
  // instead of "تفسير سورة X") — but bounded strictly between the nearest
  // phrase-anchored neighbors, not walked freely across the whole book. A
  // forward-only pointer can never self-correct a bad jump (confirmed during
  // development: an unbounded walk let one over-confident match strand the
  // pointer inside سورة يوسف while it should've stayed near the book's end),
  // so keeping the search space small is what makes this safe.
  const quoteRe = /﴿([^﴾]{6,})﴾/g;
  const quotes: { index: number; tokens: string[] }[] = [];
  {
    let qm: RegExpExecArray | null;
    while ((qm = quoteRe.exec(concat))) {
      const tokens = quoteTokens(qm[1]);
      if (tokens.length) quotes.push({ index: qm.index, tokens });
    }
  }

  let missing: number[] = [];
  for (let s = 1; s <= 114; s++) if (!anchorOffset.has(s)) missing.push(s);
  for (const s of missing) {
    let lo = 0;
    for (let p = s - 1; p >= 1; p--) if (anchorOffset.has(p)) { lo = anchorOffset.get(p)!; break; }
    let hi = concat.length;
    for (let n = s + 1; n <= 114; n++) if (anchorOffset.has(n)) { hi = anchorOffset.get(n)!; break; }
    const ayat = mushaf.get(s);
    if (!ayat) continue;
    let best = 0, bestIndex = -1;
    for (const { index, tokens } of quotes) {
      if (index < lo || index >= hi) continue;
      for (const [, text] of ayat) {
        const sc = tokenScore(tokens, text);
        if (sc > best) { best = sc; bestIndex = index; }
      }
    }
    if (best >= PASS_MIN && bestIndex >= 0) anchorOffset.set(s, bestIndex);
  }
  missing = [];
  for (let s = 1; s <= 114; s++) if (!anchorOffset.has(s)) missing.push(s);
  if (missing.length) console.log(`✗ missing anchors for: ${missing.map((s) => `${s}:${surahNames[s]}`).join(", ")}`);
  console.log(`✓ anchors found: ${114 - missing.length}/114`);

  // sanity: anchors should be in strictly increasing document order (surah
  // sequence is monotonic in this book, verified earlier against a fresh epub)
  const ordered = [...anchorOffset.entries()].sort((a, b) => a[1] - b[1]);
  let outOfOrder = 0;
  for (let i = 1; i < ordered.length; i++) if (ordered[i][0] < ordered[i - 1][0]) outOfOrder++;
  console.log(`✓ out-of-order anchors: ${outOfOrder}`);

  // Audit, not just trust: relative ordering alone doesn't catch a bad
  // anchor that's self-consistently wrong (the forward-only pointer bug
  // found during development landed one anchor deep inside سورة يوسف while
  // still reporting a monotonic sequence). Verify each anchor's own claim —
  // the next ~1500 chars after it must contain at least one ﴿quote﴾ that
  // actually scores against that surah's own early ayat.
  let auditFail = 0;
  for (const [surahNum, offset] of anchorOffset) {
    const window = concat.slice(offset, offset + 1500);
    const wq = [...window.matchAll(/﴿([^﴾]{6,})﴾/g)];
    const ayat = mushaf.get(surahNum);
    if (!ayat) continue;
    let ok = false;
    for (const q of wq) {
      const tokens = quoteTokens(q[1]);
      for (const [, text] of ayat) if (tokenScore(tokens, text) >= 0.5) { ok = true; break; }
      if (ok) break;
    }
    if (!ok) { auditFail++; console.log(`  ✗ audit failed: ${surahNum}:${surahNames[surahNum]} — no matching quote within 1500 chars of its anchor`); }
  }
  console.log(`✓ audit: ${anchorOffset.size - auditFail}/${anchorOffset.size} anchors verified`);

  // ponytail: opt-in page/volume printout for manual spot-checking against
  // the printed edition (DEBUG_PAGES=1) — zero cost when unset, kept because
  // this exact kind of drift (anchor landing pages away from the true spot)
  // is what audit's loose window can miss; worth eyeballing after any rerun.
  if (process.env.DEBUG_PAGES) {
    const pageAt = (offset: number) => {
      const rawOff = spanAt(offset).rawStart;
      const m = body.slice(Math.max(0, rawOff - 500), rawOff + 500).match(/data-page="(\d+)"\s+data-vol="(\d+)"/);
      return m ? `${m[1]}/${m[2]}` : "?";
    };
    for (const [s, off] of [...anchorOffset.entries()].sort((a, b) => a[0] - b[0])) {
      console.log(`  ${s}:${surahNames[s]} → ${pageAt(off)}`);
    }
  }

  const anchorByParaId = new Map<string, number>();
  for (const [surahNum, offset] of anchorOffset) {
    const span = spanAt(offset);
    anchorByParaId.set(span.id, surahNum);
  }

  // Rebuild the body: drop every existing heading block, keep everything
  // else verbatim, inject a fresh "## <name>" right before whichever para
  // block starts each surah. "## المقدمة" stays at the very top (predates
  // the Quran text entirely, no anchor needed).
  let paraIdx = -1;
  const out: string[] = ["## المقدمة", ""];
  for (const b of blocks) {
    // Only "## " (h2, surah boundaries) is broken — every h2 gets dropped
    // and replaced below. Other levels (h1 book title, h3+ sub-headings if
    // any) aren't part of this bug and must survive untouched.
    if (b.kind === "heading") {
      if (!/^##\s/.test(b.text)) out.push(b.text, "");
      continue;
    }
    if (b.kind === "para") {
      paraIdx++;
      const surahNum = anchorByParaId.get(parsedParagraphs[paraIdx].id);
      if (surahNum) out.push(`## ${surahNames[surahNum]}`, "");
    }
    out.push(b.text, "");
  }
  const newBody = out.join("\n").replace(/\n{3,}/g, "\n\n").trim() + "\n";
  console.log(`✓ new heading count: ${(newBody.match(/^## /gm) || []).length}`);

  if (write) {
    const newRaw = frontmatterBlock + "\n" + newBody;
    fs.writeFileSync(path.resolve(FILE), newRaw, "utf-8");
    console.log(`✓ written → ${FILE}`);
  } else {
    console.log("(dry run — pass --write to apply)");
  }
}

main();
