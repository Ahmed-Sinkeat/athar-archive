import { describe, it, expect } from "vitest";
import { buildGraph } from "./graph.js";
import { buildSubjectGroups } from "./browse.js";
import type { ContentEntry } from "./types.js";

const e = (collection: string, id: string, data: Record<string, unknown>): ContentEntry => ({
  collection, id, data: { status: "published", ...data }, body: "",
});

describe("buildSubjectGroups item order", () => {
  const corpus: ContentEntry[] = [
    e("subject", "aqeedah", { title: "العقيدة" }),
    e("topic", "t1", { title: "التوحيد", subject: "aqeedah" }),
    e("book", "late", { title: "زائد", person: "p", topics: ["t1"], authored_year: 728 }),
    e("book", "early", { title: "ألف", person: "p", topics: ["t1"], authored_year: 241 }),
    e("book", "undated", { title: "بدون", person: "p", topics: ["t1"] }),
  ];
  const { groups } = buildSubjectGroups("book", buildGraph(corpus));

  it("sorts by authored_year ascending, undated last", () => {
    expect(groups[0].topics[0].items.map((i) => i.id)).toEqual(["early", "late", "undated"]);
  });
});
