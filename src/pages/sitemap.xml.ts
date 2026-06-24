import type { APIRoute } from "astro";
import { getCollection } from "astro:content";
import { config } from "../../ahlalathar.config";
import { lessonParam } from "../lib/display";
import { analyzePoem, analyzeBook } from "../lib/chunk";
import { readBody } from "../lib/read-body";

export const GET: APIRoute = async () => {
  const origin = config.siteUrl;
  const urls: { loc: string; lastmod?: string }[] = [];
  const add = (path: string, date?: unknown) =>
    urls.push({ loc: new URL(path, origin).href, lastmod: date ? new Date(date as string).toISOString() : undefined });

  // static, indexable pages (search is noindex → excluded)
  ["/", "/books", "/poems", "/series", "/topics", "/subjects", "/people", "/articles", "/benefits", "/questions", "/about", "/contact"].forEach((p) => add(p));

  const pub = (c: any[]) => c.filter((e) => e.data.status === "published");
  const when = (e: any) => e.data.updated_at ?? e.data.published_at;

  for (const e of pub(await getCollection("person"))) add(`/person/${e.id}`, when(e));
  for (const e of pub(await getCollection("subject"))) add(`/subject/${e.id}`, when(e));
  for (const e of pub(await getCollection("topic"))) add(`/topic/${e.id}`, when(e));
  for (const e of pub(await getCollection("book"))) {
    add(`/book/${e.id}`, when(e));
    const a = analyzeBook(await readBody(e));
    if (a.chunked) a.chapters.forEach((c) => add(`/book/${e.id}/${c.slug}`, when(e)));
  }
  for (const e of pub(await getCollection("poem"))) {
    add(`/poem/${e.id}`, when(e));
    const a = analyzePoem(await readBody(e));
    if (a.chunked) a.chapters.forEach((c) => add(`/poem/${e.id}/${c.slug}`, when(e)));
  }
  for (const e of pub(await getCollection("series"))) add(`/series/${e.id}`, when(e));
  for (const e of pub(await getCollection("lesson"))) add(`/series/${e.data.series}/${lessonParam(e.id, e.data.series)}`, when(e));
  for (const e of pub(await getCollection("benefit"))) add(`/benefit/${e.id}`, when(e));
  for (const e of pub(await getCollection("article"))) add(`/article/${e.id}`, when(e));
  for (const e of pub(await getCollection("question"))) add(`/questions/${e.id}`, when(e));

  const body =
    `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n` +
    urls.map((u) => `  <url><loc>${u.loc}</loc>${u.lastmod ? `<lastmod>${u.lastmod}</lastmod>` : ""}</url>`).join("\n") +
    `\n</urlset>\n`;

  return new Response(body, { headers: { "Content-Type": "application/xml; charset=utf-8" } });
};
