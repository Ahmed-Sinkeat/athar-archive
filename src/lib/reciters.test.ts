import { describe, expect, it } from "vitest";
import { matchReciters, normName, reciterNames } from "./reciters.js";

describe("reciters", () => {
  const people = new Map([
    [normName("عبد العزيز الصيني"), "abd-al-aziz-al-sini"],
    [normName("سعد الغامدي"), "saad-al-ghamdi"],
  ]);

  it("reads names after the last em dash, split on list separators", () => {
    expect(reciterNames("المقدمة الآجرومية — عبد العزيز الصيني")).toEqual(["عبد العزيز الصيني"]);
    expect(reciterNames("الورقات — زيد، عمرو / بكر")).toEqual(["زيد", "عمرو", "بكر"]);
    expect(reciterNames("المجلس الأول")).toEqual([]);
  });

  it("matches normalised names, splits on «و», and reports the rest", () => {
    expect(matchReciters(["عبد العزيز الصينى", "مجهول"], people))
      .toEqual({ slugs: ["abd-al-aziz-al-sini"], unmatched: ["مجهول"] });
    expect(matchReciters(["عبد العزيز الصيني وسعد الغامدي"], people))
      .toEqual({ slugs: ["abd-al-aziz-al-sini", "saad-al-ghamdi"], unmatched: [] });
  });
});
