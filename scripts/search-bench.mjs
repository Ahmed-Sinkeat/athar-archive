#!/usr/bin/env node
// Accuracy + speed check of /api/search with the params the app sends. Exits 1 on any failure.
//   node scripts/search-bench.mjs [base]   (default: the live site; e.g. http://localhost:8799)
const BASE = (process.argv[2] ?? "https://athar.arthurarchive.com") + "/api/search";

// tashkeel/tatweel out, alef/yaa/taa-marbuta folded (escapes, not literal Arabic classes)
const norm = (s) => s.replace(/[ؐ-ًؚ-ٰٟۖ-ۭـ]/g, "")
  .replace(/[آأإٱ]/g, "ا").replace(/ى/g, "ي").replace(/ة/g, "ه");
const noMarks = (s) => s.replace(/[ؐ-ًؚ-ٰٟۖ-ۭـ]/g, "");
const plain = (s) => s.replace(/<\/?mark>/g, "");
const AR = "ء-ي";
const hasWords = (text, q) => norm(q).split(" ").every((w) => new RegExp(`(^|[^${AR}])${w}($|[^${AR}])`).test(norm(text)));

async function get(params) {
  const t = performance.now();
  const r = await fetch(`${BASE}?${new URLSearchParams(params)}`);
  return { ms: Math.round(performance.now() - t), status: r.status, body: await r.json() };
}
const P = (q, extra = {}) => ({ q, mode: "all", field: "all", type: "", sort: "relevance", snip: "48", offset: "0", ...extra });

// [query, mode, a title (folded substring) that must be in the top 3, or "" for none]
const CASES = [
  ["يقول راجي", "all", "تحفه الاطفال"],
  ["زاد المستقنع", "all", "زاد المستقنع"],
  ["بلوغ المرام", "all", "بلوغ المرام"],
  ["عمدة الأحكام", "all", "عمده الاحكام"],
  ["كتاب التوحيد", "all", "كتاب التوحيد"],
  ["الآجرومية", "all", "الاجروميه"],
  ["إنما الأعمال بالنيات", "all", ""],
  ["إنما الأعمال بالنيات", "phrase", ""],
  ["لا إله إلا الله", "all", ""],
  ["أهل السنة والجماعة", "any", ""],
  ["الصلاة", "all", ""],
];

const rows = [];
let failed = 0;
for (const [q, mode, want] of CASES) {
  const fails = [];
  const a = await get(P(q, { mode }));
  const hits = a.body.hits ?? [];
  if (a.status !== 200 || !hits.length) fails.push(`status ${a.status}, ${hits.length} hits`);
  // tiers never go backwards: exact phrase, then every whole word, then the rest (judged on the
  // title + full passage, since a long page's snippet can't always hold every word)
  if (mode !== "any") {
    let lead = 0;
    while (lead < 3 && hits[lead] && norm(hits[lead].title).startsWith(norm(q))) lead++;
    let prev = 0;
    for (const [i, h] of hits.entries()) {
      if (i < lead) continue;
      // as FTS sees it: words only, punctuation between them ignored
      const words = (s) => " " + norm(s).split(/[^\u0621-\u064a]+/).join(" ") + " ";
      const text = words(h.title + " " + ((await get({ row: String(h.row) })).body.text ?? ""));
      const tier = text.includes(words(q)) ? 0 : hasWords(text, q) ? 1 : 2;
      if (tier < prev) { fails.push(`#${i + 1} is tier ${tier} after tier ${prev}`); break; }
      prev = tier;
    }
  }
  if (want && !hits.slice(0, 3).some((h) => norm(h.title).includes(want))) fails.push(`«${want}» not in top 3 (top: ${hits[0]?.title})`);
  // typed spelling (a hamza) wins over the folded one
  if (q !== norm(q) && hits[0] && !noMarks(plain(hits[0].snippet)).includes(noMarks(q).split(" ")[0])) fails.push(`top hit lacks the typed spelling`);
  const key = (h) => h.url + "|" + h.snippet;
  if (new Set(hits.map(key)).size !== hits.length) fails.push("duplicate hits on page 1");
  const b = await get(P(q, { mode, offset: String(hits.length) }));
  const overlap = (b.body.hits ?? []).filter((h) => hits.some((x) => key(x) === key(h))).length;
  if (overlap) fails.push(`${overlap} page-2 hits repeat page 1`);
  // «المزيد»: the full passage holds the shown words, as written
  let fullMs = 0;
  if (hits[0]?.row) {
    const f = await get({ row: String(hits[0].row) });
    fullMs = f.ms;
    const shown = plain(hits[0].snippet).replace(/^…|…$/g, "").trim();
    if (!f.body.text?.includes(shown)) fails.push("full passage lacks the shown snippet");
  } else if (hits[0]) fails.push("hit has no row");
  failed += fails.length;
  rows.push({ q, mode, ms: a.ms, page2Ms: b.ms, fullMs, n: hits.length, top: hits[0]?.title.slice(0, 32), ok: fails.length ? "FAIL: " + fails.join("; ") : "ok" });
}
console.table(rows);
const ms = rows.map((r) => r.ms).sort((x, y) => x - y);
console.log(`speed: p50 ${ms[ms.length >> 1]} ms, max ${ms.at(-1)} ms · ${failed} failure(s)`);
process.exit(failed ? 1 : 0);
