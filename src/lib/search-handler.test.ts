import { DatabaseSync } from "node:sqlite";
import { afterEach, describe, expect, it, vi } from "vitest";

// The real handler over a small FTS5 index in node:sqlite, shaped like D1's (gen-search-index.ts).
const db = new DatabaseSync(":memory:");
db.exec(`CREATE VIRTUAL TABLE docs USING fts5(title, text, type UNINDEXED, book UNINDEXED, person UNINDEXED, url UNINDEXED,
  display_title UNINDEXED, raw UNINDEXED, tokenize='unicode61 remove_diacritics 2', prefix='3 4');
  CREATE TABLE doc_meta (rowid INTEGER PRIMARY KEY, type TEXT, book TEXT, person TEXT, url TEXT, prior REAL NOT NULL DEFAULT 1);
  CREATE TABLE person_meta (slug TEXT PRIMARY KEY, name TEXT, death_year INTEGER);
  CREATE TABLE work_topic (book TEXT NOT NULL, topic TEXT NOT NULL, PRIMARY KEY (topic, book));
  CREATE TABLE work_edition (book TEXT PRIMARY KEY, work TEXT NOT NULL);`);
// 25 passages per tier: the phrase, both words close, both words far apart, words that only start the same.
const filler = Array.from({ length: 20 }, (_, i) => `كلمه${i}`).join(" ");
const texts = [
  ...Array.from({ length: 25 }, (_, i) => `باب ${i} طلب علم نافع ${filler}`),
  ...Array.from({ length: 25 }, (_, i) => `باب ${i} علم هو نافع ${filler}`),
  ...Array.from({ length: 25 }, (_, i) => `باب ${i} علم ${filler} نافع`),
  ...Array.from({ length: 25 }, (_, i) => `باب ${i} علمه ${filler} نافعه`),
];
texts.forEach((text, i) => {
  db.prepare("INSERT INTO docs (rowid, title, text, type, book, person, url, display_title, raw) VALUES (?, ?, ?, 'book', ?, '', ?, ?, NULL)")
    .run(i + 1, `كتاب ${i}`, text, `b${i}`, `/book/b${i}`, `كتاب ${i}`);
  db.prepare("INSERT INTO doc_meta (rowid, type, book, person, url) VALUES (?, 'book', ?, '', ?)").run(i + 1, `b${i}`, `/book/b${i}`);
});
// One passage in a book and in its second edition.
for (const [rowid, book] of [[101, "e0"], [102, "e1"]] as const) {
  db.prepare("INSERT INTO docs (rowid, title, text, type, book, person, url, display_title, raw) VALUES (?, ?, ?, 'book', ?, '', ?, ?, NULL)")
    .run(rowid, "كتاب الزهد", `باب الزهد في الدنيا زهد مستقيم ${filler}`, book, `/book/${book}`, "كتاب الزهد");
  db.prepare("INSERT INTO doc_meta (rowid, type, book, person, url) VALUES (?, 'book', ?, '', ?)").run(rowid, book, `/book/${book}`);
}
db.exec("INSERT INTO work_edition (book, work) VALUES ('e1', 'e0')");
const d1 = {
  prepare: (sql: string) => ({ bind: (...v: (string | number)[]) => ({ all: async () => ({ results: db.prepare(sql).all(...v) }) }) }),
};
vi.mock("cloudflare:workers", () => ({ env: { SEARCH: d1 } }));
const { searchResponse } = await import("./search-handler.js");

type Page = { hits: { url: string }[]; hasMore: boolean; next?: string };
const page = async (extra: string): Promise<Page> =>
  (await searchResponse(new URL(`https://x/api/search?q=${encodeURIComponent("علم نافع")}&field=text&mode=all${extra}`))).json();
async function walk(by: "offset" | "after") {
  const seen: string[] = [];
  let p = await page("");
  for (;;) {
    seen.push(...p.hits.map((h) => h.url));
    if (!p.hasMore) return seen;
    p = await page(by === "offset" ? `&offset=${seen.length}` : `&after=${p.next}`);
  }
}

describe("search paging", () => {
  afterEach(() => vi.restoreAllMocks());

  it("a later page carries on from where the last stopped: same rows as offset, each once", async () => {
    const byOffset = await walk("offset");
    expect(byOffset.length).toBe(100);
    expect(await walk("after")).toEqual(byOffset);
  });

  it("a passage in two editions of one work shows once", async () => {
    const p = (await (await searchResponse(new URL(`https://x/api/search?q=${encodeURIComponent("زهد مستقيم")}&field=text&mode=all`))).json()) as Page;
    expect(p.hits.map((h) => h.url)).toEqual(["/book/e0"]);
  });

  it("a spent time budget ends the page early and the next one loses nothing", async () => {
    const byOffset = await walk("offset");
    let t = 0;
    vi.spyOn(Date, "now").mockImplementation(() => (t += 2000));
    const pages: number[] = [];
    const seen: string[] = [];
    let p = await page("");
    for (;;) {
      pages.push(p.hits.length);
      seen.push(...p.hits.map((h) => h.url));
      if (!p.hasMore) break;
      p = await page(`&after=${p.next}`);
    }
    expect(pages.some((n) => n < 20)).toBe(true);
    expect(seen).toEqual(byOffset);
  });
});
