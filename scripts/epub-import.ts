#!/usr/bin/env tsx
// EPUB → Markdown importer for athar-archive's content model.
// Targets the المكتبة الشاملة (Shamela) EPUB export — one xhtml per printed
// page, آيات in {…}, chapters in <span class="title">, المحقق notes in
// <span class="footnote">. Produces a `book` .md (chapters as `## …`) and, if
// missing, a stub `person` .md for the author (satisfies the book→person rule
// in validate.ts). Zero new deps: shells out to `unzip`, regex-parses the rigid,
// machine-generated XHTML.
// ponytail: regex over a single, regular producer's output — if another EPUB
// shape shows up, branch on it (or add a parser) then, not now.
//
// Usage:  pnpm import:epub <file.epub|dir/> [more…] [flags]
//   --out <dir>        content root (default: src/content)
//   --kind <متن|مرجع|مجموع>   book.kind (omitted by default — no badge)
//   --status <draft|review|published>  (default: published)
//   --slug <slug>      override book slug (single book only)
//   --person-slug <s>  override author slug (single book only)
//   --dry-run          print what would be written, write nothing
//   --selftest         run built-in assertions and exit

import { execFileSync } from "node:child_process";
import { mkdtempSync, rmSync, readFileSync, writeFileSync, existsSync, mkdirSync, readdirSync, statSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

// ---------- arabic → slug ----------
const TASHKEEL = /[ً-ْٰـ]/g; // harakat + tatweel
const stripTashkeel = (s: string) => s.replace(TASHKEEL, "");
const TR: Record<string, string> = {
  "ا": "a", "أ": "a", "إ": "i", "آ": "a", "ٱ": "a",
  "ب": "b", "ت": "t", "ث": "th", "ج": "j", "ح": "h",
  "خ": "kh", "د": "d", "ذ": "dh", "ر": "r", "ز": "z",
  "س": "s", "ش": "sh", "ص": "s", "ض": "d", "ط": "t",
  "ظ": "z", "ع": "a", "غ": "gh", "ف": "f", "ق": "q",
  "ك": "k", "ل": "l", "م": "m", "ن": "n", "ه": "h",
  "ة": "a", "و": "w", "ؤ": "w", "ي": "y", "ى": "a",
  "ئ": "y", "ء": "",
};
const ALIF_LAM = "ال"; // ال
function translitWord(w: string): string {
  if (w.startsWith(ALIF_LAM) && w.length > 2) return "al-" + translitWord(w.slice(2));
  return [...w].map((c) => TR[c] ?? "").join("");
}
export function slugify(ar: string): string {
  const s = stripTashkeel(ar)
    .split(/\s+/).map(translitWord).join("-")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/-{2,}/g, "-")
    .replace(/^-+|-+$/g, "");
  return /^[a-z0-9]+(-[a-z0-9]+)*$/.test(s) ? s : "book-" + Math.random().toString(36).slice(2, 8);
}

