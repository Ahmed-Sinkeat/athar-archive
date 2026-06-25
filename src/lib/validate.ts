// Cross-entity build-time validation.
// Each rule that can be enforced per-entity lives in config.ts (Zod).
// This module handles rules that require resolving refs across collections.

import { extractAnchors } from "./chapters.js";
import { SLUG_RE } from "./slug.js";
import type { ContentEntry } from "./types.js";

export type { ContentEntry } from "./types.js";

export interface BuildError {
  collection: string;
  id: string;
  rule: string;
  message: string;
}

type CollectionMap = Map<string, Map<string, { status: string; data: Record<string, unknown>; body: string }>>;

// SourceType master sets per entity (mirrors config.ts enums)
const SOURCE_TYPES: Record<string, readonly string[]> = {
  series: ["book", "poem"],
  benefit: ["lesson", "book", "article", "poem"],
  audio: ["lesson", "book", "poem", "article"],
};

// Entities that require a person field
const REQUIRES_PERSON = new Set(["book", "poem", "series", "benefit", "article"]);

function buildMap(entries: ContentEntry[]): CollectionMap {
  const map: CollectionMap = new Map();
  for (const e of entries) {
    if (!map.has(e.collection)) map.set(e.collection, new Map());
    map.get(e.collection)!.set(e.id, { status: String(e.data.status ?? "draft"), data: e.data, body: e.body });
  }
  return map;
}

function get(map: CollectionMap, collection: string, id: string) {
  return map.get(collection)?.get(id);
}

function str(v: unknown): string {
  return String(v ?? "");
}

