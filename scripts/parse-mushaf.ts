#!/usr/bin/env tsx
// Parse a Shamela-format mushaf EPUB → src/content/quran/<N>.md (one file per surah).
//
// Non-Shamela layout (this epub): OEBPS/Text/page_N.xhtml, plain <p> text.
// Surah 1 header: <h1>N - سورة Name</h1>; all others inline in <p>: "N - سورة NameZtext".
// Ayah N ends with " (N)". Footer: <p class="center">الصفحة: P - الجزء: J</p>.
//
// Usage:
//   pnpm tsx scripts/parse-mushaf.ts <mushaf.epub> [--out src/content] [--dry-run] [--selftest]

import { execFileSync } from "node:child_process";
import { mkdtempSync, rmSync, readFileSync, writeFileSync, mkdirSync, readdirSync, existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

// ── HTML helpers ──────────────────────────────
function decode(s: string): string {
  return s
    .replace(/&quot;/g, '"').replace(/&nbsp;/g, " ").replace(/&lt;/g, "<").replace(/&gt;/g, ">")
    .replace(/&#(\d+);/g, (_, d) => String.fromCodePoint(+d))
    .replace(/&#x([0-9a-f]+);/gi, (_, h) => String.fromCodePoint(parseInt(h, 16)))
    .replace(/&amp;/g, "&");
}
const stripTags = (s: string) => s.replace(/<[^>]+>/g, "");

// ── EPUB extraction ───────────────────────────
function extractEpub(epubPath: string): string {
  const tmp = mkdtempSync(join(tmpdir(), "mushaf-"));
  execFileSync("unzip", ["-o", "-q", epubPath, "-d", tmp]);
  return tmp;
}

// ── Mushaf parser ─────────────────────────────
interface SurahData { number: number; name: string; startPage: number; ayat: string[] }

// Strip the Quran page footer: <p class="center">الصفحة: N - الجزء: M</p>
const FOOTER_RE = /<p[^>]*class=["']center["'][^>]*>[\s\S]*?<\/p>/gi;
// Surah header: "N - سورة Name" followed by Z (artifact) or newline
// ponytail: Z is ASCII 0x5A artifact present in this epub between surah name and first ayah
const SURAH_HDR_RE = /(\d+)\s*-\s*سورة\s+([^\nZ]+?)(?=Z|\n|$)/gu;

export function parseMushaf(pagesDir: string): SurahData[] {
  const pageFiles = readdirSync(pagesDir)
    .filter((f) => /^page_\d+\.xhtml$/.test(f))
    .sort((a, b) => +a.match(/\d+/)![0] - +b.match(/\d+/)![0]);

  // Concatenate all pages as plain text (footer stripped)
  let full = "";
  for (const f of pageFiles) {
    const pageNum = f.match(/\d+/)![0];
    const xhtml = readFileSync(join(pagesDir, f), "utf-8").replace(FOOTER_RE, "");
    // Convert block-level closing tags to newlines so h1/p content doesn't merge
    const withNl = xhtml.replace(/<\/(?:h[1-6]|p|div|br)\s*>/gi, "\n");
    const pageText = decode(stripTags(withNl)).replace(/[ \t]+/g, " ").trim();
    full += `\n<page:${pageNum}>\n` + pageText;
  }

  // Find all surah boundaries
  const boundaries: { pos: number; num: number; name: string; textStart: number }[] = [];
  SURAH_HDR_RE.lastIndex = 0;
  let m: RegExpExecArray | null;
  while ((m = SURAH_HDR_RE.exec(full)) !== null) {
    boundaries.push({
      pos: m.index,
      num: +m[1],
      name: m[2].trim(),
      textStart: m.index + m[0].length,
    });
  }

  const surahs: SurahData[] = [];
  for (let i = 0; i < boundaries.length; i++) {
    const { pos, num, name, textStart } = boundaries[i];
    // Skip duplicate headers (same surah appearing twice due to page boundaries)
    if (surahs.length > 0 && surahs[surahs.length - 1].number === num) continue;
    const end = i + 1 < boundaries.length ? boundaries[i + 1].pos : full.length;
    const surahText = full.slice(textStart, end).replace(/^Z/, ""); // strip leading Z artifact

    const textBefore = full.slice(0, pos);
    const pageMatches = [...textBefore.matchAll(/<page:(\d+)>/g)];
    const startPage = pageMatches.length > 0 ? parseInt(pageMatches[pageMatches.length - 1][1], 10) : 1;

    surahs.push({ number: num, name, startPage, ayat: splitAyat(surahText) });
  }
  return surahs;
}

// Split ayah text by (N) markers — each chunk before (N) is that ayah's text
function splitAyat(text: string): string[] {
  const parts = text.split(/\s*\(\d+\)\s*/);
  // last part is empty or the start of next surah header (handled by boundary slicing)
  return parts.slice(0, -1).map((s) => s.trim()).filter(Boolean);
}

// ── YAML helpers ──────────────────────────────
const y = (s: string) => `"${s.replace(/"/g, '\\"')}"`;

// ── Surah → .md content ───────────────────────
function surahToMd(surah: SurahData, today: string): string {
  const fm = [
    "---",
    `title: ${y("سورة " + surah.name)}`,
    `number: ${surah.number}`,
    `name: ${y(surah.name)}`,
    `start_page: ${surah.startPage}`,
    `ayah_count: ${surah.ayat.length}`,
    `status: published`,
    `published_at: ${today}`,
    "---",
    "",
  ].join("\n");
  // Each ayah: text + {#N} anchor on next line (same paragraph block)
  const body = surah.ayat
    .map((text, i) => {
      const clean = text.replace(/<page:(\d+)>/g, '<hr class="page-sep" data-page="$1" />');
      return `${clean}\n{#${i + 1}}`;
    })
    .join("\n\n");
  return fm + body + "\n";
}

// ── Self-test ─────────────────────────────────
function selftest() {
  const a = (cond: boolean, msg: string) => { if (!cond) throw new Error("selftest: " + msg); };

  // Simulate two pages: page 1 = Fatiha (h1), page 2 = Baqarah inline with Z artifact
  const page1 = `<body><h1>1 - سورة الفاتحة</h1><p>بِسْمِ اللَّهِ (1) الْحَمْدُ لِلَّهِ (2) </p><p class="center">الصفحة: 1 - الجزء: 1</p></body>`;
  const page2 = `<body><p>2 - سورة البقرةZالم (1) ذَلِكَ الْكِتَابُ (2) </p><p class="center">الصفحة: 2 - الجزء: 1</p></body>`;

  // Write to temp dir and run parseMushaf
  const tmp = mkdtempSync(join(tmpdir(), "mushaf-test-"));
  writeFileSync(join(tmp, "page_1.xhtml"), page1);
  writeFileSync(join(tmp, "page_2.xhtml"), page2);
  const surahs = parseMushaf(tmp);
  rmSync(tmp, { recursive: true });

  a(surahs.length === 2,                              "2 surahs parsed: " + surahs.length);
  a(surahs[0].number === 1,                           "surah 1 number: " + surahs[0].number);
  a(surahs[0].name === "الفاتحة",                    "surah 1 name: " + surahs[0].name);
  a(surahs[0].ayat.length === 2,                      "surah 1 ayah count: " + surahs[0].ayat.length);
  a(surahs[0].ayat[0].includes("بِسْمِ"),             "surah 1 ayah 1 text: " + surahs[0].ayat[0]);
  a(surahs[1].number === 2,                           "surah 2 number: " + surahs[1].number);
  a(surahs[1].name === "البقرة",                      "surah 2 name: " + surahs[1].name);
  a(surahs[1].ayat.length === 2,                      "surah 2 ayah count: " + surahs[1].ayat.length);
  a(surahs[1].ayat[0] === "الم",                      "surah 2 ayah 1 = الم: " + surahs[1].ayat[0]);
  a(!surahs[0].ayat[0].includes("الجزء"),             "footer stripped from ayah text");

  // Test surahToMd format
  const md = surahToMd(surahs[0], "2026-01-01");
  a(md.includes("number: 1"),                         "number in frontmatter");
  a(md.includes("ayah_count: 2"),                     "ayah_count in frontmatter");
  a(md.includes("{#1}"),                              "anchor {#1} in body");
  a(md.includes("{#2}"),                              "anchor {#2} in body");
  a(md.includes("بِسْمِ"),                             "ayah text in body");

  console.log("✓ parse-mushaf selftest passed");
}

// ── Main ──────────────────────────────────────
function main() {
  const argv = process.argv.slice(2);
  if (argv.includes("--selftest")) return selftest();

  const dryRun = argv.includes("--dry-run");
  const outIdx = argv.indexOf("--out");
  const outDir = outIdx >= 0 ? argv[outIdx + 1] : "src/content";
  const epubPath = argv.find((a) => !a.startsWith("--") && argv[argv.indexOf(a) - 1] !== "--out");

  if (!epubPath) {
    console.error("usage: pnpm tsx scripts/parse-mushaf.ts <mushaf.epub> [--out src/content] [--dry-run]");
    process.exit(1);
  }

  const tmp = extractEpub(epubPath);
  const pagesDir = join(tmp, "OEBPS", "Text");

  try {
    const surahs = parseMushaf(pagesDir);
    if (surahs.length !== 114) {
      console.warn(`⚠  expected 114 surahs, got ${surahs.length}`);
    }

    const today = new Date().toISOString().slice(0, 10);
    const quranDir = join(outDir, "quran");
    if (!dryRun) mkdirSync(quranDir, { recursive: true });

    for (const surah of surahs) {
      const path = join(quranDir, `${surah.number}.md`);
      const text = surahToMd(surah, today);
      if (dryRun) {
        console.log(`[dry-run] ${path} (${surah.ayat.length} ayat)`);
      } else {
        writeFileSync(path, text);
        console.log(`✓ ${path} (${surah.ayat.length} ayat)`);
      }
    }

    console.log(`\n${surahs.length} surahs written to ${quranDir}`);
    if (!dryRun) console.log("Next: pnpm validate:content");
  } finally {
    rmSync(tmp, { recursive: true });
  }
}

main();
