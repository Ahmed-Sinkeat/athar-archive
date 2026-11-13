// Qafiyah (github.com/WTFoss/qafiyah, MIT) → one ديوان book per poet.
//   tsx scripts/import-qafiyah.ts --db q.db [--write] [--review docs/qafiyah-review.tsv]
// q.db: the project's Postgres dump loaded into SQLite (tables poems, poets, eras, meters, rhymes).
// Dry-run by default: prints what would be written. Poets on the lists below are skipped whole;
// poems hitting FLAG words are held back and listed in --review for the owner to judge.
// decisions live in the constants here; once reviewed poems need restoring, add an --allow file.
import { DatabaseSync } from "node:sqlite";
import fs from "node:fs";
import path from "node:path";
import { slugify, y } from "./lib/slugify.ts";
import { verseHalves } from "../src/lib/app-v2/parser.ts";

const arg = (name: string) => { const i = process.argv.indexOf(name); return i > 0 ? process.argv[i + 1] : undefined; };
const DB = arg("--db"), WRITE = process.argv.includes("--write"), REVIEW = arg("--review");
if (!DB) throw new Error("--db <qafiyah sqlite> is required");

const norm = (s: string) => s.replace(/[ً-ْٰـ]/g, "").replace(/[أإآٱ]/g, "ا").replace(/ى/g, "ي").replace(/ة/g, "ه")
  .replace(/[^ء-ي ]/g, " ").replace(/\s+/g, " ").trim();

// مجون وفحش، ومن هجا النبي ﷺ (agreed with the owner 2026-11-12).
const EXCLUDE = ["أبو نواس", "الحسين بن الضحاك", "ابن سكرة", "ابن حجاج", "أبو الهندي", "ديك الجن", "بشار بن برد",
  "يحيى بن زياد الحارثي", "جحظة البرمكي", "ابن الهبارية", "أبو الرقعمق", "أبو العنبس الصيمري", "أبو الشمقمق",
  "أبو دُلامة", "الخبز أرزي", "الواساني", "الوليد بن يزيد", "الأقيشر الأسدي", "عمر بن أبي ربيعة", "العرجي",
  "الأحوص الأنصاري", "وضاح اليمن", "الحكم بن عبدل الأسدي", "الأخطل", "ابن عنين", "الملك الأمجد",
  "ابن دانيال الموصلي", "ابن سودون", "أبو الحسين الجزار", "الشاب الظريف", "النبهاني العماني", "الستالي",
  "أحمد البهلول", "عبد الله بن الزبعرى", "ضرار الفهري", "أبو عفك", "هند بنت عتبة",
  // from their qafiyah bios (إلحاد، مجون، خلاعة، لهو وخمر):
  "ابن دنينير", "أبو علي البصير", "عمار ذو كبار", "عمرو الوراق", "ابن سهل الأندلسي"];
// زندقة أو تهمة بها، باطنية، وحدة الوجود والغلو، رفض، خوارج، نصارى: not now, decided one by one later.
const HOLD = ["أبو العلاء المعري", "المتنبي", "ابن الرومي", "الحلاج", "صالح بن عبد القدوس", "سلم الخاسر", "أبان الالحقي",
  "عبد الله بن معاوية", "السهروردي المقتول", "ابن سينا", "عمر الخيام", "جميل صدقي الزهاوي", "المكزون السنجاري",
  "تميم الفاطمي", "ابن هانئ الأندلسي", "محيي الدين بن عربي", "ابن الفارض", "العفيف التلمساني", "أبو الحسن الششتري",
  "عبد الغني النابلسي", "البوصيري", "البرعي", "ابن علوي الحداد", "أبو بكر العيدروس", "ابن عطاء الله المصري",
  "السيد الحميري", "دعبل الخزاعي", "مهيار الديلمي", "الشريف الرضي", "الشريف المرتضى", "الصاحب بن عباد",
  "صفي الدين الحلي", "بهاء الدين العاملي", "الحر العاملي", "إبراهيم بن يحيى العاملي", "جعفر كاشف الغطاء",
  "محمد سعيد الحبوبي", "ابن معتوق", "ابن معصوم", "قطري بن الفجاءة", "الطرماح", "نافع الحروري", "جرمانوس فرحات",
  "نيقولاوس الصائغ", "نقولا النقاش", "بطرس البستاني", "إلياس إده", "يوسف باخوس",
  // from their qafiyah bios (باطنية، تهمة بالزندقة، خوارج):
  "ابن حيون", "علي بن جبلة - العكوك", "كلثوم العتابي", "الطغرائي", "لسان الدين بن الخطيب", "ذو النون المصري",
  "حبيب الهلالي", "عبيدة اليشكري", "يزيد بن حبناء", "بن أبي الحوساء"];
