// Static JSON content export for the Android companion app (docs/android-app.md).
// Runs in build:finish; output lands in dist/r2-upload/app/v1/ so the existing
// scripts/upload-r2-assets.mjs diff-uploads it to the BOOK_ASSETS bucket, and
// src/pages/app/v1/[...path].ts serves it with one R2 read. Layout:
//
//   app/v1/catalog.json                     — every published readable entry:
//                                             titles/authors/topics + audio tracks
//                                             + chapter lists (the app's library
//                                             AND its bundled "what exists" search)
//   app/v1/text/<coll>/<id>.json            — whole-entry payload  {id,coll,title,md,text}
//   app/v1/text/book/<id>/<chapter>.json    — chunked-book chapter {book,slug,title,n,of,prev,next,md,text}
//   app/v1/manifest.json                    — path → sha256-16 of the exact bytes;
//                                             the app diffs this against its copy
//                                             to sync, and busts edge cache with
//                                             ?v=<hash> when refetching a path.
//
// `md` is the raw markdown body (source of truth — the app renders it; {#id}
// anchors are left in for deep links). `text` is the SAME stripMd() output the
// D1 search index stores, so app-side offline FTS matches site search by
// construction — only the QUERY normalizer needs a Kotlin port (ArNormalize.kt,
// verified against golden vectors from gen-ar-vectors.ts).
//
// Determinism: no timestamps or other volatile bytes, sorted iteration —
// unchanged content must hash identically or every deploy re-uploads ~4GB
// (the aa-build lesson in gen-book-chapters.ts, learned at 14min/deploy).
// ponytail: md+text roughly doubles stored bytes vs md alone; if R2 growth or
// first-upload time ever hurts, drop `text` and port stripMd() instead.
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { loadContentFromDisk } from "../src/lib/load.js";
import { analyzeBook } from "../src/lib/chunk.js";
import { stripMd } from "../src/lib/strip-md.js";

const OUT = path.resolve("dist/r2-upload/app/v1");

interface Track {
  id: string;
  title: string;
  url: string;
  format: string;
  duration?: string;
  size_bytes?: number;
}

function main() {
  const all = loadContentFromDisk();
  const entries = all.filter((e) => e.data.status === "published");
  const personName = new Map(
    entries.filter((e) => e.collection === "person").map((e) => [e.id, String(e.data.title ?? "")]),
  );

  // Audio is deliberately NOT status-filtered: the site renders draft audio
  // (the 855 swteat recordings are status:draft yet live). Track order = id
  // order, same as the site's filename-glob playlist order.
  const audioBySource = new Map<string, Track[]>();
  for (const a of all.filter((e) => e.collection === "audio").sort((x, y) => x.id.localeCompare(y.id))) {
    const key = `${a.data.source_type}:${a.data.source_id}`;
    const t: Track = {
      id: a.id,
      title: String(a.data.title ?? ""),
      url: String(a.data.url ?? ""),
      format: String(a.data.format ?? "opus"),
    };
    if (a.data.duration) t.duration = String(a.data.duration);
    if (typeof a.data.size_bytes === "number") t.size_bytes = a.data.size_bytes;
    (audioBySource.get(key) ?? audioBySource.set(key, []).get(key)!).push(t);
  }

  // Streaming writes: stringify→hash→write→free per file. Buffering all
  // payloads first (md+text for every chapter of a 2.4GB corpus) OOMs node.
  fs.rmSync(OUT, { recursive: true, force: true });
  const manifest: Record<string, string> = {};
  let bytes = 0;
  let count = 0;
  const emit = (p: string, payload: unknown) => {
    const body = JSON.stringify(payload);
    manifest[p] = crypto.createHash("sha256").update(body).digest("hex").slice(0, 16);
    const dst = path.join(OUT, p);
    fs.mkdirSync(path.dirname(dst), { recursive: true });
    fs.writeFileSync(dst, body, "utf-8");
    bytes += Buffer.byteLength(body, "utf-8");
    count++;
  };
  const catalog: Record<string, unknown>[] = [];

  const READABLE = new Set(["book", "poem", "article", "question", "quran"]);
  for (const e of entries.filter((x) => READABLE.has(x.collection)).sort((a, b) =>
    a.collection === b.collection ? a.id.localeCompare(b.id) : a.collection.localeCompare(b.collection),
  )) {
    const title = String(e.data.title ?? "");
    const person = e.data.person ? String(e.data.person) : undefined;
    const item: Record<string, unknown> = { id: e.id, coll: e.collection, title };
    if (person) {
      item.person = person;
      const name = personName.get(person);
      if (name) item.personName = name;
    }
    if (e.collection === "book") item.kind = e.data.kind ?? "كتاب"; // zod default bypassed by disk loader
    if (Array.isArray(e.data.topics) && e.data.topics.length) item.topics = e.data.topics;
    if (e.data.description) item.description = e.data.description;
    const tracks = audioBySource.get(`${e.collection}:${e.id}`);
    if (tracks) item.audio = tracks;

    if (e.collection === "book") {
      const a = analyzeBook(e.body);
      item.words = a.wordCount;
      if (a.chunked) {
        item.chapters = a.chapters.map((c) => ({ slug: c.slug, title: c.title }));
        a.chapters.forEach((c, i) => {
          emit(`text/book/${e.id}/${c.slug}.json`, {
            book: e.id,
            slug: c.slug,
            title: c.title,
            n: i + 1,
            of: a.chapters.length,
            prev: i > 0 ? a.chapters[i - 1].slug : null,
            next: i < a.chapters.length - 1 ? a.chapters[i + 1].slug : null,
            md: c.content,
            text: stripMd(c.content),
          });
        });
        catalog.push(item);
        continue;
      }
    }
    emit(`text/${e.collection}/${e.id}.json`, {
      id: e.id,
      coll: e.collection,
      title,
      md: e.body,
      text: stripMd(e.body),
    });
    catalog.push(item);
  }

  if (catalog.length === 0) throw new Error("app content export came out empty — refusing to write");
  emit("catalog.json", { v: 1, entries: catalog });
  const sortedManifest = Object.fromEntries(Object.entries(manifest).sort(([a], [b]) => a.localeCompare(b)));
  fs.writeFileSync(path.join(OUT, "manifest.json"), JSON.stringify({ v: 1, files: sortedManifest }), "utf-8");

  console.log(
    `✓ gen-app-content: ${catalog.length} catalog entries, ${count} file(s), ${(bytes / 1e6).toFixed(0)} MB → ${OUT}`,
  );
}

main();
