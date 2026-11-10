import { describe, expect, it, vi } from "vitest";
import { parseReadableDocument } from "./app-v2/parser.js";
import { stripMdRaw } from "./strip-md.js";

vi.mock("../../scripts/gen-app-dict.js", () => ({ DICTS: [] }));
const { appPlace, placedPassages } = await import("../../scripts/gen-search-index.js");

// 30 printed pages of distinct paragraphs, with a caret note and an inline marker the app keeps apart.
// Distinct words made of letters only: the placement matches letters, not digits.
const L = "ابتثجحخدذرزسشصضطظعغفقكلمنهوي";
const word = (k: number) => Array.from({ length: 4 }, (_, d) => L[Math.floor(k / L.length ** d) % L.length]).join("");
const tag = (n: number, i: number) => `وسم${word(n * 7 + i + 1000)}`;
const para = (n: number, i: number) => `فقرة ${Array.from({ length: 50 }, (_, w) => word((n * 4 + i) * 50 + w)).join(" ")} ${tag(n, i)}`;
const page = (n: number) => [0, 1, 2, 3].map((i) => para(n, i)).join("\n\n") + `\n\n(^${n}) حاشية الصفحة ${n}`;
const body = Array.from({ length: 30 }, (_, i) => `${page(i + 1)}\n\n<hr class="page-sep" data-page="${i + 1}" />`).join("\n\n");

describe("search passages carry the app's blocks", () => {
  it("each passage's range holds the blocks its words are in", () => {
    const blocks = parseReadableDocument({ coll: "book", id: "t", body, sourcePath: "t.md" }).blocks;
    const place = appPlace("t", body)!;
    const out = placedPassages(stripMdRaw(body), place);
    expect(out.length).toBeGreaterThan(5);
    expect(place.missed).toBe(0);
    for (const p of out) {
      const [from, to] = p.pg!.split("-").map(Number);
      for (const t of p.text.match(/وسم\S{4}/g) ?? []) {
        const at = blocks.findIndex((b) => String(b.x ?? "").includes(t));
        expect(at, `${t} in ${p.pg}`).toBeGreaterThanOrEqual(from);
        expect(at, `${t} in ${p.pg}`).toBeLessThanOrEqual(to);
      }
      expect(to - from).toBeLessThan(20);
    }
  });
});