// Names poetry is commonly fathered on (منحول): last, after the rest is in.
const LAST = ["علي بن أبي طالب", "الإمام الشافعي", "الحسين بن علي", "معاوية بن أبي سفيان", "يزيد بن معاوية",
  "أبو بكر الصديق", "أبو طالب", "عبد الله بن المبارك", "الخليل الفراهيدي", "الغزالي", "فاطمة الزهراء", "ورقة بن نوفل",
  "قس بن ساعدة", "عمار بن ياسر", "خالد بن الوليد", "علي بن الحسين", "الزبير بن العوام", "عائشة بنت أبي بكر",
  "آمنة بنت وهب", "برة بنت عبد المطلب", "أميمة بنت عبد المطلب", "هارون الرشيد", "المأمون", "الأمين العباسي",
  "ابن تيمية", "ابن قيم الجوزية", "قيس بن الملوح", "ليلى العامرية"];
const SKIP_ERAS = new Set(["متأخر"]); // modern poets: not now
// Poets whose poems we already carry in our own edition (diwan-ilbiri--N covers all of Qafiyah's الإلبيري).
const OURS = new Set(["أبو إسحاق الإلبيري"].map(norm));
// Flagged poems read and released (the word is incidental: مدح/رثاء/زهد, «نهد» a horse, «زنا» in «وزنا»…).
// ids inline; move to a file if the review of docs/qafiyah-review.tsv releases many.
const ALLOW = new Set([
  "43220", "43357", "42885", // أبو العتاهية
  "43585", "43621", "43654", "44014", // أبو الفتح البستي
  "46195", "46208", "46170", "46220", "46230", "46231", "46274", "46286", "46363", "46335", "46642", "46348", "46173",
  "46190", "46344", // أبو تمام
]);
const ERA: Record<string, string> = { "جاهلي": "الجاهلي", "مخضرم": "صدر الإسلام", "إسلامي": "صدر الإسلام", "أموي": "الأموي",
  "عباسي": "العباسي", "أندلسي": "الأندلسي", "أيوبي": "المتأخّر", "مملوكي": "المتأخّر", "عثماني": "المتأخّر" };
// خمر، فحش، غزل بالمذكر، أوثان: a hit holds the poem for review, it does not condemn it.
const FLAG = new RegExp(`(?:^| )(?:و|ف|ب|ل|ك)?(?:ال)?(${["خمر", "خمور", "مدامه", "صهباء", "سلاف", "نديم", "ندمان", "ندامي",
  "سكران", "سكاري", "دنان", "حانه", "خماره", "امرد", "عذار", "زنا", "لواط", "نهد", "نهود", "ردف", "ارداف", "لات", "عزي"]
  .join("|")})(?= |$)`);

const lists = new Map<string, string>();
for (const [list, names] of [["exclude", EXCLUDE], ["hold", HOLD], ["last", LAST]] as const)
  for (const name of names) lists.set(norm(name), list);

const db = new DatabaseSync(DB, { readOnly: true });
const rows = db.prepare(`select m.id, m.title, m.content, p.name poet, p.bio, e.name era, mt.name meter, r.pattern rhyme
  from poems m join poets p on p.id = m.poet_id join eras e on e.id = p.era_id
  left join meters mt on mt.id = m.meter_id left join rhymes r on r.id = m.rhyme_id
  order by p.name, r.pattern, cast(m.id as int)`).all() as Record<string, string>[];
const known = new Set(lists.keys());
const missing = [...known].filter((n) => !rows.some((r) => norm(r.poet) === n));
if (missing.length) console.warn("list names with no poet in the dump:", missing.join("، "));

// Our people, by title and also_known_as; and the poems we already carry, by their opening hemistich.
const root = process.cwd(), personDir = path.join(root, "src/content/person");
const people = new Map<string, string>();
for (const f of fs.readdirSync(personDir)) {
  const text = fs.readFileSync(path.join(personDir, f), "utf8"), id = f.replace(/\.md$/, "");
  const title = text.match(/^title:\s*"?(.+?)"?\s*$/m)?.[1];
  if (title) people.set(norm(title), id);
  for (const aka of JSON.parse(text.match(/^also_known_as:\s*(\[.*\])\s*$/m)?.[1] ?? "[]")) people.set(norm(aka), id);
}
const opening = (verse: string) => norm(verse.replace(/^[0-9٠-٩]+\s*-\s*/, "").split(/\s(?:…|\.\.\.)\s/)[0]).slice(0, 30);
const ours = new Set<string>();
for (const f of fs.readdirSync(path.join(root, "src/content/poem"))) {
  const body = fs.readFileSync(path.join(root, "src/content/poem", f), "utf8").split(/^---$/m)[2] ?? "";
  const first = body.split("\n").find((l) => /…|\.\.\./.test(l));
  if (first) ours.add(opening(first));
}

