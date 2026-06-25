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
    e("book", "bk1", { title: "كتاب", person: "p1", topics: ["t1"] }),
    e("article", "ar1", { title: "مقالة", person: "p1", topics: ["t1"] }),
    e("series", "s1", { title: "سلسلة", person: "p1", source_type: "book", source_id: "bk1" }),
    e("lesson", "s1--lesson-2", { title: "الثاني", series: "s1", order: 2, duration: "58:20" }, "نص"),
    e("lesson", "s1--lesson-1", { title: "الأول", series: "s1", order: 1, duration: "1:12:34" }, "نص"),
    e("benefit", "bn1", { title: "فائدة", person: "p1", source_type: "book", source_id: "bk1" }),
    e("audio", "au1", { title: "صوت", source_type: "lesson", source_id: "s1--lesson-1", url: "x" }),
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
    expect(ids).toEqual(["ar1", "bk1"]);
  });

  it("indexes materials by subject via its topics", () => {
    const ids = g.materialsBySubject("aqeedah").map((m) => m.id).sort();
    expect(ids).toEqual(["ar1", "bk1"]);
  });

  it("indexes materials by person", () => {
    const ids = g.materialsByPerson("p1").map((m) => m.collection).sort();
    expect(ids).toEqual(["article", "benefit", "book", "series"]);
  });

  it("orders lessons by `order`", () => {
    const order = g.lessonsBySeries("s1").map((l) => l.id);
    expect(order).toEqual(["s1--lesson-1", "s1--lesson-2"]);
  });

  it("resolves reverse polymorphic lookups", () => {
    expect(g.audioForSource("lesson", "s1--lesson-1").map((a) => a.id)).toEqual(["au1"]);
    expect(g.benefitsForSource("book", "bk1").map((b) => b.id)).toEqual(["bn1"]);
    expect(g.seriesForSource("book", "bk1").map((s) => s.id)).toEqual(["s1"]);
    expect(g.annotationsForTarget("book", "bk1").map((a) => a.id)).toEqual(["bk1--p1--h"]);
  });

  it("derives series stats (count + summed duration)", () => {
    const stats = g.seriesStats("s1");
    expect(stats.lessonCount).toBe(2);
    expect(stats.totalDuration).toBe("2:10:54"); // 1:12:34 + 58:20
  });
});

describe("backlinks (ما يشير إلى هذا)", () => {
  const corpus = smallCorpus();
  corpus.push(e("article", "ar2", { title: "إشارة", person: "p1" }, "انظر [[book:bk1]] للتفصيل"));
  const g = buildGraph(corpus);

  it("collects reverse references to a book (annotation, benefit, series, wiki-link)", () => {
    const byId = Object.fromEntries(g.backlinksFor("book", "bk1").map((r) => [r.entry.id, r.relation]));
    expect(byId["bk1--p1--h"]).toBe("شرح/حاشية");
    expect(byId["bn1"]).toBe("فائدة");
    expect(byId["s1"]).toBe("سلسلة شرح");
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

  it("derives sharh-al-wasitiyyah stats matching the fixtures", () => {
    const stats = g.seriesStats("sharh-al-wasitiyyah");
    expect(stats.lessonCount).toBe(2);
    expect(stats.publishedLessonCount).toBe(1); // lesson-2 is a draft
    expect(stats.totalDuration).toBe("2:10:54");
  });

  it("links al-asma-was-sifat to its materials", () => {
    const ids = g.materialsByTopic("al-asma-was-sifat").map((m) => m.id);
    expect(ids).toContain("al-wasitiyyah");
    expect(ids).toContain("maqala-tawhid");
    expect(ids).toContain("masail-al-asma-was-sifat");
  });
});
