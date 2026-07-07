import { describe, expect, it } from "vitest";
import { parseAtharNumber, isAtharNumberedBook, parseAtharMatn } from "./hadith.js";
import { normalizeArabic } from "./ar-normalize.js";

describe("parseAtharNumber", () => {
  it("reads a Latin-digit leading number", () => {
    expect(parseAtharNumber("17 - حدثنا فلان")).toBe(17);
  });
  it("reads an Arabic-Indic leading number", () => {
    expect(parseAtharNumber("١٧ - حدثنا فلان")).toBe(17);
  });
  it("returns null for ordinary prose", () => {
    expect(parseAtharNumber("قال المصنف رحمه الله")).toBeNull();
    expect(parseAtharNumber("سنة ١٤٤٧ كانت مباركة")).toBeNull();
  });
});

describe("isAtharNumberedBook", () => {
  it("requires at least 10 matching paragraphs", () => {
    const few = Array.from({ length: 9 }, (_, i) => ({ text: `${i + 1} - حدثنا فلان` }));
    expect(isAtharNumberedBook(few)).toBe(false);
    const many = Array.from({ length: 10 }, (_, i) => ({ text: `${i + 1} - حدثنا فلان` }));
    expect(isAtharNumberedBook(many)).toBe(true);
  });
});

describe("parseAtharMatn", () => {
  it("extracts the first «…» quote", () => {
    expect(parseAtharMatn("١٧ - حدثنا فلان عن فلان قال: «إنما الأعمال بالنيات»")).toBe("إنما الأعمال بالنيات");
  });
  it("returns null when there's no quoted matn", () => {
    expect(parseAtharMatn("١٧ - حدثنا فلان عن فلان قال: لا شيء هنا")).toBeNull();
  });
});

describe("takhrij matching (normalizeArabic-keyed grouping, as gen-takhrij.ts does)", () => {
  it("groups the same matn quoted with different isnad/tashkeel across two books", () => {
    const bookA = "١ - حدثنا أحمد عن نافع قال: قال رسول الله صلى الله عليه وسلم: «إنَّما الأعمالُ بالنِّيّاتِ»";
    const bookB = "٥ - أخبرنا يحيى عن مالك عن نافع، عن ابن عمر قال: «إنما الأعمال بالنيات»";
    const matnA = parseAtharMatn(bookA)!;
    const matnB = parseAtharMatn(bookB)!;
    expect(normalizeArabic(matnA)).toBe(normalizeArabic(matnB));
  });
  it("does not equate genuinely different narrations", () => {
    const matnA = parseAtharMatn("١ - قال: «إنما الأعمال بالنيات»")!;
    const matnB = parseAtharMatn("٢ - قال: «الدين النصيحة»")!;
    expect(normalizeArabic(matnA)).not.toBe(normalizeArabic(matnB));
  });
});
