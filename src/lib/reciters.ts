// Title → reciter person slugs, for scripts/add-audio-reciters.ts. Arabic is written
// as \u escapes on purpose (bidi-safe); see the Android ArNormalize note.
import { normalizeArabic } from "./ar-normalize.js";

const EM_DASH = "—";
// «،» , / & between names
const LIST_SEP = /\s*[،,/&]\s*/u;
// «و» joining two names: "زيد وعمرو"
const WAW_JOIN = /\s+و(?=\S)/u;

export const normName = (s: string) => normalizeArabic(s).replace(/\s+/gu, " ").trim();

/** Names after the last «—» in an audio title; [] when the title names nobody. */
export function reciterNames(title: string): string[] {
  const at = title.lastIndexOf(EM_DASH);
  if (at < 0) return [];
  return title.slice(at + 1).split(LIST_SEP).map((s) => s.trim()).filter(Boolean);
}

/** Resolves names against normalised person names → slug. A name that only resolves
 *  when split on «و» counts as several reciters; anything else is unmatched. */
export function matchReciters(names: string[], people: ReadonlyMap<string, string>) {
  const slugs: string[] = [];
  const unmatched: string[] = [];
  for (const name of names) {
    const whole = people.get(normName(name));
    if (whole) { slugs.push(whole); continue; }
    const parts = name.split(WAW_JOIN).map((p) => people.get(normName(p)));
    if (parts.length > 1 && parts.every(Boolean)) slugs.push(...(parts as string[]));
    else unmatched.push(name);
  }
  return { slugs: [...new Set(slugs)], unmatched };
}
