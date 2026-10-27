import { describe, expect, it } from "vitest";
import { normalizeArabic, normalizeWithMap } from "./ar-normalize.js";

describe("normalizeArabic", () => {
  it("strips tashkeel and quranic marks", () => {
    expect(normalizeArabic("وَالْعَادِيَاتِ ضَبْحًا")).toBe("والعاديات ضبحا");
  });
  it("unifies alef, ya, ta marbuta variants", () => {
    expect(normalizeArabic("أإآٱ ى ة")).toBe("اااا ي ه");
  });
  it("query and indexed text normalize to the same form", () => {
    expect(normalizeArabic("الصَّلَاة")).toBe(normalizeArabic("الصلاه"));
  });
});

describe("normalizeWithMap", () => {
  it("folds every Arabic-block char exactly as normalizeArabic does", () => {
    for (let c = 0x600; c <= 0x6ff; c++) {
      const ch = String.fromCharCode(c).normalize("NFC");
      expect(normalizeWithMap(ch).text, c.toString(16)).toBe(normalizeArabic(ch));
    }
  });
  it("maps folded positions back to the text as written", () => {
    const raw = "قَالَ: «إِنَّمَا الأَعْمَالُ»";
    const { text, at } = normalizeWithMap(raw);
    const i = text.indexOf("انما");
    expect(raw.slice(at[i], at[i + 3] + 1)).toBe("إِنَّمَا");
  });
});
