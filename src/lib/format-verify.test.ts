import { expect, test } from "vitest";
import { verifyFormatting } from "./format-verify.js";

const src = `---\ntitle: "x"\n---\n- اعلم - رحمك الله - ﴿والعصر﴾ 1\n\n<hr class="page-sep" data-page="2" data-notes='["حاشية"]' />\n\n**الأولى**: العِلم<sup data-fn="1">2</sup>`;

test("accepts markup-only edits", () => {
  expect(verifyFormatting(src, `---\ntitle: "x"\n---\n### اعلم - رحمك الله -\n\n﴿والعصر﴾ (^1)\n<hr class="page-sep" data-page="2" data-notes='["حاشية"]' />\nالأولى: العلم(^2)`)).toBeUndefined();
});

test("rejects changed letters, punctuation, frontmatter, page markers or their notes", () => {
  expect(verifyFormatting(src, src.replace("اعلم", "اعلموا"))).toMatch(/text/);
  expect(verifyFormatting(src, src.replace("﴿والعصر﴾", "والعصر"))).toMatch(/text/);
  expect(verifyFormatting(src, src.replace(" - رحمك", " رحمك"))).toMatch(/text/);
  expect(verifyFormatting(src, src.replace(" 1\n", "\n"))).toMatch(/text/);
  expect(verifyFormatting(src, src.replace('"x"', '"y"'))).toMatch(/frontmatter/);
  expect(verifyFormatting(src, src.replace(/<hr[^>]*>/, ""))).toMatch(/page/);
  expect(verifyFormatting(src, src.replace("حاشية", "حاشيه مختلفة"))).toMatch(/text/);
  expect(verifyFormatting(src, src.replace('data-page="2"', 'data-page="3"'))).toMatch(/page/);
});

test("allows merging a cut note back together, not rewording it", () => {
  const cut = `<hr class="page-sep" data-page="30" data-notes='["رقم:","و20 و120.","الأصل"]' />`;
  expect(verifyFormatting(cut, cut.replace('"رقم:","و20', '"رقم: و20'))).toBeUndefined();
  expect(verifyFormatting(cut, cut.replace('"رقم:","و20', '"رقم: و21'))).toMatch(/text/);
});

test("import-fix mode allows renumbering pages and moving text, not changing it", () => {
  const scrambled = `(ودن التي (وقل مليكنا... أتت وتربح) بذلك وأفصحوا)\n<hr class="page-sep" data-page="11" />\nب\n<hr class="page-sep" data-page="9" />`;
  const fixed = `(ودن التي ... أتت وتربح)\n\n(وقل مليكنا... بذلك وأفصحوا)\n<hr class="page-sep" data-page="8" />\nب\n<hr class="page-sep" data-page="9" />`;
  expect(verifyFormatting(scrambled, fixed)).toMatch(/page/);
  expect(verifyFormatting(scrambled, fixed, true)).toBeUndefined();
  expect(verifyFormatting(scrambled, fixed.replace("بذلك", "بذاك"), true)).toMatch(/text/);
  expect(verifyFormatting(scrambled, fixed.replace(/<hr[^>]*>\n/, ""), true)).toMatch(/page/);
});

test("import-fix mode may merge a page the import split in two, moving its notes", () => {
  const split = `أ (^1)\n<hr class="page-sep" data-page="67" data-notes='["عائبين"]' />\n## باب\nب (^1)\n<hr class="page-sep" data-page="67" data-notes='["أخرجه"]' />`;
  const merged = `أ (^1)\n## باب\nب (^2)\n<hr class="page-sep" data-page="67" data-notes='["عائبين","أخرجه"]' />`;
  expect(verifyFormatting(split, merged, true)).toBeUndefined();
  expect(verifyFormatting(split, merged)).toMatch(/page/);
  expect(verifyFormatting(split, merged.replace("## باب\n", ""), true)).toMatch(/text/);
});
