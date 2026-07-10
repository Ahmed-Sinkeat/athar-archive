#!/usr/bin/env tsx
// One-off cleanup: legacy swteat_k questions got chrome text (bracket headers,
// "أسئلة البوت"/"المجلس ..." session banners, bare "السؤال الأول:" ordinals) as
// their title because that was the first line of the message. Re-derive the
// title from the first real sentence of the السؤال body instead.
import { readdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { y } from "./lib/slugify.ts";

const DIR = "src/content/question";
const CHROME_LINE = /^\s*(\[.*\]|https?:\/\/\S+|السؤال\s*:?\s*$|-?\s*مقدمة المجلس.*|السؤال\s+(الأول|الثاني|الثالث|الرابع|الخامس|السادس|السابع|الثامن|التاسع|العاشر)\s*:?\s*)\s*$/;

function deriveTitle(body: string): string | null {
  const qMatch = body.match(/## السؤال\n([\s\S]*?)(\n## |$)/);
  if (!qMatch) return null;
  const lines = qMatch[1].split("\n").map((l) => l.trim()).filter(Boolean);
  const kept = lines.filter((l) => !CHROME_LINE.test(l));
  let text = (kept[0] ?? lines[0] ?? "").replace(/^السؤال\s*:?\s*/, "");
  if (!text) return null;
  text = text.split(/[.\n؟!]/)[0].trim().slice(0, 90).trim();
  return text || null;
}

let changed = 0, skipped = 0;
for (const name of readdirSync(DIR)) {
  if (/^swteat-\d+\.md$/.test(name)) continue; // numbered era — titles already clean
  const path = join(DIR, name);
  const text = readFileSync(path, "utf-8");
  const titleMatch = text.match(/^title: "(.*)"$/m);
  if (!titleMatch) continue;
  const current = titleMatch[1];
  // only rewrite obviously-broken titles: bracket chrome, bare ordinal labels, or a URL
  const isBroken = /^\[.*\]|^السؤال\s+(الأول|الثاني|الثالث|الرابع|الخامس|السادس|السابع|الثامن|التاسع|العاشر)\s*:?\s*$|^https?:\/\//.test(current);
  if (!isBroken) { skipped++; continue; }
  const newTitle = deriveTitle(text);
  if (!newTitle || newTitle === current) { skipped++; continue; }
  const updated = text.replace(/^title: ".*"$/m, `title: ${y(newTitle)}`);
  writeFileSync(path, updated);
  changed++;
}
console.log(`${changed} title(s) rewritten, ${skipped} left as-is`);
