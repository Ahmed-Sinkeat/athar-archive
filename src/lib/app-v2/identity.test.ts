import { describe, expect, it } from "vitest";
import { assignStableBlockIds, sha256Hex } from "./identity.js";
import type { DraftBlock } from "./contract.js";

const blocks: DraftBlock[] = [
  { t: "h2", a: "chapter", x: "المقدمة" },
  { t: "p", a: "chapter", x: "الحمد لله رب العالمين" },
  { t: "p", a: "chapter", x: "وصلى الله على محمد" },
];

describe("app/v2 stable block identity", () => {
  it("is deterministic after the first sidecar and preserves IDs through insertion and heading rename", () => {
    const source1 = sha256Hex("source one");
    const first = assignStableBlockIds(blocks, source1);
    expect(first.created).toBe(3);
    expect(new Set(first.blocks.map((block) => block.id)).size).toBe(3);

    const repeated = assignStableBlockIds(blocks, source1, first.sidecar);
    expect(repeated.created).toBe(0);
    expect(repeated.sidecar).toEqual(first.sidecar);
    expect(repeated.blocks).toEqual(first.blocks);

    const changed = [
      { t: "p", a: "renamed", x: "تمهيد جديد" },
      ...blocks.map((block) => ({ ...block, a: "renamed" })),
    ] satisfies DraftBlock[];
    const moved = assignStableBlockIds(changed, sha256Hex("source two"), first.sidecar);
    expect(moved.sidecar.generation).toBe(2);
    expect(moved.created).toBe(1);
    expect(moved.blocks.slice(1).map((block) => block.id)).toEqual(first.blocks.map((block) => block.id));
  });

  it("rejects a duplicated persisted identity", () => {
    const first = assignStableBlockIds(blocks, sha256Hex("source"));
    const duplicate = structuredClone(first.sidecar);
    duplicate.ids[1]!.blockId = duplicate.ids[0]!.blockId;
    expect(() => assignStableBlockIds(blocks, sha256Hex("next"), duplicate)).toThrow("duplicate stable blockId");
  });
});
