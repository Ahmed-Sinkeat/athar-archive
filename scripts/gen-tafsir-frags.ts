// Per-verse tafsir fragments, split per SOURCE (v2 of M1 in HANDOFF-perf-size.md).
// v1 bundled every source's full text into one per-ayah file, so each ayah tap
// downloaded ~10 tafsirs to show one, and offline surah downloads carried the
// whole bundle (سورة البقرة: 8.7MB). Now, per verse:
//
//   tafsir-frag/<s>/<a>.html          — STUB: the ann-pack with one EMPTY entry
//                                       per source (kind/label/data-lazy-src).
//                                       The sheet's dropdowns render from these;
//                                       reader.ts fetches a body on first show.
//   tafsir-frag/<s>/<a>.<slug>.html   — BODY: one source's full .ann-entry.
//
// and per source:
//
//   dist/client/tafsir-dl/<slug>.json — {title, urls} consumed by downloads.ts's
//                                       "download this whole tafsir" path (the
//                                       urls are that source's stubs + bodies).
//
// All files live in dist/r2-upload/ (R2, via upload-r2-assets.mjs) except the
// tiny dl manifests, which ship as static assets. Served by
// src/pages/tafsir-frag/[surah]/[ayah].html.ts — same URL shape, the `ayah`
// param now optionally carries the ".slug" suffix. Runs after `astro build`.
import fs from "node:fs";
import path from "node:path";
import { markdownToSafeHtml } from "../src/lib/sanitize.js";

type TafsirNote = { kind: string; label: string; sourceSlug: string; sourceHref?: string; sourceTitle?: string; body: string };

const esc = (s: string) => s.replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
const SLUG_OK = /^[a-z0-9-]+$/;

function main() {
  // fs.readFileSync + JSON.parse, NOT a JSON module import — an `import
  // ... with { type: "json" }` for a 300+MB file forces TypeScript to infer
  // a full literal type for the whole object graph at check-time, which
  // OOMs `astro check` (it type-checks every .ts file in the project, not
  // just Astro pages). A plain runtime read is opaque to the type-checker.
  const index: Record<string, TafsirNote[]> = JSON.parse(fs.readFileSync(path.resolve("src/data/quran-tafsir-index.json"), "utf-8"));
  const outRoot = path.resolve("dist/r2-upload/tafsir-frag");
  const dlRoot = path.resolve("dist/client/tafsir-dl");
  // bytes = body-file sum (stubs are noise) — downloads.ts warns before big ones
  const bySource = new Map<string, { title: string; urls: string[]; bytes: number }>();
  let stubs = 0;
  let bodies = 0;

  // Pass 1 — the canonical source list (first-appearance order). Every stub
  // lists EVERY source in this same order; ones absent on that ayah are
  // data-missing="1" (dimmed in the sheet, «ليس لهذا المصدر كلام…» on select).
  // A stable menu keeps the reader's chosen tafsir sticky across ayat instead
  // of silently falling back to another author — and stops "where did the
  // other tafsirs go?" confusion when coverage is partial (السعدي, ابن القيم…).
  const canon = new Map<string, { kind: string; label: string }>();
  for (const notes of Object.values(index)) {
    for (const nt of notes) if (!canon.has(nt.sourceSlug)) canon.set(nt.sourceSlug, { kind: nt.kind, label: nt.label });
  }

  for (const [verseKey, notes] of Object.entries(index)) {
    if (!notes.length) continue;
    const [surah, ayah] = verseKey.split(":");
    const stubUrl = `/tafsir-frag/${surah}/${ayah}.html`;

    // group this verse's notes by source, preserving index order
    const groups = new Map<string, TafsirNote[]>();
    for (const nt of notes) {
      if (!SLUG_OK.test(nt.sourceSlug)) {
        console.error(`✗ gen-tafsir-frags: bad sourceSlug ${JSON.stringify(nt.sourceSlug)} at ${verseKey}`);
        process.exit(1);
      }
      if (!groups.has(nt.sourceSlug)) groups.set(nt.sourceSlug, []);
      groups.get(nt.sourceSlug)!.push(nt);
    }

    const dir = path.join(outRoot, surah);
    fs.mkdirSync(dir, { recursive: true });

    for (const [slug, group] of groups) {
      const first = group[0];
      const bodyUrl = `/tafsir-frag/${surah}/${ayah}.${slug}.html`;
      // a source with several notes on one verse (athar collections) renders
      // as one entry: bodies joined by a dashed rule, each keeping its own
      // "اقرأ في موضعه" link inline; single-note keeps v1's trailing link.
      const single = group.length === 1;
      const parts = group.map((nt) => {
        const inline = !single && nt.sourceHref
          ? `<a class="ann-src-inline" href="${esc(nt.sourceHref)}">اقرأ في موضعه ←</a>`
          : "";
        return `${markdownToSafeHtml(nt.body)}${inline}`;
      });
      const tail = single && first.sourceHref
        ? `<a class="ann-source-link" href="${esc(first.sourceHref)}">اقرأ في موضعه${first.sourceTitle ? `: ${esc(first.sourceTitle)}` : ""} ←</a>`
        : "";
      const bodyHtml = `<div class="ann-entry-body" data-ar>${parts.join('<hr class="ann-note-sep" />')}</div>`;
      const bodyFile = `<div class="ann-entry k-tafsir" data-kind="${esc(first.kind)}" data-label="${esc(first.label)}">${bodyHtml}${tail}</div>`;
      fs.writeFileSync(path.join(dir, `${ayah}.${slug}.html`), bodyFile, "utf-8");
      bodies++;

      const src = bySource.get(slug) ?? { title: first.sourceTitle || first.label, urls: [], bytes: 0 };
      src.urls.push(stubUrl, bodyUrl);
      src.bytes += Buffer.byteLength(bodyFile);
      bySource.set(slug, src);
    }

    // stub = the FULL canonical list, in canonical order; absent → data-missing
    const allEntries = [...canon.entries()].map(([slug, meta]) => {
      const group = groups.get(slug);
      if (group) {
        const first = group[0];
        return `<div class="ann-entry k-tafsir" data-kind="${esc(first.kind)}" data-label="${esc(first.label)}" data-slug="${slug}" data-lazy-src="/tafsir-frag/${surah}/${ayah}.${slug}.html"></div>`;
      }
      return `<div class="ann-entry k-tafsir" data-kind="${esc(meta.kind)}" data-label="${esc(meta.label)}" data-slug="${slug}" data-missing="1"></div>`;
    });
    fs.writeFileSync(
      path.join(dir, `${ayah}.html`),
      `<div class="ann-pack" id="ann-quran-${surah}-${ayah}" data-ann-pack hidden>${allEntries.join("")}</div>`,
      "utf-8",
    );
    stubs++;
  }

  fs.mkdirSync(dlRoot, { recursive: true });
  for (const [slug, src] of bySource) {
    fs.writeFileSync(path.join(dlRoot, `${slug}.json`), JSON.stringify(src), "utf-8");
  }

  console.log(`✓ gen-tafsir-frags: ${stubs} stub(s) + ${bodies} per-source body file(s) → dist/r2-upload/tafsir-frag; ${bySource.size} download manifest(s) → dist/client/tafsir-dl`);
}

main();
