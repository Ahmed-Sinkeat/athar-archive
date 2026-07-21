import { describe, it, expect } from "vitest";
import { applyTajweed, findTajweedMatches, TAJWEED_COLORS } from "./tajweed.js";

describe("findTajweedMatches / applyTajweed", () => {
  it("finds natural madd (madd-tabii) on a bare alif/waw/ya after a matching vowel", () => {
    const matches = findTajweedMatches("الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ");
    const rules = matches.map((m) => m.rule);
    expect(rules).toContain("madd-tabii");
    expect(applyTajweed("الْعَالَمِينَ")).toContain('<span class="tw-madd-tabii">');
  });

  it("does not flag a waw-sukun that's a consonantal glide, not a madd letter", () => {
    // يَوْمِ — fatha before the waw-sukun (diphthong "aw"), not damma (which would be madd)
    const matches = findTajweedMatches("يَوْمِ");
    expect(matches.map((m) => m.rule)).not.toContain("madd-tabii");
  });

  it("finds qalqalah on a qalqalah letter carrying sukun", () => {
    const matches = findTajweedMatches("يَجْعَلُونَ");
    expect(matches.map((m) => m.rule)).toContain("qalqalah");
  });

  it("finds ikhfa on noon-sakinah before an ikhfa letter", () => {
    const matches = findTajweedMatches("مِنْ قَبْلِكَ");
    expect(matches.map((m) => m.rule)).toContain("ikhfa");
  });

  it("leaves plain text with no tajweed triggers untouched", () => {
    expect(applyTajweed("قُلْ")).toBe("قُلْ");
  });

  it("finds the elided madd in Allah's name (no alif in the rasm at all)", () => {
    // بِسْمِ اللَّهِ — real ayah 1 opener
    const matches = findTajweedMatches("بِسْمِ اللَّهِ الرَّحْمَنِ الرَّحِيمِ");
    const hit = matches.find((m) => m.rule === "madd-tabii");
    expect(hit).toBeDefined();
  });

  it("finds the elided madd in Ar-Rahman (no alif in the rasm at all)", () => {
    const matches = findTajweedMatches("الرَّحْمَنِ");
    expect(matches.map((m) => m.rule)).toContain("madd-tabii");
  });

  it("finds madd-arid on a word/ayah-final alif-maksura after a fatha", () => {
    expect(findTajweedMatches("يَخْشَى").map((m) => m.rule)).toContain("madd-arid");
  });

  it("finds idgham shamsiyyah on ال + a sun letter (shadda lands on the sun letter, real ayah words)", () => {
    expect(findTajweedMatches("الرَّحْمَنِ").map((m) => m.rule)).toContain("idgham-shamsiyyah");
    expect(findTajweedMatches("الصِّرَاطَ").map((m) => m.rule)).toContain("idgham-shamsiyyah");
  });

  it("does not flag idgham shamsiyyah on ال + a moon letter (lam stays voiced, no shadda)", () => {
    expect(findTajweedMatches("الْحَمْدُ").map((m) => m.rule)).not.toContain("idgham-shamsiyyah");
    expect(findTajweedMatches("الْعَالَمِينَ").map((m) => m.rule)).not.toContain("idgham-shamsiyyah");
  });

  it("does not misclassify Allah's name as shamsiyyah (ل is deliberately excluded)", () => {
    expect(findTajweedMatches("اللَّهِ").map((m) => m.rule)).not.toContain("idgham-shamsiyyah");
    expect(findTajweedMatches("لِلَّهِ").map((m) => m.rule)).not.toContain("idgham-shamsiyyah");
  });

  it("does not flag الذين — its shadda sits on the lam itself, a different word entirely", () => {
    // real ayah-7 word: the relative pronoun's own doubled lam, not ال + sun-letter ذ
    expect(findTajweedMatches("الَّذِينَ").map((m) => m.rule)).not.toContain("idgham-shamsiyyah");
  });

  it("finds madd-badal on آ (hamza fused with a mandatory madd), real ayah word", () => {
    expect(findTajweedMatches("بِآيَاتِ").map((m) => m.rule)).toContain("madd-badal");
  });

  it("finds natural madd on a medial (non-ayah-final) alif-maksura, real ayah context", () => {
    const matches = findTajweedMatches("لَا يَخْفَى عَلَيْهِ");
    expect(matches.map((m) => m.rule).filter((r) => r === "madd-tabii").length).toBeGreaterThanOrEqual(2);
  });

  it("finds idgham shamsiyyah on a real ال + ل word that is not Allah\u2019s name", () => {
    expect(findTajweedMatches("اللَّغْوِ").map((m) => m.rule)).toContain("idgham-shamsiyyah");
  });

  it("produces only known, non-overlapping, well-formed ranges", () => {
    const text = "الرَّحْمَنِ الرَّحِيمِ مَالِكِ يَوْمِ الدِّينِ";
    const matches = findTajweedMatches(text);
    let cursor = -1;
    for (const m of matches) {
      expect(Object.keys(TAJWEED_COLORS)).toContain(m.rule);
      expect(m.start).toBeGreaterThan(cursor);
      expect(m.end).toBeGreaterThan(m.start);
      cursor = m.end - 1;
    }
  });
});