export function validate(entries: ContentEntry[]): BuildError[] {
  const errors: BuildError[] = [];
  const map = buildMap(entries);

  function fail(collection: string, id: string, rule: string, message: string) {
    errors.push({ collection, id, rule, message });
  }

  // --- check id slug format ---
  for (const { id, collection } of entries) {
    if (!SLUG_RE.test(id)) {
      fail(collection, id, "slug-format", `id '${id}' does not match slug pattern (lowercase letters, digits, hyphens)`);
    }
  }

  for (const entry of entries) {
    const { id, collection, data, body } = entry;
    const status = str(data.status) || "draft";
    const isPublished = status === "published";

    // --- id slug format already checked above ---

    // --- mandatory person ref ---
    if (REQUIRES_PERSON.has(collection)) {
      const personId = str(data.person);
      if (!personId) {
        fail(collection, id, "mandatory-relation", `'${collection}/${id}' is missing required field 'person'`);
      } else {
        const person = get(map, "person", personId);
        if (!person) {
          fail(collection, id, "ref-resolution", `person '${personId}' not found (referenced by '${collection}/${id}')`);
        } else if (isPublished && person.status === "draft") {
          fail(collection, id, "draft-ref-guard", `published '${collection}/${id}' references draft person '${personId}'`);
        }
      }
    }

    // --- Topic → Subject mandatory ---
    if (collection === "topic") {
      const subjectId = str(data.subject);
      if (!subjectId) {
        fail(collection, id, "mandatory-relation", `'topic/${id}' is missing required field 'subject'`);
      } else {
        const subjectEntry = get(map, "subject", subjectId);
        if (!subjectEntry) {
          fail(collection, id, "ref-resolution", `subject '${subjectId}' not found (referenced by 'topic/${id}')`);
        } else if (isPublished && subjectEntry.status === "draft") {
          fail(collection, id, "draft-ref-guard", `published 'topic/${id}' references draft subject '${subjectId}'`);
        }
      }
    }

    // --- Lesson → Series mandatory + transcript gate ---
    if (collection === "lesson") {
      const seriesId = str(data.series);
      if (!seriesId) {
        fail(collection, id, "mandatory-relation", `'lesson/${id}' is missing required field 'series'`);
      } else {
        const seriesEntry = get(map, "series", seriesId);
        if (!seriesEntry) {
          fail(collection, id, "ref-resolution", `series '${seriesId}' not found (referenced by 'lesson/${id}')`);
        } else if (isPublished && seriesEntry.status === "draft") {
          fail(collection, id, "draft-ref-guard", `published 'lesson/${id}' references draft series '${seriesId}'`);
        }
      }
      // transcript gate: a published lesson must have non-empty body
      if (isPublished && !body.trim()) {
        fail(collection, id, "transcript-gate", `published 'lesson/${id}' has no transcript — add content to the Markdown body`);
      }
    }

    // --- polymorphic source refs (Series, Benefit) ---
    if (collection === "series" || collection === "benefit") {
      const sourceType = str(data.source_type);
      const sourceId = str(data.source_id);
      if (sourceType && sourceId) {
        const allowed = SOURCE_TYPES[collection]!;
        if (!allowed.includes(sourceType)) {
          fail(collection, id, "source-type", `'${collection}/${id}' has invalid source_type '${sourceType}' — allowed: ${allowed.join(", ")}`);
        } else {
          const source = get(map, sourceType, sourceId);
          if (!source) {
            fail(collection, id, "ref-resolution", `${sourceType} '${sourceId}' not found (referenced by '${collection}/${id}')`);
          } else if (isPublished && source.status === "draft") {
            fail(collection, id, "draft-ref-guard", `published '${collection}/${id}' references draft ${sourceType} '${sourceId}'`);
          }
        }
      }
    }

    // --- Audio: required polymorphic source ---
    if (collection === "audio") {
      const sourceType = str(data.source_type);
      const sourceId = str(data.source_id);
      if (!sourceType || !sourceId) {
        fail(collection, id, "mandatory-relation", `'audio/${id}' requires both source_type and source_id`);
      } else {
        const allowed = SOURCE_TYPES["audio"]!;
        if (!allowed.includes(sourceType)) {
          fail(collection, id, "source-type", `'audio/${id}' has invalid source_type '${sourceType}' — allowed: ${allowed.join(", ")}`);
        } else {
          const source = get(map, sourceType, sourceId);
          if (!source) {
            fail(collection, id, "ref-resolution", `${sourceType} '${sourceId}' not found (referenced by 'audio/${id}')`);
          }
        }
      }
    }

    // --- Annotation: required target + anchor ---
    if (collection === "annotation") {
      const targetType = str(data.target_type);
      const targetId = str(data.target_id);
      if (!targetType || !targetId) {
        fail(collection, id, "mandatory-relation", `'annotation/${id}' requires both target_type and target_id`);
      } else if (!["book", "poem", "quran"].includes(targetType)) {
        fail(collection, id, "source-type", `'annotation/${id}' has invalid target_type '${targetType}' — allowed: book, poem, quran`);
      } else {
        const target = get(map, targetType, targetId);
        if (!target) {
          // dangling annotation: fails the build
          fail(collection, id, "ref-resolution", `${targetType} '${targetId}' not found (dangling annotation '${id}')`);
        } else if (isPublished && target.status === "draft") {
          fail(collection, id, "draft-ref-guard", `published 'annotation/${id}' references draft ${targetType} '${targetId}'`);
        } else {
          // anchor must resolve to a real position in the target body
          const anchor = str(data.anchor);
          const anchors = extractAnchors(targetType, target.body);
          if (anchor && !anchors.has(anchor)) {
            fail(collection, id, "anchor-resolution", `anchor '${anchor}' not found in ${targetType} '${targetId}' (annotation '${id}')`);
          }
        }
      }
    }

    // --- topic refs on any entity ---
    const topics = Array.isArray(data.topics) ? (data.topics as string[]) : [];
    for (const topicId of topics) {
      const topicEntry = get(map, "topic", topicId);
      if (!topicEntry) {
        fail(collection, id, "ref-resolution", `topic '${topicId}' not found (referenced by '${collection}/${id}')`);
      } else if (isPublished && topicEntry.status === "draft") {
        fail(collection, id, "draft-ref-guard", `published '${collection}/${id}' references draft topic '${topicId}'`);
      }
    }

    // --- optional article.audio ref ---
    if (collection === "article" && data.audio) {
      const audioId = str(data.audio);
      const audioEntry = get(map, "audio", audioId);
      if (!audioEntry) {
        fail(collection, id, "ref-resolution", `audio '${audioId}' not found (referenced by 'article/${id}')`);
      }
    }

    // --- optional lesson.audio ref ---
    if (collection === "lesson" && data.audio) {
      const audioId = str(data.audio);
      const audioEntry = get(map, "audio", audioId);
      if (!audioEntry) {
        fail(collection, id, "ref-resolution", `audio '${audioId}' not found (referenced by 'lesson/${id}')`);
      }
    }
  }

  return errors;
}

// Formats errors as a human-readable string for the build log
export function formatErrors(errors: BuildError[]): string {
  return errors
    .map((e) => `  [${e.rule}] ${e.collection}/${e.id}: ${e.message}`)
    .join("\n");
}
