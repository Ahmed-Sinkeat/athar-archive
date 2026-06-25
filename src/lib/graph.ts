// In-memory knowledge graph built from the content collections.
// Replaces a relational DB at Phase-1 scale (FR-B-05): it answers the
// relational queries the site needs — materials by topic/subject/person,
// lessons by series (ordered), and reverse polymorphic lookups.

import {
  MATERIAL_COLLECTIONS,
  type ContentEntry,
} from "./types.js";
import { parseWikilinks } from "./wikilink.js";

function str(v: unknown): string {
  return String(v ?? "");
}

function key(type: string, id: string): string {
  return `${type}:${id}`;
}

// --- duration helpers (for derived series stats) ---

export function parseDuration(d: string | undefined): number {
  if (!d) return 0;
  const parts = d.split(":").map((p) => parseInt(p, 10));
  if (parts.some(Number.isNaN)) return 0;
  return parts.reduce((acc, n) => acc * 60 + n, 0);
}

export function formatDuration(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  const pad = (n: number) => String(n).padStart(2, "0");
  return h > 0 ? `${h}:${pad(m)}:${pad(sec)}` : `${m}:${pad(sec)}`;
}

export interface SeriesStats {
  lessonCount: number;
  publishedLessonCount: number;
  totalDuration: string;
  totalSeconds: number;
}

export interface Graph {
  all: ContentEntry[];
  getById(collection: string, id: string): ContentEntry | undefined;

  materialsByTopic(topicId: string): ContentEntry[];
  topicsBySubject(subjectId: string): ContentEntry[];
  materialsBySubject(subjectId: string): ContentEntry[];
  materialsByPerson(personId: string): ContentEntry[];
  lessonsBySeries(seriesId: string): ContentEntry[];

  annotationsForTarget(type: string, id: string): ContentEntry[];
  audioForSource(type: string, id: string): ContentEntry[];
  benefitsForSource(type: string, id: string): ContentEntry[];
  seriesForSource(type: string, id: string): ContentEntry[];
  // «ما يشير إلى هذا»: reverse references (annotations, benefits, series, authored
  // works, topic/subject members, and [[wiki-link]] mentions) pointing at this entity.
  backlinksFor(collection: string, id: string): { entry: ContentEntry; relation: string }[];

  seriesStats(seriesId: string): SeriesStats;
}

