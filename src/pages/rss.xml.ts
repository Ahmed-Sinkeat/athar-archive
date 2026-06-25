import type { APIRoute } from "astro";
import { getCollection } from "astro:content";
import { config } from "../../ahlalathar.config";
import { hrefFor } from "../lib/display";

const esc = (s: string) => s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&apos;" }[c]!));

export const GET: APIRoute = async () => {
  const origin = config.siteUrl;
  const pub = (c: any[]) => c.filter((e) => e.data.status === "published");

  const kinds: [string, string][] = [["book", "متن"], ["poem", "منظومة"], ["article", "مقالة"], ["benefit", "فائدة"]];
  const items: { title: string; link: string; date: Date; desc: string }[] = [];
  for (const [coll, label] of kinds) {
    for (const e of pub(await getCollection(coll as any))) {
      items.push({
        title: `${label}: ${e.data.title}`,
        link: new URL(hrefFor(coll, e.id), origin).href,
        date: new Date(e.data.published_at),
        desc: e.data.description ?? e.data.title,
      });
    }
  }
  items.sort((a, b) => +b.date - +a.date);
  const latest = items.slice(0, 30);

  const body =
    `<?xml version="1.0" encoding="UTF-8"?>\n<rss version="2.0"><channel>\n` +
    `<title>أهل الأثر</title>\n<link>${origin}</link>\n` +
    `<description>أحدث الإضافات في الأرشيف العلمي: متونٌ ومنظوماتٌ ودروسٌ وفوائدُ ومقالات.</description>\n` +
    `<language>ar</language>\n` +
    latest
      .map(
        (i) =>
          `<item><title>${esc(i.title)}</title><link>${i.link}</link><guid>${i.link}</guid>` +
          `<pubDate>${i.date.toUTCString()}</pubDate><description>${esc(i.desc)}</description></item>`,
      )
      .join("\n") +
    `\n</channel></rss>\n`;

  return new Response(body, { headers: { "Content-Type": "application/rss+xml; charset=utf-8" } });
};