const halves = (content: string) => content.split("*").map((h) => h.trim()).filter(Boolean);
const verses = (content: string) => {
  const h = halves(content), out: string[] = [];
  for (let i = 0; i < h.length; i += 2) out.push(h[i + 1] ? `${h[i]} … ${h[i + 1]}` : h[i]);
  return out;
};

const count = { exclude: 0, hold: 0, last: 0, era: 0, flagged: 0, ours: 0, dup: 0, kept: 0, prose: 0, verses: 0 };
const review: string[] = ["poet\tpoem_id\tword\ttitle\tfirst_verse"];
const diwans = new Map<string, { poet: string; era: string; bio: string; poems: Record<string, string>[]; seen: Set<string> }>();
for (const row of rows) {
  const list = lists.get(norm(row.poet));
  if (list) { count[list as "exclude"]++; continue; }
  if (SKIP_ERAS.has(row.era) || OURS.has(norm(row.poet))) { count.era++; continue; }
  const vs = verses(row.content);
  if (!vs.length) continue;
  const hit = norm(row.content.replace(/\*/g, " ")).match(FLAG);
  if (hit && !ALLOW.has(String(row.id))) { count.flagged++; review.push([row.poet, row.id, hit[1], row.title, vs[0]].join("\t")); continue; }
  if (ours.has(opening(vs[0]))) { count.ours++; continue; }
  const key = norm(row.poet); // qafiyah lists a few poets twice (أبو الحسن السلامي، صاعد البغدادي…): one ديوان each
  const diwan = diwans.get(key) ?? { poet: row.poet, era: row.era, bio: row.bio ?? "", poems: [], seen: new Set<string>() };
  const text = norm(row.content);
  if (diwan.seen.has(text)) { count.dup++; continue; }
  diwan.seen.add(text);
  diwan.poems.push(row);
  diwans.set(key, diwan);
  count.kept++;
  count.verses += vs.length;
  count.prose += vs.filter((v) => !verseHalves(v)).length;
}

const today = "2026-11-12";
let newPeople = 0, bytes = 0;
const taken = new Set(fs.readdirSync(personDir).map((f) => f.replace(/\.md$/, "")));
for (const diwan of diwans.values()) {
  let person = people.get(norm(diwan.poet));
  if (!person) {
    let slug = slugify(diwan.poet, "shair");
    while (taken.has(slug)) slug += "-shair";
    taken.add(slug);
    person = slug;
    newPeople++;
    if (WRITE) fs.writeFileSync(path.join(personDir, `${slug}.md`),
      `---\ntitle: ${y(diwan.poet)}\nstatus: published\npublished_at: ${today}\nera: ${y(ERA[diwan.era])}\n---\n${diwan.bio.trim()}\n`);
  }
  const body = diwan.poems.map((p) =>
    `## ${p.title.trim()}\n\n${p.meter ? `من ${/^بحر|^مشطور|^مجزوء|^مخلع|^أحذ|^موشح/.test(p.meter) ? p.meter : `البحر ${p.meter}`}\n\n` : ""}${verses(p.content).join("\n")}\n`).join("\n");
  const id = `diwan-${person}`;
  const file = `---\ntitle: ${y(`ديوان ${diwan.poet}`)}\nstatus: published\npublished_at: ${today}\nperson: ${person}\n` +
    `work_type: "ديوان"\ntopics: ["al-adab"]\ndescription: ${y(`ديوان ${diwan.poet}: ${diwan.poems.length} قصيدة ومقطوعة، من مشروع قافية`)}\n---\n\n${body}`;
  bytes += Buffer.byteLength(file);
  const dir = Buffer.byteLength(file) >= 100 * 1024 ? "book-lg" : "book";
  const target = path.join(root, "src/content", dir, `${id}.md`);
  if (fs.existsSync(path.join(root, "src/content/book", `${id}.md`)) || fs.existsSync(path.join(root, "src/content/book-lg", `${id}.md`)))
    throw new Error(`${id} already exists — refusing to overwrite`);
  if (WRITE) fs.writeFileSync(target, file);
}
if (REVIEW) fs.writeFileSync(REVIEW, review.join("\n") + "\n");

console.log({ ...count, diwans: diwans.size, newPeople, matchedPeople: diwans.size - newPeople, mb: +(bytes / 1e6).toFixed(1),
  proseShare: `${((count.prose / count.verses) * 100).toFixed(1)}% of verses would not render as verse`, write: WRITE });