// ---------- html helpers ----------
function decode(s: string): string {
  return s
    .replace(/&quot;/g, '"').replace(/&nbsp;/g, " ").replace(/&lt;/g, "<").replace(/&gt;/g, ">")
    .replace(/&#(\d+);/g, (_, d) => String.fromCodePoint(+d))
    .replace(/&#x([0-9a-f]+);/gi, (_, h) => String.fromCodePoint(parseInt(h, 16)))
    .replace(/&amp;/g, "&");
}
const stripTags = (s: string) => s.replace(/<[^>]+>/g, "");
const cleanInline = (s: string) => decode(stripTags(s)).replace(/[ \t]+/g, " ").trim();

// ---------- one page xhtml → markdown ----------
// Returns md (clean text, no footnote refs) and notes (المحقق note texts for
// the page separator's data-notes; displayed in the حاشية panel on click).
export function pageToMd(xhtml: string, pageId: string): { md: string; notes: string[] } {
  let inner = (xhtml.match(/<div[^>]*id=["']book-container["'][^>]*>([\s\S]*?)<\/div>/i)?.[1] ?? "");

  // footnotes: pull the المحقق notes out, remember number -> text
  const fnotes: { n: string; t: string }[] = [];
  inner = inner.replace(/<span class=["']footnote-hr["']>[\s\S]*?<\/span>/gi, "");
  inner = inner.replace(/<span class=["']footnote["']>\s*(\d+)\s*([\s\S]*?)<\/span>/gi, (_m, n, t) => {
    fnotes.push({ n, t: cleanInline(t).replace(/\{/g, "﴿").replace(/\}/g, "﴾") }); // آيات quoted in a note
    return "";
  });

  // chapter titles -> H2 (strip the leading number/tatweel Shamela prepends);
  // red narration numbers -> plain text
  inner = inner.replace(/<span class=["']title["']>([\s\S]*?)<\/span>/gi,
    (_m, t) => `\n## ${cleanInline(t).replace(/^[\d\s.ـ\-–—]+/, "")}\n`);
  inner = inner.replace(/<span class=["']red["']>([\s\S]*?)<\/span>/gi, "$1");

  // structure -> text
  inner = inner.replace(/<a [^>]*>\s*<\/a>/gi, "");      // empty chapter anchors
  inner = inner.replace(/<br\s*\/?>/gi, "\n");
  inner = inner.replace(/\{/g, "﴿").replace(/\}/g, "﴾"); // Quran braces -> ﴿ ﴾
  let text = decode(stripTags(inner));

  // Replace inline footnote markers (digit glued to Arabic) with clickable <sup>.
  // Content numbers (hadith sequences, years) are not preceded by Arabic → untouched.
  for (const fn of fnotes) {
    text = text.replace(
      new RegExp(`(?<=[\\u0600-\\u06FF])${fn.n}(?![0-9])`, "g"),
      `<sup data-fn="${fn.n}" data-sep-page="${pageId}">${fn.n}</sup>`,
    );
  }

  // Join lines with space (not \n\n) to avoid turning every typeset line into
  // its own paragraph. Only headings (## …) get a real paragraph break.
  const lines = text.split("\n").map((l) => l.replace(/[ \t]+/g, " ").trim()).filter(Boolean);
  const parts: string[] = [];
  let para = "";
  for (const line of lines) {
    if (line.startsWith("#")) {
      if (para) { parts.push(para.trim()); para = ""; }
      parts.push(line);
    } else {
      para += (para ? " " : "") + line;
    }
  }
  if (para) parts.push(para.trim());
  return { md: parts.join("\n\n"), notes: fnotes.map((fn) => fn.t) };
}

// ---------- yaml ----------
const y = (s: string) => `"${s.replace(/\\/g, "\\\\").replace(/"/g, '\\"')}"`;

// ---------- epub -> files ----------
interface Meta { title: string; creator: string; edition?: string; muhaqqiq?: string; died?: string }

function readEpub(file: string): { meta: Meta; pages: { id: string; xhtml: string }[] } {
  const dir = mkdtempSync(join(tmpdir(), "epub-"));
  try {
    execFileSync("unzip", ["-o", "-q", file, "-d", dir]);
    const opfPath = findOpf(dir);
    const opf = readFileSync(opfPath, "utf8");
    const opfDir = opfPath.slice(0, opfPath.lastIndexOf("/"));

    const meta: Meta = {
      title: cleanInline(opf.match(/<dc:title[^>]*>([\s\S]*?)<\/dc:title>/i)?.[1] ?? "بدون عنوان"),
      creator: cleanInline(opf.match(/<dc:creator[^>]*>([\s\S]*?)<\/dc:creator>/i)?.[1] ?? ""),
    };
    // manifest id -> href, spine order
    const manifest = new Map<string, string>();
    for (const m of opf.matchAll(/<item\s+[^>]*id=["']([^"']+)["'][^>]*href=["']([^"']+)["']/gi)) manifest.set(m[1], m[2]);
    const order = [...opf.matchAll(/<itemref\s+[^>]*idref=["']([^"']+)["']/gi)].map((m) => m[1]);

    // richer metadata from info.xhtml if present
    const infoHref = manifest.get("info");
    if (infoHref) {
      const info = readFileSync(join(opfDir, infoHref), "utf8");
      meta.edition = infoField(info, "الطبعة");   // الطبعة
      meta.muhaqqiq = infoField(info, "المحقق");  // المحقق
      meta.died = (info.match(/المتوفى:\s*(\d+)\s*هـ/) ?? [])[1]; // المتوفى: NNNهـ
    }
    if (!meta.died) meta.died = (file.match(/(\d{2,4})\s*هـ/) ?? [])[1];

    const pages: { id: string; xhtml: string }[] = [];
    for (const id of order) {
      if (id === "info" || /cover/i.test(id)) continue;
      const href = manifest.get(id);
      if (!href) continue;
      pages.push({ id, xhtml: readFileSync(join(opfDir, href), "utf8") });
    }
    return { meta, pages };
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}
function findOpf(dir: string): string {
  const container = readFileSync(join(dir, "META-INF", "container.xml"), "utf8");
  const rel = container.match(/full-path=["']([^"']+\.opf)["']/i)?.[1];
  if (!rel) throw new Error("no .opf in container.xml");
  return join(dir, rel);
}
function infoField(info: string, label: string): string | undefined {
  const re = new RegExp(`info-title["']>\\s*${label}:?\\s*</span>\\s*<span[^>]*>([\\s\\S]*?)</span>`, "i");
  const v = info.match(re)?.[1];
  return v ? cleanInline(v) : undefined;
}

interface Opt { out: string; kind?: string; status: string; slug?: string; personSlug?: string; dryRun: boolean; today: string }

function build(file: string, opt: Opt): { book: { path: string; text: string }; person: { path: string; text: string } | null } {
  const { meta, pages } = readEpub(file);
  const personSlug = opt.personSlug ?? slugify(meta.creator || "unknown");
  const bookSlug = opt.slug ?? slugify(meta.title);

  const bodyParts: string[] = [];
  let pageNum = 0;
  for (const p of pages) {
    pageNum++;
    const { md, notes } = pageToMd(p.xhtml, String(pageNum));
    if (!md.trim()) { pageNum--; continue; }
    const na = notes.length ? ` data-notes='${JSON.stringify(notes).replace(/'/g, "&#39;")}'` : "";
    // page separator after the content: horizontal rule + page number pill + optional حاشية
    bodyParts.push(`${md}\n\n<div class="page-sep" data-page="${pageNum}"${na}></div>`);
  }
  let body = bodyParts.join("\n\n");

  const fm = [
    "---",
    `title: ${y(meta.title)}`,
    `status: ${opt.status}`,
    `published_at: ${opt.today}`,
    `person: ${personSlug}`,
    opt.kind ? `kind: ${opt.kind}` : null,
    meta.edition ? `edition: ${y(meta.edition)}` : null,
    meta.muhaqqiq ? `description: ${y("بتحقيق " + meta.muhaqqiq)}` : null, // بتحقيق
    "---",
    "",
  ].filter((l) => l !== null).join("\n");

  const bareName = stripTashkeel(meta.creator);
  const personText = [
    "---",
    `title: ${y(bareName || personSlug)}`,
    `status: ${opt.status}`,
    `published_at: ${opt.today}`,
    meta.died ? `died: ${y(meta.died + "هـ")}` : null, // هـ
    meta.creator !== bareName ? `also_known_as: [${y(meta.creator)}]` : null,
    "---",
    "",
  ].filter((l) => l !== null).join("\n");

  const personPath = join(opt.out, "person", personSlug + ".md");
  return {
    book: { path: join(opt.out, "book", bookSlug + ".md"), text: fm + body },
    person: existsSync(personPath) ? null : { path: personPath, text: personText },
  };
}

function writeFileMk(path: string, text: string) {
  mkdirSync(path.slice(0, path.lastIndexOf("/")), { recursive: true });
  writeFileSync(path, text);
}

// ---------- selftest ----------
function selftest() {
  const a = (cond: boolean, msg: string) => { if (!cond) throw new Error("selftest: " + msg); };
  const iman = "الإيمان"; // الإيمان
  a(/^[a-z0-9]+(-[a-z0-9]+)*$/.test(slugify(iman)), "slug valid: " + slugify(iman));
  const sample =
    `<div id="book-container"><hr/><a id='C1'></a>1<span class="title">ـ باب الإيمان</span>` +
    `<br />قال تعالى {إنا} [ال:1] ` +
    `<span class="red">9- </span>حدثي1<span class="footnote-hr">&nbsp;</span>` +
    `<span class="footnote">1 إسناده صحيح</span></div>`;
  const { md, notes } = pageToMd(sample, "P1");
  a(md.includes("## باب الإيمان"), "title -> H2: " + md);
  a(md.includes("﴿إنا﴾"), "braces -> ornate: " + md);
  a(md.includes("9- "), "red number kept");
  a(!md.includes("[^P1_1]"), "footnote ref stripped from text: " + md);
  a(notes[0] === "إسناده صحيح", "note text in notes array: " + notes[0]);
  console.log("✓ selftest passed");
}

// ---------- main ----------
function main() {
  const argv = process.argv.slice(2);
  if (argv.includes("--selftest")) return selftest();
  const flag = (name: string) => { const i = argv.indexOf(name); return i >= 0 ? argv[i + 1] : undefined; };
  const valued = new Set(["--out", "--kind", "--status", "--slug", "--person-slug"]);
  const positional = argv.filter((a, i) => !a.startsWith("--") && !valued.has(argv[i - 1]));
  if (!positional.length) {
    console.error("usage: pnpm import:epub <file.epub|dir/> [more…] [--out src/content] [--kind متن] [--status published] [--slug s] [--person-slug s] [--dry-run]");
    process.exit(1);
  }
  const opt: Opt = {
    out: flag("--out") ?? "src/content",
    kind: flag("--kind"),
    status: flag("--status") ?? "published",
    slug: flag("--slug"),
    personSlug: flag("--person-slug"),
    dryRun: argv.includes("--dry-run"),
    today: new Date().toISOString().slice(0, 10),
  };
  if ((opt.slug || opt.personSlug) && positional.length > 1) {
    console.error("--slug/--person-slug only make sense with a single epub"); process.exit(1);
  }
  // expand directories to their .epub files
  const files: string[] = [];
  for (const p of positional) {
    if (!existsSync(p)) { console.error("✗ not found: " + p); continue; }
    if (statSync(p).isDirectory()) {
      files.push(...readdirSync(p).filter((f) => f.endsWith(".epub")).map((f) => join(p, f)));
    } else {
      files.push(p);
    }
  }
  for (const file of files) {
    if (!existsSync(file)) { console.error("✗ not found: " + file); continue; }
    const { book, person } = build(file, opt);
    const lines = [`\u{1F4D6} ${file}`, `   -> ${book.path} (${book.text.length} bytes)`];
    lines.push(person ? `   -> ${person.path} (new author stub)` : `   . author exists — reused`);
    console.log(lines.join("\n"));
    if (opt.dryRun) { console.log("   [dry-run] " + book.text.split("\n").slice(0, 12).join("\n   ")); continue; }
    if (person) writeFileMk(person.path, person.text);
    writeFileMk(book.path, book.text);
  }
  if (!opt.dryRun) console.log("\nNext: pnpm validate:content");
}

main();
