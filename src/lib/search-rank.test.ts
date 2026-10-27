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
    expect(tiers).toEqual([[2], [3], [4]]);
    expect(tiers.flat()).not.toContain(1); // راجينا alone: no يقول at all
  });
  it("any mode adds single words after every all-words tier", () => {
    expect(tierExprs(["يقول", "راجي"], "any").map(ids).flat()).toEqual([2, 3, 4, 1]);
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
