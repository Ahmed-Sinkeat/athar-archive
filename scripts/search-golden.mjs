// The search golden list (plan 8.8): 20 queries and, where there is one, the source that must be in
// the first three. 8.2–8.4 (reindex, editions, ranking) are judged against it.
//   node scripts/search-golden.mjs [base] [--fresh]
// base defaults to the live Worker; --fresh adds a cache-buster (measures the uncached path).
// One query at a time with a pause: a burst of uncached heavy queries is what overloads D1.
// Exits 1 when an expected source is missing from the top three or a query fails.
const base = process.argv.slice(2).find((a) => !a.startsWith("--")) ?? "https://search.arthurarchive.com/api/search";
const fresh = process.argv.includes("--fresh");

// [query, mode, sources that count (book slug, url part or author name), what it is for]
const GOLDEN = [
  ["من أرتد ثم تاب حبط", "all", ["majmu-al-fatawa"], "the phase's acceptance case: ابن تيمية's passage"],
  ["إنما الأعمال بالنيات", "all", ["sahih-bukhari", "al-arbaeen-al-nawawiyyah"], "a hadith: its sources, not a modern book"],
  ["إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ", "all", ["sahih-bukhari", "al-arbaeen-al-nawawiyyah"], "same, typed with tashkeel"],
  ["انما الاعمال بالنيه", "all", ["sahih-bukhari", "al-arbaeen-al-nawawiyyah"], "same, typed loosely"],
  ["الأصول الثلاثة", "all", ["al-usul-al-thalathah"], "a متن by its title"],
  ["ثلاثة الأصول", "all", ["al-usul-al-thalathah", "thalathat-al-usul"], "the title the other way round"],
  ["ابن تيمية", "all", ["ابن تيمية"], "a name: his own books before books about him"],
  ["البربهاري", "all", ["sharh-al-sunnah-al-barbahari"], "a name: his book before a nisba entry"],
  ["شرح السنة", "all", ["sharh-al-sunnah"], "a title shared by several books"],
  ["الصلاة", "all", [], "a very common word: answers, fast"],
  ["حكم صلاة الجماعة", "all", [], "a question"],
  ["قل هو الله أحد", "all", [], "an ayah (no Quran in the index yet)"],
  ["الإيمان قول وعمل يزيد وينقص", "phrase", [], "a phrase"],
  ["وقال رسول الله صلى الله عليه وسلم", "all", [], "a long common phrase: must not take search down"],
  ["اعتزل ذكر الأغاني والغزل", "all", ["lamiyyat-ibn-al-wardi"], "a verse: its poem"],
  ["الصبر", "any", [], "one word, «بعض الكلمات»"],
  ["المؤمنون", "all", [], "a common word"],
  ["كمبيوتر", "all", [], "a modern word: few or no hits, no error"],
  ["tawhid", "all", [], "Latin letters"],
  ["نخبة الفكر", "all", ["nukhbat-al-fikar"], "a متن by its title"],
];

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let failed = 0;
for (const [q, mode, expect, why] of GOLDEN) {
  const u = new URL(base);
  for (const [k, v] of Object.entries({ q, mode, field: "all", snip: "48" })) u.searchParams.set(k, v);
  if (fresh) u.searchParams.set("_", String(Math.random()).slice(2, 10));
  const t = Date.now();
  let res;
  try {
    res = await fetch(u, { headers: { "user-agent": "athar-golden" } });
  } catch (e) {
    console.log(`FAIL ${q}: ${e}`);
    failed++;
    continue;
  }
  const ms = Date.now() - t;
  if (!res.ok) {
    console.log(`FAIL ${q}: HTTP ${res.status} (${ms} ms)`);
    failed++;
    await sleep(1500);
    continue;
  }
  const { hits = [] } = await res.json();
  const top = hits.slice(0, 3);
  const has = (h) => expect.some((e) => [h.book, h.url, h.person_name].some((f) => f?.includes(e)));
  const at = hits.findIndex(has) + 1;
  const works = hits.map((h) => h.book || h.url);
  const repeats = works.length - new Set(works).size;
  const ok = !expect.length || top.some(has);
  if (!ok) failed++;
  console.log(`${ok ? "ok  " : "MISS"} ${q} [${mode}] ${ms} ms, ${hits.length} hits, same work repeated ${repeats}` +
    (expect.length ? `, expected at ${at || "-"}` : "") + ` — ${why}`);
  for (const h of top) console.log(`       ${h.type} ${h.title.slice(0, 60)}${h.person_name ? ` · ${h.person_name}` : ""}`);
  await sleep(1500);
}
console.log(failed ? `\n${failed} of ${GOLDEN.length} failed` : `\nall ${GOLDEN.length} pass`);
process.exit(failed ? 1 : 0);
