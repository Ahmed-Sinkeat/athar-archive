import { describe, expect, it } from "vitest";
import { DatabaseSync } from "node:sqlite";
import { exactSnippet, hasTypedSpelling, tierExprs, titleStartExpr, typedSpelling } from "./search-rank.js";
import { normalizeArabic } from "./ar-normalize.js";

describe("search tiers", () => {
  // A tiny FTS5 index with the live schema's two searchable columns.
  const db = new DatabaseSync(":memory:");
  db.exec("CREATE VIRTUAL TABLE docs USING fts5(title, text, raw UNINDEXED)");
  const rows = [
    ["شرح الفية ابن مالك", "كرب راجينا عظيم الامل"],
    ["تحفه الاطفال", "يقول راجي رحمه الغفور"],
    ["كتاب", "راجي ثم يقول بعد ذلك"],
    ["كتاب", "يقولون انه راجيا"],
  ];
  for (const [title, text] of rows) db.prepare("INSERT INTO docs (title, text) VALUES (?, ?)").run(title, text);
  const ids = (expr: string) => db.prepare("SELECT rowid FROM docs WHERE docs MATCH ? ORDER BY rank").all(expr).map((r) => Number(r.rowid));

  it("puts the exact phrase first and prefix-only matches last, never twice", () => {
    const tiers = tierExprs(["يقول", "راجي"], "all").map(ids);
    expect(tiers).toEqual([[2], [3], [], [4]]); // phrase, near (row 3 is exact-word AND, 5 apart… caught by near), all-words, prefix
    expect(tiers.flat()).not.toContain(1); // راجينا alone: no يقول at all
  });
  it("any mode adds single words after every all-words tier", () => {
    expect(tierExprs(["يقول", "راجي"], "any").map(ids).flat()).toEqual([2, 3, 4, 1]);
  });
  it("a long query's every-word tiers skip particles, the phrase keeps them", () => {
    const [phrase, near, all] = tierExprs(["من", "ارتد", "ثم", "تاب", "حبط"], "all");
    expect(near).toContain('NEAR("ارتد" "تاب" "حبط"');
    expect(phrase).toBe('"من ارتد ثم تاب حبط"');
    expect(all).toContain('"ارتد" AND "تاب" AND "حبط"');
    expect(all).not.toContain('"من" AND');
    expect(tierExprs(["من", "ثم"], "all")[2]).toContain('"من" AND "ثم"'); // short query: unchanged
  });
  it("words close together come before the same words far apart", () => {
    const d = new DatabaseSync(":memory:");
    d.exec("CREATE VIRTUAL TABLE docs USING fts5(title, text)");
    const far = "ارتد " + "كلمه ".repeat(40) + "تاب " + "كلمه ".repeat(40) + "حبط";
    d.prepare("INSERT INTO docs (title, text) VALUES (?, ?)").run("a", far);
    d.prepare("INSERT INTO docs (title, text) VALUES (?, ?)").run("b", "من كان مومنا ثم ارتد ثم تاب حبط عمله");
    const tiers = tierExprs(["ارتد", "تاب", "حبط"], "all").map((e) => d.prepare("SELECT rowid FROM docs WHERE docs MATCH ?").all(e).map((r) => Number(r.rowid)));
    expect(tiers.flat()).toEqual([2, 1]); // near first, far after, never twice
  });
  it("finds a title that starts with the words", () => {
    db.prepare("INSERT INTO docs (title, text) VALUES (?, ?)").run("زاد المستقنع في اختصار المقنع", "");
    db.prepare("INSERT INTO docs (title, text) VALUES (?, ?)").run("شرح زاد المستقنع", "");
    expect(ids(titleStartExpr(["زاد", "المستقنع"]))).toEqual([5]);
  });
});

describe("exactSnippet", () => {
  const raw = "قال النبي صلى الله عليه وسلم: «إِنَّمَا الأعمال بالنيات» رواه البخاري";
  it("shows the words as written, with the same marks", () => {
    const folded = "…" + normalizeArabic("وسلم: «") + "<mark>" + normalizeArabic("إنما الأعمال") + "</mark> " + normalizeArabic("بالنيات» رواه") + "…";
    expect(exactSnippet(folded, raw)).toBe("…وسلم: «<mark>إِنَّمَا الأعمال</mark> بالنيات» رواه…");
  });
  it("keeps the folded snippet when the passage is missing", () => {
    expect(exactSnippet("<mark>انما</mark>", null)).toBe("<mark>انما</mark>");
  });
});

describe("typed spelling", () => {
  it("prefers إنما only when the hamza was typed", () => {
    expect(typedSpelling("انما", ["انما"])).toBeNull();
    const words = typedSpelling("إنما الأعمال", ["انما", "الاعمال"])!;
    expect(hasTypedSpelling("«<mark>إِنَّمَا الأعمال</mark>»", words)).toBe(true);
    expect(hasTypedSpelling("«<mark>انما الاعمال</mark>»", words)).toBe(false);
  });
});

describe("root mode", () => {
  it("matches any form, exact first then prefix", () => {
    expect(tierExprs(["كتب", "كاتب", "في"], "root")).toEqual([
      '"كتب" OR "كاتب" OR "في"',
      '("كتب"* OR "كاتب"* OR "في") NOT ("كتب" OR "كاتب" OR "في")',
    ]);
  });
});
