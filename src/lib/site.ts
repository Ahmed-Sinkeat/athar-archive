// Page-runtime helpers: build the knowledge graph from Astro collections and
// expose common lookups so templates stay DRY.

import { getCollection } from "astro:content";
import { buildGraph, toContentEntries, type Graph } from "./graph";
import { COLLECTIONS } from "./types";
import { hrefFor, labelFor } from "./display";

export async function loadGraph(): Promise<Graph> {
  const cols = await Promise.all(COLLECTIONS.map((c) => getCollection(c as any)));
  const entries = toContentEntries(
    cols.flat().map((e: any) => ({ collection: e.collection, id: e.id, data: e.data, body: e.body, filePath: e.filePath })),
  );
  return buildGraph(entries);
}

export async function personNameMap(): Promise<Map<string, string>> {
  const people = await getCollection("person");
  return new Map(people.map((p) => [p.id, p.data.title as string]));
}

// id → العصر (literary era), only for people who have one set.
export async function personEraMap(): Promise<Map<string, string>> {
  const people = await getCollection("person");
  return new Map(people.flatMap((p) => (p.data.era ? [[p.id, p.data.era as string]] : [])));
}

export const isPub = (e: { data: { status?: unknown } }) => e.data.status === "published";

// One inline annotation: the شرح body + how to label/route it in the chooser.
export interface AnnNote {
  kind: string;
  body: string;
  annotator?: string;
  phrase?: string;
  sourceHref?: string;
  sourceLabel?: string;
}

// Published annotations for a target, grouped by anchor and enriched with the
// annotator name + a resolved source link. Shared by the poem readers.
export function notesByAnchor(
  targetType: "book" | "poem" | "quran",
  targetId: string,
  graph: Graph,
  names: Map<string, string>,
): Record<string, AnnNote[]> {
  const out: Record<string, AnnNote[]> = {};
  for (const an of graph.annotationsForTarget(targetType, targetId)) {
    if (an.data.status !== "published") continue;
    let sourceHref: string | undefined;
    let sourceLabel: string | undefined;
    const st = an.data.source_type as string | undefined;
    const si = an.data.source_id as string | undefined;
    if (st && si) {
      const e = graph.getById(st, si);
      if (e) {
        sourceHref = hrefFor(st, si, { series: (e.data as any).series });
        sourceLabel = e.data.title as string;
      }
    }
    (out[an.data.anchor as string] ??= []).push({
      kind: an.data.kind as string,
      body: an.body ?? "",
      annotator: an.data.annotator ? names.get(an.data.annotator as string) : undefined,
      phrase: an.data.phrase as string | undefined,
      sourceHref,
      sourceLabel,
    });
  }
  return out;
}

// A book's relation to the work it comments on (شرح/مختصر/حاشية), resolved
// against book or poem targets alike — سharh_of can now point at a poem.
export interface OriginLink { relation: string; href: string; title: string }
const ORIGIN_FIELDS: [field: string, relation: string][] = [
  ["sharh_of", "شرحٌ لـ"],
  ["mukhtasar_of", "مختصرٌ لـ"],
  ["hashiyah_on", "حاشيةٌ على"],
];
export function originOf(data: Record<string, any>, graph: Graph): OriginLink | undefined {
  for (const [field, relation] of ORIGIN_FIELDS) {
    const targetId = data[field] as string | undefined;
    if (!targetId) continue;
    const target = graph.getById("book", targetId) ?? graph.getById("poem", targetId);
    if (!target || !isPub(target)) continue;
    return { relation, href: hrefFor(target.collection, target.id), title: target.data.title as string };
  }
  return undefined;
}

// A material's subject titles, derived from its topics[] (topic → subject).
// Used to emit the Pagefind `subject` facet so search can filter by فن.
export function subjectTitlesFor(topicIds: string[] | undefined, graph: Graph): string[] {
  if (!topicIds?.length) return [];
  const titles = new Set<string>();
  for (const tid of topicIds) {
    const sid = graph.getById("topic", tid)?.data.subject as string | undefined;
    if (!sid) continue;
    const s = graph.getById("subject", sid);
    if (s && s.data.status === "published") titles.add(s.data.title as string);
  }
  return [...titles];
}

// Bottom-of-reader «ذات صلة»: the series that explains this متن, then the same
// author's other works, then items sharing a topic. Deduped, self excluded.
export interface RelatedItem { href: string; title: string; kind: string; sub?: string }
export function relatedTo(
  entry: { collection: string; id: string; data: Record<string, any> },
  graph: Graph,
  names: Map<string, string>,
  limit = 6,
): RelatedItem[] {
  const seen = new Set<string>([`${entry.collection}:${entry.id}`]);
  const out: RelatedItem[] = [];
  const add = (e: { collection: string; id: string; data: Record<string, any> }, kind: string) => {
    const k = `${e.collection}:${e.id}`;
    if (seen.has(k) || !isPub(e) || out.length >= limit) return;
    seen.add(k);
    out.push({ href: hrefFor(e.collection, e.id, { series: e.data.series }), title: e.data.title, kind, sub: e.data.person ? names.get(e.data.person) : undefined });
  };
  graph.commentariesOf(entry.id).forEach((s) => add(s, "شرح/تعليق"));
  if (entry.data.person) graph.materialsByPerson(entry.data.person as string).forEach((e) => add(e, labelFor(e.collection, e.data)));
  for (const t of (entry.data.topics as string[] | undefined) ?? []) graph.materialsByTopic(t).forEach((e) => add(e, labelFor(e.collection, e.data)));
  return out;
}

// «ما يشير إلى هذا»: reverse references (backlinks) as serializable items for the
// Relations panel. Composes graph.backlinksFor; keeps connectivity off the body.
export interface BacklinkItem { href: string; title: string; kind: string; relation: string; sub?: string }
export function backlinksList(
  entry: { collection: string; id: string },
  graph: Graph,
  names: Map<string, string>,
): BacklinkItem[] {
  const out: BacklinkItem[] = [];
  for (const { entry: e, relation } of graph.backlinksFor(entry.collection, entry.id)) {
    if (!isPub(e)) continue;
    out.push({
      href: hrefFor(e.collection, e.id, { series: (e.data as any).series }),
      title: e.data.title as string,
      kind: labelFor(e.collection, e.data),
      relation,
      sub: e.data.person ? names.get(e.data.person as string) : undefined,
    });
  }
  return out;
}

// published entries of a collection, newest first
export async function publishedSorted(collection: any) {
  const all = (await getCollection(collection)) as any[];
  return all
    .filter(isPub)
    .sort((a, b) => +new Date(b.data.published_at) - +new Date(a.data.published_at));
}
