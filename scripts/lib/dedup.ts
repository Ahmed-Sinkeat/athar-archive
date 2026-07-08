// Shared duplicate-detection for the article importers + cleanup script.
// Content (not title) is the reliable signal: many posts share a generic
// short title ("تنبيه") with unrelated content, and titles can differ
// slightly across platforms for the same essay — but the body text doesn't.
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import matter from "gray-matter";

export const normText = (s: string) =>
  s.replace(/[ً-ْٰـ]/g, "")
    .replace(/<!--[\s\S]*?-->/g, "")
    .replace(/[\[\]().,:،؛"'«»…\-–—]/g, "")
    .replace(/\s+/g, " ")
    .trim();

export interface ArticleIndexEntry { file: string; title: string; publishedAt: string; bodyNorm: string }

export function loadArticleIndex(dir: string): ArticleIndexEntry[] {
  if (!existsSync(dir)) return [];
  return readdirSync(dir)
    .filter((f) => f.endsWith(".md"))
    .map((f) => {
      const raw = readFileSync(join(dir, f), "utf8");
      const { data, content } = matter(raw);
      return {
        file: f,
        title: String(data.title ?? ""),
        publishedAt: String(data.published_at ?? ""),
        bodyNorm: normText(content),
      };
    });
}

const MIN_BODY_LEN = 30;

/** Exact full-body match — safe from false positives, used for same-source reposts. */
export function findByBody(bodyNorm: string, index: ArticleIndexEntry[]): ArticleIndexEntry | undefined {
  if (bodyNorm.length < MIN_BODY_LEN) return undefined;
  return index.find((e) => e.bodyNorm === bodyNorm);
}

/** Title match OR body-opening match — looser, for cross-source dedup where
 *  the same essay may carry a slightly different title/formatting per platform. */
export function findFuzzy(title: string, bodyNorm: string, index: ArticleIndexEntry[]): ArticleIndexEntry | undefined {
  const nt = normText(title);
  const bStart = bodyNorm.slice(0, 200);
  return index.find(
    (e) => (nt && normText(e.title) === nt) || (bStart.length > 40 && e.bodyNorm.slice(0, 200) === bStart),
  );
}
