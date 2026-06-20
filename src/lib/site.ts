// Page-runtime helpers: build the knowledge graph from Astro collections and
// expose common lookups so templates stay DRY.

import { getCollection } from "astro:content";
import { buildGraph, toContentEntries, type Graph } from "./graph";
import { COLLECTIONS } from "./types";

export async function loadGraph(): Promise<Graph> {
  const cols = await Promise.all(COLLECTIONS.map((c) => getCollection(c as any)));
  const entries = toContentEntries(
    cols.flat().map((e: any) => ({ collection: e.collection, id: e.id, data: e.data, body: e.body })),
  );
  return buildGraph(entries);
}

export async function personNameMap(): Promise<Map<string, string>> {
  const people = await getCollection("person");
  return new Map(people.map((p) => [p.id, p.data.title as string]));
}

export const isPub = (e: { data: { status?: unknown } }) => e.data.status === "published";

// published entries of a collection, newest first
export async function publishedSorted(collection: any) {
  const all = (await getCollection(collection)) as any[];
  return all
    .filter(isPub)
    .sort((a, b) => +new Date(b.data.published_at) - +new Date(a.data.published_at));
}