export function buildGraph(entries: ContentEntry[]): Graph {
  const byCollection = new Map<string, Map<string, ContentEntry>>();
  const topicIndex = new Map<string, ContentEntry[]>();
  const personIndex = new Map<string, ContentEntry[]>();
  const subjectTopics = new Map<string, ContentEntry[]>();
  const seriesLessons = new Map<string, ContentEntry[]>();

  const annotationsByTarget = new Map<string, ContentEntry[]>();
  const audioBySource = new Map<string, ContentEntry[]>();
  const benefitsBySource = new Map<string, ContentEntry[]>();
  const seriesBySource = new Map<string, ContentEntry[]>();
  const wikilinkIndex = new Map<string, ContentEntry[]>(); // "type:slug" → entries whose body links to it

  const push = (map: Map<string, ContentEntry[]>, k: string, e: ContentEntry) => {
    const list = map.get(k);
    if (list) list.push(e);
    else map.set(k, [e]);
  };

  const materialSet = new Set<string>(MATERIAL_COLLECTIONS);

  for (const e of entries) {
    if (!byCollection.has(e.collection)) byCollection.set(e.collection, new Map());
    byCollection.get(e.collection)!.set(e.id, e);

    // topic index (any material carrying topics[])
    const topics = Array.isArray(e.data.topics) ? (e.data.topics as string[]) : [];
    if (materialSet.has(e.collection)) {
      for (const t of topics) push(topicIndex, t, e);
    }

    // person index (material collections with a person field)
    if (materialSet.has(e.collection) && e.data.person) {
      push(personIndex, str(e.data.person), e);
    }

    switch (e.collection) {
      case "topic":
        if (e.data.subject) push(subjectTopics, str(e.data.subject), e);
        break;
      case "lesson":
        if (e.data.series) push(seriesLessons, str(e.data.series), e);
        break;
      case "annotation":
        if (e.data.target_type && e.data.target_id) {
          push(annotationsByTarget, key(str(e.data.target_type), str(e.data.target_id)), e);
        }
        break;
      case "audio":
        if (e.data.source_type && e.data.source_id) {
          push(audioBySource, key(str(e.data.source_type), str(e.data.source_id)), e);
        }
        break;
      case "benefit":
        if (e.data.source_type && e.data.source_id) {
          push(benefitsBySource, key(str(e.data.source_type), str(e.data.source_id)), e);
        }
        break;
      case "series":
        if (e.data.source_type && e.data.source_id) {
          push(seriesBySource, key(str(e.data.source_type), str(e.data.source_id)), e);
        }
        break;
    }

    for (const w of parseWikilinks(e.body ?? "")) push(wikilinkIndex, key(w.type, w.slug), e);
  }

  // lessons sorted by `order`
  for (const list of seriesLessons.values()) {
    list.sort((a, b) => Number(a.data.order ?? 0) - Number(b.data.order ?? 0));
  }

  const getById = (collection: string, id: string) => byCollection.get(collection)?.get(id);

  const materialsByTopic = (topicId: string) => topicIndex.get(topicId) ?? [];
  const topicsBySubject = (subjectId: string) => subjectTopics.get(subjectId) ?? [];

  const materialsBySubject = (subjectId: string) => {
    const seen = new Map<string, ContentEntry>();
    for (const topic of topicsBySubject(subjectId)) {
      for (const m of materialsByTopic(topic.id)) seen.set(key(m.collection, m.id), m);
    }
    return [...seen.values()];
  };

  const lessonsBySeries = (seriesId: string) => seriesLessons.get(seriesId) ?? [];

  const seriesStats = (seriesId: string): SeriesStats => {
    const lessons = lessonsBySeries(seriesId);
    let totalSeconds = 0;
    let publishedLessonCount = 0;
    for (const l of lessons) {
      totalSeconds += parseDuration(str(l.data.duration) || undefined);
      if (String(l.data.status) === "published") publishedLessonCount += 1;
    }
    return {
      lessonCount: lessons.length,
      publishedLessonCount,
      totalDuration: formatDuration(totalSeconds),
      totalSeconds,
    };
  };

  const backlinksFor = (collection: string, id: string) => {
    const out: { entry: ContentEntry; relation: string }[] = [];
    const seen = new Set<string>();
    const add = (e: ContentEntry, relation: string) => {
      const k = key(e.collection, e.id);
      if (seen.has(k)) return;
      seen.add(k);
      out.push({ entry: e, relation });
    };
    for (const a of annotationsByTarget.get(key(collection, id)) ?? []) add(a, "شرح/حاشية");
    for (const b of benefitsBySource.get(key(collection, id)) ?? []) add(b, "فائدة");
    for (const s of seriesBySource.get(key(collection, id)) ?? []) add(s, "سلسلة شرح");
    if (collection === "person") for (const e of personIndex.get(id) ?? []) add(e, "من مؤلَّفاته");
    if (collection === "topic") for (const e of topicIndex.get(id) ?? []) add(e, "في الموضوع");
    if (collection === "subject") for (const t of subjectTopics.get(id) ?? []) add(t, "موضوع");
    if (collection === "series") for (const l of seriesLessons.get(id) ?? []) add(l, "درس");
    for (const e of wikilinkIndex.get(key(collection, id)) ?? []) add(e, "إشارة");
    return out;
  };

  return {
    all: entries,
    getById,
    materialsByTopic,
    topicsBySubject,
    materialsBySubject,
    materialsByPerson: (personId: string) => personIndex.get(personId) ?? [],
    lessonsBySeries,
    annotationsForTarget: (type, id) => annotationsByTarget.get(key(type, id)) ?? [],
    audioForSource: (type, id) => audioBySource.get(key(type, id)) ?? [],
    benefitsForSource: (type, id) => benefitsBySource.get(key(type, id)) ?? [],
    seriesForSource: (type, id) => seriesBySource.get(key(type, id)) ?? [],
    backlinksFor,
    seriesStats,
  };
}

// Adapter: map Astro getCollection() entries → ContentEntry for page runtime.
export function toContentEntries(
  collected: { collection: string; id: string; data: Record<string, unknown>; body?: string }[],
): ContentEntry[] {
  return collected.map((e) => ({
    id: e.id,
    collection: e.collection,
    data: e.data,
    body: e.body ?? "",
  }));
}
