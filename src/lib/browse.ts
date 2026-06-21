// Subject → Topic → items grouping for the browse pages (الكتب / المنظومات /
// المسائل). Mirrors the data model: each Topic belongs to one Subject; books,
// poems and questions carry topics[]. An item appears under every topic it
// carries; items with no recognised topic fall into `uncategorized`.

import type { Graph } from "./graph";
import type { ContentEntry } from "./types";

const isPub = (e: { data: { status?: unknown } }) => e.data.status === "published";
const ar = (a: string, b: string) => a.localeCompare(b, "ar");

export interface TopicGroup {
  id: string;
  title: string;
  items: ContentEntry[];
}
export interface SubjectGroup {
  id: string;
  title: string;
  count: number;
  topics: TopicGroup[];
}
export interface BrowseGroups {
  groups: SubjectGroup[];
  uncategorized: ContentEntry[];
}

export function buildSubjectGroups(collection: string, graph: Graph): BrowseGroups {
  const subjects = graph.all
    .filter((e) => e.collection === "subject" && isPub(e))
    .sort((a, b) => ar(a.data.title as string, b.data.title as string));

  const placed = new Set<string>();
  const groups: SubjectGroup[] = [];

  for (const s of subjects) {
    const topics = graph
      .topicsBySubject(s.id)
      .filter(isPub)
      .sort((a, b) => ar(a.data.title as string, b.data.title as string));

    const tgroups: TopicGroup[] = [];
    let count = 0;
    for (const t of topics) {
      const items = graph
        .materialsByTopic(t.id)
        .filter((e) => e.collection === collection && isPub(e))
        .sort((a, b) => ar(a.data.title as string, b.data.title as string));
      if (!items.length) continue;
      items.forEach((i) => placed.add(i.id));
      count += items.length;
      tgroups.push({ id: t.id, title: t.data.title as string, items });
    }
    if (tgroups.length) groups.push({ id: s.id, title: s.data.title as string, count, topics: tgroups });
  }

  const uncategorized = graph.all
    .filter((e) => e.collection === collection && isPub(e) && !placed.has(e.id))
    .sort((a, b) => ar(a.data.title as string, b.data.title as string));

  return { groups, uncategorized };
}
