import { describe, it, expect } from "vitest";
import { validate, type ContentEntry } from "./validate.js";

// --- helpers ---

function entry(collection: string, id: string, data: Record<string, unknown>, body = ""): ContentEntry {
  return { id, collection, data: { status: "published", published_at: new Date(), ...data }, body };
}

function draft(collection: string, id: string, data: Record<string, unknown>, body = ""): ContentEntry {
  return entry(collection, id, { ...data, status: "draft" }, body);
}

// Minimal valid corpus shared across tests
function baseCorpus(): ContentEntry[] {
  return [
    entry("person", "ibn-taymiyyah", { title: "ابن تيمية" }),
    entry("subject", "aqeedah", { title: "العقيدة" }),
    entry("topic", "al-asma-was-sifat", { title: "الأسماء والصفات", subject: "aqeedah" }),
    entry("book", "al-wasitiyyah", { title: "الواسطية", person: "ibn-taymiyyah", topics: ["al-asma-was-sifat"] }),
  ];
}

// --- slug format ---

describe("slug-format", () => {
  it("passes valid slugs", () => {
    const errors = validate([entry("person", "valid-slug-123", { title: "Test" })]);
    const slugErrors = errors.filter((e) => e.rule === "slug-format");
    expect(slugErrors).toHaveLength(0);
  });

  it("fails an id with uppercase letters", () => {
    const errors = validate([entry("person", "InvalidSlug", { title: "Test" })]);
    expect(errors.some((e) => e.rule === "slug-format" && e.id === "InvalidSlug")).toBe(true);
  });

  it("fails an id with spaces", () => {
    const errors = validate([entry("person", "has space", { title: "Test" })]);
    expect(errors.some((e) => e.rule === "slug-format")).toBe(true);
  });
});

// --- mandatory person ref ---

describe("mandatory-relation: person", () => {
  it("allows a book with no person (anonymous/unknown author)", () => {
    const errors = validate([entry("book", "orphan-book", { title: "كتاب" })]);
    expect(errors.some((e) => e.rule === "mandatory-relation" && e.id === "orphan-book")).toBe(false);
  });

  it("fails a benefit with no person", () => {
    const errors = validate([entry("benefit", "orphan-benefit", { title: "فائدة" })]);
    expect(errors.some((e) => e.rule === "mandatory-relation" && e.id === "orphan-benefit")).toBe(true);
  });

  it("passes when person exists", () => {
    const errors = validate([
      entry("person", "p1", { title: "شخص" }),
      entry("book", "b1", { title: "كتاب", person: "p1" }),
    ]);
    expect(errors.filter((e) => e.collection === "book")).toHaveLength(0);
  });
});

// --- ref resolution ---

describe("ref-resolution", () => {
  it("fails when person ref does not exist", () => {
    const errors = validate([entry("book", "b1", { title: "كتاب", person: "ghost-person" })]);
    expect(errors.some((e) => e.rule === "ref-resolution" && e.id === "b1")).toBe(true);
  });

  it("fails when topic ref does not exist", () => {
    const errors = validate([
      entry("person", "p1", { title: "شخص" }),
      entry("book", "b1", { title: "كتاب", person: "p1", topics: ["ghost-topic"] }),
    ]);
    expect(errors.some((e) => e.rule === "ref-resolution" && e.id === "b1")).toBe(true);
  });

  it("fails a dangling annotation (target entity missing)", () => {
    const errors = validate([
      entry("annotation", "book-xyz--p1--sharh", {
        title: "شرح",
        target_type: "book",
        target_id: "non-existent-book",
        anchor: "p1",
      }),
    ]);
    expect(errors.some((e) => e.rule === "ref-resolution" && e.collection === "annotation")).toBe(true);
  });

  it("passes a valid annotation pointing to an existing poem", () => {
    const errors = validate([
      entry("person", "p1", { title: "شخص" }),
      entry("poem", "alfiyyah", { title: "الألفية", person: "p1" }, "البيت الأول --- عجزه"),
      entry("annotation", "alfiyyah--v1--sharh", {
        title: "شرح البيت الأول",
        target_type: "poem",
        target_id: "alfiyyah",
        anchor: "v1",
      }),
    ]);
    expect(errors.filter((e) => e.collection === "annotation")).toHaveLength(0);
  });

  it("fails an annotation whose anchor is out of range", () => {
    const errors = validate([
      entry("person", "p1", { title: "شخص" }),
      entry("poem", "alfiyyah", { title: "الألفية", person: "p1" }, "البيت الأول --- عجزه"),
      entry("annotation", "alfiyyah--v999--sharh", {
        title: "شرح بيت غير موجود",
        target_type: "poem",
        target_id: "alfiyyah",
        anchor: "v999",
      }),
    ]);
    expect(errors.some((e) => e.rule === "anchor-resolution")).toBe(true);
  });
});

