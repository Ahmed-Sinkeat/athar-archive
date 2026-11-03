// The app's word dictionary («معنى» on a selected word), built from the lexicons in the archive.
//
//   dict/index.json      { sources: [{ id, title }], roots: [root], forms: { form: [root] } }
//   dict/r/<hex>.json    [{ s: source index, o: heading block ordinal, x: entry text }]
//
// A word is matched to its root on the phone (DictionaryLookup.kt): first through `forms` — the
// words each dictionary itself lists under a root, e.g. «(الأجر) الثواب» under أ ج ر — then by
// trimming affixes until a known root is left. `o` is the entry's heading in the book's own app
// package, so «المزيد» opens the book there. Keys are dictKey(): normalizeArabic with every hamza
// seat folded to ا (DictionaryLookup.key must stay identical).
//
// Packed as dist/app-dict/dict.zip, which app:publish uploads and the app downloads on first «معنى».
//
// Usage: tsx scripts/gen-app-dict.ts [--out=dist/app-dict] [--selftest]
import { mkdirSync, readFileSync, rmSync, writeFileSync, existsSync } from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { normalizeArabic } from "../src/lib/ar-normalize.js";
import { parseReadableDocument } from "../src/lib/app-v2/parser.js";

/** Display order in the popup: short, everyday glosses first, the vast lexicons after. */
// `mark`: how a lexicon without root headings starts an entry. "colon": a paragraph opening with the
// bare root, «مرع: المَرْعُ…» (لسان العرب); "word": headed by a headword, not a root (شمس العلوم).
export const DICTS: { id: string; title: string; mark?: "colon" | "word" }[] = [
  { id: "mukhtar-al-sihah", title: "مختار الصحاح" },
  { id: "al-misbah-al-munir", title: "المصباح المنير" },
  { id: "al-qamus-al-muhit", title: "القاموس المحيط" },
  { id: "al-sihah-al-jawhari", title: "الصحاح" },
  { id: "lisan-al-arab", title: "لسان العرب", mark: "colon" },
  { id: "taj-al-arus", title: "تاج العروس" },
  { id: "kitab-al-ayn", title: "العين" },
  { id: "tahdhib-al-lugha", title: "تهذيب اللغة" },
  { id: "al-muhkam-wal-muhit-al-azam", title: "المحكم" },
  { id: "jamharat-al-lugha", title: "جمهرة اللغة" },
  { id: "asas-al-balagha", title: "أساس البلاغة" },
  { id: "al-ubab-al-zakhir", title: "العباب الزاخر" },
  { id: "shams-al-ulum", title: "شمس العلوم", mark: "word" },
  { id: "al-mughrib-fi-tartib-al-murib", title: "المغرب" },
  { id: "al-nihaya-fi-gharib-al-hadith", title: "النهاية في غريب الحديث" },
  { id: "al-faiq-fi-gharib-al-hadith", title: "الفائق" },
  { id: "al-majmu-al-mughith", title: "المجموع المغيث" },
  { id: "al-gharibayn", title: "الغريبين" },
  { id: "al-mufradat-fi-gharib-al-quran", title: "مفردات الراغب" },
  { id: "gharib-al-quran-al-sijistani", title: "غريب القرآن للسجستاني" },
  { id: "mujam-wa-tafsir-lughawi", title: "معجم كلمات القرآن" },
];

const HAMZA_SEATS = /[\u0621\u0624\u0626]/g;
export const dictKey = (s: string) => normalizeArabic(s).replace(HAMZA_SEATS, "ا");
const LETTERS = /^[\u0621-\u064A]+$/;
// ponytail: an entry's text is capped; the popup shows a clause of it, «المزيد» the whole entry in the book
const ENTRY_MAX = 4000;

/** A heading that names a root: «أج ر:», «(ء ب ب)», «باب الهمزة» is not one. */
export function rootOf(heading: string): string | undefined {
  const t = dictKey(heading.replace(/^\s*\u0645\u0642\u0644\u0648\u0628\u0647/, "").replace(/[()[\]{}:«»\-–—.،0-9٠-٩]/g, "").replace(/\s+/g, ""));
  return LETTERS.test(t) && t.length >= 2 && t.length <= 4 ? t : undefined;
}

/** A root's file: its code points in hex, so paths stay ASCII (DictionaryLookup.fileName must match). */
export const fileName = (root: string) => [...root].map((c) => c.charCodeAt(0).toString(16).padStart(4, "0")).join("");

const WEAK = new Set(["ا", "و", "ي"]);
/** The root's letters in order inside the word; a weak letter of the root may be gone or changed. */
export function fits(root: string, word: string): boolean {
  let i = 0;
  for (const r of root) {
    const j = word.indexOf(r, i);
    if (j < 0) { if (WEAK.has(r)) continue; return false; }
    i = j + 1;
  }
  return true;
}

