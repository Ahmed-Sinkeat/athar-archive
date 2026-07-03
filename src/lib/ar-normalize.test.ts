import { describe, expect, it } from "vitest";
import { normalizeArabic } from "./ar-normalize.js";

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