// --- transcript_status on audio-bearing books ---

describe("transcript-gate (replaced by transcript_status)", () => {
  it("passes a published book with transcript_status and non-empty body", () => {
    const corpus = [
      ...baseCorpus(),
      entry("book", "sharh-wasitiyyah", {
        title: "شرح الواسطية",
        person: "ibn-taymiyyah",
        sharh_of: "al-wasitiyyah",
        transcript_status: "قيد المراجعة",
      }, "## المقدمة\n\nنص"),
    ];
    const errors = validate(corpus);
    expect(errors.filter((e) => e.rule === "ref-resolution" && e.id === "sharh-wasitiyyah")).toHaveLength(0);
  });

  it("fails a book with sharh_of pointing to non-existent book", () => {
    const corpus = [
      ...baseCorpus(),
      entry("book", "sharh-ghost", {
        title: "شرح مجهول",
        person: "ibn-taymiyyah",
        sharh_of: "non-existent-book",
      }, "نص"),
    ];
    const errors = validate(corpus);
    expect(errors.some((e) => e.rule === "ref-resolution" && e.id === "sharh-ghost")).toBe(true);
  });
});


// --- draft-ref-guard ---

describe("draft-ref-guard", () => {
  it("fails when a published entity references a draft person", () => {
    const errors = validate([
      draft("person", "p-draft", { title: "شخص مسودة" }),
      entry("book", "b1", { title: "كتاب", person: "p-draft" }),
    ]);
    expect(errors.some((e) => e.rule === "draft-ref-guard" && e.id === "b1")).toBe(true);
  });

  it("fails when a published book with sharh_of references a draft parent", () => {
    const errors = validate([
      entry("person", "p1", { title: "شخص" }),
      draft("book", "parent-draft", { title: "كتاب مسودة", person: "p1" }),
      entry("book", "sharh-parent", {
        title: "شرح الكتاب",
        person: "p1",
        sharh_of: "parent-draft",
      }, "نص"),
    ]);
    expect(errors.some((e) => e.rule === "draft-ref-guard" && e.id === "sharh-parent")).toBe(true);
  });

  it("allows a draft entity to reference another draft", () => {
    const errors = validate([
      draft("person", "p-draft", { title: "شخص" }),
      draft("book", "b-draft", { title: "كتاب", person: "p-draft" }),
    ]);
    expect(errors.filter((e) => e.rule === "draft-ref-guard")).toHaveLength(0);
  });
});

// --- topic → subject mandatory ---

describe("mandatory-relation: topic → subject", () => {
  it("fails a topic with no subject", () => {
    const errors = validate([entry("topic", "orphan-topic", { title: "موضوع" })]);
    expect(errors.some((e) => e.rule === "mandatory-relation" && e.id === "orphan-topic")).toBe(true);
  });

  it("fails a topic pointing to a non-existent subject", () => {
    const errors = validate([entry("topic", "t1", { title: "موضوع", subject: "ghost-subject" })]);
    expect(errors.some((e) => e.rule === "ref-resolution" && e.id === "t1")).toBe(true);
  });
});

// --- polymorphic source types ---

describe("source-type validation", () => {
  it("fails a benefit with invalid source_type (series)", () => {
    const errors = validate([
      entry("person", "p1", { title: "شخص" }),
      entry("benefit", "b1", { title: "فائدة", person: "p1", source_type: "series", source_id: "x" }),
    ]);
    expect(errors.some((e) => e.rule === "source-type" && e.id === "b1")).toBe(true);
  });

  it("fails a benefit with invalid source_type (lesson)", () => {
    const errors = validate([
      entry("person", "p1", { title: "شخص" }),
      entry("benefit", "b1", { title: "فائدة", person: "p1", source_type: "lesson", source_id: "x" }),
    ]);
    expect(errors.some((e) => e.rule === "source-type" && e.id === "b1")).toBe(true);
  });

  it("passes benefit with source_type book", () => {
    const errors = validate([
      entry("person", "p1", { title: "شخص" }),
      entry("book", "bk1", { title: "كتاب", person: "p1" }),
      entry("benefit", "b1", { title: "فائدة", person: "p1", source_type: "book", source_id: "bk1" }),
    ]);
    expect(errors.filter((e) => e.collection === "benefit")).toHaveLength(0);
  });
});


