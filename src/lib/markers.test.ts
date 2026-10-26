import { describe, expect, it } from "vitest";
import { checkMarkers } from "./markers.js";

describe("checkMarkers", () => {
  it("accepts a well-formed block and ignores card markers", () => {
    expect(checkMarkers("<!-- card -->\nx\n<!-- /card -->\n\n<!-- verse -->\n\nصدر ... عجز\n\n<!-- /verse -->\n")).toEqual([]);
  });
  it("rejects unknown, unclosed, nested, empty and glued markers", () => {
    expect(checkMarkers("<!-- poem -->\n")[0]).toMatch(/unknown/);
    expect(checkMarkers("\n<!-- verse -->\n\nx\n")[0]).toMatch(/never closed/);
    expect(checkMarkers("<!-- verse -->\n\n<!-- hadith -->\n\nx\n\n<!-- /hadith -->\n\n<!-- /verse -->")).toContainEqual(expect.stringMatching(/inside/));
    expect(checkMarkers("<!-- verse -->\n\n<!-- /verse -->")).toContainEqual(expect.stringMatching(/empty/));
    expect(checkMarkers("<!-- verse -->\nx\n\n<!-- /verse -->")).toContainEqual(expect.stringMatching(/blank line/));
    expect(checkMarkers("نص <!-- verse --> نص")[0]).toMatch(/stand alone/);
  });
});