/** Words the entry itself names: «(الأجر)», or a line that starts «أثاما:». */
export function formsIn(root: string, text: string): string[] {
  const out = new Set<string>();
  for (const m of text.matchAll(/\(([\u0621-\u0652\u0670]{2,20})\)/g)) out.add(dictKey(m[1]));
  for (const m of text.matchAll(/(?:^|\n)(?:\d+ - )?([\u0621-\u0652\u0670]{2,20}):/g)) out.add(dictKey(m[1]));
  return [...out].filter((w) => LETTERS.test(w) && fits(root, w));
}

// A proclitic (optionally after و/ف) and an enclitic; mirrors DictionaryLookup.roots in the app.
const PRE = ["", "بال", "كال", "لل", "ال", "ب", "ل", "ك", "س"].flatMap((p) => [p, `و${p}`, `ف${p}`]).map(dictKey);
const SUF = ["", "هما", "كما", "تما", "ها", "هم", "هن", "كم", "كن", "نا", "ون", "ين", "ان", "ات", "وا", "ه", "ي", "ك", "ت"].map(dictKey);
const AUG = new Set(dictKey("سالتمونيه"));

/**
 * The known root(s) a headword belongs to — for lexicons that mark entries «• جَبَأَ:» instead
 * of heading them by root. Listed forms first, else the fewest augment letters dropped.
 * ponytail: same rules as the app's DictionaryLookup.roots, duplicated; keep the two in step.
 */
export function resolveRoot(word: string, roots: Set<string>, forms: Map<string, Set<string>>): string[] {
  const w = dictKey(word).replace(/[^\u0621-\u064A]/g, "");
  if (w.length < 2) return [];
  const cands = new Set([w]);
  for (const p of PRE) if (w.startsWith(p)) for (const x of SUF) {
    const t = w.slice(p.length);
    if (t.endsWith(x) && t.length - x.length >= 2) cands.add(x ? t.slice(0, -x.length) : t);
  }
  for (const c of cands) if (roots.has(c)) return [c];
  for (const c of cands) for (const v of [c, dictKey("ال") + c]) { const r = forms.get(v); if (r) return [...r]; }
  let best = Infinity; const found = new Set<string>();
  const stems = [...cands, ...[...cands].filter((c) => c.length > 3 && dictKey("يتنا").includes(c[0])).map((c) => c.slice(1))];
  for (const c of stems) {
    if (c.length > 7) continue;
    const pick = (from: number, idx: number[], size: number) => {
      if (idx.length === size) {
        const r = idx.map((i) => c[i]).join(""), dropped = [...c].filter((_, i) => !idx.includes(i));
        if (roots.has(r) && dropped.every((d) => AUG.has(d))) {
          if (dropped.length < best) { best = dropped.length; found.clear(); }
          if (dropped.length === best) found.add(r);
        }
        return;
      }
      for (let i = from; i <= c.length - (size - idx.length); i++) pick(i + 1, [...idx, i], size);
    };
    for (let size = 3; size <= Math.min(4, c.length); size++) pick(0, [], size);
  }
  return [...found];
}

const BRACKETED = /^\s*[[(]\s*[\u0621-\u064A\u064B-\u0652 ]{2,12}\s*[\])]\s*$/;
const COLON = /^([\u0621-\u064A]{2,4}): /;
const COLON_STOP = new Set(["قال", "وقال", "قيل", "وقيل", "يقول", "يقال", "قوله", "وزاد", "عمرو", "عمر", "قلت", "وقلت", "قالوا"]);

function bodyOf(id: string): string | undefined {
  for (const dir of ["book-lg", "book"]) {
    const file = path.join("src/content", dir, `${id}.md`);
    if (existsSync(file)) return readFileSync(file, "utf8").replace(/^---\n[\s\S]*?\n---\n/, "");
  }
}

