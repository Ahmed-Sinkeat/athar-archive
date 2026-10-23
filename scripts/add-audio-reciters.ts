// One-off: fill `reciters: [person slugs]` on src/content/audio/*.md from the name(s)
// after «—» in each title. Only inserts/replaces the `reciters:` frontmatter line; every
// other byte stays. Prints unmatched names with counts for the owner to resolve.
//   tsx scripts/add-audio-reciters.ts [--dry]
import fs from "node:fs";
import path from "node:path";
import matter from "gray-matter";
import { matchReciters, normName, reciterNames } from "../src/lib/reciters.js";

const dry = process.argv.includes("--dry");
const personDir = path.resolve("src/content/person");
const audioDir = path.resolve("src/content/audio");

const people = new Map<string, string>();
for (const file of fs.readdirSync(personDir).filter((f) => f.endsWith(".md"))) {
  const data = matter(fs.readFileSync(path.join(personDir, file), "utf8")).data;
  const slug = file.slice(0, -3);
  for (const name of [data.title, ...(Array.isArray(data.also_known_as) ? data.also_known_as : [])]) {
    if (typeof name === "string" && name.trim()) people.set(normName(name), slug);
  }
}

const unmatched = new Map<string, number>();
let named = 0, written = 0;
for (const file of fs.readdirSync(audioDir).filter((f) => f.endsWith(".md")).sort()) {
  const filePath = path.join(audioDir, file);
  const text = fs.readFileSync(filePath, "utf8");
  const names = reciterNames(String(matter(text).data.title ?? ""));
  if (!names.length) continue;
  named++;
  const { slugs, unmatched: missed } = matchReciters(names, people);
  for (const name of missed) unmatched.set(name, (unmatched.get(name) ?? 0) + 1);
  if (!slugs.length) continue;
  const line = `reciters: ${JSON.stringify(slugs)}`;
  const end = text.indexOf("\n---", 3);
  const head = text.slice(0, end);
  const next = /^reciters:.*$/mu.test(head)
    ? head.replace(/^reciters:.*$/mu, line) + text.slice(end)
    : head.replace(/^(title:.*)$/mu, `$1\n${line}`) + text.slice(end);
  if (next === text) continue;
  // Guard: the only change is the reciters line.
  if (next.replace(`\n${line}`, "") !== text && !/^reciters:/mu.test(head)) throw new Error(`${file}: unexpected rewrite`);
  if (!dry) fs.writeFileSync(filePath, next);
  written++;
}

console.log(`${named} titles name a reciter; ${written} file(s) ${dry ? "would be " : ""}written.`);
console.log(unmatched.size ? `Unmatched (${unmatched.size}):` : "No unmatched names.");
for (const [name, count] of [...unmatched].sort((a, b) => b[1] - a[1])) console.log(`  ${count}\t${name}`);
