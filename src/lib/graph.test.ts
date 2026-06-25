import { describe, it, expect } from "vitest";
import { buildGraph, parseDuration, formatDuration } from "./graph.js";
import { loadContentFromDisk } from "./load.js";
import type { ContentEntry } from "./types.js";

function e(collection: string, id: string, data: Record<string, unknown>, body = ""): ContentEntry {
  return { collection, id, data: { status: "published", ...data }, body };
}

function smallCorpus(): ContentEntry[] {
  return [
    e("person", "p1", { title: "شخص" }),
    e("subject", "aqeedah", { title: "العقيدة" }),
    e("topic", "t1", { title: "موضوع", subject: "aqeedah" }),
    e("book", "bk1", { title: "كتاب التوحيد", person: "p1", topics: ["t1"] }),
    e("book", "bk2", { title: "فتح المجيد", person: "p1", topics: ["t1"], sharh_of: "bk1" }),
    e("article", "ar1", { title: "مقالة", person: "p1", topics: ["t1"] }),
    e("benefit", "bn1", { title: "فائدة", person: "p1", source_type: "book", source_id: "bk1" }),
    e("audio", "au1", { title: "صوت", source_type: "book", source_id: "bk1", url: "x" }),
    e("annotation", "bk1--p1--h", { title: "شرح", target_type: "book", target_id: "bk1", anchor: "p1" }),
  ];
}

describe("duration helpers", () => {
  it("parses h:mm:ss and mm:ss to seconds", () => {
    expect(parseDuration("1:12:34")).toBe(4354);
    expect(parseDuration("58:20")).toBe(3500);
    expect(parseDuration(undefined)).toBe(0);
  });

  it("formats seconds back, dropping the hour when zero", () => {
    expect(formatDuration(4354)).toBe("1:12:34");
    expect(formatDuration(3500)).toBe("58:20");
  });
});

describe("graph indices", () => {
  const g = buildGraph(smallCorpus());

  it("indexes materials by topic", () => {
    const ids = g.materialsByTopic("t1").map((m) => m.id).sort();
    expect(ids).toEqual(["ar1", "bk1", "bk2"]);
  });

  it("indexes materials by subject via its topics", () => {
    const ids = g.materialsBySubject("aqeedah").map((m) => m.id).sort();
    expect(ids).toEqual(["ar1", "bk1", "bk2"]);
  });

  it("indexes materials by person", () => {
    const collections = g.materialsByPerson("p1").map((m) => m.collection).sort();
    expect(collections).toContain("book");
    expect(collections).toContain("article");
  });

  it("resolves commentariesOf (شروح reverse edge)", () => {
    expect(g.commentariesOf("bk1").map((b) => b.id)).toEqual(["bk2"]);
  });

  it("resolves reverse polymorphic lookups", () => {
    expect(g.audioForSource("book", "bk1").map((a) => a.id)).toEqual(["au1"]);
    expect(g.benefitsForSource("book", "bk1").map((b) => b.id)).toEqual(["bn1"]);
    expect(g.annotationsForTarget("book", "bk1").map((a) => a.id)).toEqual(["bk1--p1--h"]);
  });
});

describe("backlinks (ما يشير إلى هذا)", () => {
  const corpus = smallCorpus();
  corpus.push(e("article", "ar2", { title: "إشارة", person: "p1" }, "انظر [[book:bk1]] للتفصيل"));
  const g = buildGraph(corpus);

  it("collects reverse references to a book (annotation, benefit, شرح, wiki-link)", () => {
    const byId = Object.fromEntries(g.backlinksFor("book", "bk1").map((r) => [r.entry.id, r.relation]));
    expect(byId["bk1--p1--h"]).toBe("شرح/حاشية");
    expect(byId["bn1"]).toBe("فائدة");
    expect(byId["bk2"]).toBe("شرح/تعليق"); // sharh_of reverse edge
    expect(byId["ar2"]).toBe("إشارة");
  });

  it("collects a person's authored works as backlinks", () => {
    const ids = g.backlinksFor("person", "p1").map((r) => r.entry.id);
    expect(ids).toContain("bk1");
    expect(ids).toContain("ar1");
  });
});

describe("graph over real fixtures", () => {
  const g = buildGraph(loadContentFromDisk());

  it("commentariesOf links sharh-al-wasitiyyah to al-wasitiyyah", () => {
    const ids = g.commentariesOf("al-wasitiyyah").map((b) => b.id);
    expect(ids).toContain("sharh-al-wasitiyyah");
  });

  it("links al-asma-was-sifat to its materials", () => {
    const ids = g.materialsByTopic("al-asma-was-sifat").map((m) => m.id);
    expect(ids).toContain("al-wasitiyyah");
    expect(ids).toContain("maqala-tawhid");
    expect(ids).toContain("masail-al-asma-was-sifat");
  });
});