async function main() {
  const out = path.resolve(process.argv.find((a) => a.startsWith("--out="))?.slice(6) ?? "dist/app-dict");
  const dir = path.join(out, "dict");
  rmSync(dir, { recursive: true, force: true });
  mkdirSync(path.join(dir, "r"), { recursive: true });

  const sources: { id: string; title: string }[] = [];
  const entries = new Map<string, { s: number; o: number; x: string }[]>();
  const forms = new Map<string, Set<string>>();
  // «•» entries wait until every root-headed lexicon has named its roots.
  const bullets: { s: number; o: number; x: string; head: string }[] = [];
  const add = (root: string, e: { s: number; o: number; x: string }) => {
    const list = entries.get(root) ?? [];
    list.push(e);
    entries.set(root, list);
    for (const f of formsIn(root, e.x)) { const set = forms.get(f) ?? new Set(); set.add(root); forms.set(f, set); }
  };
  for (const d of DICTS) {
    const body = bodyOf(d.id);
    if (!body) { console.warn(`skip ${d.id}: not in src/content`); continue; }
    const s = sources.push({ id: d.id, title: d.title }) - 1;
    const { blocks } = parseReadableDocument({ coll: "book", id: d.id, body, sourcePath: d.id });
    let root: string | undefined, head: string | undefined, at = 0, text = "", count = 0;
    const flush = () => {
      if (!text) return;
      const e = { s, o: at, x: text.slice(0, ENTRY_MAX) };
      if (root) add(root, e); else if (head) bullets.push({ ...e, head }); else return;
      count++;
    };
    blocks.forEach((b, i) => {
      const x = ((b as { x?: string }).x ?? "").replace(/\u2997[^\u2998]*\u2998\s*/g, ""); // printed page marks ⦗١٤⦘
      const heading = /^h[1-6]$/.test(b.t);
      const bullet = !heading && /^\s*•\s*/.exec(x);
      // «[سعع]» alone on its line (الصحاح) is a root heading set as a paragraph.
      const bracketed = !heading && BRACKETED.test(x) ? rootOf(x) : undefined;
      const colon = d.mark === "colon" && !heading ? COLON.exec(x) : null;
      if (colon && !COLON_STOP.has(colon[1]) && rootOf(colon[1])) { flush(); root = rootOf(colon[1]); head = undefined; at = i; text = x; }
      else if (heading || bracketed) {
        flush(); root = bracketed ?? rootOf(x); at = i; text = "";
        head = !root && d.mark === "word" && /^\S+$/.test(x.replace(/[\u060C,].*$/, "").trim()) ? x.replace(/[\u060C,].*$/, "").trim() : undefined;
      }
      else if (bullet) { flush(); root = undefined; head = x.slice(bullet[0].length).split(/[\s:\u060C,]/)[0]; at = i; text = x.slice(bullet[0].length); }
      else if ((root || head) && x && text.length < ENTRY_MAX) text += (text ? "\n" : "") + x;
    });
    flush();
    console.log(`${d.title}: ${count} entries`);
  }

  const roots = new Set(entries.keys());
  let lost = 0;
  for (const { head, ...e } of bullets) {
    const r = resolveRoot(head, roots, forms)[0];
    if (!r) { lost++; continue; }
    add(r, e);
    const set = forms.get(dictKey(head)) ?? new Set(); set.add(r); forms.set(dictKey(head), set);
  }
  if (bullets.length) console.log(`• entries: ${bullets.length - lost} placed under a root, ${lost} headwords matched no known root (dropped)`);

  for (const [root, list] of entries) writeFileSync(path.join(dir, "r", `${fileName(root)}.json`), JSON.stringify(list));
  const index = {
    sources,
    roots: [...entries.keys()].sort(),
    forms: Object.fromEntries([...forms].sort(([a], [b]) => (a < b ? -1 : 1)).map(([f, r]) => [f, [...r]])),
  };
  writeFileSync(path.join(dir, "index.json"), JSON.stringify(index));
  // The app downloads the whole set once («تنزيل المعجم») and reads entries straight out of the zip.
  const zip = path.join(out, "dict.zip");
  rmSync(zip, { force: true });
  execFileSync("zip", ["-q", "-r", "-9", zip, "index.json", "r"], { cwd: dir });
  console.log(`dict: ${sources.length} sources, ${entries.size} roots, ${forms.size} forms → ${dir}`);
}

function selftest() {
  const eq = (a: unknown, b: unknown) => { if (JSON.stringify(a) !== JSON.stringify(b)) throw new Error(`${JSON.stringify(a)} != ${JSON.stringify(b)}`); };
  eq(rootOf("(ء ب ب)"), dictKey("ابب"));
  eq(rootOf("أج ر:"), dictKey("اجر"));
  eq(rootOf("باب الهمزة"), undefined);
  eq(fits("قول", "قال"), true);
  eq(fits("كتب", "مكتوب"), true);
  eq(fits("كتب", "كبت"), false);
  eq(formsIn(dictKey("اجر"), "(الأجر) الثواب\nأجورهم: ثوابهم و(الكتاب)"), [dictKey("الاجر"), dictKey("اجورهم")]);
  eq(fileName("علم"), "063906440645");
  const known = new Set(["جبا", "كتب", "قول"].map(dictKey));
  eq(resolveRoot("جَبَأَ", known, new Map()), [dictKey("جبا")]);
  eq(resolveRoot("وكَتَبَهُ", known, new Map()), [dictKey("كتب")]);
  eq(resolveRoot("مكتوبات", known, new Map()), [dictKey("كتب")]);
  console.log("✓ gen-app-dict selftest");
}

if (process.argv[1]?.endsWith("gen-app-dict.ts")) await (process.argv.includes("--selftest") ? selftest() : main());
