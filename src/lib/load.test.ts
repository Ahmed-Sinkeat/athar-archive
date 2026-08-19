import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { readFrontmatterData } from "./load.js";

const roots: string[] = [];

afterEach(() => {
  for (const root of roots.splice(0)) rmSync(root, { recursive: true, force: true });
});

describe("readFrontmatterData", () => {
  it("parses metadata without depending on a large body", () => {
    const root = mkdtempSync(path.join(tmpdir(), "athar-frontmatter-"));
    roots.push(root);
    mkdirSync(root, { recursive: true });
    const filePath = path.join(root, "large.md");
    writeFileSync(filePath, `---\ntitle: \"كتاب\"\nstatus: published\n---\n${"نص".repeat(1_000_000)}`);
    expect(readFrontmatterData(filePath)).toMatchObject({ title: "كتاب", status: "published" });
  });

  it("handles a UTF-8 character split at the read boundary", () => {
    const root = mkdtempSync(path.join(tmpdir(), "athar-frontmatter-"));
    roots.push(root);
    const padding = "x".repeat(16 * 1024 - 15);
    const filePath = path.join(root, "split.md");
    writeFileSync(filePath, `---\ntitle: \"${padding}ع\"\n---\nbody`);
    expect(String(readFrontmatterData(filePath).title).endsWith("ع")).toBe(true);
  });
});