// --- audio mandatory source ---

describe("audio source", () => {
  it("fails audio with no source_type", () => {
    const errors = validate([
      entry("audio", "aud1", { title: "صوت", url: "https://r2.example.com/a.opus" }),
    ]);
    expect(errors.some((e) => e.rule === "mandatory-relation" && e.id === "aud1")).toBe(true);
  });

  it("passes valid audio pointing to existing book", () => {
    const corpus = [
      ...baseCorpus(),
      entry("audio", "aud-sharh-1", {
        title: "صوت الشرح",
        source_type: "book",
        source_id: "al-wasitiyyah",
        url: "https://r2.arthurarchive.com/audio/sharh.opus",
      }),
    ];
    const errors = validate(corpus);
    expect(errors.filter((e) => e.collection === "audio")).toHaveLength(0);
  });
});


// --- annotation ---

describe("annotation", () => {
  it("fails annotation with missing target_type", () => {
    const errors = validate([
      entry("annotation", "ann1", { title: "شرح", target_id: "some-poem", anchor: "v1" }),
    ]);
    expect(errors.some((e) => e.rule === "mandatory-relation" && e.id === "ann1")).toBe(true);
  });

  it("fails annotation with invalid target_type", () => {
    const errors = validate([
      entry("annotation", "ann1", { title: "شرح", target_type: "article", target_id: "a1", anchor: "p1" }),
    ]);
    expect(errors.some((e) => e.rule === "source-type" && e.id === "ann1")).toBe(true);
  });
});

// --- full valid corpus passes ---

describe("valid corpus", () => {
  it("full fixture set produces zero errors", () => {
    const corpus: ContentEntry[] = [
      entry("person", "ibn-taymiyyah", { title: "ابن تيمية" }),
      entry("person", "ibn-malik-al-nahwi", { title: "ابن مالك" }),
      entry("subject", "aqeedah", { title: "العقيدة" }),
      entry("subject", "nahw", { title: "النحو" }),
      entry("topic", "al-asma-was-sifat", { title: "الأسماء والصفات", subject: "aqeedah" }),
      entry("topic", "al-nahw-al-muyassar", { title: "النحو الميسر", subject: "nahw" }),
      entry("book", "al-wasitiyyah", { title: "الواسطية", person: "ibn-taymiyyah", topics: ["al-asma-was-sifat"] }),
      entry("book", "sharh-al-wasitiyyah", {
        title: "شرح الواسطية",
        person: "ibn-taymiyyah",
        sharh_of: "al-wasitiyyah",
        transcript_status: "قيد المراجعة",
        topics: ["al-asma-was-sifat"],
      }, "## المقدمة\n\nنص"),
      entry("poem", "alfiyyah-ibn-malik", { title: "الألفية", person: "ibn-malik-al-nahwi", topics: ["al-nahw-al-muyassar"] }, "## باب الكلام\n\nكلامنا لفظ مفيد كاستقم --- واسم وفعل ثم حرف الكلم"),
      entry("poem", "al-bayquniyyah", { title: "البيقونية", person: "ibn-taymiyyah" }, "أبدأ بالحمد --- مصليا على"),
      entry("benefit", "tawhid-benefit", { title: "فائدة التوحيد", person: "ibn-taymiyyah", source_type: "book", source_id: "al-wasitiyyah", topics: ["al-asma-was-sifat"] }),
      entry("benefit", "general-benefit", { title: "فائدة عامة", person: "ibn-taymiyyah" }),
      entry("article", "maqala-tawhid", { title: "مقالة في التوحيد", person: "ibn-taymiyyah", topics: ["al-asma-was-sifat"] }),
      entry("audio", "audio-sharh-wasitiyyah", { title: "صوت الشرح", source_type: "book", source_id: "sharh-al-wasitiyyah", url: "https://r2.arthurarchive.com/audio/sharh.opus" }),
      entry("question", "masail-al-asma-was-sifat", { title: "مسائل الأسماء والصفات", person: "ibn-taymiyyah", topics: ["al-asma-was-sifat"] }),
      entry("annotation", "alfiyyah-ibn-malik--v1--sharh", { title: "شرح البيت الأول", target_type: "poem", target_id: "alfiyyah-ibn-malik", anchor: "v1" }),
      entry("announcement", "launch", { title: "انطلاق الموقع", priority: 9 }),
    ];

    const errors = validate(corpus);
    if (errors.length > 0) console.error(errors);
    expect(errors).toHaveLength(0);
  });
});

